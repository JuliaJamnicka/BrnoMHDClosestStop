// Builds the read-only timetable database (timetable.db) from the IDS JMK GTFS feed.
// Runs offline (GitHub Actions / Docker build), never on the request path.
//
// Usage: node dist/build/buildDb.js [--source <url|zip|dir>] [--out <file>]

import Database from 'better-sqlite3';
import { parse } from 'csv-parse';
import fs from 'node:fs';
import path from 'node:path';
import { Readable } from 'node:stream';
import { pathToFileURL } from 'node:url';
import yauzl from 'yauzl';
import { compareLines, modeFromRouteType, sortModes } from '../modes.js';
import { parseGtfsTime } from '../time.js';

export const DEFAULT_SOURCE = 'https://kordis-jmk.cz/gtfs/gtfs.zip';

const NEEDED_FILES = [
  'stops.txt',
  'routes.txt',
  'trips.txt',
  'stop_times.txt',
  'calendar.txt',
  'calendar_dates.txt',
] as const;

type Files = Record<string, Buffer>;
type Row = Record<string, string>;

async function readZip(buffer: Buffer): Promise<Files> {
  const files: Files = {};
  const zip = await new Promise<yauzl.ZipFile>((resolve, reject) =>
    yauzl.fromBuffer(buffer, { lazyEntries: true }, (err, z) => (err ? reject(err) : resolve(z))),
  );
  await new Promise<void>((resolve, reject) => {
    zip.on('entry', (entry: yauzl.Entry) => {
      const name = path.basename(entry.fileName);
      if (!(NEEDED_FILES as readonly string[]).includes(name)) {
        zip.readEntry();
        return;
      }
      zip.openReadStream(entry, (err, stream) => {
        if (err) return reject(err);
        const chunks: Buffer[] = [];
        stream.on('data', (c: Buffer) => chunks.push(c));
        stream.on('end', () => {
          files[name] = Buffer.concat(chunks);
          zip.readEntry();
        });
        stream.on('error', reject);
      });
    });
    zip.on('end', resolve);
    zip.on('error', reject);
    zip.readEntry();
  });
  return files;
}

export async function loadSource(source: string): Promise<Files> {
  if (/^https?:\/\//.test(source)) {
    const res = await fetch(source);
    if (!res.ok) throw new Error(`Download of ${source} failed: HTTP ${res.status}`);
    return readZip(Buffer.from(await res.arrayBuffer()));
  }
  if (fs.statSync(source).isDirectory()) {
    const files: Files = {};
    for (const name of NEEDED_FILES) {
      const p = path.join(source, name);
      if (fs.existsSync(p)) files[name] = fs.readFileSync(p);
    }
    return files;
  }
  return readZip(fs.readFileSync(source));
}

async function* rows(files: Files, name: string): AsyncGenerator<Row> {
  const buffer = files[name];
  if (!buffer) return;
  const parser = Readable.from([buffer]).pipe(
    parse({ columns: true, bom: true, skip_empty_lines: true, relax_column_count: true }),
  );
  for await (const row of parser) yield row as Row;
}

const SCHEMA = `
CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT) WITHOUT ROWID;
CREATE TABLE routes (route_id TEXT PRIMARY KEY, short TEXT NOT NULL, mode TEXT NOT NULL) WITHOUT ROWID;
CREATE TABLE trips (
  trip_id TEXT PRIMARY KEY, route_id TEXT NOT NULL, service_id TEXT NOT NULL,
  headsign TEXT NOT NULL, direction INTEGER NOT NULL, last_seq INTEGER NOT NULL DEFAULT 0
) WITHOUT ROWID;
CREATE TABLE stop_times (
  trip_id TEXT NOT NULL, seq INTEGER NOT NULL, stop_id TEXT NOT NULL,
  arr INTEGER NOT NULL, dep INTEGER NOT NULL, pickup INTEGER NOT NULL,
  PRIMARY KEY (trip_id, seq)
) WITHOUT ROWID;
CREATE TABLE calendar (service_id TEXT PRIMARY KEY, days TEXT NOT NULL, start_date INTEGER NOT NULL, end_date INTEGER NOT NULL) WITHOUT ROWID;
CREATE TABLE calendar_dates (date INTEGER NOT NULL, service_id TEXT NOT NULL, type INTEGER NOT NULL, PRIMARY KEY (date, service_id)) WITHOUT ROWID;
CREATE TABLE groups (group_id TEXT PRIMARY KEY, name TEXT NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL) WITHOUT ROWID;
CREATE TABLE platforms (
  stop_id TEXT PRIMARY KEY, group_id TEXT NOT NULL, name TEXT NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL,
  code TEXT NOT NULL, dir_label TEXT NOT NULL, lines TEXT NOT NULL, modes TEXT NOT NULL,
  opp TEXT, departures INTEGER NOT NULL
) WITHOUT ROWID;
`;

