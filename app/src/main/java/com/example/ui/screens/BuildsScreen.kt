package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BuildHistoryItem
import com.example.ui.MainViewModel
import com.example.ui.theme.*
import com.example.ui.util.AppLogoHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BuildsScreen(viewModel: MainViewModel) {
    val builds by viewModel.builds.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBg)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header (matching Screenshot 2 & 3!)
        item {
            Column {
                Text(
                    text = "RIWAYAT BUILD APK",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = NeonCyan,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Semua paket APK yang telah berhasil dikompilasi secara lokal",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
            }
        }

        if (builds.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Android,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Belum Ada APK yang Dikompilasi",
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Buka menu CONFIG atau HOME untuk mulai mengompilasi proyek web Anda ke APK.",
                            color = TextGray,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.selectTab(2) },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = CyberBg),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Buka Config & Build", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            items(builds) { item ->
                BuildItemCard(
                    item = item,
                    onInstall = { viewModel.installBuild(item) },
                    onShare = { viewModel.shareBuild(item) },
                    onHostLink = { viewModel.uploadBuildApk(item) },
                    onDelete = { viewModel.deleteBuild(item) }
                )
            }
        }
    }
}

@Composable
fun BuildItemCard(
    item: BuildHistoryItem,
    onInstall: () -> Unit,
    onShare: () -> Unit,
    onHostLink: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("build_item_card_${item.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
    ) {
        val context = LocalContext.current
        val appLogo = remember(item.id, item.apkPath, item.appName) {
            AppLogoHelper.resolveAppIcon(
                context = context,
                appName = item.appName,
                packageName = item.packageName,
                apkPath = item.apkPath
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header Row: App Logo, App Name, and Ready tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Image(
                        bitmap = appLogo,
                        contentDescription = "Logo ${item.appName}",
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, CyberBorder, RoundedCornerShape(12.dp))
                    )

                    Column {
                        Text(
                            text = item.appName,
                            fontWeight = FontWeight.Black,
                            color = TextWhite,
                            fontSize = 17.sp,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${item.packageName} • v${item.versionName}",
                            color = NeonCyanDim,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
                        val dateStr = dateFormat.format(Date(item.buildTimestamp))
                        Text(
                            text = "$dateStr • ${formatFileSize(item.fileSizeBytes)}",
                            color = TextGray,
                            fontSize = 12.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = NeonGreen.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = item.status,
                        color = NeonGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons Row 1: [INSTALL] & [BAGIKAN]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onInstall,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp)
                        .testTag("install_apk_btn_${item.id}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = CyberBg
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("INSTALL", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, maxLines = 1)
                }

                FilledTonalButton(
                    onClick = onShare,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp)
                        .testTag("share_apk_btn_${item.id}"),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = CyberSurfaceVariant,
                        contentColor = TextWhite
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("BAGIKAN", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, maxLines = 1)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons Row 2: [UPLOAD HOSTING] & [HAPUS]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onHostLink,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF0F2B20),
                        contentColor = NeonGreen
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "UPLOAD HOSTING",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }

                FilledTonalButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp)
                        .testTag("delete_apk_btn_${item.id}"),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF2E1318),
                        contentColor = CyberRed
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("HAPUS", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, maxLines = 1)
                }
            }
        }
    }
}
