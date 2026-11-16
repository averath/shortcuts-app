# NFC Shortcuts

Small Kotlin/Compose Android app: **when a trigger happens → run a list of actions**.
First trigger: scanning a specific NFC tag. Actions: Bluetooth / Wi-Fi / mobile data on/off, and **open an app** (e.g. Storytel).

## Use
1. Install `NfcShortcuts-0.4.0.apk` (minSdk 29 / Android 10+).
2. Open the app → **New rule** → hold your tag to the phone (UID fills in) → keep Bluetooth, Wi-Fi, Mobile data = On → Save.
3. Lock-screen off, phone unlocked, app closed: tap the tag. Actions run, a toast shows the result.

## Sleep setup (v0.2.0)
**New rule** → tap the **Sleep** preset → scan your bedside tag → Save.
The preset sets Wi-Fi, Bluetooth and Mobile data = Off and adds **Open Storytel** (`grit.storytel.app`).
Any rule can open apps: **Add app** in the editor shows all installed apps (searchable). Apps open after
all toggles (and any confirmation panels), so Storytel ends up in front.

## Start / finish feedback (v0.3.0)
- **Start:** one short vibration + ongoing "Running Sleep…" notification.
- **Finish:** two short vibrations (one long one if something failed) + the notification turns into
  "Sleep finished" with ✓/✗ details, then disappears after 8 s. Fires only after all toggles, panels and app launches.
- Allow notifications when the app asks (Android 13+); otherwise the same info is shown as a toast.

## Silent mode (Shizuku) – needed for mobile data
Android forbids normal apps from silently toggling Wi-Fi/Bluetooth (API 29+/33+) and has never allowed mobile data.
The app therefore has two strategies:

| | With Shizuku | Without |
|---|---|---|
| Wi-Fi | `svc wifi enable` – instant | Wi-Fi panel, 1 tap |
| Bluetooth | `cmd bluetooth_manager enable` – instant | "Allow?" dialog, 1 tap |
| Mobile data | `svc data enable` – instant | Internet panel, 1 tap |

Setup: install [Shizuku](https://shizuku.rikka.app/), start it via *Developer options → Wireless debugging*,
then press **Grant access** in this app. (Shizuku must be restarted after each reboot unless the device is rooted.)

## Bluetooth helper – silent Bluetooth without Shizuku (v0.4.0)
Android 13 turned `BluetoothAdapter.enable()/disable()` into a no-op for apps **targeting** API 33+.
The check uses the app's `targetSdk`, not the phone's Android version, so the companion APK in
module `:bthelper` (`pl.shortcuts.bthelper`) targets **API 32** and may still switch Bluetooth.

- Works without Shizuku, root or Device Owner, and keeps working after reboots.
- The main app calls it through `ContentResolver.call()` on `content://pl.shortcuts.bthelper.control`.
- The helper accepts calls **only** from `pl.shortcuts.app` signed with the **same certificate** –
  build both APKs with the same `keystore.properties`.
- Order for Bluetooth actions: helper → Shizuku → system dialog. Wi-Fi and mobile data still use Shizuku.

Setup:
1. `./gradlew :bthelper:assembleRelease` and install `bthelper-release.apk` (normal install, no ADB flag
   needed – Android 14+ blocks only targetSdk < 23/24).
2. Open **NFC Shortcuts BT helper** once → **Grant Bluetooth permission** ("Nearby devices") → test OFF/ON.
3. In NFC Shortcuts the **Bluetooth helper** card shows *Active*.

Do **not** raise `targetSdk` of `:bthelper` to 33+ – that silently breaks it.

## Structure
```
model/Rule.kt            Rule, sealed Trigger (NfcTag), sealed Action (SetToggle, LaunchApp), Preset – kotlinx.serialization
data/RuleRepository.kt   JSON in SharedPreferences, StateFlow
engine/RuleEngine.kt     TriggerEvent -> matching rules -> executor
engine/ActionExecutor.kt Shizuku shell strategy + system-panel fallback
engine/Feedback.kt       start/finish vibration + notification
shizuku/                 AIDL user service running `sh -c` with shell privileges
helper/BluetoothHelperClient  IPC client for the :bthelper APK
bthelper/                companion APK (targetSdk 32): BluetoothControlProvider + SetupActivity
ui/NfcTriggerActivity    invisible activity started by NFC TECH_DISCOVERED dispatch
ui/MainActivity          Compose rule list/editor; reader mode captures tag UIDs
ui/AppPicker             searchable list of launcher apps for the Open app action
```
Add a trigger: new `Trigger` subtype + an event source (BroadcastReceiver, WorkManager, …).
Add an action: new `Action` subtype + a branch in `ActionExecutor`.

## Build
`./gradlew assembleRelease` builds both APKs (JDK 17, Android SDK 35). Release signing reads `keystore.properties` in the project root (gitignored; keys `storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Without it, the release build falls back to the debug key.
