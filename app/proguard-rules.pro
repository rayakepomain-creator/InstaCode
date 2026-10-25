# Keep JavaScript bridge methods when minification is enabled for local builds.
-keepclassmembers class sh.instabox.instacode.TerminalBridge {
    @android.webkit.JavascriptInterface <methods>;
}
