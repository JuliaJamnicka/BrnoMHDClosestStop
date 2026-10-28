// Request/reply over Wear Engine P2P with the phone app (protocol: android/.../wear/WatchProtocol.kt).
import { P2pClient, Message, Builder } from './wearengine.js';
import { PHONE_PACKAGE, PHONE_FINGERPRINT, REQUEST_TIMEOUT_MS } from './config.js';
import { parseReply, pingOutcome, replyMatches } from './format.js';
import state, { setLang } from './state.js';

/** How long the diagnosis after a failed request may take. */
const PING_TIMEOUT_MS = 3000;
/** Longest wait for the SDK to learn the Wear Engine version before the first send. */
const VERSION_WAIT_MS = 1000;

let client = null;
let registered = false;
let pending = null; // { kind, onReply, timer, sent }

function finish(reply) {
  if (!pending) return;
  const p = pending;
  pending = null;
  clearTimeout(p.timer);
  if (reply && reply.lg) setLang(reply.lg);
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
      // only the answer to the pending request (a late reply to another page's request is dropped)
      if (reply && pending && replyMatches(pending.kind, reply)) finish(reply);
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
    kind: body.c,
    onReply: onReply,
    sent: false,
    timer: setTimeout(function () {
      failed();
    }, REQUEST_TIMEOUT_MS),
  };
  // Straight to the phone: asking Wear Engine first whether the phone app is installed cost a
  // Bluetooth round trip on every page's first request. That question is now asked only when no
  // reply comes, to say what is wrong.
  const mine = pending;
  whenReady(function () {
    if (pending === mine) send(body);
  }, 0);
}

// The SDK picks its send method by the Wear Engine version, which it gets asynchronously when the
// client is created: wait for it (briefly; after that the SDK's fallback is used).
function whenReady(action, waited) {
  if (client.version !== undefined || waited >= VERSION_WAIT_MS) {
    action();
    return;
  }
  setTimeout(function () {
    whenReady(action, waited + 50);
  }, 50);
}

// No reply (or the message did not go out): ask Wear Engine why. Phone app installed: it is not
// running ("closed"); not installed: "noapp"; no answer either: no link to the phone ("phone").
function failed() {
  if (!pending || pending.diagnosing) return;
  const mine = pending;
  mine.diagnosing = true;
  clearTimeout(mine.timer);
  const fallback = mine.sent ? 'closed' : 'phone';
  mine.timer = setTimeout(function () {
    if (pending === mine) finish({ c: 'err', e: fallback });
  }, PING_TIMEOUT_MS);
  try {
    client.ping({
      onSuccess: function () {},
      onFailure: function () {},
      onPingResult: function (result) {
        if (pending !== mine) return; // replaced or answered meanwhile
        const outcome = pingOutcome(result && result.code);
        finish({ c: 'err', e: outcome === 'send' ? 'closed' : outcome });
      },
    });
  } catch (e) {
    finish({ c: 'err', e: fallback });
  }
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
