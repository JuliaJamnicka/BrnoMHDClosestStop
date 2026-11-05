import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Math;
import Toybox.System;
import Toybox.Timer;
import Toybox.WatchUi;

//! The home screen (Garmin: Departures board): háček mark, stop, direction, the departures (UP/DOWN
//! or swipe scroll them when they do not all fit), distance. START opens the menu (Menus.mc).
class DeparturesView extends WatchUi.View {
    //! Departures are refreshed this often while shown (as on the Huawei watch).
    private const REFRESH_MS = 20000;
    //! Older data is drawn dimmed.
    private const STALE_AFTER_S = 60;

    var state as Symbol = :loading; // :loading, :error or :dep
    var reply as Dictionary or Null = null;
    var errorCode as Object or Null = null;
    //! A stop or platform list is being loaded (Menus.mc): no departures refresh meanwhile.
    var busy as Boolean = false;
    //! An error of the stop or platform list, shown in the footer until the next refresh.
    var note as String or Null = null;

    private var _receivedAt as Number = 0;
    private var _first as Number = 0;
    private var _visible as Number = 4;
    private var _refresh as Timer.Timer;
    private var _tick as Timer.Timer;

    function initialize() {
        View.initialize();
        _refresh = new Timer.Timer();
        _tick = new Timer.Timer();
    }

    function onShow() as Void {
        var app = getFcil();
        if (reply == null && app.lastDep != null) {
            reply = app.lastDep;
            state = :dep;
        }
        load();
        _refresh.start(method(:load), REFRESH_MS, true);
        // the minutes count down between refreshes
        _tick.start(method(:redraw), 10000, true);
    }

    function onHide() as Void {
        _refresh.stop();
        _tick.stop();
    }

    function redraw() as Void {
        WatchUi.requestUpdate();
    }

    function load() as Void {
        if (busy) {
            return;
        }
        var app = getFcil();
        var body = app.pinned != null ? {"c" => "dep", "p" => app.pinned} : {"c" => "home"};
        app.link.request(body, method(:onReply));
    }

    //! After the stop changed (menu): start over from the top.
    function reload() as Void {
        reply = null;
        state = :loading;
        _first = 0;
        note = null;
        WatchUi.requestUpdate();
        load();
    }

    function onReply(answer as Dictionary) as Void {
        if ("dep".equals(answer["c"])) {
            // another platform than shown: back to the first departure
            var previous = reply;
            var platform = answer["p"];
            if (previous == null || platform == null || !platform.equals(previous["p"])) {
                _first = 0;
            }
            reply = answer;
            _receivedAt = getFcil().link.now();
            getFcil().lastDep = answer;
            state = :dep;
            note = null;
        } else if (reply == null) {
            // with data shown, a failed refresh keeps it (it turns stale after a minute)
            state = :error;
            errorCode = answer["e"];
        }
        WatchUi.requestUpdate();
    }

    //! Scrolls the departures by [delta] rows; false when already at that end.
    function scroll(delta as Number) as Boolean {
        if (state != :dep) {
            return false;
        }
        var count = rows().size();
        var last = count - _visible;
        if (last < 0) {
            last = 0;
        }
        var next = _first + delta;
        if (next < 0) {
            next = 0;
        }
        if (next > last) {
            next = last;
        }
        if (next == _first) {
            return false;
        }
        _first = next;
        WatchUi.requestUpdate();
        return true;
    }

    function rows() as Array {
        var r = reply;
        if (r == null || !(r["x"] instanceof Array)) {
            return [];
        }
        return r["x"] as Array;
    }

    function onUpdate(dc as Dc) as Void {
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        dc.clear();
        if (state == :loading) {
            drawMessage(dc, Texts.t("loading"), Texts.t("loadingSub"), Graphics.COLOR_WHITE);
        } else if (state == :error) {
            var key = Texts.errorKey(errorCode);
            drawMessage(dc, Texts.t(key), Texts.t(key + "_hint") + "\n" + Texts.t("retry"), Graphics.COLOR_WHITE);
        } else {
            drawDepartures(dc);
        }
    }

