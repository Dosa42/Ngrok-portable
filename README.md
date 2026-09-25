# Ngrok Portable for Android

Native Android app with an embedded HTTP server, ngrok tunnel controls and traffic logs.

## Release build

GitHub Actions → **Build Android APK** → **Run workflow** builds this repository by default with the `release` variant. The workflow is manual only. The harness comes from the same repository and workflow commit; it does not build unrelated repositories.

Configure these repository secrets for CI release signing:

- `ANDROID_RELEASE_KEYSTORE_BASE64`
- `ANDROID_RELEASE_STORE_PASSWORD`
- `ANDROID_RELEASE_KEY_PASSWORD`
- `ANDROID_RELEASE_KEY_ALIAS`

Keep the same signing key for future updates. Never commit the keystore or its passwords. The release generated locally on 2026-09-25 uses a dedicated key; its private backup is delivered separately and must be supplied to these secrets for matching CI updates.

For a local build, install JDK 17, Android SDK platform 36.1 and Build Tools 36.0.0, set `ANDROID_HOME`, and provide `ANDROID_RELEASE_KEYSTORE`, `ANDROID_RELEASE_STORE_PASSWORD`, `ANDROID_RELEASE_KEY_PASSWORD` and `ANDROID_RELEASE_KEY_ALIAS`. Run:

```sh
./gradlew :app:assembleRelease
```

The APK is produced under `app/build/outputs/apk/release/`.

## Native tunnel

The pinned ngrok Java SDK 1.0.0 and its Android JNI binaries are packaged for `arm64-v8a` and `armeabi-v7a`. The Android loader uses the APK-installed JNI library. The displayed public URL comes from the real ngrok forwarder; connection failures remain errors.

Enter your ngrok authtoken in the app before connecting. A build and APK signature verification do not establish that a tunnel has been tested on a physical phone.
