package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object ApkHostingManager {

    suspend fun uploadApk(
        context: Context,
        apkFile: File,
        appName: String,
        versionName: String,
        packageName: String,
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): HostedApk = withContext(Dispatchers.IO) {
        if (!apkFile.exists()) {
            throw FileNotFoundException("Berkas APK tidak ditemukan di: ${apkFile.absolutePath}")
        }

        val totalBytes = apkFile.length()
        if (totalBytes <= 0) {
            throw IOException("Ukuran berkas APK kosong (0 bytes)")
        }

        onProgress(10, "Menyiapkan koneksi ke server hosting...")

        // Provider 1: tmpfiles.org
        try {
            val result = uploadToTmpFiles(apkFile, onProgress)
            val hosted = HostedApk(
                appName = appName,
                versionName = versionName,
                packageName = packageName,
                fileSizeBytes = totalBytes,
                uploadTimestamp = System.currentTimeMillis(),
                pageUrl = result.first,
                directDownloadUrl = result.second,
                fileName = apkFile.name
            )
            return@withContext hosted
        } catch (e: Exception) {
            android.util.Log.e("ApkHostingManager", "tmpfiles.org failed, trying fallback: ${e.message}")
        }

        // Provider 2: catbox.moe / litterbox
        val litterboxUrl = uploadToLitterbox(apkFile, onProgress)
        val hosted = HostedApk(
            appName = appName,
            versionName = versionName,
            packageName = packageName,
            fileSizeBytes = totalBytes,
            uploadTimestamp = System.currentTimeMillis(),
            pageUrl = litterboxUrl,
            directDownloadUrl = litterboxUrl,
            fileName = apkFile.name
        )
        return@withContext hosted
    }

    private fun uploadToTmpFiles(
        file: File,
        onProgress: (Int, String) -> Unit
    ): Pair<String, String> {
        val boundary = "==NEO_BUILDER_${UUID.randomUUID().toString().replace("-", "")}=="
        val url = URL("https://tmpfiles.org/api/v1/upload")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.doInput = true
        conn.useCaches = false
        conn.connectTimeout = 60000
        conn.readTimeout = 120000
        conn.setRequestProperty("User-Agent", "NeoApkBuilder/1.0")
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

        val totalFileSize = file.length()
        val dos = DataOutputStream(conn.outputStream)

        // Write multipart header
        dos.writeBytes("--$boundary\r\n")
        dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"\r\n")
        dos.writeBytes("Content-Type: application/vnd.android.package-archive\r\n\r\n")
        dos.flush()

        // Stream file content with progress
        val buffer = ByteArray(32768)
        var bytesRead: Int
        var totalBytesWritten = 0L

        FileInputStream(file).use { fis ->
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                dos.write(buffer, 0, bytesRead)
                totalBytesWritten += bytesRead
                val percent = 10 + ((totalBytesWritten.toDouble() / totalFileSize.toDouble()) * 80).toInt()
                onProgress(percent, "Mengupload APK (${totalBytesWritten / 1024} KB / ${totalFileSize / 1024} KB)...")
            }
        }
        dos.flush()

        // Multipart end
        dos.writeBytes("\r\n--$boundary--\r\n")
        dos.flush()
        dos.close()

        onProgress(92, "Menunggu respons dari server hosting...")

        val responseCode = conn.responseCode
        if (responseCode in 200..299) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)
            val data = json.getJSONObject("data")
            val pageUrl = data.getString("url")
            // Convert to direct download url: https://tmpfiles.org/123/name.apk -> https://tmpfiles.org/dl/123/name.apk
            val directUrl = pageUrl.replace("tmpfiles.org/", "tmpfiles.org/dl/")
            return Pair(pageUrl, directUrl)
        } else {
            val errText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
            throw IOException("Upload gagal (HTTP $responseCode): $errText")
        }
    }

    private fun uploadToLitterbox(
        file: File,
        onProgress: (Int, String) -> Unit
    ): String {
        val boundary = "==NEO_BUILDER_${UUID.randomUUID().toString().replace("-", "")}=="
        val url = URL("https://litterbox.catbox.moe/resources/internals/api.php")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.doInput = true
        conn.useCaches = false
        conn.connectTimeout = 60000
        conn.readTimeout = 120000
        conn.setRequestProperty("User-Agent", "NeoApkBuilder/1.0")
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

        val dos = DataOutputStream(conn.outputStream)

        // Field: reqtype = fileupload
        dos.writeBytes("--$boundary\r\n")
        dos.writeBytes("Content-Disposition: form-data; name=\"reqtype\"\r\n\r\n")
        dos.writeBytes("fileupload\r\n")

        // Field: time = 72h
        dos.writeBytes("--$boundary\r\n")
        dos.writeBytes("Content-Disposition: form-data; name=\"time\"\r\n\r\n")
        dos.writeBytes("72h\r\n")

        // File field: fileToUpload
        dos.writeBytes("--$boundary\r\n")
        dos.writeBytes("Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"${file.name}\"\r\n")
        dos.writeBytes("Content-Type: application/vnd.android.package-archive\r\n\r\n")

        val buffer = ByteArray(32768)
        var bytesRead: Int
        var totalBytesWritten = 0L
        val totalFileSize = file.length()

        FileInputStream(file).use { fis ->
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                dos.write(buffer, 0, bytesRead)
                totalBytesWritten += bytesRead
                val percent = 20 + ((totalBytesWritten.toDouble() / totalFileSize.toDouble()) * 70).toInt()
                onProgress(percent, "Mengupload APK...")
            }
        }
        dos.flush()

        dos.writeBytes("\r\n--$boundary--\r\n")
        dos.flush()
        dos.close()

        val responseCode = conn.responseCode
        if (responseCode in 200..299) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }.trim()
            if (responseText.startsWith("http")) {
                return responseText
            }
            throw IOException("Respon server bukan URL valid: $responseText")
        } else {
            throw IOException("Upload fallback gagal: HTTP $responseCode")
        }
    }
}
