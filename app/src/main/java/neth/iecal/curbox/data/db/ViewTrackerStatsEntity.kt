package neth.iecal.curbox.data.db

import androidx.room.Entity

@Entity(tableName = "view_tracker_stats", primaryKeys = ["date", "packageName", "ruleId"])
data class ViewTrackerStatsEntity(
    val date: String,
    val packageName: String,
    /** For built-in rules: the rule id. For custom tracking rules: a stable identifier derived from the rule string. */
    val ruleId: String,
    /** Human-readable label shown in the UI. */
    val label: String,
    val count: Int = 0
)
