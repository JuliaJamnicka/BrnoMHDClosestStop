# Fčil - Technical Specification

Status: draft v0.3 (2026-10-08) - data sources verified against live feeds (section 3), hosting fixed to Google Cloud Run, target devices known
Target devices: Huawei Watch GT 5 46 mm (HarmonyOS lite wearable, 466 x 466) paired with a Nothing Phone (2) (Android, Google Play services)
Screen designs: https://claude.ai/artifact/7ppFn5nM3PrKVr9XYxQKZC (private canvas)

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
| F5   | Live vehicle view: user position plus nearest trams/buses/trains in real time. | Should (bonus) |
| F6   | Works without any interaction on the phone (phone stays in the pocket). | Must |
| F7   | Czech and English UI, switchable in the phone app settings (default: follow phone language). | Must |
| F8   | All stops in the IDS JMK dataset are included (Brno MHD and regional, incl. trains and the Brno dam boat). | Must |
| F9   | Android home-screen widget with the same "closest stop departures" view. | Later phase (after the watch) |

### 1.2 Non-functional requirements

- Time to first departures after opening the app: under 3 s with a warm phone companion, under 6 s cold.
- Data shown must never be older than 60 s while the screen is on (the source feed itself refreshes every ~30 s); stale data is visibly marked.
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
- Screen: the target **GT 5 46 mm** has a round 1.43" AMOLED, **466 x 466 px** [S20]. Layouts are
  designed for that size and keep content inside the inner safe circle.

Because of this, the phone companion is mandatory. The heavy data processing (timetable
parsing, real-time matching) is moved off both devices to a small backend.

---

## 3. Data sources

Verified on 2026-10-08 by downloading the feeds and decoding them (results in section 9).

### 3.1 Static timetable - GTFS (stops, schedules)

- Dataset: "Jízdní řád IDS JMK ve formátu GTFS / GTFS timetable data", id
  `379d2e9a7907460c8ca7fda1f3e84328` [S1]. Licence: CC BY. Updated weekly, Sundays 12:00.
- Download: `https://kordis-jmk.cz/gtfs/gtfs.zip` (linked from the dataset page). 8.5 MB zipped,
  55 MB unzipped. Feed validity in the sample: 2026-10-02 to 2026-12-12.
- Contents of the sample:

| File | Rows | Notes |
|------|------|-------|
| `agency.txt` | 1 | "IDS JMK (Data from: KORDIS JMK, DPMB)", timezone Europe/Prague |
| `routes.txt` | 351 | `route_id` like `L4D99`; `route_type`: 0 tram (14), 800 trolleybus (14), 3 bus (294), 2 train (28), 4 boat (1). `route_color` is filled. |
| `stops.txt` | 10,925 | 7,667 platforms (`location_type` 0, id `U1073Z4`) and 3,258 stations (`location_type` 1, id `U1073N2860`). Every platform has `parent_station`. `platform_code` only partly filled. |
| `trips.txt` | 55,773 | `direction_id` filled (0/1), `trip_headsign` filled. |
| `stop_times.txt` | 930,100 | 38 MB; times go past 24:00. |
| `calendar.txt`, `calendar_dates.txt` | 362 / 1,885 | |
| `transfers.txt`, `booking_rules.txt`, `location_group*.txt`, `api.txt`, `JR_GTFS_Exp.log` | | Not needed. |

- Real example: stop group "Česká" (`U1073N2860`) has **9 platforms**: trams 4/5/6 towards
  Náměstí Míru etc. (Z1) and the opposite direction (Z2), tram 12 (Z3 / Z4), tram 9 (Z9 / Z10),
  trolleybuses (Z6) and two platforms where trolleybuses only terminate (Z5, Z7).
  This drives the direction logic in 5.4.

### 3.2 Real-time - GTFS Realtime feed (KORDIS) - the only real-time source

- URL: `https://kordis-jmk.cz/gtfs/gtfsReal.dat`, linked from the GTFS dataset page as its
  "GTFS Realtime" data [S1]. Protobuf, about 170 KB, `FULL_DATASET`.
- Contents of a snapshot: **1,226 VehiclePosition entities and 75 Alerts, no TripUpdates**.
  - 982 vehicles reported within the last 120 s; 958 of them carry `trip.trip_id`, and
    **all 958 match `trips.txt`**. No `route_id`/`start_date` in the trip descriptor.
  - Each vehicle has lat/lon, bearing, speed, `current_status` (IN_TRANSIT_TO / STOPPED_AT),
    `stop_id` of the next/current stop, and a timestamp.
  - **The real-time `stop_id` is zero-padded** (`U01677Z02`) while the static one is not
    (`U1677Z2`). Normalise with `U0*(\d+)Z0*(\d+) -> U$1Z$2`; afterwards 935 of 982 match.
  - Alerts reference lines by `route_short_name` (`"4"`, `"N94"`), not by `route_id`.
