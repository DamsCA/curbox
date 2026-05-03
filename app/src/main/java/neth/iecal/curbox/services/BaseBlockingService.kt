package neth.iecal.curbox.services

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import neth.iecal.curbox.R
import neth.iecal.curbox.utils.DataStoreManager
import kotlin.lazy

open class BaseBlockingService : AccessibilityService() {

    companion object {
        private const val FOREGROUND_CHANNEL_ID = "curbox_fg_service"
    }

    protected open val foregroundNotificationId: Int = 9001

    val dataStoreManager  by lazy {
        DataStoreManager(this)
    }


    var lastBackPressTimeStamp: Long =
        SystemClock.uptimeMillis() // prevents repetitive global actions

    override fun onServiceConnected() {
        super.onServiceConnected()
        startForegroundNotification()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onDestroy() {
        stopForegroundNotification()
        super.onDestroy()
    }

    override fun onInterrupt() {
    }

    private fun startForegroundNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            FOREGROUND_CHANNEL_ID,
            getString(R.string.fg_service_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.fg_service_title))
            .setContentText(getString(R.string.fg_service_description))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
        startForeground(foregroundNotificationId, notification)
    }

    private fun stopForegroundNotification() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }


    fun isDelayOver(lastTimestamp: Long, delay: Int): Boolean {
        val currentTime = SystemClock.uptimeMillis().toFloat()
        return currentTime - lastTimestamp > delay
    }

    fun pressHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        lastBackPressTimeStamp = SystemClock.uptimeMillis()

    }

    fun pressBack() {
            performGlobalAction(GLOBAL_ACTION_BACK)
            lastBackPressTimeStamp = SystemClock.uptimeMillis()

    }
}
