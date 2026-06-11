package com.tonnomdeved.volt

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tonnomdeved.volt.data.VoltPreferences
import com.tonnomdeved.volt.data.VoltStateBus
import com.tonnomdeved.volt.data.hibernation.HibernationController
import com.tonnomdeved.volt.data.hibernation.HibernationWorker
import com.tonnomdeved.volt.network.PushConnectionManager
import com.tonnomdeved.volt.system.ScreenStateReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Foreground Service principal de Volt.
 *
 * **Évolutions P1 Hibernate** :
 *  - Toute la logique App Standby Bucket est désormais déléguée au
 *    [HibernationController] (façade unique).
 *  - L'Intent de livraison UnifiedPush porte [Intent.FLAG_INCLUDE_STOPPED_PACKAGES]
 *    — c'est la ligne qui rend possible le wake-on-push même pour les
 *    apps force-stoppées (niveaux MEDIUM/HARD).
 *  - [triggerDeepSleep] consomme désormais la liste des packages depuis
 *    Room (via le Controller) plutôt que depuis DataStore.
 *  - Le compteur exposé via [VoltStateBus.hibernatedAppsCount] est
 *    alimenté en continu par observation du Flow du Repository.
 */
class BatteryCommandService : Service() {

    companion object {
        private const val TAG = "VoltCore"
        private const val CHANNEL_ID = "VoltCoreServiceChannel"
        private const val NOTIFICATION_ID = 101

        const val ACTION_DELIVER_PUSH = "com.tonnomdeved.volt.ACTION_DELIVER_PUSH"
        const val EXTRA_TARGET_PACKAGE = "TARGET_PACKAGE"
        const val EXTRA_MESSAGE_DATA = "MESSAGE_DATA"

        private const val UNIFIEDPUSH_MESSAGE_ACTION = "org.unifiedpush.android.connector.MESSAGE"
        private const val DEEP_SLEEP_REAPPLY_DELAY_MS = 5_000L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pendingDeepSleepJobs = ConcurrentHashMap<String, Job>()

    private var pushManager: PushConnectionManager? = null
    private var screenReceiver: ScreenStateReceiver? = null
    private var pushDeliveryReceiver: BroadcastReceiver? = null

    // Façade unique d'hibernation — récupérée via le service locator de
    // VoltApplication. Aucun autre accès direct à UsageStatsManager.
    private val hibernation: HibernationController
        get() = (applicationContext as VoltApplication).container.hibernationController

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerScreenReceiver()
        registerPushDeliveryReceiver()
        observeHibernatedCount()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        VoltStateBus.updateServiceRunning(true)

        // P5 — programme le sweep périodique 6h. Idempotent (UPDATE policy).
        HibernationWorker.schedule(applicationContext)

        if (pushManager == null) {
            serviceScope.launch {
                val url = VoltPreferences(applicationContext)
                    .pushServerUrl.firstOrNull().orEmpty()
                if (url.isNotBlank() && isActive) {
                    runCatching {
                        PushConnectionManager(applicationContext, url).also {
                            it.startListening()
                        }
                    }.onSuccess { mgr -> pushManager = mgr }
                     .onFailure { e ->
                         if (BuildConfig.DEBUG) Log.w(TAG, "PushManager init failed", e)
                     }
                }
            }
        }
        return START_STICKY
    }

