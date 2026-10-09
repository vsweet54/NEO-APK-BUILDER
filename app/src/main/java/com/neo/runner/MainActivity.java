package com.neo.runner;

import android.app.Activity;
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
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
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
                handleDownload(url, mimeType);
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

    private void handleDownload(String url, String mimeType) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Throwable t) {
            Toast.makeText(this, "Gagal mengunduh: " + t.getMessage(), Toast.LENGTH_SHORT).show();
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
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(Intent.createChooser(intent, "Pilih Berkas"), FILE_CHOOSER_RESULT_CODE);
            return true;
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
            if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file:///")) {
                return false;
            }
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
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
        public boolean saveTextFile(final String content, final String fileName, final String mimeType) {
            if (content == null) return false;
            final String safeName = (fileName == null || fileName.trim().isEmpty()) ?
                    ("file_" + System.currentTimeMillis() + ".txt") : fileName;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                        if (!dir.exists()) dir.mkdirs();
                        File f = new File(dir, safeName);
                        try (FileOutputStream fos = new FileOutputStream(f)) {
                            fos.write(content.getBytes(StandardCharsets.UTF_8));
                        }
                        Toast.makeText(MainActivity.this, "Berkas disimpan di Downloads: " + safeName, Toast.LENGTH_LONG).show();
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
                        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                        if (!dir.exists()) dir.mkdirs();
                        File f = new File(dir, safeName);
                        try (FileOutputStream fos = new FileOutputStream(f)) {
                            fos.write(bytes);
                        }
                        Toast.makeText(MainActivity.this, "Berkas disimpan di Downloads: " + safeName, Toast.LENGTH_LONG).show();
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
