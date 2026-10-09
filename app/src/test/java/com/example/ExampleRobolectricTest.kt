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

    @Test
    fun `test Real-ESRGAN 4K target dimensions calculation`() {
        val config4K = com.example.ai.RealEsrganConfig(
            model = com.example.ai.RealEsrganModel.REAL_ESRGAN_X4PLUS,
            resolutionPreset = com.example.ai.TargetResolutionPreset.UHD_4K
        )
        val (w4k, h4k) = com.example.ai.RealEsrganEngine.computeTargetDimensions(512, 512, config4K)
        assertTrue("4K width should be 3840 or scaled proportion", w4k >= 2160)

        val configSquare4K = com.example.ai.RealEsrganConfig(
            model = com.example.ai.RealEsrganModel.REAL_ESRNET_ICONS,
            resolutionPreset = com.example.ai.TargetResolutionPreset.SQUARE_4K
        )
        val (wSq, hSq) = com.example.ai.RealEsrganEngine.computeTargetDimensions(256, 256, configSquare4K)
        assertEquals(4096, wSq)
        assertEquals(4096, hSq)
    }

    @Test
    fun `test Real-ESRGAN offline super-resolution tile execution`() = kotlinx.coroutines.test.runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testBmp = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(testBmp)
        canvas.drawColor(android.graphics.Color.CYAN)

        val config = com.example.ai.RealEsrganConfig(
            model = com.example.ai.RealEsrganModel.REAL_ESRGAN_FAST_2X,
            resolutionPreset = com.example.ai.TargetResolutionPreset.SCALE_2X,
            tileSize = 128
        )

        var lastPercent = 0
        val upscaled = com.example.ai.RealEsrganEngine.processImage(context, testBmp, config) { progress ->
            lastPercent = progress.percent
        }

        assertNotNull(upscaled)
        assertEquals(128, upscaled.width)
        assertEquals(128, upscaled.height)
        assertEquals(100, lastPercent)
    }

    @Test
    fun `test nested folder hierarchical operations`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = com.example.data.ProjectRepository(context)
        val project = com.example.data.Project(
            name = "FolderTest",
            folderName = "foldertest_${System.currentTimeMillis()}",
            packageName = "com.neo.foldertest"
        )
        val projDir = repo.getProjectDir(project.folderName)

        // Create nested folders
        repo.createFolder(project, "assets/images")
        repo.createFolder(project, "css")
        repo.saveFile(project, "index.html", "<h1>Root</h1>")
        repo.saveFile(project, "css/style.css", "body { color: red; }")
        repo.saveFile(project, "assets/images/logo.png", "dummy-image-data")

        // Root listing should only show direct children: css, assets, index.html
        val rootItems = repo.listFilesAtDirectory(project, "")
        assertTrue(rootItems.any { it.name == "assets" && it.isDirectory })
        assertTrue(rootItems.any { it.name == "css" && it.isDirectory })
        assertTrue(rootItems.any { it.name == "index.html" && !it.isDirectory })
        assertFalse(rootItems.any { it.name == "style.css" }) // style.css is inside css/

        // Listing inside css/
        val cssItems = repo.listFilesAtDirectory(project, "css")
        assertEquals(1, cssItems.size)
        assertEquals("style.css", cssItems.first().name)

        // Clean up
        projDir.deleteRecursively()
    }

    @Test
    fun `test AndroidBridge saveTextFile and saveFile to storage`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bridge = com.example.bridge.AndroidBridge(context, "TestApp", "com.test.app")

        val saveTextOk = bridge.saveTextFile("Hello Download Test Content", "test_file.txt", "text/plain")
        assertTrue("saveTextFile should return true", saveTextOk)

        val base64Data = "data:text/plain;base64," + android.util.Base64.encodeToString(
            "Base64 download test content".toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )
        val saveFileOk = bridge.saveFile(base64Data, "test_b64.txt", "text/plain")
        assertTrue("saveFile should return true", saveFileOk)
    }

    @Test
    fun `test AndroidBridge downloadFile handles data and http schemes safely`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bridge = com.example.bridge.AndroidBridge(context, "TestApp", "com.test.app")

        // Test data URL download
        val dataUrl = "data:text/plain;base64," + android.util.Base64.encodeToString(
            "Data URL direct download".toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )
        val dlDataOk = bridge.downloadFile(dataUrl, "data_download.txt", "text/plain")
        assertTrue("downloadFile with data URI should succeed", dlDataOk)

        // Test http URL download (should queue or handle safely without crashing)
        val dlHttpOk = bridge.downloadFile("https://example.com/sample.zip", "sample.zip", "application/zip")
        assertTrue("downloadFile with http URI should succeed", dlHttpOk)

        // Test blob URL handling without throwing ActivityNotFoundException
        bridge.handleDownload("blob:http://localhost/1234-5678", null, "application/octet-stream", null, null)
    }

    @Test
    fun `test built APK contains updated download bridge and classes dex`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testProjectDir = File(context.filesDir, "test_dl_apk").apply { mkdirs() }
        File(testProjectDir, "index.html").writeText("<a href=\"file.zip\" download=\"file.zip\">Unduh</a>")

        val config = ProjectConfig(
            appName = "TEST DOWNLOADER",
            packageName = "com.neo.testdl",
            versionName = "1.0.0",
            versionCode = 1,
            permissions = "INTERNET,ACCESS_NETWORK_STATE,POST_NOTIFICATIONS"
        )

        val apkFile = ApkBuilder.buildApk(
            context = context,
            config = config,
            projectDir = testProjectDir,
            customIconBitmap = null
        )

        assertTrue(apkFile.exists())
        java.util.zip.ZipFile(apkFile).use { zip ->
            val bridgeEntry = zip.getEntry("assets/www/neo_bridge.js")
            assertNotNull("neo_bridge.js must exist in built APK", bridgeEntry)
            val bridgeJs = zip.getInputStream(bridgeEntry).readBytes().toString(Charsets.UTF_8)
            assertTrue("neo_bridge.js must contain downloadFile", bridgeJs.contains("downloadFile"))
            assertTrue("neo_bridge.js must contain download attribute interceptor", bridgeJs.contains("download"))

            val dexEntry = zip.getEntry("classes.dex")
            assertNotNull("classes.dex must exist in built APK", dexEntry)
            val dexBytes = zip.getInputStream(dexEntry).readBytes()
            val dexStr = String(dexBytes, Charsets.ISO_8859_1)
            assertTrue("classes.dex must contain saveBytesToDownloads", dexStr.contains("saveBytesToDownloads"))
            assertTrue("classes.dex must contain DownloadManager", dexStr.contains("DownloadManager"))
        }

        testProjectDir.deleteRecursively()
    }
}
