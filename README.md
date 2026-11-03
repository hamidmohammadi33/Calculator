# Infinity Calculator

A Material 3 Android calculator built with Kotlin, Jetpack Compose and `BigDecimal`.

## Features

- Addition, subtraction, multiplication and division
- Powers, square roots, parentheses and percentage
- Arbitrarily large integer inputs/results supported by `BigDecimal`
- Thousands separators can be toggled
- Light, dark and system theme
- Division and square-root calculations use a 1000-digit precision context

> No finite device can display literally infinite digits. Non-terminating decimal operations are rounded using the configured 1000-digit precision.

## Requirements

- Android Studio (recent stable version)
- JDK 17
- Android SDK Platform 36
- Gradle 8.13

## Open and run

1. Download or clone this repository.
2. Open the `InfinityCalculator` folder in Android Studio.
3. Allow Gradle sync and install any requested Android SDK components.
4. Run the `app` configuration on an emulator or Android device.

## Build APK locally

From the project root:

```bash
gradle assembleDebug
```

The APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Android Studio can also build it using **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

## GitHub Actions

Every push to `main` triggers `.github/workflows/android.yml`. When the workflow succeeds, download the APK from the run's **Artifacts** section.

## Notes

- The debug APK is intended for testing and personal installation.
- For public distribution, create a signed release build and keep the signing key private.
- Do not commit `local.properties`, passwords, or signing keys.
