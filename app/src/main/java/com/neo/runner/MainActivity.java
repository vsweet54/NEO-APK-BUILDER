package com.neo.runner;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {
    private static final String TAG = "NeoRunner";
    private static final int FILE_CHOOSER_RESULT_CODE = 1001;
    private static final int PERMISSION_REQUEST_CODE = 2001;

    private WebView webView;
    private ValueCallback<Uri[]> uploadMessage;
    private ValueCallback<Uri> uploadMessageLegacy;
    private PermissionRequest pendingWebPermission;
    private GeolocationPermissions.Callback pendingGeoCallback;
    private String pendingGeoOrigin;

    private String appName = "NEO App";
    private String packageName = "com.neo.app";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        applyAppConfig();
        configureWebView();
        setupClipboardAndBridge();
        checkAndRequestManifestPermissions();
        loadWebProject();
    }

    private void applyAppConfig() {
        try (InputStream is = getAssets().open("app_config.json")) {
            byte[] bytes = new byte[is.available()];
            int read = is.read(bytes);
            if (read > 0) {
                String jsonStr = new String(bytes, 0, read, StandardCharsets.UTF_8);
                JSONObject obj = new JSONObject(jsonStr);
                appName = obj.optString("appName", appName);
                packageName = obj.optString("packageName", packageName);

                String orientation = obj.optString("orientation", "sensor");
                if ("portrait".equalsIgnoreCase(orientation)) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                } else if ("landscape".equalsIgnoreCase(orientation)) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                }

                boolean fullscreen = obj.optBoolean("fullscreen", false);
                if (fullscreen) {
                    getWindow().setFlags(
                        WindowManager.LayoutParams.FLAG_FULLSCREEN,
                        WindowManager.LayoutParams.FLAG_FULLSCREEN
                    );
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void configureWebView() {
        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#060D17"));

        FrameLayout layout = new FrameLayout(this);
        layout.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        layout.addView(webView);
        setContentView(layout);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setAllowContentAccess(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }
        CookieManager.getInstance().setAcceptCookie(true);

        webView.setWebChromeClient(new NeoWebChromeClient());
        webView.setWebViewClient(new NeoWebViewClient());

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                handleDownload(url, contentDisposition, mimeType, userAgent);
            }
        });
    }

    private void setupClipboardAndBridge() {
        NeoBridge bridge = new NeoBridge();
        webView.addJavascriptInterface(bridge, "NeoAndroid");
        webView.addJavascriptInterface(bridge, "AndroidBridge");
    }

    private void loadWebProject() {
        try {
            String[] assets = getAssets().list("www");
            boolean hasIndex = false;
            if (assets != null) {
                for (String a : assets) {
                    if ("index.html".equalsIgnoreCase(a) || "index.htm".equalsIgnoreCase(a)) {
                        hasIndex = true;
                        break;
                    }
                }
            }
            if (hasIndex) {
                webView.loadUrl("file:///android_asset/www/index.html");
            } else {
                String fallbackHtml = "<!DOCTYPE html><html><body style='background:#060d17;color:#fff;font-family:sans-serif;text-align:center;padding:40px;'>" +
                        "<h2>" + appName + "</h2><p>Selamat! Aplikasi berjalan lancar.</p></body></html>";
                webView.loadDataWithBaseURL("file:///android_asset/www/", fallbackHtml, "text/html", "UTF-8", null);
            }
        } catch (Throwable e) {
            webView.loadUrl("file:///android_asset/www/index.html");
        }
    }

    private void checkAndRequestManifestPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS);
            if (info.requestedPermissions == null) return;
            List<String> needed = new ArrayList<>();
            for (String perm : info.requestedPermissions) {
                if (isDangerousPermission(perm) && checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                    needed.add(perm);
                }
            }
            if (!needed.isEmpty()) {
                requestPermissions(needed.toArray(new String[0]), PERMISSION_REQUEST_CODE);
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean isDangerousPermission(String p) {
        return p.contains("CAMERA") || p.contains("RECORD_AUDIO") || p.contains("LOCATION") ||
               p.contains("READ_MEDIA") || p.contains("STORAGE") || p.contains("CONTACTS") ||
               p.contains("CALENDAR") || p.contains("POST_NOTIFICATIONS") || p.contains("BLUETOOTH");
    }

    public boolean saveBytesToDownloads(byte[] bytes, String fileName, String mimeType) {
        if (bytes == null || bytes.length == 0) return false;
        String safeName = (fileName == null || fileName.trim().isEmpty()) ?
                ("download_" + System.currentTimeMillis() + ".bin") : fileName;
        String effMime = (mimeType == null || mimeType.trim().isEmpty()) ? "application/octet-stream" : mimeType;

        // 1. Android 10+ (API 29+) MediaStore scoped storage (zero permission requirement for Downloads)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                ContentResolver resolver = getContentResolver();
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, safeName);
                values.put(MediaStore.Downloads.MIME_TYPE, effMime);
                values.put(MediaStore.Downloads.IS_PENDING, 1);

                Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream os = resolver.openOutputStream(uri)) {
                        if (os != null) {
                            os.write(bytes);
                            os.flush();
                        }
                    }
                    values.clear();
                    values.put(MediaStore.Downloads.IS_PENDING, 0);
                    resolver.update(uri, values, null, null);
                    return true;
                }
            } catch (Throwable t) {
                Log.e(TAG, "MediaStore save failed: " + t.getMessage());
            }
        }

        // 2. Direct external public Downloads directory (Android 9 and below, or fallback)
        try {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, safeName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(bytes);
                fos.flush();
            }
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "External public save failed: " + t.getMessage());
        }

        // 3. App-specific external files dir fallback
        try {
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) dir = getFilesDir();
            File file = new File(dir, safeName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(bytes);
                fos.flush();
            }
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "App-specific save failed: " + t.getMessage());
            return false;
        }
    }

    private String guessFileNameFromDispositionOrMime(String url, String contentDisposition, String mimeType) {
        String guessed = null;
        try {
            guessed = URLUtil.guessFileName(url, contentDisposition, mimeType);
        } catch (Throwable ignored) {}
        if (guessed != null && !guessed.trim().isEmpty() && !guessed.equalsIgnoreCase("downloadfile")) {
            return guessed;
        }
        if (contentDisposition != null && contentDisposition.contains("filename=")) {
            try {
                String sub = contentDisposition.substring(contentDisposition.indexOf("filename=") + 9);
                if (sub.startsWith("\"") && sub.indexOf("\"", 1) != -1) {
                    return sub.substring(1, sub.indexOf("\"", 1));
                }
                int end = sub.indexOf(";");
                return (end != -1 ? sub.substring(0, end) : sub).trim();
            } catch (Throwable ignored) {}
        }
        String ext = ".bin";
        if (mimeType != null) {
            String m = mimeType.toLowerCase();
            if (m.contains("image/png")) ext = ".png";
            else if (m.contains("image/jpeg") || m.contains("image/jpg")) ext = ".jpg";
            else if (m.contains("image/webp")) ext = ".webp";
            else if (m.contains("image/svg")) ext = ".svg";
            else if (m.contains("application/pdf")) ext = ".pdf";
            else if (m.contains("application/zip")) ext = ".zip";
            else if (m.contains("application/vnd.android.package-archive")) ext = ".apk";
            else if (m.contains("text/plain")) ext = ".txt";
            else if (m.contains("text/html")) ext = ".html";
            else if (m.contains("text/css")) ext = ".css";
            else if (m.contains("javascript") || m.contains("json")) ext = ".json";
            else if (m.contains("audio/mpeg") || m.contains("audio/mp3")) ext = ".mp3";
            else if (m.contains("video/mp4")) ext = ".mp4";
        }
        return "download_" + System.currentTimeMillis() + ext;
    }

    public void handleDownload(final String url, final String contentDisposition, final String mimeType, final String userAgent) {
        if (url == null || url.trim().isEmpty()) return;

        // 1. BLOB URL: Must be converted to base64 inside WebView JavaScript context
        if (url.startsWith("blob:")) {
            final String safeName = guessFileNameFromDispositionOrMime(url, contentDisposition, mimeType);
            String js = "javascript:(function(){" +
                    "try {" +
                    "  var xhr = new XMLHttpRequest();" +
                    "  xhr.open('GET', '" + url.replace("'", "\\'") + "', true);" +
                    "  xhr.responseType = 'blob';" +
                    "  xhr.onload = function() {" +
                    "    if (this.status === 200 || this.status === 0) {" +
                    "      var b = this.response;" +
                    "      var r = new FileReader();" +
                    "      r.onloadend = function() {" +
                    "        var bridge = window.AndroidBridge || window.NeoAndroid;" +
                    "        if (bridge && typeof bridge.saveFile === 'function') {" +
                    "          bridge.saveFile(r.result, '" + safeName.replace("'", "\\'") + "', '" + (mimeType != null ? mimeType.replace("'", "\\'") : "application/octet-stream") + "');" +
                    "        }" +
                    "      };" +
                    "      r.readAsDataURL(b);" +
                    "    }" +
                    "  };" +
                    "  xhr.onerror = function() {" +
                    "    var bridge = window.AndroidBridge || window.NeoAndroid;" +
                    "    if (bridge && typeof bridge.showToast === 'function') bridge.showToast('Gagal memproses berkas blob');" +
                    "  };" +
                    "  xhr.send();" +
                    "} catch (e) {" +
                    "  var bridge = window.AndroidBridge || window.NeoAndroid;" +
                    "  if (bridge && typeof bridge.showToast === 'function') bridge.showToast('Error unduh blob: ' + e.message);" +
                    "}" +
                    "})();";
            webView.evaluateJavascript(js, null);
            return;
        }

        // 2. DATA URL: Parse base64/plain content and write directly to Downloads
        if (url.startsWith("data:")) {
            try {
                final String safeName = guessFileNameFromDispositionOrMime(url, contentDisposition, mimeType);
                int commaIdx = url.indexOf(",");
                if (commaIdx != -1) {
                    String header = url.substring(0, commaIdx);
                    String dataPart = url.substring(commaIdx + 1);
                    byte[] bytes;
                    if (header.contains(";base64")) {
                        bytes = Base64.decode(dataPart, Base64.DEFAULT);
                    } else {
                        bytes = Uri.decode(dataPart).getBytes(StandardCharsets.UTF_8);
                    }
                    boolean saved = saveBytesToDownloads(bytes, safeName, mimeType);
                    if (saved) {
                        Toast.makeText(this, "Berkas disimpan di Downloads: " + safeName, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "Gagal menyimpan berkas di penyimpanan", Toast.LENGTH_SHORT).show();
                    }
                }
            } catch (Throwable t) {
                Toast.makeText(this, "Gagal mengunduh berkas data: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // 3. HTTP / HTTPS: Use DownloadManager (native background system download with notification)
        if (url.startsWith("http://") || url.startsWith("https://")) {
            try {
                DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm != null) {
                    Uri uri = Uri.parse(url);
                    DownloadManager.Request request = new DownloadManager.Request(uri);
                    String fileName = guessFileNameFromDispositionOrMime(url, contentDisposition, mimeType);
                    request.setTitle(fileName);
                    request.setDescription("Mengunduh dengan " + appName);
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                    try {
                        String cookie = CookieManager.getInstance().getCookie(url);
                        if (cookie != null) request.addRequestHeader("Cookie", cookie);
                    } catch (Throwable ignored) {}
                    if (userAgent != null && !userAgent.isEmpty()) {
                        request.addRequestHeader("User-Agent", userAgent);
                    }
                    try {
                        request.allowScanningByMediaScanner();
                    } catch (Throwable ignored) {}
                    dm.enqueue(request);
                    Toast.makeText(this, "Mengunduh berkas: " + fileName, Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (Throwable dmEx) {
                Log.w(TAG, "DownloadManager error, using background stream: " + dmEx.getMessage());
            }

            // Fallback: Background HTTP thread
            startBackgroundDownload(url, contentDisposition, mimeType, userAgent);
            return;
        }

        // 4. CONTENT / FILE URIs
        if (url.startsWith("content://") || url.startsWith("file://")) {
            try {
                Uri uri = Uri.parse(url);
                String safeName = guessFileNameFromDispositionOrMime(url, contentDisposition, mimeType);
                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    if (is != null) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int r;
                        while ((r = is.read(buf)) != -1) {
                            baos.write(buf, 0, r);
                        }
                        boolean saved = saveBytesToDownloads(baos.toByteArray(), safeName, mimeType);
                        if (saved) {
                            Toast.makeText(this, "Berkas disimpan di Downloads: " + safeName, Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "Content/File stream error: " + t.getMessage());
            }
        }

        // 5. External Intent Fallback with strict ActivityNotFoundException protection
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (ActivityNotFoundException anfe) {
            Toast.makeText(this, "Tidak ada aplikasi untuk menangani tautan ini.", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "Gagal mengunduh: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void startBackgroundDownload(final String urlStr, final String contentDisposition, final String mimeType, final String userAgent) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL u = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    conn.setInstanceFollowRedirects(true);
                    if (userAgent != null && !userAgent.isEmpty()) {
                        conn.setRequestProperty("User-Agent", userAgent);
                    }
                    try {
                        String cookie = CookieManager.getInstance().getCookie(urlStr);
                        if (cookie != null) conn.setRequestProperty("Cookie", cookie);
                    } catch (Throwable ignored) {}
                    conn.connect();

                    int code = conn.getResponseCode();
                    if (code >= 200 && code < 400) {
                        String disp = conn.getHeaderField("Content-Disposition");
                        if (disp == null) disp = contentDisposition;
                        String ct = conn.getContentType();
                        if (ct == null) ct = mimeType;
                        final String fileName = guessFileNameFromDispositionOrMime(urlStr, disp, ct);

                        try (InputStream is = conn.getInputStream();
                             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                            byte[] buf = new byte[8192];
                            int r;
                            while ((r = is.read(buf)) != -1) {
                                baos.write(buf, 0, r);
                            }
                            final boolean ok = saveBytesToDownloads(baos.toByteArray(), fileName, ct);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (ok) {
                                        Toast.makeText(MainActivity.this, "Berkas disimpan di Downloads: " + fileName, Toast.LENGTH_LONG).show();
                                    } else {
                                        Toast.makeText(MainActivity.this, "Gagal menyimpan berkas: " + fileName, Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                        }
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                safeOpenExternalUrl(urlStr);
                            }
                        });
                    }
                } catch (Throwable t) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            safeOpenExternalUrl(urlStr);
                        }
                    });
                }
            }
        }).start();
    }

    private void safeOpenExternalUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Tidak ada browser untuk membuka tautan.", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "Gagal membuka tautan: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_RESULT_CODE) {
            if (uploadMessage != null) {
                Uri[] results = null;
                if (resultCode == Activity.RESULT_OK && data != null) {
                    if (data.getClipData() != null) {
                        ClipData clip = data.getClipData();
                        results = new Uri[clip.getItemCount()];
                        for (int i = 0; i < clip.getItemCount(); i++) {
                            results[i] = clip.getItemAt(i).getUri();
                        }
                    } else if (data.getData() != null) {
                        results = new Uri[]{data.getData()};
                    }
                }
                uploadMessage.onReceiveValue(results);
                uploadMessage = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (pendingWebPermission != null) {
                pendingWebPermission.grant(pendingWebPermission.getResources());
                pendingWebPermission = null;
            }
            if (pendingGeoCallback != null && pendingGeoOrigin != null) {
                pendingGeoCallback.invoke(pendingGeoOrigin, true, false);
                pendingGeoCallback = null;
                pendingGeoOrigin = null;
            }
        }
    }

    public class NeoWebChromeClient extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
            uploadMessage = filePathCallback;
            try {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(Intent.createChooser(intent, "Pilih Berkas"), FILE_CHOOSER_RESULT_CODE);
                return true;
            } catch (ActivityNotFoundException anfe) {
                if (uploadMessage != null) {
                    uploadMessage.onReceiveValue(null);
                    uploadMessage = null;
                }
                Toast.makeText(MainActivity.this, "Tidak ada aplikasi pemilih berkas di perangkat.", Toast.LENGTH_SHORT).show();
                return false;
            } catch (Throwable t) {
                if (uploadMessage != null) {
                    uploadMessage.onReceiveValue(null);
                    uploadMessage = null;
                }
                return false;
            }
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            pendingWebPermission = request;
            request.grant(request.getResources());
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
            pendingGeoOrigin = origin;
            pendingGeoCallback = callback;
            callback.invoke(origin, true, false);
        }
    }

    public class NeoWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url == null) return false;

            // Internal web content
            if (url.startsWith("file:///") || url.startsWith("about:")) {
                return false;
            }

            // Route blob URLs and data URLs directly to download handler
            if (url.startsWith("blob:") || url.startsWith("data:")) {
                handleDownload(url, null, null, null);
                return true;
            }

            // Check if this is a direct downloadable file
            String lowerUrl = url.toLowerCase();
            if (lowerUrl.endsWith(".apk") || lowerUrl.endsWith(".zip") || lowerUrl.endsWith(".rar") ||
                lowerUrl.endsWith(".7z") || lowerUrl.endsWith(".tar") || lowerUrl.endsWith(".gz") ||
                lowerUrl.endsWith(".pdf") || lowerUrl.endsWith(".epub") || lowerUrl.endsWith(".bin") ||
                lowerUrl.contains("download=true") || lowerUrl.contains("dl=1")) {
                handleDownload(url, null, null, null);
                return true;
            }

            // Normal web page navigation
            if (url.startsWith("http://") || url.startsWith("https://")) {
                return false;
            }

            // Safe Intent Scheme handling (intent://)
            if (url.startsWith("intent://")) {
                try {
                    Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            startActivity(intent);
                            return true;
                        } catch (ActivityNotFoundException anfe) {
                            String fallbackUrl = intent.getStringExtra("browser_fallback_url");
                            if (fallbackUrl != null && !fallbackUrl.isEmpty()) {
                                view.loadUrl(fallbackUrl);
                                return true;
                            }
                            String pkg = intent.getPackage();
                            if (pkg != null && !pkg.isEmpty()) {
                                try {
                                    Intent market = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg));
                                    market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    startActivity(market);
                                    return true;
                                } catch (Throwable ignored) {
                                    view.loadUrl("https://play.google.com/store/apps/details?id=" + pkg);
                                    return true;
                                }
                            }
                            Toast.makeText(MainActivity.this, "Aplikasi untuk tautan ini belum terpasang", Toast.LENGTH_SHORT).show();
                            return true;
                        }
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "Intent parse error: " + t.getMessage());
                    return true;
                }
            }

            // Safe Market Scheme handling (market://)
            if (url.startsWith("market://")) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    return true;
                } catch (ActivityNotFoundException e) {
                    int idIdx = url.indexOf("id=");
                    if (idIdx != -1) {
                        String pkg = url.substring(idIdx + 3);
                        view.loadUrl("https://play.google.com/store/apps/details?id=" + pkg);
                    }
                    return true;
                }
            }

            // Safe handling for all other external protocols: tel:, mailto:, sms:, geo:, whatsapp:, etc.
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return true;
            } catch (ActivityNotFoundException anfe) {
                Toast.makeText(MainActivity.this, "Tidak ada aplikasi untuk membuka: " + url, Toast.LENGTH_SHORT).show();
                return true;
            } catch (Throwable t) {
                Log.e(TAG, "External link error: " + t.getMessage());
                return true;
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            injectClipboardPolyfill();
        }
    }

    private void injectClipboardPolyfill() {
        String js = "javascript:(function(){" +
                "if(window._neoClipboardReady)return;" +
                "window._neoClipboardReady=true;" +
                "function getBridge(){return window.AndroidBridge||window.NeoAndroid||null;}" +
                "if(!window.navigator.clipboard){window.navigator.clipboard={};}" +
                "window.navigator.clipboard.writeText=function(text){" +
                "  return new Promise(function(resolve,reject){" +
                "    try{var b=getBridge();if(b&&typeof b.copyToClipboard==='function'){b.copyToClipboard(String(text));resolve();return;}}catch(e){reject(e);}" +
                "    resolve();" +
                "  });" +
                "};" +
                "window.navigator.clipboard.readText=function(){" +
                "  return new Promise(function(resolve,reject){" +
                "    try{var b=getBridge();if(b&&typeof b.getFromClipboard==='function'){resolve(b.getFromClipboard()||'');return;}}catch(e){reject(e);}" +
                "    resolve('');" +
                "  });" +
                "};" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    public class NeoBridge {
        private final Handler mainHandler = new Handler(Looper.getMainLooper());
        private final ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        @JavascriptInterface
        public boolean isAvailable() {
            return true;
        }

        @JavascriptInterface
        public void showToast(final String message) {
            if (message == null || message.trim().isEmpty()) return;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public boolean copyToClipboard(final String text) {
            if (text == null) return false;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        ClipData clip = ClipData.newPlainText(appName, text);
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(MainActivity.this, "Teks disalin ke papan klip", Toast.LENGTH_SHORT).show();
                    } catch (Throwable t) {
                        Log.e(TAG, "Copy failed: " + t.getMessage());
                    }
                }
            });
            return true;
        }

        @JavascriptInterface
        public String getFromClipboard() {
            try {
                FutureTask<String> task = new FutureTask<>(new Callable<String>() {
                    @Override
                    public String call() {
                        if (clipboard != null && clipboard.hasPrimaryClip()) {
                            ClipData clip = clipboard.getPrimaryClip();
                            if (clip != null && clip.getItemCount() > 0) {
                                CharSequence cs = clip.getItemAt(0).coerceToText(MainActivity.this);
                                return (cs != null) ? cs.toString() : "";
                            }
                        }
                        return "";
                    }
                });
                mainHandler.post(task);
                return task.get(1500, TimeUnit.MILLISECONDS);
            } catch (Throwable t) {
                Log.e(TAG, "Clipboard read error: " + t.getMessage());
                return "";
            }
        }

        @JavascriptInterface
        public boolean downloadFile(final String url, final String fileName, final String mimeType) {
            if (url == null || url.trim().isEmpty()) return false;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    handleDownload(url, fileName, mimeType, null);
                }
            });
            return true;
        }

        @JavascriptInterface
        public boolean saveTextFile(final String content, final String fileName, final String mimeType) {
            if (content == null) return false;
            final String safeName = (fileName == null || fileName.trim().isEmpty()) ?
                    ("file_" + System.currentTimeMillis() + ".txt") : fileName;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                        boolean ok = saveBytesToDownloads(bytes, safeName, "text/plain");
                        if (ok) {
                            Toast.makeText(MainActivity.this, "Berkas disimpan di Downloads: " + safeName, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(MainActivity.this, "Gagal menyimpan berkas di penyimpanan", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Throwable t) {
                        Toast.makeText(MainActivity.this, "Gagal menyimpan berkas: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            });
            return true;
        }

        @JavascriptInterface
        public boolean saveFile(final String base64Data, final String fileName, final String mimeType) {
            if (base64Data == null) return false;
            final String safeName = (fileName == null || fileName.trim().isEmpty()) ?
                    ("file_" + System.currentTimeMillis() + ".bin") : fileName;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        String clean = base64Data.contains(",") ? base64Data.substring(base64Data.indexOf(",") + 1) : base64Data;
                        byte[] bytes = Base64.decode(clean, Base64.DEFAULT);
                        boolean ok = saveBytesToDownloads(bytes, safeName, mimeType);
                        if (ok) {
                            Toast.makeText(MainActivity.this, "Berkas disimpan di Downloads: " + safeName, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(MainActivity.this, "Gagal menyimpan berkas di penyimpanan", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Throwable t) {
                        Toast.makeText(MainActivity.this, "Gagal menyimpan berkas: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            });
            return true;
        }

        @JavascriptInterface
        public void vibrate(long durationMs) {
            try {
                long d = (durationMs > 0 && durationMs <= 5000) ? durationMs : 100L;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                    if (vm != null) vm.getDefaultVibrator().vibrate(VibrationEffect.createOneShot(d, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                    if (v != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            v.vibrate(VibrationEffect.createOneShot(d, VibrationEffect.DEFAULT_AMPLITUDE));
                        } else {
                            v.vibrate(d);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // ==================== REAL-ESRGAN NATIVE 4K AI INTEGRATION ====================

        @JavascriptInterface
        public boolean isRealEsrganAvailable() {
            return true;
        }

        @JavascriptInterface
        public String upscaleImage4K(String base64Image) {
            return upscaleImage(base64Image, 4);
        }

        @JavascriptInterface
        public String upscaleImage(String base64Image, int scaleFactor) {
            return RealEsrganNativeEngine.upscaleBase64Image(base64Image, scaleFactor);
        }

        @JavascriptInterface
        public String getAppInfo() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("appName", appName);
                obj.put("packageName", packageName);
                obj.put("platform", "Android");
                obj.put("realEsrganSupported", true);
                obj.put("aiRuntime", "Native Real-ESRGAN 4K Engine");
                obj.put("version", "2.0");
                return obj.toString();
            } catch (Throwable t) {
                return "{}";
            }
        }
    }
}
