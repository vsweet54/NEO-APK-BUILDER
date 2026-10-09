package com.example.ui.util

import android.content.Context
import android.graphics.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

object AppLogoHelper {

    /**
     * Resolves the app logo / icon for a project or built APK:
     * 1. Checks if icon exists in the project folder (icon.png, logo.png, etc.)
     * 2. Checks if an extracted APK icon exists
     * 3. Dynamically generates a beautiful, themed application logo based on the app name/category
     *    (e.g., Galeri -> Gallery palette & icon, Kalkulator -> Calculator theme, Downloader -> Neon cyan theme, Game -> Crimson runner theme)
     */
    fun resolveAppIcon(
        context: Context,
        folderName: String? = null,
        appName: String,
        packageName: String,
        apkPath: String? = null
    ): ImageBitmap {
        // 1. Try project folder
        if (!folderName.isNullOrBlank()) {
            val pDir = File(context.filesDir, "projects/$folderName")
            val possible = listOf("icon.png", "icon.jpg", "icon.jpeg", "icon.webp", "logo.png", "logo.jpg", "app_logo.png")
            val iconFile = possible.map { File(pDir, it) }.firstOrNull { it.exists() }
            if (iconFile != null) {
                try {
                    val bmp = BitmapFactory.decodeFile(iconFile.absolutePath)
                    if (bmp != null) return bmp.asImageBitmap()
                } catch (ignored: Exception) {}
            }
        }

        // 2. Try extracting from APK archive if provided
        if (!apkPath.isNullOrBlank()) {
            val apkFile = File(apkPath)
            if (apkFile.exists()) {
                val cachedIcon = File(context.cacheDir, "apk_icons/${apkFile.nameWithoutExtension}.png")
                if (cachedIcon.exists()) {
                    try {
                        val bmp = BitmapFactory.decodeFile(cachedIcon.absolutePath)
                        if (bmp != null) return bmp.asImageBitmap()
                    } catch (ignored: Exception) {}
                } else {
                    try {
                        val pm = context.packageManager
                        val pi = pm.getPackageArchiveInfo(apkPath, 0)
                        val appInfo = pi?.applicationInfo
                        if (appInfo != null) {
                            appInfo.sourceDir = apkPath
                            appInfo.publicSourceDir = apkPath
                            val d = appInfo.loadIcon(pm)
                            if (d != null) {
                                val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
                                val canvas = Canvas(bmp)
                                d.setBounds(0, 0, canvas.width, canvas.height)
                                d.draw(canvas)
                                cachedIcon.parentFile?.mkdirs()
                                cachedIcon.outputStream().use { fos ->
                                    bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
                                }
                                return bmp.asImageBitmap()
                            }
                        }
                    } catch (ignored: Exception) {}
                }
            }
        }

        // 3. Generate a distinct, beautiful themed logo
        val generated = generateThemedAppLogo(appName, packageName)
        return generated.asImageBitmap()
    }

    /**
     * Generates a high quality themed logo bitmap (192x192) matching the application theme.
     */
    fun generateThemedAppLogo(appName: String, packageName: String): Bitmap {
        val size = 192
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())

        val lowerName = appName.lowercase().trim()
        val lowerPkg = packageName.lowercase().trim()

