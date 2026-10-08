import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { buildApp } from '../src/app.js';
import { departures } from '../src/departures.js';
import { normalizeStopId, RtVehicle, STOPPED_AT, Tracker } from '../src/realtime/tracker.js';
import { serviceDayBase, localDate, parseGtfsTime } from '../src/time.js';
import { Timetable } from '../src/timetable.js';
import { buildFixtureDb } from './fixture.js';

const IN_TRANSIT_TO = 2;
const DAY = 20261008; // a Thursday
const BASE = serviceDayBase(DAY);
const at = (hms: string) => BASE + parseGtfsTime(hms);

let tt: Timetable;

beforeAll(async () => {
  tt = new Timetable(await buildFixtureDb());
});

afterAll(() => tt.close());

describe('time', () => {
  it('uses local midnight on a normal day', () => {
    expect(BASE).toBe(Date.UTC(2026, 9, 7, 22) / 1000); // 00:00 CEST
    expect(localDate(BASE)).toBe(DAY);
  });

  it('uses noon minus 12 h on the DST change day', () => {
    // 29 March 2026: noon CEST is 10:00 UTC, so the base is 22:00 UTC the day before
    expect(serviceDayBase(20260329)).toBe(Date.UTC(2026, 2, 28, 22) / 1000);
  });
});

describe('timetable build', () => {
  it('groups platforms and finds the opposite direction', () => {
    expect(tt.platforms.get('U1Z1')).toMatchObject({ groupId: 'U1N1', opp: 'U1Z2', lines: '4 N93', modes: 'TB' });
    expect(tt.platforms.get('U1Z2')).toMatchObject({ opp: 'U1Z1', dirLabel: 'Komárov' });
  });

  it('does not count the final stop as a departure', () => {
    expect(tt.platforms.get('U3Z1')!.departures).toBe(0);
  });

  it('orders nearby groups by their closest platform', () => {
    const [first] = tt.nearbyGroups(49.198, 16.606, 5);
    expect(first.group.name).toBe('Česká');
    expect(first.platforms.map((p) => p.platform.stopId)).toEqual(['U1Z1', 'U1Z2']);
  });
});

describe('real-time stop ids', () => {
  it('strips the zero padding used by the real-time feed', () => {
    expect(normalizeStopId('U01677Z02')).toBe('U1677Z2');
    expect(normalizeStopId('U1073Z10')).toBe('U1073Z10');
  });
});

describe('departures', () => {
  it('lists scheduled departures when there is no real-time data', () => {
    const tracker = new Tracker(tt);
    const deps = departures(tt, tracker, 'U1Z1', at('9:58:00'), 4);
    expect(deps.map((d) => [d.l, d.e - BASE])).toEqual([
      ['4', parseGtfsTime('10:05:00')],
      ['4', parseGtfsTime('10:25:00')],
    ]);
    expect(deps[0]).toMatchObject({ m: 'T', h: 'Řečkovice', dl: 0, lv: 0 });
  });

  it('applies the delay of a vehicle that is late at an earlier stop', () => {
    const tracker = new Tracker(tt);
    const now = at('10:03:00');
    // still at the first stop 3 minutes after its departure time
    tracker.update([vehicle({ tripId: '100', stopId: 'U02Z01', status: STOPPED_AT, ts: now })], now);
    const [first] = departures(tt, tracker, 'U1Z1', now, 4);
    expect(first).toMatchObject({ l: '4', dl: 180, lv: 1, e: at('10:08:00') });
  });

  it('drops a departure once the vehicle has left the stop', () => {
    const tracker = new Tracker(tt);
    const now = at('10:06:00');
    tracker.update([vehicle({ tripId: '100', stopId: 'U3Z1', status: IN_TRANSIT_TO, ts: now })], now);
    const deps = departures(tt, tracker, 'U1Z1', now, 4);
    expect(deps.map((d) => d.e)).toEqual([at('10:25:00')]);
  });

  it('measures the departure delay from two observations', () => {
    const tracker = new Tracker(tt);
    tracker.update([vehicle({ tripId: '100', stopId: 'U1Z1', status: STOPPED_AT, ts: at('10:06:00') })], at('10:06:00'));
    tracker.update([vehicle({ tripId: '100', stopId: 'U3Z1', status: IN_TRANSIT_TO, ts: at('10:07:00') })], at('10:07:00'));
    // left U1Z1 between 10:06 and 10:07, scheduled 10:05 -> about 90 s late
    expect(tracker.get('100', BASE, at('10:07:00'))).toMatchObject({ delay: 90, passedSeq: 2 });
  });

  it('never reports early running as negative delay', () => {
    const tracker = new Tracker(tt);
    const now = at('10:02:00');
    tracker.update([vehicle({ tripId: '100', stopId: 'U1Z1', status: STOPPED_AT, ts: now })], now);
    expect(tracker.get('100', BASE, now)!.delay).toBe(0);
  });

  it('shows trips after midnight from the previous service day', () => {
    const tracker = new Tracker(tt);
    const nextDayBase = serviceDayBase(20261009);
    const now = nextDayBase + parseGtfsTime('0:05:00');
    const deps = departures(tt, tracker, 'U1Z1', now, 4);
    expect(deps[0]).toMatchObject({ l: 'N93', m: 'B', e: BASE + parseGtfsTime('24:10:00') });
  });

  it('respects calendar exceptions', () => {
    const tracker = new Tracker(tt);
    const christmas = serviceDayBase(20261225);
    const deps = departures(tt, tracker, 'U1Z2', christmas + parseGtfsTime('9:55:00'), 4);
    // nothing on 25 Dec; the extended window finds the next day
    expect(deps.every((d) => d.e > christmas + 86400)).toBe(true);
  });
});

