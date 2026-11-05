package com.yaadein.app.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.yaadein.feature.lock.BiometricLauncher
import com.yaadein.feature.lock.R as LockR

/** Class-3 (strong) biometrics only; PIN remains the fallback inside the app. */
object BiometricHelper {
    fun isAvailable(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    fun launcher(activity: FragmentActivity): BiometricLauncher? {
        if (!isAvailable(activity)) return null
        return BiometricLauncher { onSuccess, onError ->
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onError(errString.toString())
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(LockR.string.lock_biometric_title))
                .setSubtitle(activity.getString(LockR.string.lock_biometric_subtitle))
                .setNegativeButtonText(activity.getString(LockR.string.lock_biometric_negative))
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                .setConfirmationRequired(false)
                .build()
            prompt.authenticate(info)
        }
    }
}
