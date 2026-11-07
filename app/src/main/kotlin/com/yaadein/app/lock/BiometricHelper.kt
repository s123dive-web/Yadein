package com.yaadein.app.lock

import android.app.KeyguardManager
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.yaadein.data.crypto.BiometricKeys
import com.yaadein.data.lock.VaultSession
import com.yaadein.feature.lock.BiometricError
import com.yaadein.feature.lock.BiometricLauncher
import com.yaadein.feature.lock.DeviceLockLauncher
import com.yaadein.feature.lock.R as LockR

/**
 * Class-3 (strong) biometrics bound to the vault: unlocking decrypts the fingerprint copy of the vault keys with a
 * Keystore key that only works right after BiometricPrompt succeeds (CryptoObject). The PIN remains the fallback.
 */
object BiometricHelper {
    fun isAvailable(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    fun launcher(activity: FragmentActivity, session: VaultSession, keys: BiometricKeys): BiometricLauncher? {
        if (!isAvailable(activity)) return null
        return object : BiometricLauncher {
            override fun unlock(onSuccess: () -> Unit, onError: (BiometricError) -> Unit) {
                val crypto = if (session.isOpen) {
                    null // keys already in memory (app re-locked after a timeout): plain authentication is enough
                } else {
                    val iv = session.biometricIv() ?: return onError(BiometricError.UNAVAILABLE)
                    try {
                        BiometricPrompt.CryptoObject(keys.decryptCipher(iv))
                    } catch (_: Exception) {
                        // New fingerprint enrolled (Android destroyed the key) or the key is unusable: PIN only.
                        session.clearBiometricCopy()
                        keys.delete()
                        return onError(BiometricError.CHANGED)
                    }
                }
                prompt(activity, LockR.string.lock_biometric_title, LockR.string.lock_biometric_subtitle, LockR.string.lock_biometric_negative, crypto,
                    onSuccess = { result ->
                        if (session.unlockWithBiometric(result.cryptoObject?.cipher)) onSuccess() else onError(BiometricError.FAILED)
                    },
                    onError = onError,
                )
            }

            override fun enroll(onSuccess: () -> Unit, onError: (BiometricError) -> Unit) {
                val cipher = try {
                    keys.encryptCipher()
                } catch (_: Exception) {
                    return onError(BiometricError.UNAVAILABLE)
                }
                prompt(activity, LockR.string.lock_biometric_enroll_title, LockR.string.lock_biometric_enroll_subtitle, LockR.string.lock_biometric_cancel,
                    BiometricPrompt.CryptoObject(cipher),
                    onSuccess = { result ->
                        val authed = result.cryptoObject?.cipher
                        if (authed == null) {
                            onError(BiometricError.FAILED)
                        } else {
                            runCatching { session.storeBiometricCopy(authed) }.fold({ onSuccess() }, { onError(BiometricError.FAILED) })
                        }
                    },
                    onError = onError,
                )
            }
        }
    }

    /** The phone's own screen lock. Android 11+ only (BIOMETRIC_STRONG | DEVICE_CREDENTIAL), and only when one is set. */
    fun deviceLauncher(activity: FragmentActivity): DeviceLockLauncher? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val keyguard = activity.getSystemService(KeyguardManager::class.java)
        if (keyguard?.isDeviceSecure != true) return null
        return DeviceLockLauncher { onSuccess, onError ->
            val stillSecure = activity.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
            if (!stillSecure) return@DeviceLockLauncher onError(BiometricError.UNAVAILABLE)
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onError(errorOf(errorCode))
            })
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(LockR.string.lock_biometric_title))
                .setSubtitle(activity.getString(LockR.string.lock_device_subtitle))
                .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                .build()
            prompt.authenticate(info)
        }
    }

    private fun prompt(
        activity: FragmentActivity,
        title: Int,
        subtitle: Int,
        negative: Int,
        crypto: BiometricPrompt.CryptoObject?,
        onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
        onError: (BiometricError) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess(result)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onError(errorOf(errorCode))
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(title))
            .setSubtitle(activity.getString(subtitle))
            .setNegativeButtonText(activity.getString(negative))
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()
        if (crypto != null) prompt.authenticate(info, crypto) else prompt.authenticate(info)
    }

    private fun errorOf(code: Int): BiometricError = when (code) {
        BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON, BiometricPrompt.ERROR_CANCELED -> BiometricError.CANCELLED
        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL, BiometricPrompt.ERROR_HW_NOT_PRESENT, BiometricPrompt.ERROR_HW_UNAVAILABLE,
        BiometricPrompt.ERROR_NO_BIOMETRICS -> BiometricError.UNAVAILABLE
        else -> BiometricError.FAILED
    }
}
