import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Math;
import Toybox.Sensor;
import Toybox.Timer;
import Toybox.WatchUi;

//! Vehicle radar (Garmin: Radar board): the user in the middle, stops with up to five letters of
//! their name, vehicles as line badges with a heading arrow. The map turns with the watch's compass
//! (what is ahead at the top; north up without one). The departures screen's lines stand out, the
//! other vehicles are dimmed. UP/DOWN zoom (400 m, 800 m, 1,5 km); tapping a vehicle or picking it
//! in the START list shows its card; BACK closes the card, then returns to the departures.
class RadarView extends WatchUi.View {
    static const RANGES = [400, 800, 1500];
    private const REFRESH_MS = 15000;
    private const COMPASS_MS = 1000;
    //! The map turns only by this much at once, so it does not redraw for every tremor.
    private const TURN_STEP = 8;

    //! [line, mode, dxMetres, dyMetres, headingDegreesOrMinus1, delayMinutes, headsign, mine0or1]
    var vehicles as Array = [];
    //! [stopName, dx, dy]
    private var _stops as Array = [];
    //! Shown only while loading or after an error (no "updated" line, it took the space).
    private var _status as String or Null = null;
    private var _timer as Timer.Timer;
    private var _compass as Timer.Timer;
    var zoom as Number = 1;
    //! The watch's heading in degrees; 0 (north up) without a compass.
    private var _rotation as Number = 0;
    //! The vehicle whose card is open, found again after each refresh.
    private var _card as Array or Null = null;
    //! Screen positions of the vehicles drawn last, for taps: [x, y, vehicle index].
    private var _drawn as Array = [];

    function initialize() {
        View.initialize();
        _timer = new Timer.Timer();
        _compass = new Timer.Timer();
        _status = Texts.t("loading");
    }

    function onShow() as Void {
        load();
        _timer.start(method(:load), REFRESH_MS, true);
        _compass.start(method(:readCompass), COMPASS_MS, true);
    }

    function onHide() as Void {
        _timer.stop();
        _compass.stop();
    }

    function range() as Number {
        return RANGES[zoom] as Number;
    }

    function load() as Void {
        getFcil().link.request({"c" => "veh", "r" => range(), "m" => ownLines()}, method(:onReply));
    }

    //! The departures screen's lines as [line, headsign] ("" for a stop list's board, whose rows
    //! name stops): the phone marks their vehicles.
    function ownLines() as Array {
        var result = [];
        var dep = getFcil().lastDep;
        if (dep == null || !"dep".equals(dep["c"]) || !(dep["x"] instanceof Array)) {
            return result;
        }
        var anyDirection = dep["l"] != null;
        var rows = dep["x"] as Array;
        for (var i = 0; i < rows.size(); i++) {
            var row = rows[i] as Array;
            var pair = [row[0].toString(), anyDirection || row[2] == null ? "" : row[2].toString()];
            var known = false;
            for (var j = 0; j < result.size(); j++) {
                var other = result[j] as Array;
                if (other[0].equals(pair[0]) && other[1].equals(pair[1])) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                result.add(pair);
            }
        }
        return result;
    }

    function onReply(answer as Dictionary) as Void {
        if ("veh".equals(answer["c"])) {
            vehicles = answer["x"] instanceof Array ? answer["x"] as Array : [];
            _stops = answer["s"] instanceof Array ? answer["s"] as Array : [];
            _status = null;
            if (_card != null) {
                var i = findVehicle(_card as Array);
                _card = i < 0 ? null : vehicles[i] as Array;
            }
        } else {
            _status = Texts.t(Texts.errorKey(answer["e"]));
        }
        WatchUi.requestUpdate();
    }

    //! The same line and headsign nearest to where [previous] was (vehicles have no id); -1 if gone.
    private function findVehicle(previous as Array) as Number {
        var best = -1;
        var bestDist = 0;
        for (var i = 0; i < vehicles.size(); i++) {
            var v = vehicles[i] as Array;
            if (!v[0].equals(previous[0]) || !headsign(v).equals(headsign(previous))) {
                continue;
            }
            var dx = (v[2] as Number) - (previous[2] as Number);
            var dy = (v[3] as Number) - (previous[3] as Number);
            var d = dx * dx + dy * dy;
            if (best < 0 || d < bestDist) {
                best = i;
                bestDist = d;
            }
        }
        return best;
    }

