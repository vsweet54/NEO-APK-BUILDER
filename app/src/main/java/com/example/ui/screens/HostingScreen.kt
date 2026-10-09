package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BuildHistoryItem
import com.example.data.HostedApk
import com.example.ui.MainViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostingScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    var selectedHostingTab by remember { mutableStateOf(0) } // 0: Cloud APK Hosting, 1: Local Preview

    val hostedApks by viewModel.hostedApks.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val uploadProgress by viewModel.uploadProgress.collectAsState()
    val uploadStatusMessage by viewModel.uploadStatusMessage.collectAsState()
    val builds by viewModel.builds.collectAsState()

    val isServerRunning by viewModel.isServerRunning.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val selectedProject by viewModel.selectedProject.collectAsState()

    var showBuildSelectDialog by remember { mutableStateOf(false) }
    var selectedBuildToUpload by remember { mutableStateOf<BuildHistoryItem?>(null) }

    val apkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadCustomApkFromStorage(uri, context.contentResolver, context)
            Toast.makeText(context, "Memulai upload berkas APK...", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBg)
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "APK HOSTING & PREVIEW",
                        color = NeonCyan,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Hosting APK online & uji coba lokal",
                        color = TextGray,
                        fontSize = 11.sp
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = CyberBg)
        )

        // Sub Tabs: [ ☁️ CLOUD APK HOSTING ] | [ 🌐 LOCAL WEB PREVIEW ]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SelectableTabButton(
                text = "☁️ CLOUD APK HOSTING",
                selected = selectedHostingTab == 0,
                modifier = Modifier.weight(1f),
                onClick = { selectedHostingTab = 0 }
            )
            SelectableTabButton(
                text = "🌐 LOCAL PREVIEW",
                selected = selectedHostingTab == 1,
                modifier = Modifier.weight(1f),
                onClick = { selectedHostingTab = 1 }
            )
        }

        if (selectedHostingTab == 0) {
            // CLOUD APK HOSTING VIEW
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Card: Upload APK Action
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("upload_apk_card"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = CyberSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = NeonCyan)
                                Text(
                                    text = "UPLOAD APK KE CLOUD HOSTING",
                                    fontWeight = FontWeight.Bold,
                                    color = NeonCyan,
                                    fontSize = 13.sp
                                )
                            }

                            Text(
                                text = "Unggah file APK untuk menghasilkan tautan unduhan online publik yang dapat dibagikan ke siapa saja.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextGray
                            )

                            // Two selection source buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (builds.isEmpty()) {
                                            Toast.makeText(context, "Belum ada APK yang dibuat. Bangun APK terlebih dahulu!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            showBuildSelectDialog = true
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CyberSurfaceVariant,
                                        contentColor = TextWhite
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
                                ) {
                                    Icon(Icons.Default.History, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Dari Hasil Build", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        apkPickerLauncher.launch("application/vnd.android.package-archive")
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CyberSurfaceVariant,
                                        contentColor = TextWhite
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Dari Storage HP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // If user selected build to upload
                            if (selectedBuildToUpload != null) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF0F2436),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(selectedBuildToUpload!!.appName, fontWeight = FontWeight.Bold, color = TextWhite)
                                            Text("v${selectedBuildToUpload!!.versionName} • ${formatFileSize(selectedBuildToUpload!!.fileSizeBytes)}", fontSize = 11.sp, color = NeonCyan)
                                        }
                                        IconButton(onClick = { selectedBuildToUpload = null }) {
                                            Icon(Icons.Default.Close, contentDescription = null, tint = TextGray)
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        viewModel.uploadBuildApk(selectedBuildToUpload!!)
                                        selectedBuildToUpload = null
                                    },
                                    enabled = !isUploading,
                                    modifier = Modifier.fillMaxWidth().height(46.dp).testTag("upload_selected_build_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = CyberBg),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("UPLOAD KE CLOUD SEKARANG", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }

                            // Live upload progress banner
                            if (isUploading) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(uploadStatusMessage ?: "Mengunggah...", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text("$uploadProgress%", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    LinearProgressIndicator(
                                        progress = { uploadProgress / 100f },
                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                        color = NeonCyan,
                                        trackColor = CyberSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Section: DAFTAR APK TERHOSTING
                item {
                    Text(
                        text = "APK TERHOSTING (${hostedApks.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextGray,
                        letterSpacing = 1.sp
                    )
                }

                if (hostedApks.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = CyberSurface)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.CloudQueue, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Belum Ada APK yang Dihosting", fontWeight = FontWeight.Bold, color = TextWhite)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Pilih APK dari hasil build di atas atau dari storage HP Anda untuk mendapatkan link unduhan online instan.",
                                    color = TextGray,
                                    fontSize = 12.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(hostedApks) { item ->
                        HostedApkCard(
                            item = item,
                            onCopyLink = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("APK Download Link", item.directDownloadUrl)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Link unduhan disalin ke clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            onDownload = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.directDownloadUrl)).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            },
                            onShare = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Unduh ${item.appName} APK")
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Unduh APK ${item.appName} v${item.versionName}:\n${item.directDownloadUrl}"
                                    )
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Bagikan Link APK"))
                            },
                            onDelete = {
                                viewModel.deleteHostedApk(item)
                                Toast.makeText(context, "Tautan hosting dihapus", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        } else {
            // LOCAL WEB PREVIEW VIEW (Original HTTP server)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = "HOSTING LOKAL & PREVIEW",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Jalankan server HTTP tertanam di perangkat untuk menguji situs di peramban secara real-time via Wi-Fi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray
                    )
                }

                // Server status toggle card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = CyberSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(if (isServerRunning) NeonGreen else CyberRed)
                                )
                                Column {
                                    Text(
                                        text = if (isServerRunning) "SERVER AKTIF" else "SERVER NONAKTIF",
                                        fontWeight = FontWeight.Bold,
                                        color = if (isServerRunning) NeonGreen else TextGray,
                                        fontSize = 14.sp
                                    )
                                    if (isServerRunning && serverUrl != null) {
                                        Text(
                                            text = serverUrl!!,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            color = NeonCyan
                                        )
                                    }
                                }
                            }

                            Switch(
                                checked = isServerRunning,
                                onCheckedChange = { viewModel.toggleServer() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = CyberBg,
                                    checkedTrackColor = NeonCyan,
                                    uncheckedThumbColor = TextGray,
                                    uncheckedTrackColor = CyberSurfaceVariant
                                )
                            )
                        }
                    }
                }

                if (isServerRunning && serverUrl != null) {
                    item {
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(serverUrl)).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = CyberBg),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Buka Preview di Browser", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Served Files Info Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = CyberSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "BERKAS YANG DILAYANI",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = selectedProject?.name ?: "NEO DOWNLOADER",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Root folder: ${selectedProject?.folderName ?: "neo_downloader"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextGray
                            )
                        }
                    }
                }

                // Tips Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = CyberSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(24.dp))
                            Column {
                                Text("Tips Pengujian", fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Saat server aktif, perangkat lain di jaringan Wi-Fi yang sama dapat mengakses proyek web Anda melalui alamat IP yang tertera di atas.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextGray
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: Select Build from history to upload
    if (showBuildSelectDialog) {
        AlertDialog(
            onDismissRequest = { showBuildSelectDialog = false },
            title = { Text("Pilih APK dari Hasil Build", color = NeonCyan, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(builds) { b ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedBuildToUpload = b
                                    showBuildSelectDialog = false
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = CyberSurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(b.appName, fontWeight = FontWeight.Bold, color = TextWhite)
                                Text("v${b.versionName} • ${formatFileSize(b.fileSizeBytes)}", fontSize = 11.sp, color = NeonCyan)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBuildSelectDialog = false }) { Text("BATAL", color = TextGray) }
            },
            containerColor = CyberSurface
        )
    }
}

@Composable
fun HostedApkCard(
    item: HostedApk,
    onCopyLink: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hosted_apk_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Name, Version, Size, Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.appName, fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "v${item.versionName} • ${formatFileSize(item.fileSizeBytes)}",
                        color = NeonCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0F2B20),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "ONLINE",
                        color = NeonGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Direct Download Link Container
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF091220),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = item.directDownloadUrl,
                        fontSize = 11.sp,
                        color = NeonCyan,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    IconButton(onClick = onCopyLink, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Salin Link", tint = NeonCyan, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons Row: [ DOWNLOAD APK ] [ COPY LINK ] [ SHARE ] [ DELETE ]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDownload,
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = CyberBg),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("UNDUH APK", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onCopyLink,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text("SALIN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text("BAGIKAN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus", tint = CyberRed, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