- Update cadence: the file is regenerated about **every 30-35 s**; positions are up to ~30 s old.
- **trip_ids are renumbered with every KORDIS export**, and the real-time feed does not switch at the
  same time. Seen on 2026-10-09: `gtfs.zip` was re-exported at 06:23 Prague time, but at 09:00 the
  real-time feed still used the numbering of the 2026-10-02 export, so only ~10 % of vehicles
  matched. Fix: the build also reads data.Brno's weekly copy of the feed and stores, for each of its
  trip_ids, the trips of the current export with the same route, direction, first/last stop and
  times (`trip_alias`). The tracker tries the feed's trip_id and its aliases and keeps the trip that
  runs that day and contains the vehicle's next stop at about that time. Result on live data: 82 %
  of vehicles matched, the same as with the export the feed itself used. The backend rebuilds daily.
- The server sends `Cache-Control: max-age=86400` although the content changes every ~30 s,
  so the backend must revalidate (`If-None-Match` with the ETag) instead of trusting caches.
- There is no delay field, so **delay is derived** from position + schedule (5.3). A sanity
  check on one snapshot gave a plausible distribution: median 0 min, 90th percentile 2.4 min
  late, 17 % of vehicles at least 1 min late.

### 3.3 Brno vehicle position datasets (ArcGIS) - not used

- "Polohy vozidel hromadné dopravy / Public transit positional data" [S2] is now a **historical
  archive** (daily Parquet files, last 7 days). It has a `delay` attribute, but only for past days.
- Its recommended live WebSocket (`gis.brno.cz/ags4/.../stream_kordis_26/StreamServer`) now
  **requires a login token** (HTTP 499 "Token Required"), so it is not usable for a public app.
- The FeatureServer layer `KAM_pohyb_vozidel_mhd` [S7] was last edited in March 2025 (stale).
- Possible later use: the Parquet archive to tune the delay estimation offline.

### 3.4 Not used

- `mapa.idsjmk.cz` departure JSON: the operator explicitly forbids automated download and asks
  for API access to be arranged with KORDIS [S8]. If KORDIS ever grants access, it could replace
  the derived delays.
- "Trasy linek IDS JMK" route geometry: not needed.

### 3.5 Licence and attribution

The GTFS dataset is published under **CC BY** (dataset metadata); KORDIS real-time data is
described as CC BY 4.0 [S3]. The app shows "Data: KORDIS JMK / data.Brno, CC BY 4.0" in the phone
app settings and on the watch About page.

---

## 4. Architecture

```
+------------------+   Wear Engine P2P    +----------------------+   HTTPS/JSON   +---------------------+
|  Watch GT        | <------------------> |  Android companion   | <------------> |  Backend "mhd-api"  |
|  JS lite app     |  small JSON msgs     |  (Kotlin)            |                |  (Node.js/TS)       |
|  - UI only       |                      |  - phone GPS         |                |  - GTFS static DB   |
|  - 3 pages       |                      |  - calls backend     |                |  - RT cache (10 s)  |
+------------------+                      |  - compacts payload  |                |  - ETA engine       |
                                          +----------------------+                +----------+----------+
                                                                                             |
                                           weekly (GitHub Actions): gtfs.zip -> timetable.db +
                                       on request (15 s cache, ETag): gtfsReal.dat
```

### 4.1 Responsibilities

| Component | Does | Does not |
|-----------|------|----------|
| Watch app | Renders screens, handles taps/swipes, requests data from phone, refresh timer while visible. | No HTTP, no GTFS parsing, no geometry beyond trivial. |
| Android companion | Holds Wear Engine connection, gets location (phone GPS, fused provider), calls backend, trims response to watch format, caches last result. Runs as a foreground-service only while the watch app is open. | No timetable logic. |
| Backend | Serves the weekly-built timetable DB, fetches the real-time feed on demand, matches vehicles to trips, computes ETAs, answers 3 endpoints. | No user accounts, no personal data storage. |

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
- Hosting: free cloud tier, scale-to-zero container (see 5.6). There is no background
  poller: real-time data is fetched lazily on request and cached for 15 s (5.3), so the service
  only runs while the app is actually used.
- Simple shared API key in a header (the companion holds it) to stop casual abuse.

### 5.2 Static data import (weekly, Sunday 13:00)

