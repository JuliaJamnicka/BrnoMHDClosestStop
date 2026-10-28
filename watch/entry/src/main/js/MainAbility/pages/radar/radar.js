import { request, cancel } from '../../common/link.js';
import { radarMarkers, describeError, shortStopName, sameStop } from '../../common/format.js';
import { RADAR_REFRESH_MS } from '../../common/config.js';
import { t } from '../../common/strings.js';
import state, { go, restore } from '../../common/state.js';

const SIZE = 466;
const RADIUS_PX = 200; // outer ring
const RANGE_M = 800;

let timer = null;
let step = ''; // the statement running, for the error text (see pages/index)

export default {
    data: {
        nav: '', // shared state from the previous page (common/state.js)
        markers: [],
        arrows: [],
        stops: [],
        stopLabels: [],
        // only while loading or after an error, in place of an "updated" line (it took the space)
        status: '',
        hasStatus: true,
        stopName: '',
        hasStopName: false,
    },

    onInit() {
        restore(this);
        this.status = t(state.lang, 'loading');
        // the stop of the departures page, named above the user as on the Radar board
        this.stopName = state.lastDep ? state.lastDep.s : '';
        this.hasStopName = this.stopName !== '';
        this.load();
        const self = this;
        timer = setInterval(function () {
            self.load();
        }, RADAR_REFRESH_MS);
    },

    onDestroy() {
        clearInterval(timer);
        cancel();
    },

    load() {
        const self = this;
        request({ c: 'veh' }, function (reply) {
            try {
                self.showReply(reply);
            } catch (e) {
                self.showStatus(describeError(e) + ' @' + step);
            }
        });
    },

    showReply(reply) {
        if (reply.c !== 'veh') {
            this.showStatus(t(state.lang, 'err_' + reply.e));
            return;
        }
        step = 'markers-compute';
        const markers = radarMarkers(reply.x || [], SIZE, RADIUS_PX, RANGE_M);
        step = 'markers-set';
        this.markers = markers;
        step = 'arrows';
        this.arrows = markers.filter(function (m) {
            return !!m.arrow;
        });
        step = 'stops-compute';
        const stops = [];
        const labels = [];
        const list = reply.s || [];
        const scale = RADIUS_PX / RANGE_M;
        for (let i = 0; i < list.length; i++) {
            const x = Math.round(SIZE / 2 + list[i][1] * scale);
            const y = Math.round(SIZE / 2 - list[i][2] * scale);
            stops.push({ left: x - 7, top: y - 7 });
            if (this.stopName && sameStop(list[i][0], this.stopName)) continue;
            // right of the dot, kept inside the screen
            labels.push({
                text: shortStopName(list[i][0]),
                left: Math.max(20, Math.min(SIZE - 90, x + 10)),
                top: Math.max(20, Math.min(SIZE - 56, y - 18)),
            });
        }
        step = 'stops-set';
        this.stops = stops;
        this.stopLabels = labels;
        this.hasStatus = false;
    },

    showStatus(text) {
        this.status = text;
        this.hasStatus = true;
    },

    onSwipe(e) {
        if (e.direction === 'right') go('pages/index/index');
    },
};