    //! A title and a wrapped text in the middle of the screen (loading and error screens).
    private function drawMessage(dc as Dc, title as String, text as String, colour as Number) as Void {
        var w = dc.getWidth();
        var h = dc.getHeight();
        var titleFont = Graphics.FONT_SMALL;
        var textFont = Graphics.FONT_XTINY;
        var titleText = Graphics.fitTextToArea(title, titleFont, w * 0.8, dc.getFontHeight(titleFont) * 2, true);
        var body = Graphics.fitTextToArea(text, textFont, w * 0.72, h * 0.36, true);
        dc.setColor(colour, Graphics.COLOR_TRANSPARENT);
        dc.drawText(w / 2, h * 0.42, titleFont, titleText, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        dc.setColor(Format.GREY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(w / 2, h * 0.52, textFont, body, Graphics.TEXT_JUSTIFY_CENTER);
    }

    private function drawDepartures(dc as Dc) as Void {
        var r = reply as Dictionary;
        var app = getFcil();
        var w = dc.getWidth();
        var h = dc.getHeight();
        var cx = w / 2;
        var round = System.getDeviceSettings().screenShape == System.SCREEN_SHAPE_ROUND;
        var now = app.link.now();
        var age = _receivedAt > 0 ? now - _receivedAt : 0;
        var stale = age > STALE_AFTER_S;

        // háček mark
        var y = (round ? h * 0.06 : h * 0.03).toNumber();
        var markW = w / 18;
        dc.setColor(Format.RED, Graphics.COLOR_TRANSPARENT);
        dc.setPenWidth(w > 300 ? 4 : 3);
        dc.drawLine(cx - markW, y, cx, y + markW * 2 / 3);
        dc.drawLine(cx, y + markW * 2 / 3, cx + markW, y);
        dc.setPenWidth(1);
        y += markW * 2 / 3 + 2;

        // stop name, with the pin right before it when a platform is chosen
        var titleFont = Graphics.FONT_SMALL;
        var name = Format.fit(dc, r["s"] != null ? r["s"].toString() : "", titleFont, (w * 0.7).toNumber());
        var nameW = dc.getTextWidthInPixels(name, titleFont);
        var titleH = dc.getFontHeight(titleFont);
        if (app.pinned != null) {
            dc.setColor(Format.AMBER, Graphics.COLOR_TRANSPARENT);
            dc.fillCircle(cx - nameW / 2 - 9, y + titleH / 2, 4);
        }
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, y, titleFont, name, Graphics.TEXT_JUSTIFY_CENTER);
        y += titleH;

        // direction (a stop list has none)
        var smallFont = Graphics.FONT_XTINY;
        var direction = r["r"];
        if (direction != null && !"".equals(direction)) {
            dc.setColor(Format.GREY, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, y, smallFont, Format.fit(dc, "→ " + direction, smallFont, (w * 0.75).toNumber()), Graphics.TEXT_JUSTIFY_CENTER);
            y += dc.getFontHeight(smallFont);
        }
        y += 4;

        // departures, as many as fit above the footer
        // footer: distance (or a note) and under it the time of day
        var footerH = 2 * dc.getFontHeight(smallFont) + (round ? h * 0.05 : h * 0.02).toNumber();
        var rowFont = Graphics.FONT_TINY;
        var rowH = dc.getFontHeight(rowFont) + 8;
        _visible = (h - footerH - y) / rowH;
        if (_visible < 1) {
            _visible = 1;
        }
        var list = rows();
        if (list.size() == 0) {
            dc.setColor(Format.DIM, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, y + rowH, rowFont, Texts.t("noDepartures"), Graphics.TEXT_JUSTIFY_CENTER);
        }
        var clock = r["tf"] != null && r["tf"] == 1;
        for (var i = _first; i < list.size() && i < _first + _visible; i++) {
            var top = y + (i - _first) * rowH;
            var half = rowHalfWidth(w, h, round, top, rowH);
            drawRow(dc, list[i] as Array, cx - half, cx + half, top, rowH, now, clock, stale);
        }

        // footer: the stale note or a list error in place of the distance
        var footerY = h - footerH;
        var footer = note;
        var footerColour = Format.AMBER;
        if (footer == null && stale) {
            footer = Lang.format(Texts.t("stale"), [age / 60]);
        }
        if (footer == null) {
            footer = Format.distanceLabel(r["d"]);
            footerColour = Format.DIM;
        }
        dc.setColor(footerColour, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, footerY, smallFont, Format.fit(dc, footer, smallFont, (w * 0.6).toNumber()), Graphics.TEXT_JUSTIFY_CENTER);
        var time = System.getClockTime();
        dc.setColor(Format.GREY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, footerY + dc.getFontHeight(smallFont), smallFont,
            time.hour.format("%02d") + ":" + time.min.format("%02d"), Graphics.TEXT_JUSTIFY_CENTER);
    }

    //! Half the usable width of a row: on a round screen the chord at the row's edge farther from
    //! the centre, less a margin.
    private function rowHalfWidth(w as Number, h as Number, round as Boolean, top as Number, rowH as Number) as Number {
        var max = (w * (round ? 0.43 : 0.46)).toNumber();
        if (!round) {
            return max;
        }
        var radius = w / 2;
        var dy = (top - h / 2).abs();
        var dyBottom = (top + rowH - h / 2).abs();
        if (dyBottom > dy) {
            dy = dyBottom;
        }
        if (dy >= radius) {
            return 0;
        }
        var chord = Math.sqrt(radius * radius - dy * dy).toNumber() - 6;
        return chord < max ? chord : max;
    }

    //! One departure: badge, headsign, live dot, time (and "+N" delay beside a clock time).
    private function drawRow(dc as Dc, d as Array, x0 as Number, x1 as Number, top as Number, rowH as Number,
            now as Number, clock as Boolean, stale as Boolean) as Void {
        var mid = top + rowH / 2;
        var rowFont = Graphics.FONT_TINY;
        var smallFont = Graphics.FONT_XTINY;
        var expected = d[3] as Number;
        var delay = d[4] instanceof Number ? d[4] as Number : 0;
        var live = d[5] == 1 && !stale;

        dc.setColor(Format.RULE, Graphics.COLOR_TRANSPARENT);
        dc.drawLine(x0, top, x1, top);

        // time on the right, "min" smaller beside it
        var label = Format.timeLabel(expected, now, clock, Texts.t("now"));
        var colour = Format.timeColour(expected - now < 60, delay);
        if (stale) {
            colour = Format.dim(colour);
        }
        var x = x1;
        dc.setColor(colour, Graphics.COLOR_TRANSPARENT);
        if (!"".equals(label[1])) {
            dc.drawText(x, mid, smallFont, label[1], Graphics.TEXT_JUSTIFY_RIGHT | Graphics.TEXT_JUSTIFY_VCENTER);
            x -= dc.getTextWidthInPixels(label[1], smallFont) + 3;
        }
        dc.drawText(x, mid, rowFont, label[0], Graphics.TEXT_JUSTIFY_RIGHT | Graphics.TEXT_JUSTIFY_VCENTER);
        x -= dc.getTextWidthInPixels(label[0], rowFont) + 6;
        // minutes already include the delay (amber from 2 min); next to a clock time it is shown
        if (clock && delay >= 1) {
            var delayText = "+" + delay;
            dc.setColor(Format.AMBER, Graphics.COLOR_TRANSPARENT);
            dc.drawText(x, mid, smallFont, delayText, Graphics.TEXT_JUSTIFY_RIGHT | Graphics.TEXT_JUSTIFY_VCENTER);
            x -= dc.getTextWidthInPixels(delayText, smallFont) + 6;
        }
        if (live) {
            dc.setColor(Format.BLUE, Graphics.COLOR_TRANSPARENT);
            dc.fillCircle(x - 3, mid, 4);
            x -= 12;
        }

        // line badge on the left
        var line = d[0].toString();
        var badgeH = rowH - 10;
        var badgeW = dc.getTextWidthInPixels(line, smallFont) + 12;
        if (badgeW < badgeH * 3 / 2) {
            badgeW = badgeH * 3 / 2;
        }
        var badgeColour = Format.badgeColour(d[1]);
        dc.setColor(stale ? Format.dim(badgeColour) : badgeColour, Graphics.COLOR_TRANSPARENT);
        dc.fillRoundedRectangle(x0, mid - badgeH / 2, badgeW, badgeH, Format.badgeRadius(d[1], badgeH));
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(x0 + badgeW / 2, mid, smallFont, line, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);

        // headsign (a stop list's row: its stop) in what is left
        var textX = x0 + badgeW + 8;
        dc.setColor(stale ? 0x797979 : 0xF2F2F2, Graphics.COLOR_TRANSPARENT);
        dc.drawText(textX, mid, rowFont, Format.fit(dc, d[2].toString(), rowFont, x - textX - 4), Graphics.TEXT_JUSTIFY_LEFT | Graphics.TEXT_JUSTIFY_VCENTER);
    }
}

//! Buttons and gestures of the departures screen.
class DeparturesDelegate extends WatchUi.BehaviorDelegate {
    private var _view as DeparturesView;

    function initialize(view as DeparturesView) {
        BehaviorDelegate.initialize();
        _view = view;
    }

    //! DOWN or swipe up
    function onNextPage() as Boolean {
        _view.scroll(1);
        return true;
    }

    //! UP or swipe down
    function onPreviousPage() as Boolean {
        _view.scroll(-1);
        return true;
    }

    //! START or tap: the menu, or another try after an error
    function onSelect() as Boolean {
        if (_view.state == :error) {
            _view.reload();
        } else {
            Menus.openMain(_view);
        }
        return true;
    }

    function onMenu() as Boolean {
        Menus.openMain(_view);
        return true;
    }
}
