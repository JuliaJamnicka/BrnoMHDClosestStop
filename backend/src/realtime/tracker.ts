// Turns vehicle positions into per-trip delay estimates (docs/SPEC.md 5.3).
// The KORDIS feed has positions only, so delays are derived from the schedule:
//  - a lower bound from how overdue the vehicle is at its next stop, and
//  - a measured departure delay when two observations show it leaving a stop.

import { bearingDeg, distanceM } from '../geo.js';
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
  /** stop_sequence of the stop the vehicle was last seen standing at, until it moves on. */
  atSeq?: number;
  ts: number;
  lat: number;
  lon: number;
  /** Direction of travel in degrees (0 north), or -1 when unknown. */
  heading: number;
  line: string;
  mode: string;
  headsign: string;
}

interface TripMatch {
  tripId: string;
  stops: TripStop[];
  trip: TripInfo;
  base: number;
  /** Index in stops of the vehicle's current / next stop. */
  k: number;
  /** Standing at stops[k] (reported so, or found there by position) rather than on its way to it. */
  stopped: boolean;
}

/** Positions newer than this are "live" (docs/SPEC.md 3.2: feed refreshes every ~30 s). */
export const LIVE_MAX_AGE = 120;
/** Trip state is kept this long after the vehicle drops out of the feed. */
export const STATE_MAX_AGE = 300;
/**
 * A trip matched without the vehicle's stop (the feed's trip_id names another trip, e.g. right after
 * KORDIS renumbered its export) must still fit: its nearest stop this close to the vehicle and
 * scheduled about now. Otherwise the vehicle stays unmatched rather than getting a made-up delay.
 */
export const FALLBACK_MAX_DISTANCE_M = 1000;
/**
 * A trip matched by the vehicle's stop must be due there within this window. Real delays over an
 * hour are rare; a match that far off is almost always a trip_id of another export (seen on
 * 2026-10-16: up to +100 min). Such a vehicle stays unmatched and its departure shows the timetable.
 */
export const STOP_MATCH_MAX_LATE = 60 * 60;
export const STOP_MATCH_MAX_EARLY = 30 * 60;
export const FALLBACK_MAX_LATE = 30 * 60;
export const FALLBACK_MAX_EARLY = 10 * 60;
/**
 * A vehicle this close to its trip's last stop has finished the trip (it may idle there a while).
 * Generous because bus stations are large: parked buses at ÚAN Zvonařka or Znojmo were 150-400 m
 * from the stop's coordinates.
 */
export const TERMINUS_RADIUS_M = 400;
/**
 * A vehicle this close to a stop counts as standing there when the feed's stop does not tell (see
 * resolve). Trains stop up to a platform length away from the stop's coordinates.
 */
export const AT_STOP_RADIUS_M = 250;
/**
 * Two observations further apart than this give only a lower bound for a departure: the vehicle left
 * some time after the first one. Trains are sometimes reported only every minute or two.
 */
export const DEPARTURE_GAP_S = 60;
/**
 * A vehicle followed between stops through points the timetable does not have must stay this close
 * to the stops around it (the S3 runs up to ~1.5 km from Židenice and Lesná on its way round
 * Maloměřice).
 */
