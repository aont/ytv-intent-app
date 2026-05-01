# YouTube + Prime Video Intent App

Android TV-friendly app that lets you paste a **YouTube or Prime Video URL** and open it directly in the corresponding app via an intent.

## How it works

When you enter a supported URL and press **Open**, the app:

1. Normalizes YouTube URLs (supports `https://youtu.be/<id>`, `https://www.youtube.com/watch?v=<id>`, `https://www.youtube.com/shorts/<id>`, or a raw video ID).
2. Opens Prime Video URLs (for supported Amazon/Prime Video links) in the Prime Video app.
3. Sends an `ACTION_VIEW` intent and prefers installed native apps before falling back to a browser when needed.

As long as the YouTube or Prime Video app is installed, the intent will open directly in that app.

## Usage

1. Launch the app.
2. Paste a YouTube or Prime Video URL into the input field.
3. Select **Open** (or press the Go action on the keyboard).
4. The content opens in the matching app (or a browser fallback if needed).

## Examples

Valid inputs include:

### YouTube

- `https://www.youtube.com/watch?v=dQw4w9WgXcQ`
- `https://youtu.be/dQw4w9WgXcQ`
- `https://www.youtube.com/shorts/dQw4w9WgXcQ`
- `dQw4w9WgXcQ` (video ID only)

### Prime Video

- `https://watch.amazon.co.jp/detail?gti=...`
- `https://www.amazon.co.jp/gp/video/detail/...`

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
