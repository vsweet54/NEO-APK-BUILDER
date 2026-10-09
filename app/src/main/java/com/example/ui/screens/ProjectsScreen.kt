package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.Project
import com.example.data.WebProjectFile
import com.example.ui.MainViewModel
import com.example.ui.components.RealEsrganDialog
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val selectedProject by viewModel.selectedProject.collectAsState()
    val allProjects by viewModel.projects.collectAsState()
    val allFiles by viewModel.currentFiles.collectAsState()
    val directoryFiles by viewModel.directoryFiles.collectAsState()
    val currentPath by viewModel.currentDirectoryPath.collectAsState()
    val activeFile by viewModel.activeEditingFile.collectAsState()
    val fileContent by viewModel.fileContent.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()

    // 0 = Berkas & Folder (File Explorer), 1 = Daftar Projek (Projects List)
    var viewMode by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showCreateProjectDialog by remember { mutableStateOf(false) }
    var showProjectPickerDialog by remember { mutableStateOf(false) }
    var fileToRename by remember { mutableStateOf<WebProjectFile?>(null) }
    var fileToDelete by remember { mutableStateOf<WebProjectFile?>(null) }
    var projectToDelete by remember { mutableStateOf<Project?>(null) }
    var showWebPreview by remember { mutableStateOf(false) }

    // Real-ESRGAN Native AI State
    var aiTargetBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var aiTargetFileName by remember { mutableStateOf("") }
    var showAiUpscalerDialog by remember { mutableStateOf(false) }

    val hasIndexHtml = remember(allFiles) {
        allFiles.any { it.name.equals("index.html", ignoreCase = true) || it.name.equals("index.htm", ignoreCase = true) }
    }

    // File picker launcher for uploading HTML, CSS, JS, ZIP directly into current folder
    val importFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importFiles(uris, context.contentResolver, targetSubFolder = currentPath) { count ->
                Toast.makeText(context, "✓ $count berkas berhasil diimpor!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Image picker launcher specifically for Real-ESRGAN 4K Super-Resolution
    val aiImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        aiTargetBitmap = bmp
                        aiTargetFileName = "image_asset.png"
                        showAiUpscalerDialog = true
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal memuat gambar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Android Back Handler: Hierarchical Navigation
    if (activeFile != null) {
        BackHandler { viewModel.closeEditor() }
    } else if (showAiUpscalerDialog) {
        BackHandler { showAiUpscalerDialog = false }
    } else if (showWebPreview) {
        BackHandler { showWebPreview = false }
    } else if (viewMode == 0 && currentPath.isNotEmpty()) {
        BackHandler { viewModel.navigateUpFolder() }
    } else if (viewMode == 1) {
        BackHandler { viewMode = 0 }
    } else {
        BackHandler { viewModel.selectTab(0) }
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
                        text = "PROJECT WORKSPACE",
                        color = NeonCyan,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${selectedProject?.name ?: "Pilih Proyek"} • ${allFiles.size} berkas",
                            color = TextGray,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = NeonGreen.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "4K AI READY",
                                color = NeonGreen,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = {
                    if (viewMode == 0 && currentPath.isNotEmpty()) {
                        viewModel.navigateUpFolder()
                    } else {
                        viewModel.selectTab(0)
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = TextWhite)
                }
            },
            actions = {
                // Real-ESRGAN AI 4K Studio Launcher Button
                IconButton(
                    onClick = {
                        aiImagePickerLauncher.launch("image/*")
                    },
                    modifier = Modifier.testTag("open_real_esrgan_btn")
                ) {
                    Icon(
                        Icons.Default.Bolt,
                        contentDescription = "Real-ESRGAN AI 4K",
                        tint = CyberYellow,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Switch Project button
                IconButton(onClick = { showProjectPickerDialog = true }) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = "Ganti Proyek", tint = NeonCyan)
                }

                // Web Live Preview
                IconButton(onClick = { showWebPreview = true }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Preview Web", tint = NeonGreen, modifier = Modifier.size(28.dp))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = CyberBg)
        )

        if (isImporting) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = NeonCyan,
                trackColor = CyberSurfaceVariant
            )
        }

        // View Mode Segmented Tab: [ 📁 Berkas & Folder ] vs [ 📋 Daftar Projek ]
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(12.dp),
            color = CyberSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TabPillButton(
                    text = "📁 Berkas & Folder (${directoryFiles.size})",
                    selected = viewMode == 0,
                    modifier = Modifier.weight(1f),
                    onClick = { viewMode = 0 }
                )
                TabPillButton(
                    text = "📋 Daftar Projek (${allProjects.size})",
                    selected = viewMode == 1,
                    modifier = Modifier.weight(1f),
                    onClick = { viewMode = 1 }
                )
            }
        }

        if (viewMode == 0) {
            // ==================== MODE 0: BERKAS & FOLDER EXPLORER ====================
            val filteredFiles = remember(directoryFiles, searchQuery) {
                if (searchQuery.isBlank()) {
                    directoryFiles
                } else {
                    directoryFiles.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Hierarchical Breadcrumb Navigation Bar
                item {
                    BreadcrumbNavigationBar(
                        projectName = selectedProject?.name ?: "Proyek",
                        currentPath = currentPath,
                        onNavigateRoot = { viewModel.navigateToBreadcrumb("") },
                        onNavigateCrumb = { targetPath -> viewModel.navigateToBreadcrumb(targetPath) },
                        onNavigateUp = { viewModel.navigateUpFolder() }
                    )
                }

                // Action Bar: [+ Upload] [+ Berkas] [+ Folder] [⚡ Real-ESRGAN 4K] [▶ Pratinjau Web]
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { importFilesLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier
                                .height(44.dp)
                                .testTag("upload_files_to_folder_btn"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonGreen.copy(alpha = 0.15f),
                                contentColor = NeonGreen
                            ),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Upload HTML / ZIP", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = { showNewFileDialog = true },
                            modifier = Modifier.height(44.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberSurfaceVariant,
                                contentColor = NeonCyan
                            ),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Berkas Baru", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = { showNewFolderDialog = true },
                            modifier = Modifier.height(44.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberSurfaceVariant,
                                contentColor = CyberYellow
                            ),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Folder Baru", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = { aiImagePickerLauncher.launch("image/*") },
                            modifier = Modifier.height(44.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2C1608),
                                contentColor = CyberYellow
                            ),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyberYellow.copy(alpha = 0.6f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Real-ESRGAN AI 4K", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = { showWebPreview = true },
                            modifier = Modifier.height(44.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberSurfaceVariant,
                                contentColor = ElectricBlue
                            ),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pratinjau Web", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                // Search / Filter Input
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Cari berkas di ${if (currentPath.isEmpty()) "root" else currentPath}...", fontSize = 12.sp, color = TextGray) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextGray, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Hapus", tint = TextGray, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CyberSurface,
                            unfocusedContainerColor = CyberSurface,
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = CyberBorder
                        )
                    )
                }

                // Warning banner if index.html is missing at Root
                if (!hasIndexHtml && currentPath.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("index_html_warning_card"),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF261019)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyberRed.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = CyberRed,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "index.html Belum Ditemukan!",
                                        color = CyberRed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Dibutuhkan sebagai halaman utama APK web.",
                                        color = TextGray,
                                        fontSize = 11.sp
                                    )
                                }
                                Button(
                                    onClick = { viewModel.createIndexHtmlIfMissing() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NeonCyan,
                                        contentColor = CyberBg
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("BUAT", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                // If in nested folder, show ".." (Kembali ke folder sebelumnya) item
                if (currentPath.isNotEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { viewModel.navigateUpFolder() },
                            shape = RoundedCornerShape(10.dp),
                            color = CyberSurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.DriveFolderUpload, contentDescription = null, tint = CyberYellow, modifier = Modifier.size(20.dp))
                                Text(
                                    text = ".. (Kembali ke folder atas)",
                                    color = TextWhite,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // File & Folder Listing
                if (filteredFiles.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = CyberSurface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = TextMuted, modifier = Modifier.size(44.dp))
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Folder ini masih kosong", fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Klik tombol 'Upload', 'Berkas', atau 'Folder' di atas untuk mengisi.",
                                    color = TextGray,
                                    fontSize = 12.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(filteredFiles) { file ->
                        if (file.isDirectory) {
                            // Subfolder item row
                            FolderCard(
                                folder = file,
                                onOpen = { viewModel.navigateIntoFolder(file.relativePath) },
                                onRename = { fileToRename = file },
                                onDelete = { fileToDelete = file }
                            )
                        } else {
                            // File item row
                            WebFileCard(
                                file = file,
                                onEdit = { viewModel.openFile(file) },
                                onRename = { fileToRename = file },
                                onQuickSetIndex = {
                                    viewModel.renameFile(file, "index.html")
                                    Toast.makeText(context, "${file.name} diubah menjadi index.html!", Toast.LENGTH_SHORT).show()
                                },
                                onPreview = { showWebPreview = true },
                                onDelete = { fileToDelete = file },
                                onAiUpscale = {
                                    val bmp = viewModel.getProjectFileBitmap(file)
                                    if (bmp != null) {
                                        aiTargetBitmap = bmp
                                        aiTargetFileName = file.name
                                        showAiUpscalerDialog = true
                                    } else {
                                        Toast.makeText(context, "Tidak dapat membaca berkas gambar ini.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        } else {
            // ==================== MODE 1: DAFTAR PROJEK ====================
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "DAFTAR PROJEK TERSIMPAN",
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "${allProjects.size} aplikasi web siap dibuild ke APK",
                                color = TextGray,
                                fontSize = 11.sp
                            )
                        }
                        Button(
                            onClick = { showCreateProjectDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan,
                                contentColor = CyberBg
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PROJEK BARU", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                        }
                    }
                }

                items(allProjects) { project ->
                    val isCurrent = project.id == selectedProject?.id
                    ProjectListItemCard(
                        project = project,
                        isCurrent = isCurrent,
                        onOpen = {
                            viewModel.selectProject(project)
                            viewMode = 0 // Switch to file explorer
                        },
                        onEditConfig = {
                            viewModel.selectProject(project)
                            viewModel.selectTab(2) // Switch to Config tab
                        },
                        onBuild = {
                            viewModel.selectProject(project)
                            viewModel.buildApk()
                        },
                        onDelete = {
                            projectToDelete = project
                        }
                    )
                }
            }
        }
    }

    // Dialog: Create New File
    if (showNewFileDialog) {
        CreateFileDialog(
            targetFolder = currentPath,
            onDismiss = { showNewFileDialog = false },
            onCreate = { fileName, content ->
                viewModel.createFile(fileName, content)
                showNewFileDialog = false
                Toast.makeText(context, "Berkas $fileName berhasil dibuat!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Dialog: Create New Folder
    if (showNewFolderDialog) {
        CreateFolderDialog(
            targetFolder = currentPath,
            onDismiss = { showNewFolderDialog = false },
            onCreate = { folderName ->
                viewModel.createFolder(folderName)
                showNewFolderDialog = false
                Toast.makeText(context, "Folder $folderName berhasil dibuat!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Dialog: Rename File / Folder
    if (fileToRename != null) {
        RenameFileDialog(
            file = fileToRename!!,
            onDismiss = { fileToRename = null },
            onRename = { newName ->
                viewModel.renameFile(fileToRename!!, newName)
                fileToRename = null
                Toast.makeText(context, "Nama berhasil diperbarui!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Dialog: Delete File Confirmation
    if (fileToDelete != null) {
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = CyberRed)
                    Text("Hapus ${if (fileToDelete!!.isDirectory) "Folder" else "Berkas"}?", color = CyberRed, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    text = "Apakah Anda yakin ingin menghapus '${fileToDelete!!.name}'? Tindakan ini tidak dapat dibatalkan.",
                    color = TextWhite,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (fileToDelete!!.isDirectory) {
                            viewModel.deleteFolder(fileToDelete!!)
                        } else {
                            viewModel.deleteFile(fileToDelete!!)
                        }
                        fileToDelete = null
                        Toast.makeText(context, "Berhasil dihapus!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberRed, contentColor = TextWhite)
                ) {
                    Text("HAPUS", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) { Text("BATAL", color = TextGray) }
            },
            containerColor = CyberSurface
        )
    }

    // Dialog: Delete Project Confirmation
    if (projectToDelete != null) {
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = CyberRed)
                    Text("Hapus Projek?", color = CyberRed, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    text = "Hapus projek '${projectToDelete!!.name}' beserta seluruh berkas di foldernya?",
                    color = TextWhite,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProject(projectToDelete!!)
                        projectToDelete = null
                        Toast.makeText(context, "Projek berhasil dihapus!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberRed, contentColor = TextWhite)
                ) {
                    Text("HAPUS PROJEK", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) { Text("BATAL", color = TextGray) }
            },
            containerColor = CyberSurface
        )
    }

    // Dialog: Create Project
    if (showCreateProjectDialog) {
        CreateProjectDialog(
            onDismiss = { showCreateProjectDialog = false },
            onCreate = { name, pkg, tpl ->
                viewModel.createProject(name, pkg, tpl)
                showCreateProjectDialog = false
                viewMode = 0
            }
        )
    }

    // Dialog: Project Picker Switcher
    if (showProjectPickerDialog) {
        ProjectPickerDialog(
            projects = allProjects,
            selectedProject = selectedProject,
            onDismiss = { showProjectPickerDialog = false },
            onSelect = { proj ->
                viewModel.selectProject(proj)
                showProjectPickerDialog = false
            }
        )
    }

    // Dialog: Code Editor
    if (activeFile != null) {
        CodeEditorDialog(
            file = activeFile!!,
            initialContent = fileContent,
            onDismiss = { viewModel.closeEditor() },
            onSave = { newContent ->
                viewModel.saveActiveFile(newContent)
                viewModel.closeEditor()
            }
        )
    }

    // Dialog: Web Live Preview
    if (showWebPreview && selectedProject != null) {
        WebPreviewDialog(
            project = selectedProject!!,
            onDismiss = { showWebPreview = false }
        )
    }

    // Dialog: Native AI Real-ESRGAN Super-Resolution
    if (showAiUpscalerDialog && aiTargetBitmap != null) {
        RealEsrganDialog(
            sourceBitmap = aiTargetBitmap!!,
            sourceFileName = aiTargetFileName,
            viewModel = viewModel,
            onDismiss = {
                showAiUpscalerDialog = false
                aiTargetBitmap = null
            }
        )
    }
}

// ==================== SUBCOMPONENTS ====================

@Composable
fun TabPillButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) NeonCyan else Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
            Text(
                text = text,
                color = if (selected) CyberBg else TextGray,
                fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                fontSize = 11.5.sp,
                maxLines = 1
            )
        }
    }
}

/**
 * Hierarchical Breadcrumbs bar with interactive navigation chips.
 */
@Composable
fun BreadcrumbNavigationBar(
    projectName: String,
    currentPath: String,
    onNavigateRoot: () -> Unit,
    onNavigateCrumb: (String) -> Unit,
    onNavigateUp: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = CyberSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Root Chip
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onNavigateRoot() },
                shape = RoundedCornerShape(6.dp),
                color = if (currentPath.isEmpty()) NeonCyan.copy(alpha = 0.2f) else CyberSurfaceVariant,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (currentPath.isEmpty()) NeonCyan else Color.Transparent
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Home, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(14.dp))
                    Text(
                        text = "Root",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (currentPath.isEmpty()) NeonCyan else TextWhite
                    )
                }
            }

            // Path segments
            if (currentPath.isNotEmpty()) {
                val segments = currentPath.split("/").filter { it.isNotBlank() }
                var accumulated = ""
                segments.forEachIndexed { index, seg ->
                    accumulated = if (accumulated.isEmpty()) seg else "$accumulated/$seg"
                    val targetPath = accumulated
                    val isLast = index == segments.size - 1

                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onNavigateCrumb(targetPath) },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isLast) CyberYellow.copy(alpha = 0.2f) else CyberSurfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isLast) CyberYellow else Color.Transparent
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = CyberYellow, modifier = Modifier.size(13.dp))
                            Text(
                                text = seg,
                                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp,
                                color = if (isLast) CyberYellow else TextWhite
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Distinctive folder card for hierarchical directory navigation.
 */
@Composable
fun FolderCard(
    folder: WebProjectFile,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .testTag("folder_card_${folder.name.replace("[^a-zA-Z0-9]".toRegex(), "_")}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131D2E)),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberYellow.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CyberYellow.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = CyberYellow,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Folder • Ketuk untuk membuka",
                    color = CyberYellow.copy(alpha = 0.8f),
                    fontSize = 11.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CyberSurfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).clickable { onRename() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Ubah Nama", tint = NeonCyan, modifier = Modifier.size(17.dp))
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CyberRed.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberRed.copy(alpha = 0.35f)),
                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).clickable { onDelete() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus Folder", tint = CyberRed, modifier = Modifier.size(18.dp))
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CyberSurfaceVariant,
                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).clickable { onOpen() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Buka Folder", tint = TextWhite, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

/**
 * File card with rich metadata, image AI upscaler, code editor, preview, rename, and delete.
 */
@Composable
fun WebFileCard(
    file: WebProjectFile,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onQuickSetIndex: () -> Unit,
    onPreview: () -> Unit,
    onDelete: () -> Unit,
    onAiUpscale: () -> Unit = {}
) {
    val isImage = remember(file.name) {
        val lower = file.name.lowercase()
        lower.endsWith(".png") || lower.endsWith(".jpg") ||
        lower.endsWith(".jpeg") || lower.endsWith(".webp")
    }

    val isEditable = remember(file.name) {
        val lower = file.name.lowercase()
        !isImage && !lower.endsWith(".zip") && !lower.endsWith(".apk") && !lower.endsWith(".jar") && !lower.endsWith(".dex")
    }

    val isHtml = remember(file.name) {
        val lower = file.name.lowercase()
        lower.endsWith(".html") || lower.endsWith(".htm")
    }

    val canSetAsIndex = remember(file.name) {
        val lower = file.name.lowercase()
        lower != "index.html" && (
            lower.endsWith(".html") || lower.endsWith(".htm") ||
            lower.startsWith("msf:") || !lower.contains(".")
        )
    }

    val fileTypeLabel = remember(file.name, isImage, isHtml) {
        val lower = file.name.lowercase()
        when {
            lower == "index.html" -> "ENTRY POINT"
            isHtml -> "HTML"
            lower.endsWith(".css") -> "CSS"
            lower.endsWith(".js") || lower.endsWith(".ts") -> "JAVASCRIPT"
            lower.endsWith(".json") -> "JSON"
            lower.endsWith(".kts") || lower.endsWith(".gradle") -> "GRADLE SCRIPT"
            lower.endsWith(".properties") -> "CONFIG"
            lower.endsWith(".xml") -> "XML"
            lower.endsWith(".md") -> "MARKDOWN"
            isImage -> "GAMBAR"
            lower.endsWith(".zip") -> "ARCHIVE"
            else -> "BERKAS"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("file_card_${file.name.replace("[^a-zA-Z0-9]".toRegex(), "_")}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (file.name.equals("index.html", ignoreCase = true)) NeonCyan.copy(alpha = 0.6f) else CyberBorder
        )
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Row 1: File icon, Name, Tag/Size & Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val icon = when {
                    isHtml -> Icons.Default.Description
                    file.name.endsWith(".css", ignoreCase = true) -> Icons.Default.ColorLens
                    file.name.endsWith(".js", ignoreCase = true) || file.name.endsWith(".ts", ignoreCase = true) -> Icons.Default.Code
                    isImage -> Icons.Default.Image
                    file.name.endsWith(".zip", ignoreCase = true) -> Icons.Default.Archive
                    file.name.endsWith(".kts", ignoreCase = true) || file.name.endsWith(".gradle", ignoreCase = true) -> Icons.Default.Build
                    file.name.endsWith(".properties", ignoreCase = true) || file.name.endsWith(".json", ignoreCase = true) -> Icons.Default.Settings
                    else -> Icons.Default.InsertDriveFile
                }
                val iconTint = when {
                    file.name.equals("index.html", ignoreCase = true) -> NeonCyan
                    isHtml -> NeonCyan
                    file.name.endsWith(".css", ignoreCase = true) -> ElectricBlue
                    file.name.endsWith(".js", ignoreCase = true) || file.name.endsWith(".ts", ignoreCase = true) -> NeonGreen
                    isImage -> Color(0xFFFF4081)
                    file.name.endsWith(".zip", ignoreCase = true) -> CyberYellow
                    file.name.endsWith(".kts", ignoreCase = true) || file.name.endsWith(".gradle", ignoreCase = true) -> CyberYellow
                    file.name.endsWith(".properties", ignoreCase = true) || file.name.endsWith(".json", ignoreCase = true) -> ElectricBlue
                    else -> TextGray
                }

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = file.name,
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (file.name.equals("index.html", ignoreCase = true)) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = NeonGreen.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = "UTAMA",
                                    color = NeonGreen,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = formatFileSize(file.sizeBytes),
                            color = TextGray,
                            fontSize = 11.5.sp
                        )
                        Text(
                            text = "•",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = CyberSurfaceVariant
                        ) {
                            Text(
                                text = fileTypeLabel,
                                color = iconTint,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                        if (isImage) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = CyberYellow.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "4K READY",
                                    color = CyberYellow,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CyberRed.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberRed.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onDelete() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus Berkas", tint = CyberRed, modifier = Modifier.size(19.dp))
                    }
                }
            }

            // Row 2: Action Buttons (Smoothly scrollable, full 40dp touch target, perfectly aligned)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isImage) {
                    Button(
                        onClick = onAiUpscale,
                        modifier = Modifier.height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2C1608),
                            contentColor = CyberYellow
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberYellow.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Real-ESRGAN 4K", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                if (isEditable) {
                    Button(
                        onClick = onEdit,
                        modifier = Modifier.height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberSurfaceVariant,
                            contentColor = NeonCyan
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit Kode", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                if (isHtml) {
                    Button(
                        onClick = onPreview,
                        modifier = Modifier.height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0F2B20),
                            contentColor = NeonGreen
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pratinjau", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                FilledTonalButton(
                    onClick = onRename,
                    modifier = Modifier.height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = CyberSurfaceVariant,
                        contentColor = TextWhite
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ubah Nama", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                if (canSetAsIndex) {
                    FilledTonalButton(
                        onClick = onQuickSetIndex,
                        modifier = Modifier.height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = NeonCyan.copy(alpha = 0.12f),
                            contentColor = NeonCyan
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Jadikan index.html", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Notice only if file name is raw Android SAF identifier (e.g. msf:1234)
            if (file.name.startsWith("msf:", ignoreCase = true)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CyberYellow.copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberYellow.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = CyberYellow, modifier = Modifier.size(14.dp))
                        Text(
                            text = "Berkas SAF: gunakan 'Ubah Nama' atau 'Jadikan index.html' untuk ekstensi web yang valid.",
                            color = CyberYellow,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Project Card for the "Daftar Projek" list mode.
 */
@Composable
fun ProjectListItemCard(
    project: Project,
    isCurrent: Boolean,
    onOpen: () -> Unit,
    onEditConfig: () -> Unit,
    onBuild: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("project_item_${project.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isCurrent) NeonCyan else CyberBorder
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_app_logo),
                    contentDescription = "Project Icon",
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, if (isCurrent) NeonCyan else CyberBorder, RoundedCornerShape(10.dp))
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = TextWhite
                        )
                        if (isCurrent) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = NeonCyan.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "DIBUKA",
                                    color = NeonCyan,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${project.packageName} • v${project.versionName} • API ${project.targetSdk}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray
                    )
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus Projek", tint = CyberRed, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action buttons row (Unclipped, balanced weights, touch-friendly 44dp height)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpen,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCurrent) CyberSurfaceVariant else NeonCyan,
                        contentColor = if (isCurrent) NeonCyan else CyberBg
                    ),
                    border = if (isCurrent) androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)) else null,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Berkas", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                FilledTonalButton(
                    onClick = onEditConfig,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = CyberSurfaceVariant,
                        contentColor = TextWhite
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Config", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = onBuild,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0F2B20),
                        contentColor = NeonGreen
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Android, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Build", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CreateFolderDialog(
    targetFolder: String,
    onDismiss: () -> Unit,
    onCreate: (folderName: String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = CyberYellow)
                Text("Buat Folder Baru", color = CyberYellow, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Lokasi target: ${if (targetFolder.isEmpty()) "Root" else "Root/$targetFolder"}",
                    color = TextGray,
                    fontSize = 11.sp
                )
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Nama Folder") },
                    placeholder = { Text("contoh: assets, css, js, images") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SuggestionChip(onClick = { folderName = "assets" }, label = { Text("assets", fontSize = 10.sp) })
                    SuggestionChip(onClick = { folderName = "css" }, label = { Text("css", fontSize = 10.sp) })
                    SuggestionChip(onClick = { folderName = "js" }, label = { Text("js", fontSize = 10.sp) })
                    SuggestionChip(onClick = { folderName = "images" }, label = { Text("images", fontSize = 10.sp) })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (folderName.isNotBlank()) {
                        onCreate(folderName.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyberYellow, contentColor = CyberBg)
            ) {
                Text("BUAT FOLDER", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("BATAL", color = TextGray) }
        },
        containerColor = CyberSurface
    )
}

