import Toybox.Lang;

//! UI texts. The language comes from the phone app ("lg" in every reply), as on the Huawei watch;
//! the texts match watch/entry/src/main/js/MainAbility/common/strings.js.
module Texts {
    var _cs as Dictionary or Null = null;
    var _en as Dictionary or Null = null;

    function cs() as Dictionary {
        if (_cs == null) {
            _cs = {
                "loading" => "Hledám zastávku…",
                "loadingSub" => "Poloha z telefonu",
                "now" => "teď",
                "noDepartures" => "Žádné odjezdy",
                "stale" => "Data před $1$ min",
                "retry" => "START: zkusit znovu",
                "stops" => "Zastávky",
                "nearest" => "Nejbližší zastávky",
                "nearestAuto" => "Nejbližší (automaticky)",
                "reverse" => "Opačný směr",
                "platforms" => "Nástupiště",
                "radar" => "Radar",
                "T" => "Tram", "B" => "Bus", "V" => "Vlak", "L" => "Loď",
                "err_phone" => "Telefon není připojen",
                "err_phone_hint" => "Zapněte Bluetooth a otevřete aplikaci Fčil v telefonu.",
                "err_closed" => "Fčil v telefonu neběží",
                "err_closed_hint" => "Otevřete v telefonu aplikaci Fčil a připojte hodinky Garmin.",
                "err_far" => "Mimo oblast IDS JMK",
                "err_far_hint" => "V okolí není žádná zastávka IDS JMK.",
                "err_noloc" => "Poloha není k dispozici",
                "err_noloc_hint" => "Povolte v telefonu polohu pro aplikaci Fčil.",
                "err_net" => "Telefon je offline",
                "err_net_hint" => "Telefon se nemůže spojit se serverem.",
                "err_srv" => "Chyba serveru",
                "err_srv_hint" => "Zkuste to za chvíli znovu.",
                "err_auth" => "Chyba aplikace",
                "err_auth_hint" => "Aktualizujte aplikaci Fčil v telefonu.",
                "err_req" => "Chyba aplikace",
                "err_req_hint" => "Aktualizujte aplikaci Fčil v telefonu.",
                "err_other" => "Něco se nepovedlo",
                "err_other_hint" => "Zkuste to znovu."
            };
        }
        return _cs as Dictionary;
    }

    function en() as Dictionary {
        if (_en == null) {
            _en = {
                "loading" => "Finding stop…",
                "loadingSub" => "Location from phone",
                "now" => "now",
                "noDepartures" => "No departures",
                "stale" => "Data $1$ min old",
                "retry" => "START: try again",
                "stops" => "Stops",
                "nearest" => "Nearest stops",
                "nearestAuto" => "Nearest (automatic)",
                "reverse" => "Opposite direction",
                "platforms" => "Platforms",
                "radar" => "Radar",
                "T" => "Tram", "B" => "Bus", "V" => "Train", "L" => "Boat",
                "err_phone" => "Phone not connected",
                "err_phone_hint" => "Turn on Bluetooth and open the Fčil app on your phone.",
                "err_closed" => "Fčil not running",
                "err_closed_hint" => "Open the Fčil app on your phone and connect the Garmin watch.",
                "err_far" => "Outside IDS JMK",
                "err_far_hint" => "No IDS JMK stop nearby.",
                "err_noloc" => "No location",
                "err_noloc_hint" => "Allow location for the Fčil app on your phone.",
                "err_net" => "Phone is offline",
                "err_net_hint" => "The phone cannot reach the server.",
                "err_srv" => "Server error",
                "err_srv_hint" => "Try again in a moment.",
                "err_auth" => "App error",
                "err_auth_hint" => "Update the Fčil app on your phone.",
                "err_req" => "App error",
                "err_req_hint" => "Update the Fčil app on your phone.",
                "err_other" => "Something went wrong",
                "err_other_hint" => "Please try again."
            };
        }
        return _en as Dictionary;
    }

    function t(key as String) as String {
        var dict = "en".equals(getFcil().link.lang) ? en() : cs();
        var text = dict[key];
        return text != null ? text as String : key;
    }

    //! The text key of an error code; codes without a text read "something went wrong".
    function errorKey(code as Object or Null) as String {
        var key = "err_" + code;
        return cs().hasKey(key) ? key : "err_other";
    }
}
