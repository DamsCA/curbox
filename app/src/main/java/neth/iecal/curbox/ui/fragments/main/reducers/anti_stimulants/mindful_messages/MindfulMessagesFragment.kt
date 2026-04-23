package neth.iecal.curbox.ui.fragments.main.reducers.anti_stimulants.mindful_messages

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import neth.iecal.curbox.databinding.FragmentMindfulMessagesBinding
import neth.iecal.curbox.ui.activity.SelectAppsActivity

class MindfulMessagesFragment : Fragment() {

    companion object {
        const val FRAGMENT_ID = "MINDFUL_MESSAGES"

        private val PRESET_COLORS = intArrayOf(
            0x000000,
            0x1A1A2E,
            0x0D2818,
            0x2A0D1A,
            0x2A2A3A,
            0xFFFFFF
        )
    }

    private var _binding: FragmentMindfulMessagesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MindfulMessagesViewModel by viewModels()
    private var selectedApps = arrayListOf<String>()
    private var isUpdatingFromViewModel = false
    private var selectedColorIndex = 0
    private val colorChipViews = mutableListOf<View>()

    private val selectAppsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            val apps = result.data?.getStringArrayListExtra("SELECTED_APPS")
            if (apps != null) {
                selectedApps = apps
                updateAppsButtonText()
                viewModel.updateSelectedApps(apps.toList())
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMindfulMessagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().finish()
        }

        buildColorChips()
        setupUI()
        observeViewModel()
    }

    private fun buildColorChips() {
        val container = binding.colorChipsContainer
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
        if (isUpdatingFromViewModel) return
        selectedColorIndex = index
        refreshChipSelection()
        viewModel.updateBgColor(PRESET_COLORS[index])
    }

    private fun refreshChipSelection() {
        colorChipViews.forEachIndexed { i, chip ->
            val bg = chip.background as? GradientDrawable ?: return@forEachIndexed
            val strokeColor = if (i == selectedColorIndex) Color.parseColor("#83D5C5") else Color.TRANSPARENT
            bg.setStroke((3 * resources.displayMetrics.density).toInt(), strokeColor)
        }
    }

    private fun setupUI() {
        binding.switchIsActive.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingFromViewModel) return@setOnCheckedChangeListener
            viewModel.updateIsActive(isChecked)
        }

        binding.btnSelectApps.setOnClickListener {
            val intent = Intent(requireContext(), SelectAppsActivity::class.java)
            intent.putStringArrayListExtra("PRE_SELECTED_APPS", selectedApps)
            selectAppsLauncher.launch(intent)
        }

        binding.etMessages.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isUpdatingFromViewModel) return
                viewModel.updateMessages(s?.toString() ?: "")
            }
        })

        binding.sliderTextSize.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            binding.tvTextSizeLabel.text = getString(neth.iecal.curbox.R.string.text_size_value, value.toInt())
            viewModel.updateTextSize(value)
        }

        binding.sliderOpacity.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            binding.tvOpacityLabel.text = getString(neth.iecal.curbox.R.string.opacity_value, value.toInt())
            viewModel.updateBgOpacity(value.toInt())
        }

        binding.positionPicker.onPositionChanged = { x, y ->
            viewModel.updatePosition(x, y)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.configState.collect { config ->
                    isUpdatingFromViewModel = true

                    if (binding.switchIsActive.isChecked != config.isActive) {
                        binding.switchIsActive.isChecked = config.isActive
                    }

                    if (selectedApps.toList() != config.selectedApps) {
                        selectedApps = ArrayList(config.selectedApps)
                        updateAppsButtonText()
                    }

                    val messagesText = config.messages
                    if (binding.etMessages.text.toString() != messagesText) {
                        val cursor = binding.etMessages.selectionStart
                        binding.etMessages.setText(messagesText)
                        if (cursor >= 0 && cursor <= (binding.etMessages.text?.length ?: 0)) {
                            binding.etMessages.setSelection(cursor)
                        }
                    }

                    if (binding.sliderTextSize.value != config.textSize) {
                        binding.sliderTextSize.value = config.textSize.coerceIn(8f, 28f)
                    }
                    binding.tvTextSizeLabel.text = getString(neth.iecal.curbox.R.string.text_size_value, config.textSize.toInt())

                    if (binding.sliderOpacity.value != config.bgOpacity.toFloat()) {
                        binding.sliderOpacity.value = config.bgOpacity.toFloat().coerceIn(0f, 100f)
                    }
                    binding.tvOpacityLabel.text = getString(neth.iecal.curbox.R.string.opacity_value, config.bgOpacity)

                    val colorIdx = PRESET_COLORS.indexOfFirst { it == config.bgColor }.takeIf { it >= 0 } ?: 0
                    if (selectedColorIndex != colorIdx) {
                        selectedColorIndex = colorIdx
                        refreshChipSelection()
                    }

                    binding.positionPicker.setPosition(config.positionX, config.positionY)

                    isUpdatingFromViewModel = false
                }
            }
        }
    }

    private fun updateAppsButtonText() {
        binding.btnSelectApps.text = "Select Apps (${selectedApps.size})"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