Runs as a scheduled **GitHub Actions** workflow (free for this repository), not on the server:
it builds a compact read-only SQLite file (`timetable.db`), bakes it into the container image and
redeploys. The server therefore never parses `gtfs.zip`, which keeps cold starts short.


1. Download `gtfs.zip`, validate (required files present, row counts sane).
2. Load into SQLite tables: `stops`, `routes`, `trips`, `stop_times`, `calendar`, `calendar_dates`.
3. Derive:
   - **Stop groups**: platforms with the same `parent_station`, or if not set, the same
     `stop_name` within 300 m. A stop group is what the user sees as "a stop"
     (e.g. "Česká"); its platforms are directions.
   - **Platform direction label**: the 2-3 most frequent trip headsigns departing from that
     platform (e.g. "-> Královo Pole, Řečkovice").
   - **Modes per stop group** from `routes.route_type`, mapped to the display categories in 5.5.
     All stops in the feed are kept (decision F8); no Brno-only filter.
   - Spatial index: stops bucketed into a 500 m grid for fast nearest search.
4. Keep the previous image for one week as rollback; fail the workflow (and keep the old image) if validation fails.

### 5.3 Real-time processing (lazy, on request, 15 s cache)

1. On a request, if the cached snapshot is older than 15 s, fetch `gtfsReal.dat` with
   `If-None-Match` (the feed changes only every ~30 s, see 3.2). Keep the last 2 snapshots.
2. For each vehicle with `trip.trip_id` and a timestamp younger than 120 s:
   - normalise `stop_id` (3.2) and find that stop's `stop_sequence` k in the trip;
   - `STOPPED_AT` -> the vehicle is at stop k; `IN_TRANSIT_TO` -> it has left stop k-1.
3. Delay estimate per trip (seconds; shown rounded down to whole minutes):
   - at stop k: `delay = max(0, ts - scheduled_departure[k])`;
   - in transit to stop k: `delay = max(ts - scheduled_departure[k-1], ts - scheduled_arrival[k], 0)`,
     i.e. how late it left the previous stop, growing once it is overdue at the next one;
   - a vehicle waiting at its first stop before departure counts as on time; early running is
     shown as on time (never negative), because the schedule is the earliest departure.
4. Store per trip: `delay_s`, `passed_sequence`, `lat`, `lon`, `bearing`, `ts`.
5. A trip whose vehicle drops out of the feed keeps its last estimate for up to 5 minutes.
6. Vehicles without `trip_id` (about 2 %) are ignored: without a trip there is no line number to show.

### 5.4 Departure computation for a platform

```
input: platform_id(s), now
candidates = stop_times at platform where trip runs today (calendar + calendar_dates,
             handle times >= 24:00 from the previous service day)
             and scheduled_departure in [now - 20 min, now + 120 min]   (12 h if empty)
for each candidate:
    rt = realtime[trip]
    if rt and rt.passed_sequence >= candidate.stop_sequence: skip   # already left
    eta = scheduled_departure + (rt ? rt.delay_s : 0)
    if eta < now - 30 s: skip
    live = rt != null and rt.updated_at > now - 120 s
sort by eta, take first N (default 4)
```

Do not show the final stop of a trip as a departure (arrival only). At Česká this removes the
two trolleybus platforms where lines only terminate (Z5, Z7).

**Opposite direction (reverse button).** For the current platform P, take the set of
(route, direction_id) pairs departing from P in the next 2 hours. The opposite platform is the
other platform of the same stop group with the most departures of the same routes in the other
`direction_id` (Česká: Z1 <-> Z2, Z3 <-> Z4, Z9 <-> Z10). If no platform shares a route, the button
cycles through the group's platforms ordered by distance. The API returns it as `opp`.

**Which platform is shown first (`/v1/home`).** Among platforms within 400 m, the nearest one with
a departure in the next 30 minutes; if none has one, the nearest platform. Found in real use: at
night the closest stop (Žitná, lines 42 and 70) has nothing for hours while N91 stops at Kořískova,
283 m away. Otherwise the closest *platform*, not the station centre: platforms of
big stops are up to 150 m apart, and the closest one usually matches where the user stands.
Tapping the stop name on the watch opens a platform picker listing each platform's lines
(needed for hubs like Česká with 7 departure platforms).

### 5.5 API

All responses are compact JSON; times are Unix seconds; distances in metres. The implemented
API (with field-by-field notes) is documented in `backend/README.md`; in addition to the endpoints
below, `GET /v1/home?lat=&lon=&n=` returns the closest platform and its departures in one round
trip, which is what the watch home screen uses.

