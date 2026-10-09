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

const BADGE_COLOURS = { T: '#c8262c', V: '#1d5fd1' };
const BUS_COLOUR = '#1f7a4d';

/**
 * Line badge look for a badge [height] px high: tram rounded, bus (incl. trolleybus) a pill, train
 * square. Lite wearables cannot bind `class`, so pages bind these as inline style values.
 */
export function badgeStyle(mode, height) {
  if (mode === 'T') return { colour: BADGE_COLOURS.T, radius: Math.round(height * 0.21) };
  if (mode === 'V') return { colour: BADGE_COLOURS.V, radius: 2 };
  return { colour: BUS_COLOUR, radius: Math.round(height / 2) };
}

/** Departure time colour: "now" in the háček red, amber from 2 minutes late, else white. */
export function timeColour(isNow, delayMinutes) {
  if (isNow) return '#f0373e';
  return isLate(delayMinutes) ? '#ffb020' : '#ffffff';
}

/** Rows of a "dep" reply ready for the home page. */
export function departureRows(reply, now, nowWord) {
  const rows = [];
  const list = reply.x || [];
  for (let i = 0; i < list.length; i++) {
    const r = list[i];
    const label = timeLabel(r[3], now, nowWord);
    const badge = badgeStyle(r[1], 38);
    rows.push({
      line: r[0],
      badgeColour: badge.colour,
      badgeRadius: badge.radius,
      headsign: r[2],
      big: label.big,
      unit: label.unit,
      delay: delayLabel(r[4]),
      timeColour: timeColour(label.big === nowWord, r[4]),
      live: r[5] === 1,
    });
  }
  return rows;
}

const ARROW_OFFSET = 36;

/** One of 8 pre-drawn arrow images (lite wearables cannot rotate images); null when the heading is unknown. */
export function arrowFor(heading) {
  if (heading === undefined || heading === null || heading < 0) return null;
  const sector = Math.round(heading / 45) % 8;
  return { src: '/common/images/arrow' + sector + '.png', angle: sector * 45 };
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
    const badge = badgeStyle(v[1], 32);
    const marker = {
      line: v[0],
      badgeColour: badge.colour,
      badgeRadius: badge.radius,
      left: Math.round(x - 24),
      top: Math.round(y - 16),
      late: isLate(v[5]),
    };
    const arrow = arrowFor(v[4]);
    if (arrow) {
      // a small arrow just ahead of the badge, in the direction of travel
      const rad = (arrow.angle * Math.PI) / 180;
      marker.arrow = arrow.src;
      marker.arrowLeft = Math.round(x + Math.sin(rad) * ARROW_OFFSET - 12);
      marker.arrowTop = Math.round(y - Math.cos(rad) * ARROW_OFFSET - 12);
    }
    markers.push(marker);
  }
  return markers;
}

/** "85 m" / "1,2 km" */
export function distanceLabel(metres) {
  if (metres === undefined || metres === null) return '';
  if (metres < 1000) return metres + ' m';
  return (Math.round(metres / 100) / 10).toString().replace('.', ',') + ' km';
}
