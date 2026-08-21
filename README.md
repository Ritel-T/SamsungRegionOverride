# Samsung Region Override

Makes a Samsung phone report a different SIM region to the apps that gate content on it — no root, and
without changing which network the radio is actually on.

Galaxy Store, Samsung Members and TikTok all decide what you can see from the region the framework
reports for your SIM. This app rewrites that report through two Android test-override APIs, runs them
with shell privileges over [Shizuku](https://shizuku.rikka.app/), and puts the real values back when
you are done.

It is a framework-level lie, not a network one. Your modem still registers on your real carrier, your
number is unchanged, and nothing here touches roaming, billing, or carrier locks.

---

## What it changes

Two independent layers. Run either alone or both together.

The UI calls them **Network** and **Country**, after the signal each one sets rather than the API each
one writes — the question people arrive with is which switch makes a particular app believe them.
Samsung's own apps read the network; TikTok and most others read the country. The table keeps the
internal names, which is what the code and the result reports use.

| | **SIM identity** (Network) | **App country** (Country) |
|---|---|---|
| API | `ITelephony.setCarrierTestOverride` | `CarrierConfigManager.overrideConfig` |
| Writes | MCC/MNC, a test IMSI, SPN and PNN | `sim_country_iso_override_string`, optionally `carrier_name_override_bool` + `carrier_name_string` |
| Answers | `getSimOperator()`, `getSimOperatorName()` | `getSimCountryIso()` |
| Needs | SIM state `READY` | a valid subId |
| Survives reboot | no | no |
| Costs calls and SMS | no | **yes — see below** |

The SIM identity layer writes only the four fields it needs. ICCID, GID1, GID2, APN and the carrier
privilege rules go in as `null`, so the SIM keeps its real values for all of them.

Either layer alone is enough to move the region, and the tool says so. The country layer writes an ISO
code the platform hands straight back from `getSimCountryIso()`. The network layer writes no country at
all — but the MCC *is* the country under ITU-T E.212, and 234 is the United Kingdom whether or not
anything else on the phone has caught up. So where no ISO override is in force, the screen and the
notification name the disguised region from the MCC rather than pairing a British operator numeric with
the country the SIM really came from.

## What it costs

**Applying the App country layer deregisters IMS on that subscription.** Mobile data is unaffected —
data does not need IMS — but calls and SMS stop. Restoring the identity does not bring IMS back on its
own, which is why restore now cycles the SIM's UICC applications for you.

Measured with `ITelephony.isImsRegistered(subId)` on the affected subscription and on the untouched one
beside it:

```
03:14:19  ims2=true  ims4=true   numeric=46001,46009   healthy
03:14:31  ims2=true  ims4=false  numeric=46001,23430   apply, both layers
03:16:12  ims2=true  ims4=false  numeric=46001,23430   no self-heal after 100 s
03:16:25  ims2=true  ims4=false  numeric=46001,46009   restore returns the real identity
03:17:28  ims2=true  ims4=false  numeric=46001,46009   IMS is still down
```

**The SIM identity layer alone does not do this.** With that override live and nothing else — a foreign
MCC/MNC, a fake IMSI, a foreign SPN — `isImsRegistered` stays `true` and calls keep working. If the
apps you care about read the operator rather than the country, run that layer on its own and pay
nothing.

It is not perfectly deterministic, and the tool does not pretend otherwise. A later round of testing on
the same device found the App country layer applied *on its own* leaving IMS registered for a full 50 s,
and found both layers applied together going both ways across repeated runs of one build. Write order
and a wait for the carrier config reload broadcast were both tried as fixes; neither made an apply
reliably keep voice, and the negative results are recorded in `CarrierConfigInstrumentation` so nobody
retries them blind. What the tool does instead is **watch IMS for 8 s after an apply and say what
happened** — the result headline reads *Applied — calls and SMS stopped* when it did, so the failure is
never something you discover from a missed call.

One thing is firm: **cycling the UICC does not help while the override is still live.** Tested directly
— IMS stayed down for 48 s after a cycle with the country override in place. Restore is the recovery,
because it drops the override first and cycles afterwards.

Three recovery levers, tested against a genuinely deregistered stack:

| Call | Result |
|---|---|
| `ITelephony.refreshUiccProfile(subId)` | no effect — in AOSP it only re-runs carrier-privilege evaluation |
| `ITelephony.disableIms` + `enableIms` | no effect |
| `ISub.setUiccApplicationsEnabled(false → true)` | **IMS back within 15 s** |

The third is what the SIM on/off switch in Settings calls, and restore performs it automatically.
Ordering is load-bearing: cycling while an override is still live re-registers IMS against the *fake*
identity and drops it again, so it runs only after both layers have been put back.

Restore also checks before it cycles. The cycle is itself a few seconds with no service, so spending it
on a subscription whose IMS is already registered would be paying an outage to fix nothing; when the
registration is healthy the cycle is skipped and the report says so. An unreadable registration state
cycles anyway — not knowing is not the same as knowing it is fine.

If it ever fails, the manual equivalent still works — turn the SIM off and on in Settings, or reboot.

## Requirements

- A Samsung device on Android 10 (API 29) or newer. Developed and verified on **SM-S938B, Android 16,
  One UI 8.5**.
- **Shizuku 13+**, running and granted. Wireless debugging is enough; root also works but is not needed.
- A SIM in state `READY`, for the SIM identity layer.

## Build

No release APK is published yet, so build one:

```bash
./gradlew assembleDebug
```

Needs JDK 17 or newer. The wrapper pins Gradle 9.7.0, and AGP 9.3.1 supplies Kotlin 2.2.10. The four
Shizuku AARs are vendored in `app/libs` rather than resolved, so the privileged surface stays pinned to
bytes that have been run on hardware.

## Using it

1. Start Shizuku. The status row at the top must read `Connected · uid 2000 · granted`.
2. Pick a SIM. The panel below the selector puts **REAL** against **DISGUISE** — the SIM's own identity
   on the left, the mask on the right — and a `LIVE` badge sits on whichever of the two is currently in
   force. With nothing applied the right side is a preview of what Apply would do; once a layer lands
   the highlight slides across and the right side becomes what apps are actually being told.
3. Pick a target region — search the preset list, or type the MCC/MNC, country ISO and carrier name by
   hand. A preset only fills those three fields; nothing consults it afterwards.
4. Choose your layers. Both are on by default. If only TikTok-style apps matter to you, turn **Network**
   off; if only Samsung's apps matter, turn **Country** off and the apply costs nothing.
5. **Apply**, and confirm. The target apps are force-stopped for you, since they latch their region at
   startup. The apply then watches IMS for 8 s and tells you whether calls still work.
6. If an app still shows the old region, use the target apps panel to wipe its data and relaunch it.
7. **Restore** when you are finished. Calls and SMS come back on their own.

The overflow menu also holds *Clear all CarrierConfig overrides*, which removes every transient **and
persistent** test value on that subId — including ones written by other tools, which it cannot rebuild.

## While an override is live

An override is invisible from outside this app. Nothing in the system UI says the phone is reporting a
foreign network, and the consequence that matters — calls and SMS stopping — is indistinguishable from
poor coverage. So while any layer is live there is an ongoing notification per disguised SIM:

```
Samsung Region Override · Country + Network
Pretending to be 🇬🇧 GB · EE
SIM 1 is really 🇨🇳 CN · China Unicom
                                                  [ Restore ]
```

**Restore** on it runs the same restore as the button in the app, with no confirmation — restore only
ever takes state away, and the person pressing it has just noticed their phone is disguised. It opens
the screen with the operation already running, rather than working headlessly: restore needs Shizuku,
whose binder and permission prompt live behind an activity, and it is an operation that can fail.

On Android 16 the notification asks to be a **Live Update**, which is what puts the disguised country's
flag in the status bar chip — a one-glyph answer to "what is my phone claiming right now" without
opening anything. On this device One UI files it under *Live notifications* in the shade and mirrors
it into the Now Bar. The request is not a guarantee: the platform decides, it can be turned off per
app, and older releases have no such concept, so everything above still works as an ordinary ongoing
notification.

The notice tracks the SIM scan rather than the operations, so it is right whatever put the phone in this
state — an apply here, a restore from the notification itself, or a reboot that dropped every override
while the app was closed.

## How it works

Every privileged write goes through a single shell-UID service.

1. The Compose UI runs in a `:ui` process and binds `InstrumentationHostService`, whose only job is to
   keep the app's **default** process alive.
2. It waits for Shizuku, requests permission, and binds `CarrierOverrideUserService` — an AIDL
   UserService that Shizuku starts under uid 2000, the identity `adb shell` has, which carries
   `MODIFY_PHONE_STATE`.
3. The **SIM identity** layer goes straight from there to the hidden interface by reflection:
   `ServiceManager.getService("phone")` → `ITelephony$Stub.asInterface` → `setCarrierTestOverride`.
4. The **App country** layer cannot. Samsung rejects `overrideConfig` from the shell UID on recent
   firmware, and `cmd phone cc` is permission-denied for shell too. So the shell service calls
   `IActivityManager.startInstrumentation` on the app's own `CarrierConfigInstrumentation` with
   `INSTR_FLAG_NO_RESTART`, and that instrumentation calls
   `UiAutomation.adoptShellPermissionIdentity(MODIFY_PHONE_STATE, READ_PHONE_STATE)`. The result holds
   shell's permissions under the app's package identity — the combination Samsung's implementation
   accepts.

Step 1 exists because of step 4: AMS attaches a `NO_RESTART` instrumentation to the default process,
and the flow is unreliable if that process is not already running. The UI sits in `:ui` so an
instrumentation crash cannot take the interface down with it, and `androidx.startup`'s initializers are
pinned to `:ui` for the same reason.

**Restore.** AOSP has no clear API for `setCarrierTestOverride`, so restore writes the saved real values
back rather than removing anything. The originals come from a per-subscription snapshot in
SharedPreferences, captured before the first override and never overwritten while one is live. Clearing
the country layer first re-applies an override containing *only* the real ISO and waits for
`getSimCountryIso()` to report it, because Samsung caches that value and dropping the override without
warming the cache leaves the stale one in place. A reboot remains the definitive undo.

## Target apps

Changing what telephony reports is half the job — these apps read their region once, at startup, and
hold it.

| Package | App |
|---|---|
| `com.sec.android.app.samsungapps` | Galaxy Store |
| `com.samsung.android.voc` | Samsung Members |
| `com.zhiliaoapp.musically` | TikTok |

They are force-stopped after every successful apply and restore. The panel adds two options for when
that is not enough: a wipe (`Keep` / `Cache` / `Data`) and a relaunch. `Data` signs you out of the app,
but it is usually the only thing that forces a real re-detect, because the cached region tends to live
in an app's data rather than its cache.

`Cache` is kept because it is the correct API, but it is a **documented no-op on One UI 8.5** — the
platform accepts `pm clear --cache-only` and then never calls back the observer the command waits on.
It is timeout-bounded and reports `timeout` rather than claiming a wipe that did not happen.

## Diagnostics without the UI

`RuntimeProbe` is a shell entry point into the same bridges, run under `app_process` as the shell user —
the same identity, and the same `MODIFY_PHONE_STATE`, that the Shizuku service has. This is how the IMS
regression above was isolated.

```bash
adb shell CLASSPATH=$(adb shell pm path com.riteldevelopment.carriertestoverride.debug | cut -d: -f2 | tr -d '\r') app_process / com.riteldevelopment.carriertestoverride.RuntimeProbe sim-probe
```

That is the debug package; drop the `.debug` for a release build. It answers with the uid it is running
as, the proxy it resolved, and the override signature this firmware exposes:

```
uid=2000
proxy=com.android.internal.telephony.ITelephony$Stub$Proxy
methods=refreshUiccProfile(int) | setCarrierTestOverride(int,String,String,String,String,String,String,String,String,String)
```

| Command | What it does |
|---|---|
| `sim-probe` | uid, the `ITelephony` proxy, and the override signatures this build exposes |
| `methods SUBSTRING` | matching `ITelephony` methods |
| `sub-methods SUBSTRING` | matching `ISub` methods |
| `sim-set SUB MCCMNC IMSI NAME` | raw `setCarrierTestOverride`, unvalidated — lets you move one field at a time |
| `sim-restore SUB MCCMNC NAME` | write identity values back |
| `ims-state SUB` | `isImsRegistered(subId)` — the observable that matters |
| `ims-restart SLOT` | `disableIms` + `enableIms`, by slot index |
| `uicc-cycle SUB` | `setUiccApplicationsEnabled` off, settle, on |
| `uicc-refresh SUB` | `refreshUiccProfile(subId)` |
| `country-apply SUB ISO NAME` | the CarrierConfig layer — needs the instrumentation host, so not usable from a bare shell |
| `country-clear SUB [ISO]` | clear it |

`-` means `null`, which is a meaningful value for every string field.

**One caution, because it cost real time here.** Do not measure this with `IsVoiceCallAvailable` or the
CS `availableServices` list from `dumpsys telephony.registry`. Those report *modem* CS registration,
which a framework override cannot touch — they read `true` and `[VOICE,SMS,VIDEO]` straight through a
break that has genuinely killed calling. `isImsRegistered` is the observable that moves.

## Known limits

- **The display name is restored from a snapshot, or not at all.** The App country layer's name override
  lands in the subscription database (`displayName`, `displayNameSource=CARRIER`), not only in the
  transient config, so it outlives both restore and reboot on its own. Restore puts it back from a
  write-once capture taken before the first override. A SIM whose name was already overridden when this
  app first saw it has no capture to work from, and is left alone rather than guessed at.
- **No layer survives a reboot.** That is the undo story and also the limit: a phone that reboots loses
  its override.
- **The ongoing notice needs the notification permission**, and it is asked for at the moment a SIM
  first goes live rather than on first launch. Refused, everything still works — the reminder and its
  Restore shortcut are simply not there, and one line lands in logcat under `OverrideNotifier` saying
  why. Force-stopping the app also clears the notice while leaving the override in place.
- **Preset codes are one widely reported MNC per carrier.** Large networks own many, especially Indian
  circles and US regional codes. That is enough for checks that read the MCC and the country, which is
  nearly all of them. The catalog holds 204 entries across 74 countries, of which exactly one —
  `gb / EE / 23430` — has been applied and restored end to end on hardware.
- **Samsung-specific by design.** The instrumentation detour exists because Samsung rejects shell-UID
  CarrierConfig writes. On a device that does not, it is unnecessary overhead — untested there.
- **Two SIM slots.** The selector is laid out for two, which covers every consumer handset.

## Project layout

```
app/src/main/java/…/
  TelephonyBridge.java              ITelephony / ISub over reflection — the SIM identity layer
  CarrierConfigBridge.java          starts the instrumentation and waits on its result
  CarrierConfigInstrumentation.java runs in the app process with shell permissions adopted
  CarrierOverrideUserService.java   the AIDL service Shizuku hosts at uid 2000
  TargetApps.java                   stop / wipe / relaunch, every step bounded and reported verbatim
  RuntimeProbe.java                 shell entry point
  data/                             Shizuku, SIMs, snapshots, the live notice, and orchestration
  ui/                               Compose screen, view model, and the region catalog
```

## Verified on

SM-S938B · Android 16 · One UI 8.5 · Shizuku 13.1.5 · dual SIM.

Everything described here as measured was measured there. Behaviour on other Samsung firmware is likely
but unproven, and behaviour on non-Samsung devices is neither.

## License

[MIT](LICENSE).

The Shizuku libraries vendored in `app/libs` are third party and stay under their own license
(Apache-2.0), which the MIT grant here does not extend to.
