import Toybox.Application;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.WatchUi;

//! Fčil for Garmin: departures from the nearest Brno stop, asked from the Fčil phone app through
//! Garmin Connect (the phone does the location and the network; see garmin/README.md).
class FcilApp extends Application.AppBase {
    var link as Link;
    //! Platform chosen in the stop list, platform list or "Opposite direction"; null = nearest stop.
    var pinned as String or Null = null;
    //! Last "dep" reply, for the menu and a quick redraw.
    var lastDep as Dictionary or Null = null;

    function initialize() {
        AppBase.initialize();
        link = new Link();
        // the stop or direction chosen last run
        var saved = Application.Storage.getValue("pinned");
        if (saved instanceof String && saved.length() > 0) {
            pinned = saved;
        }
    }

    //! Chooses a platform (null: the nearest stop again) and keeps the choice for the next start.
    function setPinned(platform as String or Null) as Void {
        pinned = platform;
        Application.Storage.setValue("pinned", platform != null ? platform : "");
    }

    function onStart(state as Dictionary?) as Void {
        Communications.registerForPhoneAppMessages(method(:onPhone));
    }

    function onStop(state as Dictionary?) as Void {
        link.cancel();
    }

    function onPhone(msg as Communications.PhoneAppMessage) as Void {
        link.onPhone(msg.data);
    }

    function getInitialView() as [WatchUi.Views] or [WatchUi.Views, WatchUi.InputDelegates] {
        var view = new DeparturesView();
        return [view, new DeparturesDelegate(view)];
    }
}

function getFcil() as FcilApp {
    return Application.getApp() as FcilApp;
}
