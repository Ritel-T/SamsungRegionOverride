# Samsung Region Override

A Shizuku tool, aimed primarily at recent Samsung phones, that temporarily changes the SIM/carrier
region that apps such as Galaxy Store, Samsung Members and TikTok read. It combines two complementary
mechanisms into a two-layer override, each independently switchable and independently restorable:

1. **SIM identity** — overrides MCC/MNC, a test IMSI and PNN/SPN through
   `ITelephony.setCarrierTestOverride()`, resolved reflectively rather than by hard-coded binder
   transaction id.
2. **App country** — following the approach of [nrfr-android16-optimized][nrfr], overrides
   `sim_country_iso_override_string` through `CarrierConfigManager.overrideConfig()`, and optionally the
   carrier display name.

The radio, the network it is camped on, the phone number, GPS and the IP address are untouched.

## Why two layers

On SM-S938B / Android 16 / One UI 8.5, Samsung's carrier test override changes the target slot's MCC/MNC
and name to `23430 / EE`, but the system's SIM country can stay at `cn`. Conversely an Nrfr-style
CarrierConfig override changes the ISO country apps see without faking the SIM MCC/MNC that Samsung's own
software reads. Both layers together are therefore the primary mode for Galaxy Store and Samsung Members;
app country alone suits general apps such as TikTok.

## What it does

- Two-SIM aware. Slots are laid out side by side at fixed positions — a slot the hardware has but nothing
  occupies keeps its place as a hatched placeholder, so the layout does not move when a SIM appears.
- A catalog of around 200 carriers across some 75 countries, searchable by country, carrier, ISO or
  MCC/MNC at once. Any value can still be typed by hand; editing a field drops the preset.
- Custom 5–6 digit MCC/MNC, two-letter ISO country and carrier name. The test IMSI is derived from the
  MCC/MNC and padded to 15 digits.
- Accepts both `setCarrierTestOverride(int + 9 Strings)` and the older `int + 7 Strings` signature, and
  refuses to call anything it does not recognise.
- ICCID, GID1, GID2, carrier privilege rules and APN are always `null`.
- CarrierConfig writes only the **transient** layer by default, so it does not survive a reboot; an
  ordinary restore clears only that layer.
- A separate "clear all CarrierConfig test overrides" exists for explicitly removing the persistent
  values other tools leave behind. It asks for confirmation, and it is unavailable on hardware with no
  persistent override API — see the correction below.
- Each target app gets its own button to stop it, optionally wipe its storage, and optionally reopen it,
  so it re-reads the region without waiting to be killed by the system.
- The UI, the instrumentation host and the Shizuku UserService run in separate processes, so Samsung's
  `NO_RESTART` instrumentation path cannot take the interface down. The `androidx.startup` initializers
  Compose brings in (emoji2, ProcessLifecycle, ProfileInstaller) are pinned to `:ui`, leaving the default
  process as nothing but a bare binder — it is what AMS attaches the instrumentation to, and it should not
  also be doing font loading and ART profile writes.
- Each SIM shows what it currently reports and which layers this tool has written to it, and
  "now → target" is laid out for digit-by-digit comparison. After an operation the SIMs are re-read seven
  times over about five seconds, so the change is visible landing.

## Architecture

The interface is Kotlin + Jetpack Compose. The privileged core that calls hidden interfaces is still
Java and is unchanged.

```
ui/                        Compose UI: OverrideScreen + components/, theme in ui/theme/
ui/OverrideViewModel.kt    the only state holder; one operation = one cancellable coroutine
ui/RegionPresets.kt        the carrier catalog
data/                      ShizukuController / InstrumentationHost / OverrideRepository /
                           OverrideStore / SimRepository / TargetAppRepository
*.java                     TelephonyBridge, CarrierConfigBridge, CarrierConfigInstrumentation,
                           CarrierOverrideUserService, InstrumentationHostService, TargetApps,
                           RuntimeProbe
```

The privileged path — reflective `ITelephony` and `CarrierConfigManager` calls, the AIDL transaction ids,
the instrumentation flow — is left as it was, because it has been validated on real hardware. `TargetApps`
is the one addition, and it only shells out to `am` and `pm`.

In 2.x an "apply" was a state machine spread across five callbacks re-entering a shared `pendingAction`
field. It is now one straight-line coroutine, which is why **waiting for Shizuku or for the permission
grant can be cancelled**, and why progress is reported as five honest stages.

## Building

Needs **JDK 21** (AGP 9) and Android SDK Platform 37.

```powershell
$env:JAVA_HOME = "C:\Users\<you>\scoop\apps\openjdk21\current"
$env:GRADLE_USER_HOME = "$PWD\.gradle-user-home"
.\gradlew.bat :app:assembleDebug
```

