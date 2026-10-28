// UI texts; the language comes from the phone app ("lg" in every reply), not from the watch locale.
const TEXTS = {
  cs: {
    loading: 'Hledám zastávku…',
    loadingSub: 'Poloha z telefonu',
    nearest: 'Nejbližší zastávky',
    nearestAuto: 'Nejbližší (automaticky)',
    platforms: 'Nástupiště',
    now: 'teď',
    stale: 'Data před {m} min',
    noDepartures: 'Žádné odjezdy',
    retry: 'Zkusit znovu',
    err_phone: 'Telefon není připojen',
    err_phone_hint: 'Zapněte Bluetooth a jednou otevřete aplikaci Fčil v telefonu.',
    err_noapp: 'Fčil není v telefonu',
    err_noapp_hint: 'Nainstalujte do telefonu aplikaci Fčil.',
    err_closed: 'Fčil v telefonu neběží',
    err_closed_hint: 'Otevřete v telefonu aplikaci Fčil a zapněte spojení s hodinkami.',
    err_far: 'Mimo oblast IDS JMK',
    err_far_hint: 'V okolí není žádná zastávka IDS JMK.',
    err_noloc: 'Poloha není k dispozici',
    err_noloc_hint: 'Povolte v telefonu polohu pro aplikaci Fčil.',
    err_net: 'Telefon je offline',
    err_net_hint: 'Telefon se nemůže spojit se serverem.',
    err_srv: 'Chyba serveru',
    err_srv_hint: 'Zkuste to za chvíli znovu.',
    err_auth: 'Chyba aplikace',
    err_auth_hint: 'Aktualizujte aplikaci Fčil v telefonu.',
    err_req: 'Chyba aplikace',
    err_req_hint: 'Aktualizujte aplikaci Fčil v telefonu.',
  },
  en: {
    loading: 'Finding stop…',
    loadingSub: 'Location from phone',
    nearest: 'Nearest stops',
    nearestAuto: 'Nearest (automatic)',
    platforms: 'Platforms',
    now: 'now',
    stale: 'Data {m} min old',
    noDepartures: 'No departures',
    retry: 'Try again',
    err_phone: 'Phone not connected',
    err_phone_hint: 'Turn on Bluetooth and open the Fčil app on your phone once.',
    err_noapp: 'Fčil not on phone',
    err_noapp_hint: 'Install the Fčil app on your phone.',
    err_closed: 'Fčil not running',
    err_closed_hint: 'Open the Fčil app on your phone and turn on the watch link.',
    err_far: 'Outside IDS JMK',
    err_far_hint: 'No IDS JMK stop nearby.',
    err_noloc: 'No location',
    err_noloc_hint: 'Allow location for the Fčil app on your phone.',
    err_net: 'Phone is offline',
    err_net_hint: 'The phone cannot reach the server.',
    err_srv: 'Server error',
    err_srv_hint: 'Try again in a moment.',
    err_auth: 'App error',
    err_auth_hint: 'Update the Fčil app on your phone.',
    err_req: 'App error',
    err_req_hint: 'Update the Fčil app on your phone.',
  },
};

export function t(lang, key, values) {
  const dict = TEXTS[lang] || TEXTS.cs;
  let text = dict[key] || TEXTS.cs[key] || key;
  if (values) {
    // split/join with a string, not replace() with a number: the watch's engine is strict about types
    for (const name in values) text = text.split('{' + name + '}').join(String(values[name]));
  }
  return text;
}
