package com.monstock.app

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * Service d'arrière-plan : garde l'écoute des commandes en ligne et affiche une notification
 * (avec son) à chaque nouvelle commande, même quand l'app est fermée.
 * Il n'importe pas la commande : c'est l'app, à son ouverture, qui la transforme en commande en attente.
 */
class OnlineOrderService : Service() {

    private var registration: ListenerRegistration? = null
    private val alerted = HashSet<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        if (intent?.action == ACTION_STOP) {
            prefs.edit().putBoolean(KEY_ENABLED, false).apply()
            registration?.remove()
            registration = null
            @Suppress("DEPRECATION")
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        createChannels()
        showForegroundNotification()

        val pid = intent?.getStringExtra(EXTRA_PID) ?: prefs.getString(KEY_PID, null)
        if (pid.isNullOrBlank() || (intent?.getStringExtra(EXTRA_PID) == null && !prefs.getBoolean(KEY_ENABLED, true))) {
            @Suppress("DEPRECATION")
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }
        prefs.edit().putString(KEY_PID, pid).putBoolean(KEY_ENABLED, true).apply()

        if (registration == null) {
            registration = FirebaseFirestore.getInstance()
                .collection("catalogs").document(pid).collection("orders")
                .addSnapshotListener { snap, error ->
                    if (error != null || snap == null) return@addSnapshotListener
                    val fresh = snap.documents.filter { alerted.add(it.id) }
                    if (fresh.isNotEmpty()) alert(fresh)
                }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        registration?.remove()
        registration = null
        super.onDestroy()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, "Surveillance des commandes", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ORDERS, "Nouvelles commandes en ligne", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun showForegroundNotification() {
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, OnlineOrderService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(this, CH_SERVICE)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("🔔 Monstock surveille les commandes en ligne")
            .setContentText("Tu seras prévenu à chaque nouvelle commande.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Arrêter", stop)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(FG_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(FG_ID, notif)
        }
    }

    @SuppressLint("MissingPermission")
    private fun alert(docs: List<DocumentSnapshot>) {
        // Si l'écran des ventes est ouvert, l'app s'en occupe déjà (bip + message).
        if (appVisible) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val first = docs.first()
        val name = (first.getString("name") ?: "").take(40)
        val qty = (first.get("items") as? List<*>)?.sumOf { item ->
            ((item as? Map<*, *>)?.get("quantity") as? Number)?.toInt() ?: 0
        } ?: 0
        val title = if (docs.size == 1) "🌐 Nouvelle commande en ligne" else "🌐 ${docs.size} nouvelles commandes en ligne"
        val text = if (docs.size == 1) {
            (if (name.isNotBlank()) "$name · " else "") + "$qty article(s) — touche pour ouvrir"
        } else {
            "Touche pour les voir dans ⏰ Commandes en attente"
        }

        val builder = NotificationCompat.Builder(this, CH_ORDERS)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            builder.setContentIntent(
                PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        }
        NotificationManagerCompat.from(this).notify((System.currentTimeMillis() % 1_000_000).toInt() + 100, builder.build())
    }

    companion object {
        private const val PREFS = "online_orders_service"
        private const val KEY_PID = "pid"
        private const val KEY_ENABLED = "enabled"
        private const val EXTRA_PID = "pid"
        private const val ACTION_STOP = "com.monstock.app.STOP_ONLINE_ORDERS"
        private const val CH_SERVICE = "online_orders_service"
        private const val CH_ORDERS = "online_orders_alert"
        private const val FG_ID = 4201

        /** Vrai quand l'écran des ventes est affiché : pas besoin de notification dans ce cas. */
        @Volatile
        var appVisible = false

        /** Démarre la surveillance. Sans « force », respecte un arrêt demandé par l'utilisateur. */
        fun start(context: Context, publicId: String, force: Boolean) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!force && !prefs.getBoolean(KEY_ENABLED, true)) return
            ContextCompat.startForegroundService(
                context,
                Intent(context, OnlineOrderService::class.java).putExtra(EXTRA_PID, publicId)
            )
        }
    }
}
