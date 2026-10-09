import { shutdown } from './common/link.js';

export default {
  onCreate() {},
  onDestroy() {
    shutdown();
  },
};
