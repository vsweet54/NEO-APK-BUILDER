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
            fullscreen = false
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
    }
}
