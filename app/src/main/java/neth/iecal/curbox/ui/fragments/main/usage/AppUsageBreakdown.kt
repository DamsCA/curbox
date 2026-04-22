package neth.iecal.curbox.ui.fragments.main.usage

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import neth.iecal.curbox.R
import neth.iecal.curbox.data.db.ViewTrackerStatsEntity
import neth.iecal.curbox.databinding.FragmentAppUsageBreakdownBinding
import neth.iecal.curbox.ui.activity.FragmentActivity
import neth.iecal.curbox.ui.fragments.main.reducers.blockertools.appBlocker.CreateAppGroupFragment
import neth.iecal.curbox.ui.fragments.main.reducers.blockertools.autofocus.CreateAutoFocusGroupFragment
import neth.iecal.curbox.ui.fragments.main.reducers.anti_stimulants.grayscale.CreateGrayscaleGroupFragment
import neth.iecal.curbox.utils.TimeTools

class AppUsageBreakdown(private val stat: AllAppsUsageFragment.Stat) : Fragment() {

    private lateinit var binding: FragmentAppUsageBreakdownBinding
    private val shortcutViewModel: SetupShortcutViewModel by viewModels()
    private val breakdownViewModel: AppUsageBreakdownViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentAppUsageBreakdownBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupLineChart(binding.lineChart)
        plotUsageData()

        try {
            val appInfo = requireContext().packageManager.getApplicationInfo(stat.packageName, 0)
            binding.appName.text = appInfo.loadLabel(requireContext().packageManager)
            binding.appIcon.setImageDrawable(appInfo.loadIcon(requireContext().packageManager))
        } catch (_: Exception) {}
        
        binding.screentime.text = TimeTools.formatTime(stat.totalTime, false)
        binding.sessions.text = stat.startTimes.size.toString()

        // Tell the breakdown ViewModel which app we are showing.
        breakdownViewModel.setPackageName(stat.packageName)