The first build **must be online** to fetch the Kotlin/Compose dependencies. Once they are cached in
`.gradle-user-home`, `--offline` works as before.

The APK lands in [`app/build/outputs/apk/debug/app-debug.apk`](app/build/outputs/apk/debug/app-debug.apk).

Toolchain: Gradle 9.7.0, AGP 9.3.1, Kotlin 2.2.10, Compose BOM 2026.06.01. `compileSdk`/`targetSdk` are
37 and `minSdk` is 29; the current version is `3.1.0`.

A few build constraints worth knowing:

- **AGP decides the Kotlin version.** AGP 9 ships Kotlin built in, and additionally applying
  `org.jetbrains.kotlin.android` fails outright on an extension name clash. AGP 9.3.1 pins Kotlin 2.2.10,
  so the Compose compiler plugin must be exactly 2.2.10 — plugin and compiler are ABI-bound.
- `app/libs` pins the Shizuku API 13.1.5 AARs. The `annotation-1.3.0.jar` that shipped with them in the
  2.x tree is gone: it duplicates the `androidx.annotation` classes Compose pulls in transitively and
  fails the build with `Duplicate class`.
- `androidx.startup.InitializationProvider` is pinned to the `:ui` process in the manifest.
- R8 is off for release. `CarrierConfigBridge` names the instrumentation class with a string constant, so
  enabling minification needs keep rules first or the privileged path will not find the class at runtime.
- The debug APK is roughly 40 MB: uncompressed, and carrying the debug-only `ui-tooling`.

## Using it

1. Install and start Shizuku. In non-root mode it has to be restarted after every reboot.
2. Install the APK, open it, and pick the target SIM.
3. Choose a region from the catalog, or type the MCC/MNC, ISO and carrier name in directly.
4. For Galaxy Store and Samsung Members leave both layers on. For apps like TikTok, app country alone is
   often enough.
5. Press Apply, confirm, and grant Shizuku permission.
6. Use the target apps panel to stop and reopen an app so it picks the new region up.
7. Press Restore when finished. Reboot if SIM state or IMS misbehaves.

With no SIM at all there is usually no valid `subId` or `IccRecords`, so the SIM identity layer cannot
work; the CarrierConfig layer also requires a valid subscription.

## Target apps

Apply and restore already force-stop every target app. The panel does the same on demand — one button per
app, not one button for all of them, because reopening brings an app to the foreground and a bulk run
would throw three apps up in sequence and leave you wherever the last one landed. It adds the two options
that only make sense when a person is asking for them:

| Wipe | What runs | Notes |
| --- | --- | --- |
| Keep | `am force-stop` only | Always safe. Enough for an app that re-reads region on cold start. |
| Cache | `pm clear --cache-only` | **Does nothing on One UI 8.5** — see below. Reported as `timeout`. |
| Data | `pm clear` | Signs you out. The only wipe that works on the primary target device. |

"Open it afterwards" resolves the launcher activity and starts it. The underlying call still accepts a
list — it is what apply and restore use to stop everything at once — and when given several packages it
launches them in reverse order so the first listed ends up on top rather than buried.

An app that is not installed is shown as `ABSENT` with its button disabled, and every step reports its own
exit status per package, so a step that did nothing says so rather than being silently skipped.

The list is currently the three packages above. It is passed to the privileged call as an array rather
than assumed there, so a user-chosen list drops in without touching the AIDL surface.

## Restore semantics and risk

- The SIM identity layer triggers a CarrierConfig reload, which can briefly disturb APN, VoLTE, IMS or
  mobile data.
- An ordinary restore writes back the MCC/MNC, name and ISO from the snapshot taken before the first
  operation. Because Samsung's country cache does not always fall back immediately after CarrierConfig is
  cleared, the original ISO is written first and the transient override dropped afterwards. AOSP has no
  separate clear API for the SIM test override, so a reboot remains the definitive restore.
- Passing `null` to the CarrierConfig API clears *every* test value in that layer, not just this tool's
  three keys. If another tool also uses the **transient** layer, an ordinary restore clears its values too.
- "Clear all CarrierConfig test overrides" also clears the persistent layer and cannot rebuild what
  another tool had set.

## Hardware verification — SM-S938B

Target: Samsung SM-S938B, Android 16, One UI 8.5, dual SIM (slot 1 `46009`, slot 2 `46001`, both READY).

### Telephony round trip (2026-08-12 / 2026-08-13)

Runtime probe:

```text
uid=2000
setCarrierTestOverride(int,String,String,String,String,String,String,String,String,String)
```

Both layers applied to subId 2 and then restored:

