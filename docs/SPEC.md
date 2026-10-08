# Brno MHD Closest Stop - Technical Specification

Status: draft v0.1 (2026-10-08)
Target device: Huawei Watch GT series (HarmonyOS lite wearable), paired with an Android phone

---

## 1. Goal

A watch app that, with one tap, shows the next departures from the public transport stop
closest to the user, using real-time delay data, without taking the phone out of the pocket.

### 1.1 Functional requirements

| ID   | Requirement | Priority |
|------|-------------|----------|
| F1   | Main screen auto-detects the closest stop and shows the next 4 departures (line, destination, minutes to departure, live/scheduled marker). | Must |
| F2   | A "reverse direction" icon on the main screen switches to the platform(s) of the same stop serving the opposite direction. | Must |
| F3   | Stop list screen: stops ordered by distance from the user; selecting one opens the departures screen for that stop. | Must |
| F4   | Departures auto-refresh while the screen is visible. | Must |
| F5   | Live vehicle view: user position plus nearest buses/trams/trolleybuses in real time. | Should (bonus) |
| F6   | Works without any interaction on the phone (phone stays in the pocket). | Must |

### 1.2 Non-functional requirements

- Time to first departures after opening the app: under 3 s with a warm phone companion, under 6 s cold.
- Data shown must never be older than 30 s while the screen is on; stale data is visibly marked.
- Watch battery: no background work on the watch when the app is not in the foreground.
- Data attribution displayed (CC BY 4.0, see section 3.5).

---

## 2. Platform constraints (why the architecture looks like this)

The Watch GT line (GT 4/5/6 etc.) is a **HarmonyOS lite wearable**, not a full HarmonyOS smart watch:

- Third-party apps are **JS (FA model) apps written in HML/CSS/JS**, running on JerryScript (ES5.1).
  ArkTS Stage-model apps are only available on the Watch 4/5/Ultimate class; a developer reports
  that a GT 6 rejects ArkTS HAPs [S9].
  - Consequence: no `async/await`, no ES6 classes/arrow functions in watch code (or transpile to ES5).
- The watch has **no reliable direct internet access for third-party apps**. The established pattern
  is an Android companion app that does the networking and passes the result to the watch over
  **Huawei Wear Engine P2P messaging** [S9][S10][S11].
- Wear Engine P2P messages are meant for small payloads; larger content needs the file-transfer
  API [S10]. Design rule: **keep each message to the watch under ~1 KB** (exact limit to be
  confirmed in the Wear Engine docs during the spike, section 9).
- Using Wear Engine requires applying for the Wear Engine permission in AppGallery Connect;
  approval can take 3-5 business days [S12]. Start this early.
- Screen: round AMOLED, 466 x 466 px on the 46 mm GT 4/5/6 (42/41 mm models are smaller, so
  layouts use percentages and a safe circle, not fixed pixels).

Because of this, the phone companion is mandatory. The heavy data processing (timetable
parsing, real-time matching) is moved off both devices to a small backend.

---

## 3. Data sources

I could not reach `data.brno.cz`, `gis.brno.cz` or `kordis-jmk.cz` directly from the research
environment (blocked network), so the facts below come from search results of the official
pages and from third-party code that uses these feeds. **Every item marked "verify" must be
confirmed in the Phase 0 spike before implementation starts.**

### 3.1 Static timetable - GTFS (stops, schedules)

- Dataset: "Jízdní řád IDS JMK ve formátu GTFS / GTFS timetable data" (the dataset linked by you,
  id `379d2e9a7907460c8ca7fda1f3e84328`) [S1].
- Publisher: KORDIS JMK via data.Brno; covers the whole IDS JMK (all of Brno MHD plus regional lines).
- Update: weekly, Sundays 12:00 [S1].
- Direct download used by third-party integrations: `https://kordis-jmk.cz/gtfs/gtfs.zip` [S3] (verify).
- Also registered in the Mobility Database as `mdb-2155` [S4].
- Used for: `stops.txt` (positions, names, platforms), `routes.txt` (line numbers, mode),
  `trips.txt`, `stop_times.txt`, `calendar.txt`, `calendar_dates.txt`.

