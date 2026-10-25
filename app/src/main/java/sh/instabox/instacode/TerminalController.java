package sh.instabox.instacode;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.webkit.WebView;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

final class TerminalController {
    interface Listener {
        void onTerminalState(String state);
    }

    private final WebView view;
    private final InstaBoxApi api;
    private final String machineId;
    private final String apiKey;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile WebSocket socket;
    private volatile boolean viewReady;
    private int columns = 100;
    private int rows = 32;
    private int reconnectAttempts = 0;

    TerminalController(WebView view, InstaBoxApi api, String machineId, String apiKey, Listener listener) {
        this.view = view;
        this.api = api;
        this.machineId = machineId;
        this.apiKey = apiKey;
        this.listener = listener;
        view.addJavascriptInterface(new TerminalBridge(this), "AndroidTerminal");
    }

    void terminalReady() {
        viewReady = true;
        connect();
    }

    void connect() {
        if (closed.get() || socket != null || !viewReady) return;
        state("Connecting terminal…");
        String path = "/v1/machines/" + machineId + "/terminal?session=main&cols=" + columns + "&rows=" + rows;
        Request request = new Request.Builder()
                .url(InstaBoxApi.BASE_URL.replace("https://", "wss://") + path)
                .header("X-API-Key", apiKey)
                .build();
        socket = api.httpClient().newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                reconnectAttempts = 0;
                state("Terminal connected");
                resize(columns, rows);
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                String encoded = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP);
                main.post(() -> {
                    if (!closed.get()) view.evaluateJavascript("window.terminalWriteBase64('" + encoded + "')", null);
                });
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    JSONObject control = new JSONObject(text);
                    String type = control.optString("type");
                    if (type.equals("error") || type.equals("taken")) {
                        state(control.optString("message", "Terminal opened elsewhere"));
                    } else if (type.equals("exit")) {
                        state("Shell exited · reconnecting");
                    }
                } catch (JSONException ignored) {
                }
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                webSocket.close(code, reason);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                socket = null;
                if (!closed.get()) scheduleReconnect();
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable error, Response response) {
                socket = null;
                state("Terminal offline · retrying");
                if (!closed.get()) scheduleReconnect();
            }
        });
    }

    private void scheduleReconnect() {
        reconnectAttempts++;
        long delay = Math.min(15_000, 1000L * reconnectAttempts);
        main.postDelayed(this::connect, delay);
    }

    void send(String data) {
        WebSocket current = socket;
        if (current != null) current.send(ByteString.encodeString(data, StandardCharsets.UTF_8));
    }

    void resize(int columns, int rows) {
        this.columns = Math.max(10, columns);
        this.rows = Math.max(4, rows);
        WebSocket current = socket;
        if (current != null) {
            try {
                current.send(new JSONObject().put("type", "resize")
                        .put("cols", this.columns).put("rows", this.rows).toString());
            } catch (JSONException ignored) {
            }
        }
    }

    private void state(String value) {
        main.post(() -> listener.onTerminalState(value));
    }

    void close() {
        closed.set(true);
        WebSocket current = socket;
        socket = null;
        if (current != null) current.close(1000, "App closed");
    }
}
