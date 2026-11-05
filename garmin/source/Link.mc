import Toybox.Application;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.System;
import Toybox.Time;
import Toybox.Timer;

//! Request and reply with the Fčil phone app (protocol: android/.../wear/WatchProtocol.kt, sent as
//! Monkey C dictionaries and arrays instead of JSON text).
class Link {
    //! No reply in this time: the phone app is not running or its watch link is off.
    private const TIMEOUT_MS = 10000;

    private var _kind as String or Null = null;
    private var _callback as Method or Null = null;
    private var _timer as Timer.Timer;

    //! The phone app's language ("cs" or "en") from its last reply, kept for the next start.
    var lang as String = "cs";
    //! Phone clock minus watch clock, in seconds.
    var clockOffset as Number = 0;

    function initialize() {
        _timer = new Timer.Timer();
        var stored = Application.Storage.getValue("lang");
        if (stored instanceof String) {
            lang = stored;
        } else if (System.getDeviceSettings().systemLanguage != System.LANGUAGE_CES) {
            lang = "en";
        }
    }

    //! Sends [body] ({"c" => kind, ...}); [callback] gets the reply, or {"c" => "err", "e" => code}:
    //! "phone" without a phone connection, "closed" when the phone app does not answer.
    //! A newer request replaces an older one.
    function request(body as Dictionary, callback as Method) as Void {
        cancel();
        _kind = body["c"] as String;
        _callback = callback;
        if (!System.getDeviceSettings().phoneConnected) {
            finish({"c" => "err", "e" => "phone"});
            return;
        }
        Communications.transmit(body, null, new LinkListener(self));
        _timer.start(method(:onTimeout), TIMEOUT_MS, false);
    }

    //! A message from the phone: passed on only when it answers the pending request (a late reply
    //! to an earlier screen's request is dropped) or is an error.
    function onPhone(data as Object or Null) as Void {
        if (_callback == null || !(data instanceof Dictionary)) {
            return;
        }
        var c = data["c"];
        if (c == null) {
            return;
        }
        var expected = "home".equals(_kind) ? "dep" : _kind;
        if ("err".equals(c) || c.equals(expected)) {
            finish(data);
        }
    }

    function cancel() as Void {
        _timer.stop();
        _callback = null;
        _kind = null;
    }

    function onTimeout() as Void {
        if (_callback != null) {
            finish({"c" => "err", "e" => "closed"});
        }
    }

    function onTransmitError() as Void {
        if (_callback != null) {
            finish({"c" => "err", "e" => "phone"});
        }
    }

    //! "Now" in the phone's clock, epoch seconds.
    function now() as Number {
        return Time.now().value() + clockOffset;
    }

    private function finish(reply as Dictionary) as Void {
        var callback = _callback;
        cancel();
        var lg = reply["lg"];
        if (lg instanceof String && !lg.equals(lang)) {
            lang = lg;
            Application.Storage.setValue("lang", lg);
        }
        var t = reply["t"];
        if (t instanceof Number) {
            clockOffset = t - Time.now().value();
        }
        if (callback != null) {
            callback.invoke(reply);
        }
    }
}

//! Reports a message that did not leave the watch (no Bluetooth link to the phone).
class LinkListener extends Communications.ConnectionListener {
    private var _link as Link;

    function initialize(link as Link) {
        Communications.ConnectionListener.initialize();
        _link = link;
    }

    function onComplete() as Void {
    }

    function onError() as Void {
        _link.onTransmitError();
    }
}