`GET /v1/nearby?lat=49.195&lon=16.608&limit=12`
```json
{ "t": 1791480000,
  "stops": [
    { "id": "U1073N2860", "n": "Česká", "d": 85,
      "p": [ { "id": "U1073Z2", "dir": "Babická, Královo Pole" }, { "id": "U1073Z1", "dir": "Náměstí Míru" } ],
      "m": "TB" } ] }
```
`m` = modes served (codes below).

`GET /v1/departures?group=U1073N2860&platform=U1073Z2&n=4`
```json
{ "t": 1791480000, "stop": "Česká", "dir": "Babická, Královo Pole", "opp": "U1073Z1",
  "pl": [ { "id": "U1073Z2", "l": "3 4 5 6 10" }, { "id": "U1073Z1", "l": "4 5 6" } ],
  "dep": [ { "l": "4", "m": "T", "h": "Babická", "e": 1791480120, "dl": 60, "lv": 1 },
           { "l": "6", "m": "T", "h": "Královo Pole, nádraží", "e": 1791480300, "dl": 0, "lv": 0 } ] }
```
`e` = expected departure, `dl` = delay seconds, `lv` = 1 if live data was used.

`GET /v1/vehicles?lat=..&lon=..&r=800`
```json
{ "t": 1791480000,
  "v": [ { "l": "4", "m": "T", "dx": -120, "dy": 340, "b": 90, "dl": 60 } ] }
```
`dx/dy` = metres east/north of the user (so the watch needs no geodesy).

`GET /v1/health` - import date, realtime age, vehicle count.

Mode codes used in all responses (display categories, decided with the screen design):
`T` tram (`route_type` 0), `B` bus **including trolleybus** (`route_type` 3 and 800), `V` train
(`route_type` 2), `L` boat (`route_type` 4). Night lines (`N…`) keep their mode. Stop and headsign names are returned as in the dataset (Czech); only UI strings
are translated (section 7.5).

### 5.6 Hosting: Google Cloud Run (decided)

- Region: a Tier 1 European region with the full free allowance, e.g. `europe-north1` (Finland)
  or `europe-west1` (Belgium); check the Cloud Run pricing page at deploy time [S13].
- Settings: `min-instances=0`, `max-instances=1`, 1 vCPU, 512 MiB, request-based billing,
  concurrency 20, request timeout 10 s. `max-instances=1` caps cost and keeps the in-memory
  real-time cache in one place.
- Free allowance: 2M requests, 180,000 vCPU-s, 360,000 GiB-s per month [S13]. Estimated use for
  one user (about 20 openings a day, 2-3 requests a minute while open, ~100 ms each) is far below 1 %.
- Image: built by GitHub Actions and pushed to Artifact Registry (0.5 GB free storage; a cleanup
  policy keeps the last 2 images). The weekly workflow rebuilds `timetable.db` and redeploys.
- Deployment auth: GitHub Actions -> Google Cloud via Workload Identity Federation (no JSON keys in the repo).
- Protection: API key header checked by the service; the Cloud Run URL stays publicly invokable
  (the phone has no Google identity to present). Billing budget alert at 1 EUR.
- Cold start: expected 1-3 s (Node + read-only SQLite). The phone app sends `GET /v1/health` as
  soon as the watch app connects, so the instance is usually warm by the time location is ready.

Alternatives considered (not used): Azure Container Apps (same free grant [S14]), Azure Functions
and App Service F1 [S15], Oracle Always Free VM [S16], Cloudflare Workers (10 ms CPU limit [S17]),
Render (1 min wake-up [S18]), Fly.io (no free tier [S19]).

---

## 6. Android phone app specification

- Implemented in `android/` (see `android/README.md`). Package name `io.github.juliajamnicka.fcil`.
- Kotlin, minSdk 26, one main activity with four tabs + one service:
  - **Departures**: the same as the watch home screen with up to 8 departures, refreshed every
    20 s while visible; reverse button, platform chips to switch platform, pin indicator and
    "Nearest stop (automatic)" to unpin. Pinning works as in the widget (`WidgetLogic`).
  - **Stops**: the user's stop lists (6.2) and nearby stops with their platforms; tapping a
    platform or a list pins the Departures tab. "+" next to "My lists" and the pencil on a list
    open the list editor.
  - **Radar**: vehicles within 800 m, north up (as on the watch), each with an arrow for its
    direction of travel, refreshed every 15 s, with the nearest vehicles listed below with their
    destination.
  - **Settings**: watch link, language, number of departures for the watch and widget.
