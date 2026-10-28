// Unit tests for the watch's pure helpers: node --test watch/test
import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

// The watch sources are plain .js ES modules without a package.json; load them as .mjs copies.
const here = path.dirname(fileURLToPath(import.meta.url));
const common = path.join(here, '../entry/src/main/js/MainAbility/common');
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'watch-'));
for (const f of ['format.js', 'strings.js']) fs.copyFileSync(path.join(common, f), path.join(tmp, f.replace('.js', '.mjs')));
const { parseReply, timeLabel, departureRows, radarMarkers, distanceLabel, badgeStyle, timeColour, arrowFor, wrapLines, pingOutcome, dim, modeChips, shortStopName, textWidth } = await import(pathToFileURL(path.join(tmp, 'format.mjs')));
const { t } = await import(pathToFileURL(path.join(tmp, 'strings.mjs')));

// A reply exactly as android/.../WatchProtocol.kt produces it (non-ASCII escaped).
const reply = '{"c":"dep","lg":"cs","t":1000,"g":"U1073N2860","s":"\\u010cesk\\u00e1","p":"U1073Z1","o":"U1073Z2",' +
  '"r":"N\\u00e1m\\u011bst\\u00ed M\\u00edru","d":20,"x":[["6","T","Star\\u00fd L\\u00edskovec, sm.",1010,0,1],["4","T","N\\u00e1m\\u011bst\\u00ed M\\u00edru",1400,3,1],["N93","B","\\u00dat\\u011bchov",9000,1,0]]}';

test('parses phone replies given as a string or as an SDK object', () => {
  assert.equal(parseReply(reply).s, 'Česká');
  assert.equal(parseReply({ data: reply }).c, 'dep');
  assert.equal(parseReply('not json'), null);
  assert.equal(parseReply('{"x":1}'), null);
});

test('formats times like the design: now, minutes, clock time', () => {
  assert.deepEqual(timeLabel(1010, 1000, 'teď'), { big: 'teď', unit: '' });
  // 30-59 s used to read "0 min"
  assert.deepEqual(timeLabel(1045, 1000, 'teď'), { big: 'teď', unit: '' });
  assert.deepEqual(timeLabel(1060, 1000, 'teď'), { big: '1', unit: 'min' });
  assert.deepEqual(timeLabel(1400, 1000, 'teď'), { big: '6', unit: 'min' });
  assert.match(timeLabel(1000 + 3 * 3600, 1000, 'teď').big, /^\d\d:\d\d$/);
  // the clock setting: always the clock time, also for "now"
  assert.match(timeLabel(1010, 1000, 'teď', true).big, /^\d\d:\d\d$/);
  assert.equal(timeLabel(1400, 1000, 'teď', true).unit, '');
});

test('builds departure rows with delays, live dots, badge styles and "now" in red', () => {
  const rows = departureRows(parseReply(reply), 1000, 'teď');
  assert.equal(rows.length, 3);
  assert.deepEqual(
    rows.map((r) => [r.line, r.badgeColour, r.badgeRadius, r.delay, r.timeColour, r.live]),
    [
      ['6', '#c8262c', 9, '', '#f0373e', true],
      // with minutes the delay is only the amber colour (the minutes include it)
      ['4', '#c8262c', 9, '', '#ffb020', true],
      ['N93', '#1f7a4d', 22, '', '#ffffff', false],
    ],
  );
  // "tf":1 (clock setting): clock times, the departing one still in red
  const clockRows = departureRows(Object.assign({}, parseReply(reply), { tf: 1 }), 1000, 'teď');
  assert.ok(clockRows.every((r) => /^\d\d:\d\d$/.test(r.big) && r.unit === ''));
  assert.equal(clockRows[0].timeColour, '#f0373e');
  assert.deepEqual(clockRows.map((r) => r.delay), ['', '+3', '+1']);
  assert.deepEqual(badgeStyle('V', 38), { colour: '#1d5fd1', radius: 2 });
  assert.equal(timeColour(false, 0), '#ffffff');
});

test('draws "min" as an image and keeps the times right-aligned', () => {
  const rows = departureRows(parseReply(reply), 1000, 'teď');
  assert.deepEqual(rows.map((r) => [r.hasUnit, r.unitImage]), [
    [false, ''],
    [true, '/common/images/min-late.png'],
    // over an hour away: a clock time, no unit
    [false, ''],
  ]);
  // headsign + optional parts + time always fill the same row width (372 - badge 58 - gap 10)
  const width = (r) => r.headsignWidth + (r.hasDelay ? 46 : 0) + (r.live ? 16 : 0) + 4 + r.timeWidth + (r.hasUnit ? 32 : 0);
  const clockRows = departureRows(Object.assign({}, parseReply(reply), { tf: 1 }), 1000, 'teď');
  assert.deepEqual(rows.concat(clockRows).map(width), [304, 304, 304, 304, 304, 304]);
  // the time's box is about as wide as its text, so the live dot sits right beside it
  assert.ok(rows[1].timeWidth < 40 && clockRows[0].timeWidth > rows[1].timeWidth);
});

