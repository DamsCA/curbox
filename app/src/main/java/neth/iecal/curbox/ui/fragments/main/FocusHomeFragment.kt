package neth.iecal.curbox.ui.fragments.main

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import neth.iecal.curbox.R

class FocusHomeFragment : Fragment() {

    companion object {
        const val FRAGMENT_ID = "focus_home"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_focus_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<MaterialButton>(R.id.btn_enable).setOnClickListener {
            runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val v = view ?: return
        val ctx = context ?: return
        val active = isProtectionActive()
        val statusText = v.findViewById<TextView>(R.id.status_text)
        val statusSub = v.findViewById<TextView>(R.id.status_sub)
        val btn = v.findViewById<MaterialButton>(R.id.btn_enable)

        if (active) {
            statusText.text = "Le porno est bloqué"
            statusSub.text = "Protection active"
            statusSub.setTextColor(ContextCompat.getColor(ctx, android.R.color.holo_green_dark))
            btn.visibility = View.GONE
        } else {
            statusText.text = "Protection désactivée"
            statusSub.text = "Active les services d'accessibilité pour bloquer le porno"
            statusSub.setTextColor(ContextCompat.getColor(ctx, android.R.color.holo_red_dark))
            btn.visibility = View.VISIBLE
        }
    }

    private fun isProtectionActive(): Boolean {
        val ctx = context ?: return false
        val enabled = Settings.Secure.getString(
            ctx.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains("neth.iecal.curbox.services.AppBlockerService")
    }
}
