package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object PortalCrypto {
    const val MIN_PASSWORD_LENGTH = 6
    private const val HMAC_ALGO = "HmacSHA256"
    private const val ITERATIONS = 60_000
    private const val SALT_PREFIX = "clientdesk-portal-v1:"
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val PORTAL_KEY_REGEX = Regex("^[0-9a-f]{64}$")

    fun normalizeEmail(email: String): String = email.trim().lowercase()

    fun isValidEmail(email: String): Boolean {
        val normalized = normalizeEmail(email)
        return normalized.length in 5..120 && EMAIL_REGEX.matches(normalized)
    }

    fun isValidPortalKey(key: String): Boolean = PORTAL_KEY_REGEX.matches(key)

    /**
     * Pure-Kotlin RFC 2898 PBKDF2-HMAC-SHA256 deriving a 256-bit (64 lowercase hex)
     * portal capability key. Works identically on Android API 24 through API 36+.
     */
    suspend fun derivePortalKey(email: String, password: String): String = withContext(Dispatchers.Default) {
        val normalizedEmail = normalizeEmail(email)
        val passwordBytes = password.toByteArray(StandardCharsets.UTF_8)
        val saltBytes = (SALT_PREFIX + normalizedEmail).toByteArray(StandardCharsets.UTF_8)

        val mac = Mac.getInstance(HMAC_ALGO)
        mac.init(SecretKeySpec(passwordBytes, HMAC_ALGO))

        // Block 1 (32 bytes = SHA-256 output length, matches desired 256-bit key length)
        val saltWithBlock = ByteArray(saltBytes.size + 4)
        System.arraycopy(saltBytes, 0, saltWithBlock, 0, saltBytes.size)
        saltWithBlock[saltBytes.size] = 0
        saltWithBlock[saltBytes.size + 1] = 0
        saltWithBlock[saltBytes.size + 2] = 0
        saltWithBlock[saltBytes.size + 3] = 1

        var u = mac.doFinal(saltWithBlock)
        val result = u.copyOf()

        for (i in 2..ITERATIONS) {
            u = mac.doFinal(u)
            for (j in result.indices) {
                result[j] = (result[j].toInt() xor u[j].toInt()).toByte()
            }
        }

        val hexChars = CharArray(result.size * 2)
        val digits = "0123456789abcdef"
        for (i in result.indices) {
            val v = result[i].toInt() and 0xFF
            hexChars[i * 2] = digits[v ushr 4]
            hexChars[i * 2 + 1] = digits[v and 0x0F]
        }
        String(hexChars)
    }
}

object PortalSessionStore {
    private const val PREFS_NAME = "clientdesk_portal_session"
    private const val KEY_PORTAL_KEY = "portal_key"
    private const val KEY_OWNER_ID = "owner_id"
    private const val KEY_CLIENT_ID = "client_id"
    private const val KEY_CLIENT_NAME = "client_name"
    private const val KEY_CLIENT_COMPANY = "client_company"
    private const val KEY_CLIENT_COLOR_HEX = "client_color_hex"
    private const val KEY_PORTAL_EMAIL = "portal_email"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveSession(context: Context, session: ClientPortalSession) {
        prefs(context).edit()
            .putString(KEY_PORTAL_KEY, session.portalKey)
            .putString(KEY_OWNER_ID, session.ownerId)
            .putString(KEY_CLIENT_ID, session.clientId)
            .putString(KEY_CLIENT_NAME, session.clientName)
            .putString(KEY_CLIENT_COMPANY, session.clientCompany)
            .putString(KEY_CLIENT_COLOR_HEX, session.clientColorHex)
            .putString(KEY_PORTAL_EMAIL, session.portalEmail)
            .apply()
    }

    fun loadSession(context: Context): ClientPortalSession? {
        val p = prefs(context)
        val key = p.getString(KEY_PORTAL_KEY, null).orEmpty()
        val ownerId = p.getString(KEY_OWNER_ID, null).orEmpty()
        val clientId = p.getString(KEY_CLIENT_ID, null).orEmpty()
        if (!PortalCrypto.isValidPortalKey(key) || ownerId.isBlank() || clientId.isBlank()) {
            return null
        }
        return ClientPortalSession(
            portalKey = key,
            ownerId = ownerId,
            clientId = clientId,
            clientName = p.getString(KEY_CLIENT_NAME, "Client").orEmpty().ifBlank { "Client" },
            clientCompany = p.getString(KEY_CLIENT_COMPANY, "").orEmpty(),
            clientColorHex = p.getString(KEY_CLIENT_COLOR_HEX, "#2555E6").orEmpty().ifBlank { "#2555E6" },
            portalEmail = p.getString(KEY_PORTAL_EMAIL, "").orEmpty()
        )
    }

    fun clearSession(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
