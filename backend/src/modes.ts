// Display categories shown on the watch (see docs/SPEC.md, 5.5 and 7.3).
// Trolleybuses are shown as buses on purpose.

export type Mode = 'T' | 'B' | 'V' | 'L';

export function modeFromRouteType(routeType: number): Mode {
  switch (routeType) {
    case 0:
    case 900:
      return 'T';
    case 2:
    case 100:
    case 109:
      return 'V';
    case 4:
    case 1200:
      return 'L';
    default:
      // 3 bus, 11 and 800 trolleybus, 7xx bus variants
      return 'B';
  }
}

/** Orders modes for display: tram, bus, train, boat. */
export function sortModes(modes: Iterable<string>): string {
  const order = 'TBVL';
  return [...new Set(modes)].sort((a, b) => order.indexOf(a) - order.indexOf(b)).join('');
}

/** Natural sort for line names: 1, 2, 10, N89, S2, x75. */
export function compareLines(a: string, b: string): number {
  return a.localeCompare(b, 'cs', { numeric: true });
}
