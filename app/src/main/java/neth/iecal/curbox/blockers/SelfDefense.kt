package neth.iecal.curbox.blockers

import android.content.Context
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import neth.iecal.curbox.services.BaseBlockingService

/**
 * Persistent "hard lock". While locked, Focus cannot be disabled or uninstalled.
 * The lock can only be extended, never shortened, until it naturally expires.
 */
object FocusLock {
    private const val PREFS = "focus_lock"
    private const val KEY_UNTIL = "hardLockUntil"

    fun lockedUntil(ctx: Context): Long =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_UNTIL, 0L)

    fun isLocked(ctx: Context): Boolean = lockedUntil(ctx) > System.currentTimeMillis()

    fun lockForDays(ctx: Context, days: Int) {
        val until = System.currentTimeMillis() + days.toLong() * 24L * 60L * 60L * 1000L
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (until > prefs.getLong(KEY_UNTIL, 0L)) {
            prefs.edit().putLong(KEY_UNTIL, until).apply()
        }
    }

    fun lockForMinutes(ctx: Context, minutes: Int) {
        val until = System.currentTimeMillis() + minutes.toLong() * 60L * 1000L
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (until > prefs.getLong(KEY_UNTIL, 0L)) {
            prefs.edit().putLong(KEY_UNTIL, until).apply()
        }
    }
}

/**
 * Watches Settings/installer screens while the hard lock is active and forces the user
 * out of any page that would let them disable the accessibility service, deactivate the
 * device admin, or uninstall Focus.
 */
class SelfDefense {
    private var service: BaseBlockingService? = null
    private var lastAction = 0L

    fun setup(service: BaseBlockingService) {
        this.service = service
    }

    private val watchedPackages = setOf(
        "com.android.settings",
        "com.samsung.android.settings",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.miui.securitycenter"
    )

    fun check(event: AccessibilityEvent?) {
        val svc = service ?: return
        val ev = event ?: return
        if (!FocusLock.isLocked(svc)) return
        val pkg = ev.packageName?.toString() ?: return
        if (pkg !in watchedPackages) return
        if (SystemClock.uptimeMillis() - lastAction < 700) return

        val root = svc.rootInActiveWindow ?: return
        val sb = StringBuilder()
        collectText(root, sb, 0)
        val text = sb.toString().lowercase()

        val mentionsOurServices =
            text.contains("focus protection") || text.contains("focus suivi")

        val dangerousAppPage = text.contains("focus") && (
            text.contains("désinstaller") || text.contains("uninstall") ||
            text.contains("forcer l'arrêt") || text.contains("force stop") ||
            text.contains("désactiver") || text.contains("deactivate") ||
            text.contains("turn off") ||
            text.contains("administrateur") || text.contains("device admin")
        )

        if (mentionsOurServices || dangerousAppPage) {
            lastAction = SystemClock.uptimeMillis()
            svc.pressBack()
            svc.pressHome()
        }
    }

    private fun collectText(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        if (node == null || depth > 45 || sb.length > 8000) return
        node.text?.let { sb.append(it).append(' ') }
        node.contentDescription?.let { sb.append(it).append(' ') }
        val count = node.childCount
        for (i in 0 until count) {
            collectText(node.getChild(i), sb, depth + 1)
        }
    }
}
