// Pairing with the phone app over Wear Engine (see watch/README.md).
// PHONE_FINGERPRINT: SHA-256 of the phone app's signing certificate, upper case without colons, as
// Huawei's lite wearable guide asks; it must also be registered for the phone app in AppGallery Connect.
// Public information (it is in every signed APK); also in config.json (supportLists).
export const PHONE_PACKAGE = 'io.github.juliajamnicka.fcil';
export const PHONE_FINGERPRINT = 'A1800E7B4C1D93B3F459D84ED784082A822291917E411D376C6EE8DFBEB177ED';

/** No reply from the phone within this time counts as "phone not connected". */
export const REQUEST_TIMEOUT_MS = 8000;
/** Departures are refreshed this often while the home page is shown (docs/SPEC.md 7.3). */
export const HOME_REFRESH_MS = 20000;
export const RADAR_REFRESH_MS = 15000;
/** Data older than this is shown dimmed as stale. */
export const STALE_AFTER_S = 60;
