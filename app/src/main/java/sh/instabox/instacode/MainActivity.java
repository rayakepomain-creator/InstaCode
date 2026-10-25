package sh.instabox.instacode;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.HttpAuthHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(11, 13, 16);
    private static final int SURFACE = Color.rgb(20, 24, 29);
    private static final int SURFACE_2 = Color.rgb(28, 33, 40);
    private static final int TEXT = Color.rgb(243, 244, 246);
    private static final int MUTED = Color.rgb(148, 163, 184);
    private static final int ACCENT = Color.rgb(163, 230, 53);
    private static final int DANGER = Color.rgb(248, 113, 113);

    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private CredentialStore store;
    private FrameLayout root;
    private TextView workspaceStatus;
    private TextView terminalTab;
    private TextView webTab;
    private WebView terminalView;
    private WebView browserView;
    private View terminalPane;
    private View browserPane;
    private TerminalController terminal;
    private InstaBoxApi api;
    private String machineId;
    private String machineName;
    private boolean startingWeb;
    private boolean showingWeb;
    private boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        setContentView(root);
        try {
            store = new CredentialStore(getApplicationContext());
        } catch (Exception e) {
            fatal("Secure storage could not be initialized", e);
            return;
        }
        if (!store.apiKey().isEmpty() && !store.machineId().isEmpty()) {
            showWorkspace(store.apiKey(), store.machineId(), store.machineName());
        } else {
            showSetup();
        }
    }

    private void showSetup() {
        closeTerminal();
        root.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = column();
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(dp(26), dp(36), dp(26), dp(32));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -1));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        TextView eyebrow = label("I N S T A C O D E", 12, ACCENT);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(eyebrow);
        TextView title = label("OpenCode,\nin your pocket.", 36, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setLineSpacing(0, 0.92f);
        content.addView(title, margins(-1, -2, 0, 14, 0, 12));
        TextView subtitle = label("A standalone terminal and browser for your InstaBox cloud machine. No Termux, no extra apps.", 16, MUTED);
        subtitle.setLineSpacing(dp(4), 1);
        content.addView(subtitle, margins(-1, -2, 0, 0, 0, 30));

        TextView keyLabel = label("INSTABOX API KEY", 11, MUTED);
        keyLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(keyLabel);
        EditText keyInput = new EditText(this);
        keyInput.setHint("ibk_••••••••••••••••");
        keyInput.setHintTextColor(Color.rgb(75, 85, 99));
        keyInput.setTextColor(TEXT);
        keyInput.setSingleLine(true);
        keyInput.setTextSize(16);
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setPadding(dp(16), 0, dp(16), 0);
        keyInput.setBackground(round(SURFACE, 12, Color.rgb(51, 65, 85)));
        content.addView(keyInput, margins(-1, dp(54), 0, 8, 0, 8));
        TextView help = label("Create a key with machines:write access in InstaBox → API keys. It stays encrypted on this device.", 13, MUTED);
        help.setLineSpacing(dp(2), 1);
        content.addView(help, margins(-1, -2, 0, 0, 0, 20));

        Spinner machineSpinner = new Spinner(this);
        machineSpinner.setBackground(round(SURFACE, 12, Color.rgb(51, 65, 85)));
        machineSpinner.setPadding(dp(12), 0, dp(12), 0);
        machineSpinner.setVisibility(View.GONE);
        content.addView(machineSpinner, margins(-1, dp(52), 0, 0, 0, 14));

        Button connect = primaryButton("Connect to InstaBox");
        content.addView(connect, new LinearLayout.LayoutParams(-1, dp(54)));
        TextView progress = label("", 13, MUTED);
        progress.setGravity(Gravity.CENTER);
        content.addView(progress, margins(-1, -2, 0, 16, 0, 0));
        TextView foot = label("TERMINAL  +  OPENCODE WEB  +  PERSISTENT VM", 11, Color.rgb(100, 116, 139));
        foot.setGravity(Gravity.CENTER);
        content.addView(foot, margins(-1, -2, 0, 28, 0, 0));

        final AtomicReference<List<InstaBoxApi.Machine>> machines = new AtomicReference<>();
        connect.setOnClickListener(v -> {
            if (machineSpinner.getVisibility() == View.VISIBLE && machines.get() != null) {
                int selected = machineSpinner.getSelectedItemPosition();
                if (selected >= 0 && selected < machines.get().size()) {
                    String key = keyInput.getText().toString().trim();
                    InstaBoxApi selectedApi = new InstaBoxApi(key);
                    openMachine(selectedApi, machines.get().get(selected), key, connect, progress);
                }
                return;
            }
            String key = keyInput.getText().toString().trim();
            if (!key.startsWith("ibk_")) {
                progress.setTextColor(DANGER);
                progress.setText("Enter a valid InstaBox API key (it starts with ibk_).");
                return;
            }
            connect.setEnabled(false);
            progress.setTextColor(MUTED);
            progress.setText("Checking your machines…");
            InstaBoxApi newApi = new InstaBoxApi(key);
            executor.execute(() -> {
                try {
                    List<InstaBoxApi.Machine> result = newApi.listMachines();
                    runOnUiThread(() -> {
                        if (destroyed) return;
                        connect.setEnabled(true);
                        if (result.isEmpty()) {
                            progress.setText("No machine found. Create one to continue.");
                            machines.set(null);
                            createMachine(newApi, key, connect, progress);
                        } else if (result.size() == 1) {
                            openMachine(newApi, result.get(0), key, connect, progress);
                        } else {
                            machines.set(result);
                            ArrayAdapter<InstaBoxApi.Machine> adapter = new ArrayAdapter<>(this,
                                    android.R.layout.simple_spinner_dropdown_item, result);
                            machineSpinner.setAdapter(adapter);
                            machineSpinner.setVisibility(View.VISIBLE);
                            connect.setText("Open selected machine");
                            progress.setText("Choose which machine should run OpenCode.");
                        }
                    });
                } catch (Exception e) {
                    setupError(connect, progress, e);
                }
            });
        });
    }

    private void createMachine(InstaBoxApi newApi, String key, Button connect, TextView progress) {
        connect.setEnabled(false);
        executor.execute(() -> {
            try {
                runOnUiThread(() -> progress.setText("Creating your cloud machine…"));
                InstaBoxApi.Machine machine = newApi.createMachine();
                completeConnection(newApi, machine, key);
            } catch (Exception e) {
                setupError(connect, progress, e);
            }
        });
    }

    private void openMachine(InstaBoxApi newApi, InstaBoxApi.Machine machine, String key,
                             Button connect, TextView progress) {
        connect.setEnabled(false);
        progress.setTextColor(MUTED);
        progress.setText("Waking " + machine.name + "…");
        executor.execute(() -> {
            try {
                InstaBoxApi.Machine ready = newApi.ensureRunning(machine.id);
                completeConnection(newApi, ready, key);
            } catch (Exception e) {
                setupError(connect, progress, e);
            }
        });
    }

    private void completeConnection(InstaBoxApi newApi, InstaBoxApi.Machine machine, String key) {
        store.saveConnection(key, machine.id, machine.name);
        runOnUiThread(() -> {
            if (!destroyed) showWorkspace(key, machine.id, machine.name);
        });
    }

    private void setupError(Button connect, TextView progress, Exception error) {
        runOnUiThread(() -> {
            if (destroyed) return;
            connect.setEnabled(true);
            progress.setTextColor(DANGER);
            progress.setText(friendly(error));
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void showWorkspace(String key, String id, String name) {
        machineId = id;
        machineName = name;
        api = new InstaBoxApi(key);
        root.removeAllViews();
        LinearLayout screen = column();
        root.addView(screen, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(6), dp(8), dp(6));
        header.setBackgroundColor(SURFACE);
        screen.addView(header, new LinearLayout.LayoutParams(-1, dp(66)));
        LinearLayout heading = column();
        heading.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(heading, new LinearLayout.LayoutParams(0, -1, 1));
        TextView title = label(machineName, 17, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.addView(title);
        workspaceStatus = label("Preparing terminal…", 11, MUTED);
        heading.addView(workspaceStatus, margins(-1, -2, 0, 2, 0, 0));
        Button startWeb = smallButton("OpenCode ↗");
        startWeb.setOnClickListener(v -> showOpenCode());
        header.addView(startWeb, new LinearLayout.LayoutParams(-2, dp(40)));
        Button signOut = smallButton("⋮");
        signOut.setOnClickListener(v -> confirmSignOut());
        header.addView(signOut, new LinearLayout.LayoutParams(dp(42), dp(40)));

        FrameLayout content = new FrameLayout(this);
        screen.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        terminalPane = column();
        content.addView(terminalPane, new FrameLayout.LayoutParams(-1, -1));
        terminalView = new WebView(this);
        configureTerminalWebView(terminalView);
        ((LinearLayout) terminalPane).addView(terminalView, new LinearLayout.LayoutParams(-1, 0, 1));
        ((LinearLayout) terminalPane).addView(specialKeys(), new LinearLayout.LayoutParams(-1, dp(46)));

        browserPane = new FrameLayout(this);
        browserPane.setVisibility(View.GONE);
        content.addView(browserPane, new FrameLayout.LayoutParams(-1, -1));
        browserView = new WebView(this);
        configureBrowserWebView(browserView);
        ((FrameLayout) browserPane).addView(browserView, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER);
        tabs.setPadding(dp(8), dp(5), dp(8), dp(5));
        tabs.setBackgroundColor(SURFACE);
        screen.addView(tabs, new LinearLayout.LayoutParams(-1, dp(58)));
        terminalTab = tabButton("⌨   Terminal");
        webTab = tabButton("◇   OpenCode");
        tabs.addView(terminalTab, new LinearLayout.LayoutParams(0, dp(44), 1));
        tabs.addView(webTab, new LinearLayout.LayoutParams(0, dp(44), 1));
        terminalTab.setOnClickListener(v -> selectTab(false));
        webTab.setOnClickListener(v -> showOpenCode());

        terminal = new TerminalController(terminalView, api, machineId, key, state -> {
            if (workspaceStatus != null) workspaceStatus.setText(state);
        });
        terminalView.loadUrl("file:///android_asset/terminal.html");
        selectTab(false);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureTerminalWebView(WebView view) {
        view.setBackgroundColor(BG);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        view.setOverScrollMode(View.OVER_SCROLL_NEVER);
        view.setWebChromeClient(new WebChromeClient());
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureBrowserWebView(WebView view) {
        view.setBackgroundColor(BG);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        view.setWebChromeClient(new WebChromeClient());
        view.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler,
                                                  String host, String realm) {
                String expected = store.webUrl().isEmpty() ? "" : Uri.parse(store.webUrl()).getHost();
                if (host.equals(expected)) handler.proceed("opencode", store.getOrCreateWebPassword());
                else handler.cancel();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String expected = store.webUrl().isEmpty() ? "" : Uri.parse(store.webUrl()).getHost();
                if (uri.getHost() != null && uri.getHost().equals(expected)) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (ActivityNotFoundException ignored) {
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (workspaceStatus != null) workspaceStatus.setText("OpenCode web ready");
            }
        });
    }

    private View specialKeys() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setBackgroundColor(SURFACE);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(4), dp(6), dp(4));
        scroll.addView(row, new HorizontalScrollView.LayoutParams(-2, -1));
        String[] keys = {"Esc", "Tab", "Ctrl", "↑", "↓", "←", "→", "Ctrl+C"};
        String[] actions = {"ESC", "TAB", "CTRL", "UP", "DOWN", "LEFT", "RIGHT", "CTRL_C"};
        for (int i = 0; i < keys.length; i++) {
            Button button = smallButton(keys[i]);
            button.setMinWidth(dp(48));
            String action = actions[i];
            button.setOnClickListener(v -> {
                if (action.equals("CTRL")) {
                    terminalView.evaluateJavascript("window.toggleCtrl()", value -> {
                        boolean active = Boolean.parseBoolean(value);
                        button.setTextColor(active ? BG : TEXT);
                        button.setBackground(round(active ? ACCENT : SURFACE_2, 8, 0));
                        Toast.makeText(this, active ? "Ctrl armed for next key" : "Ctrl off", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    terminalView.evaluateJavascript("window.sendSpecial('" + action + "')", null);
                }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(36));
            params.setMargins(dp(3), 0, dp(3), 0);
            row.addView(button, params);
        }
        return scroll;
    }

    private void showOpenCode() {
        if (startingWeb) return;
        if (!store.webUrl().isEmpty()) {
            if (browserView.getUrl() == null) browserView.loadUrl(store.webUrl());
            selectTab(true);
            return;
        }
        startOpenCode();
    }

    private void startOpenCode() {
        startingWeb = true;
        workspaceStatus.setText("Installing / starting OpenCode…");
        selectTab(false);
        final String password = store.getOrCreateWebPassword();
        executor.execute(() -> {
            try {
                String install = "export PATH=\"$HOME/.opencode/bin:$PATH\"; " +
                        "if ! command -v opencode >/dev/null 2>&1; then " +
                        "curl -fsSL https://opencode.ai/install | bash; fi; " +
                        "export PATH=\"$HOME/.opencode/bin:$PATH\"; " +
                        "command -v opencode >/dev/null 2>&1 && opencode --version";
                api.exec(machineId, install, 300);
                runOnUiThread(() -> workspaceStatus.setText("Starting OpenCode web…"));
                String health = "curl -fsS --max-time 2 -u opencode:" + password +
                        " http://127.0.0.1:4096/global/health";
                String start = "export PATH=\"$HOME/.opencode/bin:$PATH\"; " +
                        "mkdir -p \"$HOME/.instacode\"; " +
                        "if ! " + health + " >/dev/null 2>&1; then " +
                        "if [ -f \"$HOME/.instacode/opencode-web.pid\" ]; then " +
                        "kill $(cat \"$HOME/.instacode/opencode-web.pid\") 2>/dev/null || true; sleep 1; fi; " +
                        "setsid -f env OPENCODE_SERVER_USERNAME=opencode OPENCODE_SERVER_PASSWORD=" + password +
                        " bash -c 'echo $$ > \"$HOME/.instacode/opencode-web.pid\"; " +
                        "exec opencode web --hostname 0.0.0.0 --port 4096' " +
                        ">" + "$HOME/.instacode/opencode-web.log 2>&1 < /dev/null; fi; " +
                        "for i in $(seq 1 30); do " +
                        "if " + health + " >/dev/null 2>&1; then exit 0; fi; " +
                        "sleep 1; done; cat \"$HOME/.instacode/opencode-web.log\"; exit 1";
                api.exec(machineId, start, 60);
                String url = api.publishOpenCodePort(machineId);
                store.saveWebUrl(url);
                runOnUiThread(() -> {
                    startingWeb = false;
                    if (destroyed) return;
                    browserView.loadUrl(url);
                    selectTab(true);
                    workspaceStatus.setText("Opening OpenCode…");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    startingWeb = false;
                    if (destroyed) return;
                    workspaceStatus.setText("OpenCode failed · see terminal");
                    Toast.makeText(this, friendly(e), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void selectTab(boolean web) {
        showingWeb = web;
        terminalPane.setVisibility(web ? View.GONE : View.VISIBLE);
        browserPane.setVisibility(web ? View.VISIBLE : View.GONE);
        styleTab(terminalTab, !web);
        styleTab(webTab, web);
        if (!web && terminalView != null) terminalView.postDelayed(() ->
                terminalView.evaluateJavascript("window.focusTerminal()", null), 100);
        if (web) hideKeyboard();
    }

    private void confirmSignOut() {
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle("Disconnect this device?")
                .setMessage("This removes the API key and OpenCode password from the app. Your InstaBox machine and files are not deleted.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Disconnect", (d, w) -> {
                    store.clear();
                    if (browserView != null) browserView.destroy();
                    if (terminalView != null) terminalView.destroy();
                    showSetup();
                })
                .create();
        dialog.show();
    }

    private TextView tabButton(String text) {
        TextView view = label(text, 14, MUTED);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private void styleTab(TextView view, boolean active) {
        view.setTextColor(active ? ACCENT : MUTED);
        view.setBackground(round(active ? SURFACE_2 : SURFACE, 10, 0));
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button primaryButton(String text) {
        Button button = smallButton(text);
        button.setTextColor(BG);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(round(ACCENT, 12, 0));
        return button;
    }

    private Button smallButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(13);
        button.setTextColor(TEXT);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(round(SURFACE_2, 9, 0));
        return button;
    }

    private GradientDrawable round(int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) {
            drawable.setStroke(dp(1), strokeColor);
        }
        return drawable;
    }

    private LinearLayout.LayoutParams margins(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String friendly(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message;
    }

    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused != null) {
            InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            manager.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        }
    }

    private void closeTerminal() {
        if (terminal != null) {
            terminal.close();
            terminal = null;
        }
    }

    private void fatal(String message, Exception error) {
        TextView view = label(message + "\n" + friendly(error), 16, DANGER);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.addView(view, new FrameLayout.LayoutParams(-1, -1));
    }

    @Override
    public void onBackPressed() {
        if (showingWeb && browserView != null && browserView.canGoBack()) {
            browserView.goBack();
        } else if (showingWeb) {
            selectTab(false);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        closeTerminal();
        if (terminalView != null) terminalView.destroy();
        if (browserView != null) browserView.destroy();
        executor.shutdownNow();
        super.onDestroy();
    }
}
