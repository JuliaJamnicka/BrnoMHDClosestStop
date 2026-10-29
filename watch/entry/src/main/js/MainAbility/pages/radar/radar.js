import sensor from '@system.sensor';
import { request, cancel } from '../../common/link.js';
import {
    radarMarkers,
    describeError,
    shortStopName,
    distanceLabel,
    turn,
    angleDiff,
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
const NORTH_RADIUS_PX = 214; // the "N" just outside the outer ring
/** The map turns with the watch only by this much at once, so it does not redraw for every tremor. */
const TURN_STEP = 8;

let timer = null;
let step = ''; // the statement running, for the error text (see pages/index)
let lastReply = null; // for redrawing on zoom or turn without asking the phone
let shown = []; // the vehicles behind the markers, by marker index
let cardVehicle = null; // the tapped vehicle, found again after each refresh
let rotation = 0; // the watch's heading in degrees; 0 (north up) without a compass

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
        northLeft: 213,
        northTop: 0,
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
        rotation = 0;
        this.zoom = state.radarZoom >= 0 && state.radarZoom < RADAR_RANGES.length ? state.radarZoom : 1;
        this.showRange();
        this.placeNorth();
        this.status = t(state.lang, 'loading');
        this.load();
        const self = this;
        timer = setInterval(function () {
            self.load();
        }, RADAR_REFRESH_MS);
        this.followCompass();
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
        try {
            sensor.unsubscribeCompass();
        } catch (e) {
            // never subscribed
        }
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

    // Vehicles, stops and the card from the last reply at the current zoom and heading.
    draw() {
        if (!lastReply) return;
        const range = RADAR_RANGES[this.zoom];
        const vehicles = lastReply.x || [];
        step = 'markers-compute';
        // the departures page's lines stand out; a phone app without the "mine" flag dims nothing
        const highlight = ownLines(state.lastDep).length > 0 && vehicles.length > 0 && vehicles[0].length > 7;
        const markers = radarMarkers(vehicles, SIZE, RADIUS_PX, range, { rotation: rotation, highlight: highlight });
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
        const labels = [];
        const list = lastReply.s || [];
        const scale = RADIUS_PX / range;
        for (let i = 0; i < list.length; i++) {
            const p = turn(list[i][1], list[i][2], rotation);
            if (Math.sqrt(p[0] * p[0] + p[1] * p[1]) > range) continue;
            const x = Math.round(SIZE / 2 + p[0] * scale);
            const y = Math.round(SIZE / 2 - p[1] * scale);
            stops.push({ left: x - 7, top: y - 7 });
            // right of the dot, kept inside the screen
            labels.push({
                text: shortStopName(list[i][0]),
                left: Math.max(20, Math.min(SIZE - 120, x + 10)),
                top: Math.max(20, Math.min(SIZE - 56, y - 18)),
            });
        }
        step = 'stops-set';
        this.stops = stops;
        this.stopLabels = labels;
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
        this.innerLabel = distanceLabel(range / 2);
        this.outerLabel = distanceLabel(range);
    },

    // The map turns with the watch: what is ahead is at the top. Without a compass it stays north up.
    followCompass() {
        const self = this;
        try {
            sensor.subscribeCompass({
                success: function (data) {
                    if (typeof data.direction !== 'number') return;
                    if (angleDiff(data.direction, rotation) < TURN_STEP) return;
                    rotation = data.direction;
                    self.placeNorth();
                    self.draw();
                },
                fail: function () {
                    rotation = 0;
                    self.placeNorth();
                },
            });
        } catch (e) {
            // no compass: north up
        }
    },

    placeNorth() {
        const rad = (-rotation * Math.PI) / 180;
        this.northLeft = Math.round(SIZE / 2 + Math.sin(rad) * NORTH_RADIUS_PX - 20);
        this.northTop = Math.round(SIZE / 2 - Math.cos(rad) * NORTH_RADIUS_PX - 18);
    },

    showStatus(text) {
        this.status = text;
        this.hasStatus = true;
    },

    onSwipe(e) {
        if (e.direction === 'right') go('pages/index/index');
    },
};