interface PlatformStat {
  routeId: string;
  short: string;
  mode: string;
  direction: number;
  headsign: string;
  n: number;
}

export async function buildDatabase(source: string, outFile: string, log = console.log): Promise<void> {
  const files = await loadSource(source);
  for (const name of NEEDED_FILES) {
    if (!files[name]) throw new Error(`GTFS source is missing ${name}`);
  }

  const tmpFile = `${outFile}.tmp`;
  fs.mkdirSync(path.dirname(path.resolve(outFile)), { recursive: true });
  fs.rmSync(tmpFile, { force: true });
  const db = new Database(tmpFile);
  db.pragma('journal_mode = OFF');
  db.pragma('synchronous = OFF');
  db.exec(SCHEMA);

  // routes
  const insRoute = db.prepare('INSERT INTO routes VALUES (?, ?, ?)');
  let count = 0;
  db.exec('BEGIN');
  for await (const r of rows(files, 'routes.txt')) {
    insRoute.run(r.route_id, r.route_short_name || r.route_long_name || r.route_id, modeFromRouteType(Number(r.route_type)));
    count++;
  }
  db.exec('COMMIT');
  log(`routes: ${count}`);

  // trips
  const insTrip = db.prepare('INSERT INTO trips (trip_id, route_id, service_id, headsign, direction) VALUES (?, ?, ?, ?, ?)');
  count = 0;
  db.exec('BEGIN');
  for await (const r of rows(files, 'trips.txt')) {
    insTrip.run(r.trip_id, r.route_id, r.service_id, (r.trip_headsign ?? '').replace(/ /g, ' ').trim(), Number(r.direction_id || 0));
    count++;
  }
  db.exec('COMMIT');
  log(`trips: ${count}`);

  // stop_times
  const insSt = db.prepare('INSERT INTO stop_times VALUES (?, ?, ?, ?, ?, ?)');
  const lastSeq = new Map<string, number>();
  count = 0;
  db.exec('BEGIN');
  for await (const r of rows(files, 'stop_times.txt')) {
    const seq = Number(r.stop_sequence);
    const arr = parseGtfsTime(r.arrival_time || r.departure_time);
    const dep = parseGtfsTime(r.departure_time || r.arrival_time);
    insSt.run(r.trip_id, seq, r.stop_id, arr, dep, Number(r.pickup_type || 0));
    if ((lastSeq.get(r.trip_id) ?? -1) < seq) lastSeq.set(r.trip_id, seq);
    count++;
  }
  const updLast = db.prepare('UPDATE trips SET last_seq = ? WHERE trip_id = ?');
  for (const [tripId, seq] of lastSeq) updLast.run(seq, tripId);
  db.exec('COMMIT');
  log(`stop_times: ${count}`);

  // calendar
  const insCal = db.prepare('INSERT INTO calendar VALUES (?, ?, ?, ?)');
  const insCalDate = db.prepare('INSERT OR REPLACE INTO calendar_dates VALUES (?, ?, ?)');
  let feedStart = Infinity;
  let feedEnd = 0;
  db.exec('BEGIN');
  for await (const r of rows(files, 'calendar.txt')) {
    const days = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday'].map((d) => r[d]).join('');
    insCal.run(r.service_id, days, Number(r.start_date), Number(r.end_date));
    feedStart = Math.min(feedStart, Number(r.start_date));
    feedEnd = Math.max(feedEnd, Number(r.end_date));
  }
  for await (const r of rows(files, 'calendar_dates.txt')) {
    insCalDate.run(Number(r.date), r.service_id, Number(r.exception_type));
  }
  db.exec('COMMIT');

  // stops: stations become groups, platforms point to their group
  const stops: Row[] = [];
  for await (const r of rows(files, 'stops.txt')) stops.push(r);
  const stations = new Map(stops.filter((s) => s.location_type === '1').map((s) => [s.stop_id, s]));
  const platformRows = stops.filter((s) => (s.location_type || '0') === '0');

  const stats = new Map<string, PlatformStat[]>();
  const statRows = db
    .prepare(
      `SELECT st.stop_id AS stopId, t.route_id AS routeId, r.short AS short, r.mode AS mode,
              t.direction AS direction, t.headsign AS headsign, count(*) AS n
       FROM stop_times st JOIN trips t ON t.trip_id = st.trip_id JOIN routes r ON r.route_id = t.route_id
       WHERE st.seq < t.last_seq AND st.pickup != 1
       GROUP BY 1, 2, 3, 4, 5, 6`,
    )
    .all() as (PlatformStat & { stopId: string })[];
  for (const s of statRows) {
    let list = stats.get(s.stopId);
    if (!list) stats.set(s.stopId, (list = []));
    list.push(s);
  }

  const groupPlatforms = new Map<string, Row[]>();
  for (const p of platformRows) {
    const groupId = p.parent_station && stations.has(p.parent_station) ? p.parent_station : p.stop_id;
    let list = groupPlatforms.get(groupId);
    if (!list) groupPlatforms.set(groupId, (list = []));
    list.push(p);
  }

  const insGroup = db.prepare('INSERT INTO groups VALUES (?, ?, ?, ?)');
  const insPlatform = db.prepare('INSERT INTO platforms VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)');
  db.exec('BEGIN');
  for (const [groupId, platforms] of groupPlatforms) {
    const station = stations.get(groupId);
    const lat = station ? Number(station.stop_lat) : avg(platforms.map((p) => Number(p.stop_lat)));
    const lon = station ? Number(station.stop_lon) : avg(platforms.map((p) => Number(p.stop_lon)));
    insGroup.run(groupId, (station ?? platforms[0]).stop_name, lat, lon);

    for (const p of platforms) {
      const st = stats.get(p.stop_id) ?? [];
      const departures = st.reduce((sum, s) => sum + s.n, 0);
      insPlatform.run(
        p.stop_id,
        groupId,
        p.stop_name,
        Number(p.stop_lat),
        Number(p.stop_lon),
        p.platform_code ?? '',
        directionLabel(st),
        lineList(st),
        sortModes(st.map((s) => s.mode)),
        oppositePlatform(p.stop_id, platforms, stats),
        departures,
      );
    }
  }
  db.exec('COMMIT');
  log(`groups: ${groupPlatforms.size}, platforms: ${platformRows.length}`);

  db.exec('CREATE INDEX stop_times_by_stop ON stop_times (stop_id, dep)');
  db.exec('CREATE INDEX platforms_by_group ON platforms (group_id)');
  const insMeta = db.prepare('INSERT INTO meta VALUES (?, ?)');
  insMeta.run('built_at', new Date().toISOString());
  insMeta.run('source', /^https?:/.test(source) ? source : path.basename(source));
  insMeta.run('feed_start', String(feedStart));
  insMeta.run('feed_end', String(feedEnd));
  db.exec('ANALYZE');
  db.close();

  // reopen to compact with a normal journal, then swap in atomically
  const compact = new Database(tmpFile);
  compact.exec('VACUUM');
  compact.close();
  fs.renameSync(tmpFile, outFile);
  log(`wrote ${outFile} (${(fs.statSync(outFile).size / 1e6).toFixed(1)} MB)`);
}

