package neth.iecal.curbox.blockers

import android.content.Context
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Coffre a cle : garde la cle QR qui deverrouille l'iPhone (Foqos, "Physical Unlocks").
 * La cle n'est visible que pendant la mise en place, puis seulement 24 h apres une demande.
 *
 * - 64 caracteres : Foqos affiche en clair toute cle de 28 caracteres ou moins.
 * - Chiffree par l'Android Keystore : une sauvegarde adb ou un run-as ne donne que du charabia.
 * - Date automatique obligatoire : avancer l'horloge a la main ne raccourcit pas l'attente.
 */
object KeyVault {
    const val REVEAL_DELAY_MS = 24 * 60 * 60 * 1000L
    const val REVEAL_WINDOW_MS = 60 * 60 * 1000L
    const val SETUP_WINDOW_MS = 30 * 60 * 1000L
    private const val KEY_LENGTH = 64
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
    private const val KEYSTORE_ALIAS = "focus_key_vault"
    private const val PREFS = "key_vault"

    sealed class State {
        object Empty : State()
        data class Setup(val remainingMs: Long) : State()
        object Locked : State()
        data class Pending(val remainingMs: Long) : State()
        data class Open(val remainingMs: Long) : State()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isAutoTimeOn(ctx: Context): Boolean =
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.AUTO_TIME, 0) == 1

    fun state(ctx: Context, now: Long = System.currentTimeMillis()): State {
        val p = prefs(ctx)
        if (!p.contains("cipher")) return State.Empty
        val createdAt = p.getLong("created_at", 0L)
        if (now in createdAt until createdAt + SETUP_WINDOW_MS) {
            return State.Setup(createdAt + SETUP_WINDOW_MS - now)
        }
        val requestedAt = p.getLong("requested_at", 0L)
        if (requestedAt == 0L) return State.Locked
        val openAt = requestedAt + REVEAL_DELAY_MS
        return when {
            now < requestedAt -> {
                // Horloge revenue avant la demande : on repart de zero.
                cancelRequest(ctx); State.Locked
            }
            now < openAt -> State.Pending(openAt - now)
            now < openAt + REVEAL_WINDOW_MS -> State.Open(openAt + REVEAL_WINDOW_MS - now)
            else -> {
                cancelRequest(ctx); State.Locked
            }
        }
    }

    /** Cree la cle une seule fois. Elle ne peut jamais etre remplacee. */
    fun create(ctx: Context): Boolean {
        if (prefs(ctx).contains("cipher")) return false
        val random = SecureRandom()
        val key = buildString(KEY_LENGTH) {
            repeat(KEY_LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val encrypted = cipher.doFinal(key.toByteArray(Charsets.UTF_8))
        prefs(ctx).edit()
            .putString("cipher", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putLong("created_at", System.currentTimeMillis())
            .commit()
        return true
    }

    fun request(ctx: Context): Boolean {
        if (!isAutoTimeOn(ctx) || state(ctx) != State.Locked) return false
        prefs(ctx).edit().putLong("requested_at", System.currentTimeMillis()).commit()
        return true
    }

    fun cancelRequest(ctx: Context) {
        prefs(ctx).edit().remove("requested_at").commit()
    }

    /** Rend la cle en clair, uniquement pendant la mise en place ou une fenetre d'ouverture. */
    fun reveal(ctx: Context): String? {
        val s = state(ctx)
        if (s !is State.Setup && s !is State.Open) return null
        // Sans date automatique, reculer l'horloge rouvrirait la mise en place.
        if (!isAutoTimeOn(ctx)) return null
        val p = prefs(ctx)
        return runCatching {
            val encrypted = Base64.decode(p.getString("cipher", null), Base64.NO_WRAP)
            val iv = Base64.decode(p.getString("iv", null), Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}
