import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Time;
import Toybox.Time.Gregorian;

//! Formatting shared by the screens; the rules match the Huawei watch's common/format.js.
module Format {
    const RED = 0xF0373E;
    const AMBER = 0xFFB020;
    const BLUE = 0x6EA0EE;
    const GREY = 0xC3CCDD;
    const DIM = 0x8D99B3;
    const RULE = 0x16213A;

    //! Line badge colour: tram red, train blue, bus (incl. trolleybus) and boat green.
    function badgeColour(mode as Object or Null) as Number {
        if ("T".equals(mode)) {
            return 0xC8262C;
        }
        if ("V".equals(mode)) {
            return 0x1D5FD1;
        }
        return 0x1F7A4D;
    }

    //! Corner radius of a badge [height] px high: tram rounded, bus a pill, train square.
    function badgeRadius(mode as Object or Null, height as Number) as Number {
        if ("T".equals(mode)) {
            return height / 5;
        }
        if ("V".equals(mode)) {
            return 2;
        }
        return height / 2;
    }

    //! "teď"/"now" under a minute, "N" + "min" under an hour, else (or with [clock]) "HH:MM".
    function timeLabel(expected as Number, now as Number, clock as Boolean, nowWord as String) as Array<String> {
        var seconds = expected - now;
        if (!clock) {
            if (seconds < 60) {
                return [nowWord, ""];
            }
            var minutes = seconds / 60;
            if (minutes < 60) {
                return [minutes.toString(), "min"];
            }
        }
        var info = Gregorian.info(new Time.Moment(expected), Time.FORMAT_SHORT);
        return [info.hour.format("%02d") + ":" + info.min.format("%02d"), ""];
    }

    //! Time colour: "now" in the háček red, amber from 2 minutes late, else white.
    function timeColour(isNow as Boolean, delayMinutes as Number) as Number {
        if (isNow) {
            return RED;
        }
        return delayMinutes >= 2 ? AMBER : Graphics.COLOR_WHITE;
    }

    //! [colour] at half brightness, for data that is no longer current.
    function dim(colour as Number) as Number {
        return ((colour >> 1) & 0x7F7F7F);
    }

    //! "85 m" / "1,2 km"
    function distanceLabel(metres as Object or Null) as String {
        if (!(metres instanceof Number)) {
            return "";
        }
        if (metres < 1000) {
            return metres.toString() + " m";
        }
        var tenths = (metres + 50) / 100;
        return (tenths / 10).toString() + "," + (tenths % 10).toString() + " km";
    }

    //! A stop's name on the radar: up to five letters, with a dot when the name is longer.
    function shortStopName(name as String) as String {
        if (name.length() <= 5) {
            return name;
        }
        var cut = name.substring(0, 5) as String;
        while (cut.length() > 0) {
            var last = cut.substring(cut.length() - 1, cut.length()) as String;
            if (" -,.".find(last) != null) {
                cut = cut.substring(0, cut.length() - 1) as String;
            } else {
                break;
            }
        }
        return cut + ".";
    }

    //! [text] cut with "…" to fit [width] px in [font].
    function fit(dc as Dc, text as String, font as FontType, width as Number) as String {
        if (width <= 0) {
            return "";
        }
        if (dc.getTextWidthInPixels(text, font) <= width) {
            return text;
        }
        var cut = text;
        while (cut.length() > 1) {
            cut = cut.substring(0, cut.length() - 1) as String;
            if (dc.getTextWidthInPixels(cut + "…", font) <= width) {
                return cut + "…";
            }
        }
        return "…";
    }
}
