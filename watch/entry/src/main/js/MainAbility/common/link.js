// Request/reply over Wear Engine P2P with the phone app (protocol: android/.../wear/WatchProtocol.kt).
import { P2pClient, Message, Builder } from './wearengine.js';
import { PHONE_PACKAGE, PHONE_FINGERPRINT, REQUEST_TIMEOUT_MS } from './config.js';
import { parseReply } from './format.js';
import state from './state.js';

let client = null;
let registered = false;
let pending = null; // { onReply, timer }

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

/** Sends a request; onReply gets the phone's reply or {c:"err",e:"phone"}. A newer request replaces an older one. */
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
    timer: setTimeout(function () {
      finish({ c: 'err', e: 'phone' });
    }, REQUEST_TIMEOUT_MS),
  };
  const builder = new Builder();
  builder.setDescription(JSON.stringify(body));
  const message = new Message();
  message.builder = builder;
  client.send(message, {
    onSuccess: function () {},
    onFailure: function () {
      finish({ c: 'err', e: 'phone' });
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
