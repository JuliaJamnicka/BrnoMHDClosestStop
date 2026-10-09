# Fčil brand

"Fčil" is Brno / Moravian colloquial Czech for "now": what leaves from here right now.
Design canvas: https://claude.ai/artifact/GRXkiYqGNfvwqb4K8ouFAm (private).

The blue and red recall the colours of the Brno transport company (DPMB) without copying its
logo or exact shades, so the app is not mistaken for an official one.

| File | Use |
|------|-----|
| `fcil-wordmark.svg` | Lowercase wordmark on light backgrounds; the háček is the only red element |
| `fcil-wordmark-dark.svg` | The same on dark backgrounds |
| `fcil-icon.svg`, `fcil-icon-512.png` | App icon: a watch-face ring with the háček at 12 o'clock ("now") and a white c |

The letters are geometric strokes, not a font, so they stay sharp at watch size.

| Colour | Hex | Where |
|--------|-----|-------|
| Blue | `#1A4FA3` | Wordmark on light |
| Red | `#E3242B` | Háček on light |
| Night | `#0E1B33` | Icon background, dark surfaces |
| Ring blue | `#3D7BD9` | Icon ring (lighter, for contrast on night) |
| Bright red | `#F0373E` | Háček on dark |

Copies in the apps: `android/app/src/main/res/drawable/ic_launcher_foreground.xml`,
`ic_notification.xml`, `ic_wordmark.xml` (+ `drawable-night/`), and the watch icons in
`watch/entry/src/main/resources/base/media/` (rendered from `fcil-icon.svg`).
