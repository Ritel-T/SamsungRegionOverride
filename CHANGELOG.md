# Changelog

## 3.8.0 - 2026-08-23

- Reworked the app around short, reversible disguise sessions with explicit Network and Country layers.
- Hardened restore ordering, pending-write journaling, SIM fingerprint binding and IMS recovery checks.
- Added editable target-app handling, a persistent restore notification and clearer live-state reporting.
- Added per-app language support for 15 languages, including RTL layout support for Arabic.
- Added animated conditional risk messaging and restored the original numeric and micro-label spacing.
- Documented the reproduced Samsung IMS failure mechanism, recovery sequence and compatibility limits.

This is the first packaged GitHub release. End-to-end validation currently covers one SM-S938B running
Android 16 / One UI 8.5 with Shizuku 13.1.5; other devices remain experimental.
