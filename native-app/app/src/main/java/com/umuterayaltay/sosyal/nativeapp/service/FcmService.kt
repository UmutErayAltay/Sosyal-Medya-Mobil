package com.umuterayaltay.sosyal.nativeapp.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.umuterayaltay.sosyal.nativeapp.MainActivity
import com.umuterayaltay.sosyal.nativeapp.R
import com.umuterayaltay.sosyal.nativeapp.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * FCM push bildirimi (Faz 5, Dalga 4A) — backend'in app/fcm.py'si
 * Notification(title=, body=) + data={"url": url} göndereceğini varsayar
 * (PARALEL bir ajan tarafından yazıldı, bkz. görev sözleşmesi). Firebase
 * servis hesabı anahtarı backend'de HENÜZ YOK — yani bu turda GERÇEK bir push
 * GÖNDERİMİ test EDİLEMEDİ, sadece token kaydı + derleme doğrulandı (uçtan
 * uca test SONRAYA kalıyor, bkz. görev notu).
 */
class FcmService : FirebaseMessagingService() {

    /** Uygulama önceden giriş yapmışsa (ör. cihaz yeniden başlatıldığında ya da
     * FCM token'ı sunucu tarafında geçersiz kılınıp yenilendiğinde) sistem bu
     * callback'i tetikler — Service içinde suspend fun DOĞRUDAN çağrılamaz,
     * bu yüzden kendi IO coroutine scope'unda çalıştırılıyor (bkz. görev notu). */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (!ServiceLocator.authRepository.isLoggedIn()) return
        CoroutineScope(Dispatchers.IO).launch {
            ServiceLocator.pushRepository.registerToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // notification bloğu doluysa (backend'in Notification(title=, body=)
        // alanı) önce o kullanılır, yoksa data payload'undaki title/body'ye
        // düşülür (bkz. app/fcm.py sözleşmesi).
        val title = message.notification?.title ?: message.data["title"]
        val body = message.notification?.body ?: message.data["body"]
        val url = message.data["url"]

        showNotification(title, body, url)
    }

    private fun showNotification(title: String?, body: String?, url: String?) {
        ensureChannel()

        // Bildirime tıklanınca uygulamayı AÇAN basit bir PendingIntent —
        // derin bağlantı yönlendirmesi (url'e göre doğru ekrana gitme) bu
        // turun kapsamı DIŞI, SONRAKİ bir cila turunda ele alınacak (bkz.
        // görev notu). url extra'sı yine de taşınıyor ki o tur bunu okuyabilsin.
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (url != null) putExtra(EXTRA_NOTIFICATION_URL, url)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            // Projede özel bir mipmap/drawable ikon YOK (AndroidManifest.xml
            // uygulama ikonu için de sistem ikonunu — sym_def_app_icon —
            // kullanıyor), bu yüzden bildirim küçük ikonu da AYNI gerekçeyle
            // sistem ikonu.
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title ?: getString(R.string.app_name))
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // Android 13+/API 33: POST_NOTIFICATIONS runtime izni verilmemişse
        // notify() SecurityException fırlatır — MainActivity izni ister ama
        // kullanıcı reddetmiş olabilir, burada sessizce vazgeçiliyor.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificationManager = ContextCompat.getSystemService(this, NotificationManager::class.java)
        notificationManager?.notify(System.currentTimeMillis().toInt(), notification)
    }

    /** Android 8+/API 26 zorunlu — projede bildirim gösteren başka bir yer
     * (dolayısıyla önceden oluşturulmuş bir kanal) YOK, bu yüzden burada tek
     * bir "push_messages" kanalı oluşturuluyor. createNotificationChannel()
     * zaten var olan bir kanalda no-op'tur, her mesajda çağırmak güvenli. */
    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Bildirimler",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        val notificationManager = ContextCompat.getSystemService(this, NotificationManager::class.java)
        notificationManager?.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "push_messages"
        const val EXTRA_NOTIFICATION_URL = "notification_url"
    }
}