    function setZoom(value as Number) as Void {
        if (value < 0 || value >= RANGES.size() || value == zoom) {
            return;
        }
        zoom = value;
        WatchUi.requestUpdate();
        load();
    }

    function openCard(index as Number) as Void {
        if (index >= 0 && index < vehicles.size()) {
            _card = vehicles[index] as Array;
            WatchUi.requestUpdate();
        }
    }

    //! true when a card was open
    function closeCard() as Boolean {
        if (_card == null) {
            return false;
        }
        _card = null;
        WatchUi.requestUpdate();
        return true;
    }

    //! The vehicle drawn nearest to a tap within reach of its badge; -1 for none.
    function vehicleAt(x as Number, y as Number) as Number {
        var best = -1;
        var bestDist = 40 * 40;
        for (var i = 0; i < _drawn.size(); i++) {
            var p = _drawn[i] as Array;
            var dx = (p[0] as Float) - x;
            var dy = (p[1] as Float) - y;
            var d = dx * dx + dy * dy;
            if (d < bestDist) {
                best = p[2] as Number;
                bestDist = d;
            }
        }
        return best;
    }

    function readCompass() as Void {
        var info = Sensor.getInfo();
        var heading = info has :heading ? info.heading : null;
        if (heading == null) {
            return;
        }
        var degrees = normalise(Math.toDegrees(heading as Float).toNumber());
        var diff = (degrees - _rotation).abs();
        if (diff > 180) {
            diff = 360 - diff;
        }
        if (diff >= TURN_STEP) {
            _rotation = degrees;
            WatchUi.requestUpdate();
        }
    }

    private function normalise(degrees as Number) as Number {
        return ((degrees % 360) + 360) % 360;
    }

    //! [dx, dy] metres east/north turned so that what is ahead of the watch is at the top.
    private function turn(dx as Number, dy as Number) as Array<Float> {
        var rad = Math.toRadians(_rotation);
        var c = Math.cos(rad);
        var s = Math.sin(rad);
        return [dx * c - dy * s, dy * c + dx * s];
    }

    function onUpdate(dc as Dc) as Void {
        var w = dc.getWidth();
        var h = dc.getHeight();
        var cx = w / 2;
        var cy = h / 2;
        var outer = ((w < h ? w : h) * 0.43).toNumber();
        var rangeM = range();
        var scale = outer.toFloat() / rangeM;
        var small = Graphics.FONT_XTINY;

        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        dc.clear();

        // rings (the zoom's range and half of it), cross, north
        dc.setColor(0x22345A, Graphics.COLOR_TRANSPARENT);
        dc.setPenWidth(2);
        dc.drawCircle(cx, cy, outer);
        dc.drawCircle(cx, cy, outer / 2);
        dc.setPenWidth(1);
        dc.setColor(Format.RULE, Graphics.COLOR_TRANSPARENT);
        dc.drawLine(cx, cy - outer, cx, cy + outer);
        dc.drawLine(cx - outer, cy, cx + outer, cy);
        dc.setColor(Format.DIM, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx + outer * 0.36, cy - outer * 0.36 - dc.getFontHeight(small), small, Format.distanceLabel(rangeM / 2), Graphics.TEXT_JUSTIFY_LEFT);
        dc.drawText(cx + outer * 0.71, cy - outer * 0.71 - dc.getFontHeight(small), small, Format.distanceLabel(rangeM), Graphics.TEXT_JUSTIFY_LEFT);
        var north = Math.toRadians(-_rotation);
        var northR = outer + dc.getFontHeight(small) / 2;
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx + Math.sin(north) * northR, cy - Math.cos(north) * northR, small, "N", Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);