### 3.2 Real-time - GTFS Realtime feed (KORDIS)

- URL: `https://kordis-jmk.cz/gtfs/gtfsReal.dat` (protobuf) [S3] (verify).
- Contents according to a third-party integration: **VehiclePosition entities and Alerts, but no
  TripUpdates** [S3]. This is the same feed KORDIS supplies to Google Maps since May 2023 [S5].
  (verify: decode a sample and check whether `VehiclePosition.trip.trip_id` is filled and
  matches `trips.txt`.)
- Consequence: delay per departure has to be **derived** (section 5.3) unless the
  ArcGIS source below gives it directly.

### 3.3 Real-time - Brno vehicle positions (ArcGIS, data.Brno)

- Dataset: "Polohy vozidel hromadné dopravy / Public transit positional data" [S2].
- Source KORDIS, updated every 10 s, WGS84, keeps the last 48 h [S2][S6].
- Attributes (from the dataset description/archive README): vehicle id, vehicle type
  (1 tram, 2 trolleybus, 3 bus, 4 boat, 5 train, 0 not in service), line, route, course,
  lat/lon, bearing, **delay (minutes)**, last stop id, final stop id, inactive flag,
  last update time; stop and route codes reference GTFS `stops.txt`/`routes.txt` [S2].
- Access options (verify which is live):
  - ArcGIS FeatureServer query, e.g. layer `https://gis.brno.cz/ags1/rest/services/Hosted/KAM_pohyb_vozidel_mhd/FeatureServer/0` ("delay" layer) [S7].
  - WebSocket StreamServer: `https://gis.brno.cz/ags4/rest/services/stream_kordis_26/StreamServer/subscribe` is referenced
    as the current-positions stream; an older ODAE stream was reported broken [S6].
- Why it matters: the explicit **delay** and **last stop id** fields make departure ETA
  calculation much simpler and more accurate than deriving delay from raw GPS.

### 3.4 Not used

- `mapa.idsjmk.cz` departure JSON: the operator explicitly forbids automated download and asks
  for API access to be arranged with KORDIS [S8]. Not used unless KORDIS grants access.
  (If granted, it would replace most of section 5.3 with official real-time departures.)
- "Trasy linek IDS JMK" route geometry [S2-related]: not needed for the MVP.

### 3.5 Licence and attribution

KORDIS timetable and real-time data are published under **CC BY 4.0** [S3][S6] (verify on the
dataset pages). The app shows "Data: KORDIS JMK / data.Brno, CC BY 4.0" on the About screen
and in the companion app.

---

## 4. Architecture

```
+------------------+   Wear Engine P2P    +----------------------+   HTTPS/JSON   +---------------------+
|  Watch GT        | <------------------> |  Android companion   | <------------> |  Backend "mhd-api"  |
|  JS lite app     |  small JSON msgs     |  (Kotlin)            |                |  (Node.js/TS)       |
|  - UI only       |                      |  - phone GPS         |                |  - GTFS static DB   |
|  - 3 pages       |                      |  - calls backend     |                |  - RT poller (10 s) |
+------------------+                      |  - compacts payload  |                |  - ETA engine       |
                                          +----------------------+                +----------+----------+
                                                                                             |
                                                         weekly: gtfs.zip  ------------------+
                                                         every 10 s: gtfsReal.dat + ArcGIS positions
```

### 4.1 Responsibilities

| Component | Does | Does not |
|-----------|------|----------|
| Watch app | Renders screens, handles taps/swipes, requests data from phone, refresh timer while visible. | No HTTP, no GTFS parsing, no geometry beyond trivial. |
| Android companion | Holds Wear Engine connection, gets location (phone GPS, fused provider), calls backend, trims response to watch format, caches last result. Runs as a foreground-service only while the watch app is open. | No timetable logic. |
| Backend | Imports GTFS weekly, polls real-time sources, matches vehicles to trips, computes ETAs, answers 3 endpoints. | No user accounts, no personal data storage. |

### 4.2 Why location comes from the phone

The phone is in the pocket next to the user, already has a GPS fix most of the time, and is the
one making the network call anyway. Using the watch GPS would cost watch battery and an extra
hop. Optional later: let the watch send its own GPS fix (`@system.geolocation`, verify support on
the target GT model) when the phone has no fix.

