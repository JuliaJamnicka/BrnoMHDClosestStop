import router from '@system.router';
import { request, cancel } from '../../common/link.js';
import { t } from '../../common/strings.js';
import state from '../../common/state.js';

let ids = [];

export default {
    data: {
        title: '',
        message: '',
        platforms: [],
    },

    onInit() {
        const current = state.lastDep;
        this.title = current ? current.s : t(state.lang, 'platforms');
        this.message = t(state.lang, 'loading');
        if (!current) {
            router.replace({ uri: 'pages/home/home' });
            return;
        }
        const self = this;
        request({ c: 'pl', p: current.p }, function (reply) {
            if (reply.c !== 'pl') {
                self.message = t(state.lang, 'err_' + reply.e);
                return;
            }
            self.message = '';
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

    onDestroy() {
        cancel();
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
