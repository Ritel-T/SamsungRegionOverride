# Diagnostics

When a user reports a failure, the safest useful payload is the app-generated diagnostic text, not a
device dump. Open the result card, expand **Diagnostic**, copy or share the text beginning with
`SRO-DIAGNOSTIC/1`, and paste it into the issue form's optional diagnostic field.

The app-generated report contains only an allow-listed summary: app version, manufacturer/model, Android
API level, operation, selected layer categories, SIM slot number, target country ISO, target-app count,
result tone, sampled IMS state, Shizuku state category, operation stage, failure category, runtime
capability availability and (when useful) the exception class name. It never includes a subscription ID,
ICCID, IMSI, IMEI, EID, phone number, SIM fingerprint, ADB serial, full build fingerprint, package list,
raw exception message, logcat, dumpsys or bugreport.

## Report Flow

1. Restore the real SIM identity before collecting a report. If calls or messages are affected, toggle the
   SIM in Settings or reboot after Restore.
2. Expand the result card and review the local operation details on the device. These details may contain
   raw framework output and are for local diagnosis only.
3. Copy or share the generated diagnostic text. Do not paste the local detail block or a full logcat.
4. Use **Report issue** to open the GitHub bug form, then paste the reviewed diagnostic text into the
   optional field and describe the reproduction steps, selected layers and restore result.

The app does not read or upload global logcat automatically. Android applications cannot reliably obtain a
privacy-safe system log, and Shizuku shell access would expose other apps and communications metadata.
For a case that genuinely needs system logs, a maintainer should request a narrowly filtered, user-reviewed
capture separately; it is never the default report path.

## Maintainer Triage

Use the diagnostic category to choose the next check:

- `VALIDATION` or `SHIZUKU_*`: reproduce the setup and permission state.
- `SIM_LAYER` or `COUNTRY_LAYER`: compare the selected layer with the local operation detail.
- `IMS` or `UNCONFIRMED`: ask for the before/after/restore IMS observations, not raw identifiers.
- `OPERATION`: inspect the app version, stage and runtime availability first, then request a focused
  reproduction on the affected firmware.
