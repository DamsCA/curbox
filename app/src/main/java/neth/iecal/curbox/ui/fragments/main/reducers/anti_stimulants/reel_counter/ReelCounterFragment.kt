package neth.iecal.curbox.ui.fragments.main.reducers.anti_stimulants.reel_counter

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import neth.iecal.curbox.data.models.ReelCounterOverlayConfig
import neth.iecal.curbox.databinding.FragmentReelCounterBinding

class ReelCounterFragment : Fragment() {

    companion object {
        const val FRAGMENT_ID = "reel_counter"

        private val PRESET_COLORS = intArrayOf(
            0x000000,
            0x1A1A2E,
            0x0D2818,
            0x2A0D1A,
            0x2A2A3A,
            0xFFFFFF
        )
    }

    private var _binding: FragmentReelCounterBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ReelCounterViewModel
    private var isUpdatingUi = false
    private var selectedColorIndex = 0
    private val colorChipViews = mutableListOf<View>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReelCounterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[ReelCounterViewModel::class.java]

        buildColorChips()
        setupListeners()
        observeViewModel()

        viewModel.initialize()
    }

    private fun buildColorChips() {
        val container = binding.reelColorChipsContainer
        val sizePx = (40 * resources.displayMetrics.density).toInt()
        val marginPx = (8 * resources.displayMetrics.density).toInt()

        PRESET_COLORS.forEachIndexed { index, color ->
            val chip = FrameLayout(requireContext()).apply {
                layoutParams = ViewGroup.MarginLayoutParams(sizePx, sizePx).apply {
                    marginEnd = marginPx
                }
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.rgb((color shr 16) and 0xFF, (color shr 8) and 0xFF, color and 0xFF))
                    setStroke((2 * resources.displayMetrics.density).toInt(), Color.TRANSPARENT)
                }
                background = bg
                setOnClickListener { selectColor(index) }
            }
            colorChipViews.add(chip)
            container.addView(chip)
        }
    }

    private fun selectColor(index: Int) {
        if (isUpdatingUi) return
        selectedColorIndex = index
        refreshChipSelection()
        val current = viewModel.overlayConfig.value
        viewModel.updateOverlayConfig(current.copy(bgColor = PRESET_COLORS[index]))
    }

    private fun refreshChipSelection() {
        colorChipViews.forEachIndexed { i, chip ->
            val bg = chip.background as? GradientDrawable ?: return@forEachIndexed
            val strokeColor = if (i == selectedColorIndex) Color.parseColor("#83D5C5") else Color.TRANSPARENT
            bg.setStroke((3 * resources.displayMetrics.density).toInt(), strokeColor)
        }
    }

    private fun setupListeners() {
        binding.switchEnableCounter.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingUi) {
                viewModel.setIsActive(isChecked)
            }
        }

        binding.btnPrevWeek.setOnClickListener {
            viewModel.goToPreviousWeek()
        }

        binding.btnNextWeek.setOnClickListener {
            viewModel.goToNextWeek()
        }

        binding.weeklyBarGraph.setOnDaySelectedListener { dayData ->
            val index = viewModel.weeklyData.value?.indexOf(dayData) ?: return@setOnDaySelectedListener
            if (index != -1) viewModel.selectDay(index)
        }

        binding.sliderReelTextSize.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            binding.tvReelTextSizeLabel.text = getString(neth.iecal.curbox.R.string.text_size_value, value.toInt())
            val current = viewModel.overlayConfig.value
            viewModel.updateOverlayConfig(current.copy(textSize = value))
        }

        binding.sliderReelOpacity.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            binding.tvReelOpacityLabel.text = getString(neth.iecal.curbox.R.string.opacity_value, value.toInt())
            val current = viewModel.overlayConfig.value
            viewModel.updateOverlayConfig(current.copy(bgOpacity = value.toInt()))
        }

        binding.reelPositionPicker.onPositionChanged = { x, y ->
            val current = viewModel.overlayConfig.value
            viewModel.updateOverlayConfig(current.copy(positionX = x, positionY = y))
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.settings.collectLatest { settings ->
                isUpdatingUi = true
                if (binding.switchEnableCounter.isChecked != settings.isReelCounterOn) {
                    binding.switchEnableCounter.isChecked = settings.isReelCounterOn
                }
                isUpdatingUi = false
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.overlayConfig.collectLatest { config ->
                isUpdatingUi = true

                if (binding.sliderReelTextSize.value != config.textSize) {
                    binding.sliderReelTextSize.value = config.textSize.coerceIn(24f, 120f)
                }
                binding.tvReelTextSizeLabel.text = getString(neth.iecal.curbox.R.string.text_size_value, config.textSize.toInt())

                if (binding.sliderReelOpacity.value != config.bgOpacity.toFloat()) {
                    binding.sliderReelOpacity.value = config.bgOpacity.toFloat().coerceIn(0f, 100f)
                }
                binding.tvReelOpacityLabel.text = getString(neth.iecal.curbox.R.string.opacity_value, config.bgOpacity)

                val colorIdx = PRESET_COLORS.indexOfFirst { it == config.bgColor }.takeIf { it >= 0 } ?: 0
                if (selectedColorIndex != colorIdx) {
                    selectedColorIndex = colorIdx
                    refreshChipSelection()
                }

                binding.reelPositionPicker.setPosition(config.positionX, config.positionY)

                isUpdatingUi = false
            }
        }

        viewModel.weeklyData.observe(viewLifecycleOwner) { data ->
            val selectedIdx = viewModel.selectedDayIndex.value ?: 6
            binding.weeklyBarGraph.setData(data, selectedIdx)
        }

        viewModel.selectedDayIndex.observe(viewLifecycleOwner) { index ->
            binding.weeklyBarGraph.setSelectedIndex(index)
        }

        viewModel.selectedDayTotal.observe(viewLifecycleOwner) { count ->
            binding.totalReelsCount.text = count.toString()
        }

        viewModel.dateSublabel.observe(viewLifecycleOwner) { label ->
            binding.dateSublabel.text = label
        }

        viewModel.weekRangeLabel.observe(viewLifecycleOwner) { label ->
            binding.tvWeekRange.text = label
        }

        viewModel.canGoNext.observe(viewLifecycleOwner) { canGo ->
            binding.btnNextWeek.alpha = if (canGo) 1f else 0.3f
            binding.btnNextWeek.isEnabled = canGo
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
