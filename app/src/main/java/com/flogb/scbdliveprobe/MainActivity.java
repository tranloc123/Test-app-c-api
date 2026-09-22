package com.flogb.scbdliveprobe;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
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
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
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
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private WebView webView;
    private EditText userInput;
    private TextView statusView;
    private TextView logView;
    private ScrollView logScroll;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final StringBuilder logBuffer = new StringBuilder();
    private final AtomicInteger wsCount = new AtomicInteger();
    private final AtomicInteger domCount = new AtomicInteger();
    private final AtomicInteger netCount = new AtomicInteger();

    private String probeScript = "";
    private boolean documentStartInstalled = false;

    private static final int MAX_LOG_CHARS = 2_000_000;
    private static final Pattern INTERESTING =
            Pattern.compile("webcast|live|room|gift|comment|rank|viewer|im/fetch|message|event",
                    Pattern.CASE_INSENSITIVE);

    private static final String DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/131.0.0.0 Safari/537.36";

    @Override
    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        probeScript = readAsset("probe.js");
        WebView.setWebContentsDebuggingEnabled(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(12, 14, 18));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(6), dp(6), dp(6), dp(6));
        bar.setGravity(Gravity.CENTER_VERTICAL);

        userInput = new EditText(this);
        userInput.setSingleLine(true);
        userInput.setHint("@TikTok ID");
        userInput.setTextColor(Color.WHITE);
        userInput.setHintTextColor(Color.GRAY);
        userInput.setInputType(InputType.TYPE_CLASS_TEXT);
        userInput.setText(getPreferences(MODE_PRIVATE).getString("last_user", ""));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        bar.addView(userInput, inputLp);

        Button openBtn = button("MỞ LIVE");
        openBtn.setOnClickListener(v -> openLive());
        bar.addView(openBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));

        Button markBtn = button("MARK");
        markBtn.setOnClickListener(v -> addLog("MARK", "========== USER MARK =========="));
        bar.addView(markBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));

        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout statusBar = new LinearLayout(this);
        statusBar.setOrientation(LinearLayout.HORIZONTAL);
        statusBar.setPadding(dp(8), 0, dp(8), dp(4));
        statusView = new TextView(this);
        statusView.setTextColor(Color.rgb(116, 236, 161));
        statusView.setTextSize(12f);
        statusView.setText("SCBD LIVE PROBE V0.1 | ready");
        statusBar.addView(statusView, new LinearLayout.LayoutParams(0, dp(28), 1f));

        Button exportBtn = button("XUẤT LOG");
        exportBtn.setOnClickListener(v -> exportLog());
        statusBar.addView(exportBtn);

        Button copyBtn = button("COPY");
        copyBtn.setOnClickListener(v -> copyLog());
        statusBar.addView(copyBtn);

        Button clearBtn = button("XÓA");
        clearBtn.setOnClickListener(v -> clearLog());
        statusBar.addView(clearBtn);

        root.addView(statusBar);

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
        s.setUserAgentString(DESKTOP_UA);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new JsBridge(), "SCBD");

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try {
                WebViewCompat.addDocumentStartJavaScript(
                        webView,
                        probeScript,
                        Collections.singleton("*")
                );
                documentStartInstalled = true;
                addLog("INIT", "Document-start injection: SUPPORTED");
            } catch (Throwable t) {
                addLog("INIT", "Document-start injection failed: " + t);
            }
        } else {
            addLog("INIT", "Document-start injection: NOT SUPPORTED; fallback onPageFinished");
        }

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                addLog("CONSOLE", cm.message() + " @" + cm.lineNumber());
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                addLog("PAGE", "finished " + url);

                // Safe fallback/reinstall. probe.js exits immediately if already installed.
                view.evaluateJavascript(probeScript, null);
                updateStatus("page ready");
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                try {
                    String url = request.getUrl().toString();
                    if (INTERESTING.matcher(url).find()) {
                        addLog("NET_URL", request.getMethod() + " " + url);
                        netCount.incrementAndGet();
                        refreshCounters();
                    }
                } catch (Throwable ignored) {}
                return super.shouldInterceptRequest(view, request);
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

        LinearLayout.LayoutParams webLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(webView, webLp);

        logScroll = new ScrollView(this);
        logScroll.setFillViewport(true);
        logScroll.setBackgroundColor(Color.rgb(6, 7, 10));

        logView = new TextView(this);
        logView.setTextColor(Color.rgb(214, 221, 230));
        logView.setTextSize(10f);
        logView.setPadding(dp(8), dp(6), dp(8), dp(6));
        logView.setTextIsSelectable(true);

        logScroll.addView(logView, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(logScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(200)));

        setContentView(root);

        addLog("INIT", "No cookies/passwords/localStorage are exported by this probe.");
        addLog("INIT", "Use a unique comment such as SCBDTEST12345, then tap MARK before/after the event.");
        refreshCounters();
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(11f);
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
        s = s.replaceAll("[^A-Za-z0-9._-]", "");
        return s;
    }

    private void openLive() {
        String user = normalizeUser(userInput.getText().toString());
        if (user.isEmpty()) {
            Toast.makeText(this, "Nhập @TikTok ID trước", Toast.LENGTH_SHORT).show();
            return;
        }
        getPreferences(MODE_PRIVATE).edit().putString("last_user", user).apply();

        wsCount.set(0);
        domCount.set(0);
        netCount.set(0);
        refreshCounters();

        String url = "https://www.tiktok.com/@" + user + "/live";
        addLog("OPEN", url);
        updateStatus("loading @" + user);
        webView.loadUrl(url);
    }

    private void updateStatus(String state) {
        mainHandler.post(() ->
                statusView.setText("SCBD LIVE PROBE V0.1 | " + state +
                        " | docStart=" + (documentStartInstalled ? "YES" : "NO")));
    }

    private void refreshCounters() {
        mainHandler.post(() -> {
            String current = statusView.getText().toString();
            int ix = current.indexOf(" | WS=");
            if (ix >= 0) current = current.substring(0, ix);
            statusView.setText(current +
                    " | WS=" + wsCount.get() +
                    " DOM=" + domCount.get() +
                    " NET=" + netCount.get());
        });
    }

    private void addLog(String kind, String payload) {
        mainHandler.post(() -> {
            String time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
            String p = payload == null ? "" : payload;
            if (p.length() > 14000) p = p.substring(0, 14000) + "…[cut]";
            String line = "[" + time + "][" + kind + "] " + p + "\n";

            logBuffer.append(line);
            if (logBuffer.length() > MAX_LOG_CHARS) {
                int remove = logBuffer.length() - (MAX_LOG_CHARS * 3 / 4);
                logBuffer.delete(0, Math.max(0, remove));
                logBuffer.insert(0, "[LOG TRIMMED]\n");
            }

            logView.setText(logBuffer.toString());
            logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void clearLog() {
        logBuffer.setLength(0);
        logView.setText("");
        wsCount.set(0);
        domCount.set(0);
        netCount.set(0);
        addLog("CLEAR", "log cleared");
        refreshCounters();
    }

    private void copyLog() {
        ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cb.setPrimaryClip(ClipData.newPlainText("SCBD Live Probe", logBuffer.toString()));
        Toast.makeText(this, "Đã copy log", Toast.LENGTH_SHORT).show();
    }

    private void exportLog() {
        String name = "SCBD_LIVE_PROBE_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) +
                ".txt";
        byte[] bytes = logBuffer.toString().getBytes(StandardCharsets.UTF_8);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, name);
                values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                Uri uri = getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

                if (uri == null) throw new IllegalStateException("MediaStore insert failed");

                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os == null) throw new IllegalStateException("openOutputStream failed");
                    os.write(bytes);
                }
                Toast.makeText(this, "Đã lưu Download/" + name, Toast.LENGTH_LONG).show();
            } else {
                File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (dir == null) throw new IllegalStateException("No external files dir");
                File out = new File(dir, name);
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    fos.write(bytes);
                }
                Toast.makeText(this, "Đã lưu " + out.getAbsolutePath(), Toast.LENGTH_LONG).show();
            }
        } catch (Throwable t) {
            addLog("EXPORT_ERR", String.valueOf(t));
            Toast.makeText(this, "Xuất log lỗi: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String readAsset(String name) {
        try {
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(getAssets().open(name), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            return sb.toString();
        } catch (Throwable t) {
            return "try{SCBD.report('JS_ERR','asset load failed');}catch(e){}";
        }
    }

    public class JsBridge {
        @JavascriptInterface
        public void report(String kind, String payload) {
            String k = kind == null ? "JS" : kind;
            if (k.startsWith("WS_")) wsCount.incrementAndGet();
            if ("DOM".equals(k)) domCount.incrementAndGet();
            if (k.startsWith("FETCH_") || k.startsWith("XHR_") || "RESOURCE".equals(k)) {
                netCount.incrementAndGet();
            }
            addLog(k, payload);
            refreshCounters();
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
        if (webView != null) {
            webView.removeJavascriptInterface("SCBD");
            webView.destroy();
        }
        super.onDestroy();
    }
}
