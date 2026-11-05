import Toybox.Lang;
import Toybox.WatchUi;

//! The START menu of the departures screen (Garmin: Departures board, view "menu") and the
//! stop and platform lists it opens, as native Menu2 lists.
module Menus {
    function openMain(view as DeparturesView) as Void {
        var app = getFcil();
        var r = view.reply;
        var title = r != null && r["s"] != null ? r["s"].toString() : "Fčil";
        var menu = new WatchUi.Menu2({:title => title});
        menu.addItem(new WatchUi.MenuItem(Texts.t("stops"), null, :stops, {}));
        if (r != null && r["o"] != null) {
            menu.addItem(new WatchUi.MenuItem(Texts.t("reverse"), null, :reverse, {}));
        }
        if (r != null && r["p"] != null && !"".equals(r["p"])) {
            menu.addItem(new WatchUi.MenuItem(Texts.t("platforms"), null, :platforms, {}));
        }
        if (app.pinned != null) {
            menu.addItem(new WatchUi.MenuItem(Texts.t("nearestAuto"), null, :unpin, {}));
        }
        menu.addItem(new WatchUi.MenuItem(Texts.t("radar"), null, :radar, {}));
        WatchUi.pushView(menu, new MainMenuDelegate(view), WatchUi.SLIDE_UP);
    }
}

class MainMenuDelegate extends WatchUi.Menu2InputDelegate {
    private var _view as DeparturesView;

    function initialize(view as DeparturesView) {
        Menu2InputDelegate.initialize();
        _view = view;
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        var id = item.getId();
        var app = getFcil();
        WatchUi.popView(WatchUi.SLIDE_DOWN);
        if (id == :stops) {
            new ListRequest(_view, :near).start();
        } else if (id == :platforms) {
            new ListRequest(_view, :platforms).start();
        } else if (id == :reverse) {
            var r = _view.reply;
            if (r != null && r["o"] != null) {
                app.setPinned(r["o"].toString());
                _view.reload();
            }
        } else if (id == :unpin) {
            app.setPinned(null);
            _view.reload();
        } else if (id == :radar) {
            var radar = new RadarView();
            WatchUi.pushView(radar, new RadarDelegate(radar), WatchUi.SLIDE_LEFT);
        }
    }
}

//! Asks the phone for the nearest stops ("near") or the platforms of the shown stop ("pl"), then
//! shows them as a list; picking one pins it on the departures screen.
class ListRequest {
    private var _view as DeparturesView;
    private var _kind as Symbol;

    function initialize(view as DeparturesView, kind as Symbol) {
        _view = view;
        _kind = kind;
    }

    function start() as Void {
        var body;
        if (_kind == :near) {
            body = {"c" => "near"};
        } else {
            var r = _view.reply;
            if (r == null || r["p"] == null) {
                return;
            }
            body = {"c" => "pl", "p" => r["p"]};
        }
        _view.busy = true;
        _view.note = Texts.t("loading");
        WatchUi.requestUpdate();
        getFcil().link.request(body, method(:onReply));
    }

    function onReply(answer as Dictionary) as Void {
        _view.busy = false;
        var c = answer["c"];
        if (!(answer["x"] instanceof Array) || "err".equals(c)) {
            _view.note = Texts.t(Texts.errorKey(answer["e"]));
            WatchUi.requestUpdate();
            return;
        }
        _view.note = null;
        var list = answer["x"] as Array;
        var menu;
        if (_kind == :near) {
            menu = new WatchUi.Menu2({:title => Texts.t("nearest")});
            menu.addItem(new WatchUi.MenuItem(Texts.t("nearestAuto"), null, :auto, {}));
            for (var i = 0; i < list.size(); i++) {
                // [closestPlatformId, name, distanceM, modes]
                var stop = list[i] as Array;
                menu.addItem(new WatchUi.MenuItem(stop[1].toString(), Format.distanceLabel(stop[2]) + "  " + modeNames(stop[3]), stop[0], {}));
            }
        } else {
            var r = _view.reply;
            menu = new WatchUi.Menu2({:title => r != null && r["s"] != null ? r["s"].toString() : Texts.t("platforms")});
            for (var i = 0; i < list.size(); i++) {
                // [platformId, lines, direction]
                var platform = list[i] as Array;
                menu.addItem(new WatchUi.MenuItem(platform[1].toString(), "→ " + platform[2], platform[0], {}));
            }
        }
        WatchUi.pushView(menu, new PickDelegate(_view), WatchUi.SLIDE_UP);
    }

    //! "TB" -> "Tram · Bus"
    private function modeNames(modes as Object or Null) as String {
        if (!(modes instanceof String)) {
            return "";
        }
        var names = "";
        for (var i = 0; i < modes.length(); i++) {
            var name = Texts.t(modes.substring(i, i + 1) as String);
            names = names.length() == 0 ? name : names + " · " + name;
        }
        return names;
    }
}

//! A stop or platform picked in a list: the departures screen shows it (or the nearest again).
class PickDelegate extends WatchUi.Menu2InputDelegate {
    private var _view as DeparturesView;

    function initialize(view as DeparturesView) {
        Menu2InputDelegate.initialize();
        _view = view;
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        var id = item.getId();
        var app = getFcil();
        app.setPinned(id == null || id == :auto ? null : id.toString());
        app.lastDep = null;
        WatchUi.popView(WatchUi.SLIDE_DOWN);
        _view.reload();
    }
}
