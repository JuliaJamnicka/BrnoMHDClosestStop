// Time helpers for the Europe/Prague timezone used by the IDS JMK feed.
// GTFS times are seconds since "noon minus 12 hours" of the service day, which
// equals local midnight except on DST change days.

export const TIMEZONE = 'Europe/Prague';

const partsFormat = new Intl.DateTimeFormat('en-CA', {
  timeZone: TIMEZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
});

interface LocalParts {
  year: number;
  month: number;
  day: number;
  hour: number;
  minute: number;
  second: number;
}

function localParts(epochMs: number): LocalParts {
  const p: Record<string, number> = {};
  for (const part of partsFormat.formatToParts(new Date(epochMs))) {
    if (part.type !== 'literal') p[part.type] = Number(part.value);
  }
  return { year: p.year, month: p.month, day: p.day, hour: p.hour, minute: p.minute, second: p.second };
}

/** Offset of local Prague time from UTC at the given instant, in milliseconds. */
function offsetMs(epochMs: number): number {
  const p = localParts(epochMs);
  const asUtc = Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second);
  return asUtc - Math.floor(epochMs / 1000) * 1000;
}

/** Service date (YYYYMMDD as number) of the local calendar day containing the instant. */
export function localDate(epochSec: number): number {
  const p = localParts(epochSec * 1000);
  return p.year * 10000 + p.month * 100 + p.day;
}

/** Adds days to a YYYYMMDD date. */
export function addDays(date: number, days: number): number {
  const y = Math.floor(date / 10000);
  const m = Math.floor((date % 10000) / 100);
  const d = date % 100;
  const t = new Date(Date.UTC(y, m - 1, d + days));
  return t.getUTCFullYear() * 10000 + (t.getUTCMonth() + 1) * 100 + t.getUTCDate();
}

/** Day of week of a YYYYMMDD date, 0 = Monday ... 6 = Sunday. */
export function weekday(date: number): number {
  const y = Math.floor(date / 10000);
  const m = Math.floor((date % 10000) / 100);
  const d = date % 100;
  return (new Date(Date.UTC(y, m - 1, d)).getUTCDay() + 6) % 7;
}

/** Epoch seconds that GTFS time 0:00:00 refers to on the given service date. */
export function serviceDayBase(date: number): number {
  const y = Math.floor(date / 10000);
  const m = Math.floor((date % 10000) / 100);
  const d = date % 100;
  const noonGuess = Date.UTC(y, m - 1, d, 12);
  const noon = noonGuess - offsetMs(noonGuess);
  return Math.round(noon / 1000) - 12 * 3600;
}

/** Parses a GTFS time "H:MM:SS" (hours may exceed 23) into seconds. */
export function parseGtfsTime(value: string): number {
  const [h, m, s] = value.split(':');
  return Number(h) * 3600 + Number(m) * 60 + Number(s);
}
