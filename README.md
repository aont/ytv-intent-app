# YouTube Intent App

Android TV-friendly app that lets you paste a YouTube URL and open it directly in the YouTube app via an intent.

## How it works

When you enter a YouTube URL and press **Open**, the app:

1. Normalizes the URL (supports `https://youtu.be/<id>`, `https://www.youtube.com/watch?v=<id>`, `https://www.youtube.com/shorts/<id>`, or a raw video ID).  
2. Sends an `ACTION_VIEW` intent and prefers:
   1. **YouTube for Android TV** (`com.google.android.youtube.tv`)
   2. **YouTube mobile app** (`com.google.android.youtube`)
   3. **Browser** (fallback if no YouTube app is available)

As long as the YouTube app is installed, the intent will open the video directly in YouTube.

## Usage

1. Launch the app.
2. Paste a YouTube URL into the input field.
3. Select **Open** (or press the Go action on the keyboard).
4. The video opens in the YouTube app (or a browser fallback if needed).

## Examples

Valid inputs include:

- `https://www.youtube.com/watch?v=dQw4w9WgXcQ`
- `https://youtu.be/dQw4w9WgXcQ`
- `https://www.youtube.com/shorts/dQw4w9WgXcQ`
- `dQw4w9WgXcQ` (video ID only)

## GitHub Actions build

This repo includes `.github/workflows/android.yml` with two jobs:

- `build`: always builds `assembleDebug` and uploads `app-debug.apk`
- `signed-release`: builds `assembleRelease` only when all signing secrets are set

### Required secrets for signed release

Set these repository secrets in **GitHub → Settings → Secrets and variables → Actions**:

- `ANDROID_KEYSTORE_BASE64`: Base64-encoded JKS/keystore file
- `ANDROID_KEYSTORE_PASSWORD`: keystore password
- `ANDROID_KEY_ALIAS`: key alias in the keystore
- `ANDROID_KEY_PASSWORD`: key password

Example to generate Base64 locally:

```bash
base64 -i release.keystore | tr -d '\n'
```

The workflow decodes the keystore at runtime and passes signing values to Gradle via `-Psigning.*` properties.
