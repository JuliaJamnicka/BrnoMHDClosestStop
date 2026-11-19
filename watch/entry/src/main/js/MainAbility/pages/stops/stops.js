import { request, cancel } from '../../common/link.js';
import { distanceLabel, modeChips } from '../../common/format.js';
import { t, errorKey } from '../../common/strings.js';
import state, { go, restore, setPinned } from '../../common/state.js';

/** A "near" row for the list: name, distance and up to three mode chips in fixed slots. */
function stopRow(name, metres, modes) {
    const chips = modeChips(modes, state.lang);
    const row = { name: name, distance: distanceLabel(metres), nameWidth: 236, distanceWidth: 104, colour: '#ffffff' };
    for (let i = 0; i < 3; i++) {
        const c = chips[i];
        row['has' + i] = !!c; // lite wearable `if` takes booleans
        row['c' + i] = c ? c.src : '';
        row['w' + i] = c ? c.w : 0;
    }
    return row;
}

/** A row of one text across the list: "Nearest (automatic)" in blue, the loading text or an error in grey. */
function textRow(text, colour) {
    return { name: text, distance: '', nameWidth: 340, distanceWidth: 0, colour: colour, has0: false, has1: false, has2: false, c0: '', c1: '', c2: '', w0: 0, w1: 0, w2: 0 };
}

const AUTO = '#6ea0ee';
const NOTE = '#8d99b3';

// the platform id of each row; '' for "Nearest (automatic)", null for the loading or error text
let ids = [];

export default {
    data: {
        nav: '', // shared state from the previous page (common/state.js)
        title: '',
        rows: [],
    },

    onInit() {
        restore(this);
        this.title = t(state.lang, 'nearest');
        ids = ['', null];
        this.rows = [textRow(t(state.lang, 'nearestAuto'), AUTO), textRow(t(state.lang, 'loading'), NOTE)];
        const self = this;
        request({ c: 'near' }, function (reply) {
            self.title = t(state.lang, 'nearest');
            const rows = [textRow(t(state.lang, 'nearestAuto'), AUTO)];
            ids = [''];
            if (reply.c !== 'near') {
                rows.push(textRow(t(state.lang, errorKey(reply.e)), NOTE));
                ids.push(null);
            } else {
                const list = reply.x || [];
                for (let i = 0; i < list.length; i++) {
                    ids.push(list[i][0]);
                    rows.push(stopRow(list[i][1], list[i][2], list[i][3]));
                }
            }
            self.rows = rows;
        });
    },

    // the crown scrolls the list
    onShow() {
        try {
            this.$refs.list.rotation({ focus: true });
        } catch (e) {
            // no crown support: swiping still scrolls
        }
    },

    onDestroy() {
        cancel();
    },

    pick(index) {
        if (index < 0 || index >= ids.length || ids[index] === null) return;
        setPinned(ids[index] || null); // '': "Nearest (automatic)"
        state.lastDep = null;
        go('pages/index/index');
    },

    onSwipe(e) {
        if (e.direction === 'right') go('pages/index/index');
    },
};
