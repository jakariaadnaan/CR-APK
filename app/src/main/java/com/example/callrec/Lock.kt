package com.example.callrec

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** App lock state. The PIN is never stored, only a salted PBKDF2 hash. */
object Lock {
    @Volatile
    var unlocked = false

    @Volatile
    var lastActive = 0L

    @Volatile
    var showing = false

    private fun sp(c: Context) = c.getSharedPreferences("lock", Context.MODE_PRIVATE)

    fun enabled(c: Context): Boolean = sp(c).getBoolean("on", false) && sp(c).getString("hash", null) != null
    /** "pin" or "pattern". */
    fun type(c: Context): String = sp(c).getString("type", "pin") ?: "pin"
    fun pinLength(c: Context): Int = sp(c).getInt("len", 4)
    fun bioEnabled(c: Context): Boolean = sp(c).getBoolean("bio", false)
    fun graceMs(c: Context): Long = sp(c).getLong("grace", 30_000L)

    fun setBio(c: Context, on: Boolean) {
        sp(c).edit().putBoolean("bio", on).apply()
    }

    fun setGrace(c: Context, ms: Long) {
        sp(c).edit().putLong("grace", ms).apply()
    }

    private fun hash(pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt.toByteArray(), 60000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return bytes.joinToString("") { String.format("%02x", it) }
    }

    /** [secret] is the PIN digits, or the pattern as a digit string of dot numbers (0..8). */
    fun setSecret(c: Context, type: String, secret: String) {
        val raw = ByteArray(16)
        SecureRandom().nextBytes(raw)
        val salt = raw.joinToString("") { String.format("%02x", it) }
        sp(c).edit()
            .putString("salt", salt)
            .putString("hash", hash(secret, salt))
            .putString("type", type)
            .putInt("len", secret.length)
            .putBoolean("on", true)
            .apply()
        markUnlocked()
    }

    fun setPin(c: Context, pin: String) = setSecret(c, "pin", pin)

    fun check(c: Context, pin: String): Boolean {
        val salt = sp(c).getString("salt", null) ?: return false
        val stored = sp(c).getString("hash", null) ?: return false
        return hash(pin, salt) == stored
    }

    fun disable(c: Context) {
        sp(c).edit().remove("on").remove("hash").remove("salt").remove("bio").remove("type").apply()
    }

    fun markUnlocked() {
        unlocked = true
        lastActive = System.currentTimeMillis()
    }

    fun needsUnlock(c: Context): Boolean {
        if (!enabled(c)) return false
        if (!unlocked) return true
        return System.currentTimeMillis() - lastActive > graceMs(c)
    }

    fun biometricAvailable(c: Context): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val bm = c.getSystemService(BiometricManager::class.java) ?: return false
        return bm.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
    }
}

/** Base class for every screen: asks for the lock when needed and hides the recents preview. */
open class GuardedActivity : Activity() {
    private var stamp = 0

    override fun attachBaseContext(newBase: Context) {
        val fs = newBase.getSharedPreferences("settings", Context.MODE_PRIVATE).getFloat("fontScale", 1f)
        if (fs == 1f) {
            super.attachBaseContext(newBase)
        } else {
            val c = android.content.res.Configuration(newBase.resources.configuration)
            c.fontScale = newBase.resources.configuration.fontScale * fs
            super.attachBaseContext(newBase.createConfigurationContext(c))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Ui.apply(this)
        stamp = Ui.stamp
        setTheme(if (Ui.dark) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
        super.onCreate(savedInstanceState)
        if (Lock.enabled(this)) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onResume() {
        super.onResume()
        if (stamp != Ui.stamp) {
            recreate()
            return
        }
        if (Lock.needsUnlock(this) && !Lock.showing) {
            startActivity(Intent(this, LockActivity::class.java))
        }
    }

    override fun onPause() {
        super.onPause()
        Lock.lastActive = System.currentTimeMillis()
    }
}
