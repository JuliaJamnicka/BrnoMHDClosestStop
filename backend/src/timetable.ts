// Read-only access to timetable.db plus small in-memory indexes.

import Database from 'better-sqlite3';
import { distanceM } from './geo.js';
import { addDays, weekday } from './time.js';

export interface Platform {
  stopId: string;
  groupId: string;
  name: string;
  lat: number;
  lon: number;
  code: string;
  dirLabel: string;
  lines: string;
  modes: string;
  opp: string | null;
  departures: number;
}

export interface Group {
  groupId: string;
  name: string;
  lat: number;
  lon: number;
  platforms: Platform[];
}

export interface ScheduledDeparture {
  tripId: string;
  seq: number;
  dep: number;
  serviceId: string;
  headsign: string;
  line: string;
  mode: string;
}

export interface TripStop {
  seq: number;
  stopId: string;
  arr: number;
  dep: number;
}

export interface TripInfo {
  tripId: string;
  serviceId: string;
  line: string;
  mode: string;
  headsign: string;
  lastSeq: number;
}

const TRIP_CACHE_LIMIT = 4000;

export class Timetable {
  readonly db: Database.Database;
  readonly platforms = new Map<string, Platform>();
  readonly groups = new Map<string, Group>();
  readonly meta: Record<string, string> = {};
  private readonly servicesByDate = new Map<number, Set<string>>();
  private readonly tripStopsCache = new Map<string, TripStop[]>();
  private readonly stmtDepartures: Database.Statement;
  private readonly stmtTripStops: Database.Statement;
  private readonly stmtTrip: Database.Statement;

  constructor(file: string) {
    this.db = new Database(file, { readonly: true, fileMustExist: true });
    for (const row of this.db.prepare('SELECT key, value FROM meta').all() as { key: string; value: string }[]) {
      this.meta[row.key] = row.value;
    }
    for (const g of this.db.prepare('SELECT group_id, name, lat, lon FROM groups').all() as any[]) {
      this.groups.set(g.group_id, { groupId: g.group_id, name: g.name, lat: g.lat, lon: g.lon, platforms: [] });
    }
    for (const p of this.db.prepare('SELECT * FROM platforms').all() as any[]) {
      const platform: Platform = {
        stopId: p.stop_id,
        groupId: p.group_id,
        name: p.name,
        lat: p.lat,
        lon: p.lon,
        code: p.code,
        dirLabel: p.dir_label,
        lines: p.lines,
        modes: p.modes,
        opp: p.opp,
        departures: p.departures,
      };
      this.platforms.set(platform.stopId, platform);
      this.groups.get(platform.groupId)?.platforms.push(platform);
    }

    this.stmtDepartures = this.db.prepare(
      `SELECT st.trip_id AS tripId, st.seq AS seq, st.dep AS dep, t.service_id AS serviceId,
              t.headsign AS headsign, r.short AS line, r.mode AS mode
       FROM stop_times st
       JOIN trips t ON t.trip_id = st.trip_id
       JOIN routes r ON r.route_id = t.route_id
       WHERE st.stop_id = ? AND st.dep BETWEEN ? AND ? AND st.seq < t.last_seq AND st.pickup != 1
       ORDER BY st.dep`,
    );
    this.stmtTripStops = this.db.prepare(
      'SELECT seq, stop_id AS stopId, arr, dep FROM stop_times WHERE trip_id = ? ORDER BY seq',
    );
    this.stmtTrip = this.db.prepare(
      `SELECT t.trip_id AS tripId, t.service_id AS serviceId, r.short AS line, r.mode AS mode,
              t.headsign AS headsign, t.last_seq AS lastSeq
       FROM trips t JOIN routes r ON r.route_id = t.route_id WHERE t.trip_id = ?`,
    );
  }

  close(): void {
    this.db.close();
  }

  /** Service ids running on a YYYYMMDD service date. */
  activeServices(date: number): Set<string> {
    let set = this.servicesByDate.get(date);
    if (set) return set;
    set = new Set<string>();
    const day = weekday(date);
    const rows = this.db
      .prepare('SELECT service_id, days FROM calendar WHERE start_date <= ? AND end_date >= ?')
      .all(date, date) as { service_id: string; days: string }[];
    for (const r of rows) if (r.days[day] === '1') set.add(r.service_id);
    const exceptions = this.db
      .prepare('SELECT service_id, type FROM calendar_dates WHERE date = ?')
      .all(date) as { service_id: string; type: number }[];
    for (const e of exceptions) {
      if (e.type === 1) set.add(e.service_id);
      else set.delete(e.service_id);
    }
    if (this.servicesByDate.size > 14) this.servicesByDate.clear();
    this.servicesByDate.set(date, set);
    return set;
  }

  /** Scheduled departures from a platform with GTFS times in [fromSec, toSec] of some service day. */
  scheduledDepartures(stopId: string, fromSec: number, toSec: number): ScheduledDeparture[] {
    return this.stmtDepartures.all(stopId, fromSec, toSec) as ScheduledDeparture[];
  }

  tripStops(tripId: string): TripStop[] {
    let stops = this.tripStopsCache.get(tripId);
    if (stops) return stops;
    stops = this.stmtTripStops.all(tripId) as TripStop[];
    if (this.tripStopsCache.size >= TRIP_CACHE_LIMIT) this.tripStopsCache.clear();
    this.tripStopsCache.set(tripId, stops);
    return stops;
  }

  trip(tripId: string): TripInfo | undefined {
    return this.stmtTrip.get(tripId) as TripInfo | undefined;
  }

  /** Groups that have departures, ordered by distance of their closest departure platform. */
  nearbyGroups(lat: number, lon: number, limit: number): { group: Group; distance: number; platforms: { platform: Platform; distance: number }[] }[] {
    const result = [];
    for (const group of this.groups.values()) {
      const platforms = group.platforms
        .filter((p) => p.departures > 0)
        .map((platform) => ({ platform, distance: distanceM(lat, lon, platform.lat, platform.lon) }))
        .sort((a, b) => a.distance - b.distance);
      if (platforms.length === 0) continue;
      result.push({ group, distance: platforms[0].distance, platforms });
    }
    return result.sort((a, b) => a.distance - b.distance).slice(0, limit);
  }

  /** Dates whose service day may still be running at the given local date: today and yesterday. */
  static candidateDates(today: number): number[] {
    return [today, addDays(today, -1)];
  }
}
