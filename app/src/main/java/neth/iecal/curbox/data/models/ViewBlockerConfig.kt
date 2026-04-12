package neth.iecal.curbox.data.models

import android.graphics.Color

/**
 * UI and persistence model for a single built-in view-blocking rule.
 *
 * This is the model that is stored in DataStore and shown in the UI. It carries a stable
 * [id], a human-readable [label], and an [isEnabled] toggle.
 *
 * At runtime, enabled rules are converted directly to
 * [neth.iecal.curbox.blockers.viewblocker.ViewBlockerFilterRule] objects via the
 * `ViewBlockerRule.toFilterRule()` extension defined in ViewBlockerRuleParser.kt.
 *
 * Fields that accept NodeMatcher syntax strings (e.g. [requirePresent], [requireAbsent])
 * use the format `"type:value;type2:value2"` — see NodeMatcher.parse() in ViewBlockerFilterRule.kt.
 * Multiple matchers in [requirePresent]/[requireAbsent] are pipe-separated: `"m1|m2"`.
 */
data class ViewBlockerRule(
    val id: String,
    val packageName: String,
    /** Human-readable description shown in the UI toggle list. */
    val label: String,
    val viewId: String? = null,
    /** Pipe-separated content descriptions, e.g. `"Like|Me gusta"`. */
    val desc: String? = null,
    val path: String? = null,
    val className: String? = null,
    val text: String? = null,
    val color: Int = Color.WHITE,
    val blockTouches: Boolean = true,
    val isEnabled: Boolean = false,
    /** NodeMatcher strings; rule fires only when ALL are absent from the screen. */
    val requireAbsent: List<String> = emptyList(),
    /** NodeMatcher strings; rule fires only when ALL are present on the screen. */
    val requirePresent: List<String> = emptyList(),
    /** `"overlay"` (default) or `"back"`. */
    val action: String? = null,
    val textContains: String? = null,
    val descContains: String? = null,
    val textRegex: String? = null,
    val descRegex: String? = null,
    /** A single NodeMatcher string to match children of the target node. */
    val matchChildren: String? = null,
    /** NodeMatcher string identifying the layout to cover entirely. */
    val blockLayout: String? = null,
    /** Pipe-separated NodeMatcher strings for regions to leave uncovered inside blockLayout. */
    val excludeFromLayout: String? = null,
    /** `"true"` / `"false"` / `"1"` / `"0"` / `"yes"` / `"no"`. */
    val clickable: String? = null,
    val maxPerScreen: Int = 0
)

/**
 * Top-level configuration for the ViewBlocker feature, stored in DataStore.
 *
 * [rules] holds the built-in rule list (with per-rule enabled flags).
 * [customRules] holds raw user-entered rule strings in either the token-based or
 * legacy `##`-delimited format; disabled custom rules are prefixed with `!DISABLED!`.
 */
data class ViewBlockerConfig(
    val isActive: Boolean = false,
    val rules: List<ViewBlockerRule> = emptyList(),
    val customRules: List<String> = emptyList()
)
