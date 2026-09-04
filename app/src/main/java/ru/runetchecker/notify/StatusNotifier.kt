package ru.runetchecker.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ru.runetchecker.MainActivity
import ru.runetchecker.R
import ru.runetchecker.domain.CheckResult
import ru.runetchecker.domain.NetworkState
import ru.runetchecker.domain.VpnStatus
import ru.runetchecker.monitor.MonitorService
import ru.runetchecker.settings.SettingsStore
import ru.runetchecker.settings.withAppLocale
import ru.runetchecker.ui.CheckUiState

class StatusNotifier(
    context: Context,
    private val settings: SettingsStore,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingAlert: Runnable? = null

    private fun localized(): Context = appContext.withAppLocale(settings.language())

    private fun str(id: Int, vararg args: Any): String {
        val context = localized()
        return if (args.isEmpty()) context.getString(id) else context.getString(id, *args)
    }

    fun ensureChannels() {
        settings.migrateAlertPipeline()
        val strings = localized()
        val manager = appContext.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(CHANNEL_ALERTS_LEGACY)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS,
                strings.getString(R.string.channel_status_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = strings.getString(R.string.channel_status_desc)
                setShowBadge(false)
            },
        )
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                strings.getString(R.string.channel_alerts_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = strings.getString(R.string.channel_alerts_desc)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 280, 160, 280)
                enableLights(true)
                setSound(sound, audio)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
            },
        )
    }

    fun ongoingNotification(state: CheckUiState): Notification {
        ensureChannels()
        val builder = NotificationCompat.Builder(appContext, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent(ONGOING_ID))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (state.isChecking) {
            val title = str(R.string.notification_checking)
            val detail = str(
                R.string.notification_checking_detail,
                state.checkAttempt,
                state.maxAttempts,
                state.countdownSeconds,
            )
            return pinOngoing(
                builder
                    .setContentTitle(title)
                    .setContentText(detail)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                    .setProgress(0, 0, true)
                    .build(),
            )
        }

        val result = state.result
        val vpn = state.vpn
        val title = statusLabel(result?.state)
        val text = buildOngoingText(result, vpn)
        return pinOngoing(
            builder
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setProgress(0, 0, false)
                .build(),
        )
    }

    fun publishOngoing(state: CheckUiState) {
        val notification = ongoingNotification(state)
        if (!MonitorService.republishForeground(notification)) {
            notify(ONGOING_ID, notification)
        }
    }

    fun onCheckFinished(result: CheckResult, vpn: VpnStatus, showOngoing: Boolean) {
        ensureChannels()
        if (showOngoing) {
            publishOngoing(
                CheckUiState(
                    isChecking = false,
                    result = result,
                    vpn = vpn,
                ),
            )
        }
        maybePopup(result, vpn)
    }

    fun cancelOngoing() {
        NotificationManagerCompat.from(appContext).cancel(ONGOING_ID)
    }

    private fun buildOngoingText(result: CheckResult?, vpn: VpnStatus?): String = buildString {
        append(vpnLine(vpn))
        if (result != null) {
            append('\n')
            append(
                str(
                    R.string.notification_counts,
                    result.globalReached,
                    result.globalTotal,
                    result.whitelistReached,
                    result.whitelistTotal,
                ),
            )
        }
        if (result?.state == NetworkState.WHITELIST && vpn?.active != true) {
            append('\n')
            append(str(R.string.whitelist_vpn_reminder))
        }
        if (result?.state == NetworkState.OFFLINE && vpn?.active == true) {
            append('\n')
            append(str(R.string.vpn_offline_warning))
        }
    }

    private fun maybePopup(result: CheckResult, vpn: VpnStatus) {
        val now = System.currentTimeMillis()
        val allowed = PopupLimiter.shouldPopup(
            newState = result.state,
            lastCheckState = settings.lastCheckState(),
            nowMs = now,
            lastPopupAtMs = settings.lastPopupAtMs(),
            popupTimesMs = settings.popupTimesMs(),
            unlimited = settings.popupUnlimited(),
            cooldownMs = settings.popupCooldownSeconds() * 1000L,
            maxPerHour = settings.popupMaxPerHour(),
        )
        if (!allowed) {
            val hanging = settings.lastPoppedState()
            if (hanging != null && hanging != result.state) {
                dismissPostedAlert()
            }
            return
        }
        settings.recordCheckState(result.state)

        val title = statusLabel(result.state)
        val text = buildString {
            append(statusDescription(result.state))
            if (result.state == NetworkState.OFFLINE && vpn.active) {
                append('\n')
                append(str(R.string.vpn_offline_warning))
            }
            if (result.state == NetworkState.WHITELIST && !vpn.active) {
                append('\n')
                append(str(R.string.whitelist_vpn_reminder))
            }
        }
        val previousId = settings.lastAlertNotificationId()
        val nextId = nextAlertId(previousId)
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setTicker(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(sound)
            .setVibrate(longArrayOf(0, 280, 160, 280))
            .setSilent(false)
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(nextId))
            .build()
        settings.setLastAlertNotificationId(nextId)
        settings.recordPopup(result.state, now)
        enqueueAlert(nextId, notification, previousId)
    }

    private fun enqueueAlert(id: Int, notification: Notification, previousId: Int) {
        pendingAlert?.let { mainHandler.removeCallbacks(it) }
        val task = Runnable {
            try {
                val manager = appContext.getSystemService(NotificationManager::class.java)
                manager.notify(id, notification)
                if (previousId != 0 && previousId != id) {
                    mainHandler.postDelayed(
                        { manager.cancel(previousId) },
                        OLD_ALERT_CANCEL_DELAY_MS,
                    )
                }
            } catch (_: SecurityException) {
            }
        }
        pendingAlert = task
        mainHandler.postDelayed(task, ALERT_DELAY_MS)
    }

    private fun dismissPostedAlert() {
        val manager = appContext.getSystemService(NotificationManager::class.java)
        val id = settings.lastAlertNotificationId()
        if (id != 0) {
            manager.cancel(id)
        }
    }

    private fun pinOngoing(notification: Notification): Notification {
        notification.flags = notification.flags or
            Notification.FLAG_ONGOING_EVENT or
            Notification.FLAG_NO_CLEAR or
            Notification.FLAG_FOREGROUND_SERVICE
        return notification
    }

    private fun nextAlertId(previousId: Int): Int {
        if (previousId < ALERT_ID_START) return ALERT_ID_START
        if (previousId >= ALERT_ID_START + 1_000_000) return ALERT_ID_START
        return previousId + 1
    }

    private fun notify(id: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(appContext).notify(id, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun openAppIntent(requestCode: Int): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun statusLabel(state: NetworkState?): String = when (state) {
        NetworkState.ONLINE -> str(R.string.status_online)
        NetworkState.WHITELIST -> str(R.string.status_whitelist)
        NetworkState.OFFLINE -> str(R.string.status_offline)
        NetworkState.UNKNOWN -> str(R.string.status_unknown)
        null -> str(R.string.checking)
    }

    private fun statusDescription(state: NetworkState): String = when (state) {
        NetworkState.ONLINE -> str(R.string.status_online_desc)
        NetworkState.WHITELIST -> str(R.string.status_whitelist_desc)
        NetworkState.OFFLINE -> str(R.string.status_offline_desc)
        NetworkState.UNKNOWN -> str(R.string.status_unknown_desc)
    }

    private fun vpnLine(vpn: VpnStatus?): String = when {
        vpn == null -> str(R.string.vpn_off)
        !vpn.active -> str(R.string.vpn_off)
        vpn.countryCode.isNullOrBlank() -> str(R.string.vpn_on_unknown)
        else -> str(R.string.vpn_on_country, vpn.countryCode)
    }

    companion object {
        const val CHANNEL_STATUS = "status"
        const val CHANNEL_ALERTS = "alerts_heads"
        private const val CHANNEL_ALERTS_LEGACY = "alerts"
        const val ONGOING_ID = 1
        private const val ALERT_ID_START = 1000
        private const val ALERT_DELAY_MS = 800L
        private const val OLD_ALERT_CANCEL_DELAY_MS = 500L
    }
}
