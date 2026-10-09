package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.wifi.WifiManager
import android.text.format.Formatter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BuildHistoryItem
import com.example.data.HostedApk
import com.example.data.Project
import com.example.data.ProjectRepository
import com.example.data.WebProjectFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.ServerSocket
import java.net.Socket

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProjectRepository(application)

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private val _selectedProject = MutableStateFlow<Project?>(null)
    val selectedProject: StateFlow<Project?> = _selectedProject.asStateFlow()

    private val _builds = MutableStateFlow<List<BuildHistoryItem>>(emptyList())
    val builds: StateFlow<List<BuildHistoryItem>> = _builds.asStateFlow()

    private val _currentFiles = MutableStateFlow<List<WebProjectFile>>(emptyList())
    val currentFiles: StateFlow<List<WebProjectFile>> = _currentFiles.asStateFlow()

    private val _activeEditingFile = MutableStateFlow<WebProjectFile?>(null)
    val activeEditingFile: StateFlow<WebProjectFile?> = _activeEditingFile.asStateFlow()

    private val _fileContent = MutableStateFlow("")
    val fileContent: StateFlow<String> = _fileContent.asStateFlow()

    private val _isBuilding = MutableStateFlow(false)
    val isBuilding: StateFlow<Boolean> = _isBuilding.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _buildStatusMessage = MutableStateFlow<String?>(null)
    val buildStatusMessage: StateFlow<String?> = _buildStatusMessage.asStateFlow()

    private val _buildError = MutableStateFlow<String?>(null)
    val buildError: StateFlow<String?> = _buildError.asStateFlow()

    private val _customIcon = MutableStateFlow<Bitmap?>(null)
    val customIcon: StateFlow<Bitmap?> = _customIcon.asStateFlow()

    private val _hostedApks = MutableStateFlow<List<HostedApk>>(emptyList())
    val hostedApks: StateFlow<List<HostedApk>> = _hostedApks.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _uploadProgress = MutableStateFlow(0)
    val uploadProgress: StateFlow<Int> = _uploadProgress.asStateFlow()

    private val _uploadStatusMessage = MutableStateFlow<String?>(null)
    val uploadStatusMessage: StateFlow<String?> = _uploadStatusMessage.asStateFlow()

    private val _lastUploadedApk = MutableStateFlow<HostedApk?>(null)
    val lastUploadedApk: StateFlow<HostedApk?> = _lastUploadedApk.asStateFlow()

    private val _lastBuiltItem = MutableStateFlow<BuildHistoryItem?>(null)
    val lastBuiltItem: StateFlow<BuildHistoryItem?> = _lastBuiltItem.asStateFlow()

    private val _showBuildSuccessDialog = MutableStateFlow(false)
    val showBuildSuccessDialog: StateFlow<Boolean> = _showBuildSuccessDialog.asStateFlow()

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl.asStateFlow()

    private var serverSocket: ServerSocket? = null

    init {
        viewModelScope.launch {
            repository.initDefaultProjectsIfEmpty()
        }

        viewModelScope.launch {
            repository.projectsFlow.collect { list ->
                _projects.value = list
                if (_selectedProject.value == null && list.isNotEmpty()) {
                    selectProject(list.first())
                } else if (_selectedProject.value != null) {
                    val updated = list.find { it.id == _selectedProject.value?.id }
                    if (updated != null) {
                        _selectedProject.value = updated
                    }
                }
            }
        }

        viewModelScope.launch {
            repository.buildsFlow.collect { list ->
                _builds.value = list
            }
        }

        viewModelScope.launch {
            repository.hostedApksFlow.collect { list ->
                _hostedApks.value = list
            }
        }
    }

    fun selectTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun selectProject(project: Project) {
        _selectedProject.value = project
        refreshFiles(project)
        // Load custom icon if exists
        val iconFile = File(repository.getProjectDir(project.folderName), "icon.png")
        if (iconFile.exists()) {
            try {
                _customIcon.value = BitmapFactory.decodeFile(iconFile.absolutePath)
            } catch (ignored: Exception) {
                _customIcon.value = null
            }
        } else {
            _customIcon.value = null
        }
    }

    fun refreshFiles(project: Project? = _selectedProject.value) {
        if (project == null) return
        viewModelScope.launch(Dispatchers.IO) {
            val files = repository.listProjectFiles(project)
            _currentFiles.value = files
        }
    }

    fun createProject(name: String, packageName: String, template: String) {
        viewModelScope.launch {
            val p = repository.createProject(name, packageName, template)
            selectProject(p)
            _selectedTab.value = 1 // Go to Projects tab
        }
    }

    fun updateProjectConfig(
        name: String,
        packageName: String,
        versionName: String,
        versionCode: Int,
        orientation: String,
        fullscreen: Boolean
    ) {
        val current = _selectedProject.value ?: return
        viewModelScope.launch {
            val updated = current.copy(
                name = name,
                packageName = packageName,
                versionName = versionName,
                versionCode = versionCode,
                orientation = orientation,
                fullscreen = fullscreen
            )
            repository.updateProject(updated)
            _selectedProject.value = updated
        }
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch {
            repository.deleteProject(project)
            val remaining = _projects.value.filter { it.id != project.id }
            _selectedProject.value = remaining.firstOrNull()
            if (_selectedProject.value != null) {
                refreshFiles(_selectedProject.value)
            }
        }
    }

    fun openFile(file: WebProjectFile) {
        val project = _selectedProject.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val content = repository.readFile(project, file.relativePath)
            _fileContent.value = content
            _activeEditingFile.value = file
        }
    }

    fun closeEditor() {
        _activeEditingFile.value = null
    }

    fun saveActiveFile(newContent: String) {
        val project = _selectedProject.value ?: return
        val file = _activeEditingFile.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveFile(project, file.relativePath, newContent)
            _fileContent.value = newContent
            refreshFiles(project)
        }
    }

    fun createFile(relPath: String, content: String) {
        val project = _selectedProject.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveFile(project, relPath, content)
            refreshFiles(project)
        }
    }

    fun renameFile(file: WebProjectFile, newName: String) {
        val project = _selectedProject.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.renameFile(project, file.relativePath, newName)
            refreshFiles(project)
        }
    }

    fun importFiles(
        uris: List<android.net.Uri>,
        contentResolver: android.content.ContentResolver,
        onComplete: (Int) -> Unit = {}
    ) {
        val project = _selectedProject.value ?: _projects.value.firstOrNull() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isImporting.value = true
            val count = repository.importFilesFromUris(project, uris, contentResolver)
            val updatedFiles = repository.listProjectFiles(project)
            withContext(Dispatchers.Main) {
                _currentFiles.value = ArrayList(updatedFiles)
                _isImporting.value = false
                onComplete(count)
            }
        }
    }

    fun saveFullConfig(
        appName: String,
        packageName: String,
        versionName: String,
        versionCode: Int,
        orientation: String,
        fullscreen: Boolean,
        minSdk: Int,
        targetSdk: Int,
        buildType: String,
        nativeBridge: Boolean,
        domStorage: Boolean,
        permissions: String
    ) {
        val current = _selectedProject.value ?: return
        viewModelScope.launch {
            val updated = current.copy(
                name = appName,
                packageName = packageName,
                versionName = versionName,
                versionCode = versionCode,
                orientation = orientation,
                fullscreen = fullscreen,
                minSdk = minSdk,
                targetSdk = targetSdk,
                buildType = buildType,
                nativeBridge = nativeBridge,
                domStorage = domStorage,
                permissions = permissions
            )
            repository.updateProject(updated)
            _selectedProject.value = updated
        }
    }

    fun deleteFile(file: WebProjectFile) {
        val project = _selectedProject.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteFile(project, file.relativePath)
            if (_activeEditingFile.value?.relativePath == file.relativePath) {
                _activeEditingFile.value = null
            }
            refreshFiles(project)
        }
    }

    fun createIndexHtmlIfMissing() {
        val project = _selectedProject.value ?: return
        val starter = com.example.builder.ApkBuilder.generateDefaultIndexHtml(project.name)
        createFile("index.html", starter)
    }

    fun setCustomIcon(bitmap: Bitmap?) {
        _customIcon.value = bitmap
        val project = _selectedProject.value ?: return
        if (bitmap != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val iconFile = File(repository.getProjectDir(project.folderName), "icon.png")
                iconFile.outputStream().use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                }
            }
        }
    }

    fun buildApk() {
        val project = _selectedProject.value ?: _projects.value.firstOrNull()
        if (project == null) {
            viewModelScope.launch {
                repository.initDefaultProjectsIfEmpty()
                val list = _projects.value
                val p = list.firstOrNull() ?: repository.createProject("NEO DOWNLOADER", "com.neo.downloader", "downloader")
                selectProject(p)
                performBuild(p)
            }
            return
        }
        performBuild(project)
    }

    private fun performBuild(project: Project) {
        _isBuilding.value = true
        _buildStatusMessage.value = "Memvalidasi berkas proyek & struktur HTML..."
        _buildError.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                kotlinx.coroutines.delay(200)
                _buildStatusMessage.value = "Mengonfigurasi Manifest: ${project.packageName}..."
                kotlinx.coroutines.delay(200)
                _buildStatusMessage.value = "Menyuntikkan berkas HTML/CSS/JS ke APK..."
                kotlinx.coroutines.delay(200)
                _buildStatusMessage.value = "Memproses ikon & resource..."
                kotlinx.coroutines.delay(200)
                _buildStatusMessage.value = "Menandatangani APK dengan sertifikat (JAR Signer)..."

                val historyItem = repository.buildApk(project, _customIcon.value)
                _buildStatusMessage.value = "Kompilasi Berhasil! APK siap diinstall."
                kotlinx.coroutines.delay(300)
                withContext(Dispatchers.Main) {
                    _lastBuiltItem.value = historyItem
                    _showBuildSuccessDialog.value = true
                    _isBuilding.value = false
                    _buildStatusMessage.value = null
                    _selectedTab.value = 4 // Go to Builds tab
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isBuilding.value = false
                    _buildStatusMessage.value = null
                    _buildError.value = "Kompilasi gagal: ${e.message ?: "Terjadi kesalahan internal"}"
                }
            }
        }
    }

    fun dismissBuildSuccessDialog() {
        _showBuildSuccessDialog.value = false
    }

    fun clearBuildError() {
        _buildError.value = null
    }

    fun uploadBuildApk(item: BuildHistoryItem) {
        _isUploading.value = true
        _uploadProgress.value = 0
        _uploadStatusMessage.value = "Menyiapkan berkas APK ${item.appName}..."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val hosted = repository.uploadBuildApk(item) { progress, msg ->
                    _uploadProgress.value = progress
                    _uploadStatusMessage.value = msg
                }
                withContext(Dispatchers.Main) {
                    _lastUploadedApk.value = hosted
                    _isUploading.value = false
                    _uploadStatusMessage.value = null
                    _selectedTab.value = 3 // Go to Hosting tab!
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isUploading.value = false
                    _uploadStatusMessage.value = null
                    _buildError.value = "Upload hosting gagal: ${e.message}"
                }
            }
        }
    }

    fun uploadCustomApkFromStorage(
        uri: android.net.Uri,
        contentResolver: android.content.ContentResolver,
        context: android.content.Context
    ) {
        _isUploading.value = true
        _uploadProgress.value = 0
        _uploadStatusMessage.value = "Membaca berkas APK dari penyimpanan..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tempFile = File(context.cacheDir, "upload_temp_${System.currentTimeMillis()}.apk")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                val hosted = repository.uploadCustomApk(
                    file = tempFile,
                    appName = tempFile.nameWithoutExtension,
                    versionName = "1.0",
                    packageName = "com.custom.apk"
                ) { progress, msg ->
                    _uploadProgress.value = progress
                    _uploadStatusMessage.value = msg
                }
                tempFile.delete()
                withContext(Dispatchers.Main) {
                    _lastUploadedApk.value = hosted
                    _isUploading.value = false
                    _uploadStatusMessage.value = null
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isUploading.value = false
                    _uploadStatusMessage.value = null
                    _buildError.value = "Upload gagal: ${e.message}"
                }
            }
        }
    }

    fun deleteHostedApk(item: HostedApk) {
        viewModelScope.launch {
            repository.deleteHostedApk(item)
        }
    }

    fun installBuild(item: BuildHistoryItem) {
        repository.installApk(item)
    }

    fun shareBuild(item: BuildHistoryItem) {
        repository.shareApk(item)
    }

    fun deleteBuild(item: BuildHistoryItem) {
        viewModelScope.launch {
            repository.deleteBuild(item)
        }
    }

    fun toggleServer() {
        if (_isServerRunning.value) {
            stopServer()
        } else {
            startServer()
        }
    }

    private fun startServer() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val port = 8080
                serverSocket = ServerSocket(port)
                val ip = getLocalIpAddress() ?: "127.0.0.1"
                _serverUrl.value = "http://$ip:$port"
                _isServerRunning.value = true

                while (_isServerRunning.value) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        handleClient(client)
                    } catch (e: Exception) {
                        break
                    }
                }
            } catch (e: Exception) {
                _isServerRunning.value = false
                _serverUrl.value = null
            }
        }
    }

    private fun stopServer() {
        _isServerRunning.value = false
        _serverUrl.value = null
        try {
            serverSocket?.close()
        } catch (ignored: Exception) {}
        serverSocket = null
    }

    private fun handleClient(socket: Socket) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val reader = socket.getInputStream().bufferedReader()
                val line = reader.readLine() ?: return@launch
                val parts = line.split(" ")
                val path = if (parts.size > 1) parts[1].removePrefix("/") else ""
                val cleanPath = if (path.isEmpty() || path == "/") "index.html" else path

                val project = _selectedProject.value
                val projectDir = project?.let { repository.getProjectDir(it.folderName) }
                val targetFile = projectDir?.let { File(it, cleanPath) }

                val out = socket.getOutputStream()
                if (targetFile != null && targetFile.exists() && !targetFile.isDirectory) {
                    val bytes = targetFile.readBytes()
                    val mime = getMimeType(targetFile.name)
                    val header = "HTTP/1.1 200 OK\r\nContent-Type: $mime\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                    out.write(header.toByteArray())
                    out.write(bytes)
                } else {
                    val notFound = "<html><body style='background:#060d17;color:#00e5ff;font-family:sans-serif;'><h2>404 Not Found</h2><p>$cleanPath tidak ditemukan</p></body></html>"
                    val bytes = notFound.toByteArray()
                    val header = "HTTP/1.1 404 Not Found\r\nContent-Type: text/html\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                    out.write(header.toByteArray())
                    out.write(bytes)
                }
                out.flush()
                socket.close()
            } catch (ignored: Exception) {}
        }
    }

    private fun getMimeType(fileName: String): String {
        return when {
            fileName.endsWith(".html") || fileName.endsWith(".htm") -> "text/html"
            fileName.endsWith(".css") -> "text/css"
            fileName.endsWith(".js") -> "application/javascript"
            fileName.endsWith(".json") -> "application/json"
            fileName.endsWith(".png") -> "image/png"
            fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") -> "image/jpeg"
            fileName.endsWith(".svg") -> "image/svg+xml"
            else -> "text/plain"
        }
    }

    private fun getLocalIpAddress(): String? {
        return try {
            val wifiManager = getApplication<Application>().applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as? WifiManager
            val ip = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ip != 0) Formatter.formatIpAddress(ip) else "127.0.0.1"
        } catch (e: Exception) {
            "127.0.0.1"
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopServer()
    }
}
