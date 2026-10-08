// Lazily fetched KORDIS GTFS-RT feed (docs/SPEC.md 3.2, 5.3).
// The file changes every ~30 s but is served with max-age=86400, so we always
// revalidate with the ETag instead of trusting HTTP caches.

import GtfsRealtimeBindings from 'gtfs-realtime-bindings';
import { RtVehicle, Tracker } from './tracker.js';

export const DEFAULT_FEED_URL = 'https://kordis-jmk.cz/gtfs/gtfsReal.dat';

const { transit_realtime } = GtfsRealtimeBindings;

export function decodeFeed(buffer: Uint8Array): { timestamp: number; vehicles: RtVehicle[] } {
  const feed = transit_realtime.FeedMessage.decode(buffer);
  const vehicles: RtVehicle[] = [];
  for (const entity of feed.entity) {
    const v = entity.vehicle;
    if (!v?.position) continue;
    vehicles.push({
      vehicleId: v.vehicle?.id ?? entity.id,
      tripId: v.trip?.tripId || undefined,
      stopId: v.stopId || undefined,
      status: v.currentStatus ?? 2,
      ts: Number(v.timestamp ?? feed.header.timestamp ?? 0),
      lat: v.position.latitude,
      lon: v.position.longitude,
      bearing: v.position.bearing ?? undefined,
    });
  }
  return { timestamp: Number(feed.header.timestamp ?? 0), vehicles };
}

export interface FeedStatus {
  /** Header timestamp of the last decoded feed (epoch seconds), 0 if none yet. */
  feedTimestamp: number;
  lastCheck: number;
  lastError?: string;
}

export class RealtimeFeed {
  private etag?: string;
  private lastCheck = 0;
  private feedTimestamp = 0;
  private lastError?: string;
  private inFlight?: Promise<void>;

  constructor(
    private readonly tracker: Tracker,
    private readonly url = DEFAULT_FEED_URL,
    private readonly ttlSec = 15,
    private readonly timeoutMs = 4000,
  ) {}

  /** Makes sure the tracker reflects a feed no older than the TTL; never throws. */
  async refresh(now = Math.floor(Date.now() / 1000)): Promise<void> {
    if (now - this.lastCheck < this.ttlSec) return;
    this.inFlight ??= this.fetchOnce(now).finally(() => {
      this.inFlight = undefined;
    });
    return this.inFlight;
  }

  status(): FeedStatus {
    return { feedTimestamp: this.feedTimestamp, lastCheck: this.lastCheck, lastError: this.lastError };
  }

  private async fetchOnce(now: number): Promise<void> {
    try {
      const res = await fetch(this.url, {
        headers: this.etag ? { 'If-None-Match': this.etag } : {},
        cache: 'no-store',
        signal: AbortSignal.timeout(this.timeoutMs),
      });
      this.lastCheck = now;
      if (res.status === 304) return;
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const decoded = decodeFeed(new Uint8Array(await res.arrayBuffer()));
      this.etag = res.headers.get('etag') ?? undefined;
      this.feedTimestamp = decoded.timestamp;
      this.tracker.update(decoded.vehicles, now);
      this.lastError = undefined;
    } catch (err) {
      // Keep serving the previous state; retry after the TTL.
      this.lastCheck = now;
      this.lastError = err instanceof Error ? err.message : String(err);
    }
  }
}
