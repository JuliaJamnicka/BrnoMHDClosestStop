import app from '@system.app';
import { request, now, cancel } from '../../common/link.js';
import { departureRows, distanceLabel, wrapLines, describeError, textWidth } from '../../common/format.js';
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
let shownCount = -1;
const VISIBLE_ROWS = 4; // .rows: 4 rows of 51 px
const ROWS_HEIGHT = 204;

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
        stopWidth: 300,
        direction: '',
        distance: '',
        showDistance: false,
        pinned: false,
        canReverse: false,
        rows: [],
        empty: false,
        // scroll track beside the rows; scrolltop/scrollbottom are the only scroll positions a lite list reports
        scrollable: false,
        trackColour: '#000000',
        thumbTop: 0,
        thumbHeight: 0,
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
        // centred in the header; with the pin beside it the name's box fits the name (300 px at most
        // with the 36 px pin), so the pin is not left out at the edge of the round screen
        this.stopWidth = this.pinned ? Math.min(264, textWidth(r.s, 38) + 4) : 300;
        this.canReverse = !!r.o;
        const age = receivedAt ? current - receivedAt : 0;
        const stale = age > STALE_AFTER_S;
        step = 'rows-compute';
        const rows = departureRows(r, current, t(state.lang, 'now'), stale);
        step = 'rows-set';
        this.rows = rows;
        this.empty = rows.length === 0;
        step = 'scroll';
        this.updateScroll(rows.length);
        // stale data dims the rows and takes the distance's place in the footer
        step = 'stale';
        this.isStale = stale;
        this.staleText = stale ? t(state.lang, 'stale', { m: Math.floor(age / 60) }) : '';
        this.showDistance = !stale && this.distance !== '';
        step = 'view';
        this.view = 'dep';
        step = 'done';
    },

    updateScroll(count) {
        const scrollable = count > VISIBLE_ROWS;
        if (scrollable === this.scrollable && count === shownCount) return;
        shownCount = count;
        this.scrollable = scrollable;
        this.trackColour = scrollable ? '#16213a' : '#000000';
        this.thumbHeight = scrollable ? Math.round((ROWS_HEIGHT * VISIBLE_ROWS) / count) : 0;
        this.thumbTop = 0;
    },

    scrolledTop() {
        this.thumbTop = 0;
    },

    scrolledBottom() {
        this.thumbTop = ROWS_HEIGHT - this.thumbHeight;
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
        // no swipe up for the stop list: that scrolls the departures (the ≡ button opens it)
    },
};
