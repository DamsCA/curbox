package neth.iecal.curbox.blockers

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import java.io.File
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import neth.iecal.curbox.services.BaseBlockingService

/**
 * Persistent "hard lock". While locked, Focus cannot be disabled or uninstalled.
 * The lock can only be extended, never shortened, until it naturally expires.
 */
object FocusLock {
    const val MAX_MINUTES = 525_600 // 365 jours = plafond de securite
    const val MAX_DAYS = 365
    private const val FILE_NAME = "focus_lock.txt"

    // Stocke le verrou dans un fichier de filesDir (partage par TOUS les processus).
    // Les SharedPreferences sont cachees par processus et NE se synchronisent PAS entre
    // l'UI et le service d'accessibilite (:app_blocker_service) -> le verrou serait invisible
    // pour l'auto-defense. La lecture fichier a frais resout ca.
    fun lockedUntil(ctx: Context): Long = runCatching {
        val f = File(ctx.filesDir, FILE_NAME)
        if (f.exists()) f.readText().trim().toLongOrNull() ?: 0L else 0L
    }.getOrDefault(0L)

    fun isLocked(ctx: Context): Boolean = lockedUntil(ctx) > System.currentTimeMillis()

    private fun extendTo(ctx: Context, until: Long) {
        runCatching {
            if (until > lockedUntil(ctx)) {
                val tmp = File(ctx.filesDir, "$FILE_NAME.tmp")
                tmp.writeText(until.toString())
                tmp.renameTo(File(ctx.filesDir, FILE_NAME))
            }
        }
    }

    fun lockForDays(ctx: Context, days: Int) =
        extendTo(ctx, System.currentTimeMillis() + days.coerceIn(0, MAX_DAYS).toLong() * 24L * 60L * 60L * 1000L)

    fun lockForMinutes(ctx: Context, minutes: Int) =
        extendTo(ctx, System.currentTimeMillis() + minutes.coerceIn(0, MAX_MINUTES).toLong() * 60L * 1000L)
}

/**
 * Watches Settings/installer screens while the hard lock is active and forces the user
 * out of any page that would let them disable the accessibility service, deactivate the
 * device admin, or uninstall Focus.
 */
class SelfDefense {
    private var service: BaseBlockingService? = null
    private var lastAction = 0L
    private var lastGrayscale = 0L

    fun setup(service: BaseBlockingService) {
        this.service = service
    }

    /** Tant que le verrou est actif, force l'ecran en noir & blanc (re-applique si coupe). */
    private fun enforceGrayscaleIfLocked(svc: BaseBlockingService) {
        if (!FocusLock.isLocked(svc)) return
        if (SystemClock.uptimeMillis() - lastGrayscale < 3000) return
        lastGrayscale = SystemClock.uptimeMillis()
        runCatching {
            val cr = svc.contentResolver
            val on = Settings.Secure.getInt(cr, "accessibility_display_daltonizer_enabled", 0) == 1 &&
                Settings.Secure.getInt(cr, "accessibility_display_daltonizer", -1) == 0
            if (!on) {
                Settings.Secure.putInt(cr, "accessibility_display_daltonizer", 0)
                Settings.Secure.putInt(cr, "accessibility_display_daltonizer_enabled", 1)
            }
        }
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
        enforceGrayscaleIfLocked(svc)
        val pkg = (ev.packageName?.toString() ?: return).lowercase()

        // Verrou actif: empeche d'ouvrir l'app-bloqueur gardee (Stay Focused) pour qu'on ne
        // puisse pas la desactiver. Sa desinstallation est deja couverte (son nom contient "focus").
        if (pkg == "com.stayfocused") {
            if (FocusLock.isLocked(svc) && SystemClock.uptimeMillis() - lastAction >= 500) {
                lastAction = SystemClock.uptimeMillis()
                svc.pressBack()
                svc.pressHome()
            }
            return
        }

        val systemUi = pkg in watchedPackages || pkg.contains("settings") ||
            pkg.contains("packageinstaller") || pkg.contains("permissioncontroller") ||
            pkg.contains("securitycenter") || pkg.contains("packagemanager")
        if (!systemUi) return
        if (!FocusLock.isLocked(svc)) return
        if (SystemClock.uptimeMillis() - lastAction < 500) return

        val sb = StringBuilder()
        collectAllWindowsText(svc, sb)
        val text = sb.toString().lowercase()
        if (text.isEmpty()) return

        val mentionsOurServices =
            text.contains("focus protection") || text.contains("focus suivi")

        // Tightened to AVOID false positives on benign Samsung screens that merely
        // contain "Focus" + a common word like "désactiver" (One UI Focus mode, etc.).
        // We only react to app-management / device-admin / uninstall screens for OUR app.
        val dangerousFocusPage = text.contains("focus") && (
            text.contains("désinstaller") || text.contains("uninstall") ||
            text.contains("forcer l'arrêt") || text.contains("force stop") ||
            text.contains("administration de l'appareil") ||
            text.contains("administrateur de l'appareil") ||
            text.contains("device admin")
        )

        if (mentionsOurServices || dangerousFocusPage) {
            lastAction = SystemClock.uptimeMillis()
            svc.pressBack()
            svc.pressHome()
        }
    }

    private fun collectAllWindowsText(svc: BaseBlockingService, sb: StringBuilder) {
        // Only the FOCUSED window. Reading all windows also captured persistent
        // notification / status-bar text, causing false positives that blocked every
        // Settings screen. The active window holds the dialog/screen we care about.
        runCatching { collectText(svc.rootInActiveWindow, sb, 0) }
    }

    private fun collectText(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        if (node == null || depth > 45 || sb.length > 8000) return
        node.text?.let { sb.append(it).append(' ') }
        node.contentDescription?.let { sb.append(it).append(' ') }
        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            collectText(child, sb, depth + 1)
            @Suppress("DEPRECATION") child.recycle()
        }
    }
}
