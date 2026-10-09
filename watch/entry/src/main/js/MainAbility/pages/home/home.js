import app from '@system.app';
import router from '@system.router';
import { request, now, cancel } from '../../common/link.js';
import { departureRows, distanceLabel, wrapLines } from '../../common/format.js';
import { HOME_REFRESH_MS, STALE_AFTER_S } from '../../common/config.js';
import { t } from '../../common/strings.js';
import state from '../../common/state.js';

const HINT_CHARS = 22; // ~15 px per character at 30 px, 400 px lines, with margin for wide letters

// Page-level variables (lite wearable pages only keep data and methods on `this`).
let reply = null;
let receivedAt = 0;
let refreshTimer = null;
let tickTimer = null;

export default {
    data: {
        view: 'loading',
        loadingText: '',
        errorTitle: '',
        errorLines: [],
        retryText: '',
        noDeparturesText: '',
        stop: '',
        direction: '',
        distance: '',
        pinned: false,
        canReverse: false,
        rows: [],
        empty: false,
        staleText: '',
    },
    onInit() {
        this.applyTexts();
        if (state.lastDep) {
            // returning from another page: show the last data at once, then refresh
            reply = state.lastDep;
            this.render();
        }
        this.load();
        const self = this;
        refreshTimer = setInterval(function () {
            self.load();
        }, HOME_REFRESH_MS);
        tickTimer = setInterval(function () {
            self.render();
        }, 10000);
    },

    onDestroy() {
        clearInterval(refreshTimer);
        clearInterval(tickTimer);
        cancel();
    },

    applyTexts() {
        this.loadingText = t(state.lang, 'loading');
        this.retryText = t(state.lang, 'retry');
        this.noDeparturesText = t(state.lang, 'noDepartures');
    },

    load() {
        const self = this;
        const body = state.pinned ? { c: 'dep', p: state.pinned } : { c: 'home' };
        request(body, function (answer) {
            self.onReply(answer);
        });
    },

    onReply(answer) {
        this.applyTexts();
        if (answer.c === 'dep') {
            reply = answer;
            receivedAt = now();
            state.lastDep = answer;
            this.render();
        } else if (answer.c === 'err') {
            if (reply) {
                this.render(); // keep showing the last data; it turns stale after a minute
            } else {
                this.errorTitle = t(state.lang, 'err_' + answer.e);
                this.errorLines = wrapLines(t(state.lang, 'err_' + answer.e + '_hint'), HINT_CHARS);
                this.view = 'error';
            }
        }
    },

    render() {
        const r = reply;
        if (!r) return;
        const current = now();
        this.stop = r.s;
        this.direction = r.r ? '→ ' + r.r : ''; // a stop list (r.l) has no single direction
        this.distance = distanceLabel(r.d);
        this.pinned = !!state.pinned;
        this.canReverse = !!r.o;
        this.rows = departureRows(r, current, t(state.lang, 'now'));
        this.empty = this.rows.length === 0;
        const age = receivedAt ? current - receivedAt : 0;
        const stale = age > STALE_AFTER_S;
        this.staleText = stale ? t(state.lang, 'stale', { m: Math.floor(age / 60) }) : '';
        this.view = 'dep';
    },

    reverse() {
        if (!reply || !reply.o) return;
        state.pinned = reply.o;
        this.load();
    },

    unpin() {
        state.pinned = null;
        this.load();
    },

    openStops() {
        router.replace({ uri: 'pages/stops/stops' });
    },

    openPlatforms() {
        if (reply && reply.p) router.replace({ uri: 'pages/platforms/platforms' });
    },

    openRadar() {
        router.replace({ uri: 'pages/radar/radar' });
    },

    onSwipe(e) {
        if (e.direction === 'right') app.terminate();
        else if (e.direction === 'left') this.openRadar();
        else if (e.direction === 'up') this.openStops();
    },
};
