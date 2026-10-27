import app from '@system.app';
import { request, now, cancel } from '../../common/link.js';
import { departureRows, distanceLabel, wrapLines, describeError } from '../../common/format.js';
import { HOME_REFRESH_MS, STALE_AFTER_S } from '../../common/config.js';
import { t } from '../../common/strings.js';
import state, { go, restore } from '../../common/state.js';

// Brand-coloured pictures for the link errors (Fčil logo when the phone app is missing).
const ERROR_ICONS = {
    noapp: '/common/images/err-noapp.png',
    closed: '/common/images/err-closed.png',
    phone: '/common/images/err-phone.png',
    far: '/common/images/err-far.png',
};
const HINT_CHARS = 22; // ~15 px per character at 30 px, 400 px lines, with margin for wide letters

// Page-level variables (lite wearable pages only keep data and methods on `this`).
let reply = null;
let receivedAt = 0;
let refreshTimer = null;
let tickTimer = null;
let step = '';

export default {
    data: {
        nav: '', // shared state from the previous page (common/state.js)
        view: 'loading',
        loadingText: '',
        loadingSubText: '',
        errorTitle: '',
        errorLines: [],
        errorIcon: '',
        hasErrorIcon: false,
        retryText: '',
        noDeparturesText: '',
        stop: '',
        direction: '',
        distance: '',
        showDistance: false,
        pinned: false,
        canReverse: false,
        rows: [],
        empty: false,
        staleText: '',
        isStale: false,
    },
    onInit() {
        restore(this);
        this.applyTexts();
        if (state.lastDep) {
            // returning from another page: show the last data at once, then refresh
            reply = state.lastDep;
            this.showDepartures();
        }
        this.load();
        const self = this;
        refreshTimer = setInterval(function () {
            self.load();
        }, HOME_REFRESH_MS);
        tickTimer = setInterval(function () {
            try {
                self.showDepartures();
            } catch (e) {
                self.showScriptError(e);
            }
        }, 10000);
    },

    onDestroy() {
        clearInterval(refreshTimer);
        clearInterval(tickTimer);
        cancel();
    },

    applyTexts() {
        this.loadingText = t(state.lang, 'loading');
        this.loadingSubText = t(state.lang, 'loadingSub');
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
        try {
            this.showReply(answer);
        } catch (e) {
            this.showScriptError(e);
        }
    },

    showScriptError(e) {
        this.errorTitle = t(state.lang, 'err_req');
        this.hasErrorIcon = false;
        this.errorLines = wrapLines(describeError(e) + ' @' + step, HINT_CHARS);
        this.view = 'error';
    },

    showReply(answer) {
        step = 'reply';
        this.applyTexts();
        if (answer.c === 'dep') {
            reply = answer;
            receivedAt = now();
            state.lastDep = answer;
            this.showDepartures();
        } else if (answer.c === 'err') {
            if (reply) {
                this.showDepartures(); // keep showing the last data; it turns stale after a minute
            } else {
                this.errorTitle = t(state.lang, 'err_' + answer.e);
                this.errorIcon = ERROR_ICONS[answer.e] || '';
                this.hasErrorIcon = this.errorIcon !== '';
                this.errorLines = wrapLines(t(state.lang, 'err_' + answer.e + '_hint'), HINT_CHARS);
                this.view = 'error';
            }
        }
    },

    // not "render": the framework uses that name for the compiled template
    showDepartures() {
        const r = reply;
        if (!r) return;
        // `step` names the statement running, for the error screen (the watch reports a bare
        // "TypeError" without a place)
        step = 'now';
        const current = now();
        step = 'stop';
        this.stop = r.s;
        step = 'direction';
        this.direction = r.r ? '→ ' + r.r : ''; // a stop list (r.l) has no single direction
        step = 'distance';
        this.distance = distanceLabel(r.d);
        step = 'pinned';
        this.pinned = !!state.pinned;
        this.canReverse = !!r.o;
        const age = receivedAt ? current - receivedAt : 0;
        const stale = age > STALE_AFTER_S;
        step = 'rows-compute';
        const rows = departureRows(r, current, t(state.lang, 'now'), stale);
        step = 'rows-set';
        this.rows = rows;
        this.empty = rows.length === 0;
        // stale data dims the rows and takes the distance's place in the footer
        step = 'stale';
        this.isStale = stale;
        this.staleText = stale ? t(state.lang, 'stale', { m: Math.floor(age / 60) }) : '';
        this.showDistance = !stale && this.distance !== '';
        step = 'view';
        this.view = 'dep';
        step = 'done';
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
        go('pages/stops/stops');
    },

    openPlatforms() {
        if (reply && reply.p) go('pages/platforms/platforms');
    },

    openRadar() {
        go('pages/radar/radar');
    },

    onSwipe(e) {
        if (e.direction === 'right') app.terminate();
        else if (e.direction === 'left') this.openRadar();
        else if (e.direction === 'up') this.openStops();
    },
};