- Look: Fčil brand (docs/brand): blue accents, "now" in the háček red, night blue in dark mode.
- The Wear Engine receiver only works while the app's process runs, and the watch cannot start the
  phone app. So the service runs permanently as a low-priority foreground service (it is idle until
  a message arrives; no polling) instead of "only while the watch app is open". It restarts after a
  reboot if it was on; location then needs the optional "Allow all the time" permission.
- Target phone: Nothing Phone (2), Android with Google Play services.
- Libraries: Huawei Wear Engine SDK (phone side), Google Fused Location Provider,
  OkHttp + kotlinx.serialization (`LocationManager` fallback for phones without Play services).
- The Huawei Health app must be installed and the watch paired in it; Wear Engine works through it.
- Settings tab:
  - Language: System / Čeština / English (sent to the watch with every reply as `"lg":"cs"|"en"`).
  - Number of departures shown (3 or 4).
  - The backend URL and API key are set at build time (GitHub secret `MHD_API_KEY`), not in the app.
  - About: data attribution (CC BY 4.0, KORDIS JMK / data.Brno).
- Onboarding: grant location permission ("while in use" plus foreground service), Wear Engine
  device authorisation, backend URL/key (pre-filled).
- Message protocol with the watch (JSON strings over P2P):

| From watch | Meaning | Phone reply |
|------------|---------|-------------|
| `{"c":"home"}` | Main screen: closest stop + departures | location -> `/nearby?limit=1` -> `/departures` for first platform -> `{"c":"dep", ...}` |
| `{"c":"dep","g":"g1234","p":"1234Z2"}` | Departures for given platform | `/departures` -> `{"c":"dep", ...}` |
| `{"c":"near"}` | Stop list | `/nearby?limit=12` -> `{"c":"near", ...}` (entries dropped from the end if over the size limit) |
| `{"c":"pl","p":"U1073Z2"}` | Platform picker | `/departures` -> `{"c":"pl", ...}` with each platform's lines and direction |
| `{"c":"veh"}` | Radar | `/vehicles?r=800` -> `{"c":"veh", ...}` (max 15 vehicles) |
| any | Error | `{"c":"err","e":"noloc" / "net" / "srv"}` |

- Location strategy: use last known location if < 30 s old and accuracy < 50 m, else request a
  single high-accuracy fix with a 4 s timeout, falling back to the last known one.
- Cache last `home` response for 20 s so reopening the watch app is instant.
- Destination names are shortened on the phone to fit the watch (max 16 chars, Czech
  diacritics kept; the watch font must be checked for diacritics in the spike).
- Code structure prepared for the later widget phase: `data` module (backend client, location,
  cache) is separate from the `wear` module (Wear Engine bridge), so the widget reuses `data`.

### 6.2 Stop lists

For places with several useful stops (e.g. leaving work, three stops nearby all lead home), the
user makes a named list of platforms (stop + direction) in the Stops tab. In Czech these are
"skupiny" (groups).

- List editor (a full screen): the name, the chosen platforms (× removes one), and a search over
  all stops of the network (`GET /v1/stops?q=`, case and diacritics ignored; nearby stops while
  the search is empty) where + adds a platform and the check removes it. "Save" stores the list;
  an existing list can also be deleted there.

- A list shows by itself, instead of the single nearest stop, when any of its stops is within
  500 m; if several lists qualify, the one with the closest stop wins. Pinning a stop or a list
  overrides this, as in the widget.
- One merged timeline (`GET /v1/board?platforms=...`): the next departures of all its platforms
  sorted by time, each row with its stop name. No walking times: the user picked the stops and
  knows where they are.
- Same on the phone, the widget and the watch (the watch shows the stop name in place of the
  headsign, `"l":1` in the reply).
