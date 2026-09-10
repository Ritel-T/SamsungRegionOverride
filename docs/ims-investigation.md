# IMS failure investigation

Test device: Samsung SM-S938B, Android 16, One UI 8.5, dual China Unicom subscriptions. All destructive experiments used the non-default subscription and were restored afterwards.

The UICC cycles below are historical controlled experiments, not the current Restore behavior. Restore no longer disables or cycles UICC; it observes IMS read-only for up to 15 seconds after clearing overrides.

## Reproduced sequences

| Sequence | Immediate result | Forced reconnect result |
|---|---|---|
| Country only | IMS stayed registered for more than 70 seconds | Not observed to fail on the reference run |
| Network only | Existing IMS stayed registered | UICC cycle made IMS drop and remain down |
| Country, wait, then Network | Existing IMS stayed registered | UICC cycle made IMS drop and remain down |
| Network, wait, then Country | IMS changed from registered to unregistered within seconds | Stayed down until the real Network identity was restored |
| Both live, `disableIms` + `enableIms` | No useful reset | Existing session could remain, so this was not decisive |
| Both live, UICC applications off/on | IMS dropped | Repeated fake-carrier registrations failed |
| Restore real MCC/MNC and country, then UICC cycle | IMS returned | Stable on repeated samples |

This explains why write order appears to “fix” the problem in casual testing. A successful order can preserve an already-established Chinese IMS session. It does not make the fake Network identity safe for the next registration.

## Root cause

`IccRecords.setCarrierTestOverride()` changes the SIM operator name and numeric globally and notifies the rest of telephony that records were overridden. It is not scoped to Galaxy Store. See the [AOSP IccRecords implementation](https://android.googlesource.com/platform/frameworks/opt/telephony/+/9d7ed998ab93d19ac6ee6eb414e77fddb5d8a85c/src/java/com/android/internal/telephony/uicc/IccRecords.java#298).

The overridden MCC/MNC, IMSI and names are then consumed by carrier matching. [CarrierResolver](https://android.googlesource.com/platform/frameworks/opt/telephony/+/307806916047d68377ff74674f95ca499243e068/src/java/com/android/internal/telephony/CarrierResolver.java) reloads rules and updates the carrier identity from those SIM attributes.

Country uses `CarrierConfigManager.overrideConfig()`. A CarrierConfig update causes Android's data stack to rebuild data profiles. [DataProfileManager](https://android.googlesource.com/platform/frameworks/opt/telephony/+/ee88fa09b5e59a3960ba0c096164c2e803b90c2f/src/java/com/android/internal/telephony/data/DataProfileManager.java) responds to CarrierConfig changes, and [DataNetworkController](https://android.googlesource.com/platform/frameworks/opt/telephony/+/15d844360c99d93c0925c1f4825351f6a5e8202b/src/java/com/android/internal/telephony/data/DataNetworkController.java) can tear down a network whose data profile became invalid.

On the reference device, the sequence was:

1. CarrierConfig reload invalidated the IMS data profile and disconnected the IMS PDN.
2. Samsung IMS still selected the China Unicom VoLTE profile and real China Unicom IMS APN.
3. It read the globally faked `getSimOperator()` value `23430`.
4. It generated the EE home domain `ims.mnc030.mcc234.3gppnetwork.org`.
5. `REGISTER` to that domain over the China Unicom network received `SIP/2.0 403 Forbidden`.
6. Registration entered a permanently-prohibited state until the real identity was restored and UICC was cycled.

Therefore:

- **Network is the latent identity cause.**
- **Country is a common reconnect trigger.**
- An 8-second healthy sample is a current observation, not a safety guarantee.
- Any later IMS/SIM reconnect can expose a live fake Network identity.

## Product consequences

The non-root Shizuku approach cannot return fake MCC/MNC only to Galaxy apps while returning real MCC/MNC to `com.android.phone` or Samsung IMS. That would require caller/process-specific hooking, typically a rooted/custom framework solution.

This project instead treats the feature as a short session and preserves an explicit recovery path:

- apply shows a temporary-session warning;
- Network is never described as call-safe;
- Restore is primary while a disguise is live;
- writes are journaled before Binder mutation;
- Country clear waits for its final reload;
- Restore never disables or cycles UICC, even after the real Network identity is confirmed;
- Restore observes IMS read-only for up to 15 seconds and reports recovery as unconfirmed if IMS remains unregistered or unavailable;
- after restoring the real identity, the user checks mobile service and, if needed, re-enables the SIM in Android Settings;
- version 4.0.0 could leave UICC persistently disabled, and reboot alone does not re-enable it.

The IMS reports in [issue #3](https://github.com/Ritel-T/SamsungRegionOverride/issues/3) and [issue #4](https://github.com/Ritel-T/SamsungRegionOverride/issues/4) concern the global Network identity limitation. App-country-only use can reduce exposure when it meets the target app's needs, but it is not a service-safety guarantee. Removing automatic UICC cycling avoids that recovery-side mutation; it does not make a fake Network identity safe or resolve every IMS registration failure.

## What was ruled out

- The synthetic IMSI was not sufficient to explain the failure; the Samsung domain builder directly consumed fake SIM operator numeric.
- `ITelephony.refreshUiccProfile(subId)` only refreshed carrier privilege evaluation and did not recover IMS.
- `disableIms` followed by `enableIms` did not reliably rebuild registration from a clean identity.
- Waiting for `ACTION_CARRIER_CONFIG_CHANGED` removed one race but did not make a live fake Network safe.

Results outside the reference firmware and carrier remain unverified. Vendor IMS implementations and fallback circuit-switched calling differ.
