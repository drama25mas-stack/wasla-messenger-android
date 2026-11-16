const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { onDocumentCreated } = require("firebase-functions/v2/firestore");

initializeApp();
const db = getFirestore();
const messaging = getMessaging();

async function notifyMembers(memberIds, senderId, title, body, data) {
  const recipients = [...new Set(memberIds)].filter((uid) => uid && uid !== senderId);
  const snapshots = await Promise.all(
    recipients.map((uid) => db.collection("profiles").doc(uid).collection("devices").get())
  );
  const tokenDocs = snapshots.flatMap((snapshot) =>
    snapshot.docs.map((doc) => ({ ref: doc.ref, token: doc.get("token") || doc.id }))
  ).filter((item) => item.token);

  if (tokenDocs.length === 0) return;
  const response = await messaging.sendEachForMulticast({
    tokens: tokenDocs.map((item) => item.token),
    notification: { title, body: body.slice(0, 180) },
    data
  });

  const invalidCodes = new Set([
    "messaging/invalid-registration-token",
    "messaging/registration-token-not-registered"
  ]);
  const removals = [];
  response.responses.forEach((result, index) => {
    if (!result.success && invalidCodes.has(result.error?.code)) {
      removals.push(tokenDocs[index].ref.delete());
    }
  });
  if (removals.length) await Promise.all(removals);
}

exports.notifyDirectMessage = onDocumentCreated(
  "chats/{chatId}/messages/{messageId}",
  async (event) => {
    const message = event.data?.data();
    if (!message?.senderId || !message?.text) return;
    const chatSnap = await db.collection("chats").doc(event.params.chatId).get();
    if (!chatSnap.exists) return;
    const chat = chatSnap.data();
    const senderSnap = await db.collection("profiles").doc(message.senderId).get();
    const senderName = senderSnap.get("displayName") || "رسالة جديدة";
    await notifyMembers(
      chat.members || [],
      message.senderId,
      senderName,
      message.text,
      { type: "direct", chatId: event.params.chatId, messageId: event.params.messageId }
    );
  }
);

exports.notifyGroupMessage = onDocumentCreated(
  "groups/{groupId}/messages/{messageId}",
  async (event) => {
    const message = event.data?.data();
    if (!message?.senderId || !message?.text) return;
    const groupSnap = await db.collection("groups").doc(event.params.groupId).get();
    if (!groupSnap.exists) return;
    const group = groupSnap.data();
    const senderSnap = await db.collection("profiles").doc(message.senderId).get();
    const senderName = senderSnap.get("displayName") || "عضو المجموعة";
    await notifyMembers(
      group.members || [],
      message.senderId,
      group.name ? senderName + " • " + group.name : senderName,
      message.text,
      { type: "group", groupId: event.params.groupId, messageId: event.params.messageId }
    );
  }
);
