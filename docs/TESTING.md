# Device-testing report — DevGuard 1.0.1

**Device:** Samsung Galaxy A53 5G (SM-A536E), Android 16 (SDK 36), One UI, 1080×2400 (density override 420)
**Build under test:** debug `com.devhoss.devguard` 1.0.1 (versionCode 2), installed with `adb install -r`
**Release artifact:** `dist/DevGuard-1.0.1-release.apk`, 68,984 bytes, signed by `CN=DevGuard, O=DevHoss, C=US`
**Permission:** granted with `adb shell pm grant com.devhoss.devguard android.permission.WRITE_SECURE_SETTINGS`
**Method:** ADB for state reads/writes, `uiautomator` for hierarchy checks and taps, `screencap` for the
screenshots in `docs/screenshots/`, logcat for outcomes observed while ADB was disconnected.

## The debug test harness

Disabling debugging necessarily kills the ADB link used to drive a test, so debug builds ship
`app/src/debug/java/.../DebugProbeReceiver.java`. It is **not** part of release builds (verified:
zero occurrences of `DebugProbeReceiver`, `DevGuardProbe`, `keep_usb` or `stage_dev` in the release dex).

It accepts explicit broadcasts with these extras:

| Extra | Effect |
|---|---|
| `delay_seconds` | Wait, then call the production `ModeController.restoreDevelopmentMode()` |
| `delay_seconds -1` | Arm only: apply fault flags and log them, run nothing (used to confirm a test is armed before a risky restore) |
| `stage_dev` / `stage_usb` / `stage_wifi` | Replace the stored snapshot at restore time (test the "saved OFF" and "USB off" cases without locking myself out) |
| `keep_usb` | Re-enable **only** USB debugging after a delay (needed to keep a test link across the reboot test) |
| `reject` | Comma-separated setting keys whose writes behave like an Android rejection (`Faults`, debug seam only) |

A `restore starting | dev=… usb=… wifi=…` line is written *before* the restore. It survives in the
logcat ring buffer, so the state the device reached while ADB was down can still be read afterwards.

The injection seam (`Faults.java`) is compiled into both variants but its setter is reachable only
from the debug receiver; R8 removes it from release. A real device-policy rejection cannot be produced
on a retail A53, so this is a simulation of the framework refusal path, not a real policy block.

---

## Results

