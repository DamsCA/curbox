package neth.iecal.curbox.data.models

/**
 * Configuration for the View Tracker feature, stored in DataStore.
 *
 * [customRules] holds user-added rule strings in ViewBlocker rule format
 * (token-based: `pkg:com.example id:com.example:id/foo comment:"My Rule"`).
 * These rules are tracked (encounter-counted) but NOT blocked.
 */
data class ViewTrackerConfig(
    val customRules: List<String> = emptyList()
)
