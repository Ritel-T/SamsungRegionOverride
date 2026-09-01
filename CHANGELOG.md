# Changelog

## Unreleased

- Refined the Compose screen with a fixed top bar, compact Shizuku status, country-first SIM details,
  full-card disclosure ripples and an always-visible action bar.
- Made target-app stopping an explicit manual action instead of part of apply/restore, and kept operation
  details behind a compact result-card disclosure.
- Added a privacy-safe `SRO-DIAGNOSTIC/1` summary that users can copy, share or attach to a GitHub issue.

## 3.8.0 - 2026-08-23

- Reworked the app around short, reversible disguise sessions with explicit Network and Country layers.
- Hardened restore ordering, pending-write journaling, SIM fingerprint binding and IMS recovery checks.
- Added editable target-app handling, a persistent restore notification and clearer live-state reporting.
- Added per-app language support for 15 languages, including RTL layout support for Arabic.
- Added animated conditional risk messaging and restored the original numeric and micro-label spacing.
- Documented the reproduced Samsung IMS failure mechanism, recovery sequence and compatibility limits.

This is the first packaged GitHub release. Validation currently covers one SM-S938B running Android 16 /
One UI 8.5 and Android 17 / One UI 9 Beta; other devices remain experimental.
