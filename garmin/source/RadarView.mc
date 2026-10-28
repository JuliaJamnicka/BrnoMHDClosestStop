import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Math;
import Toybox.Timer;
import Toybox.WatchUi;

//! Vehicle radar (Garmin: Radar board): north up, the user in the middle, the outer ring 800 m,
//! stops with up to five letters of their name, vehicles as line badges with a heading arrow.
//! BACK returns to the departures.
class RadarView extends WatchUi.View {
    private const RANGE_M = 800;
    private const REFRESH_MS = 15000;

    //! [line, mode, dxMetres, dyMetres, headingDegreesOrMinus1, delayMinutes]
    private var _vehicles as Array = [];
    //! [stopName, dx, dy]
    private var _stops as Array = [];
    //! Shown only while loading or after an error (no "updated" line, it took the space).
    private var _status as String or Null = null;
    private var _timer as Timer.Timer;

    function initialize() {
        View.initialize();
        _timer = new Timer.Timer();
        _status = Texts.t("loading");
    }

    function onShow() as Void {
        load();
        _timer.start(method(:load), REFRESH_MS, true);
    }

    function onHide() as Void {
        _timer.stop();
    }

    function load() as Void {
        getFcil().link.request({"c" => "veh"}, method(:onReply));
    }

    function onReply(answer as Dictionary) as Void {
        if ("veh".equals(answer["c"])) {
            _vehicles = answer["x"] instanceof Array ? answer["x"] as Array : [];
            _stops = answer["s"] instanceof Array ? answer["s"] as Array : [];
            _status = null;
        } else {
            _status = Texts.t(Texts.errorKey(answer["e"]));
        }
        WatchUi.requestUpdate();
    }

    function onUpdate(dc as Dc) as Void {
        var w = dc.getWidth();
        var h = dc.getHeight();
        var cx = w / 2;
        var cy = h / 2;
        var outer = ((w < h ? w : h) * 0.43).toNumber();
        var scale = outer.toFloat() / RANGE_M;
        var small = Graphics.FONT_XTINY;

        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        dc.clear();

        // rings (800 m, 400 m), cross, north
        dc.setColor(0x22345A, Graphics.COLOR_TRANSPARENT);
        dc.setPenWidth(2);
        dc.drawCircle(cx, cy, outer);
        dc.drawCircle(cx, cy, outer / 2);
        dc.setPenWidth(1);
        dc.setColor(Format.RULE, Graphics.COLOR_TRANSPARENT);
        dc.drawLine(cx, cy - outer, cx, cy + outer);
        dc.drawLine(cx - outer, cy, cx + outer, cy);
        dc.setColor(Format.DIM, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx + outer * 0.36, cy - outer * 0.36 - dc.getFontHeight(small), small, "400 m", Graphics.TEXT_JUSTIFY_LEFT);
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, cy - outer - dc.getFontHeight(small), small, "N", Graphics.TEXT_JUSTIFY_CENTER);

        // stops: a ring and a short name
        for (var i = 0; i < _stops.size(); i++) {
            var stop = _stops[i] as Array;
            var x = cx + (stop[1] as Number) * scale;
            var y = cy - (stop[2] as Number) * scale;
            dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_TRANSPARENT);
            dc.fillCircle(x, y, 6);
            dc.setColor(Format.DIM, Graphics.COLOR_TRANSPARENT);
            dc.setPenWidth(2);
            dc.drawCircle(x, y, 6);
            dc.setPenWidth(1);
            dc.setColor(0x6F7890, Graphics.COLOR_TRANSPARENT);
            dc.drawText(x + 10, y, small, Format.shortStopName(stop[0].toString()), Graphics.TEXT_JUSTIFY_LEFT | Graphics.TEXT_JUSTIFY_VCENTER);
        }

        // the user: red dot with a halo
        dc.setColor(0x4A1416, Graphics.COLOR_TRANSPARENT);
        dc.fillCircle(cx, cy, 11);
        dc.setColor(Format.RED, Graphics.COLOR_TRANSPARENT);
        dc.fillCircle(cx, cy, 6);

        // vehicles within range
        for (var i = 0; i < _vehicles.size(); i++) {
            var v = _vehicles[i] as Array;
            var dx = v[2] as Number;
            var dy = v[3] as Number;
            if (dx * dx + dy * dy > RANGE_M * RANGE_M) {
                continue;
            }
            drawVehicle(dc, v, cx + dx * scale, cy - dy * scale);
        }

        if (_status != null) {
            dc.setColor(Format.DIM, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, cy + outer - dc.getFontHeight(small), small, _status, Graphics.TEXT_JUSTIFY_CENTER);
        }
    }

    //! A line badge (as wide as its number) and, with a known heading, an arrow just ahead of it.
    private function drawVehicle(dc as Dc, v as Array, x as Float, y as Float) as Void {
        var font = Graphics.FONT_XTINY;
        var line = v[0].toString();
        var badgeH = dc.getFontHeight(font) + 2;
        var badgeW = dc.getTextWidthInPixels(line, font) + 10;
        if (badgeW < badgeH * 4 / 3) {
            badgeW = badgeH * 4 / 3;
        }
        var heading = v[4] instanceof Number ? v[4] as Number : -1;
        if (heading >= 0) {
            var rad = Math.toRadians(heading);
            var reach = badgeW / 2 + 8;
            var tipX = x + Math.sin(rad) * (reach + 7);
            var tipY = y - Math.cos(rad) * (reach + 7);
            var baseX = x + Math.sin(rad) * reach;
            var baseY = y - Math.cos(rad) * reach;
            var sideX = Math.cos(rad) * 6;
            var sideY = Math.sin(rad) * 6;
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            dc.fillPolygon([[tipX, tipY], [baseX + sideX, baseY + sideY], [baseX - sideX, baseY - sideY]]);
        }
        var late = v[5] instanceof Number && (v[5] as Number) >= 2;
        dc.setColor(Format.badgeColour(v[1]), Graphics.COLOR_TRANSPARENT);
        dc.fillRoundedRectangle(x - badgeW / 2, y - badgeH / 2, badgeW, badgeH, Format.badgeRadius(v[1], badgeH));
        dc.setColor(late ? Format.AMBER : Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(x, y, font, line, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    }
}

class RadarDelegate extends WatchUi.BehaviorDelegate {
    function initialize() {
        BehaviorDelegate.initialize();
    }

    function onBack() as Boolean {
        WatchUi.popView(WatchUi.SLIDE_RIGHT);
        return true;
    }
}