        // Theme palette based on app purpose
        val (c1, c2, symbol) = when {
            lowerName.contains("galeri") || lowerName.contains("gallery") || lowerName.contains("foto") || lowerName.contains("photo") -> {
                Triple(0xFFFF4081.toInt(), 0xFF7C4DFF.toInt(), "🖼️") // Deep pink to deep purple
            }
            lowerName.contains("kalkulator") || lowerName.contains("calc") || lowerName.contains("hitung") -> {
                Triple(0xFFFF9100.toInt(), 0xFFFF3D00.toInt(), "🔢") // Orange to deep orange
            }
            lowerName.contains("game") || lowerName.contains("runner") || lowerName.contains("play") -> {
                Triple(0xFFFF1744.toInt(), 0xFFD500F9.toInt(), "🎮") // Neon red to magenta
            }
            lowerName.contains("download") || lowerName.contains("unduh") -> {
                Triple(0xFF00E5FF.toInt(), 0xFF0091EA.toInt(), "📥") // Cyan to blue
            }
            lowerName.contains("musik") || lowerName.contains("music") || lowerName.contains("audio") || lowerName.contains("lagu") -> {
                Triple(0xFF00E676.toInt(), 0xFF1DE9B6.toInt(), "🎵") // Emerald to teal
            }
            lowerName.contains("chat") || lowerName.contains("pesan") || lowerName.contains("message") -> {
                Triple(0xFF2979FF.toInt(), 0xFF651FFF.toInt(), "💬") // Royal blue
            }
            lowerName.contains("note") || lowerName.contains("catat") || lowerName.contains("buku") || lowerName.contains("todo") -> {
                Triple(0xFFFFD600.toInt(), 0xFFFF6D00.toInt(), "📝") // Amber to orange
            }
            lowerName.contains("shop") || lowerName.contains("toko") || lowerName.contains("pasar") || lowerName.contains("belanja") -> {
                Triple(0xFF00B0FF.toInt(), 0xFF00E5FF.toInt(), "🛍️") // Cyan blue
            }
            lowerName.contains("video") || lowerName.contains("film") || lowerName.contains("movie") || lowerName.contains("tube") -> {
                Triple(0xFFFF1744.toInt(), 0xFFB71C1C.toInt(), "🎬") // Cinema red
            }
            lowerName.contains("cuaca") || lowerName.contains("weather") -> {
                Triple(0xFF00E5FF.toInt(), 0xFF2979FF.toInt(), "☀️") // Sky blue
            }
            else -> {
                // Determine colors deterministically from app name hash
                val hash = Math.abs((lowerName + lowerPkg).hashCode())
                val palettes = listOf(
                    Triple(0xFF00E5FF.toInt(), 0xFF0091EA.toInt(), null),
                    Triple(0xFF7C4DFF.toInt(), 0xFF651FFF.toInt(), null),
                    Triple(0xFF00E676.toInt(), 0xFF00B0FF.toInt(), null),
                    Triple(0xFFFF9100.toInt(), 0xFFFF3D00.toInt(), null),
                    Triple(0xFFFF4081.toInt(), 0xFFF50057.toInt(), null),
                    Triple(0xFF00B0FF.toInt(), 0xFF2979FF.toInt(), null)
                )
                palettes[hash % palettes.size]
            }
        }

        // 1. Draw rounded rectangle background with gradient
        val bgPaint = Paint().apply {
            isAntiAlias = true
            shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                c1, c2,
                Shader.TileMode.CLAMP
            )
        }
        val cornerRadius = size * 0.22f
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

        // 2. Draw subtle inner cyber glow border
        val borderPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.argb(120, 255, 255, 255)
        }
        val borderRect = RectF(1.5f, 1.5f, size - 1.5f, size - 1.5f)
        canvas.drawRoundRect(borderRect, cornerRadius - 1.5f, cornerRadius - 1.5f, borderPaint)

        // 3. Draw Symbol or Initial letter in center
        val textPaint = Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        if (symbol != null) {
            textPaint.textSize = size * 0.46f
            val fontMetrics = textPaint.fontMetrics
            val baseline = (size - fontMetrics.bottom - fontMetrics.top) / 2f
            canvas.drawText(symbol, size / 2f, baseline, textPaint)
        } else {
            // Draw prominent stylish 2-letter monogram
            val initials = if (appName.isNotBlank()) {
                val words = appName.trim().split("\\s+".toRegex())
                if (words.size >= 2) {
                    "${words[0].firstOrNull()?.uppercaseChar() ?: 'N'}${words[1].firstOrNull()?.uppercaseChar() ?: 'P'}"
                } else {
                    appName.take(2).uppercase()
                }
            } else "NEO"

            textPaint.apply {
                color = Color.WHITE
                textSize = size * 0.40f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setShadowLayer(8f, 0f, 4f, Color.argb(100, 0, 0, 0))
            }
            val fontMetrics = textPaint.fontMetrics
            val baseline = (size - fontMetrics.bottom - fontMetrics.top) / 2f
            canvas.drawText(initials, size / 2f, baseline, textPaint)
        }

        return bmp
    }
}
