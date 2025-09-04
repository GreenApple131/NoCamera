# NoCamera

**NoCamera** is a lightweight Android app that captures pure RAW (DNG) images using the Camera2 API, with no added processing, filters, or AI. It’s designed for photography enthusiasts who prefer full control over their image editing.

## Overview

This project demonstrates a basic Camera2 API implementation in Kotlin:
- Display camera preview on an `AspectRatioTextureView`.
- Capture and save images in RAW DNG format.
- Exclude custom orientation logic to prevent save failures.

## Features

- RAW (DNG) image capture.
- Kotlin-based Camera2 API implementation.
- Simple camera preview interface.
- Stable saving without internal orientation processing.

## Prerequisites

- Android Studio 4.0 or higher.
- Android SDK with API Level 23 (Android 6.0) or above.
- Device or emulator with Camera2 API support and RAW capture capability.

## Installation and Running

1. Clone the repository:
   ```bash
   git clone https://github.com/GreenApple131/NoCamera.git
   ```
2. Open the project in Android Studio.
3. Ensure all dependencies and SDK components are installed.
4. Connect an Android device or start an emulator.
5. Click **Run** in Android Studio.

## Permissions

The app requires the following permissions in `AndroidManifest.xml`:

- `android.permission.CAMERA` — access the camera.
- `android.permission.WRITE_EXTERNAL_STORAGE` — write image files to storage.
- `android.permission.READ_EXTERNAL_STORAGE` — read saved images.

**Note:** On Android 8.0+ you must request permissions at runtime.

## Usage

After launching the app, the camera preview will appear. Tap the **Capture** button (or the designated UI element in `activity_main.xml`) to take a photo. The RAW DNG image will be saved to the device gallery.

## Project Structure

```
NoCamera/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/nocamera/
│   │   │   └── MainActivity.kt      # Main Activity
│   │   └── res/layout/
│   │       └── activity_main.xml    # Layout file
│   └── build.gradle.kts             # App module Gradle config
├── build.gradle.kts                 # Project Gradle config
├── settings.gradle.kts
└── gradle/
```

## Technologies and Libraries

- Kotlin
- Android Camera2 API
- `DngCreator` for RAW image saving
