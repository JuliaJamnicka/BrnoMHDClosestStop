// HTTP API (docs/SPEC.md 5.5). Responses are compact JSON for the phone app,
// which forwards trimmed versions to the watch.

import Fastify, { FastifyInstance } from 'fastify';
import { departures } from './departures.js';
import { offsetM, distanceM } from './geo.js';
import { RealtimeFeed } from './realtime/feed.js';
import { Tracker } from './realtime/tracker.js';
import { Group, Platform, Timetable } from './timetable.js';

export interface AppOptions {
  timetable: Timetable;
  tracker: Tracker;
  feed: Pick<RealtimeFeed, 'refresh' | 'status'>;
  apiKey?: string;
  now?: () => number;
  logger?: boolean;
}

const MAX_NEARBY = 20;
const MAX_DEPARTURES = 8;
const MAX_RADIUS = 2000;
const MAX_VEHICLES = 15;
const MAX_BOARD_PLATFORMS = 8;
const HOME_RADIUS = 400;
const HOME_CANDIDATES = 12;
const HOME_SOON = 30 * 60;

export function buildApp(opts: AppOptions): FastifyInstance {
  const { timetable: tt, tracker, feed } = opts;
  const now = opts.now ?? (() => Math.floor(Date.now() / 1000));
  const app = Fastify({ logger: opts.logger ?? false });

  app.addHook('onRequest', async (req, reply) => {
    if (!opts.apiKey || req.url.startsWith('/v1/health')) return;
    if (req.headers['x-api-key'] !== opts.apiKey) {
      return reply.code(401).send({ error: 'unauthorized' });
    }
  });

  // The nearest platform within HOME_RADIUS with a departure soon; e.g. at night the closest
  // stop may have nothing for hours while a night line stops a few hundred metres away.
  const platformWithService = (lat: number, lon: number, t: number) =>
    tt
      .nearbyPlatforms(lat, lon, HOME_RADIUS)
      .slice(0, HOME_CANDIDATES)
      .find(({ platform }) => {
        const [next] = departures(tt, tracker, platform.stopId, t, 1);
        return next !== undefined && next.e - t <= HOME_SOON;
      });

  // la/lo let the phone tell which of the user's stop lists is nearby without asking the server
  const platformInfo = (p: Platform) => ({ id: p.stopId, dir: p.dirLabel, l: p.lines, la: round5(p.lat), lo: round5(p.lon) });

  const departurePayload = async (group: Group, platform: Platform, n: number, distance?: number) => {
    await feed.refresh(now());
    const t = now();
    return {
      t,
      g: group.groupId,
      stop: group.name,
      p: platform.stopId,
      dir: platform.dirLabel,
      opp: platform.opp,
      ...(distance !== undefined ? { d: Math.round(distance) } : {}),
      pl: group.platforms.filter((x) => x.departures > 0).map(platformInfo),
      dep: departures(tt, tracker, platform.stopId, t, n),
    };
  };

  app.get('/v1/health', async () => {
    const status = feed.status();
    const t = now();
    return {
      ok: true,
      t,
      timetable: { builtAt: tt.meta.built_at, validFrom: tt.meta.feed_start, validTo: tt.meta.feed_end },
      realtime: {
        feedAge: status.feedTimestamp ? t - status.feedTimestamp : null,
        trackedTrips: tracker.size,
        lastError: status.lastError ?? null,
      },
    };
  });

  app.get<{ Querystring: { lat?: string; lon?: string; limit?: string } }>('/v1/nearby', async (req, reply) => {
    const pos = position(req.query);
    if (!pos) return reply.code(400).send({ error: 'lat and lon are required' });
    const limit = clampInt(req.query.limit, 12, 1, MAX_NEARBY);
    return {
      t: now(),
      stops: tt.nearbyGroups(pos.lat, pos.lon, limit).map(({ group, distance, platforms }) => ({
        id: group.groupId,
        n: group.name,
        d: Math.round(distance),
        m: modesOf(platforms.map((x) => x.platform)),
        p: platforms.map((x) => platformInfo(x.platform)),
      })),
    };
  });

  // Stop search by name for the stop-list editor (docs/SPEC.md 6.2): case and diacritics are ignored;
  // names starting with the query come first, then a word starting with it, then any match.
  app.get<{ Querystring: { q?: string; lat?: string; lon?: string; limit?: string } }>('/v1/stops', async (req, reply) => {
    const q = fold((req.query.q ?? '').trim());
    if (q.length < 2) return reply.code(400).send({ error: 'q must have at least 2 characters' });
    const limit = clampInt(req.query.limit, 20, 1, MAX_NEARBY);
    const pos = position(req.query);
    const matches = [];
    for (const group of tt.groups.values()) {
      const platforms = group.platforms.filter((p) => p.departures > 0);
      if (platforms.length === 0) continue;
      const name = fold(group.name);
      const at = name.indexOf(q);
      if (at < 0) continue;
      const rank = at === 0 ? 0 : /[\s,.\-(]/.test(name[at - 1]) ? 1 : 2;
      const distance = pos ? distanceM(pos.lat, pos.lon, group.lat, group.lon) : undefined;
      matches.push({ group, platforms, rank, distance });
    }
    matches.sort((a, b) => a.rank - b.rank || (a.distance ?? 0) - (b.distance ?? 0) || a.group.name.localeCompare(b.group.name, 'cs'));
    return {
      t: now(),
      stops: matches.slice(0, limit).map(({ group, platforms, distance }) => ({
        id: group.groupId,
        n: group.name,
        ...(distance !== undefined ? { d: Math.round(distance) } : {}),
        m: modesOf(platforms),
        p: platforms.map(platformInfo),
      })),
    };
  });

  // One round trip for the watch home screen: closest platform plus its departures.
  app.get<{ Querystring: { lat?: string; lon?: string; n?: string } }>('/v1/home', async (req, reply) => {
    const pos = position(req.query);
    if (!pos) return reply.code(400).send({ error: 'lat and lon are required' });
    const [closest] = tt.nearbyGroups(pos.lat, pos.lon, 1);
    if (!closest) return reply.code(404).send({ error: 'no stops' });
    const n = clampInt(req.query.n, 4, 1, MAX_DEPARTURES);
    await feed.refresh(now());
    const chosen = platformWithService(pos.lat, pos.lon, now()) ?? {
      platform: closest.platforms[0].platform,
      distance: closest.distance,
    };
    return departurePayload(tt.groups.get(chosen.platform.groupId)!, chosen.platform, n, chosen.distance);
  });

  app.get<{ Querystring: { platform?: string; n?: string; lat?: string; lon?: string } }>(
    '/v1/departures',
    async (req, reply) => {
      const platform = req.query.platform ? tt.platforms.get(req.query.platform) : undefined;
      const group = platform ? tt.groups.get(platform.groupId) : undefined;
      if (!platform || !group) return reply.code(404).send({ error: 'unknown platform' });
      const n = clampInt(req.query.n, 4, 1, MAX_DEPARTURES);
      const pos = position(req.query);
      const distance = pos ? distanceM(pos.lat, pos.lon, platform.lat, platform.lon) : undefined;
      return departurePayload(group, platform, n, distance);
    },
  );

  // A user's stop list (docs/SPEC.md 6.2): the next departures of several platforms in one timeline.
  // Unknown platform ids are skipped; a new timetable export can drop a stop.
  app.get<{ Querystring: { platforms?: string; n?: string } }>('/v1/board', async (req, reply) => {
    const platforms = [...new Set((req.query.platforms ?? '').split(','))]
      .slice(0, MAX_BOARD_PLATFORMS)
      .map((id) => tt.platforms.get(id))
      .filter((p): p is Platform => p !== undefined);
    if (platforms.length === 0) return reply.code(404).send({ error: 'unknown platforms' });
    const n = clampInt(req.query.n, 4, 1, MAX_DEPARTURES);
    await feed.refresh(now());
    const t = now();
    const dep = platforms
      .flatMap((p) => departures(tt, tracker, p.stopId, t, n).map((d) => ({ ...d, p: p.stopId, sn: p.name })))
      .sort((a, b) => a.e - b.e || a.s - b.s)
      .slice(0, n);
    return { t, dep };
  });

  app.get<{ Querystring: { lat?: string; lon?: string; r?: string } }>('/v1/vehicles', async (req, reply) => {
    const pos = position(req.query);
    if (!pos) return reply.code(400).send({ error: 'lat and lon are required' });
    const radius = clampInt(req.query.r, 800, 100, MAX_RADIUS);
    await feed.refresh(now());
    const t = now();
    const seen = new Set<string>();
    const vehicles = tracker
      .liveVehicles(t)
      .map((v) => ({ v, distance: distanceM(pos.lat, pos.lon, v.lat, v.lon) }))
      .filter((x) => x.distance <= radius)
      .sort((a, b) => a.distance - b.distance)
      .map(({ v }) => ({ l: v.line, m: v.mode, ...offsetM(pos.lat, pos.lon, v.lat, v.lon), b: v.heading, h: v.headsign, dl: v.delay, a: t - v.ts }))
      // coupled units are reported as separate trips at the same spot; show them once
      .filter((v) => {
        const key = `${v.l}|${Math.round(v.dx / 20)}|${Math.round(v.dy / 20)}`;
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
      })
      .slice(0, MAX_VEHICLES);
    const stops = tt
      .nearbyGroups(pos.lat, pos.lon, 3)
      .filter((x) => x.distance <= radius)
      .map(({ group }) => ({ n: group.name, ...offsetM(pos.lat, pos.lon, group.lat, group.lon) }));
    return { t, v: vehicles, s: stops };
  });

  return app;
}

function position(q: { lat?: string; lon?: string }): { lat: number; lon: number } | undefined {
  const lat = Number(q.lat);
  const lon = Number(q.lon);
  if (q.lat === undefined || q.lon === undefined || !Number.isFinite(lat) || !Number.isFinite(lon)) return undefined;
  if (Math.abs(lat) > 90 || Math.abs(lon) > 180) return undefined;
  return { lat, lon };
}

/** Lower case without diacritics, for name search ("Konečného" matches "konecneho"). */
function fold(text: string): string {
  return text.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
}

function round5(x: number): number {
  return Math.round(x * 1e5) / 1e5;
}

function clampInt(value: string | undefined, fallback: number, min: number, max: number): number {
  const n = Number.parseInt(value ?? '', 10);
  return Number.isFinite(n) ? Math.min(max, Math.max(min, n)) : fallback;
}

function modesOf(platforms: Platform[]): string {
  const order = 'TBVL';
  return [...new Set(platforms.flatMap((p) => p.modes.split('')))]
    .sort((a, b) => order.indexOf(a) - order.indexOf(b))
    .join('');
}
