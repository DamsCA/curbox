package neth.iecal.curbox.ui.fragments.main.reducers.anti_stimulants.mindful_messages

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import neth.iecal.curbox.R
import neth.iecal.curbox.data.models.MindfulMessageConfig
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
    private var positionScrim: View? = null

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
            binding.tvTextSizeLabel.text = getString(R.string.text_size_value, value.toInt())
            viewModel.updateTextSize(value)
        }

        binding.sliderOpacity.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            binding.tvOpacityLabel.text = getString(R.string.opacity_value, value.toInt())
            viewModel.updateBgOpacity(value.toInt())
        }

        binding.btnSetPosition.setOnClickListener {
            showPositionDragOverlay()
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
                    binding.tvTextSizeLabel.text = getString(R.string.text_size_value, config.textSize.toInt())

                    if (binding.sliderOpacity.value != config.bgOpacity.toFloat()) {
                        binding.sliderOpacity.value = config.bgOpacity.toFloat().coerceIn(0f, 100f)
                    }
                    binding.tvOpacityLabel.text = getString(R.string.opacity_value, config.bgOpacity)

                    val colorIdx = PRESET_COLORS.indexOfFirst { it == config.bgColor }.takeIf { it >= 0 } ?: 0
                    if (selectedColorIndex != colorIdx) {
                        selectedColorIndex = colorIdx
                        refreshChipSelection()
                    }

                    isUpdatingFromViewModel = false
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showPositionDragOverlay() {
        if (positionScrim != null) return
        val config = viewModel.configState.value
        val decorView = requireActivity().window.decorView as FrameLayout
        val dm = resources.displayMetrics

        val scrim = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.argb(180, 0, 0, 0))
        }

        val hint = TextView(requireContext()).apply {
            text = getString(R.string.position_hint)
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(32, 0, 32, 0)
        }
        scrim.addView(hint, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).also {
            it.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            it.topMargin = (56 * dm.density).toInt()
        })

        val widget = LayoutInflater.from(requireContext())
            .inflate(R.layout.mindfulmsg_overlay, scrim, false)

        val r = (config.bgColor shr 16) and 0xFF
        val g = (config.bgColor shr 8) and 0xFF
        val b = config.bgColor and 0xFF
        val alpha = config.bgOpacity * 255 / 100
        widget.findViewById<TextView>(R.id.mindful_txt).apply {
            text = config.messages.lines().firstOrNull()?.ifBlank { "Mindful message" } ?: "Mindful message"
            textSize = config.textSize
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(alpha, r, g, b))
            setPadding(32, 32, 32, 32)
        }

        scrim.addView(widget, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ))

        widget.post {
            widget.x = (dm.widthPixels * config.positionX - widget.width / 2f)
                .coerceIn(0f, (dm.widthPixels - widget.width).toFloat().coerceAtLeast(0f))
            widget.y = (dm.heightPixels * config.positionY - widget.height / 2f)
                .coerceIn(0f, (dm.heightPixels - widget.height).toFloat().coerceAtLeast(0f))
        }

        var downOffsetX = 0f
        var downOffsetY = 0f
        widget.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downOffsetX = event.rawX - v.x
                    downOffsetY = event.rawY - v.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    v.x = (event.rawX - downOffsetX)
                        .coerceIn(0f, (dm.widthPixels - v.width).toFloat().coerceAtLeast(0f))
                    v.y = (event.rawY - downOffsetY)
                        .coerceIn(0f, (dm.heightPixels - v.height).toFloat().coerceAtLeast(0f))
                    true
                }
                else -> false
            }
        }

        val okBtn = MaterialButton(requireContext()).apply {
            text = getString(android.R.string.ok)
            setOnClickListener {
                val posX = ((widget.x + widget.width / 2f) / dm.widthPixels).coerceIn(0f, 1f)
                val posY = ((widget.y + widget.height / 2f) / dm.heightPixels).coerceIn(0f, 1f)
                viewModel.updatePosition(posX, posY)
                decorView.removeView(scrim)
                positionScrim = null
            }
        }
        scrim.addView(okBtn, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).also {
            it.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            it.bottomMargin = (32 * dm.density).toInt()
        })

        decorView.addView(scrim)
        positionScrim = scrim
    }

    private fun updateAppsButtonText() {
        binding.btnSelectApps.text = "Select Apps (${selectedApps.size})"
    }

    override fun onDestroyView() {
        positionScrim?.let {
            (activity?.window?.decorView as? FrameLayout)?.removeView(it)
            positionScrim = null
        }
        super.onDestroyView()
        _binding = null
    }
}
