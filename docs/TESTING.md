# Device-testing report — DevGuard 1.0.0

**Device:** Samsung Galaxy A53 5G (SM-A536E), Android 16 (SDK 36), One UI, 1080×2400
**Installed:** debug build `com.devhoss.devguard` v1.0.0 (versionCode 1)
**Permission:** `WRITE_SECURE_SETTINGS` granted with
`adb shell pm grant com.devhoss.devguard android.permission.WRITE_SECURE_SETTINGS`
**Method:** ADB shell for state reads/writes, `uiautomator` for hierarchy checks, `screencap`
for the screenshots in `docs/screenshots/`, plus direct on-device interaction.

Because disabling debugging necessarily kills the ADB connection used to drive the test, debug
builds ship a test-only receiver (`DebugProbeReceiver`, `app/src/debug/`) that schedules a
restore through the production `ModeController.restoreDevelopmentMode()` path. It is not part of
release builds.

---

## Results

| # | Check | Result | Evidence |
|---|---|---|---|
| 1 | Initial permission onboarding | **Pass** | Revoked the grant: dashboard showed the *Setup required* card and dimmed switches. Setup screen listed the steps, the exact `pm grant` command and a copy button. Re-granting + *Check permission* flipped the card to *Permission granted*. |
| 2 | Enabling/disabling Developer Options | **Pass** | Disabled as part of Safe Mode; the restore probe observed `dev=false` while safe and `dev=true` after restore. Writes go through the same verified path (`putInt` then read-back). |
| 3 | USB Debugging toggle | **Pass** | Safe Mode turned it off (`adb_enabled=0`) and the ADB link dropped, which is direct proof the system applied it. Restore returned `adb_enabled=1` and the device came back. |
| 4 | Wireless Debugging toggle | **Pass** | Row tap → `adb_wifi_enabled=0`; tap again → `1`; UI, tile and widget all followed. USB ADB was unaffected, as expected. |
| 5 | Snapshot capture | **Pass** | `shared_prefs/devguard_state.xml`: `{"version":1,"timestamp":1795434724225,"devOptions":true,"usb":true,"wireless":1}`. |
| 6 | Safe Mode activation | **Pass** | Tapping *Activate Safe Mode* turned all three settings off (probe read `dev=false usb=false wifi=false`) and killed the ADB link within ~2 s. |
| 7 | Restoration of the original configuration | **Pass** | Production restore path logged `restore done ok=true message=Development Mode restored. | dev=true usb=true wifi=true`; shell reads confirmed all three are 1. |
| 8 | Repeated Safe Mode without overwriting the snapshot | **Pass** | After a second Safe Mode cycle the stored snapshot timestamp was still `1795434724225` (the original 1:52 PM capture). `captureIfAbsent` works as specified. |
| 9 | Partial restoration behaviour | **Not exercised on device** | The code paths exist (Wi-Fi disconnected → explanatory message; rejected write → "Android rejected the change"), but no on-device partial-failure case was produced. Wi-Fi was connected throughout. |
| 10 | Quick Settings tile | **Pass** | Tile added through the Android 13+ *Add this button?* flow. SystemUI logged `Label = DevGuard, State = 1` while in Safe Mode and `State = 2` after restoring. `cmd statusbar click-tile` drove the tile without opening the app, enabled Safe Mode (ADB link dropped) and the tile/widget state refreshed afterwards. |
| 11 | Compact widget | **Pass** | Pinned from *Settings → Pin compact widget*: brand row, mint *Development* / grey *Safe* chip, status line, one-tap action. Refreshed after restore. |
| 12 | Expanded widget | **Pass** | Pinned and rendered after fixing a real bug: plain `<View>` is not a permitted RemoteViews class and made the widget blank. Now shows all three rows (dot + On/Off pill), and button taps are delivered (`TOGGLE_DEV` broadcast from the launcher was observed, the setting changed, widgets re-rendered). |
| 13 | Synchronisation after external changes | **Pass** | `adb shell settings put global adb_wifi_enabled 1` while the dashboard was open flipped the switch and status text to *On* without any user interaction (`ContentObserver`). |
| 14 | Persistence after force-stop / reinstall | **Partial** | Snapshot and theme survived `am force-stop`, app updates (`adb install -r`) and process death; re-opening the app showed the saved snapshot. Device reboot was **not** performed in this pass (the permission is documented as surviving reboots, but that was not re-tested here). |
| 15 | Widget surface (Default / Clear) and specular rim | **Pass** | Switching to *Clear* left both widget bodies transparent — the wallpaper image is visible through the expanded widget — with light text. Toggling *Specular highlight rim* rewrote the widget background: rim on shows the bright edge and top reflection arc, rim off the faint 1 dp border (`bg_widget_clear` vs `bg_widget_clear_flat`). Preference persisted (`widget_surface=1`, `widget_specular_rim=false` then `true`) and every change re-rendered the pinned widgets without reopening the app. Screenshots: `settings-surface.png`, `widget-clear-rim.png`, `widget-clear-flat.png`, `widget-default-rim.png`. |
| 17 | Pill widget (2×1) | **Pass** (rendering) | Pinned through *Settings → Pin pill widget* → system *Confirm*; `dumpsys appwidget` shows `PillWidgetProvider` id 266. Renders brand + `widget_pill_switch_on` at the Development state, 40 dp tall, inside the chosen surface/rim. First build clipped the label to "DevGua" at the width the launcher hands a 2×1 cell; dropping the leading icon fixed it. The tap itself was **not** fired on the live device — it enters Safe Mode and kills the ADB link used to test — the pill's root click reuses the `ACTION_MODE` path verified in checks 10 and 12. Screenshot: `widget-pill.png`. |
| 16 | Both themes | **Pass** | Light and dark screenshots captured; theme choice persisted; system-bar icons and the navy hero card stay legible in both. |

