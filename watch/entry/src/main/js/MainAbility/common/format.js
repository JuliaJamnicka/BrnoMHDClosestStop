// Pure helpers shared by the pages; unit tested in watch/test (no watch APIs here).

/** Parses a phone reply; the SDK may hand over a string or an object carrying it. */
export function parseReply(raw) {
  try {
    const text = typeof raw === 'string' ? raw : raw && (raw.data || raw.message || raw.description);
    const obj = typeof text === 'string' ? JSON.parse(text) : raw;
    return obj && typeof obj.c === 'string' ? obj : null;
  } catch (e) {
    return null;
  }
}

function pad(n) {
  return n < 10 ? '0' + n : '' + n;
}

/** "teď"/"now" under 30 s, "N min" under 60 min, else the clock time "HH:MM". */
export function timeLabel(expected, now, nowWord) {
  const seconds = expected - now;
  if (seconds < 30) return { big: nowWord, unit: '' };
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return { big: '' + minutes, unit: 'min' };
  const d = new Date(expected * 1000);
  return { big: pad(d.getHours()) + ':' + pad(d.getMinutes()), unit: '' };
}

/** "+N" from 1 minute late; amber from 2 minutes (docs/SPEC.md 7.3). */
export function delayLabel(delayMinutes) {
  return delayMinutes >= 1 ? '+' + delayMinutes : '';
}

export function isLate(delayMinutes) {
  return delayMinutes >= 2;
}

/** CSS class of a line badge: tram, bus (incl. trolleybus) and train look different. */
export function badgeClass(mode) {
  if (mode === 'T') return 'badge badge-tram';
  if (mode === 'V') return 'badge badge-train';
  return 'badge badge-bus';
}

/** Rows of a "dep" reply ready for the home page. */
export function departureRows(reply, now, nowWord) {
  const rows = [];
  const list = reply.x || [];
  for (let i = 0; i < list.length; i++) {
    const r = list[i];
    const label = timeLabel(r[3], now, nowWord);
    rows.push({
      line: r[0],
      badge: badgeClass(r[1]),
      headsign: r[2],
      big: label.big,
      unit: label.unit,
      delay: delayLabel(r[4]),
      timeClass: label.big === nowWord ? 'time time-now' : isLate(r[4]) ? 'time time-late' : 'time',
      live: r[5] === 1,
    });
  }
  return rows;
}

/**
 * Radar marker positions in a size x size round screen, north up, user in the centre.
 * radiusPx is the screen distance of rangeM metres (the outer ring).
 */
export function radarMarkers(vehicles, size, radiusPx, rangeM) {
  const scale = radiusPx / rangeM;
  const centre = size / 2;
  const markers = [];
  for (let i = 0; i < vehicles.length; i++) {
    const v = vehicles[i];
    const x = centre + v[2] * scale;
    const y = centre - v[3] * scale;
    const dist = Math.sqrt(v[2] * v[2] + v[3] * v[3]);
    if (dist > rangeM) continue;
    markers.push({ line: v[0], badge: badgeClass(v[1]), left: Math.round(x - 24), top: Math.round(y - 16), late: isLate(v[5]) });
  }
  return markers;
}

/** "85 m" / "1,2 km" */
export function distanceLabel(metres) {
  if (metres === undefined || metres === null) return '';
  if (metres < 1000) return metres + ' m';
  return (Math.round(metres / 100) / 10).toString().replace('.', ',') + ' km';
}