| # | Check | Result | Evidence |
|---|---|---|---|
| 1 | Permission onboarding and recheck | **Pass** | Revoking the grant puts *"DevGuard needs the WRITE_SECURE_SETTINGS permission…"* in the hero card with a **Finish setup** button; the switches stay disabled but still show the real values. Re-granting restores the normal card. Screenshot: `permission-required.png`. |
| 2 | Developer Options write | **Pass** | Safe Mode turns it off, restore turns it back on; both read back and verified. |
| 3 | USB Debugging write | **Pass** | Safe Mode turns it off and the ADB link drops — direct proof the system applied it; restore brings the link back. |
| 4 | Wireless Debugging write | **Pass** | On/off round trip with Wi-Fi connected; UI, tile and widgets follow. |
| 5 | Snapshot capture | **Pass** | Stored payload after this pass: `{"version":1,"timestamp":1795598442095,"devOptions":true,"usb":true,"wireless":1}`. |
| 6 | Safe Mode activation | **Pass** | Probe logged `restore starting … dev=false usb=false wifi=false`. |
| 7 | Full restoration | **Pass** | `restore done status=SUCCESS … dev=true usb=true wifi=true`. |
| 8 | Snapshot preserved across repeated Safe Mode | **Pass** | Timestamp `1795598442095` survived several Safe Mode cycles; `captureIfAbsent` never overwrote it. |
| 9 | Partial restore — Wi-Fi disconnected | **Pass** | `status=PARTIAL … "Wireless debugging could not be restored: Wi-Fi is not connected"`. The setting was accepted, then Android cleared it seconds later, so the PARTIAL verdict matched the end state. Screenshot: `restore-wifi-off-toast.png`. |
| 10 | Android refusing a Wireless write | **Pass** (injected) | `status=PARTIAL … "Wireless Debugging could not be changed. Android rejected the request."` USB was still restored in the same pass. |
| 11 | Android refusing a USB write | **Pass** (injected) | `status=PARTIAL … "USB Debugging could not be changed. Android rejected the request."` The setting stayed on, which is also why the test link survived. |
| 12 | Restore when the saved Developer Options were OFF | **Pass** | Staged snapshot `dev=false usb=false wifi=0`: `status=SUCCESS … dev=false usb=false wifi=false`; a queued recovery probe then restored the full configuration. |
| 13 | Permission revoked | **Pass** | Restore returns `status=FAILURE` with the ADB permission text and changes nothing; the tile does not alter settings; the dashboard shows real values with disabled controls and the setup affordance. |
| 14 | Reboot persistence | **Pass** (see caveat) | After `adb reboot`: permission `granted=true`, snapshot timestamp unchanged, Developer Options still off, widgets re-bound and the pill rendered **Off**, and a tile click restored all three settings. Caveat: Safe Mode leaves USB debugging off, so `keep_usb` re-enabled only that transport before the reboot to keep a test link. Developer Options stayed off, so the device was still in Safe Mode. Screenshot: `after-reboot-safe.png`. |
| 15 | Quick Settings tile after reboot | **Pass** (second click) | The first `click-tile` right after boot was ignored while SystemUI was still starting; a click one minute later logged `State = 2` and restored the configuration. |
| 16 | Locked-device tile | **Not verifiable on this device** | The phone has a swipe-only keyguard (no PIN/pattern/credential), so `unlockAndRun` has nothing to authenticate and runs immediately. The tile therefore applied Safe Mode while the screen was "locked" — expected behaviour for a device with no credential, not a bypass. The authentication path cannot be tested until a device credential exists. |
| 17 | Pill widget tap | **Pass** | Tapping the pill on the home screen dropped ADB; the probe logged `restore starting … dev=false usb=false wifi=false` and the restore returned `status=SUCCESS`. Screenshot: `widget-pill-dev.png`. |
| 18 | Expanded widget layout | **Pass** (after fix) | At 420 dpi the widget cell is 220 dp tall. The 1.0.0 layout needed 227 dp, so the action button was clipped; 1.0.1 uses 191 dp (rows 32 dp, padding 12 dp, action 38 dp) and fits. "Wireless debugging" was truncated behind the On pill and is now "Wireless". Screenshot: `widget-expanded-clear.png`. |
| 19 | Widget accessibility text | **Pass** | Rows read as `Developer options, On`, `USB debugging, On`, `Wireless debugging, On` instead of the app name; the pill reads `DevGuard, Development. Tap to switch mode.` |
| 20 | Widget surface and rim | **Pass** | Clear surface with rim on remains transparent with the wallpaper visible; Default and rim-off variants unchanged since 1.0.0. Screenshots: `widget-clear-rim.png`, `widget-clear-flat.png`, `widget-default-rim.png`. |
| 21 | Light and dark themes | **Pass** | Screenshots `dashboard-dark.png`, `dashboard-light.png`, `settings-dialog.png`; theme preference persisted and was reset to *Follow system* afterwards. |
| 22 | Release build, signing and contents | **Pass** | `assembleRelease` exits 0; 68,984 bytes; `versionCode=2`, `versionName=1.0.1`; permissions are exactly `WRITE_SECURE_SETTINGS` and `ACCESS_NETWORK_STATE` (no `INTERNET`); certificate matches the project key. |
| 23 | Debug-only code excluded from release | **Pass** | Manifest has no probe receiver; dex scan finds 0 occurrences of `DebugProbeReceiver`, `DevGuardProbe`, `keep_usb`, `stage_dev`; `Faults` is inlined away. |
| 24 | Wi-Fi detection | **Pass** (normal) | Restores with Wi-Fi on and off behave as reported; disabling Wi-Fi makes Android clear `adb_wifi_enabled` within the same second. |
| 25 | Wi-Fi detection with a VPN | **Not device-tested** | No VPN transport was active during the pass. The implementation enumerates every network and ignores VPN transports instead of looking only at the active network, which is what fixes the old false "no Wi-Fi". |
| 26 | Android 8–10 (API 26–29) behaviour | **Not device-tested** | One Android 16 device only. Wireless Debugging paths are guarded by `Build.VERSION` checks and the row shows *Requires Android 11 or newer*. |

### Side note from the Wi-Fi test

An automation companion (`app.nectarlink`) also held `WRITE_SECURE_SETTINGS` and was suspected of
toggling wireless debugging during the test. Force-stopping it did not change the result: with Wi-Fi
off, `adb_wifi_enabled=1` is reverted to `0` by the platform immediately, and with Wi-Fi on it sticks.
The behaviour is Android's, not another app's.

