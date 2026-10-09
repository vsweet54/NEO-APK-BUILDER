package com.example.ai

import android.graphics.Bitmap

/**
 * Model profiles supported by the Native AI Real-ESRGAN engine.
 */
enum class RealEsrganModel(
    val displayName: String,
    val description: String,
    val defaultScale: Int,
    val badge: String
) {
    REAL_ESRGAN_X4PLUS(
        displayName = "Real-ESRGAN x4+",
        description = "Universal super-resolution untuk foto, tekstur, dan aset game beresolusi tinggi.",
        defaultScale = 4,
        badge = "4X ULTRA"
    ),
    REAL_ESRGAN_ANIME(
        displayName = "Real-ESRGAN Anime 6B",
        description = "Dioptimalkan khusus gambar anime, ilustrasi 2D, garis tajam, dan seni digital.",
        defaultScale = 4,
        badge = "4X ANIME"
    ),
    REAL_ESRNET_ICONS(
        displayName = "Real-ESRNet (Icons & UI)",
        description = "Fokus pada ketajaman ikon aplikasi, logo, teks, dan elemen vektor tanpa artefak.",
        defaultScale = 4,
        badge = "4X ICONS"
    ),
    REAL_ESRGAN_FAST_2X(
        displayName = "Real-ESRGAN Fast (2x)",
        description = "Penskalaan 2x kilat offline hemat memori untuk pratinjau cepat.",
        defaultScale = 2,
        badge = "2X FAST"
    )
}

/**
 * Target output resolution presets up to 4K UHD.
 */
enum class TargetResolutionPreset(
    val title: String,
    val targetWidth: Int,
    val targetHeight: Int,
    val is4K: Boolean = false
) {
    SCALE_2X("2x Scale", 0, 0),
    SCALE_4X("4x Scale (Ultra)", 0, 0),
    FHD_1080P("Full HD 1080p", 1920, 1080),
    QHD_2K("2K QHD (1440p)", 2560, 1440),
    UHD_4K("4K UHD (2160p)", 3840, 2160, is4K = true),
    SQUARE_4K("4K Square (4096px)", 4096, 4096, is4K = true),
    ICON_1024("App Icon HD (1024px)", 1024, 1024)
}

/**
 * Configuration parameters for Real-ESRGAN processing.
 */
data class RealEsrganConfig(
    val model: RealEsrganModel = RealEsrganModel.REAL_ESRGAN_X4PLUS,
    val resolutionPreset: TargetResolutionPreset = TargetResolutionPreset.SCALE_4X,
    val customTargetWidth: Int? = null,
    val customTargetHeight: Int? = null,
    val tileSize: Int = 256, // 128, 256, or 512 for OOM-safe chunking
    val tilePad: Int = 16,   // Overlap padding to eliminate seam artifacts
    val denoiseStrength: Float = 0.5f, // 0.0f to 1.0f
    val sharpnessStrength: Float = 0.7f, // 0.0f to 1.0f
    val preserveAlpha: Boolean = true
)

/**
 * Real-time progress update during offline super-resolution processing.
 */
data class RealEsrganProgress(
    val percent: Int,
    val currentTile: Int,
    val totalTiles: Int,
    val statusText: String,
    val previewBitmap: Bitmap? = null
)
