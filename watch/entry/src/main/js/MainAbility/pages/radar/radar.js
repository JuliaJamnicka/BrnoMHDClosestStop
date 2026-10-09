import router from '@system.router';
import { request, now, cancel } from '../../common/link.js';
import { radarMarkers } from '../../common/format.js';
import { RADAR_REFRESH_MS } from '../../common/config.js';
import { t } from '../../common/strings.js';
import state from '../../common/state.js';

const SIZE = 466;
const RADIUS_PX = 200; // outer ring
const RANGE_M = 800;

let timer = null;
let tick = null;
let receivedAt = 0;

export default {
    data: {
        markers: [],
        stops: [],
        status: '',
    },

    onInit() {
        this.status = t(state.lang, 'loading');
        this.load();
        const self = this;
        timer = setInterval(function () {
            self.load();
        }, RADAR_REFRESH_MS);
        tick = setInterval(function () {
            self.updateStatus();
        }, 5000);
    },

    onDestroy() {
        clearInterval(timer);
        clearInterval(tick);
        cancel();
    },

    load() {
        const self = this;
        request({ c: 'veh' }, function (reply) {
            if (reply.c !== 'veh') {
                self.status = t(state.lang, 'err_' + reply.e);
                return;
            }
            receivedAt = now();
            self.markers = radarMarkers(reply.x || [], SIZE, RADIUS_PX, RANGE_M);
            const stops = [];
            const list = reply.s || [];
            const scale = RADIUS_PX / RANGE_M;
            for (let i = 0; i < list.length; i++) {
                stops.push({
                    left: Math.round(SIZE / 2 + list[i][1] * scale - 7),
                    top: Math.round(SIZE / 2 - list[i][2] * scale - 7),
                });
            }
            self.stops = stops;
            self.updateStatus();
        });
    },

    updateStatus() {
        if (receivedAt) this.status = t(state.lang, 'updated', { s: Math.max(0, now() - receivedAt) });
    },

    onSwipe(e) {
        if (e.direction === 'right') router.replace({ uri: 'pages/home/home' });
    },
};
