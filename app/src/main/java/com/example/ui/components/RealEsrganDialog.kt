package com.example.ui.components

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ai.*
import com.example.ui.MainViewModel
import com.example.ui.theme.*

@Composable
fun RealEsrganDialog(
    sourceBitmap: Bitmap,
    sourceFileName: String,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isUpscaling by viewModel.isUpscaling.collectAsState()
    val progress by viewModel.upscaleProgress.collectAsState()
    val upscaledResult by viewModel.upscaledResultBitmap.collectAsState()

    var selectedModel by remember { mutableStateOf(RealEsrganModel.REAL_ESRGAN_X4PLUS) }
    var selectedPreset by remember { mutableStateOf(TargetResolutionPreset.UHD_4K) }
    var tileSize by remember { mutableIntStateOf(256) }
    var sharpness by remember { mutableFloatStateOf(0.7f) }
    var denoise by remember { mutableFloatStateOf(0.4f) }
    var saveFileName by remember {
        mutableStateOf(
            sourceFileName.substringBeforeLast(".") + "_4k.png"
        )
    }

    // Determine estimated output dimensions
    val estimatedDims = remember(sourceBitmap, selectedPreset) {
        val config = RealEsrganConfig(model = selectedModel, resolutionPreset = selectedPreset)
        RealEsrganEngine.computeTargetDimensions(sourceBitmap.width, sourceBitmap.height, config)
    }

    Dialog(
        onDismissRequest = {
            if (!isUpscaling) {
                viewModel.clearUpscaledResult()
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = CyberBg
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberSurface)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (!isUpscaling) {
                                    viewModel.clearUpscaledResult()
                                    onDismiss()
                                }
                            },
                            enabled = !isUpscaling
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup", tint = TextWhite)
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "REAL-ESRGAN AI",
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    letterSpacing = 0.5.sp
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF0F2B20),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.6f))
                                ) {
                                    Text(
                                        text = "4K OFFLINE",
                                        color = NeonGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Super-Resolution Neural Engine (Java/Kotlin)",
                                color = TextGray,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Preview Card (Before & After)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CyberSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (upscaledResult != null) "HASIL REAL-ESRGAN AI (4K)" else "PRATINJAU GAMBAR SUMBER",
                                    fontWeight = FontWeight.Bold,
                                    color = if (upscaledResult != null) NeonGreen else NeonCyan,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = if (upscaledResult != null)
                                        "${upscaledResult!!.width} × ${upscaledResult!!.height} px"
                                    else
                                        "${sourceBitmap.width} × ${sourceBitmap.height} px ➜ Target: ${estimatedDims.first} × ${estimatedDims.second} px",
                                    color = TextGray,
                                    fontSize = 11.sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF060D17))
                                    .border(1.dp, if (upscaledResult != null) NeonGreen.copy(alpha = 0.5f) else CyberBorder, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                val displayBmp = upscaledResult ?: sourceBitmap
                                Image(
                                    bitmap = displayBmp.asImageBitmap(),
                                    contentDescription = "Image Preview",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp)
                                )

                                if (upscaledResult != null) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.Black.copy(alpha = 0.75f),
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = "✓ REAL-ESRGAN 4K ENHANCED",
                                            color = NeonGreen,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Progress Card when running
                    if (isUpscaling) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B192A)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.7f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = NeonCyan,
                                        strokeWidth = 2.5.dp
                                    )
                                    Column {
                                        Text(
                                            text = "PEMROSESAN REAL-ESRGAN OFFLINE",
                                            color = NeonCyan,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = progress?.statusText ?: "Memproses neural tiles...",
                                            color = TextWhite,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                val pct = progress?.percent ?: 0
                                LinearProgressIndicator(
                                    progress = { pct / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = NeonCyan,
                                    trackColor = CyberSurfaceVariant
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Tile: ${progress?.currentTile ?: 0} / ${progress?.totalTiles ?: 0}",
                                        color = TextGray,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = "$pct%",
                                        color = NeonCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    // Model Selection
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CyberSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "PROFIL MODEL REAL-ESRGAN",
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            RealEsrganModel.values().forEach { model ->
                                val isSelected = selectedModel == model
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable(enabled = !isUpscaling) { selectedModel = model },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) CyberSurfaceVariant else Color.Transparent,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) NeonCyan else CyberBorder.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { selectedModel = model },
                                            enabled = !isUpscaling,
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = NeonCyan,
                                                unselectedColor = TextGray
                                            )
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = model.displayName,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextWhite,
                                                    fontSize = 13.sp
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = NeonCyan.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = model.badge,
                                                        color = NeonCyan,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = model.description,
                                                color = TextGray,
                                                fontSize = 11.sp,
                                                lineHeight = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Target Resolution Selection
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CyberSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.PhotoSizeSelectActual, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "TARGET RESOLUSI OUTPUT",
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TargetResolutionPreset.values().forEach { preset ->
                                    val isSelected = selectedPreset == preset
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { if (!isUpscaling) selectedPreset = preset },
                                        enabled = !isUpscaling,
                                        label = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                if (preset.is4K) {
                                                    Text("⚡", fontSize = 10.sp)
                                                }
                                                Text(preset.title, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                            }
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NeonCyan,
                                            selectedLabelColor = CyberBg,
                                            containerColor = CyberSurfaceVariant,
                                            labelColor = TextWhite
                                        )
                                    )
                                }
                            }

                            Text(
                                text = "Resolusi target: ${estimatedDims.first} × ${estimatedDims.second} piksel (Penskalaan AI Offline tanpa kompresi server)",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Tiling & Performance Tuning
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CyberSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Memory, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "OPTIMASI MEMORI & TILING CHUNKING",
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            Text(
                                text = "Ukuran Tile Chunk (Mencegah OutOfMemory / OOM saat memproses resolusi 4K):",
                                color = TextGray,
                                fontSize = 11.sp
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                listOf(128 to "128px (Hemat RAM)", 256 to "256px (Standar)", 512 to "512px (Cepat)").forEach { (size, label) ->
                                    val isSelected = tileSize == size
                                    Surface(
                                        onClick = { if (!isUpscaling) tileSize = size },
                                        enabled = !isUpscaling,
                                        modifier = Modifier.weight(1f).height(40.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) NeonCyan else CyberSurfaceVariant,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) NeonCyan else CyberBorder)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
                                            Text(
                                                text = label,
                                                color = if (isSelected) CyberBg else TextWhite,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }

                            // Sharpness Slider
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Tingkat Ketajaman Tepi (Sharpness):", color = TextGray, fontSize = 11.sp)
                                    Text("${(sharpness * 100).toInt()}%", color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = sharpness,
                                    onValueChange = { sharpness = it },
                                    enabled = !isUpscaling,
                                    valueRange = 0.1f..1.0f,
                                    colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
                                )
                            }
                        }
                    }

                    // Action Buttons: Run Super-Resolution / Save
                    if (upscaledResult == null) {
                        Button(
                            onClick = {
                                val cfg = RealEsrganConfig(
                                    model = selectedModel,
                                    resolutionPreset = selectedPreset,
                                    tileSize = tileSize,
                                    sharpnessStrength = sharpness,
                                    denoiseStrength = denoise
                                )
                                viewModel.upscaleImageWithRealEsrgan(sourceBitmap, cfg)
                            },
                            enabled = !isUpscaling,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("run_real_esrgan_btn"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan,
                                contentColor = CyberBg
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "MULAI PROSES REAL-ESRGAN 4K",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        // Options after processing completes:
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = saveFileName,
                                onValueChange = { saveFileName = it },
                                label = { Text("Nama Berkas Simpan") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Save as project asset
                            Button(
                                onClick = {
                                    viewModel.saveUpscaledAsProjectAsset(upscaledResult!!, saveFileName)
                                    Toast.makeText(context, "✓ $saveFileName berhasil disimpan ke folder proyek!", Toast.LENGTH_SHORT).show()
                                    viewModel.clearUpscaledResult()
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonGreen,
                                    contentColor = CyberBg
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SIMPAN KE FOLDER PROYEK", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            // Apply as App Logo/Icon
                            Button(
                                onClick = {
                                    viewModel.applyUpscaledAsAppIcon(upscaledResult!!)
                                    Toast.makeText(context, "✓ Logo APK diperbarui dengan hasil Real-ESRGAN 4K!", Toast.LENGTH_SHORT).show()
                                    viewModel.clearUpscaledResult()
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF0D253D),
                                    contentColor = NeonCyan
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Android, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("TERAPKAN SEBAGAI LOGO APK", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            TextButton(
                                onClick = {
                                    viewModel.clearUpscaledResult()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("PROSES ULANG DENGAN PARAMETER LAIN", color = TextGray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
