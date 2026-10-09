// Turns vehicle positions into per-trip delay estimates (docs/SPEC.md 5.3).
// The KORDIS feed has positions only, so delays are derived from the schedule:
//  - a lower bound from how overdue the vehicle is at its next stop, and
//  - a measured departure delay when two observations show it leaving a stop.

import { distanceM } from '../geo.js';
import { Timetable, TripInfo, TripStop } from '../timetable.js';
import { localDate, serviceDayBase } from '../time.js';

export const STOPPED_AT = 1;

export interface RtVehicle {
  vehicleId: string;
  tripId?: string;
  stopId?: string;
  /** GTFS-RT VehicleStopStatus: 0 incoming at, 1 stopped at, 2 in transit to. */
  status: number;
  ts: number;
  lat: number;
  lon: number;
  bearing?: number;
}

export interface TripState {
  tripId: string;
  base: number;
  /** Highest stop_sequence the vehicle has already left. */
  passedSeq: number;
  delay: number;
  /** Departure delay measured at the last stop the vehicle was seen leaving. */
  carry?: number;
  ts: number;
  lat: number;
  lon: number;
  bearing: number;
  line: string;
  mode: string;
}

interface TripMatch {
  tripId: string;
  stops: TripStop[];
  trip: TripInfo;
  base: number;
  /** Index in stops of the vehicle's current / next stop. */
  k: number;
}

/** Positions newer than this are "live" (docs/SPEC.md 3.2: feed refreshes every ~30 s). */
export const LIVE_MAX_AGE = 120;
/** Trip state is kept this long after the vehicle drops out of the feed. */
export const STATE_MAX_AGE = 300;

/** Real-time stop ids are zero-padded (U01677Z02); static ones are not (U1677Z2). */
export function normalizeStopId(id: string): string {
  const m = /^U0*(\d+)Z0*(\d+)$/.exec(id);
  return m ? `U${m[1]}Z${m[2]}` : id;
}

export class Tracker {
  private readonly states = new Map<string, TripState>();

  constructor(private readonly timetable: Timetable) {}

  update(vehicles: RtVehicle[], now: number): void {
    for (const v of vehicles) {
      if (!v.tripId || now - v.ts > STATE_MAX_AGE) continue;
      const match = this.resolve(v);
      if (!match) continue;
      const prev = this.states.get(match.tripId);
      if (prev && prev.ts >= v.ts) continue;
      this.states.set(match.tripId, this.estimate(v, match, prev));
    }
    for (const [tripId, s] of this.states) {
      if (now - s.ts > STATE_MAX_AGE) this.states.delete(tripId);
    }
  }

  /** State of a trip on a given service day, if known and not too old. */
  get(tripId: string, base: number, now: number): TripState | undefined {
    const s = this.states.get(tripId);
    if (!s || s.base !== base || now - s.ts > STATE_MAX_AGE) return undefined;
    return s;
  }

  /** Vehicles with a known trip and a position younger than LIVE_MAX_AGE. */
  liveVehicles(now: number): TripState[] {
    return [...this.states.values()].filter((s) => now - s.ts <= LIVE_MAX_AGE);
  }

  get size(): number {
    return this.states.size;
  }

  /**
   * Finds the trip a vehicle runs. The feed's trip_id may use the numbering of an older export
   * (KORDIS renumbers trips with every export), so the id and its alias are both tried and the one
   * whose schedule contains the vehicle's next stop at about this time wins.
   */
  private resolve(v: RtVehicle): TripMatch | undefined {
    const candidates = [v.tripId!, ...this.timetable.aliasesOf(v.tripId!)];
    const stopId = v.stopId ? normalizeStopId(v.stopId) : undefined;
    let fallback: TripMatch | undefined;
    // first pass: trips running that day; second pass: any trip whose times fit
    for (const runningOnly of [true, false]) {
      for (const tripId of candidates) {
        const stops = this.timetable.tripStops(tripId);
        const trip = this.timetable.trip(tripId);
        if (stops.length === 0 || !trip) continue;
        const base = this.chooseBase(v.ts, stops, trip.serviceId, runningOnly);
        if (base === undefined) continue;
        const k = stopId ? stops.findIndex((s) => s.stopId === stopId) : -1;
        if (k >= 0) return { tripId, stops, trip, base, k };
        if (!fallback) {
          const nearest = this.nearestStopIndex(stops, v.lat, v.lon);
          if (nearest >= 0) fallback = { tripId, stops, trip, base, k: nearest };
        }
      }
    }
    return fallback;
  }

  private estimate(v: RtVehicle, match: TripMatch, prev: TripState | undefined): TripState {
    const { stops, trip, base, k } = match;

    let passedSeq: number;
    let lower: number;
    if (v.status === STOPPED_AT) {
      passedSeq = stops[k].seq - 1;
      lower = v.ts - (base + stops[k].dep);
    } else if (k === 0) {
      passedSeq = stops[0].seq - 1;
      lower = 0;
    } else {
      passedSeq = stops[k - 1].seq;
      lower = v.ts - (base + stops[k].arr);
    }

    let carry = prev && prev.base === base ? prev.carry : undefined;
    if (prev && prev.base === base && passedSeq > prev.passedSeq) {
      // Seen leaving a stop between the two observations: use the midpoint as departure time.
      const left = stops.find((s) => s.seq === passedSeq);
      if (left) carry = Math.max(0, Math.round((prev.ts + v.ts) / 2) - (base + left.dep));
    }
    if (passedSeq < stops[0].seq) carry = undefined;

    return {
      tripId: match.tripId,
      base,
      passedSeq,
      delay: Math.max(0, Math.round(lower), carry ?? 0),
      carry,
      ts: v.ts,
      lat: v.lat,
      lon: v.lon,
      bearing: v.bearing ?? 0,
      line: trip.line,
      mode: trip.mode,
    };
  }

  /** Service day the trip is running on: today or yesterday (trips after midnight). */
  private chooseBase(ts: number, stops: TripStop[], serviceId: string, runningOnly = false): number | undefined {
    let fallback: number | undefined;
    for (const date of Timetable.candidateDates(localDate(ts))) {
      const base = serviceDayBase(date);
      const start = base + stops[0].dep - 3600;
      const end = base + stops[stops.length - 1].arr + 3600;
      if (ts < start || ts > end) continue;
      if (this.timetable.activeServices(date).has(serviceId)) return base;
      fallback ??= base;
    }
    return runningOnly ? undefined : fallback;
  }

  private nearestStopIndex(stops: TripStop[], lat: number, lon: number): number {
    let best = -1;
    let bestDistance = Infinity;
    stops.forEach((s, i) => {
      const p = this.timetable.platforms.get(s.stopId);
      if (!p) return;
      const d = distanceM(lat, lon, p.lat, p.lon);
      if (d < bestDistance) {
        bestDistance = d;
        best = i;
      }
    });
    return best;
  }
}
