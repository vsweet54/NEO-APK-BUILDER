package com.example.builder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.example.data.ProjectConfig
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

object ApkBuilder {

    private const val TEMPLATE_ASSET = "runner_template.apk"

    fun buildApk(
        context: Context,
        config: ProjectConfig,
        projectDir: File,
        customIconBitmap: Bitmap? = null
    ): File {
        val entries = LinkedHashMap<String, ByteArray>()

        // 1. Read base template APK entries from assets
        context.assets.open(TEMPLATE_ASSET).use { isStream ->
            ZipInputStream(isStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory && !name.startsWith("META-INF/")) {
                        val baos = ByteArrayOutputStream()
                        zis.copyTo(baos)
                        entries[name] = baos.toByteArray()
                    }
                    entry = zis.nextEntry
                }
            }
        }

        // 2. Modify AndroidManifest.xml
        val originalManifest = entries["AndroidManifest.xml"]
        if (originalManifest != null) {
            val manifestReplacements = mapOf(
                "com.neo.template" to config.packageName.trim(),
                "Neo Template" to config.appName.trim(),
                "1.0.0" to config.versionName.trim()
            )
            entries["AndroidManifest.xml"] = AxmlModifier.replaceStrings(originalManifest, manifestReplacements)
        }

        // 3. Modify resources.arsc
        val originalArsc = entries["resources.arsc"]
        if (originalArsc != null) {
            val arscReplacements = mapOf(
                "Neo Template" to config.appName.trim()
            )
            entries["resources.arsc"] = ArscModifier.replaceStrings(originalArsc, arscReplacements)
        }

        // 4. Inject Custom Launcher Icons if available
        val iconToUse = customIconBitmap ?: loadProjectIcon(projectDir)
        if (iconToUse != null) {
            val densities = mapOf(
                "res/mipmap-mdpi-v4/ic_launcher.png" to 48,
                "res/mipmap-hdpi-v4/ic_launcher.png" to 72,
                "res/mipmap-xhdpi-v4/ic_launcher.png" to 96,
                "res/mipmap-xxhdpi-v4/ic_launcher.png" to 144,
                "res/mipmap-xxxhdpi-v4/ic_launcher.png" to 192
            )
            for ((path, size) in densities) {
                entries[path] = resizeBitmapToPng(iconToUse, size)
            }
        }

        // 5. Inject Web Files into assets/www/
        val webFiles = collectProjectFiles(projectDir)
        var hasIndex = false
        for ((relPath, file) in webFiles) {
            val normalized = relPath.replace('\\', '/')
            if (normalized.equals("index.html", ignoreCase = true) || normalized.equals("index.htm", ignoreCase = true)) {
                hasIndex = true
            }
            entries["assets/www/$normalized"] = file.readBytes()
        }

        // If no index.html exists, create a default modern starter index.html
        if (!hasIndex) {
            val starterHtml = generateDefaultIndexHtml(config.appName)
            entries["assets/www/index.html"] = starterHtml.toByteArray(Charsets.UTF_8)
        }

        // 6. Sign and output the APK
        val buildsDir = File(context.filesDir, "builds").apply { mkdirs() }
        val safeFileName = "${config.appName.replace("[^a-zA-Z0-9_-]".toRegex(), "_")}_${config.versionName}.apk"
        val outputFile = File(buildsDir, safeFileName)

        ApkSigner.signApk(context, entries, outputFile)
        return outputFile
    }

    private fun loadProjectIcon(projectDir: File): Bitmap? {
        val possibleIcons = listOf("icon.png", "icon.jpg", "logo.png", "logo.jpg")
        for (name in possibleIcons) {
            val f = File(projectDir, name)
            if (f.exists()) {
                try {
                    return BitmapFactory.decodeFile(f.absolutePath)
                } catch (ignored: Exception) {}
            }
        }
        return null
    }

    private fun resizeBitmapToPng(source: Bitmap, size: Int): ByteArray {
        val scaled = Bitmap.createScaledBitmap(source, size, size, true)
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 100, baos)
        return baos.toByteArray()
    }

    private fun collectProjectFiles(dir: File): List<Pair<String, File>> {
        val list = mutableListOf<Pair<String, File>>()
        fun scan(current: File, prefix: String) {
            val files = current.listFiles() ?: return
            for (f in files) {
                if (f.name.startsWith(".")) continue
                if (f.isDirectory) {
                    scan(f, if (prefix.isEmpty()) f.name else "$prefix/${f.name}")
                } else {
                    val rel = if (prefix.isEmpty()) f.name else "$prefix/${f.name}"
                    list.add(Pair(rel, f))
                }
            }
        }
        if (dir.exists()) {
            scan(dir, "")
        }
        return list
    }

    fun generateDefaultIndexHtml(appName: String): String {
        return """
<!DOCTYPE html>
<html lang="id">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  <title>$appName</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background: linear-gradient(135deg, #060D17 0%, #0B162C 100%);
      color: #FFFFFF;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 24px;
      text-align: center;
    }
    .badge {
      display: inline-block;
      padding: 6px 16px;
      background: rgba(0, 229, 255, 0.15);
      color: #00E5FF;
      border: 1px solid rgba(0, 229, 255, 0.4);
      border-radius: 20px;
      font-size: 13px;
      font-weight: 600;
      letter-spacing: 1px;
      margin-bottom: 20px;
    }
    h1 {
      font-size: 28px;
      font-weight: 800;
      color: #00E5FF;
      text-shadow: 0 0 20px rgba(0, 229, 255, 0.5);
      margin-bottom: 12px;
    }
    p {
      color: #A0B2C6;
      font-size: 15px;
      line-height: 1.6;
      max-width: 360px;
      margin-bottom: 28px;
    }
    .card {
      background: rgba(17, 28, 46, 0.85);
      border: 1px solid rgba(0, 229, 255, 0.25);
      border-radius: 20px;
      padding: 28px 24px;
      max-width: 380px;
      width: 100%;
      box-shadow: 0 12px 40px rgba(0, 0, 0, 0.6);
    }
    .btn {
      display: inline-block;
      width: 100%;
      padding: 14px 20px;
      background: linear-gradient(90deg, #00E5FF, #0091EA);
      color: #001220;
      font-size: 15px;
      font-weight: bold;
      border-radius: 12px;
      border: none;
      cursor: pointer;
      transition: all 0.2s ease;
      text-decoration: none;
    }
    .btn:active {
      transform: scale(0.98);
      filter: brightness(0.9);
    }
    .status {
      margin-top: 18px;
      font-size: 13px;
      color: #00E676;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
    }
  </style>
</head>
<body>
  <div class="card">
    <div class="badge">NEO WEB RUNNER</div>
    <h1>$appName</h1>
    <p>Aplikasi web Anda berhasil dikompilasi ke dalam format APK Android dan berjalan dengan lancar!</p>
    <button class="btn" onclick="alert('Halo! Aplikasi $appName berjalan dengan baik.')">UJI INTERAKTIF</button>
    <div class="status">
      <span>●</span> Siap untuk dimodifikasi
    </div>
  </div>
</body>
</html>
        """.trimIndent()
    }
}
