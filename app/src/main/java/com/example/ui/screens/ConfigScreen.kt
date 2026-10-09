package com.example.ui.screens

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.MainViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val project by viewModel.selectedProject.collectAsState()
    val customIcon by viewModel.customIcon.collectAsState()

    var appName by remember(project) { mutableStateOf(project?.name ?: "NEO DOWNLOADER") }
    var packageName by remember(project) { mutableStateOf(project?.packageName ?: "com.neo.downloader") }
    var versionName by remember(project) { mutableStateOf(project?.versionName ?: "1.0.1") }
    var versionCode by remember(project) { mutableStateOf(project?.versionCode?.toString() ?: "1") }

    // Config fields matching screenshots 1, 2, 4
    var buildType by remember(project) { mutableStateOf(project?.buildType ?: "release") }
    var minSdk by remember(project) { mutableStateOf(project?.minSdk ?: 21) }
    var targetSdk by remember(project) { mutableStateOf(project?.targetSdk ?: 34) }
    var orientation by remember(project) { mutableStateOf(project?.orientation ?: "portrait") }
    var fullscreen by remember(project) { mutableStateOf(project?.fullscreen ?: false) }
    var nativeBridge by remember(project) { mutableStateOf(project?.nativeBridge ?: true) }
    var domStorage by remember(project) { mutableStateOf(project?.domStorage ?: true) }

    // Permissions state powered by PermissionManager
    var selectedPermissions by remember(project) {
        mutableStateOf(com.example.data.PermissionManager.parsePermissions(project?.permissions))
    }

    var showRealEsrganDialog by remember { mutableStateOf(false) }
    var iconToUpscale by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        viewModel.setCustomIcon(bitmap)
                        Toast.makeText(context, "Logo kustom diterapkan!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (ignored: Exception) {}
        }
    }

    fun saveConfig() {
        val vCode = versionCode.toIntOrNull() ?: 1
        val permsString = selectedPermissions.joinToString(",")

        viewModel.saveFullConfig(
            appName = appName,
            packageName = packageName,
            versionName = versionName,
            versionCode = vCode,
            orientation = orientation,
            fullscreen = fullscreen,
            minSdk = minSdk,
            targetSdk = targetSdk,
            buildType = buildType,
            nativeBridge = nativeBridge,
            domStorage = domStorage,
            permissions = permsString
        )
        Toast.makeText(context, "Konfigurasi berhasil disimpan!", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBg)
    ) {
        // Top App Bar matching Screenshots 1, 2, 4
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "APP CONFIGURATION",
                        color = NeonCyan,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Package, Manifest & Android Settings",
                        color = TextGray,
                        fontSize = 11.sp
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = { viewModel.selectTab(0) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = TextWhite)
                }
            },
            actions = {
                Button(
                    onClick = { saveConfig() },
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .testTag("save_config_top_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = CyberBg),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Simpan", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = CyberBg)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Identity Card (Name, Package, Logo)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
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
                            Icon(Icons.Default.Badge, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                            Text(
                                text = "IDENTITAS APLIKASI & LOGO",
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan,
                                fontSize = 13.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.5.dp, NeonCyan, RoundedCornerShape(14.dp))
                                    .clickable {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (customIcon != null) {
                                    Image(
                                        bitmap = customIcon!!.asImageBitmap(),
                                        contentDescription = "Custom Icon",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_app_logo),
                                        contentDescription = "Default Icon",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CyberSurfaceVariant,
                                            contentColor = TextWhite
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Ganti Logo", fontSize = 12.sp)
                                    }

                                    Button(
                                        onClick = {
                                            val iconBmp = customIcon ?: BitmapFactory.decodeResource(context.resources, R.drawable.ic_app_logo)
                                            if (iconBmp != null) {
                                                iconToUpscale = iconBmp
                                                showRealEsrganDialog = true
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF2C1608),
                                            contentColor = CyberYellow
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberYellow.copy(alpha = 0.6f)),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("AI 4K", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = appName,
                            onValueChange = { appName = it },
                            label = { Text("Nama Aplikasi") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = packageName,
                            onValueChange = { packageName = it },
                            label = { Text("Package Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = versionName,
                                onValueChange = { versionName = it },
                                label = { Text("Version Name") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = versionCode,
                                onValueChange = { versionCode = it },
                                label = { Text("Version Code") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }
                }
            }

            // Card: BUILD & KEYSTORE CONFIG (Screenshot 2)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                            Icon(Icons.Default.VpnKey, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                            Text(
                                text = "BUILD & KEYSTORE CONFIG",
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan,
                                fontSize = 13.sp
                            )
                        }

                        Text("Tipe Tanda Tangan APK", style = MaterialTheme.typography.bodySmall, color = TextGray)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SelectableTabButton(
                                text = "Debug Build",
                                selected = buildType == "debug",
                                modifier = Modifier.weight(1f),
                                onClick = { buildType = "debug" }
                            )
                            SelectableTabButton(
                                text = "Release Build",
                                selected = buildType == "release",
                                modifier = Modifier.weight(1f),
                                onClick = { buildType = "release" }
                            )
                        }
                    }
                }
            }

            // Card: ANDROID SDK CUSTOMIZER (KOMPATIBILITAS HP) (Screenshot 2)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Android, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                            Text(
                                text = "ANDROID SDK CUSTOMIZER (KOMPATIBILITAS HP)",
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan,
                                fontSize = 13.sp
                            )
                        }

                        // Minimum SDK (minSdkVersion)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Minimum SDK (minSdkVersion)",
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Versi Android terlama yang diizinkan untuk menginstall APK:",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextGray
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                SelectableTabButton(
                                    text = "Android 5.0 (API 21)",
                                    selected = minSdk == 21,
                                    modifier = Modifier.weight(1f),
                                    onClick = { minSdk = 21 }
                                )
                                SelectableTabButton(
                                    text = "Android 7.0 (API 24)",
                                    selected = minSdk == 24,
                                    modifier = Modifier.weight(1f),
                                    onClick = { minSdk = 24 }
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                SelectableTabButton(
                                    text = "Android 8.0 (API 26)",
                                    selected = minSdk == 26,
                                    modifier = Modifier.weight(1f),
                                    onClick = { minSdk = 26 }
                                )
                                SelectableTabButton(
                                    text = "Android 10 (API 29)",
                                    selected = minSdk == 29,
                                    modifier = Modifier.weight(1f),
                                    onClick = { minSdk = 29 }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Target SDK (targetSdkVersion)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Target SDK (targetSdkVersion)",
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Target optimasi sistem Android:",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextGray
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                SelectableTabButton(
                                    text = "Android 16 (API 36)",
                                    selected = targetSdk == 36,
                                    modifier = Modifier.weight(1f),
                                    onClick = { targetSdk = 36 }
                                )
                                SelectableTabButton(
                                    text = "Android 15 (API 35)",
                                    selected = targetSdk == 35,
                                    modifier = Modifier.weight(1f),
                                    onClick = { targetSdk = 35 }
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                SelectableTabButton(
                                    text = "Android 14 (API 34)",
                                    selected = targetSdk == 34,
                                    modifier = Modifier.weight(1f),
                                    onClick = { targetSdk = 34 }
                                )
                                SelectableTabButton(
                                    text = "Android 13 (API 33)",
                                    selected = targetSdk == 33,
                                    modifier = Modifier.weight(1f),
                                    onClick = { targetSdk = 33 }
                                )
                            }
                        }
                    }
                }
            }

            // Card: KONFIGURASI LAYAR & WEBVIEW RUNTIME (Screenshot 1)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Layers, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                            Text(
                                text = "KONFIGURASI LAYAR & WEBVIEW RUNTIME",
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan,
                                fontSize = 13.sp
                            )
                        }

                        // Orientasi Layar (Screen Orientation)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Orientasi Layar (Screen Orientation)",
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                fontSize = 13.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                SelectableTabButton(
                                    text = "Portrait",
                                    selected = orientation == "portrait",
                                    modifier = Modifier.weight(1f),
                                    onClick = { orientation = "portrait" }
                                )
                                SelectableTabButton(
                                    text = "Landscape",
                                    selected = orientation == "landscape",
                                    modifier = Modifier.weight(1f),
                                    onClick = { orientation = "landscape" }
                                )
                                SelectableTabButton(
                                    text = "Auto",
                                    selected = orientation == "sensor",
                                    modifier = Modifier.weight(1f),
                                    onClick = { orientation = "sensor" }
                                )
                            }
                        }

                        // Mode Layar Penuh (Fullscreen)
                        SwitchSettingRow(
                            title = "Mode Layar Penuh (Fullscreen)",
                            subtitle = "Sembunyikan status bar sistem saat aplikasi dijalankan",
                            checked = fullscreen,
                            onCheckedChange = { fullscreen = it }
                        )

                        // JavaScript Native Bridge
                        SwitchSettingRow(
                            title = "JavaScript Native Bridge (Android Bridge)",
                            subtitle = "Akses fitur Toast, Download, File & Clipboard via window.NeoAndroid",
                            checked = nativeBridge,
                            onCheckedChange = { nativeBridge = it }
                        )

                        // DOM Storage & LocalDatabase
                        SwitchSettingRow(
                            title = "DOM Storage & LocalDatabase",
                            subtitle = "Mengizinkan localStorage dan IndexedDB di WebView",
                            checked = domStorage,
                            onCheckedChange = { domStorage = it }
                        )
                    }
                }
            }

            // Card: PERIZINAN ANDROID (PERMISSIONS MANAGER)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                                Text(
                                    text = "ANDROID PERMISSIONS MANAGER",
                                    fontWeight = FontWeight.Bold,
                                    color = NeonCyan,
                                    fontSize = 13.sp
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = NeonCyan.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "${selectedPermissions.size} Izin Aktif",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 11.sp,
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Text(
                            text = "Konfigurasikan izin yang dicantumkan ke AndroidManifest.xml. Izin yang tidak dicentang akan otomatis difilter sehingga APK Anda bersih dan aman.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextGray,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )

                        // Quick buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    selectedPermissions = setOf("INTERNET", "ACCESS_NETWORK_STATE", "VIBRATE")
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text("Default Aman", fontSize = 11.sp, color = NeonCyan)
                            }
                            OutlinedButton(
                                onClick = {
                                    selectedPermissions = com.example.data.PermissionManager.DEFINITIONS.map { it.id }.toSet()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text("Pilih Semua", fontSize = 11.sp, color = TextWhite)
                            }
                        }

                        // Categories
                        com.example.data.PermissionCategory.values().forEach { category ->
                            val itemsInCategory = com.example.data.PermissionManager.DEFINITIONS.filter { it.category == category }
                            if (itemsInCategory.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = category.displayName.uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = NeonCyan,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.5.sp
                                )
                                itemsInCategory.forEach { def ->
                                    val isChecked = selectedPermissions.contains(def.id)
                                    PermissionItemRow(
                                        definition = def,
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            selectedPermissions = if (checked) {
                                                selectedPermissions + def.id
                                            } else {
                                                selectedPermissions - def.id
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // Clipboard Note
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CyberSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                                Column {
                                    Text(
                                        text = "Fitur Clipboard (Salin & Tempel)",
                                        fontWeight = FontWeight.Bold,
                                        color = TextWhite,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "Tersedia secara native melalui ClipboardManager dan polyfill JavaScript otomatis tanpa memerlukan deklarasi izin khusus di AndroidManifest.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextGray,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Save & Build Button
            item {
                Button(
                    onClick = {
                        saveConfig()
                        viewModel.buildApk()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("build_apk_from_config_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = CyberBg),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SIMPAN & BUILD APK", fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
            }
        }
    }

    if (showRealEsrganDialog && iconToUpscale != null) {
        com.example.ui.components.RealEsrganDialog(
            sourceBitmap = iconToUpscale!!,
            sourceFileName = "app_logo.png",
            viewModel = viewModel,
            onDismiss = {
                showRealEsrganDialog = false
                iconToUpscale = null
            }
        )
    }
}

@Composable
fun PermissionItemRow(
    definition: com.example.data.PermissionDefinition,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = definition.title,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    fontSize = 13.sp
                )
                if (definition.isSpecialAccess) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = androidx.compose.ui.graphics.Color(0xFFFF9100).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "Khusus",
                            color = androidx.compose.ui.graphics.Color(0xFFFF9100),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                } else if (definition.isRuntime) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = NeonCyan.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "Runtime",
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
                text = definition.description,
                style = MaterialTheme.typography.bodySmall,
                color = TextGray,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
            if (definition.note != null) {
                Text(
                    text = "⚠ " + definition.note,
                    color = androidx.compose.ui.graphics.Color(0xFFFFB74D),
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = NeonCyan,
                checkmarkColor = CyberBg,
                uncheckedColor = TextGray
            )
        )
    }
}

@Composable
fun SelectableTabButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) NeonCyan else CyberSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) NeonCyan else CyberBorder
        )
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
            Text(
                text = text,
                color = if (selected) CyberBg else TextGray,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SwitchSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextGray, fontSize = 11.sp, lineHeight = 14.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CyberBg,
                checkedTrackColor = NeonCyan,
                uncheckedThumbColor = TextGray,
                uncheckedTrackColor = CyberSurfaceVariant
            )
        )
    }
}

@Composable
fun PermissionCheckboxRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = TextGray, fontSize = 11.sp)
        }
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = NeonCyan,
                checkmarkColor = CyberBg,
                uncheckedColor = TextGray
            )
        )
    }
}
