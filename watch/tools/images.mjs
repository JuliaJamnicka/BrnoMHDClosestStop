// Draws the small watch images the lite wearable cannot render itself (text under 30 px, icons) from
// the design canvas, in Barlow Semi Condensed like the canvas. Needs Playwright with Chromium:
//   node watch/tools/images.mjs
// Writes into entry/src/main/js/MainAbility/common/images/.
import { chromium } from 'playwright';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const OUT = path.join(path.dirname(fileURLToPath(import.meta.url)), '../entry/src/main/js/MainAbility/common/images');

const svg = (w, h, view, body) =>
  `<svg width="${w}" height="${h}" viewBox="${view}" xmlns="http://www.w3.org/2000/svg">${body}</svg>`;

// "min" after the minutes, white and amber (late) like the time beside it, grey for stale data
const unit = (colour) => `<span style="display: block; width: 28px; height: 20px; font-size: 18px; font-weight: 500; line-height: 20px; color: ${colour}">min</span>`;

// mode chips of the stop list (Stops board); boats share the bus look, as in the phone app
// fixed widths, which common/format.js (CHIPS) gives the images on the watch
const chip = (colour, radius, label, width) =>
  `<span style="display: inline-flex; align-items: center; justify-content: center; width: ${width}px; height: 22px; padding: 0 5px; box-sizing: border-box; border-radius: ${radius}px; background: ${colour}; color: #FFFFFF; font-size: 16px; font-weight: 700">${label}</span>`;
const TRAM = ['#C8262C', 5];
const BUS = ['#1F7A4D', 11];
const TRAIN = ['#1D5FD1', 1];

const IMAGES = {
  'min.png': unit('#FFFFFF'),
  'min-late.png': unit('#FFB020'),
  'min-stale.png': unit('#808080'),
  // live data: the signal mark of the Departures board
  'live.png': svg(18, 18, '0 0 24 24',
    '<g fill="none" stroke="#6EA0EE" stroke-width="3" stroke-linecap="round"><path d="M4 11a9 9 0 0 1 9 9"/><path d="M4 4a16 16 0 0 1 16 16"/></g><circle cx="5" cy="19" r="2" fill="#6EA0EE"/>'),
  // location mark before the distance
  'loc.png': svg(24, 24, '0 0 24 24',
    '<g fill="none" stroke="#8D99B3" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 21s-6-5.5-6-11a6 6 0 0 1 12 0c0 5.5-6 11-6 11z"/><circle cx="12" cy="10" r="2"/></g>'),
  // pinned stop: the push pin of the Departures board
  'pin.png': svg(28, 28, '0 0 24 24',
    '<g fill="none" stroke="#FFB020" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M12 17v5"/><path d="M9 3h6l-1 6 3 4H7l3-4z"/></g>'),
  'chip-T.png': chip(...TRAM, 'Tram', 44),
  'chip-B.png': chip(...BUS, 'Bus', 35),
  'chip-V-cs.png': chip(...TRAIN, 'Vlak', 39),
  'chip-V-en.png': chip(...TRAIN, 'Train', 44),
  'chip-L-cs.png': chip(...BUS, 'Loď', 35),
  'chip-L-en.png': chip(...BUS, 'Boat', 41),
};

const html = `<!doctype html><html><head><meta charset="utf-8">
<link href="https://fonts.googleapis.com/css2?family=Barlow+Semi+Condensed:wght@500;700&display=swap" rel="stylesheet">
<style>body { margin: 0; background: transparent; font-family: 'Barlow Semi Condensed', sans-serif; }
.item { display: flex; width: max-content; margin: 4px; } .item > span { line-height: normal; }</style></head><body>
${Object.entries(IMAGES).map(([name, body]) => `<div class="item" data-name="${name}">${body}</div>`).join('\n')}
</body></html>`;

const browser = await chromium.launch(process.env.CHROMIUM ? { executablePath: process.env.CHROMIUM } : {});
const page = await browser.newPage({ deviceScaleFactor: 1 });
await page.setContent(html, { waitUntil: 'networkidle' });
await page.evaluate(() => document.fonts.ready);
for (const el of await page.$$('.item')) {
  const name = await el.getAttribute('data-name');
  await el.screenshot({ path: path.join(OUT, name), omitBackground: true });
  console.log(name);
}
await browser.close();
