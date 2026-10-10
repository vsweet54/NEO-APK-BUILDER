package com.neo.runner;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.PictureInPictureParams;
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
import android.content.res.Configuration;
import android.graphics.Color;
import android.media.MediaScannerConnection;
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
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.util.Rational;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private boolean autoPipEnabled = false;
    private boolean enableDownloadBridge = true;
    private String downloadFolderPrimary = "Neo Downloader";
    private String downloadSubfolders = "mp4, mp3";
    private boolean showDownloadToast = true;

    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(4);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Hardware Acceleration to eliminate frame drops and tearing
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        );

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

                enableDownloadBridge = obj.optBoolean("enableDownloadBridge", true);
                downloadFolderPrimary = obj.optString("downloadFolderPrimary", "Neo Downloader");
                downloadSubfolders = obj.optString("downloadSubfolders", "mp4, mp3");
                showDownloadToast = obj.optBoolean("showDownloadToast", true);
            }
        } catch (Throwable ignored) {
        }
    }

    private void configureWebView() {
        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#060D17"));

        // Hardware acceleration is handled directly at the Window level.
        // LAYER_TYPE_NONE ensures direct GPU compositing without allocating redundant offscreen buffers,
        // eliminating frame drops, tearing, and stuttering during animations, 60fps/120fps scrolling, and Canvas rendering.
        webView.setLayerType(View.LAYER_TYPE_NONE, null);
        webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        webView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

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
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
        s.setRenderPriority(WebSettings.RenderPriority.HIGH);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            s.setSafeBrowsingEnabled(false);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            s.setOffscreenPreRaster(true);
        }

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
        if (enableDownloadBridge) {
            webView.addJavascriptInterface(new DownloadBridge(this), "AndroidBridge");
        }
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

    // ==================== CONTENT & MAGIC BYTE VALIDATOR ====================

    public static class FileFormatInfo {
        public final String extension;
        public final String mimeType;

        public FileFormatInfo(String extension, String mimeType) {
            this.extension = extension;
            this.mimeType = mimeType;
        }
    }

    private static boolean containsAsciiSequence(byte[] data, String pattern) {
        if (data == null || pattern == null || pattern.isEmpty()) return false;
        byte[] p = pattern.getBytes(StandardCharsets.US_ASCII);
        int maxScan = Math.min(data.length, 65536);
        for (int i = 0; i <= maxScan - p.length; i++) {
            boolean match = true;
            for (int j = 0; j < p.length; j++) {
                if (data[i + j] != p[j]) {
                    match = false;
                    break;
                }
            }
            if (match) return true;
        }
        return false;
    }

    /**
     * Inspects binary content magic numbers (signatures) to ensure downloaded files
     * receive their true, genuine file format and extension rather than a blind .bin fallback.
     */
    public static FileFormatInfo inspectMagicBytes(byte[] bytes, String rawName, String passedMime) {
        if (bytes == null || bytes.length == 0) {
            return new FileFormatInfo(".bin", "application/octet-stream");
        }

        int len = bytes.length;
        String lowerName = (rawName != null) ? rawName.toLowerCase() : "";

        // 1. PNG: 89 50 4E 47 0D 0A 1A 0A
        if (len >= 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E &&
                bytes[3] == 0x47 && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A) {
            return new FileFormatInfo(".png", "image/png");
        }

        // 2. JPEG: FF D8 FF
        if (len >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return new FileFormatInfo(".jpg", "image/jpeg");
        }

        // 3. GIF: GIF87a or GIF89a
        if (len >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8') {
            return new FileFormatInfo(".gif", "image/gif");
        }

        // 4. WEBP: RIFF....WEBP
        if (len >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F' &&
                bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return new FileFormatInfo(".webp", "image/webp");
        }

        // 5. BMP: BM
        if (len >= 2 && bytes[0] == 'B' && bytes[1] == 'M') {
            return new FileFormatInfo(".bmp", "image/bmp");
        }

        // 6. PDF: %PDF-
        if (len >= 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F') {
            return new FileFormatInfo(".pdf", "application/pdf");
        }

        // 7. ZIP / APK / JAR / DOCX / XLSX / EPUB: PK\x03\x04, PK\x05\x06, PK\x07\x08
        if (len >= 4 && bytes[0] == 'P' && bytes[1] == 'K' &&
                (bytes[2] == 0x03 || bytes[2] == 0x05 || bytes[2] == 0x07) &&
                (bytes[3] == 0x04 || bytes[3] == 0x06 || bytes[3] == 0x08)) {

            // Deep check: Android APK contains AndroidManifest.xml
            if (containsAsciiSequence(bytes, "AndroidManifest.xml") ||
                    lowerName.endsWith(".apk") ||
                    (passedMime != null && passedMime.contains("android.package-archive"))) {
                return new FileFormatInfo(".apk", "application/vnd.android.package-archive");
            }
            if (lowerName.endsWith(".epub") || (passedMime != null && passedMime.contains("epub"))) {
                return new FileFormatInfo(".epub", "application/epub+zip");
            }
            if (lowerName.endsWith(".docx") || (containsAsciiSequence(bytes, "[Content_Types].xml") && containsAsciiSequence(bytes, "word/"))) {
                return new FileFormatInfo(".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            }
            if (lowerName.endsWith(".xlsx") || (containsAsciiSequence(bytes, "[Content_Types].xml") && containsAsciiSequence(bytes, "xl/"))) {
                return new FileFormatInfo(".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            }
            return new FileFormatInfo(".zip", "application/zip");
        }

        // 8. 7Z: 37 7A BC AF 27 1C
        if (len >= 6 && (bytes[0] & 0xFF) == 0x37 && (bytes[1] & 0xFF) == 0x7A && (bytes[2] & 0xFF) == 0xBC &&
                (bytes[3] & 0xFF) == 0xAF && (bytes[4] & 0xFF) == 0x27 && (bytes[5] & 0xFF) == 0x1C) {
            return new FileFormatInfo(".7z", "application/x-7z-compressed");
        }

        // 9. RAR: Rar!
        if (len >= 4 && bytes[0] == 'R' && bytes[1] == 'a' && bytes[2] == 'r' && bytes[3] == '!') {
            return new FileFormatInfo(".rar", "application/vnd.rar");
        }

        // 10. GZIP: 1F 8B
        if (len >= 2 && (bytes[0] & 0xFF) == 0x1F && (bytes[1] & 0xFF) == 0x8B) {
            return new FileFormatInfo(".gz", "application/gzip");
        }

        // 11. MP3: ID3 or sync frame FF FB / FF F3 / FF F2
        if ((len >= 3 && bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3') ||
                (len >= 2 && (bytes[0] & 0xFF) == 0xFF && ((bytes[1] & 0xFF) & 0xE0) == 0xE0)) {
            return new FileFormatInfo(".mp3", "audio/mpeg");
        }

        // 12. MP4 / M4A: ftyp at offset 4
        if (len >= 8 && bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p') {
            return new FileFormatInfo(".mp4", "video/mp4");
        }

        // 13. OGG: OggS
        if (len >= 4 && bytes[0] == 'O' && bytes[1] == 'g' && bytes[2] == 'g' && bytes[3] == 'S') {
            return new FileFormatInfo(".ogg", "audio/ogg");
        }

        // 14. WAV: RIFF....WAVE
        if (len >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F' &&
                bytes[8] == 'W' && bytes[9] == 'A' && bytes[10] == 'V' && bytes[11] == 'E') {
            return new FileFormatInfo(".wav", "audio/wav");
        }

        // 15. FLAC: fLaC
        if (len >= 4 && bytes[0] == 'f' && bytes[1] == 'L' && bytes[2] == 'a' && bytes[3] == 'C') {
            return new FileFormatInfo(".flac", "audio/flac");
        }

        // 16. WebM / Matroska: 1A 45 DF A3
        if (len >= 4 && (bytes[0] & 0xFF) == 0x1A && (bytes[1] & 0xFF) == 0x45 &&
                (bytes[2] & 0xFF) == 0xDF && (bytes[3] & 0xFF) == 0xA3) {
            if (passedMime != null && passedMime.contains("audio")) {
                return new FileFormatInfo(".weba", "audio/webm");
            }
            return new FileFormatInfo(".webm", "video/webm");
        }

        // 17. Text-based detection: SVG, HTML, JSON, CSV, CSS, JS, TXT
        String textSnippet = "";
        try {
            int scanLen = Math.min(len, 1024);
            textSnippet = new String(bytes, 0, scanLen, StandardCharsets.UTF_8).trim();
        } catch (Throwable ignored) {}

        if (!textSnippet.isEmpty()) {
            String lowerSnippet = textSnippet.toLowerCase();
            if (lowerSnippet.startsWith("<svg") || (lowerSnippet.startsWith("<?xml") && lowerSnippet.contains("<svg"))) {
                return new FileFormatInfo(".svg", "image/svg+xml");
            }
            if (lowerSnippet.startsWith("<!doctype html") || lowerSnippet.startsWith("<html")) {
                return new FileFormatInfo(".html", "text/html");
            }
            if ((textSnippet.startsWith("{") && textSnippet.endsWith("}")) || (textSnippet.startsWith("[") && textSnippet.endsWith("]"))) {
                return new FileFormatInfo(".json", "application/json");
            }
            if (lowerName.endsWith(".css")) {
                return new FileFormatInfo(".css", "text/css");
            }
            if (lowerName.endsWith(".csv")) {
                return new FileFormatInfo(".csv", "text/csv");
            }
            if (lowerName.endsWith(".js")) {
                return new FileFormatInfo(".js", "application/javascript");
            }

            // Check if mostly printable characters
            boolean printable = true;
            for (int i = 0; i < Math.min(len, 256); i++) {
                int b = bytes[i] & 0xFF;
                if (b < 0x09 || (b > 0x0D && b < 0x20 && b != 0x1B)) {
                    printable = false;
                    break;
                }
            }
            if (printable) {
                return new FileFormatInfo(".txt", "text/plain");
            }
        }

        // 18. Fallback to MIME type mapping
        if (passedMime != null && !passedMime.trim().isEmpty() && !passedMime.equalsIgnoreCase("application/octet-stream")) {
            String m = passedMime.toLowerCase().trim();
            if (m.contains("image/png")) return new FileFormatInfo(".png", "image/png");
            if (m.contains("image/jpeg") || m.contains("image/jpg")) return new FileFormatInfo(".jpg", "image/jpeg");
            if (m.contains("image/webp")) return new FileFormatInfo(".webp", "image/webp");
            if (m.contains("image/gif")) return new FileFormatInfo(".gif", "image/gif");
            if (m.contains("image/svg")) return new FileFormatInfo(".svg", "image/svg+xml");
            if (m.contains("application/pdf")) return new FileFormatInfo(".pdf", "application/pdf");
            if (m.contains("application/zip")) return new FileFormatInfo(".zip", "application/zip");
            if (m.contains("android.package-archive")) return new FileFormatInfo(".apk", "application/vnd.android.package-archive");
            if (m.contains("text/plain")) return new FileFormatInfo(".txt", "text/plain");
            if (m.contains("text/html")) return new FileFormatInfo(".html", "text/html");
            if (m.contains("text/css")) return new FileFormatInfo(".css", "text/css");
            if (m.contains("json")) return new FileFormatInfo(".json", "application/json");
            if (m.contains("audio/mpeg") || m.contains("audio/mp3")) return new FileFormatInfo(".mp3", "audio/mpeg");
            if (m.contains("video/mp4")) return new FileFormatInfo(".mp4", "video/mp4");
            if (m.contains("audio/ogg") || m.contains("video/ogg")) return new FileFormatInfo(".ogg", "audio/ogg");
            if (m.contains("audio/wav")) return new FileFormatInfo(".wav", "audio/wav");
            if (m.contains("video/webm")) return new FileFormatInfo(".webm", "video/webm");
        }

        return new FileFormatInfo(".bin", "application/octet-stream");
    }

    /**
     * Resolves the true, genuine filename by combining the user/bridge requested name,
     * the detected magic bytes, and the verified MIME type. Never forces .bin when content is known.
     */
    public static String resolveGenuineFileName(byte[] bytes, String rawName, String passedMime) {
        FileFormatInfo info = inspectMagicBytes(bytes, rawName, passedMime);

        String clean = (rawName != null) ? rawName.trim().replaceAll("[/\\\\:*?\"<>|]", "_") : "";

        // Remove any generic downloadfile or UUID names
        if (clean.isEmpty() || clean.equalsIgnoreCase("downloadfile") || clean.equalsIgnoreCase("downloadfile.bin") ||
                clean.matches("^[0-9a-fA-F\\-]{36}(\\.bin)?$")) {
            return "download_" + System.currentTimeMillis() + info.extension;
        }

        // If the name ended with .bin, replace it with genuine detected extension if known
        if (clean.toLowerCase().endsWith(".bin")) {
            if (!info.extension.equalsIgnoreCase(".bin")) {
                return clean.substring(0, clean.length() - 4) + info.extension;
            }
            return clean;
        }

        // If the name lacks an extension, append genuine detected extension
        if (!clean.contains(".")) {
            return clean + info.extension;
        }

        // Check if current extension matches content, or if it should be corrected
        int dot = clean.lastIndexOf('.');
        String currentExt = (dot != -1) ? clean.substring(dot).toLowerCase() : "";
        if (!currentExt.isEmpty() && !info.extension.equalsIgnoreCase(".bin")) {
            // Harmonize obviously mismatched extensions
            if ((currentExt.equals(".txt") || currentExt.equals(".bin")) &&
                    (info.extension.equals(".png") || info.extension.equals(".jpg") || info.extension.equals(".pdf") ||
                     info.extension.equals(".apk") || info.extension.equals(".zip") || info.extension.equals(".mp4"))) {
                return clean.substring(0, dot) + info.extension;
            }
        }

        return clean;
    }

    public boolean saveBytesToDownloads(byte[] bytes, String fileName, String mimeType) {
        if (bytes == null || bytes.length == 0) return false;

        // Perform strict content-based format validation
        FileFormatInfo formatInfo = inspectMagicBytes(bytes, fileName, mimeType);
        final String safeName = resolveGenuineFileName(bytes, fileName, mimeType);
        final String effMime = formatInfo.mimeType;

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

                    // Scan file so it is immediately discoverable
                    notifyMediaScanner(safeName);
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
            notifyMediaScanner(file.getAbsolutePath());
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
            notifyMediaScanner(file.getAbsolutePath());
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "App-specific save failed: " + t.getMessage());
            return false;
        }
    }

    private void notifyMediaScanner(String pathOrName) {
        try {
            MediaScannerConnection.scanFile(this, new String[]{pathOrName}, null, null);
        } catch (Throwable ignored) {}
    }

    private String guessFileNameFromDispositionOrMime(String url, String contentDisposition, String mimeType) {
        // If contentDisposition is a direct, clean filename (e.g. from <a download="foo.png">)
        if (contentDisposition != null && !contentDisposition.trim().isEmpty()) {
            String trimmed = contentDisposition.trim();
            if (!trimmed.toLowerCase().contains("attachment") && !trimmed.contains(";")) {
                String sanitized = trimmed.replaceAll("[/\\\\:*?\"<>|]", "_");
                if (sanitized.contains(".") && !sanitized.endsWith(".bin")) {
                    return sanitized;
                }
            }
            if (trimmed.contains("filename=")) {
                try {
                    String sub = trimmed.substring(trimmed.indexOf("filename=") + 9);
                    if (sub.startsWith("\"") && sub.indexOf("\"", 1) != -1) {
                        return sub.substring(1, sub.indexOf("\"", 1));
                    }
                    int end = sub.indexOf(";");
                    return (end != -1 ? sub.substring(0, end) : sub).trim();
                } catch (Throwable ignored) {}
            }
        }

        // For blob and data URLs, check if mimeType has valid mapped extension
        if (url != null && (url.startsWith("blob:") || url.startsWith("data:"))) {
            String ext = "";
            if (mimeType != null) {
                String m = mimeType.toLowerCase();
                if (m.contains("image/png")) ext = ".png";
                else if (m.contains("image/jpeg") || m.contains("image/jpg")) ext = ".jpg";
                else if (m.contains("image/webp")) ext = ".webp";
                else if (m.contains("image/svg")) ext = ".svg";
                else if (m.contains("application/pdf")) ext = ".pdf";
                else if (m.contains("application/zip")) ext = ".zip";
                else if (m.contains("android.package-archive")) ext = ".apk";
                else if (m.contains("text/plain")) ext = ".txt";
                else if (m.contains("text/html")) ext = ".html";
                else if (m.contains("text/css")) ext = ".css";
                else if (m.contains("json")) ext = ".json";
                else if (m.contains("audio/mpeg") || m.contains("audio/mp3")) ext = ".mp3";
                else if (m.contains("video/mp4")) ext = ".mp4";
            }
            if (!ext.isEmpty()) {
                return "download_" + System.currentTimeMillis() + ext;
            }
            return ""; // Allow saveFile to resolve based on decoded bytes
        }

        String guessed = null;
        try {
            guessed = URLUtil.guessFileName(url, contentDisposition, mimeType);
        } catch (Throwable ignored) {}
        if (guessed != null && !guessed.trim().isEmpty() && !guessed.equalsIgnoreCase("downloadfile") &&
                !guessed.equalsIgnoreCase("downloadfile.bin")) {
            return guessed;
        }

        return "download_" + System.currentTimeMillis() + ".bin";
    }

    public void handleDownload(final String url, final String contentDisposition, final String mimeType, final String userAgent) {
        if (url == null || url.trim().isEmpty()) return;

        // 1. BLOB URL: Fetched via WebView JS context to obtain genuine Blob data & MIME type
        if (url.startsWith("blob:")) {
            final String safeName = (contentDisposition != null && !contentDisposition.trim().isEmpty() && !contentDisposition.toLowerCase().endsWith(".bin")) ?
                    guessFileNameFromDispositionOrMime(url, contentDisposition, mimeType) : "";
            String js = "javascript:(function(){" +
                    "try {" +
                    "  var targetUrl = '" + url.replace("'", "\\'") + "';" +
                    "  var regEntry = (window._neoBlobRegistry && window._neoBlobRegistry.get(targetUrl)) || null;" +
                    "  var suggestedName = '" + safeName.replace("'", "\\'") + "';" +
                    "  if (!suggestedName && regEntry && regEntry.name) suggestedName = regEntry.name;" +
                    "  var doSave = function(b, sName) {" +
                    "    var r = new FileReader();" +
                    "    r.onloadend = function(){" +
                    "      var bridge = window.AndroidBridge || window.NeoAndroid;" +
                    "      if (bridge && typeof bridge.saveFile === 'function') {" +
                    "        var m = b.type || '" + (mimeType != null ? mimeType.replace("'", "\\'") : "application/octet-stream") + "';" +
                    "        bridge.saveFile(r.result, sName, m);" +
                    "      }" +
                    "    };" +
                    "    r.readAsDataURL(b);" +
                    "  };" +
                    "  if (regEntry && regEntry.blob) {" +
                    "    doSave(regEntry.blob, suggestedName);" +
                    "    return;" +
                    "  }" +
                    "  fetch(targetUrl)" +
                    "  .then(function(res){ return res.blob(); })" +
                    "  .then(function(b){ doSave(b, suggestedName); })" +
                    "  .catch(function(err){" +
                    "    var xhr = new XMLHttpRequest();" +
                    "    xhr.open('GET', targetUrl, true);" +
                    "    xhr.responseType = 'blob';" +
                    "    xhr.onload = function(){" +
                    "      if(this.status === 200 || this.status === 0){" +
                    "        doSave(this.response, suggestedName);" +
                    "      }" +
                    "    };" +
                    "    xhr.onerror = function(){" +
                    "      var bridge = window.AndroidBridge || window.NeoAndroid;" +
                    "      if(bridge && typeof bridge.showToast === 'function') bridge.showToast('Gagal memproses berkas blob');" +
                    "    };" +
                    "    xhr.send();" +
                    "  });" +
                    "} catch(e) {" +
                    "  var bridge = window.AndroidBridge || window.NeoAndroid;" +
                    "  if(bridge && typeof bridge.showToast === 'function') bridge.showToast('Error unduh blob: ' + e.message);" +
                    "}" +
                    "})();";
            webView.evaluateJavascript(js, null);
            return;
        }

        // 2. DATA URL: Parse base64/plain content and write to Downloads on background executor
        if (url.startsWith("data:")) {
            ioExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        int commaIdx = url.indexOf(",");
                        if (commaIdx != -1) {
                            String header = url.substring(0, commaIdx);
                            String dataPart = url.substring(commaIdx + 1);

                            String extractedMime = mimeType;
                            if (header.contains(":") && header.contains(";")) {
                                extractedMime = header.substring(5, header.indexOf(";"));
                            }

                            byte[] bytes;
                            if (header.contains(";base64")) {
                                bytes = Base64.decode(dataPart, Base64.DEFAULT);
                            } else {
                                bytes = Uri.decode(dataPart).getBytes(StandardCharsets.UTF_8);
                            }

                            final String finalName = resolveGenuineFileName(bytes, contentDisposition, extractedMime);
                            final boolean saved = saveBytesToDownloads(bytes, finalName, extractedMime);
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (saved) {
                                        Toast.makeText(MainActivity.this, "Berkas disimpan di Downloads: " + finalName, Toast.LENGTH_LONG).show();
                                    } else {
                                        Toast.makeText(MainActivity.this, "Gagal menyimpan berkas di penyimpanan", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                        }
                    } catch (final Throwable t) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "Gagal mengunduh berkas data: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            });
            return;
        }

        // 3. HTTP / HTTPS: Use DownloadManager or background stream with content validation
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

            // Fallback: Background HTTP executor
            startBackgroundDownload(url, contentDisposition, mimeType, userAgent);
            return;
        }

        // 4. CONTENT / FILE URIs
        if (url.startsWith("content://") || url.startsWith("file://")) {
            ioExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        Uri uri = Uri.parse(url);
                        try (InputStream is = getContentResolver().openInputStream(uri)) {
                            if (is != null) {
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                byte[] buf = new byte[8192];
                                int r;
                                while ((r = is.read(buf)) != -1) {
                                    baos.write(buf, 0, r);
                                }
                                byte[] bytes = baos.toByteArray();
                                final String finalName = resolveGenuineFileName(bytes, contentDisposition, mimeType);
                                final boolean saved = saveBytesToDownloads(bytes, finalName, mimeType);
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (saved) {
                                            Toast.makeText(MainActivity.this, "Berkas disimpan di Downloads: " + finalName, Toast.LENGTH_LONG).show();
                                        }
                                    }
                                });
                                return;
                            }
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "Content/File stream error: " + t.getMessage());
                    }
                }
            });
            return;
        }

        // 5. External Intent Fallback
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
        ioExecutor.execute(new Runnable() {
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

                        try (InputStream is = conn.getInputStream();
                             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                            byte[] buf = new byte[8192];
                            int r;
                            while ((r = is.read(buf)) != -1) {
                                baos.write(buf, 0, r);
                            }
                            byte[] bytes = baos.toByteArray();
                            final String resolvedName = resolveGenuineFileName(bytes, disp, ct);
                            final boolean ok = saveBytesToDownloads(bytes, resolvedName, ct);
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (ok) {
                                        Toast.makeText(MainActivity.this, "Berkas disimpan di Downloads: " + resolvedName, Toast.LENGTH_LONG).show();
                                    } else {
                                        Toast.makeText(MainActivity.this, "Gagal menyimpan berkas: " + resolvedName, Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                        }
                    } else {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                safeOpenExternalUrl(urlStr);
                            }
                        });
                    }
                } catch (Throwable t) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            safeOpenExternalUrl(urlStr);
                        }
                    });
                }
            }
        });
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

    // ==================== PICTURE-IN-PICTURE (PiP) SUPPORT ====================

    public boolean enterPipMode(int aspectNumerator, int aspectDenominator) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false;
        }
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            Toast.makeText(this, "Perangkat tidak mendukung Picture-in-Picture", Toast.LENGTH_SHORT).show();
            return false;
        }
        try {
            PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();
            int num = (aspectNumerator > 0) ? aspectNumerator : 16;
            int den = (aspectDenominator > 0) ? aspectDenominator : 9;
            Rational rational = new Rational(num, den);
            float f = rational.floatValue();
            if (f >= 0.41841f && f <= 2.39f) {
                builder.setAspectRatio(rational);
            }
            return enterPictureInPictureMode(builder.build());
        } catch (Throwable t) {
            Log.e(TAG, "PiP error: " + t.getMessage());
            return false;
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (autoPipEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPipMode(16, 9);
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (webView != null) {
            webView.evaluateJavascript(
                "if (typeof window.onPipModeChanged === 'function') { window.onPipModeChanged(" + isInPictureInPictureMode + "); }" +
                "window.dispatchEvent(new CustomEvent('pipmodechange', { detail: { inPip: " + isInPictureInPictureMode + " } }));",
                null
            );
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
            ioExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                        final String finalName = resolveGenuineFileName(bytes, safeName, "text/plain");
                        final boolean ok = saveBytesToDownloads(bytes, finalName, "text/plain");
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (ok) {
                                    Toast.makeText(MainActivity.this, "Berkas disimpan di Downloads: " + finalName, Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(MainActivity.this, "Gagal menyimpan berkas di penyimpanan", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    } catch (Throwable t) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "Gagal menyimpan berkas: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            });
            return true;
        }

        @JavascriptInterface
        public boolean saveFile(final String base64Data, final String fileName, final String mimeType) {
            if (base64Data == null) return false;
            ioExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        String clean = base64Data.contains(",") ? base64Data.substring(base64Data.indexOf(",") + 1) : base64Data;
                        String detectedMime = mimeType;
                        if (base64Data.startsWith("data:") && base64Data.contains(";")) {
                            detectedMime = base64Data.substring(5, base64Data.indexOf(";"));
                        }
                        byte[] bytes = Base64.decode(clean, Base64.DEFAULT);

                        // Strict format & magic byte validation
                        final String resolvedName = resolveGenuineFileName(bytes, fileName, detectedMime);
                        FileFormatInfo formatInfo = inspectMagicBytes(bytes, resolvedName, detectedMime);
                        final boolean ok = saveBytesToDownloads(bytes, resolvedName, formatInfo.mimeType);

                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (ok) {
                                    Toast.makeText(MainActivity.this, "Berkas disimpan di Downloads: " + resolvedName, Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(MainActivity.this, "Gagal menyimpan berkas di penyimpanan", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    } catch (final Throwable t) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "Gagal menyimpan berkas: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            });
            return true;
        }

        @JavascriptInterface
        public boolean saveBlobData(final String base64Data, final String fileName, final String mimeType) {
            return saveFile(base64Data, fileName, mimeType);
        }

        // ==================== SYSTEM OVERLAY PERMISSION ====================

        @JavascriptInterface
        public boolean canDrawOverlays() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return Settings.canDrawOverlays(MainActivity.this);
            }
            return true;
        }

        @JavascriptInterface
        public boolean requestOverlayPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(MainActivity.this)) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Intent intent = new Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + getPackageName())
                                );
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                                Toast.makeText(MainActivity.this, "Aktifkan 'Izinkan ditampilkan di atas aplikasi lain'", Toast.LENGTH_LONG).show();
                            } catch (Throwable t) {
                                Toast.makeText(MainActivity.this, "Gagal membuka pengaturan overlay: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                    return true;
                }
            }
            return true;
        }

        // ==================== PICTURE-IN-PICTURE (PiP) ====================

        @JavascriptInterface
        public boolean enterPip(int aspectWidth, int aspectHeight) {
            final int w = (aspectWidth > 0) ? aspectWidth : 16;
            final int h = (aspectHeight > 0) ? aspectHeight : 9;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    enterPipMode(w, h);
                }
            });
            return true;
        }

        @JavascriptInterface
        public boolean isPipSupported() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);
            }
            return false;
        }

        @JavascriptInterface
        public boolean isInPipMode() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                return isInPictureInPictureMode();
            }
            return false;
        }

        @JavascriptInterface
        public void setAutoPip(boolean enable) {
            autoPipEnabled = enable;
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
                obj.put("pipSupported", isPipSupported());
                obj.put("overlaySupported", true);
                obj.put("realEsrganSupported", true);
                obj.put("downloadBridgeSupported", enableDownloadBridge);
                obj.put("primaryFolder", downloadFolderPrimary);
                obj.put("version", "2.1-Pro");
                return obj.toString();
            } catch (Throwable t) {
                return "{}";
            }
        }
    }

    // ==================== DOWNLOAD BRIDGE (MEDIASTORE SUBFOLDER STORAGE) ====================

    public static class DownloadSaveSession {
        final String saveId;
        final String subfolder;
        final String sanitizedName;
        final String mimeType;
        final OutputStream outputStream;
        final Uri mediaStoreUri;
        final File targetFile;
        final String relativeDisplayPath;

        public DownloadSaveSession(String saveId, String subfolder, String sanitizedName, String mimeType,
                                   OutputStream outputStream, Uri mediaStoreUri, File targetFile, String relativeDisplayPath) {
            this.saveId = saveId;
            this.subfolder = subfolder;
            this.sanitizedName = sanitizedName;
            this.mimeType = mimeType;
            this.outputStream = outputStream;
            this.mediaStoreUri = mediaStoreUri;
            this.targetFile = targetFile;
            this.relativeDisplayPath = relativeDisplayPath;
        }
    }

    public class DownloadBridge {
        private final Context bridgeContext;
        private final java.util.concurrent.ConcurrentHashMap<String, DownloadSaveSession> activeSaveSessions =
                new java.util.concurrent.ConcurrentHashMap<>();

        public DownloadBridge(Context context) {
            this.bridgeContext = context;
        }

        private boolean isSubfolderAllowed(String subfolder) {
            if (subfolder == null || subfolder.trim().isEmpty()) return false;
            String clean = subfolder.trim().toLowerCase();
            String[] configured = downloadSubfolders.split(",");
            java.util.HashSet<String> validSet = new java.util.HashSet<>();
            for (String s : configured) {
                String t = s.trim().toLowerCase();
                if (!t.isEmpty()) validSet.add(t);
            }
            if (validSet.isEmpty()) {
                validSet.add("mp4");
                validSet.add("mp3");
            }
            return validSet.contains(clean);
        }

        private String sanitizeFileName(String filename) {
            if (filename == null || filename.trim().isEmpty()) {
                return "file_" + System.currentTimeMillis();
            }
            String name = filename.replace('\\', '/').trim();
            int slash = name.lastIndexOf('/');
            if (slash != -1) {
                name = name.substring(slash + 1);
            }
            name = name.replace("..", "_");
            name = name.replaceAll("[/\\\\:*?\"<>|]", "_");
            if (name.trim().isEmpty() || name.equals(".") || name.equals("_")) {
                name = "file_" + System.currentTimeMillis();
            }
            return name;
        }

        @JavascriptInterface
        public boolean isAvailable() {
            return true;
        }

        @JavascriptInterface
        public String beginSave(final String subfolder, final String filename, final String mimeType) {
            try {
                FutureTask<String> task = new FutureTask<>(new Callable<String>() {
                    @Override
                    public String call() {
                        try {
                            String sub = (subfolder != null) ? subfolder.trim().toLowerCase() : "";
                            if (!isSubfolderAllowed(sub)) {
                                return "ERROR: Subfolder tidak diizinkan. Hanya 'mp4' atau 'mp3' yang diterima.";
                            }

                            String safeName = sanitizeFileName(filename);
                            String effMime = mimeType;
                            if (effMime == null || effMime.trim().isEmpty()) {
                                if ("mp4".equals(sub)) effMime = "video/mp4";
                                else if ("mp3".equals(sub)) effMime = "audio/mpeg";
                                else effMime = "application/octet-stream";
                            } else {
                                effMime = effMime.trim();
                            }

                            // Ensure valid extension for subfolder
                            if ("mp4".equals(sub) && !safeName.toLowerCase().endsWith(".mp4")) {
                                int dot = safeName.lastIndexOf('.');
                                safeName = (dot != -1) ? safeName.substring(0, dot) + ".mp4" : safeName + ".mp4";
                            } else if ("mp3".equals(sub) && !safeName.toLowerCase().endsWith(".mp3")) {
                                int dot = safeName.lastIndexOf('.');
                                safeName = (dot != -1) ? safeName.substring(0, dot) + ".mp3" : safeName + ".mp3";
                            }

                            String primary = (downloadFolderPrimary != null && !downloadFolderPrimary.trim().isEmpty()) ?
                                    downloadFolderPrimary.trim() : "Neo Downloader";
                            String relativeDir = "Download/" + primary + "/" + sub;
                            String saveId = "save_" + System.currentTimeMillis() + "_" + java.util.UUID.randomUUID().toString().substring(0, 8);

                            // Android 10+ (API 29+): MediaStore scoped storage with relative path
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                try {
                                    ContentResolver resolver = getContentResolver();
                                    ContentValues values = new ContentValues();
                                    values.put(MediaStore.Downloads.DISPLAY_NAME, safeName);
                                    values.put(MediaStore.Downloads.MIME_TYPE, effMime);
                                    values.put(MediaStore.Downloads.RELATIVE_PATH, relativeDir + "/");
                                    values.put(MediaStore.Downloads.IS_PENDING, 1);

                                    Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                                    if (uri != null) {
                                        OutputStream os = resolver.openOutputStream(uri);
                                        if (os != null) {
                                            DownloadSaveSession session = new DownloadSaveSession(
                                                saveId, sub, safeName, effMime, os, uri, null, relativeDir + "/" + safeName
                                            );
                                            activeSaveSessions.put(saveId, session);
                                            return saveId;
                                        }
                                    }
                                } catch (Throwable t) {
                                    Log.w(TAG, "MediaStore insert fallback to public storage: " + t.getMessage());
                                }
                            }

                            // Android 9 and below: Direct public downloads storage
                            File baseDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                            File targetDir = new File(new File(baseDownloads, primary), sub);
                            if (!targetDir.exists()) {
                                targetDir.mkdirs();
                            }

                            File destFile = new File(targetDir, safeName);
                            if (destFile.exists()) {
                                int dot = safeName.lastIndexOf('.');
                                String base = (dot != -1) ? safeName.substring(0, dot) : safeName;
                                String ext = (dot != -1) ? safeName.substring(dot) : "";
                                destFile = new File(targetDir, base + "_" + System.currentTimeMillis() + ext);
                            }

                            FileOutputStream fos = new FileOutputStream(destFile);
                            DownloadSaveSession session = new DownloadSaveSession(
                                saveId, sub, destFile.getName(), effMime, fos, null, destFile, relativeDir + "/" + destFile.getName()
                            );
                            activeSaveSessions.put(saveId, session);
                            return saveId;
                        } catch (Throwable t) {
                            Log.e(TAG, "beginSave error: " + t.getMessage(), t);
                            return "ERROR: " + (t.getMessage() != null ? t.getMessage() : "Kesalahan inisialisasi penyimpanan berkas");
                        }
                    }
                });

                ioExecutor.execute(task);
                return task.get(30, TimeUnit.SECONDS);
            } catch (Throwable t) {
                Log.e(TAG, "beginSave async error: " + t.getMessage(), t);
                return "ERROR: " + (t.getMessage() != null ? t.getMessage() : "Timeout atau kesalahan proses");
            }
        }

        @JavascriptInterface
        public boolean appendChunk(final String saveId, final String base64Chunk) {
            if (saveId == null || saveId.trim().isEmpty() || base64Chunk == null) return false;
            final DownloadSaveSession session = activeSaveSessions.get(saveId);
            if (session == null) return false;

            try {
                FutureTask<Boolean> task = new FutureTask<>(new Callable<Boolean>() {
                    @Override
                    public Boolean call() {
                        try {
                            String clean = base64Chunk.contains(",") ?
                                    base64Chunk.substring(base64Chunk.indexOf(",") + 1) : base64Chunk;
                            byte[] bytes = Base64.decode(clean, Base64.DEFAULT);
                            if (bytes != null && bytes.length > 0) {
                                session.outputStream.write(bytes);
                                session.outputStream.flush();
                            }
                            return true;
                        } catch (Throwable t) {
                            Log.e(TAG, "appendChunk error: " + t.getMessage(), t);
                            return false;
                        }
                    }
                });

                ioExecutor.execute(task);
                return task.get(60, TimeUnit.SECONDS);
            } catch (Throwable t) {
                Log.e(TAG, "appendChunk async error: " + t.getMessage(), t);
                return false;
            }
        }

        @JavascriptInterface
        public String finishSave(final String saveId) {
            if (saveId == null || saveId.trim().isEmpty()) return "ERROR: saveId kosong";
            final DownloadSaveSession session = activeSaveSessions.remove(saveId);
            if (session == null) return "ERROR: Sesi simpan tidak ditemukan atau sudah selesai";

            try {
                FutureTask<String> task = new FutureTask<>(new Callable<String>() {
                    @Override
                    public String call() {
                        try {
                            try {
                                session.outputStream.flush();
                                session.outputStream.close();
                            } catch (Throwable ignored) {}

                            String finalPath = session.relativeDisplayPath;

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && session.mediaStoreUri != null) {
                                ContentResolver resolver = getContentResolver();
                                ContentValues values = new ContentValues();
                                values.put(MediaStore.Downloads.IS_PENDING, 0);
                                resolver.update(session.mediaStoreUri, values, null, null);
                                notifyMediaScanner(finalPath);
                            } else if (session.targetFile != null) {
                                notifyMediaScanner(session.targetFile.getAbsolutePath());
                                finalPath = session.relativeDisplayPath;
                            }

                            final String finalFolderDesc = "Download/" +
                                    ((downloadFolderPrimary != null && !downloadFolderPrimary.trim().isEmpty()) ?
                                            downloadFolderPrimary.trim() : "Neo Downloader") +
                                    "/" + session.subfolder;

                            if (showDownloadToast) {
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(MainActivity.this, "Tersimpan di " + finalFolderDesc, Toast.LENGTH_LONG).show();
                                    }
                                });
                            }

                            return finalPath;
                        } catch (Throwable t) {
                            Log.e(TAG, "finishSave error: " + t.getMessage(), t);
                            return "ERROR: " + (t.getMessage() != null ? t.getMessage() : "Gagal menyelesaikan penulisan berkas");
                        }
                    }
                });

                ioExecutor.execute(task);
                return task.get(30, TimeUnit.SECONDS);
            } catch (Throwable t) {
                Log.e(TAG, "finishSave async error: " + t.getMessage(), t);
                return "ERROR: " + (t.getMessage() != null ? t.getMessage() : "Timeout penyelesaian berkas");
            }
        }
    }
}
