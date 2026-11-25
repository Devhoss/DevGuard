# DevGuard

**Developer control. Simplified.**

<p align="center">
  <img src="docs/icon.png" width="104" alt="DevGuard app icon">
</p>

DevGuard is a small, offline Android utility that gives you one-tap control over the three
developer settings that everyday apps complain about:

| Setting | Android global setting | Availability |
|---|---|---|
| Developer Options | `development_settings_enabled` | Android 8.0+ |
| USB Debugging | `adb_enabled` | Android 8.0+ |
| Wireless Debugging | `adb_wifi_enabled` | Android 11+ |

It is a framework-only Java app: no backend, no accounts, no analytics, no ad libraries and
no `INTERNET` permission. It makes no network requests at all; the only network-related
declaration is `ACCESS_NETWORK_STATE`, used to ask Android whether a Wi-Fi transport exists.
Everything happens on the device through `Settings.Global` and the one-time
`WRITE_SECURE_SETTINGS` grant.

- Package: `com.devhoss.devguard`
- Min SDK 26 (Android 8.0), target/compile SDK 35
- Native Views + XML, `TileService`, three `AppWidgetProvider`s
- Version 1.0.1 · Release APK: ~69 KB

---

## What it does

### Safe Mode

- Captures the current development configuration **once** and never overwrites it just because
  Safe Mode is activated again (explicit save/replace/forget lives in *Saved configuration*).
- Turns Wireless Debugging off, then USB Debugging, then Developer Options.
- Reads every setting back and reports anything that did not change, instead of claiming success.

### Development Mode

- Restores Developer Options first, then USB and Wireless debugging **exactly as the snapshot
  recorded them** — never "turn everything on".
- If no snapshot exists, only Developer Options is enabled; debugging is left alone.
- The result is always read back from the device and reported as one of three states: **success**,
  **partial** (the mode changed but a setting did not) or **failure** (nothing changed). A refused
  USB or Wireless write is never reported as a successful restore.

### Surfaces

- **Dashboard** with live state, three rows and switches, snapshot card and setup card.
- **Quick Settings tile** that switches modes without opening the app, with lock-screen unlock
  handling and correct active/inactive state.
- **Pill widget** (2×1): brand and a miniature switch, nothing else — tap the pill anywhere to
  switch between Safe and Development Mode.
- **Compact widget** (2×2): brand, mode chip, status line, one-tap mode action.
- **Expanded widget** (4×3): all three settings with individual controls plus the mode action.
- **Widget surface** — *Default* (solid card) or *Clear* (transparent glass, no fill, so the
  wallpaper shows through), each with an optional **Specular highlight rim**: a brighter edge
  plus a thin top-edge reflection. Pill, compact and expanded widgets follow the choice instantly.

All surfaces read the same `DevSettings` controller and are refreshed through `RefreshCenter`
after every write, so the dashboard, tile and widgets always mirror the real system state.
A `ContentObserver` keeps the dashboard honest when settings change elsewhere.

---

## Build

Requirements: JDK 17+ (JDK 21 works), Android SDK Platform 35 and Build-Tools 35.0.0.

```bash
cd devguard

# Debug APK  -> app/build/outputs/apk/debug/DevGuard-1.0.1.apk
./gradlew assembleDebug

# Release APK -> app/build/outputs/apk/release/DevGuard-1.0.1.apk
./gradlew assembleRelease
```

Prebuilt APKs are also copied to `dist/`:

- `dist/DevGuard-1.0.1-debug.apk`
- `dist/DevGuard-1.0.1-release.apk` (signed with the project keystore, certificate `CN=DevGuard, O=DevHoss, C=US`)

`local.properties` points at `X:/Android/Sdk` on this machine; change `sdk.dir` if your SDK
lives elsewhere. The release build reads `keystore.properties` + `devguard-release.jks`
(both are local development material — generate your own keystore before distributing).

---

## Install and one-time ADB setup

```bash
adb install -r dist/DevGuard-1.0.1-release.apk
adb shell pm grant com.devhoss.devguard android.permission.WRITE_SECURE_SETTINGS
```

`android.permission.WRITE_SECURE_SETTINGS` is a protected permission: Android has no runtime
dialog for it. Grant it once from a computer with USB debugging enabled. It survives reboots
and app updates until it is revoked or the app is uninstalled.

The app shows the same command on its setup screen with a copy button and a *Check permission*
action, so nothing needs to be typed by hand. No wireless debugging, Shizuku or root is required
for setup.

