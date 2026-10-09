package com.example.bridge

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.util.Base64
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
import java.net.HttpURLConnection
import java.net.URL

/**
 * Native Android Bridge providing seamless two-way communication between
 * HTML/JavaScript web apps and Android native platform services.
 * 
 * Supports Clipboard, Toast, File Storage, Haptics, and Native AI Real-ESRGAN Super-Resolution.
 */
class AndroidBridge(
    private val context: Context,
    private val appName: String = "NEO App",
    private val packageName: String = "com.neo.app"
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

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
        val safeName = if (fileName.isNullOrBlank()) "download_${System.currentTimeMillis()}.bin" else fileName
        val effMime = if (mimeType.isNullOrBlank()) "application/octet-stream" else mimeType

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
            return true
        } catch (t: Throwable) {
            t.printStackTrace()
            return false
        }
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
        val bytes = content.toByteArray(Charsets.UTF_8)
        val ok = saveBytesToDownloads(bytes, name, "text/plain")
        mainHandler.post {
            if (ok) {
                Toast.makeText(context, "Berkas disimpan ke Downloads: $name", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Gagal menyimpan berkas di penyimpanan", Toast.LENGTH_SHORT).show()
            }
        }
        return ok
    }

    @JavascriptInterface
    fun saveFile(base64Data: String?, fileName: String?, mimeType: String?): Boolean {
        if (base64Data == null) return false
        val name = if (fileName.isNullOrBlank()) "file_${System.currentTimeMillis()}.bin" else fileName
        return try {
            val cleanBase64 = if (base64Data.contains(",")) base64Data.substringAfter(",") else base64Data
            val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            val ok = saveBytesToDownloads(bytes, name, mimeType)
            mainHandler.post {
                if (ok) {
                    Toast.makeText(context, "Berkas disimpan ke Downloads: $name", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Gagal menyimpan berkas di penyimpanan", Toast.LENGTH_SHORT).show()
                }
            }
            ok
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun handleDownload(url: String?, contentDisposition: String? = null, mimeType: String? = null, userAgent: String? = null, webView: WebView? = null) {
        if (url.isNullOrBlank()) return

        // 1. BLOB URL
        if (url.startsWith("blob:")) {
            val safeName = guessFileName(url, contentDisposition, mimeType)
            if (webView != null) {
                val js = "javascript:(function(){" +
                        "try {" +
                        "  var xhr = new XMLHttpRequest();" +
                        "  xhr.open('GET', '" + url.replace("'", "\\'") + "', true);" +
                        "  xhr.responseType = 'blob';" +
                        "  xhr.onload = function() {" +
                        "    if (this.status === 200 || this.status === 0) {" +
                        "      var b = this.response;" +
                        "      var r = new FileReader();" +
                        "      r.onloadend = function() {" +
                        "        var bridge = window.AndroidBridge || window.NeoAndroid;" +
                        "        if (bridge && typeof bridge.saveFile === 'function') {" +
                        "          bridge.saveFile(r.result, '" + safeName.replace("'", "\\'") + "', '" + (mimeType?.replace("'", "\\'") ?: "application/octet-stream") + "');" +
                        "        }" +
                        "      };" +
                        "      r.readAsDataURL(b);" +
                        "    }" +
                        "  };" +
                        "  xhr.onerror = function() {" +
                        "    var bridge = window.AndroidBridge || window.NeoAndroid;" +
                        "    if (bridge && typeof bridge.showToast === 'function') bridge.showToast('Gagal memproses berkas blob');" +
                        "  };" +
                        "  xhr.send();" +
                        "} catch (e) {" +
                        "  var bridge = window.AndroidBridge || window.NeoAndroid;" +
                        "  if (bridge && typeof bridge.showToast === 'function') bridge.showToast('Error unduh blob: ' + e.message);" +
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
            try {
                val safeName = guessFileName(url, contentDisposition, mimeType)
                val commaIdx = url.indexOf(",")
                if (commaIdx != -1) {
                    val header = url.substring(0, commaIdx)
                    val dataPart = url.substring(commaIdx + 1)
                    val bytes = if (header.contains(";base64")) {
                        Base64.decode(dataPart, Base64.DEFAULT)
                    } else {
                        Uri.decode(dataPart).toByteArray(Charsets.UTF_8)
                    }
                    val ok = saveBytesToDownloads(bytes, safeName, mimeType)
                    if (ok) {
                        Toast.makeText(context, "Berkas disimpan ke Downloads: $safeName", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Gagal menyimpan berkas di penyimpanan", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (t: Throwable) {
                Toast.makeText(context, "Gagal mengunduh berkas: ${t.message}", Toast.LENGTH_SHORT).show()
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
        return try {
            val guessed = URLUtil.guessFileName(url, contentDisposition, mimeType)
            if (!guessed.isNullOrBlank() && !guessed.equals("downloadfile", ignoreCase = true)) {
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
    fun isAvailable(): Boolean {
        return true
    }

    @JavascriptInterface
    fun isRealEsrganAvailable(): Boolean {
        return true
    }

    /**
     * Synchronously upscales an image using the native Real-ESRGAN engine.
     * Takes a Base64 encoded image string (or data:image/...;base64,...),
     * applies Real-ESRGAN super-resolution up to 4K resolution, and returns
     * the enhanced image as a Base64 PNG data URL.
     */
    @JavascriptInterface
    fun upscaleImage4K(base64Image: String?): String {
        return upscaleImage(base64Image, 4)
    }

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
            val json = JSONObject()
            json.put("appName", appName)
            json.put("packageName", packageName)
            json.put("platform", "Android")
            json.put("realEsrganSupported", true)
            json.put("version", "2.0")
            json.toString()
        } catch (e: Exception) {
            "{}"
        }
    }
}
