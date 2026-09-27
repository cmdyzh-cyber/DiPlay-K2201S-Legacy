# Building DiPlay

Requirements: JDK 25, Android SDK 37, NDK 28.2.13676358 and the included Gradle wrapper.

The APK declares `minSdk = 24` (Android 7.0). `mobile`, `common` and `shared` all use that floor;
`automotive` stays at 29 because `androidx.car.app:app-automotive` requires it.

## Android 7 (API 24/25) notes

Android 7 and 7.1 are supported by degrading the features whose platform APIs do not exist there:

- `LocalOnlyHotspot` starts at API 26, so wireless falls back to a legacy Wi-Fi Direct group.
- `WifiP2pConfig.Builder` (API 29), `WifiP2pManager.createGroup(Channel, WifiP2pConfig, ActionListener)`
  (API 29), `WifiP2pGroup.getFrequency()` (API 29) and `WifiP2pManager.Channel.close()` (API 27) are
  all unavailable. The legacy `createGroup(Channel, ActionListener)` overload is used instead, the
  platform chooses the group SSID and passphrase, and the operating channel is reported to the
  iPhone as 0 (unknown), which makes the phone discover the AP by scanning. Because the channel is
  never known, no working frequency is remembered for the next session.
- `NotificationChannel`, the two-argument `Notification.Builder(Context, String)` and
  `startForegroundService` are API 26; Android 7 posts the session notification on the channel-less
  path and starts the service with `startService`.
- `java.time` and `java.util.Base64` are API 26 APIs used by the pairing and location code. Core
  library desugaring (`com.android.tools:desugar_jdk_libs`) supplies them on API 24/25.
- `WifiP2pGroupManager` still needs the device to act as Wi-Fi Direct group owner; the wired USB
  path is unaffected.

## Source and CI builds

```sh
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :mobile:lintDebug :mobile:assembleDebug
```

The resulting source-only APK contains no accessory identity. Standalone CarPlay requires runtime authentication provisioning. Tests generate synthetic identities at runtime; no test private-key files are tracked.

## Local release packaging

Provide an external asset directory using `DIPLAY_AUTH_ASSETS_DIR`. The directory must contain exactly the intended runtime files under `offline-mfi/identity.pk8` and `offline-mfi/certificate.p7b`. Neither file belongs in Git. The build permits those two files only when this explicit input is set and rejects unexpected credential containers elsewhere in APK assets.

Set `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` locally for your Android signing key. Never commit these values or the keystore. Different signing keys cannot update an existing project-signed installation.

```sh
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :mobile:lintRelease :mobile:assembleRelease
```

Output: `mobile/build/outputs/apk/release/mobile-release.apk`. The release APK deliberately contains the experimental identity described in the notices; it is extractable by recipients. The separate Android signing key is not included. The retired build-beta.py helper is not used; this Gradle workflow uses explicit environment inputs.

The public release source archive corresponds to the tagged source and excludes runtime identities, signing keys, local configuration and build output.
