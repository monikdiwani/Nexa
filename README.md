# Nexa

Android project for the Nexa app.

## Requirements

- Android Studio (latest stable recommended)
- JDK 17
- Android SDK with appropriate build tools

## Setup

1. Clone the repository.
2. Open the project in Android Studio.
3. Sync Gradle files.
4. Add your local `app/google-services.json` file (not committed to git).
5. Build and run:
   - Debug APK: `./gradlew assembleDebug` (Linux/macOS)
   - Debug APK: `gradlew.bat assembleDebug` (Windows)

## GitHub Notes

This repository ignores local and generated files such as:

- `.idea/`
- `local.properties`
- `build/`
- `app/build/`
- `app/google-services.json`

If Firebase is required, each developer should use their own local `google-services.json`.