    // ============================================================== //
    // Receivers
    // ============================================================== //
    private fun registerScreenReceiver() {
        screenReceiver = ScreenStateReceiver { isScreenOn ->
            if (!isScreenOn) triggerDeepSleep() else exitDeepSleep()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenReceiver, filter)
        }
    }

    private fun registerPushDeliveryReceiver() {
        pushDeliveryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != ACTION_DELIVER_PUSH) return
                val targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE) ?: return
                val messageData = intent.getStringExtra(EXTRA_MESSAGE_DATA) ?: return
                handleDelivery(targetPackage, messageData)
            }
        }
        val filter = IntentFilter(ACTION_DELIVER_PUSH)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pushDeliveryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(pushDeliveryReceiver, filter)
        }
    }

    /**
     * Observe le compteur d'apps hibernées et le propage dans VoltStateBus.
     * Une seule coroutine, vivante toute la durée du Service.
     */
    private fun observeHibernatedCount() {
        serviceScope.launch {
            hibernation.activeCount.collect { count ->
                VoltStateBus.updateHibernatedAppsCount(count)
            }
        }
    }

    // ============================================================== //
    // Livraison push — phase WAKE → BROADCAST → REHIBERNATE
    //
    // Le FLAG_INCLUDE_STOPPED_PACKAGES sur l'Intent de livraison est la
    // pièce maîtresse : il permet de réveiller une app force-stoppée
    // (niveau MEDIUM/HARD) qui ne recevrait sinon aucun broadcast.
    // ============================================================== //
    private fun handleDelivery(targetKey: String, messageData: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return

        // Coalesce sur la clé reçue (token ou package).
        pendingDeepSleepJobs[targetKey]?.cancel()

        val job = serviceScope.launch {
            // 0) Résolution token → package (UnifiedPush). Repli : la clé EST
            //    déjà un package (rétro-compat avec l'ancien format wire).
            val token = targetKey
            val container = (applicationContext as VoltApplication).container
            val targetPackage = container.pushRegistrationRepository
                .packageForToken(token) ?: token

            // 1) WAKE — promotion bucket → ACTIVE (no-op si app non hibernée)
            val wakeState = hibernation.wakeForPush(targetPackage)

            // 2) BROADCAST UnifiedPush — conforme spec + FLAG_INCLUDE_STOPPED_PACKAGES (CRITIQUE)
            val pushIntent = Intent(UNIFIEDPUSH_MESSAGE_ACTION).apply {
                putExtra("message", messageData)
                putExtra("token", token)
                setPackage(targetPackage)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            sendBroadcast(pushIntent)

            // 3) REHIBERNATE — uniquement si l'app était hibernée et écran toujours éteint
            delay(DEEP_SLEEP_REAPPLY_DELAY_MS)
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isInteractive && wakeState.wasHibernated()) {
                hibernation.rehibernate(wakeState)
            }
            pendingDeepSleepJobs.remove(targetKey)
        }
        pendingDeepSleepJobs[targetKey] = job
    }

    // ============================================================== //
    // Sweep périodique (écran éteint)
    // ============================================================== //
    private fun triggerDeepSleep() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        serviceScope.launch {
            val applied = hibernation.applyAllPolicies()
            VoltStateBus.updateRestrictedAppsCount(applied)
        }
    }

    private fun exitDeepSleep() {
        VoltStateBus.updateRestrictedAppsCount(0)
    }

    // ============================================================== //
    // Cycle de vie
    // ============================================================== //
    override fun onDestroy() {
        VoltStateBus.updateServiceRunning(false)

        pushManager?.stopListening()
        pushManager = null

        listOf(pushDeliveryReceiver, screenReceiver).forEach { r ->
            r?.let {
                runCatching { unregisterReceiver(it) }
                    .onFailure { e ->
                        if (BuildConfig.DEBUG) Log.w(TAG, "Receiver déjà désenregistré", e)
                    }
            }
        }
        pushDeliveryReceiver = null
        screenReceiver = null

        pendingDeepSleepJobs.values.forEach(Job::cancel)
        pendingDeepSleepJobs.clear()
        serviceScope.cancel()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ============================================================== //
    // Notification FGS
    // ============================================================== //
    private fun createNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Volt")
            .setContentText("Optimisation active")
            .setSmallIcon(R.drawable.ic_volt_notification)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Volt — Service",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                lockscreenVisibility = Notification.VISIBILITY_SECRET
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
    }
}