        // stops: a ring and a short name
        for (var i = 0; i < _stops.size(); i++) {
            var stop = _stops[i] as Array;
            var dx = stop[1] as Number;
            var dy = stop[2] as Number;
            if (dx * dx + dy * dy > rangeM * rangeM) {
                continue;
            }
            var p = turn(dx, dy);
            var x = cx + p[0] * scale;
            var y = cy - p[1] * scale;
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

        // vehicles within range: the others first, dimmed when the departures' lines are marked,
        // then the departures' own lines over them
        // a phone app without the "mine" flag dims nothing
        var highlight = ownLines().size() > 0 && vehicles.size() > 0 && (vehicles[0] as Array).size() > 7;
        _drawn = [];
        for (var pass = 0; pass < 2; pass++) {
            for (var i = 0; i < vehicles.size(); i++) {
                var v = vehicles[i] as Array;
                var mine = !highlight || isMine(v);
                if ((pass == 0) == mine) {
                    continue;
                }
                var dx = v[2] as Number;
                var dy = v[3] as Number;
                if (dx * dx + dy * dy > rangeM * rangeM) {
                    continue;
                }
                var p = turn(dx, dy);
                var x = cx + p[0] * scale;
                var y = cy - p[1] * scale;
                drawVehicle(dc, v, x, y, !mine);
                _drawn.add([x, y, i]);
            }
        }

        if (_card != null) {
            drawCard(dc, _card as Array, cx, cy + outer * 0.55);
        } else if (_status != null) {
            dc.setColor(Format.DIM, Graphics.COLOR_TRANSPARENT);
            dc.drawText(cx, cy + outer - dc.getFontHeight(small), small, _status, Graphics.TEXT_JUSTIFY_CENTER);
        }
    }

    private function isMine(v as Array) as Boolean {
        return v.size() > 7 && v[7] instanceof Number && (v[7] as Number) == 1;
    }

    static function headsign(v as Array) as String {
        return v.size() > 6 && v[6] != null ? v[6].toString() : "";
    }

    //! "+3 min · 350 m": delay ("včas" on time) and distance from the user.
    static function details(v as Array) as String {
        var delay = v[5] instanceof Number ? v[5] as Number : 0;
        var when = Texts.t("onTime");
        if (delay >= 1) {
            when = "+" + delay.toString() + " min";
        } else if (delay <= -1) {
            when = "-" + (-delay).toString() + " min";
        }
        var dx = (v[2] as Number).toFloat();
        var dy = (v[3] as Number).toFloat();
        return when + " · " + Format.distanceLabel(Math.sqrt(dx * dx + dy * dy).toNumber());
    }