- Stored on the phone only (DataStore, with each platform's coordinates from `/v1/nearby`), so
  the server keeps no user data.
- Candidate for a later premium feature; not gated for now.

### 6.1 Phone widget (F9) - implemented early, while waiting for the Wear Engine approval

- Jetpack Glance app widget (2x2 and 4x2 sizes), showing stop, direction and the next 3-4
  departures, with a reverse-direction button and tap-to-refresh.
- Android limits automatic widget updates (`updatePeriodMillis` minimum 30 min, WorkManager
  periodic work minimum 15 min), so a widget cannot stay "live" every 20 s in the background.
  Plan: refresh on tap, on screen unlock while the widget is visible (best effort), and every
  15 min via WorkManager; "N min" labels are shown as absolute times (`HH:MM`) so they do not go stale.
- Same backend endpoints; no backend change expected.

---

## 7. Watch app specification

### 7.1 Project

- Implemented in `watch/` (see `watch/README.md`); built and signed in DevEco Studio.
- DevEco Studio, project type "Lite Wearable", JS (FA model), HML + CSS + JS (ES5).
- Pages: `pages/home`, `pages/stops`, `pages/platforms`, `pages/radar` (data attribution is in the phone app).
- Wear Engine watch-side SDK (`common/wearengine.js`, Huawei, Apache-2.0) for P2P messaging with the phone. It sends and receives text, so the phone escapes non-ASCII characters as `\uXXXX` in its JSON replies.
- State shared between pages in `common/state.js` (pinned platform, last reply, clock offset, language).

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

The visual designs are on the design canvas: https://claude.ai/artifact/7ppFn5nM3PrKVr9XYxQKZC
(departures, stop list, radar, loading, phone not connected, phone settings; Czech/English
switch on each). The rules below describe them.

**Visual system**

- Black background (AMOLED, saves battery), white text; secondary text `#8E8E93` / `#C7C7CC`.
- Font in the mockups: Barlow Semi Condensed (stand-in); on the watch the system font is used,
  so text widths must be re-checked on the device.
- Mode badges (colour and shape both differ, so they are distinguishable without colour):

| Category | Covers | Badge |
|----------|--------|-------|
| Tram | `route_type` 0 | red `#C8262C`, rounded corners (8 px) |
| Bus | bus and trolleybus (`route_type` 3, 800) | green `#1F7A4D`, pill |
| Train | `route_type` 2 | blue `#1D5FD1`, square |
| Boat | `route_type` 4 | uses the bus badge with "LOĎ" (rare) |

- Live indicator: small blue (`#6EA0EE`) dot before the time; "now" is shown in red (`#F0373E`), the Fčil háček colour.
- Delay shown explicitly as "+N min" before the time from 1 min (grey), amber (`#FFB020`) from 2 min,
  where the time itself also turns amber. Amber is also used for "stale data" and "pinned stop".
- Touch targets at least 44 px.

**Home / departures**
- Clock (small, top), stop name (bold, 36 px), direction "-> Babická, Královo Pole" with the
  reverse button directly next to it, 4 departure rows, footer with stop list button, distance
  to the stop and radar button.
- Rows: badge with line number, headsign (ellipsis if too long), time: "teď"/"now" under 30 s,
  "N min" under 60 min, else "HH:MM".
- Reverse button: switches to the opposite platform as defined in 5.4. Tapping the stop name
  opens the platform picker for big stops.
- Pinned stop (chosen in the list): amber pin icon before the name; tapping the pin returns to
  "closest stop" mode.
- Stale data (no fresh reply for 60 s): rows dimmed to 50 % and an amber line
  "Data před 2 min · bez spojení" / "Data 2 min old · offline".
- Refresh: on open, then every 20 s while the page is visible (`onShow`/`onHide`); the
  "N min" values are re-rendered every 10 s locally from `e - now` without a network call.

**Stop list**
- Title "Nejbližší zastávky" / "Nearest stops"; rows with stop name, mode chips and distance;
  12 entries max, scrolls; the last visible row is dimmed as a scroll hint. Tap opens home for that stop.

**Radar (bonus, F5)**
- No map tiles: lite wearables have no map component and image transfer every few seconds
  would be too heavy. User at centre (red dot), north up, rings at 400 m and 800 m, vehicles
  as mode badges with line number and a small arrow ahead of each badge for its direction of
  travel, nearest stops as hollow dots, the closest stop labelled.
- Direction of travel: the GTFS-RT `bearing` (present for about 90 % of vehicles, any angle;
  0 is also sent by standing vehicles, so it counts as missing), else the direction to the
  vehicle's next stop (backend, `heading` in `realtime/tracker.ts`). Lite wearables cannot rotate
  images, so the watch picks one of 8 pre-drawn arrows (`common/images/arrow0..7.png`). "Aktualizováno před 4 s" / "Updated 4 s ago" at the bottom.
- Implemented with a `stack` of absolutely positioned elements (or `canvas` if available on
  the device API level; check during phase 4).
- Refresh every 15 s while visible (the source updates about every 30 s).

**States**
- Loading: spinner + "Hledám zastávku…" / "Finding stop…".
- Phone not connected: icon, "Telefon není připojen" / "Phone not connected", hint to turn on
  Bluetooth and open the phone app once, "Zkusit znovu" / "Try again" button.
- No departures in the next 2 h: next departure with its time, or "Žádné odjezdy" / "No departures".

### 7.4 Watch-side performance rules

- No more than 1 outstanding request; ignore replies for a page that is no longer shown.
- Timeout 8 s per request, then error state with retry tap.
- Keep DOM small (max 4 departure rows, 12 list items, 15 radar markers).

---

### 7.5 Localisation

- All UI strings live in a small dictionary in `app.js` (`cs` and `en`), selected by the `lg`
  value the phone sends. The lite wearable `i18n` resource folder follows the watch system
  language only, so it is not enough for a user-selectable language.
- Stop names and headsigns are proper names and stay in Czech in both languages.
- Example strings: "Hledám zastávku…" / "Finding stop…", "Nejbližší zastávky" / "Nearest stops",
  "Připojte telefon" / "Connect your phone", "teď" / "now".

## 8. Error handling and edge cases

| Case | Behaviour |
|------|-----------|
| Phone not connected / companion not installed | Watch shows "Připojte telefon" and a hint to open the companion once. |
| No GPS fix | Use last known location if < 10 min old and mark "approx."; else error state. |
| Realtime feed down | Backend returns scheduled times with `lv=0`; watch shows no live dots. |
| GTFS import fails | Keep previous DB; `/health` reports it. |
| Night (no departures within 2 h) | Show next departure time even if later, or "Žádné odjezdy". |
| Regional stop with few departures | Shown like any other stop (F8); if nothing departs within 2 h, the next departure is shown with its time. |
| Times after midnight (`25:10:00`) | Handled via previous service day logic (5.4). |
| Vehicle without `trip_id` | Shown on the radar only, never used for delays. |
| Real-time `stop_id` not found in the trip after normalisation (~5 %) | Use the nearest stop of the trip by distance from the vehicle position. |

---

## 9. Phase 0 spike - results

| # | Item | Result (2026-10-08) |
|---|------|---------------------|
| 1 | GTFS structure | Done, see 3.1. `parent_station` always set; 9 platforms at Česká; `direction_id` filled. |
| 2 | GTFS-RT content | Done, see 3.2. Vehicle positions with `trip_id` (100 % match), zero-padded stop ids, ~30 s cadence, no TripUpdates. |
| 3 | ArcGIS positions / stream | Done, see 3.3. Live stream needs a token; layer is stale; not used. |
| 4 | Licence | GTFS dataset metadata: CC BY. |
| 5 | Watch "Hello world" + Wear Engine round trip, message size limit, `canvas`, Czech diacritics | Open: needs the Huawei developer account and the watch. |
| 6 | Wear Engine permission in AppGallery Connect | Open: developer account sign-up in progress. |

---

## 10. Delivery plan

| Phase | Content | Done when |
|-------|---------|-----------|
| 0 | Spike (section 9) | Items 1-4 done; 5-6 waiting for the Huawei developer account. |
| 1 | Backend: GTFS import, `/nearby`, `/departures` (scheduled only) | Implemented (`backend/`); spot-check 5 stops vs. idos.cz still to do. |
| 2 | Backend realtime: lazy fetch + cache, trip matching, delays, deploy to Cloud Run (implemented; deploy waits for the Google Cloud setup) | `lv=1` on most departures during the day; ETA within 1 min of the stop display boards on spot checks. |
| 3 | Companion app: location, Wear Engine, protocol | Implemented (`android/`); round trip with the watch waits for the Wear Engine permission and the watch app. |
| 4 | Watch app: home + reverse direction + stop list | F1-F4, F6 met on the device. |
| 5 | Bonus: radar (`/vehicles` + page) | F5 met. |
| 6 | Polish: error states, about/attribution, Czech/English, battery check | 1 week of daily use without issues. |
| 7 | Phone app UI + widget (F9) | Implemented ahead of the watch (`android/.../widget`); check on the phone. |

Repository layout:
```
/backend      Node.js/TS service (import, realtime, API)
/companion    Android app (Kotlin)
/watch        DevEco Studio lite wearable JS project
/docs         this spec, decisions, spike results
```

---

## 11. Decisions and open questions

Decided:

- Hosting: Google Cloud Run (5.6).
- Language: Czech and English, switchable in the phone app settings (6, 7.5).
- Stops: all stops in the dataset are included (F8).
- Display categories: tram, bus (incl. trolleybus), train (7.3).
- Phone widget: later phase (6.1, phase 7).
- Devices: Huawei Watch GT 5 46 mm; Nothing Phone (2) with Google Play services.
- Real-time source: KORDIS GTFS-RT only; delays derived (3.2, 5.3).
- Name: "Fčil" (Brno colloquial for "now"); wordmark and icon in `docs/brand/`.

Open:

- Huawei developer account and Wear Engine permission (sign-up in progress).
- Google Cloud account with billing enabled (needed for Cloud Run, even in the free tier).

---

## Sources

- [S1] Jízdní řád IDS JMK ve formátu GTFS / GTFS timetable data - https://data.brno.cz/datasets/379d2e9a7907460c8ca7fda1f3e84328 (mirror: https://datahub.brno.cz/datasets/379d2e9a7907460c8ca7fda1f3e84328)
- [S2] Polohy vozidel hromadné dopravy / Public transit positional data - https://data.brno.cz/datasets/mestobrno::polohy-vozidel-hromadn%C3%A9-dopravy-public-transit-positional-data/about
- [S3] gtfs2-idsjmk (third-party Home Assistant integration documenting the KORDIS feed URLs and content) - https://github.com/dominikgalovic/gtfs2-idsjmk
- [S4] Mobility Database, IDS JMK feed mdb-2155 - https://mobilitydatabase.org/feeds/mdb-2155
- [S5] Brno Daily, "Live Data About The Location of Brno Public Transport Is Now Available on Google Maps" (2023-05-24) - https://brnodaily.com/2023/05/24/brno/live-data-about-the-location-of-brno-public-transport-is-now-available-on-google-maps/
- [S6] (historical) National open data catalogue (NKOD), Public transit positional data - https://data.gov.cz/dataset?iri=https%3A%2F%2Fdata.gov.cz%2Fzdroj%2Fdatov%C3%A9-sady%2F44992785%2F2629f44c90338528b23bcb3a3dbbeb4a
- [S7] ArcGIS layer KAM_pohyb_vozidel_mhd - https://gis.brno.cz/ags1/rest/services/Hosted/KAM_pohyb_vozidel_mhd/FeatureServer/0
- [S8] E-paper departures panel (shows the mapa.idsjmk.cz JSON and its "automated download forbidden" notice) - https://epaper.kubaandrysek.cz/wrapper/mhd/ ; map: https://mapa.idsjmk.cz/
- [S9] Home Assistant client for Huawei watches (GT = lite JS only, phone companion via Wear Engine P2P) - https://github.com/gentslava/Home-Assistant-HarmonyOS-Next
- [S10] Wear Engine lite wearable <-> Android sample (messages and file transfer) - https://github.com/Explore-In-HMOS-Wearable/sportwatch-wear-engine-lite-wearable-to-mobile
- [S11] LiveScore Wearable Demo (phone fetches API, forwards via Wear Engine) - https://github.com/minkiapps/LiveScore-Wearable-Demo
- [S12] P2P communication between Android and HarmonyOS wearable - https://dev.to/harmonyos/p2p-communication-between-android-and-next-wearable-fa-stage-model-device-technical-guide-3d9e
- Wear Engine Kit docs - https://developer.huawei.com/consumer/en/codelabsPortal/carddetails/tutorials_WearEngine-ArkTS
- [S13] Cloud Run free tier summaries - https://agentdeals.dev/vendor/google-cloud-run , https://cloudchipr.com/blog/cloud-run-pricing
- [S14] Azure Container Apps pricing (free grant) - https://www.azure.cn/en-us/pricing/details/container-apps/ , https://freetier.co/directory/products/azure-container-apps , https://cloudtoolstack.com/tools/azure-container-apps-cost-estimator
- [S15] Azure Functions pricing - https://azure.microsoft.com/pricing/details/functions/ ; App Service F1 - https://freetier.co/directory/products/azure-app-service , https://learn.microsoft.com/en-us/answers/questions/541687/i-am-using-f1-tier-for-app-service-and-i-can-only
- [S16] Oracle Always Free A1 reduction report - https://linuxiac.com/oracle-quietly-cuts-free-tier-ampere-a1-resources-in-half/
- [S17] Cloudflare Workers limits - https://developers.cloudflare.com/workers/platform/limits/
- [S18] Render free tier - https://render.com/docs/free
- [S19] Fly.io free tier status - https://agentdeals.dev/vendor/fly-io
- [S20] Huawei Watch GT 5 46 mm specifications - https://www.newmobile.com/Compare/Huawei-Watch-GT-5-46mm/Huawei-Watch-GT-4-46mm/Huawei-Watch-GT/Huawei-Watch-GT-2-46mm/Huawei-Watch-GT-3-46mm/Huawei-Watch-GT-6-46mm , https://www.ad-hoc-news.de/wirtschaft/produkte/huawei-watch-gt-5-46mm-1-43-inch-amoled-display/70114729