```text
MCC/MNC     46001 -> 23430
country     cn    -> gb
carrier     China Unicom -> EE
IMSI        234300000000001
call        setCarrierTestOverride(int,String x9)
```

After an ordinary restore:

```text
gsm.sim.operator.numeric      46009,46001     <- reverted
gsm.sim.operator.iso-country  cn,cn           <- reverted
gsm.sim.operator.alpha        China Unicom,EE <- not reverted
dumpsys carrier_config        mOverrideConfigs : null
                              carrier_name_override_bool = false
                              carrier_name_string =
                              sim_country_iso_override_string =
```

**The CarrierConfig layer clears completely** — `mOverrideConfigs` returns to `null`. The residual `EE`
does not come from the app country layer; it is the SPN/PNN the SIM identity layer wrote, still sitting in
the telephony framework's name cache. The restore call does pass `SPN/PNN=China Unicom` and the framework
accepts it, but `setCarrierTestOverride` has no way to clear that cache. Still true 40 seconds later, so
it is not a settling delay. This matches "a reboot is the definitive restore" above.

Both values that drive Galaxy Store and TikTok region detection — MCC/MNC and ISO — revert cleanly.

### Correction: this device has no persistent CarrierConfig API

"Clear all CarrierConfig test overrides" **cannot succeed here**. The runtime probe reports:

```text
carrierConfigMethod=overrideConfig(int,PersistableBundle)
```

Two parameters, no `boolean persistent`. An earlier note in this file recorded
`overrideConfig(int,PersistableBundle,boolean)`; that was wrong, and this section supersedes it.

So the operation fails and says so, rather than guessing:

```text
ERROR: Clearing CarrierConfig overrides failed
java.lang.IllegalStateException: java.lang.UnsupportedOperationException:
This Android version has no persistent override API
```

That is the designed behaviour. `CarrierConfigInstrumentation` throws when asked for a persistent write it
cannot make, which is the same probe-then-refuse rule the SIM identity layer follows instead of guessing
binder transaction ids.

The practical impact is limited: apply and ordinary restore only ever touch the transient layer and both
work. Only clearing **another tool's persistent values** is unavailable here; that needs a reboot or the
tool that wrote them.

### Target-app commands (2026-08-13)

Verified from an adb shell, which runs as the same uid 2000 the UserService does:

```text
am force-stop --user current <pkg>           ok
pm clear --user 0 <pkg>                      Success in 0.4s, cache directory gone
pm clear --cache-only --user 0 <pkg>         never returns; killed after 90s
cmd package resolve-activity --brief ...     com.sec.android.app.samsungapps/.SamsungAppsMainActivity
am start -a MAIN -c LAUNCHER -n <component>  ok
```

`--cache-only` was tested against a debuggable package by seeding a file in its cache directory: after the
call was killed, **the file was still there**. The platform accepts the request and then never invokes the
`IPackageDataObserver` the shell command blocks on. PackageManager stays responsive afterwards, so the
call is safe to make — it is simply useless here, which is why the wipe level is a visible choice and why
the report shows `timeout` rather than claiming success.

Stopping and reopening was confirmed working on this device. The installed/absent state was confirmed too,
from both sides: TikTok was missing at first and shown as `ABSENT`, and after it was installed the panel
picked that up on the next resume without a restart.

## Compatibility

- **Primary scope**: recent Samsung One UI / Android 12–16. Only the SM-S938B above has been fully
  validated on hardware.
- **Other vendors**: the CarrierConfig app country layer sits on an AOSP interface and should port
  reasonably well. The SIM identity layer depends on whether the OEM keeps a compatible signature and how
  it restricts shell. The app probes first and reports a failure on a mismatch rather than guessing binder
  transaction ids.
- Android and OEM updates can change hidden interfaces and permission policy, so every model and major
  version should get a capability probe and a reversibility test before being called supported.

## Sources

- [AOSP `CarrierConfigManager.overrideConfig()`][carrier-config]
- [AOSP `KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING`][country-key]
- [AOSP `CarrierConfigLoader` override implementation][carrier-loader]
- [Reference project `nrfr-android16-optimized`][nrfr]

[carrier-config]: https://android.googlesource.com/platform/frameworks/base/+/master/telephony/java/android/telephony/CarrierConfigManager.java
[country-key]: https://android.googlesource.com/platform/frameworks/base/+/master/telephony/java/android/telephony/CarrierConfigManager.java
[carrier-loader]: https://android.googlesource.com/platform/packages/services/Telephony/+/master/src/com/android/phone/CarrierConfigLoader.java
[nrfr]: https://github.com/Swimteam0/nrfr-android16-optimized
