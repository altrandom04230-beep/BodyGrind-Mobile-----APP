/**
 * BodyGrind Firebase Cloud Function (v2) — Session Cancellation Push Fan-Out & Admin Email Enrichment.
 *
 * Deploy to Firebase project `bodygrind-gym-app` when ready to enable background FCM push delivery
 * for trainers and admins whose app is closed, and to ensure admin emails are always copied on
 * `/mail` documents created by trainers:
 *
 *   firebase deploy --only functions
 *
 * Also install the official Firebase Extension "Trigger Email from Firestore" watching `/mail`.
 */

const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();
const messaging = admin.messaging();

exports.onSessionCancellationCreated = onDocumentCreated(
  "cancellationNotifications/{notifId}",
  async (event) => {
    const data = event.data?.data();
    if (!data) return;

    const trainerId = data.trainerId || "";
    const sessionTitle = data.sessionTitle || "Scheduled Session";
    const memberName = data.memberName || "Member";
    const cancelledByName = data.cancelledByName || "Staff";
    const cancelledByRole = data.cancelledByRole || "trainer";
    const reason = data.reason || "No reason provided";
    const scheduledDateKey = data.scheduledDateKey || "";

    // 1. Collect target UIDs: affected trainer + all admins
    const targetUids = new Set();
    if (trainerId) targetUids.add(trainerId);

    const [adminsLower, adminsUpper] = await Promise.all([
      db.collection("users").where("role", "==", "admin").get(),
      db.collection("users").where("accountType", "==", "ADMIN").get(),
    ]);
    const adminEmails = new Set();
    for (const snap of [adminsLower, adminsUpper]) {
      snap.forEach((doc) => {
        targetUids.add(doc.id);
        const email = (doc.get("email") || "").trim();
        if (email.includes("@")) adminEmails.add(email);
      });
    }

    // 2. Gather FCM device tokens from /users/{uid}/fcmTokens
    const tokens = [];
    for (const uid of targetUids) {
      const tokenSnaps = await db
        .collection("users")
        .document(uid)
        .collection("fcmTokens")
        .get();
      tokenSnaps.forEach((t) => {
        const tok = t.get("token") || t.id;
        if (tok) tokens.push(tok);
      });
    }

    // 3. Send FCM multicast push notification
    if (tokens.length > 0) {
      await messaging.sendEachForMulticast({
        tokens,
        notification: {
          title: `Session Cancelled: ${sessionTitle}`,
          body: `${memberName} (${scheduledDateKey}) — Cancelled by ${cancelledByName} (${cancelledByRole}). Reason: ${reason}`,
        },
        data: {
          type: "session_cancellation",
          sessionId: String(data.sessionId || data.assignedPlanId || ""),
          sessionTitle: String(sessionTitle),
          reason: String(reason),
          cancelledByName: String(cancelledByName),
          scheduledDateKey: String(scheduledDateKey),
        },
        android: {
          priority: "high",
          notification: {
            channelId: "session_cancellations",
          },
        },
      });
    }

    // 4. Ensure all admin emails are included in the corresponding /mail document
    const mailRef = db.collection("mail").doc(`mail_${event.params.notifId}`);
    const mailSnap = await mailRef.get();
    if (mailSnap.exists && adminEmails.size > 0) {
      const existingTo = Array.isArray(mailSnap.get("to")) ? mailSnap.get("to") : [];
      const merged = Array.from(new Set([...existingTo, ...adminEmails]));
      if (merged.length !== existingTo.length) {
        await mailRef.update({ to: merged });
      }
    }
  }
);
