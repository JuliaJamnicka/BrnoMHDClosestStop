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

/**
 * Whether [reply] answers a request of kind [requestKind] ("home" is answered by "dep"); an error
 * answers any. A late reply to an earlier page's request must not be taken for this page's.
 */
export function replyMatches(requestKind, reply) {
  if (!reply) return false;
  if (reply.c === 'err') return true;
  return reply.c === (requestKind === 'home' ? 'dep' : requestKind);
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

// Row layout of pages/index/index.css: lite wearables have no flex-grow and no auto-width text, so
// the time gets about the width of its text (the live dot then sits right beside it) and the
// headsign takes what is left.
const ROW_W = 304; // 372 row - 58 badge - 10 gap
const DELAY_W = 46; // .delay incl. margin
const LIVE_W = 16; // .live: 10 px dot + 6 px margin
const TIME_GAP = 4; // .time margin
const UNIT_W = 32; // .unit incl. margin
const TIME_MAX = 104;

/**
 * Approximate width in px of [text] in the watch's system font at [size] px, for boxes that should
 * fit their text (narrow and wide letters differ; digits are about 0.56 em).
 */
export function textWidth(text, size) {
  let em = 0;
  const s = String(text);
  for (let i = 0; i < s.length; i++) {
    const c = s.charAt(i);
    if (' .,:;\'!|iíjlľtťfr'.indexOf(c) >= 0) em += 0.3;
    else if ('mwMWŠŽ'.indexOf(c) >= 0) em += 0.85;
    else if (c >= 'A' && c <= 'Z') em += 0.65;
    else em += 0.56;
  }
  return Math.ceil(em * size);
}

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
    const badge = badgeStyle(r[1], 44); // pages/index .badge height
    const colour = timeColour(r[3] - now < 60, r[4]);
    const delay = clock ? delayLabel(r[4]) : '';
    let unitImage = '';
    if (label.unit) unitImage = stale ? UNIT_IMAGES.stale : isLate(r[4]) ? UNIT_IMAGES.late : UNIT_IMAGES.normal;
    const live = r[5] === 1 && !stale;
    const timeWidth = Math.min(TIME_MAX, textWidth(label.big, 30) + 2);
    rows.push({
      line: r[0],
      badgeColour: stale ? dim(badge.colour) : badge.colour,
      badgeRadius: badge.radius,
      headsign: r[2],
      headsignWidth: ROW_W - (delay ? DELAY_W : 0) - (live ? LIVE_W : 0) - TIME_GAP - timeWidth - (unitImage ? UNIT_W : 0),
      headsignColour: stale ? '#797979' : '#f2f2f2',
      big: label.big,
      timeWidth: timeWidth,
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
    const badge = badgeStyle(v[1], 38); // pages/radar .badge height
    // as wide as the line number ("301" does not fit the 48 px of a short one)
    const width = Math.max(48, textWidth(v[0], 30) + 12);
    const marker = {
      line: v[0],
      badgeColour: badge.colour,
      badgeRadius: badge.radius,
      width: width,
      left: Math.round(x - width / 2),
      top: Math.round(y - 19),
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

/**
 * A stop's name on the radar: up to five letters, with a dot when the name is longer
 * ("Konečného náměstí" -> "Koneč.", "Brno-Královo Pole" -> "Brno.", "Úvoz" stays).
 */
export function shortStopName(name) {
  const plain = String(name).split('…').join('').trim();
  if (plain.length <= 5) return plain;
  let cut = plain.substring(0, 5);
  // no space, hyphen or comma right before the dot
  while (cut.length > 0 && ' -,.'.indexOf(cut.charAt(cut.length - 1)) >= 0) cut = cut.substring(0, cut.length - 1);
  return cut + '.';
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

/**
 * Text of a script error for the screen: the watch shows no logs, so a page that fails while
 * drawing a reply shows this instead of staying on its loading text.
 */
export function describeError(e) {
  const text = e && e.message ? e.message : String(e);
  return 'JS: ' + text;
}
