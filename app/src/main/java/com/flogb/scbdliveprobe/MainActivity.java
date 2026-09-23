package com.flogb.scbddevshell;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final String PROBE_URL = "http://127.0.0.1:8788/probe.js";
    private static final String VERSION_URL = "http://127.0.0.1:8788/probe_version.txt";
    private static final int DISPLAY_LINES = 70;
    private static final int MAX_LOG_CHARS = 700_000;

    private WebView webView;
    private EditText userInput;
    private TextView statusView;
    private TextView logView;
    private ScrollView logScroll;
    private Button captureBtn;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioPool = Executors.newSingleThreadExecutor();

    private final ConcurrentLinkedQueue<String> pendingLines = new ConcurrentLinkedQueue<>();
    private final ArrayDeque<String> recentLines = new ArrayDeque<>();
    private final StringBuilder fullLog = new StringBuilder();

    private String bootstrapScript = "";
    private String fallbackProbe = "";
    private boolean docStartInstalled = false;
    private volatile boolean captureActive = false;
    private volatile long captureUntilMs = 0L;
    private volatile String probeVersion = "none";
    private volatile String probeState = "OFFLINE";

    private final Runnable flushRunnable = new Runnable() {
        @Override
        public void run() {
            flushLogs();
            mainHandler.postDelayed(this, 400);
        }
    };

    private final Runnable tickerRunnable = new Runnable() {
        @Override
        public void run() {
            if (captureActive) {
                long left = captureUntilMs - System.currentTimeMillis();
                if (left <= 0) {
                    captureActive = false;
                    captureBtn.setText("CAPTURE 10s");
                } else {
                    captureBtn.setText(String.format(Locale.US, "%.1fs", left / 1000.0));
                }
            }
            refreshStatus();
            mainHandler.postDelayed(this, 300);
        }
    };

    @Override
    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        bootstrapScript = readAsset("bootstrap.js");
        fallbackProbe = readAsset("probe_fallback.js");

        WebView.setWebContentsDebuggingEnabled(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(10, 12, 16));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setPadding(dp(6), dp(5), dp(6), dp(3));

        userInput = new EditText(this);
        userInput.setSingleLine(true);
        userInput.setHint("@TikTok ID");
        userInput.setTextColor(Color.WHITE);
        userInput.setHintTextColor(Color.GRAY);
        userInput.setInputType(InputType.TYPE_CLASS_TEXT);
        userInput.setText(getPreferences(MODE_PRIVATE).getString("last_user", ""));
        row1.addView(userInput, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button openBtn = button("MỞ LIVE");
        openBtn.setOnClickListener(v -> openLive());
        row1.addView(openBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));

        Button reloadBtn = button("RELOAD PROBE");
        reloadBtn.setOnClickListener(v -> reloadProbe());
        row1.addView(reloadBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));

        root.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(dp(6), 0, dp(6), dp(4));

        statusView = new TextView(this);
        statusView.setTextColor(Color.rgb(116, 236, 161));
        statusView.setTextSize(11f);
        statusView.setGravity(Gravity.CENTER_VERTICAL);
        row2.addView(statusView, new LinearLayout.LayoutParams(0, dp(46), 1f));

        captureBtn = button("CAPTURE 10s");
        captureBtn.setOnClickListener(v -> startCapture(10_000));
        row2.addView(captureBtn);

        Button exportBtn = button("XUẤT LOG");
        exportBtn.setOnClickListener(v -> exportLog());
        row2.addView(exportBtn);

        Button clearBtn = button("XÓA");
        clearBtn.setOnClickListener(v -> clearLog());
        row2.addView(clearBtn);

        root.addView(row2);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadsImagesAutomatically(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Safari/537.36"
        );

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new JsBridge(), "SCBD");

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try {
                WebViewCompat.addDocumentStartJavaScript(
                        webView,
                        bootstrapScript,
                        Collections.singleton("*")
                );
                docStartInstalled = true;
                addLog("INIT", "document-start bootstrap installed");
            } catch (Throwable t) {
                addLog("INIT", "document-start install failed: " + t);
            }
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                addLog("PAGE", "finished " + url);

                // Fallback only if document-start is unavailable.
                if (!docStartInstalled) {
                    view.evaluateJavascript(bootstrapScript, null);
                }

                // Try local hot probe first.
                reloadProbe();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String scheme = u.getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    return false;
                }
                addLog("NAV_BLOCK", String.valueOf(u));
                return true;
            }
        });

        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        logScroll = new ScrollView(this);
        logScroll.setBackgroundColor(Color.rgb(5, 6, 9));

        logView = new TextView(this);
        logView.setTextColor(Color.rgb(210, 218, 228));
        logView.setTextSize(9f);
        logView.setPadding(dp(8), dp(4), dp(8), dp(4));
        logView.setTextIsSelectable(true);

        logScroll.addView(logView, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(logScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(120)));

        setContentView(root);

        addLog("INIT", "SCBD Dev Shell V2");
        addLog("INIT", "Hot probe source: " + PROBE_URL);
        addLog("INIT", "Full-frame bridge enabled. Future decoder/filter changes live in probe.js.");

        mainHandler.post(flushRunnable);
        mainHandler.post(tickerRunnable);

        // Check local server immediately.
        reloadProbe();
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(9.5f);
        b.setAllCaps(false);
        return b;
    }

    private int dp(int n) {
        return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String normalizeUser(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.startsWith("http://") || s.startsWith("https://")) {
            int at = s.indexOf("/@");
            if (at >= 0) {
                s = s.substring(at + 2);
                int slash = s.indexOf('/');
                if (slash >= 0) s = s.substring(0, slash);
            }
        }
        while (s.startsWith("@")) s = s.substring(1);
        return s.replaceAll("[^A-Za-z0-9._-]", "");
    }

    private void openLive() {
        String user = normalizeUser(userInput.getText().toString());
        if (user.isEmpty()) {
            Toast.makeText(this, "Nhập @TikTok ID", Toast.LENGTH_SHORT).show();
            return;
        }

        getPreferences(MODE_PRIVATE).edit().putString("last_user", user).apply();
        addLog("OPEN", "@" + user);
        webView.loadUrl("https://www.tiktok.com/@" + user + "/live");
    }

    private String httpGet(String urlText) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlText).openConnection();
        c.setConnectTimeout(1800);
        c.setReadTimeout(2500);
        c.setUseCaches(false);
        c.setRequestProperty("Cache-Control", "no-cache");
        c.setRequestProperty("Pragma", "no-cache");

        int status = c.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("HTTP " + status);
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(
                c.getInputStream(), StandardCharsets.UTF_8));

        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line).append('\n');
        }
        br.close();
        c.disconnect();
        return sb.toString();
    }

    private void reloadProbe() {
        probeState = "LOADING";
        refreshStatus();

        ioPool.submit(() -> {
            try {
                String version;
                try {
                    version = httpGet(VERSION_URL + "?t=" + System.currentTimeMillis()).trim();
                } catch (Throwable ignored) {
                    version = "local";
                }

                String code = httpGet(PROBE_URL + "?t=" + System.currentTimeMillis());
                final String v = version;

                mainHandler.post(() -> {
                    if (webView == null) return;

                    String dispose =
                            "try{if(window.__SCBD_LOCAL_PROBE__&&" +
                            "window.__SCBD_LOCAL_PROBE__.dispose){" +
                            "window.__SCBD_LOCAL_PROBE__.dispose();}" +
                            "}catch(e){};";

                    webView.evaluateJavascript(dispose + "\n" + code, value -> {
                        probeVersion = v;
                        probeState = "LOCAL";
                        addLog("PROBE", "loaded local " + v);
                        refreshStatus();
                    });
                });

            } catch (Throwable t) {
                mainHandler.post(() -> {
                    probeState = "FALLBACK";
                    probeVersion = "fallback-1";
                    addLog("PROBE", "local server offline, using fallback: " + t.getMessage());

                    if (webView != null) {
                        webView.evaluateJavascript(fallbackProbe, null);
                    }
                    refreshStatus();
                });
            }
        });
    }

    private void startCapture(long ms) {
        if (webView == null) return;

        captureActive = true;
        captureUntilMs = System.currentTimeMillis() + ms;
        addLog("MARK", "========== CAPTURE START ==========");

        String js =
                "(function(){try{" +
                "if(window.__SCBD_LOCAL_PROBE__&&window.__SCBD_LOCAL_PROBE__.startCapture){" +
                "window.__SCBD_LOCAL_PROBE__.startCapture(" + ms + ");return 'OK';" +
                "}return 'NO_PROBE';" +
                "}catch(e){return 'ERR:'+e;}})();";

        webView.evaluateJavascript(js, value -> addLog("CAPTURE_CMD", String.valueOf(value)));

        mainHandler.postDelayed(() -> {
            captureActive = false;
            if (captureBtn != null) captureBtn.setText("CAPTURE 10s");
        }, ms + 250);
    }

    private void refreshStatus() {
        mainHandler.post(() -> {
            if (statusView == null) return;
            statusView.setText(
                    "DEV SHELL V2 | docStart=" + (docStartInstalled ? "YES" : "NO") +
                    "\nProbe=" + probeState + " " + probeVersion
            );
        });
    }

    private void addLog(String kind, String payload) {
        String time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
        String p = payload == null ? "" : payload;
        if (p.length() > 6500) p = p.substring(0, 6500) + "…[cut]";
        pendingLines.add("[" + time + "][" + kind + "] " + p);
    }

    private void flushLogs() {
        boolean changed = false;
        String line;

        while ((line = pendingLines.poll()) != null) {
            changed = true;

            fullLog.append(line).append('\n');
            if (fullLog.length() > MAX_LOG_CHARS) {
                int remove = fullLog.length() - (MAX_LOG_CHARS * 3 / 4);
                fullLog.delete(0, Math.max(0, remove));
                fullLog.insert(0, "[FULL LOG TRIMMED]\n");
            }

            recentLines.addLast(line);
            while (recentLines.size() > DISPLAY_LINES) recentLines.removeFirst();
        }

        if (!changed || logView == null) return;

        StringBuilder display = new StringBuilder();
        for (String s : recentLines) display.append(s).append('\n');

        logView.setText(display.toString());
        logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void clearLog() {
        pendingLines.clear();
        recentLines.clear();
        fullLog.setLength(0);
        if (logView != null) logView.setText("");
        addLog("CLEAR", "log cleared");
    }

    private void exportLog() {
        flushLogs();

        String name = "SCBD_DEV_SHELL_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) +
                ".txt";

        byte[] bytes = fullLog.toString().getBytes(StandardCharsets.UTF_8);

        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, name);
            values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            }

            Uri uri = getContentResolver().insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

            if (uri == null) throw new IllegalStateException("MediaStore insert failed");

            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os == null) throw new IllegalStateException("openOutputStream failed");
                os.write(bytes);
            }

            Toast.makeText(this, "Đã lưu Download/" + name, Toast.LENGTH_LONG).show();

        } catch (Throwable t) {
            addLog("EXPORT_ERR", String.valueOf(t));
            Toast.makeText(this, "Xuất log lỗi: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String readAsset(String name) {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    getAssets().open(name), StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            String line;

            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            return sb.toString();

        } catch (Throwable t) {
            return "";
        }
    }

    public class JsBridge {
        @JavascriptInterface
        public void report(String kind, String payload) {
            if ("PROBE_READY".equals(kind)) {
                probeVersion = payload == null ? "unknown" : payload;
                if ("FALLBACK".equals(probeState)) {
                    probeState = "FALLBACK";
                } else {
                    probeState = "LOCAL";
                }
                refreshStatus();
            }

            if ("CAPTURE_START".equals(kind)) {
                captureActive = true;
            }

            if ("CAPTURE_END".equals(kind)) {
                captureActive = false;
                captureUntilMs = 0L;
                mainHandler.post(() -> {
                    if (captureBtn != null) captureBtn.setText("CAPTURE 10s");
                });
            }

            addLog(kind == null ? "JS" : kind, payload);
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
    protected void onDestroy() {
        mainHandler.removeCallbacks(flushRunnable);
        mainHandler.removeCallbacks(tickerRunnable);
        ioPool.shutdownNow();

        if (webView != null) {
            try {
                webView.evaluateJavascript(
                        "try{if(window.__SCBD_LOCAL_PROBE__&&" +
                        "window.__SCBD_LOCAL_PROBE__.dispose){" +
                        "window.__SCBD_LOCAL_PROBE__.dispose();}}catch(e){}",
                        null
                );
            } catch (Throwable ignored) {}

            webView.removeJavascriptInterface("SCBD");
            webView.destroy();
        }

        super.onDestroy();
    }
}
