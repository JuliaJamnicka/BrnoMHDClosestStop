# Fčil for Garmin watches

A Connect IQ watch app (Monkey C) for colour Garmin watches. Like the Huawei app in `watch/`, it
has no network or GPS work of its own: it asks the Fčil phone app through Garmin Connect, and the
phone answers with the same replies it sends the Huawei watch (`android/.../wear/WatchProtocol.kt`),
as Monkey C dictionaries instead of JSON text (`GarminMessages.kt`).

| Screen | Shows | Controls |
|--------|-------|----------|
| Departures (`DeparturesView.mc`) | háček mark, stop, direction, departures (as many as fit, the rest scroll), distance | UP/DOWN or swipe: scroll; START or tap: menu; BACK: close |
| Menu (`Menus.mc`) | Stops, Opposite direction, Platforms, Nearest (automatic) when a stop is chosen, Radar | native list |
| Stops / Platforms | nearest stops with distance and modes / platforms of the stop with lines and direction | pick one to show its departures |
| Radar (`RadarView.mc`) | 800 m around you, north up: stops (five letters), vehicles with a heading arrow | BACK: departures |

Designs: the Garmin row on the canvas (Garmin: Departures, Garmin: Radar).

## Setup (once)

1. Install [Visual Studio Code](https://code.visualstudio.com/), the **Monkey C** extension and the
   [Connect IQ SDK Manager](https://developer.garmin.com/connect-iq/sdk/). In the SDK Manager,
   sign in with a Garmin account, download the current SDK and the devices you want (at least
   your own watch).
2. In VS Code run **Monkey C: Generate a Developer Key** and keep the key file outside the
   repository (it signs your builds; `.gitignore` excludes `*.der`).
3. Open this `garmin/` folder in VS Code.

## Try it in the simulator

**Run > Run Without Debugging** builds for a watch and opens it in the Connect IQ simulator. Without the
phone app it shows "Telefon není připojen" or, after a while, "Fčil v telefonu neběží".

To reach the phone app from the simulator: build the phone app with `garminTethered=true` (in
`android/local.properties`, or `GARMIN_TETHERED=true`), connect the phone by USB, run
`adb forward tcp:7381 tcp:7381`, choose **Connect watch > Garmin** in the app and start the link from the
simulator's **Connection** menu. Connect IQ SDK 9.1 and 9.2 have a known simulator crash when the watch sends
to the phone over this link
([bug report](https://forums.garmin.com/developer/connect-iq/i/bug-reports/connect-iq-simulator-9-1-0-crashes-on-first-communications-transmit-over-tethered-connection));
if it happens, try another SDK version in the SDK Manager.

## On the watch

1. **Monkey C: Build for Device**, pick your watch: it writes a `.prg` file.
2. Connect the watch by USB and copy the `.prg` into `GARMIN/APPS` on it.
   ([Garmin FAQ](https://forums.garmin.com/developer/connect-iq/w/wiki/4/new-developer-faq/revision/20)).
3. In the phone app: **Settings > Connect watch > Garmin** (Garmin Connect must be installed with
   the watch paired). Then open Fčil on the watch.

## Notes

- The app id in `manifest.xml` is the one the phone sends to (`GARMIN_APP_ID` in
  `android/app/build.gradle.kts`, without dashes). Change both together or not at all.
- The products in `manifest.xml` are the colour watches with Connect IQ 3.2+ whose ids the SDK's own
  samples use. Add more with **Monkey C: Edit Products**.
- The language comes from the phone app's replies and is kept for the next start
  (`Application.Storage`), as on the Huawei watch.
- Not checked on a real Garmin watch yet. The code builds with SDK 9.3.0, checked against a
  stand-in device definition, because the real ones need a Garmin login.
