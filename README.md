# InstaCode for Android

A native Android client for running OpenCode on an InstaBox cloud machine. The app contains its own xterm.js terminal and an embedded OpenCode web browser; Termux and ADB are not required.

## Features

- Connect using an InstaBox API key with `machines:write` access.
- Select an existing VM or create a `universal` VM when none exists.
- Automatically resumes suspended or stopped machines.
- Interactive persistent terminal with ANSI/VT support, resize, scrollback, and mobile keys for Esc, Tab, Ctrl, arrows, and Ctrl+C.
- Installs OpenCode when missing, starts `opencode web` on port 4096, publishes the InstaBox port, and opens the GUI inside the app.
- Generates a random OpenCode Basic Auth password and supplies it only to the matching web host.
- Stores the API key, machine selection, and OpenCode password in Android Keystore-backed encrypted preferences.
- Reconnects to the same persistent terminal session after network drops.

## Install

1. Install the APK on Android 8.0 (API 26) or newer. Android may ask you to allow installation from this source.
2. In InstaBox, open **API keys** and create a key with **machines:write** (or full access).
3. Paste the key into InstaCode and choose a machine.
4. Use **Terminal** for shell access, or tap **OpenCode** to install/start the web UI.

OpenCode still needs an AI provider login or API key. Configure that in OpenCode after the first launch.

## Security notes

- InstaCode talks only to `api.instabox.sh` and the published OpenCode URL. Authentication keys are never exposed to the terminal's JavaScript layer.
- Publishing port 4096 creates a public HTTPS URL. OpenCode is started with a random Basic Auth password; keep the URL private anyway.
- **Disconnect** in the app menu removes local credentials. It does not delete the VM, revoke the InstaBox API key, or unpublish the port. Revoke the key and unpublish the port in InstaBox when they are no longer needed.
- This is a cloud client: OpenCode and shell commands run on the InstaBox VM, not locally on the phone. An internet connection is required.

## Build

Requires JDK 17 and Android SDK 35.

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleRelease
```

The release output is unsigned by default. Sign it with `apksigner`, or use `:app:assembleDebug` for an automatically debug-signed build.

## Implementation

- Native Android/Java UI
- OkHttp HTTP and WebSocket client
- InstaBox machine, exec, public-port, and persistent-terminal APIs
- Bundled xterm.js 5.5.0 and Fit Addon 0.10.0
- Android WebView for the official OpenCode web interface