        observeShortcuts()
        observeViewTrackerStats()
        setupAddTrackingRuleButton()
    }

    private fun observeShortcuts() {
        viewLifecycleOwner.lifecycleScope.launch {
            shortcutViewModel.settings.collectLatest { settings ->
                if (settings == null) return@collectLatest
                binding.dynamicShortcutsContainer.removeAllViews()

                val matchedAppGroups = settings.blockedAppGroups.filter { it.selectedPackages.contains(stat.packageName) }
                matchedAppGroups.forEach { group ->
                    addShortcutCard(
                        title = group.name,
                        subtitle = "App Blocker",
                        isActive = group.isActive,
                        iconRes = R.drawable.ic_app_blocker_aesthetic,
                        onToggle = { active -> shortcutViewModel.toggleAppGroup(group.id, active) },
                        onClick = {
                            startActivity(Intent(requireContext(), FragmentActivity::class.java).apply {
                                putExtra("fragment", CreateAppGroupFragment.FRAGMENT_ID)
                                putExtra("group_id", group.id)
                            })
                        }
                    )
                }

                val matchedGrayscale = settings.grayscaleGroups.filter { it.packages.contains(stat.packageName) }
                matchedGrayscale.forEach { group ->
                    addShortcutCard(
                        title = group.groupName,
                        subtitle = "Grayscale",
                        isActive = group.isActive,
                        iconRes = R.drawable.ic_grayscale_aesthetic,
                        onToggle = { active -> shortcutViewModel.toggleGrayscaleGroup(group.groupId, active) },
                        onClick = {
                            startActivity(Intent(requireContext(), FragmentActivity::class.java).apply {
                                putExtra("fragment", CreateGrayscaleGroupFragment.FRAGMENT_ID)
                                putExtra("group_id", group.groupId)
                            })
                        }
                    )
                }

                val matchedAutoFocus = settings.autoFocusGroups.filter { it.packages.contains(stat.packageName) }
                matchedAutoFocus.forEach { group ->
                    addShortcutCard(
                        title = group.groupName,
                        subtitle = "Auto Focus",
                        isActive = true,
                        iconRes = R.drawable.ic_timer_aesthetic,
                        onToggle = null,
                        onClick = {
                            startActivity(Intent(requireContext(), FragmentActivity::class.java).apply {
                                putExtra("fragment", CreateAutoFocusGroupFragment.FRAGMENT_ID)
                                putExtra("group_id", group.groupId)
                            })
                        }
                    )
                }
            }
        }
    }

    private fun observeViewTrackerStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            breakdownViewModel.viewTrackerStats.collectLatest { stats ->
                buildTrackerStatsCards(stats)
            }
        }
    }

    /** Build/rebuild the view-tracker stats cards from [stats]. */
    private fun buildTrackerStatsCards(stats: List<ViewTrackerStatsEntity>) {
        val container = binding.viewTrackerContainer
        container.removeAllViews()

        if (stats.isEmpty()) {
            // Show a placeholder so the section is still visible even with no data yet.
            val placeholder = TextView(requireContext()).apply {
                text = getString(R.string.view_tracker_no_data)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                val typedValue = android.util.TypedValue()
                context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
                setTextColor(typedValue.data)
                alpha = 0.7f
            }
            container.addView(placeholder)
            return
        }

        for (stat in stats) {
            val card = MaterialCardView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (8 * resources.displayMetrics.density).toInt() }

                val typedValue = android.util.TypedValue()
                context.theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainerHigh, typedValue, true)
                setCardBackgroundColor(typedValue.data)
                radius = 16 * resources.displayMetrics.density
                cardElevation = 0f
                strokeWidth = 0

                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    val p = (16 * resources.displayMetrics.density).toInt()
                    setPadding(p, p, p, p)
                }

                val labelView = TextView(requireContext()).apply {
                    text = stat.label
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = (8 * resources.displayMetrics.density).toInt()
                    }
                    val typedVal = android.util.TypedValue()
                    context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedVal, true)
                    setTextColor(typedVal.data)
                }

                val countView = TextView(requireContext()).apply {
                    text = TimeTools.formatTime(stat.durationMs, false)
                    textSize = 14f
                    val typedVal = android.util.TypedValue()
                    context.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedVal, true)
                    setTextColor(typedVal.data)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }

                row.addView(labelView)
                row.addView(countView)
                addView(row)
            }
            container.addView(card)
        }
    }

    private fun setupAddTrackingRuleButton() {
        binding.btnCreateNewRule.setOnClickListener {
            val options = arrayOf(
                getString(R.string.app_blocker),
                getString(R.string.grayscale),
                getString(R.string.auto_focus),
                getString(R.string.view_tracker_add_rule)
            )
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.add_a_reducer))
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> startActivity(Intent(requireContext(), FragmentActivity::class.java).apply {
                            putExtra("fragment", CreateAppGroupFragment.FRAGMENT_ID)
                            putExtra("prefill_package", stat.packageName)
                        })
                        1 -> startActivity(Intent(requireContext(), FragmentActivity::class.java).apply {
                            putExtra("fragment", CreateGrayscaleGroupFragment.FRAGMENT_ID)
                            putExtra("prefill_package", stat.packageName)
                        })
                        2 -> startActivity(Intent(requireContext(), FragmentActivity::class.java).apply {
                            putExtra("fragment", CreateAutoFocusGroupFragment.FRAGMENT_ID)
                            putExtra("prefill_package", stat.packageName)
                        })
                        3 -> showAddTrackingRuleDialog()
                    }
                }
                .show()
        }

        // Long-press on the view tracker stats container opens the custom rule management dialog.
        binding.viewTrackerContainer.setOnLongClickListener {
            showManageCustomTrackingRulesDialog()
            true
        }
    }

    /** Show a dialog to enter a new custom view tracking rule (ViewBlocker format). */
    private fun showAddTrackingRuleDialog() {
        val editText = EditText(requireContext()).apply {
            hint = getString(R.string.custom_rule_hint)
            // Pre-fill the pkg token so the user only needs to add the selector.
            setText("pkg:${stat.packageName} ")
            setSelection(text.length)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.view_tracker_add_rule))
            .setMessage(getString(R.string.view_tracker_add_rule_hint))
            .setView(editText)
            .setPositiveButton(getString(R.string.add)) { _, _ ->
                val rule = editText.text.toString().trim()
                if (rule.isNotEmpty()) {
                    breakdownViewModel.addCustomTrackingRule(rule)
                    Toast.makeText(requireContext(), getString(R.string.view_tracker_rule_added), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(android.R.string.cancel), null)
            .show()
    }

    /** Show a dialog listing current custom tracking rules for this app with delete option. */
    private fun showManageCustomTrackingRulesDialog() {
        val config = breakdownViewModel.viewTrackerConfig.value
        val appRules = config.customRules.filter { it.contains("pkg:${stat.packageName}") }
        if (appRules.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.view_tracker_no_custom_rules), Toast.LENGTH_SHORT).show()
            return
        }

        val labels = appRules.map { rule ->
            val commentMatch = Regex("""comment:(?:"([^"]*)"|(\S+))""").find(rule)
            commentMatch?.groupValues?.get(1)?.ifEmpty { commentMatch.groupValues[2] } ?: rule
        }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.view_tracker_custom_rules))
            .setItems(labels) { _, which ->
                val selectedRule = appRules[which]
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.view_tracker_delete_rule))
                    .setMessage(labels[which])
                    .setPositiveButton(getString(R.string.delete)) { _, _ ->
                        breakdownViewModel.removeCustomTrackingRule(selectedRule)
                    }
                    .setNegativeButton(getString(android.R.string.cancel), null)
                    .show()
            }
            .show()
    }

    private fun addShortcutCard(
        title: String,
        subtitle: String,
        isActive: Boolean,
        iconRes: Int,
        onToggle: ((Boolean) -> Unit)?,
        onClick: () -> Unit
    ) {
        val item = LayoutInflater.from(requireContext()).inflate(R.layout.item_usage_shortcut, binding.dynamicShortcutsContainer, false)
        item.findViewById<TextView>(R.id.tv_title).text = title
        item.findViewById<TextView>(R.id.tv_subtitle).text = subtitle
        
        try {
            item.findViewById<ImageView>(R.id.icon_type).setImageResource(iconRes)
        } catch (_: Exception) {}

        val switchView = item.findViewById<SwitchMaterial>(R.id.switch_active)
        if (onToggle != null) {
            switchView.visibility = View.VISIBLE
            switchView.isChecked = isActive
            switchView.setOnCheckedChangeListener { _, isChecked -> onToggle(isChecked) }
        } else {
            switchView.visibility = View.GONE
        }

        item.setOnClickListener { onClick() }
        binding.dynamicShortcutsContainer.addView(item)
    }

    private fun setupLineChart(lineChart: LineChart) {
        lineChart.apply {
            description.isEnabled = false
            legend.isEnabled = true
            setTouchEnabled(false)
            setPinchZoom(false)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                labelRotationAngle = 45f
                valueFormatter = HourAxisFormatter()
            }
            axisLeft.apply {
                valueFormatter = MinutesAxisFormatter()
                axisMinimum = 0f
            }
            axisRight.isEnabled = false
            animateX(1000)
        }
    }

    private fun plotUsageData() {
        val hourlyUsage = stat.hourlyUsage
        val entries = hourlyUsage.mapIndexed { hour, durationMs ->
            Entry(hour.toFloat(), durationMs / (1000f * 60f))
        }
        val dataSet = LineDataSet(entries, "Usage Time (minutes)")
        setupChartUI(binding.lineChart, dataSet)
    }

    private fun setupChartUI(chart: LineChart, lineDataSet: LineDataSet) {
        val primaryColor = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorPrimary, ContextCompat.getColor(requireContext(), R.color.text_color))
        lineDataSet.apply {
            color = primaryColor
            valueTextColor = primaryColor
            lineWidth = 3f
            setDrawCircles(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.2f
        }
        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            labelCount = 5
            setDrawGridLines(false)
            textColor = primaryColor
        }
        chart.axisLeft.apply {
            isEnabled = true
            setDrawGridLines(false)
            textColor = primaryColor
            valueFormatter = MinutesAxisFormatter()
            axisMinimum = 0f
        }
        chart.apply {
            axisRight.isEnabled = false
            legend.isEnabled = false
            description.isEnabled = false
            animateY(800, Easing.EaseInCubic)
            setTouchEnabled(false)
            isDragEnabled = false
            setScaleEnabled(false)
            setPinchZoom(false)
            data = LineData(lineDataSet)
        }
        chart.invalidate()
    }

    private class HourAxisFormatter : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            val hour = value.toInt()
            return String.format("%02d:00", hour)
        }
    }

    private class MinutesAxisFormatter : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            val totalMinutes = value.toInt()
            if (totalMinutes == 0) return "0m"
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return if (hours > 0) {
                if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h"
            } else {
                "${minutes}m"
            }
        }
    }

    private class MinutesValueFormatter : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            if (value == 0f) return ""
            return "${value.toInt()}m"
        }
    }
}

