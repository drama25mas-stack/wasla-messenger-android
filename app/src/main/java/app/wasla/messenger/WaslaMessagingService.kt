package app.wasla.messenger

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

fun registerWaslaPushToken(uid: String) {
    FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
        FirebaseFirestore.getInstance().collection("profiles").document(uid)
            .collection("devices").document(token)
            .set(mapOf("token" to token, "updatedAt" to FieldValue.serverTimestamp()))
    }
}

class WaslaMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("profiles").document(uid)
            .collection("devices").document(token)
            .set(mapOf("token" to token, "updatedAt" to FieldValue.serverTimestamp()))
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "WASLA"
        val body = message.notification?.body ?: message.data["body"] ?: "وصلك إشعار جديد"
        val channelId = "wasla_messages"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "رسائل WASLA", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "تنبيهات الرسائل والمجموعات"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(this).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
        } catch (_: SecurityException) {
            // Android 13+ requires the user to grant POST_NOTIFICATIONS.
        }
    }
}
