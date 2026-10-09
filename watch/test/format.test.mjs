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
const { parseReply, timeLabel, departureRows, radarMarkers, distanceLabel, badgeClass } = await import(pathToFileURL(path.join(tmp, 'format.mjs')));
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
  assert.deepEqual(timeLabel(1400, 1000, 'teď'), { big: '6', unit: 'min' });
  assert.match(timeLabel(1000 + 3 * 3600, 1000, 'teď').big, /^\d\d:\d\d$/);
});

test('builds departure rows with delays, live dots and badge styles', () => {
  const rows = departureRows(parseReply(reply), 1000, 'teď');
  assert.equal(rows.length, 3);
  assert.deepEqual(
    rows.map((r) => [r.line, r.badge, r.delay, r.timeClass, r.live]),
    [
      ['6', 'badge badge-tram', '', 'time', true],
      ['4', 'badge badge-tram', '+3', 'time time-late', true],
      ['N93', 'badge badge-bus', '+1', 'time', false],
    ],
  );
  assert.equal(badgeClass('V'), 'badge badge-train');
});

test('places radar markers north-up around the centre and drops far vehicles', () => {
  const markers = radarMarkers([['4', 'T', 0, 400, 0, 0], ['1', 'T', 900, 0, 0, 0], ['67', 'B', -400, 0, 0, 2]], 466, 200, 800);
  assert.deepEqual(markers.map((m) => [m.line, m.left, m.top, m.late]), [
    ['4', 233 - 24, 233 - 100 - 16, false],
    ['67', 233 - 100 - 24, 233 - 16, true],
  ]);
});

test('labels distances and texts in both languages', () => {
  assert.equal(distanceLabel(85), '85 m');
  assert.equal(distanceLabel(1240), '1,2 km');
  assert.equal(t('en', 'stale', { m: 2 }), 'Data 2 min old');
  assert.equal(t('cs', 'err_phone'), 'Telefon není připojen');
  assert.equal(t('xx', 'now'), 'teď');
});
