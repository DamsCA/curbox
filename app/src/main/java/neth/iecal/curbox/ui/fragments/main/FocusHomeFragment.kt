package neth.iecal.curbox.ui.fragments.main

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import neth.iecal.curbox.R
import neth.iecal.curbox.blockers.FocusLock
import neth.iecal.curbox.receivers.AdminReceiver

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
        view.findViewById<MaterialButton>(R.id.btn_lock_7).setOnClickListener { confirmLock(7) }
        view.findViewById<MaterialButton>(R.id.btn_lock_30).setOnClickListener { confirmLock(30) }
        view.findViewById<MaterialButton>(R.id.btn_lock_test).setOnClickListener { confirmTestLock() }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun confirmLock(days: Int) {
        val ctx = context ?: return
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Verrouiller $days jours ?")
            .setMessage("Pendant $days jours tu ne pourras PLUS désactiver Focus ni le désinstaller. C'est impossible à annuler. Tu es sûr ?")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Verrouiller") { _, _ -> activateLock(days) }
            .show()
    }

    private fun activateLock(days: Int) {
        val ctx = context ?: return
        runCatching {
            val admin = ComponentName(ctx, AdminReceiver::class.java)
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (!dpm.isAdminActive(admin)) {
                startActivity(
                    Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                        .putExtra(
                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "Empêche la désinstallation de Focus pendant le verrou."
                        )
                )
            }
        }
        FocusLock.lockForDays(ctx, days)
        updateStatus()
    }

    private fun confirmTestLock() {
        val ctx = context ?: return
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Tester le verrou (1 heure)")
            .setMessage("Verrouille Focus pendant 1 heure pour vérifier que la désactivation et la désinstallation sont bien bloquées. Dure 1h, non annulable.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Tester") { _, _ ->
                requestAdmin()
                FocusLock.lockForMinutes(ctx, 60)
                updateStatus()
            }
            .show()
    }

    private fun requestAdmin() {
        val ctx = context ?: return
        runCatching {
            val admin = ComponentName(ctx, AdminReceiver::class.java)
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (!dpm.isAdminActive(admin)) {
                startActivity(
                    Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                        .putExtra(
                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "Empêche la désinstallation de Focus pendant le verrou."
                        )
                )
            }
        }
    }

    private fun updateStatus() {
        val v = view ?: return
        val ctx = context ?: return
        val active = isProtectionActive()
        val statusText = v.findViewById<TextView>(R.id.status_text)
        val statusSub = v.findViewById<TextView>(R.id.status_sub)
        val btnEnable = v.findViewById<MaterialButton>(R.id.btn_enable)
        val lockInfo = v.findViewById<TextView>(R.id.lock_info)
        val btn7 = v.findViewById<MaterialButton>(R.id.btn_lock_7)
        val btn30 = v.findViewById<MaterialButton>(R.id.btn_lock_30)

        if (active) {
            statusText.text = "Le porno est bloqué"
            statusSub.text = "Protection active"
            statusSub.setTextColor(ContextCompat.getColor(ctx, android.R.color.holo_green_dark))
            btnEnable.visibility = View.GONE
        } else {
            statusText.text = "Protection désactivée"
            statusSub.text = "Active les services d'accessibilité pour bloquer le porno"
            statusSub.setTextColor(ContextCompat.getColor(ctx, android.R.color.holo_red_dark))
            btnEnable.visibility = View.VISIBLE
        }

        val until = FocusLock.lockedUntil(ctx)
        if (until > System.currentTimeMillis()) {
            val daysLeft = ((until - System.currentTimeMillis()) / (24L * 60L * 60L * 1000L) + 1L).toInt()
            lockInfo.text = "🔒 Verrouillé encore $daysLeft jour(s)\nImpossible de désactiver ou désinstaller Focus."
            lockInfo.visibility = View.VISIBLE
            btn7.visibility = View.GONE
            btn30.visibility = View.GONE
        } else {
            lockInfo.visibility = View.GONE
            btn7.visibility = View.VISIBLE
            btn30.visibility = View.VISIBLE
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