### 4.3 Alternative considered: no backend (all logic in the companion)

Possible (the companion downloads `gtfs.zip` weekly into SQLite and polls the feeds itself),
but it costs phone battery and data, duplicates the realtime polling per user, and makes the
companion much bigger. Kept as fallback if hosting is a problem; the backend modules are
written so they could be ported.

---

## 5. Backend specification

### 5.1 Tech choices

- Node.js 22 + TypeScript, Fastify.
- `gtfs-realtime-bindings` for protobuf decoding.
- SQLite (better-sqlite3) for the static timetable, rebuilt weekly into a new file and swapped atomically.
- Hosting: one small always-on instance (e.g. Cloud Run with `min-instances=1`, Fly.io, or a
  home Raspberry Pi behind Cloudflare Tunnel). Always-on is needed because the real-time poller
  keeps state in memory; on a scale-to-zero platform the poller must instead run lazily on request
  with a 10 s cache (works, but first request after idle is slower).
- Simple shared API key in a header (the companion holds it) to stop casual abuse.

### 5.2 Static data import (weekly, Sunday 13:00 and on startup)

1. Download `gtfs.zip`, validate (required files present, row counts sane).
2. Load into SQLite tables: `stops`, `routes`, `trips`, `stop_times`, `calendar`, `calendar_dates`.
3. Derive:
   - **Stop groups**: platforms with the same `parent_station`, or if not set, the same
     `stop_name` within 300 m. A stop group is what the user sees as "a stop"
     (e.g. "Česká"); its platforms are directions.
   - **Platform direction label**: the 2-3 most frequent trip headsigns departing from that
     platform (e.g. "-> Královo Pole, Řečkovice").
   - **Brno filter flag** per stop: served by at least one line with DPMB-style numbering
     (1-99, N-lines) or inside the Brno city polygon; the stop list hides regional-only stops
     by default.
   - Spatial index: stops bucketed into a 500 m grid for fast nearest search.
4. Keep the previous DB for one week as rollback.

### 5.3 Real-time processing (every 10 s)

1. Fetch ArcGIS vehicle positions (primary, has `delay`, `laststopid`) and `gtfsReal.dat`
   (secondary, has `trip_id` if filled). Keep whichever is fresher per vehicle.
2. For each active vehicle (`isinactive = false`, last update < 120 s), resolve the GTFS trip:
   - direct `trip_id` from GTFS-RT if present, else
   - match by (line, course/route, final stop, current time) against active trips.
3. Store per trip: `delay_s`, `last_stop_sequence`, `lat`, `lon`, `bearing`, `updated_at`.
4. If only a position (no delay) is known, estimate delay: find the trip's nearest upcoming stop,
   delay = now - scheduled departure of the last passed stop (never negative while still at it).
   This mirrors the approach used by the third-party integration [S3].
5. Drop vehicle state older than 5 min.

### 5.4 Departure computation for a platform

```
input: platform_id(s), now
candidates = stop_times at platform where trip runs today (calendar + calendar_dates,
             handle times >= 24:00 from the previous service day)
             and scheduled_departure in [now - 15 min, now + 120 min]
for each candidate:
    rt = realtime[trip]
    if rt and rt.last_stop_sequence >= candidate.stop_sequence: skip   # already left
    eta = scheduled_departure + (rt ? rt.delay_s : 0)
    if eta < now - 30 s: skip
    live = rt != null and rt.updated_at > now - 120 s
sort by eta, take first N (default 4)
```

Do not show the final stop of a trip as a departure (arrival only).

### 5.5 API

All responses are compact JSON; times are Unix seconds; distances in metres.

`GET /v1/nearby?lat=49.195&lon=16.608&limit=12`
```json
{ "t": 1791480000,
  "stops": [
    { "id": "g1234", "n": "Česká", "d": 85,
      "p": [ { "id": "1234Z1", "dir": "Královo Pole" }, { "id": "1234Z2", "dir": "Hlavní nádraží" } ],
      "m": "TB" } ] }
```
`m` = modes served: T tram, B bus, R trolleybus, N night.

