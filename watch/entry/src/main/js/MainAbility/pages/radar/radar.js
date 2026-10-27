import router from '@system.router';
import { request, now, cancel } from '../../common/link.js';
import { radarMarkers, describeError } from '../../common/format.js';
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
        arrows: [],
        stops: [],
        status: '',
        stopName: '',
        hasStopName: false,
    },

    onInit() {
        this.status = t(state.lang, 'loading');
        // the stop of the departures page, named above the user as on the Radar board
        this.stopName = state.lastDep ? state.lastDep.s : '';
        this.hasStopName = this.stopName !== '';
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
            try {
                self.showReply(reply);
            } catch (e) {
                self.status = describeError(e);
            }
        });
    },

    showReply(reply) {
        if (reply.c !== 'veh') {
            this.status = t(state.lang, 'err_' + reply.e);
            return;
        }
        receivedAt = now();
        const markers = radarMarkers(reply.x || [], SIZE, RADIUS_PX, RANGE_M);
        this.markers = markers;
        this.arrows = markers.filter(function (m) {
            return !!m.arrow;
        });
        const stops = [];
        const list = reply.s || [];
        const scale = RADIUS_PX / RANGE_M;
        for (let i = 0; i < list.length; i++) {
            stops.push({
                left: Math.round(SIZE / 2 + list[i][1] * scale - 7),
                top: Math.round(SIZE / 2 - list[i][2] * scale - 7),
            });
        }
        this.stops = stops;
        this.updateStatus();
    },

    updateStatus() {
        try {
            if (receivedAt) this.status = t(state.lang, 'updated', { s: Math.max(0, now() - receivedAt) });
        } catch (e) {
            this.status = describeError(e);
        }
    },

    onSwipe(e) {
        if (e.direction === 'right') router.replace({ uri: 'pages/index/index' });
    },
};
