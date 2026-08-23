# Release procedure

Release signing is intentionally configured only through environment variables. Never commit a
keystore, password, generated APK or local signing script.

Required variables:

```text
SRO_RELEASE_STORE_FILE
SRO_RELEASE_STORE_PASSWORD
SRO_RELEASE_KEY_ALIAS
SRO_RELEASE_KEY_PASSWORD
```

From a clean tag candidate, run:

```bash
./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

Before publishing:

1. Confirm `versionName` matches the proposed `vX.Y.Z` tag and `versionCode` increased.
2. Verify the release APK with the newest installed `apksigner` and record its certificate SHA-256.
3. Install that exact signed APK on the reference device and cold-launch it.
4. Confirm the real SIM numeric/country and IMS registration are unchanged by installation.
5. Publish the renamed APK plus its SHA-256 file from the exact tagged commit.
6. Read the GitHub Release back and compare its tag, target commit, assets, sizes and digests.

The release key is the permanent Android update identity. Keep an encrypted offline backup; losing it
means existing release installations cannot be upgraded under the same package name.
