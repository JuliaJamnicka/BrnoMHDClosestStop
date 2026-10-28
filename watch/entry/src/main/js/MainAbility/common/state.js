// State shared between pages (pages are replaced, not stacked, on lite wearables).
// Each page is bundled separately with its own copy of this module, so the state does not survive
// a page change by itself: go() carries it in the router params and restore() reads it back.
import router from '@system.router';
import storage from '@system.storage';

const state = {
  /** Platform chosen in the stop list / platform picker / reverse button; null = follow the nearest stop. */
  pinned: null,
  /** Last "dep" reply, for the platform picker and quick redraw when returning to home. */
  lastDep: null,
  /** Difference phone clock - watch clock in seconds, from the last reply. */
  clockOffset: 0,
  lang: 'cs',
};

export default state;

/** Opens the page [uri] with the shared state; the page declares `nav: ''` in its data. */
export function go(uri) {
  router.replace({ uri: uri, params: { nav: JSON.stringify(state) } });
}

/** Takes over the state [page] was opened with (its `nav` data); call first in onInit. */
export function restore(page) {
  if (!page.nav) return;
  try {
    const saved = JSON.parse(page.nav);
    for (const key in saved) state[key] = saved[key];
  } catch (e) {
    // keep the defaults
  }
}

const LANG_KEY = 'lang';

/**
 * Reads the phone app's language from the last run (replies carry it, but the first screen shows
 * before the first reply); calls [done] either way.
 */
export function loadLang(done) {
  try {
    storage.get({
      key: LANG_KEY,
      default: '',
      success: function (value) {
        if (value === 'cs' || value === 'en') state.lang = value;
      },
      complete: function () {
        done();
      },
    });
  } catch (e) {
    done();
  }
}

/** Takes the language of a reply and keeps it for the next start. */
export function setLang(lang) {
  if (!lang || lang === state.lang) return;
  state.lang = lang;
  try {
    storage.set({ key: LANG_KEY, value: lang });
  } catch (e) {
    // the next start shows Czech until the first reply
  }
}
