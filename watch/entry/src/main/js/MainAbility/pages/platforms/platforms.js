import { request, cancel } from '../../common/link.js';
import { t, errorKey } from '../../common/strings.js';
import state, { go, restore } from '../../common/state.js';

let ids = [];

export default {
    data: {
        nav: '', // shared state from the previous page (common/state.js)
        title: '',
        message: '',
        hasMessage: false,
        platforms: [],
    },

    onInit() {
        restore(this);
        const current = state.lastDep;
        this.title = current ? current.s : t(state.lang, 'platforms');
        this.message = t(state.lang, 'loading');
        this.hasMessage = true;
        if (!current) {
            go('pages/index/index');
            return;
        }
        const self = this;
        request({ c: 'pl', p: current.p }, function (reply) {
            if (reply.c !== 'pl') {
                self.message = t(state.lang, errorKey(reply.e));
                return;
            }
            self.message = '';
            self.hasMessage = false;
            const list = reply.x || [];
            ids = [];
            const platforms = [];
            for (let i = 0; i < list.length; i++) {
                ids.push(list[i][0]);
                platforms.push({ lines: list[i][1], direction: '→ ' + list[i][2] });
            }
            self.platforms = platforms;
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
        if (index < 0 || index >= ids.length) return;
        state.pinned = ids[index];
        state.lastDep = null;
        go('pages/index/index');
    },

    onSwipe(e) {
        if (e.direction === 'right') go('pages/index/index');
    },
};