> Note: after you activate Safe Mode, USB debugging is off — that is the point. To grant the
> permission again later you have to switch USB debugging back on from Android Settings. The app
> can restore it with one tap as long as the permission is still granted.

---

## Project structure

```
app/src/main/java/com/devhoss/devguard/
  DevSettings.java            Single source of truth: read / write / verify the three settings
  Snapshot.java               Immutable snapshot model (versioned JSON)
  SnapshotStore.java          Private SharedPreferences storage, capture-if-absent semantics
  Faults.java                 Test seam for refused-write tests (inert in release; see below)
  ModeController.java         Safe Mode / Development Mode orchestration + honest outcomes
  Actions.java                Single-setting actions with user-facing errors
  RefreshCenter.java          Refreshes widgets, tile and open activities after a change
  ThemeHelper.java            Light / dark / system appearance (uiMode, no AndroidX)
  MainActivity.java           Dashboard, dialogs, tile + widget pinning
  SetupActivity.java          ADB onboarding
  SettingsTileService.java    Quick Settings tile
  Widgets.java                RemoteViews building + widget refresh
  WidgetSurface.java          Widget material (Default / Clear) + specular rim preference
  WidgetActionReceiver.java   Widget button actions
  PillWidgetProvider.java     Pill widget (2×1 switch only)
  CompactWidgetProvider.java  Compact widget
  ExpandedWidgetProvider.java Expanded widget
app/src/debug/java/.../DebugProbeReceiver.java
                              Debug-only test hook (never in release): schedules a restore so
                              ADB comes back after on-device Safe Mode tests
```

Layouts: `activity_main`, `activity_setup`, `dialog_settings`, `dialog_snapshot`,
`widget_pill`, `widget_compact`, `widget_expanded`.

Colours and typography live in `values/` + `values-night/` (Midnight Navy `#101D2D`,
Mint `#75DDB5`, Dark Surface `#202A36`, Light Surface `#F1F4F6`). The launcher icon is an
original adaptive vector (mint toggle with an X motif on a navy gradient) with a monochrome
layer for themed icons.

---

## Privacy

- No `INTERNET` permission, no analytics, no advertising, no accounts, no telemetry, and no
  network requests of any kind. The app declares `ACCESS_NETWORK_STATE` for one read-only
  question — "is a Wi-Fi transport connected?" — because Wireless Debugging needs one.
- No installed-app lists, no accessibility service, no background service.
- The only persisted data is the snapshot (three booleans + timestamp + version), the theme
  preference and the widget surface choice, in the app's private storage. Backups are disabled.
- DevGuard only controls your own developer settings. It does not hide debugging state from
  other apps and makes no promise that a particular banking app will accept the device.

---

## Testing

Device: **Samsung Galaxy A53 5G (SM-A536E), Android 16 / One UI, build SDK 36**, arm64.

Verified on the real device: permission onboarding and recheck, wireless toggle, snapshot
capture, Safe Mode (all three settings off, ADB link drops), restore through the saved
snapshot, repeated Safe Mode without overwriting the snapshot, tile click and tile state,
all three widgets (pill, compact and the full expanded one), a pill-widget tap, individual
widget action delivery, live sync after an external settings change, light/dark themes, the
Clear widget surface with the specular rim on and off, partial restoration with Wi-Fi off,
injected USB and Wireless write refusals, a snapshot whose Developer Options were OFF,
permission revocation, and reboot persistence (permission, snapshot, widgets, tile and a
restore after boot). Screenshots live in `docs/screenshots/`. The detailed report, including
what was *not* covered and how the debug harness works, is in
[`docs/TESTING.md`](docs/TESTING.md).

---

## Known limitations

- Wireless Debugging exists from Android 11; on older releases the row is shown as unavailable
  and snapshots record it as unknown.
- Wireless debugging needs a Wi-Fi transport. Android may accept the setting while Wi-Fi is
  disconnected; the app reports the real state and warns when the network is missing.
- Manufacturer policies can reject a write. Every write is read back, and the app reports a
  refusal instead of a false success.
- A snapshot stores booleans only: it does not preserve wireless debugging port/pairing details.
- Wireless debugging cannot work without a Wi-Fi transport. Android also clears the setting when
  Wi-Fi drops, so a restore with Wi-Fi off is reported as partial instead of pretending the
  wireless link is back.
- Wi-Fi detection enumerates every network, so a connected VPN does not look like "no Wi-Fi".
- No automatic "restore after N minutes" scheduling, no app rules, no Tasker/Shizuku/remote
  control — deliberately out of scope for V1.
- Widgets refresh on actions and whenever the app refreshes; there is no polling.
