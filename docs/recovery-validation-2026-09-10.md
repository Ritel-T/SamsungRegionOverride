# Recovery validation — 2026-09-10

The 4.0.1 recovery changes were tested on an SM-S938B running Android 17 / API 37.
These results cover the debug build and the reference device, not every Samsung firmware or a signed
release upgrade. Raw device logs and screenshots remain local; no SIM identifiers are included here.

## Reproduced failure

In 4.0.0, Country operations repeatedly failed with `Cannot call disconnect() while connecting
UiAutomation` in `Instrumentation.finish()`. A diagnostic build preserved the earlier exception:
`UiAutomationService ... already registered!`. The framework rejected another accessibility automation
client, then its failed connection cleanup hid that rejection behind a process crash.

The new implementation leaves the existing automation client connected. The shell host delegates the
two phone-state permissions to its active instrumentation after checking the per-run token and the
requesting UID's package. The instrumentation confirms those permissions before performing a write.
Normal completion handles the delegation lifecycle without creating a UiAutomation client.

Android 14 and earlier receive an additional preflight check because their framework cleanup can
revoke another instrumentation's delegate. This is a best-effort occupancy check, not an atomic lock
across tools. That older-device branch has unit coverage but has not been exercised on this device.

## Checks

- 43 unit tests passed, including authorization rejection/timeout, legacy delegation occupancy,
  pending-state behavior, read-only IMS observation, and unavailable IMS diagnostics.
- Debug lint: 0 errors, 12 warnings concerning reflected private Android APIs.
- Debug APK, unsigned release APK and Android test APK compiled successfully. The full Android UI test
  suite was compiled, not run; the operation flow below was exercised manually on the device.
- Three Country-only apply/restore cycles on the non-data SIM changed its reported ISO from CN to GB
  and back to CN. The last cycle used the final debug APK, whose installed hash matched the build.
- A synthetic Country pending journal was seeded in the debug app after restoring the SIM. Start was
  disabled as expected. Restore completed through the real privileged path, removed the pending
  marker, and re-enabled Start. The production app's private state was not edited.
- Final system reads showed both SIMs reporting their original country and operator numeric; both
  sampled IMS states were registered. The debug app retained no applied or pending markers. The
  pre-existing accessibility automation service remained present throughout the successful runs.
- An independent shell read after Restore found no delegated shell permissions. Both
  `MODIFY_PHONE_STATE` and `READ_PHONE_STATE` were denied for the debug app again, confirming that the
  temporary delegation did not remain active after the operation.

## Scope of the issue fixes

Issue #2's disable/sleep/enable UICC sequence and its runtime command were removed. Restore now observes
IMS for at most 15 seconds without changing the SIM's enabled state. Tests cover registered,
unregistered, unreadable and interrupted observations. This prevents that code path from leaving the
SIM disabled after process death; it does not automatically repair a SIM disabled by an earlier build.
Such a SIM must be explicitly enabled in Settings or recovered with a verified, targeted enable-only
operation. Reboot alone does not re-enable it.

Issues #3 and #4 concern IMS registration. A global fake Network identity can still cause IMS to use
the wrong carrier identity on reconnect. This change improves recovery and reporting; it does not
claim to make Network disguise call-safe. See [the IMS investigation](ims-investigation.md).
