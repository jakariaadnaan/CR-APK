package com.example.callrec

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Password protection for backup files: PBKDF2-HMAC-SHA256 key, AES-256-GCM. */
object BackupCrypto {
    private const val ITER = 120_000

    private fun key(pass: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(pass, salt, ITER, 256)
        return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
    }

    fun isEncrypted(o: JSONObject): Boolean = o.optInt("enc", 0) == 1 && o.has("ct")

    fun encrypt(plain: String, pass: CharArray): String {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(pass, salt), GCMParameterSpec(128, iv))
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        val o = JSONObject()
        o.put("app", "amar-khata")
        o.put("enc", 1)
        o.put("iter", ITER)
        o.put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
        o.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
        o.put("ct", Base64.encodeToString(ct, Base64.NO_WRAP))
        return o.toString(2)
    }

    /** Returns the original text, or null when the password is wrong or the file is damaged. */
    fun decrypt(o: JSONObject, pass: CharArray): String? = try {
        val salt = Base64.decode(o.getString("salt"), Base64.NO_WRAP)
        val iv = Base64.decode(o.getString("iv"), Base64.NO_WRAP)
        val ct = Base64.decode(o.getString("ct"), Base64.NO_WRAP)
        val spec = PBEKeySpec(pass, salt, o.optInt("iter", ITER), 256)
        val k = SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, iv))
        String(c.doFinal(ct), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }
}
