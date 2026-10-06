// Sends push notifications so phones react even when Focus Lamp is closed.
//  - new request          -> every parent in the family  (Approve / Deny buttons)
//  - request answered     -> the student who asked        (block lifts right away)
//  - protection turned off -> every parent

const { onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();
const db = getFirestore();

async function tokensFor(familyId, filter) {
  const members = await db.collection(`families/${familyId}/members`).get();
  const out = [];
  members.forEach((doc) => {
    if (filter(doc)) (doc.data().tokens || []).forEach((t) => out.push({ uid: doc.id, token: t }));
  });
  return out;
}

async function send(familyId, targets, data) {
  if (targets.length === 0) return;
  const res = await getMessaging().sendEachForMulticast({
    tokens: targets.map((t) => t.token),
    data: Object.fromEntries(Object.entries(data).map(([k, v]) => [k, String(v ?? "")])),
    android: { priority: "high" },
  });
  // Drop tokens from uninstalled apps.
  await Promise.all(res.responses.map((r, i) => {
    const code = r.error && r.error.code;
    if (code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token") {
      return db.doc(`families/${familyId}/members/${targets[i].uid}`)
        .update({ tokens: FieldValue.arrayRemove(targets[i].token) });
    }
    return null;
  }));
}

exports.onTimeRequest = onDocumentCreated("families/{familyId}/requests/{requestId}", async (event) => {
  const r = event.data && event.data.data();
  if (!r || r.status !== "pending") return;
  const parents = await tokensFor(event.params.familyId, (d) => d.data().role === "parent");
  await send(event.params.familyId, parents, {
    type: "time_request",
    requestId: event.params.requestId,
    childName: r.childName,
    appLabel: r.appLabel,
    pkg: r.pkg,
    minutes: r.minutes,
    reason: r.reason,
  });
});

exports.onRequestAnswered = onDocumentUpdated("families/{familyId}/requests/{requestId}", async (event) => {
  const before = event.data.before.data();
  const after = event.data.after.data();
  if (before.status !== "pending" || after.status === "pending") return;
  await notifyStudent(event.params.familyId, event.params.requestId, after);
});

// A parent opening an app directly creates an already-approved request.
exports.onParentUnblock = onDocumentCreated("families/{familyId}/requests/{requestId}", async (event) => {
  const r = event.data && event.data.data();
  if (!r || r.status !== "approved") return;
  await notifyStudent(event.params.familyId, event.params.requestId, r);
});

async function notifyStudent(familyId, requestId, r) {
  const child = await tokensFor(familyId, (d) => d.id === r.childUid);
  await send(familyId, child, {
    type: "request_answered",
    requestId,
    status: r.status,
    pkg: r.pkg,
    appLabel: r.appLabel,
    minutes: r.grantedMinutes || 0,
    byParent: r.byParent ? "true" : "false",
  });
}

exports.onProtectionChange = onDocumentUpdated("families/{familyId}/children/{childId}", async (event) => {
  const before = event.data.before.data();
  const after = event.data.after.data();
  if (before.protectionOk === false || after.protectionOk !== false) return;
  const parents = await tokensFor(event.params.familyId, (d) => d.data().role === "parent");
  await send(event.params.familyId, parents, {
    type: "protection",
    childUid: event.params.childId,
    childName: after.name,
    issues: (after.issues || []).join(". "),
  });
});
