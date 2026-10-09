import router from '@system.router';
import { request, cancel } from '../../common/link.js';
import { distanceLabel } from '../../common/format.js';
import { t } from '../../common/strings.js';
import state from '../../common/state.js';

const MODE_NAMES = {
    cs: { T: 'Tram', B: 'Bus', V: 'Vlak', L: 'Loď' },
    en: { T: 'Tram', B: 'Bus', V: 'Train', L: 'Boat' },
};

function modesText(modes) {
    const dict = MODE_NAMES[state.lang] || MODE_NAMES.cs;
    const names = [];
    for (let i = 0; i < modes.length; i++) names.push(dict[modes.charAt(i)] || modes.charAt(i));
    return names.join(' · ');
}

let ids = [];

export default {
    data: {
        title: '',
        autoText: '',
        message: '',
        stops: [],
    },

    onInit() {
        this.title = t(state.lang, 'nearest');
        this.autoText = t(state.lang, 'nearestAuto');
        this.message = t(state.lang, 'loading');
        const self = this;
        request({ c: 'near' }, function (reply) {
            self.title = t(state.lang, 'nearest');
            self.autoText = t(state.lang, 'nearestAuto');
            if (reply.c !== 'near') {
                self.message = t(state.lang, 'err_' + reply.e);
                return;
            }
            self.message = '';
            const list = reply.x || [];
            ids = [];
            const stops = [];
            for (let i = 0; i < list.length; i++) {
                ids.push(list[i][0]);
                stops.push({ name: list[i][1], detail: distanceLabel(list[i][2]) + '  ' + modesText(list[i][3]) });
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
