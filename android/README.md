# Android phone app

Companion app for the watch (docs/SPEC.md, section 6). It answers the watch's requests over
Huawei Wear Engine, using the phone's location and the backend API. It also has a settings
screen (language, number of departures) and a "Departures here" preview, so it can be tested before the watch app exists.

- Kotlin, Jetpack Compose, minSdk 26, targetSdk 35
- Package name: `io.github.juliajamnicka.brnomhd`
- Huawei Wear Engine SDK `com.huawei.hms:wearengine:5.0.3.302` (from `https://developer.huawei.com/repo/`)
- Google Fused Location Provider, OkHttp, kotlinx.serialization, DataStore

## Structure

| Path | What it does |
|------|--------------|
| `data/ApiClient.kt` | Calls `/v1/home`, `/v1/departures`, `/v1/nearby`, `/v1/vehicles` with the `x-api-key` header |
| `data/LocationSource.kt` | Last known location if < 30 s old and < 50 m accurate, else a fresh fix (4 s timeout) |
| `data/TransitRepository.kt` | Location + API; caches the home response for 20 s |
| `data/Settings.kt` | Language and number of departures (DataStore) |
| `wear/WatchProtocol.kt` | Message format between watch and phone (kept under 1000 bytes) |
| `wear/RequestHandler.kt` | Watch request -> reply bytes, errors mapped to codes the watch shows |
| `wear/WearBridge.kt` | Wear Engine: finds the paired watch, receives and sends P2P messages |
| `wear/WatchService.kt` | Foreground service that keeps the receiver alive; idle until a message arrives |
| `ui/` | Settings screen (design: phone settings artboard on the design canvas) |

The `data` package does not depend on Wear Engine, so the later home-screen widget can reuse it.

### Watch protocol

Watch -> phone: `{"c":"home"}`, `{"c":"dep","p":"U1073Z2"}`, `{"c":"pl","p":"U1073Z2"}`,
`{"c":"near"}`, `{"c":"veh"}`.

Phone -> watch (lists are positional arrays to save space):

```json
{"c":"dep","lg":"cs","t":1791489562,"g":"U1073N2860","s":"Česká","p":"U1073Z1","o":"U1073Z2",
 "r":"Náměstí Míru, Starý…","d":20,"x":[["6","T","Starý Lískovec, sm.",1791489660,0,1]]}
```

`x` rows: line, mode (T/B/V/L), headsign, expected departure (epoch s), delay (min), live (0/1).
Other replies: `pl` (platform picker), `near` (stop list), `veh` (radar) and
`{"c":"err","e":"noloc|net|srv|auth|req"}`. Exact field order is documented in `WatchProtocol.kt`.

## Building

Open the `android/` folder in Android Studio, or:

```sh
cd android
./gradlew testDebugUnitTest lintDebug assembleDebug   # APK: app/build/outputs/apk/debug/app-debug.apk
```

Optional settings in `android/local.properties` (not committed) or environment variables:

| Property | Env var | Meaning |
|----------|---------|---------|
| `mhdApiKey` | `MHD_API_KEY` | Backend API key baked into the build (GitHub: repository secret `MHD_API_KEY`) |
| `mhdApiUrl` | `MHD_API_URL` | Backend URL (default: the Cloud Run service) |
| `watchPackage` | `WATCH_PACKAGE` | Package name of the watch app (default `io.github.juliajamnicka.brnomhd.watch`) |
| `watchFingerprint` | `WATCH_FINGERPRINT` | Signing fingerprint of the watch app (from DevEco Studio) |
| `signingStoreFile`, `signingStorePassword`, `signingKeyAlias`, `signingKeyPassword` | `SIGNING_*` | Stable signing key, see below |

Every push that changes `android/` builds the app on GitHub (`.github/workflows/android.yml`).
The APK is attached to the workflow run as the artifact `brno-mhd-debug-apk`.

## Installing on the phone

1. Download `brno-mhd-debug-apk` from the latest successful **android** workflow run (GitHub > Actions),
   unzip it and open `app-debug.apk` on the phone (allow installing from your browser or files app).
2. Open the app and allow location. The API key is built in from the GitHub secret `MHD_API_KEY`
   (local builds: `mhdApiKey` in `local.properties`); there is no setting for it in the app.
3. **Departures here > Load departures** should list the departures at your nearest stop.
4. **Connect watch** needs the Huawei Health app with the watch paired, and the Wear Engine
   permission for this app (applied for in AppGallery Connect). Until then it shows an error,
   which is expected.

## Stable signing key (needed for Wear Engine)

Huawei Wear Engine identifies the phone app by package name and the SHA-256 fingerprint of its
signing certificate, and the watch app lists the same pair in its `config.json`. Debug builds
from GitHub are otherwise signed with a random key each time, so create one key and reuse it:

```sh
keytool -genkeypair -v -keystore brnomhd.jks -alias brnomhd -keyalg RSA -keysize 2048 -validity 10000
keytool -list -v -keystore brnomhd.jks -alias brnomhd | grep SHA256   # the fingerprint to register
base64 -w0 brnomhd.jks > brnomhd.jks.b64                               # value for the GitHub secret
```

Keep `brnomhd.jks` and its password safe (not in the repository). Then add GitHub
**secrets** `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD` and `SIGNING_KEY_PASSWORD`.

## Permissions

| Permission | Why |
|------------|-----|
| Location (precise) | Find the closest stop |
| Location "Allow all the time" (optional) | Keep working after a phone restart, when the service starts without the app being opened |
| Notifications | Android requires a visible notification for the always-on watch link |
| Foreground service (connected device, location) | Keep the Wear Engine receiver alive and read the location while the phone is in the pocket |
| Start at boot | Restore the watch link after a restart if it was on |
