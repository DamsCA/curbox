package neth.iecal.curbox.blockers

import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import neth.iecal.curbox.services.BaseBlockingService
import java.util.Locale

/**
 * Bloque des termes de recherche tapes A L'INTERIEUR des applications.
 *
 * KeywordBlocker ne voit que les navigateurs (via la barre d'URL). Il est donc aveugle
 * a une recherche faite dans TikTok ou Instagram. Ici on lit le texte au moment ou il
 * est saisi, ce qui ne depend d'aucun identifiant interne de ces apps et survit donc
 * a leurs mises a jour.
 */
class SearchTermBlocker {
    private var service: BaseBlockingService? = null
    private var lastAction = 0L

    private val watchedPackages = setOf(
        "com.zhiliaoapp.musically",      // TikTok
        "com.ss.android.ugc.trill",      // TikTok (variante)
        "com.instagram.android",         // Instagram
        "com.instagram.barcelona",       // Threads
        "com.google.android.youtube",
        "com.snapchat.android",
        "com.twitter.android",
        "com.x.android",
        "com.reddit.frontpage",
        "com.pinterest",
        "com.google.android.googlequicksearchbox"
    )

    /**
     * Termes bannis, deja normalises : minuscules, sans espace ni ponctuation.
     * La normalisation de la saisie fait que "Daisy Marie", "daisy_marie",
     * "daisy.marie" et "@daisymarie" tombent tous sur "daisymarie".
     */
    private val blockedTerms = listOf(
        "melodyfufflyngton",
        "fufflyngton",
        "daisymarie",
        "bouncingbunny"
    )

    fun setup(service: BaseBlockingService) {
        this.service = service
    }

    private fun normalize(text: String): String =
        text.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    fun check(event: AccessibilityEvent?) {
        val svc = service ?: return
        val ev = event ?: return

        val pkg = ev.packageName?.toString() ?: return
        if (pkg !in watchedPackages) return

        if (ev.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return

        val typed = runCatching { ev.text?.joinToString(" ") }.getOrNull() ?: return
        if (typed.isEmpty()) return

        val normalized = normalize(typed)
        if (normalized.length < 4) return

        if (blockedTerms.any { normalized.contains(it) }) {
            if (SystemClock.uptimeMillis() - lastAction < 800) return
            lastAction = SystemClock.uptimeMillis()
            svc.pressBack()
            svc.pressHome()
        }
    }
}
