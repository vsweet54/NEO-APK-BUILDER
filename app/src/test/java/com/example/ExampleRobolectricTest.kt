package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.builder.ApkBuilder
import com.example.data.ProjectConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("NEO APK BUILDER", appName)
    }

    @Test
    fun `test buildApk end to end`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testProjectDir = File(context.filesDir, "test_project").apply { mkdirs() }
        File(testProjectDir, "index.html").writeText("<h1>Hello Test</h1>")

        val config = ProjectConfig(
            appName = "NEO DOWNLOADER",
            packageName = "com.neo.downloader",
            versionName = "1.0.1",
            versionCode = 1,
            orientation = "portrait",
            fullscreen = false,
            permissions = "INTERNET,ACCESS_NETWORK_STATE,CAMERA"
        )

        val apkFile = ApkBuilder.buildApk(
            context = context,
            config = config,
            projectDir = testProjectDir,
            customIconBitmap = null
        )

        File("/tmp/built_test.apk").writeBytes(apkFile.readBytes())

        assertNotNull("Generated APK should not be null", apkFile)
        assertTrue("Generated APK must exist", apkFile.exists())
        assertTrue("Generated APK size must be greater than 10KB", apkFile.length() > 10_000)

        // Verify that APK is signed with APK Signature Scheme v1, v2, v3
        val verifier = com.android.apksig.ApkVerifier.Builder(apkFile).build()
        val result = verifier.verify()
        assertTrue("Generated APK must pass signature verification", result.isVerified)
        assertTrue("Generated APK must have v1 scheme", result.isVerifiedUsingV1Scheme)
        assertTrue("Generated APK must have v2 scheme", result.isVerifiedUsingV2Scheme)
    }

    @Test
    fun `test PermissionManager manifest mapping`() {
        val perms = com.example.data.PermissionManager.getManifestPermissions("INTERNET,ACCESS_NETWORK_STATE,CAMERA")
        assertTrue(perms.contains("android.permission.INTERNET"))
        assertTrue(perms.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertTrue(perms.contains("android.permission.CAMERA"))
        assertFalse(perms.contains("android.permission.READ_CONTACTS"))
        assertFalse(perms.contains("android.permission.MANAGE_EXTERNAL_STORAGE"))
    }

    @Test
    fun `test selective permissions in built APK`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testProjectDir = File(context.filesDir, "test_project_perms").apply { mkdirs() }
        File(testProjectDir, "index.html").writeText("<h1>Permissions Test</h1>")

        val config = ProjectConfig(
            appName = "MINIMAL APP",
            packageName = "com.neo.minimal",
            versionName = "1.0.0",
            versionCode = 2,
            permissions = "INTERNET,ACCESS_NETWORK_STATE"
        )

        val apkFile = ApkBuilder.buildApk(
            context = context,
            config = config,
            projectDir = testProjectDir,
            customIconBitmap = null
        )

        assertTrue(apkFile.exists())
        val verifier = com.android.apksig.ApkVerifier.Builder(apkFile).build()
        assertTrue(verifier.verify().isVerified)
    }
}
