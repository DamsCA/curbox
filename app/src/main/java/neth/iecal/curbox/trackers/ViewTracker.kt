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
 * Tracks how long specific views are visible per app, per day.
 *
 * By default, all pre-built ViewBlocker rules are tracked regardless of whether
 * the ViewBlocker feature is enabled or disabled.  Users can also add custom
 * tracking rules (same format as ViewBlocker custom rules) on a per-app basis.
 *
 * Duration is measured from the moment a view first appears to when it disappears.
 * Accumulated durations are persisted to Room so the totals survive app restarts.
 * In-progress sessions are flushed on a window-state change (meaning the user left
 * the screen where the view was visible) and also on service destroy.
 */
class ViewTracker {

    companion object {
        private const val TAG = "ViewTracker"

        private val TARGET_EVENTS_MASK =
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
            AccessibilityEvent.TYPE_VIEW_SCROLLED
    }

    private lateinit var service: BaseBlockingService
    private lateinit var statsDao: ViewTrackerStatsDao

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ViewBlocker instance reused for its public matching helpers.
    private val viewBlockerHelper = ViewBlocker()

    // rules grouped by packageName -> list of (filterRule, stableId, label)
    private data class TrackingEntry(
        val rule: ViewBlockerFilterRule,
        val ruleId: String,
        val label: String
    )
    @Volatile private var rulesByPackage: Map<String, List<TrackingEntry>> = emptyMap()

    // Active visibility windows: ruleId -> (uptimeMs when visible started, packageName, label)
    private data class VisibleSession(val startMs: Long, val packageName: String, val label: String)
    private val visibleSince = HashMap<String, VisibleSession>()

    fun setup(service: BaseBlockingService) {
        this.service = service
        viewBlockerHelper.service = service

        val db = AppDatabase.getInstance(service)
        this.statsDao = db.viewTrackerStatsDao()

        scope.launch {
            service.dataStoreManager.settings.collectLatest { settings ->
                // Flush in-progress sessions before switching rule sets.
                flushAllSessions(TimeTools.getCurrentDate())
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
        val date = TimeTools.getCurrentDate()

        // A window state change means the user navigated to a new screen; flush all sessions.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            flushAllSessions(date)
        }

        val entries = rulesByPackage[pkg] ?: run {
            // No rules for this pkg; any stale sessions from previous apps were already flushed above.
            return
        }

        try {
            val root = service.rootInActiveWindow ?: return
            val now = SystemClock.uptimeMillis()

            try {
                for (entry in entries) {
                    val isVisible = viewBlockerHelper.hasRuleMatch(root, entry.rule)
                    val session = visibleSince[entry.ruleId]

                    if (isVisible) {
                        if (session == null) {
                            // View just became visible; start tracking.
                            visibleSince[entry.ruleId] = VisibleSession(now, pkg, entry.label)
                        }
                    } else {
                        if (session != null) {
                            // View is gone; flush the accumulated duration.
                            val duration = now - session.startMs
                            visibleSince.remove(entry.ruleId)
                            if (duration > 0) addDuration(date, pkg, entry.ruleId, entry.label, duration)
                        }
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

    /**
     * Flush all in-progress visibility sessions by crediting the elapsed time since
     * each view became visible.  Called on window transitions and on service destroy.
     */
    private fun flushAllSessions(date: String) {
        if (visibleSince.isEmpty()) return
        val now = SystemClock.uptimeMillis()
        for ((ruleId, session) in visibleSince) {
            val duration = now - session.startMs
            if (duration > 0) addDuration(date, session.packageName, ruleId, session.label, duration)
        }
        visibleSince.clear()
    }

    /** Add [durationMs] to the running total for (date, packageName, ruleId) in Room. */
    private fun addDuration(date: String, packageName: String, ruleId: String, label: String, durationMs: Long) {
        scope.launch {
            try {
                val existing = statsDao.getStat(date, packageName, ruleId)
                statsDao.upsert(
                    ViewTrackerStatsEntity(
                        date = date,
                        packageName = packageName,
                        ruleId = ruleId,
                        label = label,
                        durationMs = (existing?.durationMs ?: 0L) + durationMs
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
        // Flush any in-progress sessions so no tracked time is lost.
        flushAllSessions(TimeTools.getCurrentDate())
    }
}