    //! A line badge (as wide as its number) and, with a known heading, an arrow just ahead of it.
    private function drawVehicle(dc as Dc, v as Array, x as Float, y as Float, dimmed as Boolean) as Void {
        var font = Graphics.FONT_XTINY;
        var line = v[0].toString();
        var badgeH = dc.getFontHeight(font) + 2;
        var badgeW = dc.getTextWidthInPixels(line, font) + 10;
        if (badgeW < badgeH * 4 / 3) {
            badgeW = badgeH * 4 / 3;
        }
        var heading = v[4] instanceof Number ? v[4] as Number : -1;
        if (heading >= 0) {
            var rad = Math.toRadians(heading - _rotation);
            var reach = badgeW / 2 + 8;
            var tipX = x + Math.sin(rad) * (reach + 7);
            var tipY = y - Math.cos(rad) * (reach + 7);
            var baseX = x + Math.sin(rad) * reach;
            var baseY = y - Math.cos(rad) * reach;
            var sideX = Math.cos(rad) * 6;
            var sideY = Math.sin(rad) * 6;
            dc.setColor(dimmed ? Format.dim(Graphics.COLOR_WHITE) : Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            dc.fillPolygon([[tipX, tipY], [baseX + sideX, baseY + sideY], [baseX - sideX, baseY - sideY]]);
        }
        var late = v[5] instanceof Number && (v[5] as Number) >= 2;
        var colour = Format.badgeColour(v[1]);
        dc.setColor(dimmed ? Format.dim(colour) : colour, Graphics.COLOR_TRANSPARENT);
        dc.fillRoundedRectangle(x - badgeW / 2, y - badgeH / 2, badgeW, badgeH, Format.badgeRadius(v[1], badgeH));
        var text = late ? Format.AMBER : Graphics.COLOR_WHITE;
        dc.setColor(dimmed ? Format.dim(text) : text, Graphics.COLOR_TRANSPARENT);
        dc.drawText(x, y, font, line, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    }

    //! The open vehicle's card: badge and headsign, then delay and distance.
    private function drawCard(dc as Dc, v as Array, cx as Number, top as Float) as Void {
        var font = Graphics.FONT_XTINY;
        var lineH = dc.getFontHeight(font);
        var cardW = (dc.getWidth() * 0.62).toNumber();
        var cardH = lineH * 2 + 12;
        var left = cx - cardW / 2;
        dc.setColor(0x1B2233, Graphics.COLOR_TRANSPARENT);
        dc.fillRoundedRectangle(left, top, cardW, cardH, 10);

        var line = v[0].toString();
        var badgeH = lineH + 2;
        var badgeW = dc.getTextWidthInPixels(line, font) + 10;
        if (badgeW < badgeH * 4 / 3) {
            badgeW = badgeH * 4 / 3;
        }
        var rowY = top + 5;
        dc.setColor(Format.badgeColour(v[1]), Graphics.COLOR_TRANSPARENT);
        dc.fillRoundedRectangle(left + 8, rowY, badgeW, badgeH, Format.badgeRadius(v[1], badgeH));
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(left + 8 + badgeW / 2, rowY + badgeH / 2, font, line, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        var textLeft = left + 8 + badgeW + 6;
        dc.drawText(textLeft, rowY + badgeH / 2, font, Format.fit(dc, headsign(v), font, left + cardW - 8 - textLeft), Graphics.TEXT_JUSTIFY_LEFT | Graphics.TEXT_JUSTIFY_VCENTER);
        var late = v[5] instanceof Number && (v[5] as Number) >= 2;
        dc.setColor(late ? Format.AMBER : Format.DIM, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, rowY + badgeH + 2, font, details(v), Graphics.TEXT_JUSTIFY_CENTER);
    }
}

class RadarDelegate extends WatchUi.BehaviorDelegate {
    private var _view as RadarView;

    function initialize(view as RadarView) {
        BehaviorDelegate.initialize();
        _view = view;
    }

    //! UP or swipe down: zoom in
    function onPreviousPage() as Boolean {
        _view.setZoom(_view.zoom - 1);
        return true;
    }

    //! DOWN or swipe up: zoom out
    function onNextPage() as Boolean {
        _view.setZoom(_view.zoom + 1);
        return true;
    }

    //! A tap on a vehicle opens its card; elsewhere it counts as START.
    function onTap(event as WatchUi.ClickEvent) as Boolean {
        var xy = event.getCoordinates();
        var index = _view.vehicleAt(xy[0], xy[1]);
        if (index < 0) {
            return false;
        }
        _view.openCard(index);
        return true;
    }

    //! START: the vehicles in range as a list, the departures' lines first
    function onSelect() as Boolean {
        var vehicles = _view.vehicles;
        if (vehicles.size() == 0) {
            return true;
        }
        var range = _view.range();
        var menu = new WatchUi.Menu2({:title => Texts.t("radar")});
        for (var i = 0; i < vehicles.size(); i++) {
            var v = vehicles[i] as Array;
            var dx = v[2] as Number;
            var dy = v[3] as Number;
            if (dx * dx + dy * dy > range * range) {
                continue;
            }
            var headsign = RadarView.headsign(v);
            var label = headsign.length() > 0 ? v[0].toString() + " → " + headsign : v[0].toString();
            menu.addItem(new WatchUi.MenuItem(label, RadarView.details(v), i, {}));
        }
        WatchUi.pushView(menu, new VehiclePickDelegate(_view), WatchUi.SLIDE_UP);
        return true;
    }

    function onBack() as Boolean {
        if (_view.closeCard()) {
            return true;
        }
        WatchUi.popView(WatchUi.SLIDE_RIGHT);
        return true;
    }
}

//! A vehicle picked in the START list: its card on the radar.
class VehiclePickDelegate extends WatchUi.Menu2InputDelegate {
    private var _view as RadarView;

    function initialize(view as RadarView) {
        Menu2InputDelegate.initialize();
        _view = view;
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        var id = item.getId();
        WatchUi.popView(WatchUi.SLIDE_DOWN);
        if (id instanceof Number) {
            _view.openCard(id as Number);
        }
    }
}