`GET /v1/departures?group=g1234&platform=1234Z1&n=4`
```json
{ "t": 1791480000, "stop": "Česká", "dir": "Královo Pole", "pl": ["1234Z1","1234Z2"],
  "dep": [ { "l": "4", "m": "T", "h": "Masarykova čtvrť", "e": 1791480120, "dl": 60, "lv": 1 },
           { "l": "12","m": "T", "h": "Komárov", "e": 1791480300, "dl": 0, "lv": 0 } ] }
```
`e` = expected departure, `dl` = delay seconds, `lv` = 1 if live data was used.

`GET /v1/vehicles?lat=..&lon=..&r=800`
```json
{ "t": 1791480000,
  "v": [ { "l": "4", "m": "T", "dx": -120, "dy": 340, "b": 90, "dl": 60 } ] }
```
`dx/dy` = metres east/north of the user (so the watch needs no geodesy).

`GET /v1/health` - import date, realtime age, vehicle count.

---

## 6. Android companion specification

- Kotlin, minSdk 26, single activity (settings/onboarding) + one service.
- Libraries: Huawei Wear Engine SDK (phone side), Fused Location Provider (or Android
  `LocationManager` if Google Play services are missing), OkHttp + kotlinx.serialization.
- Onboarding: grant location permission ("while in use" plus foreground service), Wear Engine
  device authorisation, backend URL/key (pre-filled).
- Message protocol with the watch (JSON strings over P2P):

| From watch | Meaning | Phone reply |
|------------|---------|-------------|
| `{"c":"home"}` | Main screen: closest stop + departures | location -> `/nearby?limit=1` -> `/departures` for first platform -> `{"c":"dep", ...}` |
| `{"c":"dep","g":"g1234","p":"1234Z2"}` | Departures for given platform | `/departures` -> `{"c":"dep", ...}` |
| `{"c":"near"}` | Stop list | `/nearby?limit=12` -> `{"c":"near", ...}` (split into pages of 4 if over size limit) |
| `{"c":"veh"}` | Radar | `/vehicles?r=800` -> `{"c":"veh", ...}` (max 15 vehicles) |
| any | Error | `{"c":"err","e":"noloc" / "net" / "srv"}` |

- Location strategy: use last known location if < 30 s old and accuracy < 50 m, else request a
  single high-accuracy fix with a 4 s timeout, falling back to the last known one.
- Cache last `home` response for 20 s so reopening the watch app is instant.
- Destination names are shortened on the phone to fit the watch (max 16 chars, Czech
  diacritics kept; the watch font must be checked for diacritics in the spike).

---

## 7. Watch app specification

### 7.1 Project

- DevEco Studio, project type "Lite Wearable", JS (FA model), HML + CSS + JS (ES5).
- Pages: `pages/home`, `pages/stops`, `pages/radar`, `pages/about`.
- Wear Engine watch-side API (`@system.wearengine` / P2P) for messaging with the companion.
- State shared between pages via `app.js` globals (current group/platform, last payloads).

### 7.2 Navigation

```
          swipe up / "list" icon                 tap stop
 [home] -----------------------------> [stops] -----------> [home with that stop]
   |                                                         (pinned, no auto-closest)
   | swipe left / "radar" icon
   v
 [radar]          swipe right = back on every page (system gesture)
```

Home shows a small pin icon when a stop was chosen manually; tapping it returns to
"closest stop" mode.

### 7.3 Screen designs (466 x 466 round, safe area = inner circle)

**Home / departures**
```
            .-----------------------.
         .'      Česká      ⇄  12:41  '.
       /     -> Královo Pole            \
      |  ----------------------------    |
      |  [T] 4   Masarykova č.   2 min●  |
      |  [T] 12  Komárov         5 min   |
      |  [R] 32  Novolíšeňská    7 min●  |
      |  [B] 67  Ústřední hřb.  11 min   |
       \      85 m      ≡      ◎        /
         '.                         .'
            '-----------------------'
```
- Header: stop name (bold, marquee if too long), direction label below it.
- `⇄` reverse direction: cycles through the stop group's platforms. With 2 platforms it is a
  simple toggle; with more (big hubs) each tap goes to the next platform, and the direction
  label shows where that platform goes.
