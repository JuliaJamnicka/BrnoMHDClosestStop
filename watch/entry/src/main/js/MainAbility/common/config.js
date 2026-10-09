// Pairing with the phone app over Wear Engine (see watch/README.md).
// PHONE_FINGERPRINT: the phone app's signing fingerprint, in the format Huawei's Wear Engine docs
// describe for the lite-wearable peer fingerprint. Fill it in before building; never commit secrets here.
export const PHONE_PACKAGE = 'io.github.juliajamnicka.fcil';
export const PHONE_FINGERPRINT = 'PHONE_APP_FINGERPRINT';

/** No reply from the phone within this time counts as "phone not connected". */
export const REQUEST_TIMEOUT_MS = 8000;
/** Departures are refreshed this often while the home page is shown (docs/SPEC.md 7.3). */
export const HOME_REFRESH_MS = 20000;
export const RADAR_REFRESH_MS = 15000;
/** Data older than this is shown dimmed as stale. */
export const STALE_AFTER_S = 60;