describe('HTTP API', () => {
  const feed = { refresh: async () => {}, status: () => ({ feedTimestamp: 0, lastCheck: 0 }) };

  it('returns the closest platform and its departures in one call', async () => {
    const app = buildApp({ timetable: tt, tracker: new Tracker(tt), feed, now: () => at('9:58:00') });
    const res = await app.inject('/v1/home?lat=49.1981&lon=16.6061&n=2');
    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(body).toMatchObject({ stop: 'Česká', p: 'U1Z1', opp: 'U1Z2', d: 0 });
    expect(body.dep).toHaveLength(2);
    expect(body.pl.map((p: { id: string }) => p.id)).toEqual(['U1Z1', 'U1Z2']);
  });

  it('requires the API key when one is configured, except for health', async () => {
    const app = buildApp({ timetable: tt, tracker: new Tracker(tt), feed, apiKey: 'secret' });
    expect((await app.inject('/v1/nearby?lat=49.2&lon=16.6')).statusCode).toBe(401);
    expect((await app.inject({ url: '/v1/nearby?lat=49.2&lon=16.6', headers: { 'x-api-key': 'secret' } })).statusCode).toBe(200);
    expect((await app.inject('/v1/health')).statusCode).toBe(200);
  });

  it('rejects missing coordinates and unknown platforms', async () => {
    const app = buildApp({ timetable: tt, tracker: new Tracker(tt), feed });
    expect((await app.inject('/v1/nearby')).statusCode).toBe(400);
    expect((await app.inject('/v1/departures?platform=nope')).statusCode).toBe(404);
  });

  it('returns vehicles relative to the user', async () => {
    const tracker = new Tracker(tt);
    const now = at('10:03:00');
    tracker.update([vehicle({ tripId: '100', stopId: 'U1Z1', status: IN_TRANSIT_TO, ts: now, lat: 49.199, lon: 16.606 })], now);
    const app = buildApp({ timetable: tt, tracker, feed, now: () => now });
    const body = (await app.inject('/v1/vehicles?lat=49.198&lon=16.606&r=500')).json();
    expect(body.v).toEqual([{ l: '4', m: 'T', dx: 0, dy: 111, b: 0, dl: 0, a: 0 }]);
  });
});

function vehicle(v: Partial<RtVehicle> & { ts: number }): RtVehicle {
  return { vehicleId: '1', status: IN_TRANSIT_TO, lat: 49.2, lon: 16.6, ...v };
}
