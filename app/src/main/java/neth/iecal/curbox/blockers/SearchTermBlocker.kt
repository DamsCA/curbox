package neth.iecal.curbox.blockers

import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import neth.iecal.curbox.services.BaseBlockingService
import java.util.Locale

/**
 * Bloque des termes bannis A L'INTERIEUR des applications (TikTok, Instagram...).
 *
 * KeywordBlocker ne lit que la barre d'URL des navigateurs : il est aveugle a une
 * recherche faite dans une app. Ce module couvre quatre chemins d'acces :
 *   1. le terme est TAPE          -> TYPE_VIEW_TEXT_CHANGED
 *   2. le terme est RESTAURE/COLLE/CLIQUE dans l'historique -> scan de l'ecran
 *   3. l'app est ROUVERTE juste apres -> fenetre de blocage persistante
 *   4. le champ garde le terme    -> le champ est vide avant l'ejection
 */
class SearchTermBlocker {
    private var service: BaseBlockingService? = null
    private var lastAction = 0L
    private var lastScan = 0L
    private var blockedUntil = 0L

    /** Duree pendant laquelle l'app reste inaccessible apres une detection. */
    private val blockWindowMs = 20_000L

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
     * On vise le fragment le plus distinctif (le nom de famille suffit), pour qu'une
     * saisie partielle soit prise aussi, et on couvre les fautes de frappe courantes.
     */
    private val blockedTerms = listOf(
        "fluffington",
        "flufington",
        "fluffigton",
        "fufflyngton",
        "fuflyngton",
        "daisymarie",
        "bouncingbunny",
        "bouncebunny"
    )

    fun setup(service: BaseBlockingService) {
        this.service = service
    }

    private fun normalize(text: String): String =
        text.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    private fun containsBanned(text: String): Boolean {
        val n = normalize(text)
        if (n.length < 4) return false
        return blockedTerms.any { n.contains(it) }
    }

    fun check(event: AccessibilityEvent?) {
        val svc = service ?: return
        val ev = event ?: return
        val pkg = ev.packageName?.toString() ?: return
        if (pkg !in watchedPackages) return

        val now = SystemClock.uptimeMillis()

        // (3) L'app a ete rouverte pendant la fenetre de blocage : on ressort.
        if (now < blockedUntil) {
            eject(svc, now)
            return
        }

        when (ev.eventType) {
            // (1) Terme tape au clavier : le texte est dans l'evenement, aucun scan requis.
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val typed = runCatching { ev.text?.joinToString(" ") }.getOrNull() ?: return
                if (containsBanned(typed)) trigger(svc, now)
            }
            // (2) Terme deja affiche : restaure a l'ouverture, colle, ou choisi
            // dans l'historique. Aucun evenement de frappe n'est emis dans ces cas.
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (now - lastScan < 500) return
                lastScan = now
                if (screenContainsBanned(svc)) trigger(svc, now)
            }
        }
    }

    /**
     * Ne lit QUE les champs de saisie (barre de recherche), pas tout l'ecran.
     * Lire tout l'ecran declenchait sur n'importe quelle mention du terme (description
     * d'une video, commentaire, suggestion) et rendait l'app inutilisable en boucle.
     */
    private fun screenContainsBanned(svc: BaseBlockingService): Boolean {
        val root = runCatching { svc.rootInActiveWindow }.getOrNull() ?: return false
        val sb = StringBuilder()
        runCatching { collectEditableText(root, sb, 0) }
        return sb.isNotEmpty() && containsBanned(sb.toString())
    }

    private fun collectEditableText(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        if (node == null || depth > 30 || sb.length > 2000) return
        if (node.isEditable) {
            node.text?.let { sb.append(it).append(' ') }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectEditableText(child, sb, depth + 1)
            @Suppress("DEPRECATION") child.recycle()
        }
    }

    private fun trigger(svc: BaseBlockingService, now: Long) {
        blockedUntil = now + blockWindowMs
        clearSearchFields(svc)   // (4) sinon le terme revient a la prochaine ouverture
        eject(svc, now)
    }

    /** Vide les champs de saisie contenant un terme banni, pour ne pas le laisser en place. */
    private fun clearSearchFields(svc: BaseBlockingService) {
        runCatching {
            val root = svc.rootInActiveWindow ?: return
            clearEditable(root, 0)
        }
    }

    private fun clearEditable(node: AccessibilityNodeInfo?, depth: Int) {
        if (node == null || depth > 30) return
        if (node.isEditable) {
            val current = node.text?.toString() ?: ""
            if (current.isNotEmpty() && containsBanned(current)) {
                val args = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, ""
                    )
                }
                runCatching { node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) }
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            clearEditable(child, depth + 1)
            @Suppress("DEPRECATION") child.recycle()
        }
    }

    private fun eject(svc: BaseBlockingService, now: Long) {
        if (now - lastAction < 700) return
        lastAction = now
        svc.pressBack()
        svc.pressHome()
    }
}
