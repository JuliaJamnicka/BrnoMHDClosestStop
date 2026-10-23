import router from '@system.router';
import { request, cancel } from '../../common/link.js';
import { distanceLabel, modeChips } from '../../common/format.js';
import { t } from '../../common/strings.js';
import state from '../../common/state.js';

/** A "near" row for the list: name, distance and up to three mode chips in fixed slots. */
function stopRow(name, metres, modes) {
    const chips = modeChips(modes, state.lang);
    const row = { name: name, distance: distanceLabel(metres) };
    for (let i = 0; i < 3; i++) {
        const c = chips[i];
        row['has' + i] = !!c; // lite wearable `if` takes booleans
        row['c' + i] = c ? c.src : '';
        row['w' + i] = c ? c.w : 0;
    }
    return row;
}

let ids = [];

export default {
    data: {
        title: '',
        autoText: '',
        message: '',
        hasMessage: false,
        stops: [],
    },

    onInit() {
        this.title = t(state.lang, 'nearest');
        this.autoText = t(state.lang, 'nearestAuto');
        this.message = t(state.lang, 'loading');
        this.hasMessage = true;
        const self = this;
        request({ c: 'near' }, function (reply) {
            self.title = t(state.lang, 'nearest');
            self.autoText = t(state.lang, 'nearestAuto');
            if (reply.c !== 'near') {
                self.message = t(state.lang, 'err_' + reply.e);
                return;
            }
            self.message = '';
            self.hasMessage = false;
            const list = reply.x || [];
            ids = [];
            const stops = [];
            for (let i = 0; i < list.length; i++) {
                ids.push(list[i][0]);
                stops.push(stopRow(list[i][1], list[i][2], list[i][3]));
            }
            self.stops = stops;
        });
    },

    onDestroy() {
        cancel();
    },

    pickNearest() {
        state.pinned = null;
        state.lastDep = null;
        router.replace({ uri: 'pages/home/home' });
    },

    pick(index) {
        if (index < 0 || index >= ids.length) return;
        state.pinned = ids[index];
        state.lastDep = null;
        router.replace({ uri: 'pages/home/home' });
    },

    onSwipe(e) {
        if (e.direction === 'right') router.replace({ uri: 'pages/home/home' });
    },
};
