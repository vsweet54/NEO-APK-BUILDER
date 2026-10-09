package com.example.bridge

import android.app.Activity
import android.app.DownloadManager
import android.app.PictureInPictureParams
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.provider.Settings
import android.util.Base64
import android.util.Rational
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.Toast
import com.example.ai.RealEsrganConfig
import com.example.ai.RealEsrganEngine
import com.example.ai.RealEsrganModel
import com.example.ai.TargetResolutionPreset
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

/**
 * Native Android Bridge providing seamless two-way communication between
 * HTML/JavaScript web apps and Android native platform services.
 * 
 * Includes Content Format Magic-Byte Validator, Blob URL handling, Picture-in-Picture,
 * System Overlay permission management, and offline AI upscaling.
 */
class AndroidBridge(
    private val context: Context,
    private val appName: String = "NEO App",
    private val packageName: String = "com.neo.app"
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor = Executors.newFixedThreadPool(4)
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    private var autoPipEnabled = false

    data class FileFormatInfo(val extension: String, val mimeType: String)

    companion object {
        fun inspectMagicBytes(bytes: ByteArray, rawName: String?, passedMime: String?): FileFormatInfo {
            if (bytes.isEmpty()) return FileFormatInfo(".bin", "application/octet-stream")
            val len = bytes.size

            // 1. PNG: 89 50 4E 47 0D 0A 1A 0A
            if (len >= 8 && (bytes[0].toInt() and 0xFF) == 0x89 && bytes[1].toInt() == 0x50 &&
                bytes[2].toInt() == 0x4E && bytes[3].toInt() == 0x47 && bytes[4].toInt() == 0x0D &&
                bytes[5].toInt() == 0x0A && bytes[6].toInt() == 0x1A && bytes[7].toInt() == 0x0A) {
                return FileFormatInfo(".png", "image/png")
            }

            // 2. JPEG: FF D8 FF
            if (len >= 3 && (bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xFF) == 0xD8 && (bytes[2].toInt() and 0xFF) == 0xFF) {
                return FileFormatInfo(".jpg", "image/jpeg")
            }

            // 3. GIF: GIF87a or GIF89a
            if (len >= 6 && bytes[0].toInt() == 'G'.code && bytes[1].toInt() == 'I'.code && bytes[2].toInt() == 'F'.code && bytes[3].toInt() == '8'.code) {
                return FileFormatInfo(".gif", "image/gif")
            }

            // 4. WEBP: RIFF....WEBP
            if (len >= 12 && bytes[0].toInt() == 'R'.code && bytes[1].toInt() == 'I'.code && bytes[2].toInt() == 'F'.code && bytes[3].toInt() == 'F'.code &&
                bytes[8].toInt() == 'W'.code && bytes[9].toInt() == 'E'.code && bytes[10].toInt() == 'B'.code && bytes[11].toInt() == 'P'.code) {
                return FileFormatInfo(".webp", "image/webp")
            }

            // 5. BMP: BM
            if (len >= 2 && bytes[0].toInt() == 'B'.code && bytes[1].toInt() == 'M'.code) {
                return FileFormatInfo(".bmp", "image/bmp")
            }

            // 6. PDF: %PDF-
            if (len >= 4 && bytes[0].toInt() == '%'.code && bytes[1].toInt() == 'P'.code && bytes[2].toInt() == 'D'.code && bytes[3].toInt() == 'F'.code) {
                return FileFormatInfo(".pdf", "application/pdf")
            }

            // 7. ZIP / APK / JAR / DOCX / XLSX / EPUB: PK\x03\x04
            if (len >= 4 && bytes[0].toInt() == 'P'.code && bytes[1].toInt() == 'K'.code &&
                (bytes[2].toInt() == 0x03 || bytes[2].toInt() == 0x05 || bytes[2].toInt() == 0x07) &&
                (bytes[3].toInt() == 0x04 || bytes[3].toInt() == 0x06 || bytes[3].toInt() == 0x08)) {
                val lower = rawName?.lowercase() ?: ""
                return when {
                    lower.endsWith(".apk") || passedMime?.contains("android.package-archive") == true -> FileFormatInfo(".apk", "application/vnd.android.package-archive")
                    lower.endsWith(".epub") || passedMime?.contains("epub") == true -> FileFormatInfo(".epub", "application/epub+zip")
                    lower.endsWith(".docx") -> FileFormatInfo(".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    lower.endsWith(".xlsx") -> FileFormatInfo(".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    else -> FileFormatInfo(".zip", "application/zip")
                }
            }

            // 8. 7Z: 37 7A BC AF 27 1C
            if (len >= 6 && (bytes[0].toInt() and 0xFF) == 0x37 && (bytes[1].toInt() and 0xFF) == 0x7A && (bytes[2].toInt() and 0xFF) == 0xBC &&
                (bytes[3].toInt() and 0xFF) == 0xAF && (bytes[4].toInt() and 0xFF) == 0x27 && (bytes[5].toInt() and 0xFF) == 0x1C) {
                return FileFormatInfo(".7z", "application/x-7z-compressed")
            }

            // 9. RAR: Rar!
            if (len >= 4 && bytes[0].toInt() == 'R'.code && bytes[1].toInt() == 'a'.code && bytes[2].toInt() == 'r'.code && bytes[3].toInt() == '!'.code) {
                return FileFormatInfo(".rar", "application/vnd.rar")
            }

            // 10. GZIP: 1F 8B
            if (len >= 2 && (bytes[0].toInt() and 0xFF) == 0x1F && (bytes[1].toInt() and 0xFF) == 0x8B) {
                return FileFormatInfo(".gz", "application/gzip")
            }

            // 11. MP3
            if ((len >= 3 && bytes[0].toInt() == 'I'.code && bytes[1].toInt() == 'D'.code && bytes[2].toInt() == '3'.code) ||
                (len >= 2 && (bytes[0].toInt() and 0xFF) == 0xFF && ((bytes[1].toInt() and 0xFF) and 0xE0) == 0xE0)) {
                return FileFormatInfo(".mp3", "audio/mpeg")
            }

            // 12. MP4
            if (len >= 8 && bytes[4].toInt() == 'f'.code && bytes[5].toInt() == 't'.code && bytes[6].toInt() == 'y'.code && bytes[7].toInt() == 'p'.code) {
                return FileFormatInfo(".mp4", "video/mp4")
            }

            // Textual inspection
            try {
                val scanLen = Math.min(len, 1024)
                val snippet = String(bytes, 0, scanLen, StandardCharsets.UTF_8).trim()
                val lower = snippet.lowercase()
                if (lower.startsWith("<svg") || (lower.startsWith("<?xml") && lower.contains("<svg"))) {
                    return FileFormatInfo(".svg", "image/svg+xml")
                }
                if (lower.startsWith("<!doctype html") || lower.startsWith("<html")) {
                    return FileFormatInfo(".html", "text/html")
                }
                if ((snippet.startsWith("{") && snippet.endsWith("}")) || (snippet.startsWith("[") && snippet.endsWith("]"))) {
                    return FileFormatInfo(".json", "application/json")
                }
            } catch (ignored: Throwable) {}

            // MIME mapping
            if (!passedMime.isNullOrBlank() && !passedMime.equals("application/octet-stream", ignoreCase = true)) {
                val m = passedMime.lowercase()
                return when {
                    m.contains("image/png") -> FileFormatInfo(".png", "image/png")
                    m.contains("image/jpeg") || m.contains("image/jpg") -> FileFormatInfo(".jpg", "image/jpeg")
                    m.contains("image/webp") -> FileFormatInfo(".webp", "image/webp")
                    m.contains("image/svg") -> FileFormatInfo(".svg", "image/svg+xml")
                    m.contains("application/pdf") -> FileFormatInfo(".pdf", "application/pdf")
                    m.contains("application/zip") -> FileFormatInfo(".zip", "application/zip")
                    m.contains("android.package-archive") -> FileFormatInfo(".apk", "application/vnd.android.package-archive")
                    m.contains("text/plain") -> FileFormatInfo(".txt", "text/plain")
                    m.contains("text/html") -> FileFormatInfo(".html", "text/html")
                    m.contains("text/css") -> FileFormatInfo(".css", "text/css")
                    m.contains("json") -> FileFormatInfo(".json", "application/json")
                    m.contains("audio/mpeg") || m.contains("audio/mp3") -> FileFormatInfo(".mp3", "audio/mpeg")
                    m.contains("video/mp4") -> FileFormatInfo(".mp4", "video/mp4")
                    else -> FileFormatInfo(".bin", "application/octet-stream")
                }
            }

            return FileFormatInfo(".bin", "application/octet-stream")
        }

        fun resolveGenuineFileName(bytes: ByteArray, rawName: String?, passedMime: String?): String {
            val info = inspectMagicBytes(bytes, rawName, passedMime)
            val clean = rawName?.trim()?.replace("[/\\\\:*?\"<>|]".toRegex(), "_") ?: ""

            if (clean.isBlank() || clean.equals("downloadfile", ignoreCase = true) ||
                clean.equals("downloadfile.bin", ignoreCase = true) || clean.matches("^[0-9a-fA-F\\-]{36}(\\.bin)?$".toRegex())) {
                return "download_${System.currentTimeMillis()}${info.extension}"
            }

            if (clean.endsWith(".bin", ignoreCase = true)) {
                if (!info.extension.equals(".bin", ignoreCase = true)) {
                    return clean.substring(0, clean.length - 4) + info.extension
                }
                return clean
            }

            if (!clean.contains(".")) {
                return "$clean${info.extension}"
            }

            return clean
        }
    }

    @JavascriptInterface
    fun showToast(message: String?) {
        if (message.isNullOrBlank()) return
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun copyToClipboard(text: String?): Boolean {
        if (text == null) return false
        return try {
            mainHandler.post {
                val clip = ClipData.newPlainText(appName, text)
                clipboard?.setPrimaryClip(clip)
                Toast.makeText(context, "Teks disalin ke papan klip", Toast.LENGTH_SHORT).show()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @JavascriptInterface
    fun getFromClipboard(): String {
        return try {
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    fun saveBytesToDownloads(bytes: ByteArray, fileName: String?, mimeType: String?): Boolean {
        if (bytes.isEmpty()) return false
        val formatInfo = inspectMagicBytes(bytes, fileName, mimeType)
        val safeName = resolveGenuineFileName(bytes, fileName, mimeType)
        val effMime = formatInfo.mimeType

        // 1. Android 10+ (API 29+) MediaStore scoped storage
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(MediaStore.Downloads.MIME_TYPE, effMime)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { os ->
                        os.write(bytes)
                        os.flush()
                    }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    notifyMediaScanner(safeName)
                    return true
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }

        // 2. Direct external public Downloads directory
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val targetFile = File(downloadsDir, safeName)
            FileOutputStream(targetFile).use { fos ->
                fos.write(bytes)
                fos.flush()
            }
            notifyMediaScanner(targetFile.absolutePath)
            return true
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        // 3. Fallback to app-specific external or internal files
        try {
            val fallbackDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val fallbackFile = File(fallbackDir, safeName)
            FileOutputStream(fallbackFile).use { fos ->
                fos.write(bytes)
                fos.flush()
            }
            notifyMediaScanner(fallbackFile.absolutePath)
            return true
        } catch (t: Throwable) {
            t.printStackTrace()
            return false
        }
    }

    private fun notifyMediaScanner(pathOrName: String) {
        try {
            MediaScannerConnection.scanFile(context, arrayOf(pathOrName), null, null)
        } catch (ignored: Throwable) {}
    }

    @JavascriptInterface
    fun downloadFile(url: String?, fileName: String?, mimeType: String?): Boolean {
        if (url.isNullOrBlank()) return false
        mainHandler.post {
            handleDownload(url, fileName, mimeType, null)
        }
        return true
    }

    @JavascriptInterface
    fun saveTextFile(content: String?, fileName: String?, mimeType: String?): Boolean {
        if (content == null) return false
        val name = if (fileName.isNullOrBlank()) "file_${System.currentTimeMillis()}.txt" else fileName
        ioExecutor.execute {
            val bytes = content.toByteArray(Charsets.UTF_8)
            val resolvedName = resolveGenuineFileName(bytes, name, "text/plain")
            val ok = saveBytesToDownloads(bytes, resolvedName, "text/plain")
            mainHandler.post {
                if (ok) {
                    Toast.makeText(context, "Berkas disimpan ke Downloads: $resolvedName", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Gagal menyimpan berkas di penyimpanan", Toast.LENGTH_SHORT).show()
                }
            }
        }
        return true
    }

    @JavascriptInterface
    fun saveFile(base64Data: String?, fileName: String?, mimeType: String?): Boolean {
        if (base64Data == null) return false
        ioExecutor.execute {
            try {
                var detectedMime = mimeType
                if (base64Data.startsWith("data:") && base64Data.contains(";")) {
                    detectedMime = base64Data.substring(5, base64Data.indexOf(";"))
                }
                val cleanBase64 = if (base64Data.contains(",")) base64Data.substringAfter(",") else base64Data
                val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)

                val resolvedName = resolveGenuineFileName(bytes, fileName, detectedMime)
                val formatInfo = inspectMagicBytes(bytes, resolvedName, detectedMime)
                val ok = saveBytesToDownloads(bytes, resolvedName, formatInfo.mimeType)

                mainHandler.post {
                    if (ok) {
                        Toast.makeText(context, "Berkas disimpan ke Downloads: $resolvedName", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Gagal menyimpan berkas di penyimpanan", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    Toast.makeText(context, "Gagal memproses berkas: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        return true
    }

    // ==================== SYSTEM OVERLAY & PIP ====================

    @JavascriptInterface
    fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    @JavascriptInterface
    fun requestOverlayPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(context)) {
                mainHandler.post {
                    try {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        ).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                        Toast.makeText(context, "Aktifkan 'Izinkan ditampilkan di atas aplikasi lain'", Toast.LENGTH_LONG).show()
                    } catch (e: Throwable) {
                        Toast.makeText(context, "Gagal membuka setelan overlay: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                return true
            }
        }
        return true
    }

    @JavascriptInterface
    fun enterPip(aspectWidth: Int, aspectHeight: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val activity = context as? Activity ?: return false
        if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            return false
        }
        mainHandler.post {
            try {
                val w = if (aspectWidth > 0) aspectWidth else 16
                val h = if (aspectHeight > 0) aspectHeight else 9
                val rational = Rational(w, h)
                val builder = PictureInPictureParams.Builder()
                if (rational.toFloat() in 0.41841f..2.39f) {
                    builder.setAspectRatio(rational)
                }
                activity.enterPictureInPictureMode(builder.build())
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
        return true
    }

    @JavascriptInterface
    fun isPipSupported(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        } else false
    }

    @JavascriptInterface
    fun isInPipMode(): Boolean {
        val activity = context as? Activity ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            activity.isInPictureInPictureMode
        } else false
    }

    @JavascriptInterface
    fun setAutoPip(enable: Boolean) {
        autoPipEnabled = enable
    }

    fun handleDownload(url: String?, contentDisposition: String? = null, mimeType: String? = null, userAgent: String? = null, webView: WebView? = null) {
        if (url.isNullOrBlank()) return

        // 1. BLOB URL
        if (url.startsWith("blob:")) {
            val safeName = guessFileName(url, contentDisposition, mimeType)
            if (webView != null) {
                val js = "javascript:(function(){" +
                        "try {" +
                        "  fetch('" + url.replace("'", "\\'") + "')" +
                        "  .then(function(res){ return res.blob(); })" +
                        "  .then(function(b){" +
                        "    var r = new FileReader();" +
                        "    r.onloadend = function(){" +
                        "      var bridge = window.AndroidBridge || window.NeoAndroid;" +
                        "      if (bridge && typeof bridge.saveFile === 'function') {" +
                        "        bridge.saveFile(r.result, '" + safeName.replace("'", "\\'") + "', b.type || '" + (mimeType?.replace("'", "\\'") ?: "application/octet-stream") + "');" +
                        "      }" +
                        "    };" +
                        "    r.readAsDataURL(b);" +
                        "  })" +
                        "  .catch(function(){" +
                        "    var xhr = new XMLHttpRequest();" +
                        "    xhr.open('GET', '" + url.replace("'", "\\'") + "', true);" +
                        "    xhr.responseType = 'blob';" +
                        "    xhr.onload = function(){" +
                        "      if(this.status === 200 || this.status === 0){" +
                        "        var b = this.response;" +
                        "        var r = new FileReader();" +
                        "        r.onloadend = function(){" +
                        "          var bridge = window.AndroidBridge || window.NeoAndroid;" +
                        "          if(bridge && typeof bridge.saveFile === 'function') {" +
                        "            bridge.saveFile(r.result, '" + safeName.replace("'", "\\'") + "', b.type || 'application/octet-stream');" +
                        "          }" +
                        "        };" +
                        "        r.readAsDataURL(b);" +
                        "      }" +
                        "    };" +
                        "    xhr.send();" +
                        "  });" +
                        "} catch(e) {" +
                        "  var bridge = window.AndroidBridge || window.NeoAndroid;" +
                        "  if(bridge && typeof bridge.showToast === 'function') bridge.showToast('Error unduh blob: ' + e.message);" +
                        "}" +
                        "})();"
                webView.evaluateJavascript(js, null)
            } else {
                Toast.makeText(context, "Memproses unduhan berkas...", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // 2. DATA URL
        if (url.startsWith("data:")) {
            ioExecutor.execute {
                try {
                    val commaIdx = url.indexOf(",")
                    if (commaIdx != -1) {
                        val header = url.substring(0, commaIdx)
                        val dataPart = url.substring(commaIdx + 1)
                        var extractedMime = mimeType
                        if (header.contains(":") && header.contains(";")) {
                            extractedMime = header.substring(5, header.indexOf(";"))
                        }
                        val bytes = if (header.contains(";base64")) {
                            Base64.decode(dataPart, Base64.DEFAULT)
                        } else {
                            Uri.decode(dataPart).toByteArray(Charsets.UTF_8)
                        }
                        val resolvedName = resolveGenuineFileName(bytes, contentDisposition, extractedMime)
                        val ok = saveBytesToDownloads(bytes, resolvedName, extractedMime)
                        mainHandler.post {
                            if (ok) {
                                Toast.makeText(context, "Berkas disimpan ke Downloads: $resolvedName", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Gagal menyimpan berkas di penyimpanan", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (t: Throwable) {
                    mainHandler.post {
                        Toast.makeText(context, "Gagal mengunduh berkas: ${t.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            return
        }

        // 3. HTTP / HTTPS URL
        if (url.startsWith("http://") || url.startsWith("https://")) {
            try {
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                if (dm != null) {
                    val uri = Uri.parse(url)
                    val fileName = guessFileName(url, contentDisposition, mimeType)
                    val request = DownloadManager.Request(uri).apply {
                        setTitle(fileName)
                        setDescription("Mengunduh dengan $appName")
                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                        try {
                            val cookie = CookieManager.getInstance().getCookie(url)
                            if (!cookie.isNullOrBlank()) addRequestHeader("Cookie", cookie)
                        } catch (ignored: Throwable) {}
                        if (!userAgent.isNullOrBlank()) {
                            addRequestHeader("User-Agent", userAgent)
                        }
                    }
                    dm.enqueue(request)
                    Toast.makeText(context, "Mengunduh berkas: $fileName", Toast.LENGTH_SHORT).show()
                    return
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }

            // Fallback: Safe Intent
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (anfe: ActivityNotFoundException) {
                Toast.makeText(context, "Tidak ada browser untuk membuka tautan.", Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                Toast.makeText(context, "Gagal membuka link: ${t.message}", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // 4. Other schemes
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (anfe: ActivityNotFoundException) {
            Toast.makeText(context, "Tidak ada aplikasi untuk menangani tautan ini.", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Toast.makeText(context, "Gagal membuka tautan: ${t.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun guessFileName(url: String, contentDisposition: String?, mimeType: String?): String {
        if (!contentDisposition.isNullOrBlank()) {
            val trimmed = contentDisposition.trim()
            if (!trimmed.lowercase().contains("attachment") && !trimmed.contains(";")) {
                val sanitized = trimmed.replace("[/\\\\:*?\"<>|]".toRegex(), "_")
                if (sanitized.contains(".") && !sanitized.endsWith(".bin")) {
                    return sanitized
                }
            }
            if (trimmed.contains("filename=")) {
                try {
                    val sub = trimmed.substring(trimmed.indexOf("filename=") + 9)
                    if (sub.startsWith("\"") && sub.indexOf("\"", 1) != -1) {
                        return sub.substring(1, sub.indexOf("\"", 1))
                    }
                    val end = sub.indexOf(";")
                    return (if (end != -1) sub.substring(0, end) else sub).trim()
                } catch (ignored: Throwable) {}
            }
        }

        if (url.startsWith("blob:") || url.startsWith("data:")) {
            var ext = ".bin"
            if (mimeType != null) {
                val m = mimeType.lowercase()
                ext = when {
                    m.contains("image/png") -> ".png"
                    m.contains("image/jpeg") || m.contains("image/jpg") -> ".jpg"
                    m.contains("image/webp") -> ".webp"
                    m.contains("image/svg") -> ".svg"
                    m.contains("application/pdf") -> ".pdf"
                    m.contains("application/zip") -> ".zip"
                    m.contains("android.package-archive") -> ".apk"
                    m.contains("text/plain") -> ".txt"
                    m.contains("text/html") -> ".html"
                    m.contains("text/css") -> ".css"
                    m.contains("json") -> ".json"
                    m.contains("audio/mpeg") || m.contains("audio/mp3") -> ".mp3"
                    m.contains("video/mp4") -> ".mp4"
                    else -> ".bin"
                }
            }
            return "download_${System.currentTimeMillis()}$ext"
        }

        return try {
            val guessed = URLUtil.guessFileName(url, contentDisposition, mimeType)
            if (!guessed.isNullOrBlank() && !guessed.equals("downloadfile", ignoreCase = true) && !guessed.equals("downloadfile.bin", ignoreCase = true)) {
                guessed
            } else {
                "download_${System.currentTimeMillis()}.bin"
            }
        } catch (e: Exception) {
            "download_${System.currentTimeMillis()}.bin"
        }
    }

    @JavascriptInterface
    fun vibrate(durationMs: Long) {
        try {
            val dur = if (durationMs in 10..5000) durationMs else 100L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(dur, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(dur, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(dur)
                }
            }
        } catch (ignored: Exception) {}
    }

    // ==================== REAL-ESRGAN NATIVE AI INTEGRATION ====================

    @JavascriptInterface
    fun isAvailable(): Boolean = true

    @JavascriptInterface
    fun isRealEsrganAvailable(): Boolean = true

    @JavascriptInterface
    fun upscaleImage4K(base64Image: String?): String = upscaleImage(base64Image, 4)

    @JavascriptInterface
    fun upscaleImage(base64Image: String?, scaleFactor: Int): String {
        if (base64Image.isNullOrBlank()) return ""
        return try {
            val cleanBase64 = if (base64Image.contains(",")) base64Image.substringAfter(",") else base64Image
            val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            val inputBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return ""

            val targetPreset = when (scaleFactor) {
                2 -> TargetResolutionPreset.SCALE_2X
                else -> TargetResolutionPreset.SCALE_4X
            }

            val config = RealEsrganConfig(
                model = RealEsrganModel.REAL_ESRGAN_X4PLUS,
                resolutionPreset = targetPreset,
                tileSize = 256,
                sharpnessStrength = 0.7f
            )

            val outputBitmap: Bitmap = runBlocking {
                RealEsrganEngine.processImage(context, inputBitmap, config)
            }

            val baos = ByteArrayOutputStream()
            outputBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
            val resultBytes = baos.toByteArray()
            val encoded = Base64.encodeToString(resultBytes, Base64.NO_WRAP)
            outputBitmap.recycle()
            inputBitmap.recycle()

            "data:image/png;base64,$encoded"
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    @JavascriptInterface
    fun getAppInfo(): String {
        return try {
            val json = JSONObject().apply {
                put("appName", appName)
                put("packageName", packageName)
                put("platform", "Android")
                put("pipSupported", isPipSupported())
                put("overlaySupported", true)
                put("realEsrganSupported", true)
                put("version", "2.1-Pro")
            }
            json.toString()
        } catch (e: Exception) {
            "{}"
        }
    }
}
