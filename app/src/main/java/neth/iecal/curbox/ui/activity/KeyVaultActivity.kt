package neth.iecal.curbox.ui.activity

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import neth.iecal.curbox.blockers.KeyVault

/** Ecran du coffre a cle de l'iPhone. Captures d'ecran interdites (FLAG_SECURE). */
class KeyVaultActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var title: TextView
    private lateinit var message: TextView
    private lateinit var qr: ImageView
    private lateinit var primary: MaterialButton
    private lateinit var secondary: MaterialButton
    private var shownState: KeyVault.State? = null

    private val ticker = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val pad = (24 * resources.displayMetrics.density).toInt()
        title = TextView(this).apply { textSize = 22f; setPadding(0, 0, 0, pad / 2) }
        message = TextView(this).apply { textSize = 15f; setPadding(0, 0, 0, pad) }
        qr = ImageView(this).apply {
            setBackgroundColor(Color.WHITE)
            setPadding(pad / 2, pad / 2, pad / 2, pad / 2)
            adjustViewBounds = true
        }
        primary = MaterialButton(this)
        secondary = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad * 2, pad, pad)
            addView(title)
            addView(message)
            addView(qr, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(primary, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(secondary, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        })
    }

    override fun onResume() {
        super.onResume()
        shownState = null
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
        // La cle ne reste jamais affichee en arriere-plan.
        qr.setImageDrawable(null)
    }

    private fun render() {
        val state = KeyVault.state(this)
        val autoTime = KeyVault.isAutoTimeOn(this)
        val sameKind = shownState?.let { it::class == state::class } == true
        shownState = state

        primary.visibility = View.GONE
        secondary.visibility = View.GONE
        primary.setOnClickListener(null)
        secondary.setOnClickListener(null)

        when (state) {
            is KeyVault.State.Empty -> {
                hideQr()
                title.text = "Coffre à clé de l'iPhone"
                message.text = "Focus va créer une clé unique. Elle sera visible 30 minutes, " +
                    "le temps de la scanner dans Foqos (Physical Unlocks > QR/Barcode). " +
                    "Ensuite, il faudra la demander et attendre 24 heures pour la revoir.\n\n" +
                    "Ne la photographie pas : une copie rendrait le coffre inutile."
                primary.text = "Créer la clé"
                primary.visibility = View.VISIBLE
                primary.setOnClickListener {
                    if (!autoTime) {
                        message.text = "Active d'abord la date et l'heure automatiques dans les Réglages."
                    } else {
                        KeyVault.create(this); shownState = null; render()
                    }
                }
            }
            is KeyVault.State.Setup -> {
                title.text = "Scanne cette clé dans Foqos"
                message.text = "Mise en place : encore ${format(state.remainingMs)}.\n" +
                    "Ajoute-la dans Physical Unlocks > QR/Barcode, puis lance la session."
                if (!sameKind || qr.drawable == null) showQr()
            }
            is KeyVault.State.Locked -> {
                hideQr()
                title.text = "Clé verrouillée"
                message.text = "Pour la revoir, demande-la : elle s'affichera 24 heures plus tard, " +
                    "pendant une heure."
                primary.text = "Demander la clé"
                primary.visibility = View.VISIBLE
                primary.setOnClickListener {
                    if (!KeyVault.request(this)) {
                        message.text = "Active d'abord la date et l'heure automatiques dans les Réglages."
                    } else {
                        shownState = null; render()
                    }
                }
            }
            is KeyVault.State.Pending -> {
                hideQr()
                title.text = "Clé demandée"
                message.text = "Elle s'affichera dans ${format(state.remainingMs)}." +
                    if (!autoTime) "\n\nDate automatique désactivée : la clé ne s'affichera pas tant qu'elle l'est." else ""
                secondary.text = "Annuler la demande"
                secondary.visibility = View.VISIBLE
                secondary.setOnClickListener { KeyVault.cancelRequest(this); shownState = null; render() }
            }
            is KeyVault.State.Open -> {
                title.text = "Ta clé"
                message.text = "Visible encore ${format(state.remainingMs)}. Ensuite, elle se reverrouille."
                if (!sameKind || qr.drawable == null) showQr()
                secondary.text = "Reverrouiller maintenant"
                secondary.visibility = View.VISIBLE
                secondary.setOnClickListener { KeyVault.cancelRequest(this); shownState = null; render() }
            }
        }
    }

    private fun showQr() {
        val key = KeyVault.reveal(this)
        if (key == null) {
            hideQr()
            message.text = "${message.text}\n\nActive la date et l'heure automatiques pour afficher la clé."
            return
        }
        qr.setImageBitmap(BarcodeEncoder().encodeBitmap(key, BarcodeFormat.QR_CODE, 900, 900))
        qr.visibility = View.VISIBLE
    }

    private fun hideQr() {
        qr.setImageDrawable(null)
        qr.visibility = View.GONE
    }

    private fun format(ms: Long): String {
        val totalMin = (ms + 59_999) / 60_000
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "${h} h ${m.toString().padStart(2, '0')}" else "$m min"
    }
}
