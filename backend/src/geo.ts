const EARTH_RADIUS_M = 6371000;

/** Great-circle distance in metres. */
export function distanceM(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const toRad = Math.PI / 180;
  const dLat = (lat2 - lat1) * toRad;
  const dLon = (lon2 - lon1) * toRad;
  const a =
    Math.sin(dLat / 2) ** 2 + Math.cos(lat1 * toRad) * Math.cos(lat2 * toRad) * Math.sin(dLon / 2) ** 2;
  return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
}

/** Offset of point 2 from point 1 in metres east (dx) and north (dy); accurate enough within a few km. */
export function offsetM(lat1: number, lon1: number, lat2: number, lon2: number): { dx: number; dy: number } {
  const toRad = Math.PI / 180;
  const dx = (lon2 - lon1) * toRad * EARTH_RADIUS_M * Math.cos(((lat1 + lat2) / 2) * toRad);
  const dy = (lat2 - lat1) * toRad * EARTH_RADIUS_M;
  return { dx: Math.round(dx), dy: Math.round(dy) };
}

/** Compass bearing from point 1 to point 2 in degrees (0 north, 90 east); short distances only. */
export function bearingDeg(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const { dx, dy } = offsetM(lat1, lon1, lat2, lon2);
  return (Math.round((Math.atan2(dx, dy) * 180) / Math.PI) + 360) % 360;
}
