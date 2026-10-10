package com.example.data

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.builder.ApkBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

class ProjectRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val projectDao = db.projectDao()
    private val buildHistoryDao = db.buildHistoryDao()
    private val hostedApkDao = db.hostedApkDao()

    val projectsFlow: Flow<List<Project>> = projectDao.getAllProjects()
    val buildsFlow: Flow<List<BuildHistoryItem>> = buildHistoryDao.getAllBuilds()
    val hostedApksFlow: Flow<List<HostedApk>> = hostedApkDao.getAllHosted()

    private val projectsRoot = File(context.filesDir, "projects").apply { mkdirs() }

    suspend fun initDefaultProjectsIfEmpty() = withContext(Dispatchers.IO) {
        val count = projectDao.getProjectCount()
        if (count == 0) {
            val neoDownloader = Project(
                name = "NEO DOWNLOADER",
                folderName = "neo_downloader",
                packageName = "com.neo.downloader",
                versionName = "1.0.1",
                versionCode = 1,
                description = "Aplikasi pengunduh berkas web modern dengan UI Cyberpunk",
                orientation = "portrait",
                minSdk = 21,
                targetSdk = 34,
                buildType = "release",
                nativeBridge = true,
                domStorage = true
            )
            projectDao.insertProject(neoDownloader)
            setupNeoDownloaderTemplate(neoDownloader)

            // Second starter project: Cyber Runner Game
            val gameProject = Project(
                name = "CYBER RUNNER",
                folderName = "cyber_runner",
                packageName = "com.neo.cyberrunner",
                versionName = "1.0.0",
                versionCode = 1,
                description = "Game arkade HTML5 Canvas 2D interaktif",
                orientation = "portrait",
                minSdk = 21,
                targetSdk = 34
            )
            projectDao.insertProject(gameProject)
            setupGameTemplate(gameProject)
        } else {
            val first = projectDao.getFirstProject()
            if (first != null) {
                val dir = getProjectDir(first.folderName)
                val indexFile = File(dir, "index.html")
                if (!indexFile.exists()) {
                    setupNeoDownloaderTemplate(first)
                }
            }
        }
    }

    fun getProjectDir(folderName: String): File {
        return File(projectsRoot, folderName).apply { mkdirs() }
    }

    suspend fun createProject(
        name: String,
        packageName: String,
        template: String
    ): Project = withContext(Dispatchers.IO) {
        val safeFolder = name.lowercase().replace("[^a-z0-9_]".toRegex(), "_") + "_" + System.currentTimeMillis() % 10000
        val p = Project(
            name = name,
            folderName = safeFolder,
            packageName = packageName.ifBlank { "com.neo." + name.lowercase().replace("[^a-z0-9]".toRegex(), "") },
            versionName = "1.0.1",
            versionCode = 1,
            targetSdk = 34,
            minSdk = 21
        )
        val id = projectDao.insertProject(p)
        val created = p.copy(id = id)
        when (template) {
            "downloader" -> setupNeoDownloaderTemplate(created)
            "game" -> setupGameTemplate(created)
            else -> setupBlankTemplate(created)
        }
        created
    }

    suspend fun updateProject(project: Project) = withContext(Dispatchers.IO) {
        projectDao.updateProject(project.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteProject(project: Project) = withContext(Dispatchers.IO) {
        val dir = getProjectDir(project.folderName)
        dir.deleteRecursively()
        projectDao.deleteProject(project)
    }

    fun listProjectFiles(project: Project): List<WebProjectFile> {
        val dir = getProjectDir(project.folderName)
        val list = mutableListOf<WebProjectFile>()
        fun scan(cur: File, rel: String) {
            val files = cur.listFiles() ?: return
            for (f in files) {
                if (f.name.startsWith(".")) continue
                val childRel = if (rel.isEmpty()) f.name else "$rel/${f.name}"
                list.add(
                    WebProjectFile(
                        name = f.name,
                        relativePath = childRel,
                        isDirectory = f.isDirectory,
                        sizeBytes = if (f.isDirectory) 0 else f.length(),
                        lastModified = f.lastModified()
                    )
                )
                if (f.isDirectory) {
                    scan(f, childRel)
                }
            }
        }
        scan(dir, "")
        return list.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
    }

    /**
     * Lists only direct items in the specified relative directory path for nested navigation.
     */
    fun listFilesAtDirectory(project: Project, subPath: String): List<WebProjectFile> {
        val projectDir = getProjectDir(project.folderName)
        val cleanSub = subPath.trim().removePrefix("/").removeSuffix("/")
        val targetDir = if (cleanSub.isEmpty()) projectDir else File(projectDir, cleanSub)
        if (!targetDir.exists() || !targetDir.isDirectory) return emptyList()

        val list = mutableListOf<WebProjectFile>()
        val files = targetDir.listFiles() ?: return emptyList()
        for (f in files) {
            if (f.name.startsWith(".")) continue
            val childRel = if (cleanSub.isEmpty()) f.name else "$cleanSub/${f.name}"
            list.add(
                WebProjectFile(
                    name = f.name,
                    relativePath = childRel,
                    isDirectory = f.isDirectory,
                    sizeBytes = if (f.isDirectory) calculateDirectorySize(f) else f.length(),
                    lastModified = f.lastModified()
                )
            )
        }
        // Folders first, then files alphabetically
        return list.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    private fun calculateDirectorySize(dir: File): Long {
        var size = 0L
        dir.walkTopDown().filter { it.isFile }.forEach { size += it.length() }
        return size
    }

    fun createFolder(project: Project, folderRelPath: String): Boolean {
        val cleanRel = folderRelPath.trim().removePrefix("/").removeSuffix("/")
        if (cleanRel.isBlank()) return false
        val f = File(getProjectDir(project.folderName), cleanRel)
        return f.mkdirs()
    }

    fun deleteFolder(project: Project, folderRelPath: String): Boolean {
        val f = File(getProjectDir(project.folderName), folderRelPath)
        return f.deleteRecursively()
    }

    fun getFile(project: Project, relativePath: String): File {
        return File(getProjectDir(project.folderName), relativePath)
    }

    fun getBitmap(project: Project, relativePath: String): Bitmap? {
        val f = getFile(project, relativePath)
        if (!f.exists()) return null
        return try {
            BitmapFactory.decodeFile(f.absolutePath)
        } catch (e: Exception) {
            null
        }
    }

    fun readFile(project: Project, relativePath: String): String {
        val f = File(getProjectDir(project.folderName), relativePath)
        return if (f.exists()) f.readText(Charsets.UTF_8) else ""
    }

    fun saveFile(project: Project, relativePath: String, content: String) {
        val f = File(getProjectDir(project.folderName), relativePath)
        f.parentFile?.mkdirs()
        f.writeText(content, Charsets.UTF_8)
    }

    fun renameFile(project: Project, oldRelPath: String, newName: String): Boolean {
        val projectDir = getProjectDir(project.folderName)
        val oldFile = File(projectDir, oldRelPath)
        if (!oldFile.exists()) return false

        val parentDir = oldFile.parentFile ?: projectDir
        val newFile = File(parentDir, newName.trim())
        return oldFile.renameTo(newFile)
    }

    fun deleteFile(project: Project, relativePath: String): Boolean {
        val f = File(getProjectDir(project.folderName), relativePath)
        return f.deleteRecursively()
    }

    suspend fun importFilesFromUris(
        project: Project,
        uris: List<Uri>,
        contentResolver: ContentResolver,
        targetSubFolder: String = ""
    ): Int = withContext(Dispatchers.IO) {
        val projectDir = getProjectDir(project.folderName)
        val cleanSub = targetSubFolder.trim().removePrefix("/").removeSuffix("/")
        val destDir = if (cleanSub.isEmpty()) projectDir else File(projectDir, cleanSub).apply { mkdirs() }
        var count = 0
        for (uri in uris) {
            try {
                var displayName = queryDisplayName(uri, contentResolver)
                if (displayName.isNullOrBlank() || displayName.startsWith("msf:", ignoreCase = true)) {
                    val mime = try { contentResolver.getType(uri) } catch (ignored: Exception) { null }
                    val ext = when {
                        mime?.contains("html") == true -> ".html"
                        mime?.contains("css") == true -> ".css"
                        mime?.contains("javascript") == true -> ".js"
                        mime?.contains("zip") == true -> ".zip"
                        mime?.contains("png") == true -> ".png"
                        mime?.contains("jpeg") == true -> ".jpg"
                        else -> ""
                    }
                    displayName = if (ext.isNotEmpty()) "imported_file$ext" else "index.html"
                }

                if (displayName.endsWith(".zip", ignoreCase = true)) {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        unzipStream(stream, destDir)
                        count++
                    }
                } else {
                    val targetFile = File(destDir, displayName)
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                        count++
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        count
    }

    private fun queryDisplayName(uri: Uri, contentResolver: ContentResolver): String? {
        var name: String? = null
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (ignored: Exception) {}
        return name ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun unzipStream(inputStream: InputStream, targetDir: File) {
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val f = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    f.mkdirs()
                } else {
                    f.parentFile?.mkdirs()
                    FileOutputStream(f).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                entry = zis.nextEntry
            }
        }
    }

    suspend fun buildApk(project: Project, customIcon: Bitmap? = null): BuildHistoryItem = withContext(Dispatchers.IO) {
        val projectDir = getProjectDir(project.folderName)
        val config = ProjectConfig(
            appName = project.name,
            packageName = project.packageName,
            versionName = project.versionName,
            versionCode = project.versionCode,
            orientation = project.orientation,
            fullscreen = project.fullscreen,
            minSdk = project.minSdk,
            targetSdk = project.targetSdk,
            buildType = project.buildType,
            nativeBridge = project.nativeBridge,
            domStorage = project.domStorage,
            permissions = project.permissions,
            enableDownloadBridge = project.enableDownloadBridge,
            downloadFolderPrimary = project.downloadFolderPrimary,
            downloadSubfolders = project.downloadSubfolders,
            showDownloadToast = project.showDownloadToast
        )

        if (customIcon != null) {
            val iconFile = File(projectDir, "icon.png")
            FileOutputStream(iconFile).use { fos ->
                customIcon.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
        }

        val apkFile = ApkBuilder.buildApk(
            context = context,
            config = config,
            projectDir = projectDir,
            customIconBitmap = customIcon
        )

        val historyItem = BuildHistoryItem(
            projectId = project.id,
            appName = project.name,
            packageName = project.packageName,
            versionName = project.versionName,
            apkPath = apkFile.absolutePath,
            fileSizeBytes = apkFile.length(),
            buildTimestamp = System.currentTimeMillis(),
            status = "READY"
        )

        val id = buildHistoryDao.insertBuild(historyItem)
        historyItem.copy(id = id)
    }

    suspend fun deleteBuild(item: BuildHistoryItem) = withContext(Dispatchers.IO) {
        val f = File(item.apkPath)
        if (f.exists()) f.delete()
        buildHistoryDao.deleteBuild(item)
    }

    fun installApk(item: BuildHistoryItem) {
        val file = File(item.apkPath)
        if (!file.exists()) {
            Toast.makeText(context, "Berkas APK tidak ditemukan!", Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                        Toast.makeText(context, "Aktifkan izin instalasi untuk melanjutkan", Toast.LENGTH_LONG).show()
                        return
                    }
                } catch (e: Exception) {
                    // Fallback to general security settings if app-specific settings not handled
                    try {
                        val secIntent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(secIntent)
                        return
                    } catch (ignored: Exception) {}
                }
            }
        }

        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "Tidak ada aplikasi pemasang paket (Package Installer) yang tersedia.", Toast.LENGTH_LONG).show()
            }
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Tidak ditemukan aplikasi pemasang paket di perangkat ini.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal meluncurkan installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun shareApk(item: BuildHistoryItem) {
        val file = File(item.apkPath)
        if (!file.exists()) {
            Toast.makeText(context, "Berkas APK tidak ditemukan untuk dibagikan", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Download ${item.appName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            val chooser = Intent.createChooser(intent, "Bagikan APK ${item.appName}").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(chooser)
            } else {
                Toast.makeText(context, "Tidak ada aplikasi yang dapat menerima berkas ini.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Aplikasi untuk berbagi tidak tersedia.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membagikan APK: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun uploadBuildApk(
        item: BuildHistoryItem,
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): HostedApk = withContext(Dispatchers.IO) {
        val file = File(item.apkPath)
        val hosted = ApkHostingManager.uploadApk(
            context = context,
            apkFile = file,
            appName = item.appName,
            versionName = item.versionName,
            packageName = item.packageName,
            onProgress = onProgress
        )
        val id = hostedApkDao.insertHosted(hosted)
        hosted.copy(id = id)
    }

    suspend fun uploadCustomApk(
        file: File,
        appName: String,
        versionName: String,
        packageName: String,
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): HostedApk = withContext(Dispatchers.IO) {
        val hosted = ApkHostingManager.uploadApk(
            context = context,
            apkFile = file,
            appName = appName,
            versionName = versionName,
            packageName = packageName,
            onProgress = onProgress
        )
        val id = hostedApkDao.insertHosted(hosted)
        hosted.copy(id = id)
    }

    suspend fun deleteHostedApk(item: HostedApk) = withContext(Dispatchers.IO) {
        hostedApkDao.deleteHosted(item)
    }

    private fun setupNeoDownloaderTemplate(project: Project) {
        val dir = getProjectDir(project.folderName)
        File(dir, "index.html").writeText(
            """
<!DOCTYPE html>
<html lang="id">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
  <title>NEO DOWNLOADER</title>
  <link rel="stylesheet" href="style.css">
</head>
<body>
  <div class="header">
    <div class="logo">⚡ NEO DOWNLOADER</div>
    <div class="badge">V1.0.1 PRO</div>
  </div>
  
  <div class="card">
    <h3>Unduh Berkas Online</h3>
    <p>Masukkan URL berkas media, video, atau dokumen untuk memulai unduhan langsung.</p>
    <div class="input-group">
      <input type="text" id="urlInput" placeholder="https://example.com/file.zip">
      <div class="action-buttons">
        <button type="button" class="btn-sub" onclick="pasteUrl()">📋 Tempel URL</button>
        <button type="button" class="btn-sub" onclick="copyUrl()">📑 Salin URL</button>
      </div>
    </div>
    <button class="btn" onclick="startDownload()">UNDUH SEKARANG</button>
  </div>

  <div class="card">
    <h3>Uji Download Bridge (MediaStore Native)</h3>
    <p>Simpan file langsung ke <b>Download/Neo Downloader/mp4/</b> dan <b>Download/Neo Downloader/mp3/</b> lewat JavaScript native bridge ber-chunk:</p>
    <div class="action-buttons">
      <button type="button" class="btn" style="background:#00E5FF;color:#060D17;" onclick="testNativeSaveMp4()">🎬 Simpan ke /mp4/ (MediaStore)</button>
      <button type="button" class="btn" style="background:#00E676;color:#060D17;" onclick="testNativeSaveMp3()">🎵 Simpan ke /mp3/ (MediaStore)</button>
    </div>
    <div id="bridgeStatus" style="font-size:12px;color:#A0B2C6;margin-top:8px;word-break:break-all;">Status Bridge: Siap diuji</div>
  </div>

  <div class="card">
    <h3>Uji Fitur Native & Blob Engine</h3>
    <p>Uji unduhan Blob URL berformat PNG asli tanpa .bin, Picture-in-Picture, dan izin Overlay:</p>
    <div class="action-buttons" style="flex-direction: column;">
      <button type="button" class="btn-sub" style="padding: 10px;" onclick="downloadTestBlobImage()">🖼️ Unduh Gambar Canvas (Blob PNG Asli)</button>
      <button type="button" class="btn-sub" style="padding: 10px;" onclick="testPipMode()">📺 Buka Mode Picture-in-Picture (PiP)</button>
      <button type="button" class="btn-sub" style="padding: 10px;" onclick="testOverlayPermission()">🪟 Pengaturan Izin Overlay Sistem</button>
    </div>
  </div>

  <div class="card">
    <h3>Riwayat Unduhan Aktif</h3>
    <div class="download-item">
      <div class="icon">📦</div>
      <div class="info">
        <div class="name">cyber_asset_pack_v2.zip</div>
        <div class="progress-bar"><div class="progress" style="width: 78%;"></div></div>
        <div class="meta">78% • 14.2 MB / 18.2 MB • 2.4 MB/s</div>
      </div>
    </div>
  </div>

  <script src="script.js"></script>
</body>
</html>
            """.trimIndent(),
            Charsets.UTF_8
        )

        File(dir, "style.css").writeText(
            """
* { box-sizing: border-box; margin: 0; padding: 0; }
body {
  background: #060D17;
  color: #FFFFFF;
  font-family: -apple-system, sans-serif;
  padding: 16px;
  min-height: 100vh;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0 20px 0;
  border-bottom: 1px solid rgba(0, 229, 255, 0.2);
  margin-bottom: 20px;
}
.logo {
  font-size: 20px;
  font-weight: 800;
  color: #00E5FF;
  letter-spacing: 1px;
}
.badge {
  background: rgba(0, 229, 255, 0.2);
  color: #00E5FF;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: bold;
}
.card {
  background: #111C2E;
  border: 1px solid rgba(0, 229, 255, 0.2);
  border-radius: 16px;
  padding: 20px;
  margin-bottom: 16px;
}
.card h3 {
  color: #00E5FF;
  font-size: 16px;
  margin-bottom: 8px;
}
.card p {
  color: #8C9BAE;
  font-size: 13px;
  margin-bottom: 16px;
  line-height: 1.5;
}
.input-group input {
  width: 100%;
  padding: 12px 14px;
  background: #0B1320;
  border: 1px solid #1E2D44;
  border-radius: 10px;
  color: #FFF;
  font-size: 14px;
  margin-bottom: 10px;
}
.action-buttons {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}
.btn-sub {
  flex: 1;
  padding: 8px 12px;
  background: #111C2E;
  color: #00E5FF;
  border: 1px solid rgba(0, 229, 255, 0.3);
  border-radius: 8px;
  font-size: 12px;
  cursor: pointer;
  font-weight: 600;
  transition: all 0.2s ease;
}
.btn-sub:active {
  background: #1B2B44;
  transform: scale(0.98);
}
.btn {
  width: 100%;
  padding: 12px;
  background: #00E5FF;
  color: #060D17;
  font-weight: bold;
  font-size: 14px;
  border: none;
  border-radius: 10px;
  cursor: pointer;
}
.download-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
}
.download-item .icon { font-size: 24px; }
.download-item .info { flex: 1; }
.download-item .name { font-size: 13px; font-weight: 600; color: #E0E8F0; margin-bottom: 6px; }
.progress-bar { background: #18273E; height: 6px; border-radius: 3px; overflow: hidden; margin-bottom: 4px; }
.progress-bar .progress { height: 100%; background: #00E5FF; border-radius: 3px; }
.download-item .meta { font-size: 11px; color: #7B8D9F; }
            """.trimIndent(),
            Charsets.UTF_8
        )

        File(dir, "script.js").writeText(
            """
function pasteUrl() {
  if (navigator.clipboard && navigator.clipboard.readText) {
    navigator.clipboard.readText().then(function(text) {
      if (text) {
        document.getElementById('urlInput').value = text;
      } else {
        fallbackPaste();
      }
    }).catch(function(err) {
      fallbackPaste();
    });
  } else {
    fallbackPaste();
  }
}

function fallbackPaste() {
  if (window.NeoAndroid && window.NeoAndroid.getFromClipboard) {
    var text = window.NeoAndroid.getFromClipboard();
    if (text) {
      document.getElementById('urlInput').value = text;
    } else {
      alert('Papan klip sistem masih kosong.');
    }
  } else {
    alert('Clipboard bridge tidak dapat diakses.');
  }
}

function copyUrl() {
  var url = document.getElementById('urlInput').value;
  if (!url) {
    alert('Kolom URL masih kosong.');
    return;
  }
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(url).then(function() {
      alert('URL disalin ke papan klip!');
    }).catch(function() {
      fallbackCopy(url);
    });
  } else {
    fallbackCopy(url);
  }
}

function fallbackCopy(text) {
  if (window.NeoAndroid && window.NeoAndroid.copyToClipboard) {
    window.NeoAndroid.copyToClipboard(text);
  } else {
    alert('Gagal menyalin URL.');
  }
}

function startDownload() {
  const url = document.getElementById('urlInput').value.trim();
  if (!url) {
    alert('Silakan masukkan URL tautan unduhan.');
    return;
  }
  var bridge = window.AndroidBridge || window.NeoAndroid;
  if (bridge && typeof bridge.downloadFile === 'function') {
    bridge.downloadFile(url, '');
  } else {
    var a = document.createElement('a');
    a.href = url;
    a.download = '';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  }
}

function downloadTestBlobImage() {
  var canvas = document.createElement('canvas');
  canvas.width = 400;
  canvas.height = 400;
  var ctx = canvas.getContext('2d');
  ctx.fillStyle = '#060D17';
  ctx.fillRect(0, 0, 400, 400);
  ctx.fillStyle = '#00E5FF';
  ctx.font = 'bold 22px sans-serif';
  ctx.textAlign = 'center';
  ctx.fillText('NEO BLOB PNG VALID', 200, 180);
  ctx.fillStyle = '#00E676';
  ctx.font = '15px sans-serif';
  ctx.fillText('Format terverifikasi tanpa .bin', 200, 220);

  if (canvas.toBlob) {
    canvas.toBlob(function(blob) {
      if (!blob) return;
      if (window.saveAs) {
        window.saveAs(blob, 'neo_blob_test_' + Date.now() + '.png');
      } else {
        var blobUrl = URL.createObjectURL(blob);
        var a = document.createElement('a');
        a.href = blobUrl;
        a.download = 'neo_blob_test_' + Date.now() + '.png';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
      }
    }, 'image/png');
  }
}

function testPipMode() {
  var bridge = window.AndroidBridge || window.NeoAndroid;
  if (bridge && typeof bridge.enterPip === 'function') {
    bridge.enterPip(16, 9);
  } else {
    alert('Picture-in-Picture bridge tidak tersedia.');
  }
}

function testOverlayPermission() {
  var bridge = window.AndroidBridge || window.NeoAndroid;
  if (bridge && typeof bridge.requestOverlayPermission === 'function') {
    bridge.requestOverlayPermission();
  } else {
    alert('Izin overlay bridge tidak tersedia.');
  }
}

function testNativeSaveMp4() {
  var statusEl = document.getElementById('bridgeStatus');
  if (statusEl) statusEl.textContent = 'Memulai penyimpanan mp4...';
  if (window.AndroidBridge && typeof window.AndroidBridge.isAvailable === 'function' && window.AndroidBridge.isAvailable()) {
    try {
      var fname = 'Neo_video_' + Date.now() + '.mp4';
      var saveId = AndroidBridge.beginSave('mp4', fname, 'video/mp4');
      if (!saveId || saveId.indexOf('ERROR:') === 0) {
        if (statusEl) statusEl.textContent = 'Gagal inisialisasi: ' + saveId;
        return;
      }
      // Sample MP4 header chunk
      var sampleMp4B64 = 'AAAAIGZ0eXBpc29tAAACAGlzb21pc28yYXZjMW1wNDEAAAAIZnJlZQAAAAs=';
      AndroidBridge.appendChunk(saveId, sampleMp4B64);
      var result = AndroidBridge.finishSave(saveId);
      if (statusEl) statusEl.textContent = '✔ Sukses: ' + result;
    } catch(e) {
      if (statusEl) statusEl.textContent = 'Error: ' + e.message;
    }
  } else {
    if (statusEl) statusEl.textContent = 'AndroidBridge tidak tersedia.';
  }
}

function testNativeSaveMp3() {
  var statusEl = document.getElementById('bridgeStatus');
  if (statusEl) statusEl.textContent = 'Memulai penyimpanan mp3...';
  if (window.AndroidBridge && typeof window.AndroidBridge.isAvailable === 'function' && window.AndroidBridge.isAvailable()) {
    try {
      var fname = 'Neo_audio_' + Date.now() + '.mp3';
      var saveId = AndroidBridge.beginSave('mp3', fname, 'audio/mpeg');
      if (!saveId || saveId.indexOf('ERROR:') === 0) {
        if (statusEl) statusEl.textContent = 'Gagal inisialisasi: ' + saveId;
        return;
      }
      // Sample MP3 ID3 chunk
      var sampleMp3B64 = 'SUQzBAAAAAAAI1RTU0UAAAAPAAADTGF2ZjU4Ljc2LjEwMAAAAAAAAAAAAAAA';
      AndroidBridge.appendChunk(saveId, sampleMp3B64);
      var result = AndroidBridge.finishSave(saveId);
      if (statusEl) statusEl.textContent = '✔ Sukses: ' + result;
    } catch(e) {
      if (statusEl) statusEl.textContent = 'Error: ' + e.message;
    }
  } else {
    if (statusEl) statusEl.textContent = 'AndroidBridge tidak tersedia.';
  }
}
            """.trimIndent(),
            Charsets.UTF_8
        )
    }

    private fun setupGameTemplate(project: Project) {
        val dir = getProjectDir(project.folderName)
        File(dir, "index.html").writeText(
            """
<!DOCTYPE html>
<html>
<head>
  <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
  <title>${project.name}</title>
  <style>
    body { margin: 0; background: #000; overflow: hidden; display: flex; align-items: center; justify-content: center; height: 100vh; }
    canvas { background: #0b111e; border: 2px solid #00e5ff; border-radius: 12px; }
  </style>
</head>
<body>
  <canvas id="gameCanvas" width="320" height="480"></canvas>
  <script>
    const canvas = document.getElementById('gameCanvas');
    const ctx = canvas.getContext('2d');
    let x = 160, y = 240, dx = 3, dy = 3, r = 16;
    function draw() {
      ctx.fillStyle = 'rgba(11, 17, 30, 0.3)';
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      ctx.beginPath();
      ctx.arc(x, y, r, 0, Math.PI * 2);
      ctx.fillStyle = '#00e5ff';
      ctx.shadowBlur = 15;
      ctx.shadowColor = '#00e5ff';
      ctx.fill();
      ctx.closePath();
      if (x + dx > canvas.width - r || x + dx < r) dx = -dx;
      if (y + dy > canvas.height - r || y + dy < r) dy = -dy;
      x += dx; y += dy;
      requestAnimationFrame(draw);
    }
    draw();
  </script>
</body>
</html>
            """.trimIndent(),
            Charsets.UTF_8
        )
    }

    private fun setupBlankTemplate(project: Project) {
        val dir = getProjectDir(project.folderName)
        File(dir, "index.html").writeText(
            ApkBuilder.generateDefaultIndexHtml(project.name),
            Charsets.UTF_8
        )
    }
}
