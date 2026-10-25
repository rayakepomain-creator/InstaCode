package sh.instabox.instacode;

import android.webkit.JavascriptInterface;

public final class TerminalBridge {
    private final TerminalController controller;

    TerminalBridge(TerminalController controller) {
        this.controller = controller;
    }

    @JavascriptInterface
    public void send(String data) {
        controller.send(data);
    }

    @JavascriptInterface
    public void resize(int columns, int rows) {
        controller.resize(columns, rows);
    }

    @JavascriptInterface
    public void ready() {
        controller.terminalReady();
    }
}
