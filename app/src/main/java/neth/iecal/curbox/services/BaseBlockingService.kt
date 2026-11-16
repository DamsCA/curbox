package neth.iecal.curbox.services

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import neth.iecal.curbox.R
import neth.iecal.curbox.utils.DataStoreManager
import kotlin.lazy

@SuppressLint("AccessibilityPolicy")
open class BaseBlockingService : AccessibilityService() {

    val dataStoreManager  by lazy {
        DataStoreManager(this)
    }


    var lastBackPressTimeStamp: Long =
        SystemClock.uptimeMillis() // prevents repetitive global actions

    override fun onServiceConnected() {
        super.onServiceConnected()
        startForegroundService()
    }

    private fun startForegroundService() {
        val channelId = "blocking_service_channel"
        val channelName = getString(R.string.blocking_service_channel_name)

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.blocking_service_channel_description)
        }
        notificationManager.createNotificationChannel(channel)

        val className = this::class.simpleName
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.blocking_service_notification_title, className))
            .setContentText(getString(R.string.blocking_service_notification_text))
            .setSmallIcon(R.drawable.icon)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .build()

        val notificationId = this.javaClass.simpleName.hashCode()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(notificationId, notification)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onInterrupt() {
    }


    fun isDelayOver( delay: Int): Boolean {
        val currentTime = SystemClock.uptimeMillis().toFloat()
        return currentTime - lastBackPressTimeStamp > delay
    }

    fun pressHome() {
        logEjection("HOME")
        performGlobalAction(GLOBAL_ACTION_HOME)
        lastBackPressTimeStamp = SystemClock.uptimeMillis()
    }

    fun pressBack() {
            logEjection("BACK")
            performGlobalAction(GLOBAL_ACTION_BACK)
            lastBackPressTimeStamp = SystemClock.uptimeMillis()

    }

    /** Raison detaillee fournie par le bloqueur juste avant d'ejecter (terme trouve, etc.). */
    var ejectReason: String = ""

    // Journal des ejections (files/eject_log.txt) : quel bloqueur a renvoye a l'accueil,
    // depuis quelle app et pourquoi. Sert a diagnostiquer les fermetures inexpliquees.
    private fun logEjection(action: String) {
        runCatching {
            val caller = Throwable().stackTrace.firstOrNull {
                !it.className.endsWith("BaseBlockingService")
            }
            val pkg = runCatching { rootInActiveWindow?.packageName }.getOrNull()
            val time = java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.ROOT)
                .format(java.util.Date())
            val by = caller?.let { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
            val line = "$time $action pkg=$pkg by=$by $ejectReason" + System.lineSeparator()
            val f = java.io.File(filesDir, "eject_log.txt")
            if (f.length() > 200_000) f.writeText("")
            f.appendText(line)
            android.util.Log.w("FocusEject", line.trim())
        }
        ejectReason = ""
    }
}
