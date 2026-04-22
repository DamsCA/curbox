package neth.iecal.curbox.ui.fragments.main.usage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import neth.iecal.curbox.data.db.AppDatabase
import neth.iecal.curbox.data.db.ViewTrackerStatsEntity
import neth.iecal.curbox.data.models.ViewTrackerConfig
import neth.iecal.curbox.utils.DataStoreManager
import neth.iecal.curbox.utils.TimeTools

/**
 * ViewModel for the individual app usage breakdown screen.
 * Exposes today's view tracker stats for the given [packageName] and manages
 * custom per-app tracking rules stored in [ViewTrackerConfig].
 */
class AppUsageBreakdownViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStoreManager = DataStoreManager(application)
    private val viewTrackerStatsDao = AppDatabase.getInstance(application).viewTrackerStatsDao()

    private val _viewTrackerStats = MutableStateFlow<List<ViewTrackerStatsEntity>>(emptyList())
    val viewTrackerStats: StateFlow<List<ViewTrackerStatsEntity>> = _viewTrackerStats

    private val _viewTrackerConfig = MutableStateFlow(ViewTrackerConfig())
    val viewTrackerConfig: StateFlow<ViewTrackerConfig> = _viewTrackerConfig

    // Package name set from the fragment once it knows which app it is showing.
    private val _packageName = MutableStateFlow<String?>(null)

    init {
        // Observe DataStore for custom tracking config changes.
        viewModelScope.launch {
            dataStoreManager.settings.collectLatest { settings ->
                _viewTrackerConfig.value = settings.viewTrackerConfig
            }
        }

        // Whenever the package name changes, start streaming tracker stats for that app.
        viewModelScope.launch {
            _packageName.flatMapLatest { pkg ->
                if (pkg == null) {
                    flowOf(emptyList())
                } else {
                    viewTrackerStatsDao.getStatsForAppFlow(TimeTools.getCurrentDate(), pkg)
                }
            }.collectLatest { stats ->
                // Filter out zero-duration entries so the list stays clean.
                _viewTrackerStats.value = stats.filter { it.durationMs > 0 }
            }
        }
    }

    /** Call from the fragment after it knows which app is being shown. */
    fun setPackageName(packageName: String) {
        _packageName.value = packageName
    }

    /** Add a custom tracking rule for a specific app. The rule must follow the ViewBlocker token format. */
    fun addCustomTrackingRule(ruleString: String) {
        if (ruleString.isBlank()) return
        viewModelScope.launch {
            val current = _viewTrackerConfig.value.customRules.toMutableList()
            if (!current.contains(ruleString)) {
                current.add(ruleString)
                dataStoreManager.updateViewTrackerConfig(
                    _viewTrackerConfig.value.copy(customRules = current)
                )
            }
        }
    }

    /** Remove a custom tracking rule. */
    fun removeCustomTrackingRule(ruleString: String) {
        viewModelScope.launch {
            val current = _viewTrackerConfig.value.customRules.toMutableList()
            current.remove(ruleString)
            dataStoreManager.updateViewTrackerConfig(
                _viewTrackerConfig.value.copy(customRules = current)
            )
        }
    }
}