export const CONTINUE_MAX_DISTANCE_M = 2500;

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
    let continued: TripMatch | undefined;
    // first pass: trips running that day; second pass: any trip whose times fit
    for (const runningOnly of [true, false]) {
      // Of the candidates serving the vehicle's stop, the one due there closest to now wins: an alias
      // can name several trips, and a trip_id of another export can name a trip of another hour.
      let best: { match: TripMatch; off: number } | undefined;
      for (const tripId of candidates) {
        const stops = this.timetable.tripStops(tripId);
        const trip = this.timetable.trip(tripId);
        if (stops.length === 0 || !trip) continue;
        const base = this.chooseBase(v.ts, stops, trip.serviceId, runningOnly);
        if (base === undefined) continue;
        const k = stopId ? stops.findIndex((s) => s.stopId === stopId) : -1;
        if (k >= 0) {
          const late = v.ts - (base + (v.status === STOPPED_AT ? stops[k].dep : stops[k].arr));
          if (late > STOP_MATCH_MAX_LATE || late < -STOP_MATCH_MAX_EARLY) continue;
          if (!best || Math.abs(late) < best.off) {
            best = { match: { tripId, stops, trip, base, k, stopped: v.status === STOPPED_AT }, off: Math.abs(late) };
          }
          continue;
        }
        // The feed's stop is not on the trip. Trains report points the timetable does not have at all
        // (e.g. Brno-Maloměřice between Židenice and Lesná, where the S3 does not stop): a vehicle
        // already followed on this trip is placed by its last state and its position, not by its
        // nearest stop, which lies behind it half of the time and made on-time trains look minutes
        // late. A stop the timetable has but the trip does not serve means another trip instead.
        const prev = this.states.get(tripId);
        if (!continued && prev && prev.base === base && stopId && !this.timetable.platforms.has(stopId)) {
          continued = this.continueTrip(v, { tripId, stops, trip, base }, prev);
        }
        if (!continued && !fallback) {
          const nearest = this.nearestStopIndex(stops, v.lat, v.lon);
          if (nearest >= 0 && this.plausible(v, stops[nearest], base)) fallback = this.byPosition(v, { tripId, stops, trip, base }, nearest);
        }
      }
      if (best) return best.match;
    }
    return continued ?? fallback;
  }

  /**
   * Where a vehicle seen on this trip before is now: at the stop it was heading to, or past it.
   * Undefined when it is no longer near that part of the trip.
   */
  private continueTrip(v: RtVehicle, trip: Omit<TripMatch, 'k' | 'stopped'>, prev: TripState): TripMatch | undefined {
    const { stops } = trip;
    let k = stops.findIndex((s) => s.seq > prev.passedSeq);
    if (k < 0) return undefined;
    if (!stops.slice(Math.max(0, k - 1), k + 2).some((s) => this.near(v, s, CONTINUE_MAX_DISTANCE_M))) return undefined;
    if (this.near(v, stops[k], AT_STOP_RADIUS_M)) return { ...trip, k, stopped: true };
    // seen standing at it and now away: it has left
    if (prev.atSeq === stops[k].seq && k < stops.length - 1) k++;
    return { ...trip, k, stopped: false };
  }

  /** A vehicle matched by position only: at its nearest stop, or on its way to it or the next one. */
  private byPosition(v: RtVehicle, trip: Omit<TripMatch, 'k' | 'stopped'>, nearest: number): TripMatch {
    const { stops } = trip;
    if (v.status === STOPPED_AT || this.near(v, stops[nearest], AT_STOP_RADIUS_M)) return { ...trip, k: nearest, stopped: true };
    const next = stops[nearest + 1];
    const here = this.timetable.platforms.get(stops[nearest].stopId);
    const there = next && this.timetable.platforms.get(next.stopId);
    // closer to the next stop than the nearest one is: the nearest is behind it
    if (here && there && distanceM(v.lat, v.lon, there.lat, there.lon) < distanceM(here.lat, here.lon, there.lat, there.lon)) {
      return { ...trip, k: nearest + 1, stopped: false };
    }
    return { ...trip, k: nearest, stopped: false };
  }

  private estimate(v: RtVehicle, match: TripMatch, prev: TripState | undefined): TripState {
    const { stops, trip, base, k, stopped } = match;

    const last = stops.length - 1;
    if (k === last && (stopped || this.atStop(v, stops[last]))) {
      // At (or stopped at) the last stop the trip is done. The feed keeps reporting it there while
      // the vehicle waits, which must not grow the delay.
      return {
        tripId: match.tripId,
        base,
        passedSeq: stops[last].seq,
        delay: prev && prev.base === base ? prev.delay : 0,
        carry: undefined,
        ts: v.ts,
        lat: v.lat,
        lon: v.lon,
        heading: this.heading(v, stops[last], prev),
        line: trip.line,
        mode: trip.mode,
        headsign: trip.headsign,
      };
    }

    let passedSeq: number;
    let lower: number;
    if (stopped) {
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
      // over a long gap only the first observation is sure: it left after that
      const leftAt = v.ts - prev.ts > DEPARTURE_GAP_S ? prev.ts : Math.round((prev.ts + v.ts) / 2);
      if (left) carry = Math.max(0, leftAt - (base + left.dep));
    }
    if (passedSeq < stops[0].seq) carry = undefined;

    return {
      tripId: match.tripId,
      base,
      passedSeq,
      delay: Math.max(0, Math.round(lower), carry ?? 0),
      carry,
      // also when the feed says "in transit to" a stop the vehicle already stands at
      atSeq: stopped || this.near(v, stops[k], AT_STOP_RADIUS_M) ? stops[k].seq : undefined,
      ts: v.ts,
      lat: v.lat,
      lon: v.lon,
      heading: this.heading(v, stops[k], prev),
      line: trip.line,
      mode: trip.mode,
      headsign: trip.headsign,
    };
  }

  /**
   * The feed's bearing when it has one. About a tenth of the vehicles report 0 (also while
   * standing), so 0 counts as missing: then the direction to the next stop is used, or the last
   * known heading when the vehicle is (almost) at that stop.
   */
  private heading(v: RtVehicle, next: TripStop, prev: TripState | undefined): number {
    if (v.bearing) return Math.round(v.bearing) % 360;
    const p = this.timetable.platforms.get(next.stopId);
    if (p && distanceM(v.lat, v.lon, p.lat, p.lon) > 30) return bearingDeg(v.lat, v.lon, p.lat, p.lon);
    return prev?.heading ?? -1;
  }

  /** Whether a trip matched by position fits: its stop near the vehicle and due about now. */
  private plausible(v: RtVehicle, stop: TripStop, base: number): boolean {
    const p = this.timetable.platforms.get(stop.stopId);
    if (!p || distanceM(v.lat, v.lon, p.lat, p.lon) > FALLBACK_MAX_DISTANCE_M) return false;
    const late = v.ts - (base + stop.arr);
    return late <= FALLBACK_MAX_LATE && late >= -FALLBACK_MAX_EARLY;
  }

  private atStop(v: RtVehicle, stop: TripStop): boolean {
    return this.near(v, stop, TERMINUS_RADIUS_M);
  }

  private near(v: RtVehicle, stop: TripStop, radius: number): boolean {
    const p = this.timetable.platforms.get(stop.stopId);
    return !!p && distanceM(v.lat, v.lon, p.lat, p.lon) <= radius;
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