- Departure rows: mode badge colour (tram red, trolleybus green, bus blue, night dark blue),
  line number, shortened headsign, time.
  - Time format: "now" if < 30 s, "N min" if < 60 min, else "HH:MM".
  - `●` = live real-time data used; no dot = scheduled time only.
  - Delay over 2 min: time in orange.
- Footer: distance to stop, `≡` stop list, `◎` radar.
- Refresh: on open, then every 20 s while the page is visible (`onShow`/`onHide`); the
  "N min" values are re-rendered every 10 s locally from `e - now` without a network call.
- States: loading (spinner + "Hledám zastávku…"), no location, phone not connected,
  network error (last data kept and shown greyed with its age), no departures in next 2 h.

**Stop list**
```
            .-----------------------.
         .'     Nejbližší zastávky    '.
       /                                \
      |   Česká               85 m  T R  |
      |   Grohova            210 m  B    |
      |   Konečného nám.     340 m  T B  |
      |   Údolní             410 m  T    |
       \        (scroll for more)        /
         '.                         .'
            '-----------------------'
```
- `list` component, 12 entries max, crown/scroll to move, tap opens home for that stop.

**Radar (bonus, F5)**
```
            .-----------------------.
         .'          N  ↑             '.
       /         4 ▲                    \
      |               .   (400 m ring)   |
      |     32 ●      ✛ you       12 ▲   |
      |               .                  |
       \           67 ■                 /
         '.        800 m            .'
            '-----------------------'
```
- No map tiles: lite wearables have no map component and image transfer every few seconds
  would be too heavy. Instead a "radar": user at centre, north up, rings at 400 m and 800 m,
  vehicles positioned from `dx/dy`, marker shape by mode (▲ tram, ● trolleybus, ■ bus) with the
  line number, arrow rotated by bearing if supported. Nearest 2-3 stops drawn as small dots.
- Implemented with `canvas` if available on the target API level, otherwise a `stack` of
  absolutely positioned `text`/`div` elements (verify in the spike).
- Refresh every 10 s while visible.
- Possible later upgrade: the companion renders a real map snapshot (OpenStreetMap tiles) to a
  small PNG and sends it via Wear Engine file transfer every 30 s as background.

### 7.4 Watch-side performance rules

- No more than 1 outstanding request; ignore replies for a page that is no longer shown.
- Timeout 8 s per request, then error state with retry tap.
- Keep DOM small (max 4 departure rows, 12 list items, 15 radar markers).

---

## 8. Error handling and edge cases

| Case | Behaviour |
|------|-----------|
| Phone not connected / companion not installed | Watch shows "Připojte telefon" and a hint to open the companion once. |
| No GPS fix | Use last known location if < 10 min old and mark "approx."; else error state. |
| Realtime feed down | Backend returns scheduled times with `lv=0`; watch shows no live dots. |
| GTFS import fails | Keep previous DB; `/health` reports it. |
| Night (no departures within 2 h) | Show next departure time even if later, or "Žádné odjezdy". |
| Stop served by regional (non-MHD) lines only | Hidden from stop list unless no Brno stop within 1 km. |
| Times after midnight (`25:10:00`) | Handled via previous service day logic (5.4). |
| Vehicle matched to wrong trip | Only apply live delay when vehicle line and final stop match the trip. |

---

## 9. Phase 0 spike (do first, about 1-2 days)

These items could not be verified during research and decide details of the design:

1. Download `gtfs.zip`; check `parent_station` usage, platform codes, stop naming, size of `stop_times.txt`.
2. Decode `gtfsReal.dat`; check entity types, whether `trip_id` matches `trips.txt`, refresh rate.
3. Query the ArcGIS vehicle layer / stream; confirm field names (`delay`, `laststopid`, ...), access without a token, rate limits.
4. Confirm licence (CC BY 4.0) and any usage terms on the data.Brno dataset pages.
5. Create a "Hello world" lite wearable JS app, deploy to your GT, and test: Wear Engine P2P
   round trip with a minimal Android app, message size limit, `canvas` support, Czech
   diacritics in the system font.
