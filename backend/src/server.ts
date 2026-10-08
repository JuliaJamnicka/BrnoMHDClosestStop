import { buildApp } from './app.js';
import { DEFAULT_FEED_URL, RealtimeFeed } from './realtime/feed.js';
import { Tracker } from './realtime/tracker.js';
import { Timetable } from './timetable.js';

const timetable = new Timetable(process.env.TIMETABLE_DB ?? 'data/timetable.db');
const tracker = new Tracker(timetable);
const feed = new RealtimeFeed(tracker, process.env.REALTIME_URL ?? DEFAULT_FEED_URL);

const app = buildApp({
  timetable,
  tracker,
  feed,
  apiKey: process.env.API_KEY || undefined,
  logger: true,
});

const port = Number(process.env.PORT ?? 8080);
app.listen({ port, host: '0.0.0.0' }).catch((err) => {
  app.log.error(err);
  process.exit(1);
});

for (const signal of ['SIGINT', 'SIGTERM'] as const) {
  process.on(signal, () => {
    app.close().finally(() => {
      timetable.close();
      process.exit(0);
    });
  });
}
