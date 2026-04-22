package neth.iecal.curbox.trackers

import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import neth.iecal.curbox.blockers.viewblocker.ViewBlocker
import neth.iecal.curbox.blockers.viewblocker.ViewBlockerFilterRule
import neth.iecal.curbox.blockers.viewblocker.ViewBlockerRuleParser
import neth.iecal.curbox.blockers.viewblocker.toFilterRule
import neth.iecal.curbox.data.db.AppDatabase
import neth.iecal.curbox.data.db.ViewTrackerStatsDao
import neth.iecal.curbox.data.db.ViewTrackerStatsEntity
import neth.iecal.curbox.services.BaseBlockingService
import neth.iecal.curbox.utils.TimeTools

/**
 * Tracks how many times specific views are encountered per app, per day.
 *
 * By default, all pre-built ViewBlocker rules are tracked regardless of whether
 * the ViewBlocker feature is enabled or disabled.  Users can also add custom
 * tracking rules (same format as ViewBlocker custom rules) on a per-app basis.
 *
 * To avoid counting the same view multiple times in rapid bursts, each rule is
 * subject to a [RULE_COOLDOWN_MS] cooldown before it can be counted again.
 */
class ViewTracker {

    companion object {
        private const val TAG = "ViewTracker"

        /** Minimum gap between two increments of the same rule in a single app session. */
        private const val RULE_COOLDOWN_MS = 3_000L

        private val TARGET_EVENTS_MASK =
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
            AccessibilityEvent.TYPE_VIEW_SCROLLED
    }

    private lateinit var service: BaseBlockingService
    private lateinit var statsDao: ViewTrackerStatsDao

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ViewBlocker instance reused for its public matching helpers (findNodeByMatcher, hasRuleMatch).
    private val viewBlockerHelper = ViewBlocker()

    // rules grouped by packageName -> list of (filterRule, stableId, label)
    private data class TrackingEntry(
        val rule: ViewBlockerFilterRule,
        val ruleId: String,
        val label: String
    )
    @Volatile private var rulesByPackage: Map<String, List<TrackingEntry>> = emptyMap()

    // Cooldown map: ruleId -> last-counted uptimeMs
    private val lastCounted = HashMap<String, Long>()

    fun setup(service: BaseBlockingService) {
        this.service = service
        viewBlockerHelper.service = service

        val db = AppDatabase.getInstance(service)
        this.statsDao = db.viewTrackerStatsDao()

        scope.launch {
            service.dataStoreManager.settings.collectLatest { settings ->
                rebuildRules(settings.viewTrackerConfig.customRules)
            }
        }
    }

    /** Rebuild the [rulesByPackage] map whenever settings change. */
    private fun rebuildRules(customRuleStrings: List<String>) {
        val entries = mutableMapOf<String, MutableList<TrackingEntry>>()

        // Always track all default ViewBlocker rules.
        for (defaultRule in ViewBlocker.DEFAULT_RULES) {
            val filterRule = defaultRule.toFilterRule()
            entries.getOrPut(defaultRule.packageName) { mutableListOf() }
                .add(TrackingEntry(filterRule, defaultRule.id, defaultRule.label))
        }

        // Also track user-added custom tracking rules.
        val parsed = ViewBlockerRuleParser.parseRules(customRuleStrings)
        for (rule in parsed) {
            // Use a stable id derived from the rule string so it survives restarts.
            val stableId = "custom_${rule.ruleString.hashCode()}"
            val label = rule.description ?: extractLabel(rule.ruleString)
            entries.getOrPut(rule.packageName) { mutableListOf() }
                .add(TrackingEntry(rule, stableId, label))
        }

        rulesByPackage = entries
    }

    fun onEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if ((event.eventType and TARGET_EVENTS_MASK) == 0) return

        val pkg = event.packageName?.toString() ?: return
        val entries = rulesByPackage[pkg] ?: return

        try {
            val root = service.rootInActiveWindow ?: return
            val date = TimeTools.getCurrentDate()
            val now = SystemClock.uptimeMillis()

            try {
                for (entry in entries) {
                    // Honour cooldown to avoid counting the same view every millisecond.
                    val lastTime = lastCounted[entry.ruleId] ?: 0L
                    if (now - lastTime < RULE_COOLDOWN_MS) continue

                    if (viewBlockerHelper.hasRuleMatch(root, entry.rule)) {
                        lastCounted[entry.ruleId] = now
                        incrementCount(date, pkg, entry.ruleId, entry.label)
                    }
                }
            } finally {
                @Suppress("DEPRECATION")
                root.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onEvent", e)
        }
    }

    /** Increment the counter for (date, packageName, ruleId) in the Room database. */
    private fun incrementCount(date: String, packageName: String, ruleId: String, label: String) {
        scope.launch {
            try {
                val existing = statsDao.getStat(date, packageName, ruleId)
                statsDao.upsert(
                    ViewTrackerStatsEntity(
                        date = date,
                        packageName = packageName,
                        ruleId = ruleId,
                        label = label,
                        count = (existing?.count ?: 0) + 1
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save view tracker stat", e)
            }
        }
    }

    /**
     * Extract a short label from a custom rule string.
     * Looks for a `comment:` token first; falls back to the package short name.
     */
    private fun extractLabel(ruleString: String): String {
        val commentMatch = Regex("""comment:(?:"([^"]*)"|(\S+))""").find(ruleString)
        if (commentMatch != null) {
            return commentMatch.groupValues[1].ifEmpty { commentMatch.groupValues[2] }
        }
        val pkgMatch = Regex("""pkg:(\S+)""").find(ruleString)
        val pkgShort = pkgMatch?.groupValues?.get(1)?.substringAfterLast(".") ?: "rule"
        val firstToken = ruleString.trim().split(" ").firstOrNull { !it.startsWith("pkg:") } ?: ""
        return if (firstToken.isEmpty()) pkgShort else "$pkgShort: $firstToken"
    }

    fun onDestroy() {
        // no persistent resources to clean up
    }
}
