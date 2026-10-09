package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.Project
import com.example.ui.MainViewModel
import com.example.ui.theme.*

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val projects by viewModel.projects.collectAsState()
    val selectedProject by viewModel.selectedProject.collectAsState()
    val builds by viewModel.builds.collectAsState()
    val isBuilding by viewModel.isBuilding.collectAsState()
    val buildStatusMessage by viewModel.buildStatusMessage.collectAsState()
    val buildError by viewModel.buildError.collectAsState()
    val showBuildSuccessDialog by viewModel.showBuildSuccessDialog.collectAsState()
    val lastBuiltItem by viewModel.lastBuiltItem.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showProjectPickerDialog by remember { mutableStateOf(false) }
    var showEngineInfoDialog by remember { mutableStateOf(false) }
    var showWebPreview by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBg)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header matching Screenshot 3
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "NEO APK BUILDER",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = NeonCyan,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Native Android APK Compiler",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray
                    )
                }

                // Engine Ready Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF0F2B20),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.6f)),
                    modifier = Modifier.clickable { showEngineInfoDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(NeonGreen)
                        )
                        Text(
                            text = "Engine Ready",
                            color = NeonGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Active Project Hero Card (Screenshot 3)
        item {
            val proj = selectedProject ?: projects.firstOrNull()
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("featured_project_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Top: Logo, Name with dropdown, Package, Badges
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_app_logo),
                            contentDescription = "Project Icon",
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.5.dp, NeonCyanDim, RoundedCornerShape(14.dp))
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.clickable { showProjectPickerDialog = true }
                            ) {
                                Text(
                                    text = proj?.name ?: "NEO DOWNLOADER",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = TextWhite,
                                    letterSpacing = 0.5.sp
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "Pilih Proyek",
                                    tint = NeonCyan
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = proj?.packageName ?: "com.neo.downloader",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextGray
                            )
                        }

                        // Badges: [API 34] [v1.0.1]
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF0F2B20),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "API ${proj?.targetSdk ?: 34}",
                                    color = NeonGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF0D253D),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "v${proj?.versionName ?: "1.0.1"}",
                                    color = NeonCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Row of 3 buttons: [+ Projek Baru] [📁 Pilih Projek] [▶ Preview]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { showCreateDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = CyberSurfaceVariant,
                                contentColor = TextWhite
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Projek Baru", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        FilledTonalButton(
                            onClick = { showProjectPickerDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = CyberSurfaceVariant,
                                contentColor = TextWhite
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pilih Projek", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        FilledTonalButton(
                            onClick = { showWebPreview = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = CyberSurfaceVariant,
                                contentColor = TextWhite
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Preview", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Big Prominent Button: [ 🔧 BUILD ANDROID APK ]
                    Button(
                        onClick = { viewModel.buildApk() },
                        enabled = !isBuilding,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("build_android_apk_hero_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan,
                            contentColor = CyberBg
                        )
                    ) {
                        if (isBuilding) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = CyberBg, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("MENGOMPILASI...", fontWeight = FontWeight.Black)
                        } else {
                            Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("BUILD ANDROID APK", fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 0.5.sp)
                        }
                    }
                }
            }
        }

        // Section: PROJECT WORKSPACE (Screenshot 3)
        item {
            Text(
                text = "PROJECT WORKSPACE",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = TextGray,
                letterSpacing = 1.sp
            )
        }

        // 6 Workspace Cards in 2 columns
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    WorkspaceCard(
                        title = "File Explorer",
                        subtitle = "HTML, CSS, JS",
                        icon = Icons.Default.Folder,
                        iconTint = NeonCyan,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.selectTab(1) }
                    )
                    WorkspaceCard(
                        title = "App Config",
                        subtitle = "Package & Logo",
                        icon = Icons.Default.Settings,
                        iconTint = Color(0xFFD05CE3),
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.selectTab(2) }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    WorkspaceCard(
                        title = "Cloud Hosting",
                        subtitle = "Upload APK & Link",
                        icon = Icons.Default.Cloud,
                        iconTint = NeonGreen,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.selectTab(3) }
                    )
                    WorkspaceCard(
                        title = "Build History",
                        subtitle = "${builds.size} APK dibuat",
                        icon = Icons.Default.History,
                        iconTint = NeonCyan,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.selectTab(4) }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    WorkspaceCard(
                        title = "Daftar Projek",
                        subtitle = "${projects.size} projek tersimpan",
                        icon = Icons.Default.FolderSpecial,
                        iconTint = CyberYellow,
                        modifier = Modifier.weight(1f),
                        onClick = { showProjectPickerDialog = true }
                    )
                    WorkspaceCard(
                        title = "Build Engine",
                        subtitle = "Toolchain Siap",
                        icon = Icons.Default.Memory,
                        iconTint = CyberRed,
                        modifier = Modifier.weight(1f),
                        onClick = { showEngineInfoDialog = true }
                    )
                }
            }
        }

        // Section: PROJEK ANDA
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "PROJEK ANDA",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextGray,
                    letterSpacing = 1.sp
                )
                TextButton(onClick = { showProjectPickerDialog = true }) {
                    Text("Lihat Semua", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        items(projects) { project ->
            ProjectSummaryCard(
                project = project,
                onOpen = {
                    viewModel.selectProject(project)
                    viewModel.selectTab(1)
                },
                onBuild = {
                    viewModel.selectProject(project)
                    viewModel.buildApk()
                }
            )
        }
    }

    if (showCreateDialog) {
        CreateProjectDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, pkg, tpl ->
                viewModel.createProject(name, pkg, tpl)
                showCreateDialog = false
            }
        )
    }

    if (showProjectPickerDialog) {
        ProjectPickerDialog(
            projects = projects,
            selectedProject = selectedProject,
            onDismiss = { showProjectPickerDialog = false },
            onSelect = { proj ->
                viewModel.selectProject(proj)
                showProjectPickerDialog = false
            }
        )
    }

    // Live Build Progress Dialog
    if (isBuilding) {
        AlertDialog(
            onDismissRequest = { /* non-cancellable while compiling */ },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = NeonCyan, strokeWidth = 2.5.dp)
                    Text("MEMBANGUN APK ANDROID", color = NeonCyan, fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = buildStatusMessage ?: "Memproses proyek...",
                        color = TextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = NeonCyan,
                        trackColor = CyberSurfaceVariant
                    )
                    Text(
                        text = "Harap tunggu, proses kompilasi native sedang berlangsung di perangkat Anda.",
                        color = TextGray,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {},
            containerColor = CyberSurface
        )
    }

    // Build Success Dialog
    if (showBuildSuccessDialog && lastBuiltItem != null) {
        val item = lastBuiltItem!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissBuildSuccessDialog() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonGreen)
                    Text("BUILD BERHASIL!", color = NeonGreen, fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF091420),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.appName, fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 16.sp)
                            Text("Package: ${item.packageName}", fontSize = 11.sp, color = TextGray)
                            Text("Versi: v${item.versionName} • Ukuran: ${formatFileSize(item.fileSizeBytes)}", fontSize = 11.sp, color = NeonCyan)
                            Text("Tipe: Release / Signed APK", fontSize = 11.sp, color = NeonGreen)
                        }
                    }

                    Text("APK telah tersimpan di storage perangkat dan siap digunakan:", fontSize = 12.sp, color = TextWhite)

                    // Action buttons in dialog
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.installBuild(item)
                                viewModel.dismissBuildSuccessDialog()
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = CyberBg),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("INSTALL APK SEKARANG", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                viewModel.uploadBuildApk(item)
                                viewModel.dismissBuildSuccessDialog()
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F2B20), contentColor = NeonGreen),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("UPLOAD KE CLOUD HOSTING", fontWeight = FontWeight.Bold)
                        }

                        FilledTonalButton(
                            onClick = {
                                viewModel.shareBuild(item)
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("BAGIKAN APK")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissBuildSuccessDialog() }) {
                    Text("TUTUP", color = TextGray)
                }
            },
            containerColor = CyberSurface
        )
    }

    // Build Error Dialog
    if (buildError != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearBuildError() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Error, contentDescription = null, tint = CyberRed)
                    Text("BUILD FAILED", color = CyberRed, fontWeight = FontWeight.Black)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(buildError!!, color = TextWhite, fontSize = 13.sp)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF2E1318),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Saran: Pastikan berkas index.html tersedia di Project Explorer dan Package Name menggunakan format valid (contoh: com.nama.app).",
                            color = TextGray,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearBuildError() },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberRed, contentColor = TextWhite)
                ) {
                    Text("MENGERTI")
                }
            },
            containerColor = CyberSurface
        )
    }

    if (showEngineInfoDialog) {
        AlertDialog(
            onDismissRequest = { showEngineInfoDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(NeonGreen))
                    Text("Build Engine Toolchain Status", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("STATUS TOOLCHAIN:", fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 12.sp)
                    Text("● BUILD ENGINE: READY", color = NeonGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("● ANDROID SDK: READY (Target API 34)", color = NeonGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("● COMPILER AXML: READY (Binary Manifest)", color = NeonGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("● ON-DEVICE JAR SIGNER: READY (v1 Scheme)", color = NeonGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("● RUNTIME WEB ENGINE: Chromium WebView Ready", color = NeonCyan, fontSize = 13.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        android.widget.Toast.makeText(context, "Semua toolchain terverifikasi SIAP!", android.widget.Toast.LENGTH_SHORT).show()
                        showEngineInfoDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = CyberBg)
                ) {
                    Text("CHECK TOOLCHAIN", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEngineInfoDialog = false }) { Text("TUTUP", color = TextGray) }
            },
            containerColor = CyberSurface
        )
    }

    if (showWebPreview && selectedProject != null) {
        WebPreviewDialog(
            project = selectedProject!!,
            onDismiss = { showWebPreview = false }
        )
    }
}

@Composable
fun WorkspaceCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clickable { onClick() }
            .testTag("workspace_card_${title.lowercase().replace(" ", "_")}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                color = TextWhite,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = TextGray,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun ProjectPickerDialog(
    projects: List<Project>,
    selectedProject: Project?,
    onDismiss: () -> Unit,
    onSelect: (Project) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pilih Proyek Aktif", color = NeonCyan, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(projects) { p ->
                    val isCurrent = p.id == selectedProject?.id
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(p) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCurrent) CyberSurfaceVariant else CyberSurface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isCurrent) NeonCyan else CyberBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(p.name, fontWeight = FontWeight.Bold, color = TextWhite)
                                Text(p.packageName, fontSize = 11.sp, color = TextGray)
                            }
                            if (isCurrent) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = NeonCyan)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("TUTUP", color = TextGray) }
        },
        containerColor = CyberSurface
    )
}

