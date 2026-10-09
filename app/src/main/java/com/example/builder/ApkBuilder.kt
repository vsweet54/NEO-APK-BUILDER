package com.example.builder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.example.data.ProjectConfig
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

object ApkBuilder {

    private const val TEMPLATE_ASSET = "runner_template.apk"

    fun buildApk(
        context: Context,
        config: ProjectConfig,
        projectDir: File,
        customIconBitmap: Bitmap? = null
    ): File {
        val entries = LinkedHashMap<String, ByteArray>()

        // 1. Read base template APK entries from assets
        context.assets.open(TEMPLATE_ASSET).use { isStream ->
            ZipInputStream(isStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory && !name.startsWith("META-INF/")) {
                        val baos = ByteArrayOutputStream()
                        zis.copyTo(baos)
                        entries[name] = baos.toByteArray()
                    }
                    entry = zis.nextEntry
                }
            }
        }

        // 2. Modify AndroidManifest.xml
        val effectiveTargetSdk = if (config.targetSdk >= 26) config.targetSdk else 34
        val effectiveMinSdk = if (config.minSdk in 14..30) config.minSdk else 21
        val effectiveVersionCode = if (config.versionCode > 0) config.versionCode else 1

        val enabledManifestPermissions = com.example.data.PermissionManager.getManifestPermissions(
            config.permissions,
            effectiveTargetSdk
        )

        val originalManifest = entries["AndroidManifest.xml"]
        if (originalManifest != null) {
            val manifestReplacements = mapOf(
                "com.neo.template" to config.packageName.trim(),
                "Neo Template" to config.appName.trim(),
                "1.0.0" to config.versionName.trim(),
                "16" to "14"
            )
            entries["AndroidManifest.xml"] = AxmlModifier.modifyManifest(
                originalManifest,
                manifestReplacements,
                versionCode = effectiveVersionCode,
                minSdkVersion = effectiveMinSdk,
                targetSdkVersion = effectiveTargetSdk,
                enabledPermissions = enabledManifestPermissions
            )
        }

        // 2b. Write runtime app config for MainActivity
        val appConfigJson = """
        {
          "appName": "${config.appName.replace("\"", "\\\"")}",
          "packageName": "${config.packageName.replace("\"", "\\\"")}",
          "orientation": "${config.orientation}",
          "fullscreen": ${config.fullscreen},
          "nativeBridge": ${config.nativeBridge},
          "domStorage": ${config.domStorage}
        }
        """.trimIndent()
        entries["assets/app_config.json"] = appConfigJson.toByteArray(Charsets.UTF_8)

        // 3. Modify resources.arsc
        val originalArsc = entries["resources.arsc"]
        if (originalArsc != null) {
            val arscReplacements = mapOf(
                "Neo Template" to config.appName.trim()
            )
            entries["resources.arsc"] = ArscModifier.replaceStrings(originalArsc, arscReplacements)
        }

        // 4. Inject Custom Launcher Icons if available
        val iconToUse = customIconBitmap ?: loadProjectIcon(projectDir)
        if (iconToUse != null) {
            val densities = mapOf(
                "res/mipmap-mdpi-v4/ic_launcher.png" to 48,
                "res/mipmap-hdpi-v4/ic_launcher.png" to 72,
                "res/mipmap-xhdpi-v4/ic_launcher.png" to 96,
                "res/mipmap-xxhdpi-v4/ic_launcher.png" to 144,
                "res/mipmap-xxxhdpi-v4/ic_launcher.png" to 192
            )
            for ((path, size) in densities) {
                entries[path] = resizeBitmapToPng(iconToUse, size)
            }
        }

        // 5. Inject Web Files into assets/www/
        val webFiles = collectProjectFiles(projectDir)
        var hasIndex = false
        for ((relPath, file) in webFiles) {
            val normalized = relPath.replace('\\', '/')
            if (normalized.equals("index.html", ignoreCase = true) || normalized.equals("index.htm", ignoreCase = true)) {
                hasIndex = true
            }
            entries["assets/www/$normalized"] = file.readBytes()
        }

        // If no index.html exists, create a default modern starter index.html
        if (!hasIndex) {
            val starterHtml = generateDefaultIndexHtml(config.appName)
            entries["assets/www/index.html"] = starterHtml.toByteArray(Charsets.UTF_8)
        }

        // 6. Sign and output the APK
        val buildsDir = File(context.filesDir, "builds").apply { mkdirs() }
        val safeFileName = "${config.appName.replace("[^a-zA-Z0-9_-]".toRegex(), "_")}_${config.versionName}.apk"
        val outputFile = File(buildsDir, safeFileName)

        ApkSigner.signApk(context, entries, outputFile, minSdkVersion = effectiveMinSdk)
        return outputFile
    }

    private fun loadProjectIcon(projectDir: File): Bitmap? {
        val possibleIcons = listOf("icon.png", "icon.jpg", "logo.png", "logo.jpg")
        for (name in possibleIcons) {
            val f = File(projectDir, name)
            if (f.exists()) {
                try {
                    return BitmapFactory.decodeFile(f.absolutePath)
                } catch (ignored: Exception) {}
            }
        }
        return null
    }

    private fun resizeBitmapToPng(source: Bitmap, size: Int): ByteArray {
        val scaled = Bitmap.createScaledBitmap(source, size, size, true)
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 100, baos)
        return baos.toByteArray()
    }

    private fun collectProjectFiles(dir: File): List<Pair<String, File>> {
        val list = mutableListOf<Pair<String, File>>()
        fun scan(current: File, prefix: String) {
            val files = current.listFiles() ?: return
            for (f in files) {
                if (f.name.startsWith(".")) continue
                if (f.isDirectory) {
                    scan(f, if (prefix.isEmpty()) f.name else "$prefix/${f.name}")
                } else {
                    val rel = if (prefix.isEmpty()) f.name else "$prefix/${f.name}"
                    list.add(Pair(rel, f))
                }
            }
        }
        if (dir.exists()) {
            scan(dir, "")
        }
        return list
    }

    fun generateDefaultIndexHtml(appName: String): String {
        return """
<!DOCTYPE html>
<html lang="id">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  <title>$appName</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background: linear-gradient(135deg, #060D17 0%, #0B162C 100%);
      color: #FFFFFF;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: flex-start;
      padding: 24px 16px;
      text-align: center;
    }
    .badge {
      display: inline-block;
      padding: 6px 16px;
      background: rgba(0, 229, 255, 0.15);
      color: #00E5FF;
      border: 1px solid rgba(0, 229, 255, 0.4);
      border-radius: 20px;
      font-size: 12px;
      font-weight: 700;
      letter-spacing: 1px;
      margin-bottom: 14px;
    }
    h1 {
      font-size: 24px;
      font-weight: 800;
      color: #00E5FF;
      text-shadow: 0 0 20px rgba(0, 229, 255, 0.4);
      margin-bottom: 8px;
    }
    p.sub {
      color: #A0B2C6;
      font-size: 13px;
      line-height: 1.5;
      max-width: 380px;
      margin-bottom: 20px;
    }
    .card {
      background: rgba(17, 28, 46, 0.9);
      border: 1px solid rgba(0, 229, 255, 0.25);
      border-radius: 18px;
      padding: 20px 18px;
      max-width: 400px;
      width: 100%;
      box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5);
      margin-bottom: 16px;
      text-align: left;
    }
    .card h3 {
      font-size: 14px;
      color: #00E5FF;
      margin-bottom: 12px;
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .input-field {
      width: 100%;
      padding: 12px 14px;
      background: #0B162C;
      border: 1px solid rgba(0, 229, 255, 0.3);
      border-radius: 10px;
      color: #FFFFFF;
      font-size: 14px;
      margin-bottom: 10px;
      outline: none;
    }
    .input-field:focus {
      border-color: #00E5FF;
      box-shadow: 0 0 10px rgba(0, 229, 255, 0.3);
    }
    .btn-row {
      display: flex;
      gap: 8px;
      margin-bottom: 8px;
    }
    .btn {
      flex: 1;
      padding: 12px 14px;
      background: linear-gradient(90deg, #00E5FF, #0091EA);
      color: #001220;
      font-size: 13px;
      font-weight: 700;
      border-radius: 10px;
      border: none;
      cursor: pointer;
      transition: all 0.2s ease;
      text-align: center;
    }
    .btn:active {
      transform: scale(0.97);
      filter: brightness(0.9);
    }
    .btn-secondary {
      background: #192740;
      color: #00E5FF;
      border: 1px solid rgba(0, 229, 255, 0.3);
    }
    .status-box {
      background: #060D17;
      border-radius: 8px;
      padding: 8px 12px;
      font-size: 12px;
      color: #A0B2C6;
      word-break: break-all;
      min-height: 32px;
      display: flex;
      align-items: center;
    }
    .status-ok { color: #00E676; }
    .status-err { color: #FF5252; }
    .footer {
      font-size: 12px;
      color: #5C708A;
      margin-top: 10px;
    }
  </style>
</head>
<body>
  <div class="badge">NEO APK RUNNER MODERN v2.0</div>
  <h1>$appName</h1>
  <p class="sub">Aplikasi web berjalan dalam kontainer Android native WebView dengan dukungan Clipboard, SAF File Picker, dan Permissions.</p>

  <!-- TEST CARD 1: CLIPBOARD -->
  <div class="card">
    <h3>📋 PENGUJIAN CLIPBOARD (COPY & PASTE)</h3>
    <input type="text" id="clipInput" class="input-field" value="Teks pengujian clipboard $appName" placeholder="Ketik teks untuk disalin...">
    <div class="btn-row">
      <button class="btn" onclick="testCopy()">Salin Teks</button>
      <button class="btn btn-secondary" onclick="testPaste()">Tempel Teks</button>
    </div>
    <div id="clipStatus" class="status-box">Status: Siap diuji</div>
  </div>

  <!-- TEST CARD 2: FILE STORAGE & SAF -->
  <div class="card">
    <h3>📁 PENGUJIAN BERKAS (SAF & DOWNLOADS)</h3>
    <p style="font-size: 12px; color: #A0B2C6; margin-bottom: 10px;">Pilih berkas dari perangkat Anda via Storage Access Framework (SAF):</p>
    <input type="file" id="filePicker" class="input-field" onchange="onFileSelected(this)">
    <div class="btn-row">
      <button class="btn btn-secondary" onclick="testSaveFile()">Simpan File Contoh (.txt)</button>
    </div>
    <div id="fileStatus" class="status-box">Status: Belum ada berkas dipilih</div>
  </div>

  <!-- TEST CARD 3: HARDWARE & SENSORS -->
  <div class="card">
    <h3>⚙️ PENGUJIAN IZIN & SENSOR PERANGKAT</h3>
    <div class="btn-row">
      <button class="btn btn-secondary" onclick="testCamera()">Uji Kamera</button>
      <button class="btn btn-secondary" onclick="testMic()">Uji Mikrofon</button>
    </div>
    <div class="btn-row">
      <button class="btn btn-secondary" onclick="testLocation()">Uji Lokasi (GPS)</button>
      <button class="btn btn-secondary" onclick="testVibrate()">Uji Getar</button>
    </div>
    <div id="sensorStatus" class="status-box">Status: Klik tombol untuk menguji izin</div>
  </div>

  <div class="footer">NEO APK BUILDER • Powered by Modern Android Runtime</div>

  <script>
    function setStatus(id, text, isOk, isErr) {
      var el = document.getElementById(id);
      if (!el) return;
      el.textContent = text;
      el.className = 'status-box ' + (isOk ? 'status-ok' : (isErr ? 'status-err' : ''));
    }

    // 1. CLIPBOARD TESTS
    function testCopy() {
      var input = document.getElementById('clipInput');
      var text = input ? input.value : '';
      if (!text) {
        setStatus('clipStatus', 'Teks kosong, ketik sesuatu terlebih dahulu.', false, true);
        return;
      }
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(function() {
          setStatus('clipStatus', '✔ Berhasil disalin: "' + text + '"', true, false);
        }).catch(function(err) {
          fallbackCopy(text);
        });
      } else {
        fallbackCopy(text);
      }
    }

    function fallbackCopy(text) {
      if (window.NeoAndroid && window.NeoAndroid.copyToClipboard) {
        window.NeoAndroid.copyToClipboard(text);
        setStatus('clipStatus', '✔ Disalin via NeoAndroid bridge: "' + text + '"', true, false);
      } else {
        setStatus('clipStatus', 'Gagal menyalin teks.', false, true);
      }
    }

    function testPaste() {
      if (navigator.clipboard && navigator.clipboard.readText) {
        navigator.clipboard.readText().then(function(text) {
          handlePastedText(text);
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
        handlePastedText(text);
      } else {
        setStatus('clipStatus', 'Clipboard bridge tidak tersedia', false, true);
      }
    }

    function handlePastedText(text) {
      var input = document.getElementById('clipInput');
      if (input) input.value = text || '';
      setStatus('clipStatus', text ? ('✔ Ditempel: "' + text + '"') : 'Clipboard sistem kosong.', text ? true : false, false);
    }

    // 2. FILE TESTS
    function onFileSelected(input) {
      if (input.files && input.files.length > 0) {
        var file = input.files[0];
        setStatus('fileStatus', '✔ Berkas dipilih: ' + file.name + ' (' + Math.round(file.size/1024) + ' KB)', true, false);
      } else {
        setStatus('fileStatus', 'Pemilihan berkas dibatalkan.', false, false);
      }
    }

    function testSaveFile() {
      var content = 'Ini adalah berkas pengujian dari aplikasi ' + '$appName' + ' pada ' + new Date().toLocaleString();
      var fileName = 'test_neo_' + Date.now() + '.txt';
      if (window.NeoAndroid && window.NeoAndroid.saveTextFile) {
        var ok = window.NeoAndroid.saveTextFile(content, fileName, 'text/plain');
        if (ok) {
          setStatus('fileStatus', '✔ Berkas disimpan ke folder Downloads: ' + fileName, true, false);
        } else {
          setStatus('fileStatus', 'Gagal menyimpan berkas via bridge.', false, true);
        }
      } else {
        setStatus('fileStatus', 'Native file save bridge tidak tersedia.', false, true);
      }
    }

    // 3. SENSOR & PERMISSIONS TESTS
    function testCamera() {
      setStatus('sensorStatus', 'Meminta izin kamera...');
      if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
        navigator.mediaDevices.getUserMedia({ video: true }).then(function(stream) {
          setStatus('sensorStatus', '✔ Izin Kamera DIBERIKAN! Stream aktif.', true, false);
          stream.getTracks().forEach(function(t) { t.stop(); });
        }).catch(function(err) {
          setStatus('sensorStatus', 'Izin kamera ditolak / tidak tersedia: ' + err.message, false, true);
        });
      } else {
        setStatus('sensorStatus', 'MediaDevices API tidak didukung.', false, true);
      }
    }

    function testMic() {
      setStatus('sensorStatus', 'Meminta izin mikrofon...');
      if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
        navigator.mediaDevices.getUserMedia({ audio: true }).then(function(stream) {
          setStatus('sensorStatus', '✔ Izin Mikrofon DIBERIKAN!', true, false);
          stream.getTracks().forEach(function(t) { t.stop(); });
        }).catch(function(err) {
          setStatus('sensorStatus', 'Izin mikrofon ditolak: ' + err.message, false, true);
        });
      } else {
        setStatus('sensorStatus', 'MediaDevices API tidak didukung.', false, true);
      }
    }

    function testLocation() {
      setStatus('sensorStatus', 'Meminta izin lokasi (GPS)...');
      if (navigator.geolocation) {
        navigator.geolocation.getCurrentPosition(function(pos) {
          setStatus('sensorStatus', '✔ Lokasi didapat: ' + pos.coords.latitude.toFixed(4) + ', ' + pos.coords.longitude.toFixed(4), true, false);
        }, function(err) {
          setStatus('sensorStatus', 'Izin lokasi ditolak / error: ' + err.message, false, true);
        });
      } else {
        setStatus('sensorStatus', 'Geolocation API tidak didukung.', false, true);
      }
    }

    function testVibrate() {
      if (navigator.vibrate) {
        navigator.vibrate([100, 50, 100]);
        setStatus('sensorStatus', '✔ Efek getar dijalankan (Haptic triggered).', true, false);
      } else {
        setStatus('sensorStatus', 'Navigator vibrate tidak didukung.', false, false);
      }
    }
  </script>
</body>
</html>
        """.trimIndent()
    }
}