test('dims stale rows to half brightness without live marks', () => {
  const rows = departureRows(parseReply(reply), 1000, 'teď', true);
  assert.deepEqual(rows.map((r) => [r.badgeColour, r.timeColour, r.live]), [
    ['#641316', '#781c1f', false],
    ['#641316', '#805810', false],
    ['#103d27', '#808080', false],
  ]);
  assert.equal(rows[1].unitImage, '/common/images/min-stale.png');
  assert.equal(dim('#ffffff'), '#808080');
});

test('picks pre-drawn mode chips in the phone language', () => {
  assert.deepEqual(modeChips('TB', 'cs'), [
    { src: '/common/images/chip-T.png', w: 44 },
    { src: '/common/images/chip-B.png', w: 35 },
  ]);
  assert.deepEqual(modeChips('VL', 'en').map((c) => c.src), ['/common/images/chip-V-en.png', '/common/images/chip-L-en.png']);
  assert.equal(modeChips('TBVL', 'cs').length, 3);
  assert.deepEqual(modeChips('?', 'cs'), []);
});

test('places radar markers north-up around the centre and drops far vehicles', () => {
  const markers = radarMarkers([['4', 'T', 0, 400, 0, 0], ['1', 'T', 900, 0, 0, 0], ['67', 'B', -400, 0, 0, 2]], 466, 200, 800);
  assert.deepEqual(markers.map((m) => [m.line, m.left, m.top, m.late]), [
    ['4', 233 - 24, 233 - 100 - 19, false],
    ['67', 233 - 100 - 24, 233 - 19, true],
  ]);
});

test('adds a direction arrow ahead of each vehicle with a known heading', () => {
  const [north, unknown] = radarMarkers([['4', 'T', 0, 0, 0, 0], ['5', 'T', 0, 0, -1, 0]], 466, 200, 800);
  assert.deepEqual([north.arrow, north.arrowLeft, north.arrowTop], ['/common/images/arrow0.png', 233 - 12, 233 - 36 - 12]);
  assert.equal(unknown.arrow, undefined);
  assert.equal(arrowFor(100).src, '/common/images/arrow2.png');
  assert.equal(arrowFor(350).src, '/common/images/arrow0.png');
});

test('labels distances and texts in both languages', () => {
  assert.equal(distanceLabel(85), '85 m');
  assert.equal(distanceLabel(1240), '1,2 km');
  assert.equal(t('en', 'stale', { m: 2 }), 'Data 2 min old');
  assert.equal(t('cs', 'err_phone'), 'Telefon není připojen');
  assert.equal(t('xx', 'now'), 'teď');
});

test('wraps hints at spaces so words are never split', () => {
  assert.deepEqual(wrapLines('Zapněte Bluetooth a jednou otevřete aplikaci Fčil v telefonu.', 22),
    ['Zapněte Bluetooth a', 'jednou otevřete', 'aplikaci Fčil v', 'telefonu.']);
  assert.deepEqual(wrapLines('Krátké', 22), ['Krátké']);
});

test('maps Wear Engine ping results to send or an error to show', () => {
  assert.equal(pingOutcome(205), 'send');
  assert.equal(pingOutcome(204), 'noapp');
  assert.equal(pingOutcome(203), 'phone');
  assert.equal(pingOutcome(undefined), 'phone');
});

test('shortens stop names for the radar to five letters and a dot', () => {
  assert.equal(shortStopName('Konečného náměstí'), 'Koneč.');
  assert.equal(shortStopName('Brno-Královo Pole'), 'Brno.');
  assert.equal(shortStopName('Grohova'), 'Groho.');
  assert.equal(shortStopName('Úvoz'), 'Úvoz');
  assert.equal(shortStopName('Česká'), 'Česká');
  assert.equal(shortStopName('Technologický p…'), 'Techn.');
});

test('estimates text widths for boxes that fit their text', () => {
  assert.equal(textWidth('12', 30), Math.ceil(2 * 0.56 * 30));
  assert.ok(textWidth('Mendlovo náměstí', 38) > textWidth('Česká', 38));
});

test('widens radar badges for long line numbers', () => {
  const [short, long] = radarMarkers([['4', 'T', 0, 0, -1, 0], ['301', 'B', 0, 0, -1, 0]], 466, 200, 800);
  assert.equal(short.width, 48);
  assert.ok(long.width > 48 && long.width >= textWidth('301', 30) + 12);
  assert.equal(long.left, Math.round(233 - long.width / 2));
});
