# Watch app (Huawei Watch GT 5, lite wearable)

JavaScript (FA model) app for HarmonyOS lite wearables, built in **DevEco Studio**. It shows the
next departures from the nearest stop, gets them from the phone app over Huawei Wear Engine and
follows the design canvas (docs/SPEC.md 7.3).

| Page | What it shows | Gestures |
|------|---------------|----------|
| `pages/index` (home) | Stop, direction, 3-4 departures (`+N` delay, blue live dot, "now" in red, amber when 2+ min late), distance | ⇄ reverse, tap stop name: platform picker, ≡ stop list, ◎ radar; swipe left: radar, up: stops, right: close |
| `pages/stops` | Nearest stops; first row "Nearest (automatic)" | tap to pin, swipe right: back |
| `pages/platforms` | Platforms of the current stop with lines and direction | tap to pin, swipe right: back |
| `pages/radar` | Vehicles within 800 m around you, north up, an arrow for each one's direction of travel | swipe right: back |

When one of the user's stop lists (made in the phone app) is nearby, the home page shows the
list's merged departures with each row's stop name instead of the headsign. Pinned stops show an amber pin; tapping it goes back to following the nearest stop. Texts are
Czech or English, as chosen in the phone app (every reply carries `lg`).

```
entry/src/main/
  config.json                 bundle io.github.juliajamnicka.fcil.watch, pages, phone pairing (supportLists)
  js/MainAbility/
    app.js
    common/config.js          phone package + fingerprint, refresh intervals
    common/link.js            request/reply over Wear Engine P2P
    common/format.js          pure helpers (unit tested in test/)
    common/strings.js         Czech / English texts
    common/state.js           state shared between pages
    common/wearengine.js      Huawei's lite-wearable Wear Engine SDK 5.0.2.306 (Apache-2.0, unchanged)
    pages/...
```

Protocol (same as `android/.../wear/WatchProtocol.kt`): the watch sends `{"c":"home"}`,
`{"c":"dep","p":…}`, `{"c":"pl","p":…}`, `{"c":"near"}` or `{"c":"veh"}`; the phone answers with
compact JSON, non-ASCII escaped as `\uXXXX`, or `{"c":"err","e":"phone|noloc|net|srv|auth|req"}`.
Before the first request (and again after a failure) the watch asks Wear Engine `P2pClient.ping()`
whether the phone app is installed. Watch-side errors: `noapp` (ping: not installed), `closed`
(installed, but the message failed or got no reply within 8 s: the app is not running), `phone`
(no link to the phone at all).

## Setup (once)

1. Install **DevEco Studio** (Windows or macOS) and open this `watch/` folder as a project.
2. Watch developer mode: on the watch, Settings > About, tap the software version repeatedly until
   developer options appear; enable debugging. (Menu names differ between firmware versions.)
3. In AppGallery Connect, in the same project as the phone app, add an app of type
   **HarmonyOS / lite wearable** with package name `io.github.juliajamnicka.fcil.watch`, register
   the watch's UDID, and create the debug certificate (.cer) and profile (.p7b) from a CSR made in
   DevEco Studio. Then add them under *File > Project Structure > Signing Configs*.
   Never commit `*.p12`, `*.cer`, `*.p7b` or `*.csr` (they are in `.gitignore`).
4. Pairing with the phone app (both sides must name each other):
   - **Watch side:** `PHONE_FINGERPRINT` in `common/config.js` and `supportLists` in `config.json`
     hold the phone app's SHA-256 signing fingerprint, upper case without colons (set for the
     current key). After a new phone key, read it from a signed APK with
     `apksigner verify --print-certs app-debug.apk` and register it in AppGallery Connect too.
   - **Phone side:** compute this watch app's fingerprint from its certificate
     ([Huawei guide](https://developer.huawei.com/consumer/en/doc/connectivity-guides/signature-0000001053969657))
     and add it as the GitHub repository **variable** `WATCH_FINGERPRINT`; the next phone app build uses it.
5. Build and install from DevEco Studio onto the watch (or via the HAP file and the phone, as
   described in Huawei's lite-wearable guides).

## First page must be pages/index/index

The GT 5 installs an app whose first page has another name, but freezes the app list when it is
opened (no error, no log). The home page is therefore `pages/index/index`. Found by comparing a
working DevEco template build with ours: they differed only in the first page's name.

## Reserved page method names

A page method called `render` is replaced by the compiled template, and calling it throws a bare
"TypeError" on the watch (the previewer did not show it). Pages show such script errors on screen
as `JS: TypeError @step`.

## First test on the GT 5

A developer has reported "Installation failed: 40" when installing a lite-wearable app on a GT 5.
Before anything else, install this app once: even without the phone link it must open and show
an error screen ("Telefon není připojen" or "Fčil není v telefonu") within 8 seconds. That proves installing works.

Things to check on the real watch, because lite-wearable support differs between devices and
could not be tested without one:

- **Font sizes:** only 30 px and 38 px are used, the sizes lite wearables are known to support.
- **Event arguments:** list items use `onclick="pick($idx)"`.
- **Bound inline styles:** radar markers use `style="left: {{…}}px"`.
- **Circular clipping:** rows near the bottom of the round screen.
- **Images in list items:** the stop list shows mode chips as images with a bound `width`.
- **`if` with booleans only:** every `if` binds a boolean, in case truthy strings are not supported.

## Small images

Text smaller than 30 px ("min" after the minutes, the mode chips of the stop list) and the small
icons are pre-drawn PNGs in `common/images`, in the canvas font Barlow Semi Condensed. To change
them, edit and run `tools/images.mjs` (needs Playwright with Chromium; set `CHROMIUM` to use a
local Chromium binary). The chip widths are also listed in `common/format.js` (`CHIPS`).

## Checks

```sh
node --test test/*.test.mjs     # helpers in common/format.js and strings.js
```

`.github/workflows/watch.yml` runs these plus a syntax check of every JS file and the JSON configs.