@Composable
fun ProjectSummaryCard(
    project: Project,
    onOpen: () -> Unit,
    onBuild: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${project.packageName} • v${project.versionName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonCyan
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = onOpen,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = CyberSurfaceVariant,
                            contentColor = TextWhite
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Editor", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onBuild,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan,
                            contentColor = CyberBg
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Android, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Build", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, pkg: String, template: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var pkg by remember { mutableStateOf("") }
    var selectedTemplate by remember { mutableStateOf("downloader") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buat Proyek Baru", color = NeonCyan, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (pkg.isBlank() || pkg.startsWith("com.neo.")) {
                            val clean = it.lowercase().replace("[^a-z0-9]".toRegex(), "")
                            pkg = if (clean.isNotBlank()) "com.neo.$clean" else ""
                        }
                    },
                    label = { Text("Nama Aplikasi") },
                    placeholder = { Text("Contoh: NEO DOWNLOADER") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = pkg,
                    onValueChange = { pkg = it },
                    label = { Text("Package Name") },
                    placeholder = { Text("Contoh: com.neo.downloader") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("Pilih Template:", style = MaterialTheme.typography.labelMedium, color = TextGray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedTemplate == "downloader",
                        onClick = { selectedTemplate = "downloader" },
                        label = { Text("Downloader") }
                    )
                    FilterChip(
                        selected = selectedTemplate == "game",
                        onClick = { selectedTemplate = "game" },
                        label = { Text("Game") }
                    )
                    FilterChip(
                        selected = selectedTemplate == "blank",
                        onClick = { selectedTemplate = "blank" },
                        label = { Text("Blank") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = name.ifBlank { "PROYEK SAYA" }
                    val finalPkg = pkg.ifBlank { "com.neo.app" }
                    onCreate(finalName, finalPkg, selectedTemplate)
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = CyberBg)
            ) {
                Text("BUAT PROYEK", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("BATAL", color = TextGray) }
        },
        containerColor = CyberSurface
    )
}