@Composable
fun CreateFileDialog(
    targetFolder: String = "",
    onDismiss: () -> Unit,
    onCreate: (fileName: String, content: String) -> Unit
) {
    var fileName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.NoteAdd, contentDescription = null, tint = NeonCyan)
                Text("Tambah Berkas Baru", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Lokasi target: ${if (targetFolder.isEmpty()) "Root" else "Root/$targetFolder"}",
                    color = TextGray,
                    fontSize = 11.sp
                )
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("Nama Berkas") },
                    placeholder = { Text("contoh: style.css, script.js, page.html") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SuggestionChip(onClick = { fileName = "index.html" }, label = { Text("index.html", fontSize = 10.sp) })
                    SuggestionChip(onClick = { fileName = "style.css" }, label = { Text("style.css", fontSize = 10.sp) })
                    SuggestionChip(onClick = { fileName = "script.js" }, label = { Text("script.js", fontSize = 10.sp) })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (fileName.isNotBlank()) {
                        onCreate(fileName.trim(), "")
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = CyberBg)
            ) {
                Text("BUAT BERKAS", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("BATAL", color = TextGray) }
        },
        containerColor = CyberSurface
    )
}

@Composable
fun RenameFileDialog(
    file: WebProjectFile,
    onDismiss: () -> Unit,
    onRename: (newName: String) -> Unit
) {
    var newName by remember { mutableStateOf(file.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, tint = NeonCyan)
                Text("Ubah Nama ${if (file.isDirectory) "Folder" else "Berkas"}", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Nama saat ini: ${file.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )

                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nama Baru") },
                    placeholder = { Text("contoh: index.html") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_input_field")
                )

                if (!file.isDirectory) {
                    Text(
                        text = "Tip Cepat: Ganti ekstensi web umum:",
                        fontSize = 11.sp,
                        color = TextMuted
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SuggestionChip(
                            onClick = { newName = "index.html" },
                            label = { Text("index.html", fontSize = 11.sp) }
                        )
                        SuggestionChip(
                            onClick = { newName = "style.css" },
                            label = { Text("style.css", fontSize = 11.sp) }
                        )
                        SuggestionChip(
                            onClick = { newName = "script.js" },
                            label = { Text("script.js", fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newName.isNotBlank()) {
                        onRename(newName.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = CyberBg),
                modifier = Modifier.testTag("confirm_rename_btn")
            ) {
                Text("SIMPAN NAMA", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("BATAL", color = TextGray) }
        },
        containerColor = CyberSurface
    )
}

@Composable
fun CodeEditorDialog(
    file: WebProjectFile,
    initialContent: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var content by remember { mutableStateOf(initialContent) }

    val charCount = content.length
    val lineCount = content.lines().size

    Dialog(
        onDismissRequest = onDismiss,
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
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup", tint = TextWhite)
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                fontWeight = FontWeight.Black,
                                color = NeonCyan,
                                fontSize = 16.sp,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = "$charCount karakter • $lineCount baris",
                                color = TextGray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // PASTE Button
                        Button(
                            onClick = {
                                val clipText = clipboardManager.getText()?.text
                                if (!clipText.isNullOrEmpty()) {
                                    content = if (content.isEmpty()) clipText else "$content\n$clipText"
                                    Toast.makeText(context, "Teks clipboard berhasil ditempel!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Clipboard kosong", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0D253D),
                                contentColor = NeonCyan
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("paste_code_btn")
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PASTE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        // SIMPAN Button
                        Button(
                            onClick = { onSave(content) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan,
                                contentColor = CyberBg
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("save_code_btn")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SIMPAN", fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }
                    }
                }

                // Editor Text Area
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .testTag("code_editor_text_area"),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = TextWhite,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF091220),
                        unfocusedContainerColor = Color(0xFF091220),
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = CyberBorder
                    )
                )
            }
        }
    }
}

@Composable
fun WebPreviewDialog(
    project: Project,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = CyberBg
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberSurface)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Tutup", tint = TextWhite)
                        }
                        Text(
                            text = "PREVIEW: ${project.name}",
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.allowFileAccess = true
                            settings.allowContentAccess = true
                            settings.allowFileAccessFromFileURLs = true
                            settings.allowUniversalAccessFromFileURLs = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            }

                            val projectDir = java.io.File(ctx.filesDir, "projects/${project.folderName}")
                            
                            // Ensure neo_bridge.js and neo_realesrgan.js exist in project directory
                            try {
                                val bridgeJs = java.io.File(projectDir, "neo_bridge.js")
                                if (!bridgeJs.exists()) {
                                    bridgeJs.writeText(com.example.builder.ApkBuilder.generateNeoBridgeJs())
                                }
                                val esrganJs = java.io.File(projectDir, "neo_realesrgan.js")
                                if (!esrganJs.exists()) {
                                    esrganJs.writeText(com.example.builder.ApkBuilder.generateRealEsrganJs())
                                }
                            } catch (ignored: Exception) {}

                            // Inject Native AndroidBridge and NeoAndroid
                            val bridge = com.example.bridge.AndroidBridge(ctx, project.name, project.packageName)
                            addJavascriptInterface(bridge, "AndroidBridge")
                            addJavascriptInterface(bridge, "NeoAndroid")

                            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                                bridge.handleDownload(url, contentDisposition, mimeType, userAgent, this)
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                    if (url == null) return false
                                    if (url.startsWith("file:///") || url.startsWith("about:")) return false
                                    if (url.startsWith("blob:") || url.startsWith("data:")) {
                                        bridge.handleDownload(url, null, null, null, view)
                                        return true
                                    }
                                    val lower = url.lowercase()
                                    if (lower.endsWith(".apk") || lower.endsWith(".zip") || lower.endsWith(".pdf") ||
                                        lower.endsWith(".bin") || lower.contains("download=true")) {
                                        bridge.handleDownload(url, null, null, null, view)
                                        return true
                                    }
                                    if (url.startsWith("http://") || url.startsWith("https://")) return false
                                    return try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        ctx.startActivity(intent)
                                        true
                                    } catch (e: Exception) {
                                        Toast.makeText(ctx, "Tidak ada aplikasi untuk membuka: $url", Toast.LENGTH_SHORT).show()
                                        true
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    // Inject bridge scripts if not in HTML
                                    view?.evaluateJavascript(com.example.builder.ApkBuilder.generateNeoBridgeJs(), null)
                                    view?.evaluateJavascript(com.example.builder.ApkBuilder.generateRealEsrganJs(), null)
                                }
                            }

                            webChromeClient = object : android.webkit.WebChromeClient() {
                                override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                                    android.util.Log.d("WebPreview", consoleMessage?.message() ?: "")
                                    return true
                                }
                                override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                                    request?.grant(request.resources)
                                }
                            }

                            val indexFile = java.io.File(projectDir, "index.html")
                            if (indexFile.exists()) {
                                loadUrl("file://${indexFile.absolutePath}")
                            } else {
                                val html = com.example.builder.ApkBuilder.generateDefaultIndexHtml(project.name)
                                loadDataWithBaseURL("file://${projectDir.absolutePath}/", html, "text/html", "utf-8", null)
                            }
                        }
                    }
                )
            }
        }
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val df = java.text.DecimalFormat("#,##0.#")
    return when {
        bytes >= 1024 * 1024 -> "${df.format(bytes / (1024.0 * 1024.0))} MB"
        bytes >= 1024 -> "${df.format(bytes / 1024.0)} KB"
        else -> "$bytes B"
    }
}
