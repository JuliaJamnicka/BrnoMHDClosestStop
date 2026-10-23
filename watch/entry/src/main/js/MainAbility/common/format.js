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

/**
 * "teď"/"now" under a minute, "N min" under 60 min, else the clock time "HH:MM"; always the clock time
 * when [clock] (the phone's departure time setting, "tf":1).
 */
export function timeLabel(expected, now, nowWord, clock) {
  const seconds = expected - now;
  if (clock) {
    const t = new Date(expected * 1000);
    return { big: pad(t.getHours()) + ':' + pad(t.getMinutes()), unit: '' };
  }
  // under a minute it is "now", never "0 min"
  if (seconds < 60) return { big: nowWord, unit: '' };
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

/** [hex] at half brightness, as the design dims stale rows (lite wearables have no opacity). */
export function dim(hex) {
  let out = '#';
  for (let i = 1; i < 7; i += 2) out += pad2hex(Math.round(parseInt(hex.substr(i, 2), 16) / 2));
  return out;
}

function pad2hex(n) {
  return (n < 16 ? '0' : '') + n.toString(16);
}

// Row layout of pages/home/home.css: lite wearables have no flex-grow, so the headsign and time widths
// are computed to keep the times right-aligned whichever optional parts a row has.
const HEADSIGN_MAX = 196; // 372 row - 58 badge - 10 gap - 104 time with unit - 4 gap
const DELAY_W = 46; // .delay incl. margin
const LIVE_W = 24; // .live incl. margin
const UNIT_W = 32; // .unit incl. margin

const UNIT_IMAGES = { normal: '/common/images/min.png', late: '/common/images/min-late.png', stale: '/common/images/min-stale.png' };

/**
 * Rows of a "dep" reply ready for the home page; [stale] data is drawn at half brightness. "min" is an
 * image (unitImage, '' without a unit): lite wearables have no font size as small as the design's.
 */
export function departureRows(reply, now, nowWord, stale) {
  const rows = [];
  const list = reply.x || [];
  const clock = reply.tf === 1;
  for (let i = 0; i < list.length; i++) {
    const r = list[i];
    const label = timeLabel(r[3], now, nowWord, clock);
    const badge = badgeStyle(r[1], 38);
    const colour = timeColour(r[3] - now < 60, r[4]);
    const delay = clock ? delayLabel(r[4]) : '';
    let unitImage = '';
    if (label.unit) unitImage = stale ? UNIT_IMAGES.stale : isLate(r[4]) ? UNIT_IMAGES.late : UNIT_IMAGES.normal;
    const live = r[5] === 1 && !stale;
    rows.push({
      line: r[0],
      badgeColour: stale ? dim(badge.colour) : badge.colour,
      badgeRadius: badge.radius,
      headsign: r[2],
      headsignWidth: HEADSIGN_MAX - (delay ? DELAY_W : 0) - (live ? LIVE_W : 0),
      headsignColour: stale ? '#797979' : '#f2f2f2',
      big: label.big,
      timeWidth: unitImage ? 104 - UNIT_W : 104,
      unit: label.unit,
      unitImage: unitImage,
      hasUnit: unitImage !== '', // lite wearable `if` takes booleans, not truthy strings
      // minutes already include the delay (amber from 2 min); next to a clock time it is shown
      delay: delay,
      hasDelay: delay !== '',
      timeColour: stale ? dim(colour) : colour,
      live: live,
    });
  }
  return rows;
}

// Mode chips of the stop list, pre-drawn (tools/images.mjs) because their text is smaller than any
// lite wearable font; width in px. At most three fit under a stop name.
const CHIPS = {
  T: { src: 'chip-T', w: 44 },
  B: { src: 'chip-B', w: 35 },
  V: { src: { cs: 'chip-V-cs', en: 'chip-V-en' }, w: { cs: 39, en: 44 } },
  L: { src: { cs: 'chip-L-cs', en: 'chip-L-en' }, w: { cs: 35, en: 41 } },
};

/** Up to three chips for the modes string of a "near" reply ("TB"): [{ src, w }]. */
export function modeChips(modes, lang) {
  const chips = [];
  const l = lang === 'en' ? 'en' : 'cs';
  for (let i = 0; i < modes.length && chips.length < 3; i++) {
    const c = CHIPS[modes.charAt(i)];
    if (!c) continue;
    const src = typeof c.src === 'string' ? c.src : c.src[l];
    const w = typeof c.w === 'number' ? c.w : c.w[l];
    chips.push({ src: '/common/images/' + src + '.png', w: w });
  }
  return chips;
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

// Lite wearable <text> wraps at any character, splitting words; break at spaces ourselves
// and render one <text> per line. maxChars is a conservative fit for the line width.
export function wrapLines(text, maxChars) {
  const lines = [];
  let line = '';
  const words = String(text).split(' ');
  for (let i = 0; i < words.length; i++) {
    const word = words[i];
    if (line && line.length + 1 + word.length > maxChars) {
      lines.push(line);
      line = word;
    } else {
      line = line ? line + ' ' + word : word;
    }
  }
  if (line) lines.push(line);
  return lines;
}

// P2pClient.ping() codes (wearengine.js ErrorCode): 205 the phone app is installed (the watch cannot
// tell whether it runs), 204 not installed, anything else (203) no usable link to the phone.
export function pingOutcome(code) {
  if (code === 205) return 'send';
  if (code === 204) return 'noapp';
  return 'phone';
}
