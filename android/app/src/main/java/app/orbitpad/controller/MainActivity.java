package app.orbitpad.controller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
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
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native Android connection shell. The receiver supplies the shared controller UI.
 * No JavaScript-to-Java bridge is exposed to network content. */
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(20, 18, 24);
    private static final int FG = Color.rgb(230, 224, 233);
    private static final int MINT = Color.rgb(208, 188, 255);
    private static final int MUTED = Color.rgb(202, 196, 208);
    private static final int SURFACE = Color.rgb(43, 41, 48);
    private static final int OUTLINE = Color.rgb(147, 143, 153);
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private EditText address;
    private EditText code;
    private Button connectButton;
    private TextView message;
    private WebView web;
    private String origin;
    private boolean checking;
    private int generation;
    private boolean stopped;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        showConnection(null);
    }

    private int dp(float size) { return Math.round(size * getResources().getDisplayMetrics().density); }
    private GradientDrawable rounded(int color, int border, int radius) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(color); result.setCornerRadius(dp(radius));
        if (border != 0) result.setStroke(dp(1), border);
        return result;
    }
    private TextView text(String content, int size, int color) {
        TextView result = new TextView(this); result.setText(content); result.setTextSize(size);
        result.setTextColor(color); result.setLineSpacing(dp(4), 1); return result;
    }
    private void add(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(top); parent.addView(child, params);
    }
    private EditText field(String hint) {
        EditText result = new EditText(this); result.setSingleLine(true); result.setTextColor(FG);
        result.setTextSize(16); result.setHintTextColor(OUTLINE); result.setHint(hint);
        result.setMinHeight(dp(56)); result.setPadding(dp(16), dp(16), dp(16), dp(16));
        result.setBackground(rounded(BG, OUTLINE, 12));
        result.setOnFocusChangeListener((v, focused) -> result.setBackground(rounded(BG, focused ? MINT : OUTLINE, 12)));
        return result;
    }

    private void showConnection(String error) {
        generation++;
        if (web != null) {
            web.evaluateJavascript("window.dispatchEvent(new Event('blur'));", null);
            web.stopLoading(); web.loadUrl("about:blank"); web.destroy(); web = null;
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        origin = null; checking = false;
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(BG); scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_VERTICAL); content.setPadding(dp(28), dp(35), dp(28), dp(30));
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(dp(28) + insets.getSystemWindowInsetLeft(), dp(28) + insets.getSystemWindowInsetTop(),
                dp(28) + insets.getSystemWindowInsetRight(), dp(24) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        scroll.addView(content, new ScrollView.LayoutParams(-1, -1));
        TextView brand = text("Orbit Pad", 22, MINT); brand.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        add(content, brand, 0);
        TextView title = text("手机，变身手柄。", 30, FG); add(content, title, 28);
        TextView intro = text("连接同一个 Wi-Fi\n在电脑打开接收端，即可开始配对。", 14, MUTED);
        intro.setPadding(dp(20), dp(18), dp(20), dp(18)); intro.setBackground(rounded(SURFACE, 0, 24));
        add(content, intro, 20);
        add(content, text("电脑接收地址", 12, MINT), 28);
        address = field("192.168.1.100:3210");
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setText(getPreferences(0).getString("receiver", "")); add(content, address, 9);
        add(content, text("六位配对码", 12, MINT), 20);
        code = field("000000"); code.setInputType(InputType.TYPE_CLASS_NUMBER);
        code.setFilters(new android.text.InputFilter[] { new android.text.InputFilter.LengthFilter(6) });
        code.setLetterSpacing(0.25f); add(content, code, 9);
        message = text(error == null ? "在电脑接收端查看地址和配对码" : error, 12, error == null ? MUTED : Color.rgb(255, 180, 171));
        add(content, message, 12);
        connectButton = new Button(this); connectButton.setText("连接并开始  →"); connectButton.setAllCaps(false);
        connectButton.setTextColor(Color.rgb(56, 30, 114)); connectButton.setTextSize(14); connectButton.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        connectButton.setMinHeight(dp(56)); connectButton.setElevation(dp(0));
        connectButton.setPadding(dp(24), dp(16), dp(24), dp(16));
        connectButton.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33381e72), rounded(MINT, 0, 28), null));
        connectButton.setOnClickListener(v -> connect()); add(content, connectButton, 20);
        add(content, text("返回可断开连接或更换电脑\nOrbit Pad · Android 0.3.0", 12, MUTED), 24);
        setContentView(scroll);
    }

    static String normalizeAddress(String raw) throws Exception {
        String value = raw.trim();
        if (!value.contains("://")) value = "http://" + value;
        URI uri = new URI(value);
        String host = uri.getHost();
        if (!"http".equals(uri.getScheme()) || host == null || uri.getUserInfo() != null
            || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))
            || uri.getQuery() != null || uri.getFragment() != null) throw new Exception("请输入电脑接收端显示的 IP:端口，例如 192.168.1.100:3210");
        String[] parts = host.split("\\.");
        if (parts.length != 4) throw new Exception("请使用电脑的 IPv4 局域网地址");
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            if (!parts[i].matches("[0-9]{1,3}")) throw new Exception("IP 地址无效");
            octets[i] = Integer.parseInt(parts[i]);
            if (octets[i] > 255 || (parts[i].length() > 1 && parts[i].startsWith("0"))) throw new Exception("IP 地址无效");
        }
        boolean lan = octets[0] == 10 || octets[0] == 127 || (octets[0] == 192 && octets[1] == 168)
            || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
            || (octets[0] == 169 && octets[1] == 254) || (octets[0] == 198 && (octets[1] == 18 || octets[1] == 19));
        if (!lan) throw new Exception("请使用同一 Wi-Fi 内的电脑地址");
        int port = uri.getPort() == -1 ? 3210 : uri.getPort();
        if (port < 1 || port > 65535) throw new Exception("端口必须在 1–65535 之间");
        return "http://" + host + ":" + port;
    }

    private void connect() {
        if (checking) return;
        final String target;
        final String pairing = code.getText().toString().trim();
        try {
            target = normalizeAddress(address.getText().toString());
            if (!pairing.matches("[0-9]{6}")) throw new Exception("请输入电脑上显示的六位配对码");
        } catch (Exception error) { message.setText(error.getMessage()); return; }
        checking = true; connectButton.setEnabled(false); connectButton.setText("正在连接…"); message.setText("正在检查电脑接收端…");
        final int request = generation;
        network.execute(() -> {
            HttpURLConnection connection = null;
            String failure = null;
            String credential = null;
            try {
                connection = (HttpURLConnection) new URL(target + "/api/status").openConnection();
                connection.setConnectTimeout(3500); connection.setReadTimeout(3500); connection.setInstanceFollowRedirects(false);
                if (connection.getResponseCode() != 200) throw new Exception("接收端响应异常，请检查地址和端口");
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                try (InputStream in = connection.getInputStream()) {
                    byte[] bytes = new byte[2048]; int count;
                    while ((count = in.read(bytes)) != -1) {
                        buffer.write(bytes, 0, count);
                        if (buffer.size() > 16384) throw new Exception("接收端响应格式异常");
                    }
                }
                JSONObject status = new JSONObject(buffer.toString("UTF-8"));
                if (!"orbit-pad".equals(status.optString("app"))) throw new Exception("这个地址不是 Orbit Pad 接收端，请运行新版 Windows 软件");
                connection.disconnect();
                connection = (HttpURLConnection) new URL(target + "/api/pair").openConnection();
                connection.setConnectTimeout(3500); connection.setReadTimeout(3500); connection.setInstanceFollowRedirects(false);
                connection.setRequestMethod("POST"); connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] requestBody = new JSONObject().put("code", pairing).put("device", "Android 手机").toString().getBytes("UTF-8");
                connection.setFixedLengthStreamingMode(requestBody.length);
                try (OutputStream out = connection.getOutputStream()) { out.write(requestBody); }
                int responseCode = connection.getResponseCode();
                buffer.reset();
                InputStream responseStream = responseCode == 200 ? connection.getInputStream() : connection.getErrorStream();
                if (responseStream == null) throw new Exception("接收端没有返回配对结果");
                try (InputStream in = responseStream) {
                    byte[] bytes = new byte[2048]; int count;
                    while ((count = in.read(bytes)) != -1) {
                        buffer.write(bytes, 0, count);
                        if (buffer.size() > 16384) throw new Exception("配对响应格式异常");
                    }
                }
                JSONObject paired = new JSONObject(buffer.toString("UTF-8"));
                if (responseCode != 200) throw new Exception(paired.optString("error", "配对失败"));
                credential = paired.optString("token");
                if (!credential.matches("[0-9a-f]{64}")) throw new Exception("接收端配对协议异常，请更新 Windows 接收端");
            } catch (Exception error) { failure = error.getMessage(); }
            finally { if (connection != null) connection.disconnect(); }
            final String error = failure;
            final String pairedToken = credential;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || stopped || generation != request) return;
                checking = false; connectButton.setEnabled(true); connectButton.setText("连接并开始  →");
                if (error != null) { message.setText("连接失败：" + error + "\n请检查同一 Wi-Fi 和电脑防火墙。"); return; }
                getPreferences(0).edit().putString("receiver", target.substring(7)).apply();
                InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                keyboard.hideSoftInputFromWindow(code.getWindowToken(), 0);
                openController(target, pairedToken);
            });
        });
    }

    private boolean allowed(Uri uri) {
        if ("about".equals(uri.getScheme()) && "about:blank".equals(uri.toString())) return true;
        if (origin == null || !"http".equals(uri.getScheme())) return false;
        Uri target = Uri.parse(origin);
        return target.getHost().equals(uri.getHost()) && target.getPort() == uri.getPort();
    }
    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    private void openController(String target, String pairedToken) {
        origin = target;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); immersive();
        web = new WebView(this); web.setBackgroundColor(BG);
        // Game controls need sustained presses without native text-selection menus.
        web.setOnLongClickListener(v -> true);
        web.setLongClickable(false);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true); settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false); settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setUserAgentString(settings.getUserAgentString() + " OrbitPadAndroid/0.3.0");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return !allowed(request.getUrl()); }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (!allowed(request.getUrl())) return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", null, new ByteArrayInputStream(new byte[0]));
                return null;
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) runOnUiThread(() -> showConnection("无法载入手柄，请确认电脑接收端仍在运行。"));
            }
            @Override public void onPageFinished(WebView view, String url) { if (web != null) immersive(); }
        });
        setContentView(web); web.loadUrl(target + "/controller#token=" + pairedToken);
    }
    @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); if (focused && web != null) immersive(); }
    @Override public void onBackPressed() {
        if (web == null) { super.onBackPressed(); return; }
        web.evaluateJavascript("(function(){window.dispatchEvent(new Event('blur'));var d=document.querySelector('dialog[open]');if(d){d.close();return true;}return false;})()", value -> {
            if (!"true".equals(value) && web != null && !isFinishing()) confirmDisconnect();
        });
    }
    private void confirmDisconnect() {
        AlertDialog confirm = new AlertDialog.Builder(this).setTitle("断开手柄？").setMessage("返回连接页，更换电脑或重新配对。")
            .setPositiveButton("断开", (dialog, which) -> showConnection(null)).setNegativeButton("继续游戏", null).show();
        confirm.getWindow().setBackgroundDrawable(rounded(SURFACE, 0, 28));
        confirm.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(MINT);
        confirm.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(MINT);
    }
    @Override protected void onPause() {
        stopped = true; generation++; checking = false;
        if (connectButton != null) { connectButton.setEnabled(true); connectButton.setText("连接并开始  →"); }
        if (web != null) {
            web.evaluateJavascript("window.dispatchEvent(new Event('blur'));", null);
            web.onPause(); web.pauseTimers();
        }
        super.onPause();
    }
    @Override protected void onResume() {
        super.onResume(); stopped = false;
        if (web != null) { web.resumeTimers(); web.onResume(); immersive(); }
    }
    @Override protected void onDestroy() {
        generation++; network.shutdownNow();
        if (web != null) { web.loadUrl("about:blank"); web.destroy(); web = null; }
        super.onDestroy();
    }
}
