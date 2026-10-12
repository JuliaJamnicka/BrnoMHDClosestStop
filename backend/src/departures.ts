// Departure computation for a platform (docs/SPEC.md 5.4).

import { LIVE_MAX_AGE, Tracker } from './realtime/tracker.js';
import { addDays, localDate, serviceDayBase } from './time.js';
import { Timetable } from './timetable.js';

export interface Departure {
  /** Line name, e.g. "4", "N93", "S2". */
  l: string;
  /** Mode category: T tram, B bus (incl. trolleybus), V train, L boat. */
  m: string;
  /** Headsign. */
  h: string;
  /** Expected departure, epoch seconds. */
  e: number;
  /** Scheduled departure, epoch seconds. */
  s: number;
  /** Delay in seconds. */
  dl: number;
  /** 1 when a live vehicle position was used. */
  lv: 0 | 1;
}

const LOOKBACK = 20 * 60; // delayed vehicles scheduled up to 20 min ago may still come
const WINDOW = 120 * 60;
const EXTENDED_WINDOW = 12 * 3600; // night / sparse regional stops

/** [hidden]: lines the user filtered out of a stop list; left out before counting to n. */
export function departures(tt: Timetable, tracker: Tracker, stopId: string, now: number, n: number, hidden?: Set<string>): Departure[] {
  const shown = (d: Departure) => !hidden?.has(d.l);
  let result = collect(tt, tracker, stopId, now, WINDOW).filter(shown);
  if (result.length === 0) result = collect(tt, tracker, stopId, now, EXTENDED_WINDOW).filter(shown);
  return result.slice(0, n);
}

function collect(tt: Timetable, tracker: Tracker, stopId: string, now: number, window: number): Departure[] {
  const out: Departure[] = [];
  const today = localDate(now);
  // yesterday for trips after midnight, tomorrow for the extended window late in the evening
  for (const date of [addDays(today, -1), today, addDays(today, 1)]) {
    const base = serviceDayBase(date);
    const services = tt.activeServices(date);
    for (const row of tt.scheduledDepartures(stopId, now - base - LOOKBACK, now - base + window)) {
      if (!services.has(row.serviceId)) continue;
      const scheduled = base + row.dep;
      const rt = tracker.get(row.tripId, base, now);
      if (rt && rt.passedSeq >= row.seq) continue; // already left this stop
      const delay = rt?.delay ?? 0;
      const expected = scheduled + delay;
      if (expected < now - 30) continue;
      out.push({
        l: row.line,
        m: row.mode,
        h: row.headsign,
        e: expected,
        s: scheduled,
        dl: delay,
        lv: rt && now - rt.ts <= LIVE_MAX_AGE ? 1 : 0,
      });
    }
  }
  return out.sort((a, b) => a.e - b.e || a.s - b.s);
}
