import { request, cancel } from '../../common/link.js';
import {
    radarMarkers,
    describeError,
    shortStopName,
    ringLabel,
    placeStopLabels,
    textWidth,
    vehicleCard,
    findVehicle,
    ownLines,
    RADAR_RANGES,
} from '../../common/format.js';
import { RADAR_REFRESH_MS } from '../../common/config.js';
import { t, errorKey } from '../../common/strings.js';
import state, { go, restore } from '../../common/state.js';

const SIZE = 466;
const RADIUS_PX = 200; // outer ring
// Where pages/radar/radar.css puts the ring labels, "N", the user's dot and the zoom slider: stop
// names keep clear of them
const INNER_LABEL = { left: 300, top: 150 };
const OUTER_LABEL = { left: 296, top: 66 };
const FIXED_BOXES = [
    { left: 213, top: 0, width: 40, height: 36 },
    { left: 216, top: 216, width: 34, height: 34 },
    { left: 183, top: 426, width: 100, height: 16 },
];

let timer = null;
let step = ''; // the statement running, for the error text (see pages/index)
let lastReply = null; // for redrawing on zoom without asking the phone
let shown = []; // the vehicles behind the markers, by marker index
let cardVehicle = null; // the tapped vehicle, found again after each refresh

export default {
    data: {
        nav: '', // shared state from the previous page (common/state.js)
        markers: [],
        arrows: [],
        stops: [],
        stopLabels: [],
        zoom: 1,
        innerLabel: '',
        outerLabel: '',
        card: {},
        hasCard: false,
        // only while loading or after an error, in place of an "updated" line (it took the space)
        status: '',
        hasStatus: true,
    },

    onInit() {
        restore(this);
        lastReply = null;
        shown = [];
        cardVehicle = null;
        this.zoom = state.radarZoom >= 0 && state.radarZoom < RADAR_RANGES.length ? state.radarZoom : 1;
        this.showRange();
        this.status = t(state.lang, 'loading');
        this.load();
        const self = this;
        timer = setInterval(function () {
            self.load();
        }, RADAR_REFRESH_MS);
    },

    onShow() {
        const self = this;
        setTimeout(function () {
            try {
                self.$refs.zoom.rotation({ focus: true });
            } catch (e) {
                // no crown support: tapping the ring labels zooms too
            }
        }, 100);
    },

    onDestroy() {
        clearInterval(timer);
        cancel();
    },

    load() {
        const self = this;
        request({ c: 'veh', r: RADAR_RANGES[this.zoom], m: ownLines(state.lastDep) }, function (reply) {
            try {
                self.showReply(reply);
            } catch (e) {
                self.showStatus(describeError(e) + ' @' + step);
            }
        });
    },

    showReply(reply) {
        if (reply.c !== 'veh') {
            this.showStatus(t(state.lang, errorKey(reply.e)));
            return;
        }
        lastReply = reply;
        this.draw();
        this.hasStatus = false;
    },

    // Vehicles, stops and the card from the last reply at the current zoom.
    draw() {
        if (!lastReply) return;
        const range = RADAR_RANGES[this.zoom];
        const vehicles = lastReply.x || [];
        step = 'markers-compute';
        // the departures page's lines stand out; a phone app without the "mine" flag dims nothing
        const highlight = ownLines(state.lastDep).length > 0 && vehicles.length > 0 && vehicles[0].length > 7;
        const markers = radarMarkers(vehicles, SIZE, RADIUS_PX, range, { highlight: highlight });
        shown = markers.map(function (m) {
            return vehicles[m.index];
        });
        step = 'markers-set';
        this.markers = markers;
        step = 'arrows';
        this.arrows = markers.filter(function (m) {
            return !!m.arrow;
        });
        step = 'stops-compute';
        const stops = [];
        const points = [];
        const list = lastReply.s || [];
        const scale = RADIUS_PX / range;
        for (let i = 0; i < list.length; i++) {
            if (Math.sqrt(list[i][1] * list[i][1] + list[i][2] * list[i][2]) > range) continue;
            const x = Math.round(SIZE / 2 + list[i][1] * scale);
            const y = Math.round(SIZE / 2 - list[i][2] * scale);
            stops.push({ left: x - 7, top: y - 7 });
            points.push({ x: x, y: y, text: shortStopName(list[i][0]) });
        }
        step = 'stops-place';
        const taken = FIXED_BOXES.concat([
            this.labelBox(INNER_LABEL, this.innerLabel),
            this.labelBox(OUTER_LABEL, this.outerLabel),
        ]);
        step = 'stops-set';
        this.stops = stops;
        // names where they overlap nothing, or left out (their dots stay)
        this.stopLabels = placeStopLabels(points, SIZE, taken);
        step = 'card';
        if (cardVehicle) {
            const i = findVehicle(vehicles, cardVehicle);
            if (i < 0) this.closeCard();
            else this.openCard(vehicles[i]);
        }
    },

    pickVehicle(index) {
        const v = shown[index];
        if (v) this.openCard(v);
    },

    openCard(v) {
        cardVehicle = v;
        this.card = vehicleCard(v, t(state.lang, 'onTime'));
        this.hasCard = true;
    },

    closeCard() {
        cardVehicle = null;
        this.hasCard = false;
    },

    // The crown turns the zoom slider (0-2).
    onZoom(e) {
        const value = Math.round(e.value);
        if (value === this.zoom || value < 0 || value >= RADAR_RANGES.length) return;
        this.setZoom(value);
    },

    nextZoom() {
        this.setZoom((this.zoom + 1) % RADAR_RANGES.length);
    },

    setZoom(value) {
        this.zoom = value;
        state.radarZoom = value;
        this.showRange();
        // what is known redrawn at once; vehicles further out come with the answer
        this.draw();
        this.load();
    },

    showRange() {
        const range = RADAR_RANGES[this.zoom];
        this.innerLabel = ringLabel(range / 2);
        this.outerLabel = ringLabel(range);
    },

    labelBox(at, text) {
        return { left: at.left, top: at.top, width: textWidth(text, 30) + 4, height: 36 };
    },

    showStatus(text) {
        this.status = text;
        this.hasStatus = true;
    },

    onSwipe(e) {
        if (e.direction === 'right') go('pages/index/index');
    },
};