---

## Samsung / One UI notes

1. **Injected touch does not reach the notification shade.** `input tap` is ignored inside the Quick
   Settings panel, so the tile is driven with `cmd statusbar click-tile`; the panel itself has to be
   paged by hand for screenshots.
2. **Samsung Freecess** freezes backgrounded apps. Widget and tile entry points wake DevGuard, and the
   debug probe's delayed threads kept running through Safe Mode.
3. Custom implicit broadcasts are blocked for manifest receivers on modern Android; the debug probe
   must be sent explicitly (`-n …/.DebugProbeReceiver`).
4. Turning Developer Options off stops adbd on this device — the reason Safe Mode needs a deliberate
   restore to bring debugging back.
5. The launcher leaves a 220 dp-tall cell for the 4×3 widget; widget layouts must stay below that.
6. The first tile click immediately after boot can be dropped while SystemUI is still starting.

---

## Not verified / follow-ups

- Locked-tile authentication: impossible without a device credential (swipe-only keyguard here).
- A genuine Android/device-policy refusal: simulated through the debug-only seam, not a real policy.
- VPN-present Wi-Fi detection: logic reviewed and reasoned about, but no VPN was active during the pass.
- Android 8–10: code paths guarded, not exercised on hardware.
- Safe-state visuals of the widgets: reaching Safe Mode drops ADB, so the Safe state is proven by the
  probe's log line and the post-reboot screenshot, not by a live screenshot taken during those seconds.

## Screenshots

| File | Shows |
|---|---|
| `dashboard-dark.png`, `dashboard-light.png` | Dashboard in both themes (1.0.1) |
| `settings-dialog.png` | Settings: appearance, widget surface, rim, ADB, pinning, version |
| `permission-required.png` | No permission: setup copy and **Finish setup** in the hero card |
| `restore-wifi-off-toast.png` | Partial-restore toast with Wi-Fi disconnected |
| `widget-expanded-clear.png` | Expanded widget, Clear surface, rim, fitting its cell |
| `widget-clear-rim.png`, `widget-clear-flat.png`, `widget-default-rim.png` | Surface/rim variants |
| `widget-pill-dev.png` | 2×1 pill widget in Development Mode |
| `after-reboot-safe.png` | Home screen after reboot: pill shows Safe |
| `widget-pill.png`, `quick-settings.png` | Earlier visual set (1.0.0, unchanged artwork) |

---

## 1.0.2 — responsive pill on rotation

The launcher transposes the home-screen grid when the device rotates, so the 2×1 pill is handed a
tall single column instead of a wide row. Cell sizes read from `dumpsys appwidget` on the test
device (SM-A536E, Android 16, 420 dpi):

| Orientation | Pill cell | Rendering |
|---|---|---|
| Portrait | 235 × 50 dp | Flat pill: brand left, 44 × 26 dp switch right (unchanged from 1.0.1) |
| Landscape, full column | 50 × 229 dp | Upright pill: rotated "DevGuard" label at the top, 26 × 48 dp switch at the bottom edge |
| Landscape, column shrunk by hand | 50 × 139 dp | Same upright layout, theme surface between label and switch, nothing clipped |

`Widgets` now builds each widget for its own cell from `getAppWidgetOptions` (portrait size = the
minimum, landscape = the maximum) and all three providers rebuild on
`onAppWidgetOptionsChanged`, so rotation re-renders instead of stretching the old layout.

Verified by taking device screenshots in each state above. Not verified: the upright switch in Safe
Mode (muted track, thumb at the bottom) — reaching that state turns USB debugging off and drops
ADB, so it was left to normal use.

### 1.0.3 — upright pill alignment and top inset

Measured from device screenshots in the 50 × 229 dp rotated cell at 420 dpi: the switch's centre sat
at x = 440.7 px and the card's centre at 441.0 px, but the rotated label's ink sat at 444.8 px — the
font's ascent/descent leaves the glyphs low in the line box, and `rotation="-90"` turns that vertical
offset into a horizontal one. A 3 dp bottom padding on the label lifts the ink, giving label ink
440.8 px vs switch 440.7 px, i.e. one shared centre line. The top inset went from 6 dp to 14 dp so
the label clears the card's rounded corner (Tailscale-style gap); the layout needs 130 dp, still
inside the 139 dp cell the home screen can shrink to.
