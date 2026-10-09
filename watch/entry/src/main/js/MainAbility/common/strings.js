// UI texts; the language comes from the phone app ("lg" in every reply), not from the watch locale.
const TEXTS = {
  cs: {
    loading: 'Hledám zastávku…',
    nearest: 'Nejbližší zastávky',
    nearestAuto: 'Nejbližší (automaticky)',
    platforms: 'Nástupiště',
    now: 'teď',
    stale: 'Data před {m} min',
    noDepartures: 'Žádné odjezdy',
    updated: 'Aktualizováno před {s} s',
    retry: 'Zkusit znovu',
    err_phone: 'Telefon není připojen',
    err_phone_hint: 'Zapněte Bluetooth a jednou otevřete aplikaci Brno MHD v telefonu.',
    err_noloc: 'Poloha není k dispozici',
    err_noloc_hint: 'Povolte v telefonu polohu pro aplikaci Brno MHD.',
    err_net: 'Telefon je offline',
    err_net_hint: 'Telefon se nemůže spojit se serverem.',
    err_srv: 'Chyba serveru',
    err_srv_hint: 'Zkuste to za chvíli znovu.',
    err_auth: 'Chyba aplikace',
    err_auth_hint: 'Aktualizujte aplikaci Brno MHD v telefonu.',
    err_req: 'Chyba aplikace',
    err_req_hint: 'Aktualizujte aplikaci Brno MHD v telefonu.',
  },
  en: {
    loading: 'Finding stop…',
    nearest: 'Nearest stops',
    nearestAuto: 'Nearest (automatic)',
    platforms: 'Platforms',
    now: 'now',
    stale: 'Data {m} min old',
    noDepartures: 'No departures',
    updated: 'Updated {s} s ago',
    retry: 'Try again',
    err_phone: 'Phone not connected',
    err_phone_hint: 'Turn on Bluetooth and open the Brno MHD app on your phone once.',
    err_noloc: 'No location',
    err_noloc_hint: 'Allow location for the Brno MHD app on your phone.',
    err_net: 'Phone is offline',
    err_net_hint: 'The phone cannot reach the server.',
    err_srv: 'Server error',
    err_srv_hint: 'Try again in a moment.',
    err_auth: 'App error',
    err_auth_hint: 'Update the Brno MHD app on your phone.',
    err_req: 'App error',
    err_req_hint: 'Update the Brno MHD app on your phone.',
  },
};

export function t(lang, key, values) {
  const dict = TEXTS[lang] || TEXTS.cs;
  let text = dict[key] || TEXTS.cs[key] || key;
  if (values) {
    for (const name in values) text = text.replace('{' + name + '}', values[name]);
  }
  return text;
}
