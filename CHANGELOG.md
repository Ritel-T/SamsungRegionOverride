# Changelog

## 4.0.1 - 2026-09-10

- Removed automatic UICC disable/enable during Restore. Losing the privileged process can no longer
  strand the SIM in a persistently disabled state through this recovery path (#2).
- Country operations now request scoped shell phone-state permissions without connecting a
  UiAutomation accessibility client, avoiding the connection/teardown failure seen on Android 17.
- Preserve the permission or operation failure instead of replacing it with a teardown crash.
  Successful Restore clears the pending journal so another disguise can start normally.
- Name the failed Country or Network layer in the result, and report unknown IMS observations as
  unknown instead of registered. IMS failures caused by a globally faked Network identity remain a
  compatibility limit; Restore observes registration without automatically switching the SIM off.

## 4.0.0 - 2026-09-03

- Redesigned the Compose interface with Material 3 Expressive, adaptive wide-screen layouts, compact
  Shizuku status, country-first SIM details, full-card disclosure ripples and an always-visible action bar.
- Made target-app stopping an explicit manual action instead of part of apply/restore, and kept operation
  details behind a compact result-card disclosure.
- Added a privacy-safe `SRO-DIAGNOSTIC/1` summary that users can copy, share or attach to a GitHub issue.
- Added expressive press feedback across grouped and standalone actions, including a complete quick-tap
  expand-and-return cycle before the action runs, and animated Copy into a confirmation check.
- Restored the most recently applied preset when the app starts again.
- Started valid disguises directly instead of asking for a second confirmation.
- Stabilized Country apply and restore on Android 17 by letting the platform `am` command own the
  instrumentation watcher and by waiting for One UI 9's UiAutomation connection before finishing.
- Changed the Android application id to `com.ritelt.regionoverride`. Version 4 installs beside version
  3 rather than updating it in place, so any version 3 disguise must be restored before migration.
- Rewrote the user guide around the everyday workflow and replaced the legacy interface media.

## 3.8.0 - 2026-08-23

- Reworked the app around short, reversible disguise sessions with explicit Network and Country layers.
- Hardened restore ordering, pending-write journaling, SIM fingerprint binding and IMS recovery checks.
- Added editable target-app handling, a persistent restore notification and clearer live-state reporting.
- Added per-app language support for 15 languages, including RTL layout support for Arabic.
- Added animated conditional risk messaging and restored the original numeric and micro-label spacing.
- Documented the reproduced Samsung IMS failure mechanism, recovery sequence and compatibility limits.

This is the first packaged GitHub release. Validation currently covers one SM-S938B running Android 16 /
One UI 8.5 and Android 17 / One UI 9 Beta; other devices remain experimental.
