package com.example.ui.screens

import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.WebProjectFile
import com.example.ui.MainViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val selectedProject by viewModel.selectedProject.collectAsState()
    val files by viewModel.currentFiles.collectAsState()
    val activeFile by viewModel.activeEditingFile.collectAsState()
    val fileContent by viewModel.fileContent.collectAsState()

    var showNewFileDialog by remember { mutableStateOf(false) }
    var fileToRename by remember { mutableStateOf<WebProjectFile?>(null) }
    var showWebPreview by remember { mutableStateOf(false) }

    val hasIndexHtml = remember(files) {
        files.any { it.name.equals("index.html", ignoreCase = true) || it.name.equals("index.htm", ignoreCase = true) }
    }

    val isImporting by viewModel.isImporting.collectAsState()

    // File picker launcher for uploading HTML, CSS, JS, ZIP
    val importFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importFiles(uris, context.contentResolver) { count ->
                Toast.makeText(context, "✓ $count berkas berhasil diimpor!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importSingleFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importFiles(listOf(uri), context.contentResolver) { count ->
                Toast.makeText(context, "✓ $count berkas berhasil diimpor!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (activeFile != null) {
        BackHandler { viewModel.closeEditor() }
    } else if (showWebPreview) {
        BackHandler { showWebPreview = false }
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
                        text = "PROJECT EXPLORER",
                        color = NeonCyan,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "${selectedProject?.name ?: "Pilih Proyek"} (${files.size} berkas)",
                        color = TextGray,
                        fontSize = 11.sp
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = { viewModel.selectTab(0) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali ke Home", tint = TextWhite)
                }
            },
            actions = {
                IconButton(onClick = {
                    importFilesLauncher.launch(arrayOf("*/*"))
                }) {
                    Icon(Icons.Default.UploadFile, contentDescription = "Upload / Import Berkas", tint = NeonGreen)
                }
                IconButton(onClick = { showNewFileDialog = true }) {
                    Icon(Icons.Default.NoteAdd, contentDescription = "Tambah Berkas Baru", tint = NeonCyan)
                }
                IconButton(onClick = { showWebPreview = true }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Preview Web", tint = NeonCyan, modifier = Modifier.size(28.dp))
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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Upload / Import Action Banner
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { importFilesLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("upload_html_files_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonGreen.copy(alpha = 0.15f),
                            contentColor = NeonGreen
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Upload HTML / ZIP", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { showNewFileDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberSurfaceVariant,
                            contentColor = NeonCyan
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Berkas Baru", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // Warning banner if index.html is missing
            if (!hasIndexHtml) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("index_html_warning_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF261019)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberRed.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = CyberRed,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "PERINGATAN: index.html Belum Ditemukan!",
                                    color = CyberRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Root: ${selectedProject?.folderName ?: "proyek"}",
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
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("BUAT", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Root Folder Info
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Root: ${selectedProject?.folderName ?: ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Text(
                        text = "${files.size} berkas web",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan
                    )
                }
            }

            // File Cards List
            if (files.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CyberSurface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Belum ada berkas web", fontWeight = FontWeight.Bold, color = TextWhite)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Klik tombol 'Upload HTML / ZIP' di atas untuk mengimpor berkas dari HP.", color = TextGray, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            } else {
                items(files) { file ->
                    WebFileCard(
                        file = file,
                        onEdit = { viewModel.openFile(file) },
                        onRename = { fileToRename = file },
                        onQuickSetIndex = {
                            viewModel.renameFile(file, "index.html")
                            Toast.makeText(context, "${file.name} diubah menjadi index.html!", Toast.LENGTH_SHORT).show()
                        },
                        onPreview = { showWebPreview = true },
                        onDelete = { viewModel.deleteFile(file) }
                    )
                }
            }
        }
    }

    // Rename File Dialog
    if (fileToRename != null) {
        RenameFileDialog(
            file = fileToRename!!,
            onDismiss = { fileToRename = null },
            onRename = { newName ->
                viewModel.renameFile(fileToRename!!, newName)
                fileToRename = null
                Toast.makeText(context, "Nama berkas berhasil diubah!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Code Editor Dialog
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

    // Web Live Preview Dialog
    if (showWebPreview && selectedProject != null) {
        WebPreviewDialog(
            project = selectedProject!!,
            onDismiss = { showWebPreview = false }
        )
    }

    // New File Dialog
    if (showNewFileDialog) {
        CreateFileDialog(
            onDismiss = { showNewFileDialog = false },
            onCreate = { fileName, content ->
                viewModel.createFile(fileName, content)
                showNewFileDialog = false
            }
        )
    }
}

@Composable
fun WebFileCard(
    file: WebProjectFile,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onQuickSetIndex: () -> Unit,
    onPreview: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("file_card_${file.name.replace("[^a-zA-Z0-9]".toRegex(), "_")}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val icon = when {
                    file.isDirectory -> Icons.Default.Folder
                    file.name.endsWith(".html") || file.name.endsWith(".htm") -> Icons.Default.Description
                    file.name.endsWith(".css") -> Icons.Default.ColorLens
                    file.name.endsWith(".js") -> Icons.Default.Code
                    file.name.endsWith(".png") || file.name.endsWith(".jpg") -> Icons.Default.Image
                    else -> Icons.Default.InsertDriveFile
                }
                val iconTint = when {
                    file.isDirectory -> CyberYellow
                    file.name.endsWith(".html") -> NeonCyan
                    file.name.endsWith(".css") -> ElectricBlue
                    file.name.endsWith(".js") -> NeonGreen
                    else -> TextGray
                }

                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(26.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        color = TextWhite,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val sizeText = if (file.isDirectory) "Folder" else formatFileSize(file.sizeBytes)
                    Text(
                        text = sizeText,
                        color = TextGray,
                        fontSize = 11.sp
                    )
                }

                // Action buttons: Rename, Edit, Delete
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onRename, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Ubah Nama", tint = NeonCyan, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextWhite, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onPreview, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Visibility, contentDescription = "Lihat", tint = TextGray, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus", tint = CyberRed, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Quick suggestion banner if file has unknown name like "msf:..."
            if (file.name.startsWith("msf:", ignoreCase = true) || (!file.name.endsWith(".html") && !file.name.endsWith(".css") && !file.name.endsWith(".js"))) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Berkas belum berekstensi web:", color = TextMuted, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(
                            onClick = onQuickSetIndex,
                            label = { Text("Ubah ke index.html", fontSize = 10.sp, color = NeonCyan) }
                        )
                        AssistChip(
                            onClick = onRename,
                            label = { Text("Ganti Nama", fontSize = 10.sp, color = TextWhite) }
                        )
                    }
                }
            }
        }
    }
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
                Text("Ubah Nama Berkas", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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

                Text(
                    text = "Tip Cepat: Klik tombol pintasan di bawah untuk mengganti:",
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
                // Top Header matching Screenshot
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
                        // PASTE Button matching Screenshot
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

                        // SIMPAN Button matching Screenshot
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
    project: com.example.data.Project,
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
                            settings.allowFileAccess = true
                            settings.allowContentAccess = true
                            webViewClient = WebViewClient()
                            val projectDir = java.io.File(ctx.filesDir, "projects/${project.folderName}")
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

@Composable
fun CreateFileDialog(
    onDismiss: () -> Unit,
    onCreate: (fileName: String, content: String) -> Unit
) {
    var fileName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Berkas Baru", color = NeonCyan, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("Nama Berkas") },
                    placeholder = { Text("contoh: style.css, script.js, page.html") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val df = java.text.DecimalFormat("#,##0.#")
    return when {
        bytes >= 1024 * 1024 -> "${df.format(bytes / (1024.0 * 1024.0))} MB"
        bytes >= 1024 -> "${df.format(bytes / 1024.0)} KB"
        else -> "$bytes B"
    }
}
