# Samsung Region Override

[简体中文](README.zh-CN.md) | English

**Keep your SIM inserted and keep using mobile data while temporarily changing the SIM region seen by
Galaxy Store, Samsung Members, TikTok and other region-sensitive apps. No spare SIM and no Wi-Fi
handoff are needed once Shizuku is running. Restore the real region with one tap when you are done.**

On the tested Galaxy, mobile data remained available throughout apply and restore. The SIM operator layer
can still affect calling or messaging if IMS reconnects later, so short sessions followed by a prompt
restore are recommended.

[Download the latest signed APK](https://github.com/Ritel-T/SamsungRegionOverride/releases/latest)

> **Upgrading from 3.x:** version 4 uses the new Android package id `com.ritelt.regionoverride`, so it
> installs beside version 3 instead of replacing it. End every version 3 disguise before uninstalling
> the old app. Saved restore data and the Shizuku grant do not move between package ids.

<table>
  <tr>
    <th>Ready</th>
    <th>Disguise active</th>
  </tr>
  <tr>
    <td><img src="docs/images/app-ready.png" width="420" alt="Samsung Region Override ready to start a South Korea SKT disguise on SIM 1."></td>
    <td><img src="docs/images/app-active.png" width="420" alt="Samsung Region Override showing a live South Korea SKT disguise beside the real China Unicom identity."></td>
  </tr>
</table>

## Highlights

- **No SIM swap.** The real card, phone number and carrier attachment stay in place.
- **No Wi-Fi detour.** After Shizuku is running, the app works locally and has no Internet permission;
  the tested phone kept mobile data online while switching and restoring.
- **Change only what the target app needs.** App country and SIM operator are independent layers.
- **Easy to undo.** The main action becomes **End & restore** while a disguise is live, and an ongoing
  notification provides **Restore now** from anywhere.
- **Made for dual SIM.** The data SIM is clearly marked, because that is the SIM region most apps read.
- **Refresh target apps in one tap.** Convenient controls restart a target app so it can re-read and
  apply the selected region; clearing its cache or data remains optional.
- **Private diagnostics.** A compact report can be copied or shared without exporting raw logcat, SIM
  identifiers or the installed-app list.

## Galaxy Store before and after

On the reference Galaxy, Galaxy Store initially refused to open for the real SIM region. Applying the
SIM operator layer and reopening the app changed it to the selected Korean storefront, without swapping
the SIM.

<table>
  <tr>
    <th>Before</th>
    <th>After switching region</th>
  </tr>
  <tr>
    <td><img src="docs/images/store-before.png" width="360" alt="Galaxy Store saying that it is not supported in the current country."></td>
    <td><img src="docs/images/store-after.png" width="360" alt="Galaxy Store open on its Korean storefront after the region switch."></td>
  </tr>
</table>

## Material 3 Expressive

Version 4 rebuilds the interface with **M3 Expressive**: expressive shapes and loading motion, elastic
buttons, full-card touch feedback, clearer live-state colors, adaptive portrait and landscape layouts,
and an always-available bottom action bar.

<p align="center">
  <img src="docs/images/material3-expressive-progress.gif" width="720"
       alt="The result card smoothly changing shape while Samsung Region Override runs a five-stage operation.">
</p>

The result card is present before the first run, becomes the operation progress surface while work is in
flight, and collapses back to a concise outcome. Technical details and reporting actions stay one tap
away without crowding the main workflow.

## Quick start

1. Install the APK from [Releases](https://github.com/Ritel-T/SamsungRegionOverride/releases/latest).
2. Start Shizuku and grant Samsung Region Override access.
3. Select the SIM carrying mobile data, then choose a country/carrier preset.
4. Enable only the layer or layers your target app needs.
5. Tap **Start disguise** and wait for the result card to finish.
6. If the target app still shows its old region, expand **Target apps** and use **Force stop** or
   **Stop & open** for that app.
7. When finished, return to the app or notification and tap **End & restore**.

Turning a layer switch off only affects the next apply; it does not clear the current override. Once a
layer is live, tap the Restore button to clear it.

## Which layer should I use?

| Layer | Useful for | What changes | Main trade-off |
|---|---|---|---|
| **App country** | TikTok and apps that read the SIM country ISO | CarrierConfig country ISO; optionally the displayed carrier name | Reloading CarrierConfig can trigger an IMS reconnect if a fake SIM operator is already live |
| **SIM operator** | Galaxy Store, Samsung Members and Samsung apps that read MCC/MNC | MCC/MNC, test IMSI, SPN and PNN | A future IMS reconnect may try to register as the fake carrier and interrupt calls or IMS messaging |
| **Both** | Apps that compare both signals | App country first, then SIM operator | Broader signal coverage; restore the SIM operator layer when you finish |

Start with the narrower layer. App country is a useful first choice for TikTok-style country checks;
Galaxy Store and other Samsung carrier checks normally use SIM operator. Account country, IP address,
CSC, GPS, app version, server-side experiments and cached data can also take part, so results vary by app.

## Restore from anywhere

When a disguise is live, a compact flag chip keeps the current region visible in the status bar. The
ongoing notification shows the real and disguised identities and keeps a direct Restore action available
outside the app.

<p align="center">
  <img src="docs/images/live-status-chip.png" width="420"
       alt="Samsung Region Override status chip showing the South Korea flag while a disguise is live.">
</p>

<p align="center">
  <img src="docs/images/restore-notification.png" width="720"
       alt="Samsung Region Override notification over Android Settings, showing a South Korea SKT disguise, the real China Unicom identity and a Restore now button.">
</p>

Notification permission is optional. Apply and Restore still work without it; the live status indicator,
reminder and shortcut simply remain hidden.

## Requirements and tested scope

- Android 10 (API 29) or newer.
- Shizuku 13+ running as shell or root and authorized for this app. Root is not required.
- An active SIM or eSIM; the selected SIM must be `READY` when using the SIM operator layer.
- Recent Samsung firmware is the supported focus. Other Samsung and non-Samsung implementations have not
  been tested as thoroughly.

Development and hands-on testing currently center on a Galaxy S25 Ultra (SM-S938B), including Android 16 /
One UI 8.5 and Android 17 / One UI 9 Beta. Other devices and carrier combinations can behave differently.

## Target apps

The editable default list contains:

| Package | App |
|---|---|
| `com.sec.android.app.samsungapps` | Galaxy Store |
| `com.samsung.android.voc` | Samsung Members |
| `com.zhiliaoapp.musically` | TikTok |

With **Open it afterwards** enabled, the shortcut in each card stops and reopens that target app in one
tap. **Keep** preserves storage, **Cache** requests a cache clear, and **Data** removes all local app data.
Clearing data signs you out and can delete downloads, drafts and settings. If cache clearing is
unsupported or times out, the result card shows what actually happened.

## Calls, IMS and recovery

The SIM operator layer changes a framework-wide identity, not a value visible only to Galaxy Store. An
existing IMS session can remain healthy immediately after apply, then fail later after signal loss,
airplane mode, a SIM/UICC cycle, a CarrierConfig refresh or another reconnect.

The reproduced failure on the reference phone was:

1. a reconnect occurred while the fake MCC/MNC was live;
2. Samsung IMS kept the real carrier profile but derived its home domain from the fake MCC/MNC;
3. registration against that mismatched domain was rejected;
4. restoring the real identity before a controlled UICC cycle recovered IMS.

The app therefore applies App country before SIM operator and restores SIM operator before App country.
It cycles UICC only after the real operator is confirmed, then restores the captured display name and
flags an IMS state that is not yet confirmed. The post-apply IMS sample describes the current moment;
later reconnects can still behave differently.

See [IMS failure investigation](docs/ims-investigation.md) for the reproduced sequences and framework
references.

## How it works

The implementation is designed to leave a reliable restore path for every change:

- **SIM operator:** resolves Samsung's runtime `ITelephony.setCarrierTestOverride` signature and invokes
  it through a Shizuku UserService running with shell identity. No Binder transaction number is fixed in
  the source.
- **App country:** invokes `CarrierConfigManager.overrideConfig` from a short-lived instrumentation under
  the app package identity after adopting only the required shell phone-state permissions. Android 17
  uses a completed UiAutomation handshake to avoid the platform's connecting/finish race.
- **Ordering:** when both layers are selected, Country completes its reload before Network is written.
  Restore reverses that order so the real operator is back before any country reload can reconnect IMS.
- **Recovery state:** real MCC/MNC, operator name, country ISO and subscription display name are captured
  before the first write. A synchronous pending journal lets the app resume recovery after an interrupted
  process.
- **SIM safety:** snapshots are bound to a one-way card fingerprint when the firmware exposes one. The
  raw ICCID never leaves the shell service, and a reused subscription id cannot silently restore another
  card's values.

Compose runs in a separate `:ui` process while a minimal default-process service keeps the Android 17
instrumentation target alive. CarrierConfig reload waits are bounded; partial results preserve enough
state to keep Restore available and explain what completed.

## Diagnostics and privacy

The result card keeps full local operation details on the device. **Copy**, **Share** and **Report issue**
use only an allow-listed `SRO-DIAGNOSTIC/1` summary containing broad device/runtime categories and the
failed layer. It excludes subscription ids, ICCID, IMSI, IMEI, EID, phone number, card fingerprint, ADB
serial, full build fingerprint, package list, raw exception messages, logcat and dumpsys.

The app has no Internet permission, telemetry or account system. It does not automatically read or upload
global logs. See [Diagnostics](docs/diagnostics.md) for the reporting flow.

## Languages

The UI includes English, Simplified Chinese, Traditional Chinese, Japanese, Korean, French, German,
Spanish, Brazilian Portuguese, Russian, Turkish, Arabic, Indonesian, Thai and Vietnamese. Android 13+
exposes them in per-app language settings; older releases follow the system language.

## Build and test

The Android application id is `com.ritelt.regionoverride`. JDK 17 or newer is required; CI uses JDK 21.
The wrapper pins Gradle 9.7.1, AGP 9.3.2 provides Kotlin 2.2.10, and the project compiles/targets API 37.

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release signing uses the environment-variable workflow in [Release procedure](docs/RELEASING.md).
Signing material and generated APKs stay outside Git. Each GitHub Release publishes the signed APK, its
SHA-256 and the signing-certificate SHA-256.

`connectedDebugAndroidTest` uninstalls the app when it finishes and therefore discards restore snapshots.
Do not run it on a device with an active or pending disguise.

## Known limits

- The current selector presents up to two active consumer-phone subscriptions.
- Presets are convenience data, not a live carrier database.
- Some applications continue to use account, IP or cached region after both layers change.
- Firmware that hides the card identifier cannot bind new snapshots to a verified card identity; the UI
  explains the conservative reset path if identity availability later changes.
- The app changes local framework test overrides. It does not grant carrier entitlements, paid content or
  network access.

Use it only on devices and accounts you control, and follow the target service's terms and local law.
Samsung, Galaxy Store, Samsung Members, TikTok/ByteDance, Google and Shizuku do not sponsor or endorse
this project.

## License

[MIT](LICENSE). Vendored Shizuku libraries retain their Apache-2.0 license; see
[third-party notices](THIRD_PARTY_NOTICES.md).
