package com.yaadein.app

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Reads the merged manifest Robolectric loads and asserts the zero-network contract. */
@RunWith(RobolectricTestRunner::class)
class ManifestPermissionsTest {
    private val forbidden = listOf(
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.MANAGE_EXTERNAL_STORAGE",
        "android.permission.READ_CONTACTS",
        "android.permission.READ_SMS",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
    )

    private fun requestedPermissions(): List<String> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        return info.requestedPermissions?.toList().orEmpty()
    }

    @Test
    fun `merged manifest never requests network, storage, contacts, sms or location`() {
        val requested = requestedPermissions()
        forbidden.forEach { assertFalse("forbidden permission present: $it", it in requested) }
    }

    @Test
    fun `biometric and notification permissions are present`() {
        val requested = requestedPermissions()
        assertTrue("android.permission.USE_BIOMETRIC" in requested)
        assertTrue("android.permission.POST_NOTIFICATIONS" in requested)
    }

    @Test
    fun `install permission exists only in sideloaded debug builds`() {
        // Settings → Update app (debug only). Play-distributed release builds must not update themselves.
        assertEquals(BuildConfig.DEBUG, "android.permission.REQUEST_INSTALL_PACKAGES" in requestedPermissions())
    }
}