function avg(values: number[]): number {
  return values.reduce((a, b) => a + b, 0) / values.length;
}

/**
 * Up to two most frequent headsigns departing from a platform, e.g. "Babická, Štefánikova čtvrť".
 * The second one is only kept when it is reasonably common (not a depot run).
 */
function directionLabel(stats: PlatformStat[]): string {
  const byHeadsign = new Map<string, number>();
  for (const s of stats) byHeadsign.set(s.headsign, (byHeadsign.get(s.headsign) ?? 0) + s.n);
  const top = [...byHeadsign.entries()].sort((a, b) => b[1] - a[1]).slice(0, 2);
  if (top.length === 2 && top[1][1] < top[0][1] * 0.2) top.pop();
  return top.map(([h]) => h).join(', ');
}

function lineList(stats: PlatformStat[]): string {
  return [...new Set(stats.map((s) => s.short))].sort(compareLines).join(' ');
}

/**
 * The platform of the same group that serves the same routes in the other direction
 * (docs/SPEC.md 5.4). Null when no platform shares a route.
 */
function oppositePlatform(stopId: string, platforms: Row[], stats: Map<string, PlatformStat[]>): string | null {
  const own = stats.get(stopId) ?? [];
  const wanted = new Set(own.map((s) => `${s.routeId}|${1 - s.direction}`));
  let best: string | null = null;
  let bestScore = 0;
  for (const p of platforms) {
    if (p.stop_id === stopId) continue;
    const score = (stats.get(p.stop_id) ?? [])
      .filter((s) => wanted.has(`${s.routeId}|${s.direction}`))
      .reduce((sum, s) => sum + s.n, 0);
    if (score > bestScore) {
      bestScore = score;
      best = p.stop_id;
    }
  }
  return best;
}

function arg(name: string): string | undefined {
  const i = process.argv.indexOf(name);
  return i >= 0 ? process.argv[i + 1] : undefined;
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
  const source = arg('--source') ?? DEFAULT_SOURCE;
  const out = arg('--out') ?? 'data/timetable.db';
  buildDatabase(source, out).catch((err) => {
    console.error(err);
    process.exit(1);
  });
}