6. Apply for Wear Engine permission in AppGallery Connect (takes days, start on day 1).

---

## 10. Delivery plan

| Phase | Content | Done when |
|-------|---------|-----------|
| 0 | Spike (section 9) | All 6 items answered, spec updated. |
| 1 | Backend: GTFS import, `/nearby`, `/departures` (scheduled only) | Correct departures for 5 test stops vs. idos.cz. |
| 2 | Backend realtime: poller, trip matching, delays | `lv=1` on most departures during the day; ETA within 1 min of the stop display boards on spot checks. |
| 3 | Companion app: location, Wear Engine, protocol | Messages round-trip with a test watch page. |
| 4 | Watch app: home + reverse direction + stop list | F1-F4, F6 met on the device. |
| 5 | Bonus: radar (`/vehicles` + page) | F5 met. |
| 6 | Polish: error states, about/attribution, battery check | 1 week of daily use without issues. |

Repository layout:
```
/backend      Node.js/TS service (import, realtime, API)
/companion    Android app (Kotlin)
/watch        DevEco Studio lite wearable JS project
/docs         this spec, decisions, spike results
```

---

## 11. Open questions

- Hosting preference for the backend (cloud free tier vs. home server)?
- App language on the watch: Czech, English, or follow system language?
- Should regional (non-Brno) IDS JMK stops ever appear?

---

## Sources

- [S1] Jízdní řád IDS JMK ve formátu GTFS / GTFS timetable data - https://data.brno.cz/datasets/379d2e9a7907460c8ca7fda1f3e84328 (mirror: https://datahub.brno.cz/datasets/379d2e9a7907460c8ca7fda1f3e84328)
- [S2] Polohy vozidel hromadné dopravy / Public transit positional data - https://data.brno.cz/datasets/mestobrno::polohy-vozidel-hromadn%C3%A9-dopravy-public-transit-positional-data/about
- [S3] gtfs2-idsjmk (third-party Home Assistant integration documenting the KORDIS feed URLs and content) - https://github.com/dominikgalovic/gtfs2-idsjmk
- [S4] Mobility Database, IDS JMK feed mdb-2155 - https://mobilitydatabase.org/feeds/mdb-2155
- [S5] Brno Daily, "Live Data About The Location of Brno Public Transport Is Now Available on Google Maps" (2023-05-24) - https://brnodaily.com/2023/05/24/brno/live-data-about-the-location-of-brno-public-transport-is-now-available-on-google-maps/
- [S6] National open data catalogue (NKOD), Public transit positional data - https://data.gov.cz/dataset?iri=https%3A%2F%2Fdata.gov.cz%2Fzdroj%2Fdatov%C3%A9-sady%2F44992785%2F2629f44c90338528b23bcb3a3dbbeb4a
- [S7] ArcGIS layer KAM_pohyb_vozidel_mhd - https://gis.brno.cz/ags1/rest/services/Hosted/KAM_pohyb_vozidel_mhd/FeatureServer/0
- [S8] E-paper departures panel (shows the mapa.idsjmk.cz JSON and its "automated download forbidden" notice) - https://epaper.kubaandrysek.cz/wrapper/mhd/ ; map: https://mapa.idsjmk.cz/
- [S9] Home Assistant client for Huawei watches (GT = lite JS only, phone companion via Wear Engine P2P) - https://github.com/gentslava/Home-Assistant-HarmonyOS-Next
- [S10] Wear Engine lite wearable <-> Android sample (messages and file transfer) - https://github.com/Explore-In-HMOS-Wearable/sportwatch-wear-engine-lite-wearable-to-mobile
- [S11] LiveScore Wearable Demo (phone fetches API, forwards via Wear Engine) - https://github.com/minkiapps/LiveScore-Wearable-Demo
- [S12] P2P communication between Android and HarmonyOS wearable - https://dev.to/harmonyos/p2p-communication-between-android-and-next-wearable-fa-stage-model-device-technical-guide-3d9e
- Wear Engine Kit docs - https://developer.huawei.com/consumer/en/codelabsPortal/carddetails/tutorials_WearEngine-ArkTS
