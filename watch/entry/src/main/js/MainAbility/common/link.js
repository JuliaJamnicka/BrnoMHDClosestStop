// Request/reply over Wear Engine P2P with the phone app (protocol: android/.../wear/WatchProtocol.kt).
import { P2pClient, Message, Builder } from './wearengine.js';
import { PHONE_PACKAGE, PHONE_FINGERPRINT, REQUEST_TIMEOUT_MS } from './config.js';
import { parseReply, pingOutcome } from './format.js';
import state from './state.js';

let client = null;
let registered = false;
let pending = null; // { onReply, timer, sent }
let installed = false; // set once ping found the phone app; checked again after a failure

function finish(reply) {
  if (!pending) return;
  const p = pending;
  pending = null;
  clearTimeout(p.timer);
  if (reply && reply.lg) state.lang = reply.lg;
  if (reply && reply.t) state.clockOffset = reply.t - Math.floor(Date.now() / 1000);
  p.onReply(reply);
}

function ensureClient() {
  if (client) return;
  client = new P2pClient();
  client.setPeerPkgName(PHONE_PACKAGE);
  client.setPeerFingerPrint(PHONE_FINGERPRINT);
}

function ensureReceiver() {
  if (registered) return;
  registered = true;
  client.registerReceiver({
    onSuccess: function () {},
    onFailure: function () {
      registered = false;
    },
    onReceiveMessage: function (data) {
      if (data && data.isFileType) return;
      const reply = parseReply(data);
      if (reply) finish(reply);
    },
  });
}

/**
 * Sends a request; onReply gets the phone's reply or an error: {c:"err",e:"noapp"} when the phone app
 * is not installed, "closed" when it is installed but does not answer (not running), "phone" when
 * there is no link to the phone. A newer request replaces an older one.
 */
export function request(body, onReply) {
  if (pending) {
    clearTimeout(pending.timer);
    pending = null;
  }
  try {
    ensureClient();
    ensureReceiver();
  } catch (e) {
    onReply({ c: 'err', e: 'phone' });
    return;
  }
  pending = {
    onReply: onReply,
    sent: false,
    timer: setTimeout(function () {
      failed();
    }, REQUEST_TIMEOUT_MS),
  };
  if (installed) {
    send(body);
    return;
  }
  // Ask Wear Engine first whether the phone app is there at all, so the watch can say what to do.
  const mine = pending;
  try {
    ping(mine, body);
  } catch (e) {
    send(body); // no ping on this watch: just try
  }
}

function ping(mine, body) {
  client.ping({
    onSuccess: function () {},
    onFailure: function () {},
    onPingResult: function (result) {
      if (pending !== mine) return; // replaced or timed out meanwhile
      const outcome = pingOutcome(result && result.code);
      if (outcome === 'send') {
        installed = true;
        send(body);
      } else {
        finish({ c: 'err', e: outcome });
      }
    },
  });
}

// The phone app is installed (ping) but did not take or answer the message: most likely it is not
// running. Without a sent message the link itself is the problem. Ping again on the next request.
function failed() {
  if (!pending) return;
  const wasSent = pending.sent;
  installed = false;
  finish({ c: 'err', e: wasSent ? 'closed' : 'phone' });
}

function send(body) {
  if (pending) pending.sent = true;
  const builder = new Builder();
  builder.setDescription(JSON.stringify(body));
  const message = new Message();
  message.builder = builder;
  client.send(message, {
    onSuccess: function () {},
    onFailure: function () {
      failed();
    },
    onSendResult: function () {},
    onSendProgress: function () {},
  });
}

/** Watch-side "now" in the phone's clock, epoch seconds. */
export function now() {
  return Math.floor(Date.now() / 1000) + state.clockOffset;
}

export function cancel() {
  if (pending) {
    clearTimeout(pending.timer);
    pending = null;
  }
}

export function shutdown() {
  cancel();
  if (client && registered) {
    try {
      client.unregisterReceiver({ onSuccess: function () {} });
    } catch (e) {}
  }
  registered = false;
}
