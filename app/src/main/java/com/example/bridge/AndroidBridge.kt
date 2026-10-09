package com.example.bridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Base64
import android.webkit.JavascriptInterface
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

    @JavascriptInterface
    fun saveTextFile(content: String?, fileName: String?, mimeType: String?): Boolean {
        if (content == null) return false
        val name = if (fileName.isNullOrBlank()) "file_${System.currentTimeMillis()}.txt" else fileName
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val targetFile = File(downloadsDir, name)
            FileOutputStream(targetFile).use { fos ->
                fos.write(content.toByteArray(Charsets.UTF_8))
            }
            mainHandler.post {
                Toast.makeText(context, "Berkas disimpan ke Downloads: $name", Toast.LENGTH_LONG).show()
            }
            true
        } catch (e: Exception) {
            // Fallback to internal storage
            try {
                val fallbackFile = File(context.filesDir, name)
                FileOutputStream(fallbackFile).use { fos ->
                    fos.write(content.toByteArray(Charsets.UTF_8))
                }
                mainHandler.post {
                    Toast.makeText(context, "Berkas disimpan: $name", Toast.LENGTH_SHORT).show()
                }
                true
            } catch (err: Exception) {
                false
            }
        }
    }

    @JavascriptInterface
    fun saveFile(base64Data: String?, fileName: String?, mimeType: String?): Boolean {
        if (base64Data == null) return false
        val name = if (fileName.isNullOrBlank()) "file_${System.currentTimeMillis()}.bin" else fileName
        return try {
            val cleanBase64 = if (base64Data.contains(",")) base64Data.substringAfter(",") else base64Data
            val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val targetFile = File(downloadsDir, name)
            FileOutputStream(targetFile).use { fos ->
                fos.write(bytes)
            }
            mainHandler.post {
                Toast.makeText(context, "Berkas berhasil disimpan: $name", Toast.LENGTH_SHORT).show()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
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
