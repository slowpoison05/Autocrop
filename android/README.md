# AutoCrop Scanner for Android

A native Android companion to the AutoCrop Windows app.

## Features
- Google ML Kit Document Scanner flow for automatic document edge detection, capture, crop and perspective correction.
- Multi-page scanning and optional gallery import.
- Local post-processing presets: Auto clean, original colour, grayscale and black & white.
- Exports enhanced JPEG pages to Pictures/AutoCrop and a combined PDF to Documents/AutoCrop.
- Documents remain on the device; this app does not upload scans to a server.

## Build
Open the `android` folder in Android Studio (Giraffe or newer) and sync Gradle, then run the app on an Android device with Google Play services.

Or build from the repository root with JDK 17 and Gradle 8.9:

```bash
gradle -p android assembleDebug
```

The debug APK is generated at `android/app/build/outputs/apk/debug/app-debug.apk`.

The first scan may require Google Play services to download the document-scanner module. The scanner UI handles page-edge detection, auto capture, cropping and perspective correction. The app's local enhancement presets then process the returned page images.
