package com.example

import com.example.builder.AxmlModifier
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class ExampleUnitTest {

    @Test
    fun testAxmlModifier_replacesPackageAndLabel() {
        val assetTemplate = File("src/main/assets/runner_template.apk")
        assertTrue("runner_template.apk should exist in assets", assetTemplate.exists())

        var originalManifestBytes: ByteArray? = null
        ZipFile(assetTemplate).use { zip ->
            val entry = zip.getEntry("AndroidManifest.xml")
            assertNotNull("AndroidManifest.xml must exist in runner_template.apk", entry)
            originalManifestBytes = zip.getInputStream(entry).readBytes()
        }

        assertNotNull(originalManifestBytes)
        val replacements = mapOf(
            "com.neo.template" to "com.neo.downloader",
            "Neo Template" to "NEO DOWNLOADER",
            "1.0.0" to "2.0.0"
        )

        val modifiedBytes = AxmlModifier.replaceStrings(originalManifestBytes!!, replacements)
        assertNotNull(modifiedBytes)
        assertTrue(modifiedBytes.isNotEmpty())

        val modifiedStr = String(modifiedBytes, Charsets.ISO_8859_1)
        // Verify that the replacement UTF-16 strings are present
        val searchPkg = "com.neo.downloader".toByteArray(Charsets.UTF_16LE)
        val searchPkgStr = String(searchPkg, Charsets.ISO_8859_1)
        assertTrue("Modified AXML must contain com.neo.downloader", modifiedStr.contains(searchPkgStr))

        val searchLabel = "NEO DOWNLOADER".toByteArray(Charsets.UTF_16LE)
        val searchLabelStr = String(searchLabel, Charsets.ISO_8859_1)
        assertTrue("Modified AXML must contain NEO DOWNLOADER", modifiedStr.contains(searchLabelStr))
    }
}
