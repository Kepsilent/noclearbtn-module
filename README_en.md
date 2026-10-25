# NoClearBtn

Removes the **clear-all button** in the ColorOS recents panel, so your background tasks are never wiped out by an accidental tap. A standalone LSPosed module rewritten from [fentia/RecentsNoClear](https://github.com/fentia/RecentsNoClear) (GPL-3.0).

**Mechanism-level compatible** · no version whitelist · **4.3KB** · device reports welcome

## Features

- **Hides the clear-all button**: hooks `OplusClearAllPanelView#inflateIfNeeded` (fallback `onFinishInflate`), locates `PressFeedbackButton` by field type and hides it with `setAlpha(0)` — the rest of the panel layout stays untouched

- **Single-class hook**: never touches click callbacks or system APIs; swipe / long-press / multitasking stay on the native path

- **Enable and done**: no UI, no settings, no launcher icon; static-scope preload, zero configuration

- **4.3KB**: 3.0KB dex + minimal manifest + xposed trio; no Kotlin/Compose/androidx dead weight

## Install & Use

1. **LSPosed 2.x** (libxposed API 102) or another compatible framework

2. Install the APK and enable the module in LSPosed

3. **Scope: System Launcher** — `com.android.launcher`, `com.oppo.launcher`, then reboot

4. Open Recents — the clear-all button should be gone

Questions? [Open an Issue](https://github.com/Kepsilent/noclearbtn-module/issues)

## Compatibility

- Mechanism is **version-independent** (no whitelist): works as long as the system keeps `OplusClearAllPanelView` and the `PressFeedbackButton` field structure

- Logic baseline verified against the native ColorOS recents panel structure (decompiled)

- If a future system renames the class or changes the field, the hook silently logs and leaves the system untouched

- Modding carries risk — back up first

## Build

Requires JDK 17+, Android SDK (platform 34), libxposed API 102.

```
export ANDROID_HOME=/path/to/android-sdk
./build.sh
# output: release/NoClearBtn-v1.0.apk
```

Pipeline: `javac --release 8 → R8 → aapt2 link → zip reassembly → self-signed v2` (see `build.sh` in the repo root; targets 4KB-class size).

## Verify

```
apksigner verify --verbose release/NoClearBtn-v1.0.apk
# expect: Verifies · Verified using v2 scheme: true
```

## How It Works

The ColorOS recents panel hosts the clear-all button inside `OplusClearAllPanelView`. Before the panel view finishes inflating:

1. Hooks `inflateIfNeeded` (fallback `onFinishInflate`), then locates `com.android.launcher.views.PressFeedbackButton` by field type;

2. Calls `setAlpha(0)` on it — the button goes transparent and stops receiving taps.

`setAlpha` is a method of the button itself; field-type matching survives obfuscation differences. Logic is identical to the original, minus hot-reload plumbing and verbose logging.

## Layout

```
app/src/main/
├── AndroidManifest.xml      # label only (no Activity/Provider/icon)
├── java/io.github.kepsilent.noclearbtn/
│   └── NoClearBtnHook.java  # the only hook (single-file minimal)
└── res/xposed/              # java_init.list / module.prop / scope.list
build.sh                     # one-shot 4KB-class build
```

## Size Evolution

| Version | Size | Notes |
|---|---|---|
| fentia/RecentsNoClear v1.0 | 16.8 KB | original, with hot-reload plumbing & redundant fallbacks |
| **NoClearBtn v1.0** | **4.3 KB** | single-point hook rewrite + R8 + compact v2 signature (−74%) |

## License

- Hook logic and implementation derived from [fentia/RecentsNoClear](https://github.com/fentia/RecentsNoClear), **GPL-3.0**

- Banner visuals follow the [EGD (Elegant Geek Design)](https://github.com/Kepsilent/Elegant-Geek-Design) design system