Additional checks performed:

- Widgets and tile track the real settings after every write (State 1/2 transitions in SystemUI logs).
- Airplane/Wi-Fi dependent copy: the app warns when Wireless Debugging is on without a Wi-Fi transport.
- Widget rendering verified for both sizes; compact and expanded use `RemoteViews`-legal views only.
- Release build: R8 + resource shrinking succeeds; APK 59 KB, signed with the project key
  (`CN=DevGuard, O=DevHoss, C=US`), verified with `apksigner`.
- `lintVitalRelease` passes as part of the release build.

---

## Samsung / One UI notes

1. **Injected touch does not reach the notification shade** on this build. `adb shell input tap`
   and `input touchscreen tap` were ignored inside the Quick Settings panel (key events such as
   Back still worked), so the tile had to be exercised with `cmd statusbar click-tile` and the
   panel page was paged by hand for the screenshot. This is a test-automation constraint, not an
   app defect.
2. **Samsung Freecess** freezes the app when it is in the background. Widgets and the tile wake
   it through their `PendingIntent`/`TileService` entry points, which worked; the app also
   refreshes widgets whenever it comes to the foreground.
3. Custom implicit broadcasts are blocked for manifest receivers on modern Android; the debug
   probe has to be sent explicitly (`-n .../.DebugProbeReceiver`).
4. Turning Developer Options off disables the ADB daemon on this device. This is expected, and
   is why Safe Mode requires a deliberate one-tap restore to get debugging back.

---

## Not verified / follow-ups

- Partial restore failure (check 9) — needs a Wi-Fi-off or policy-blocked scenario.
- Reboot persistence (check 14) — worth one manual reboot check.
- Locked-device tile behaviour: the tile uses `unlockAndRun`, which requires the unlock flow;
  this was not observed with the screen actually locked.
- Banking-app behaviour is explicitly out of DevGuard's control and was not tested.
