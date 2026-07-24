# Sprocket Slayer

Sprocket Slayer is an Android app for printing photos to an HP Sprocket 200 printer.

The basic workflow is:

1. choose an already-paired Bluetooth printer
2. pick an image
3. crop/rotate it to the printer's aspect ratio
4. preview the print crop
5. send it to the printer over Bluetooth

## Why?

The official HP Sprocket app is much larger than the core task of printing a local image, and its Bluetooth connection logic is tortured and broken for some phones. This project explores a smaller, local-first alternative that talks directly to the printer's Bluetooth protocol.

The app currently uses the HPLPP protocol over Bluetooth Classic RFCOMM/SPP. For the HP Sprocket 200, printing works by sending:

- `CONN_SETUP_REQ`
- `RD_STATUS_REQ`
- `PRINT_START_REQ`
- repeated `FILE_WRITE_REQ` chunks containing JPEG data

## Building from source

Requirements:

- Android Studio
- JDK/Android toolchain managed by Android Studio
- Android phone with USB or wireless debugging enabled

Open the project in Android Studio from a clone of this repo:

```bash
git clone git@github.com:mmirgttam/sprocketslayer.git
cd sprocketslayer
```

Build a debug APK:

```bash
./gradlew assembleDebug
```

The APK will be produced at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Installing / sideloading

### Option 1: Android Studio

1. Connect your Android phone.
2. Enable Developer Options and USB debugging.
3. Click **Run** in Android Studio.

### Option 2: adb

Build and install:

```bash
./gradlew installDebug
```

Or install the generated APK manually:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

If an older debug build is already installed and Android refuses the install, uninstall first:

```bash
adb uninstall com.example.sprocketslayer
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Using the app

1. Pair your printer in Android system settings:

   ```text
   Settings → Bluetooth → Pair new device
   ```

2. Open Sprocket Slayer.
3. Tap the paired printer on the first screen.
4. Wait for the connection/status check to complete.
5. Pick an image.
6. Crop/zoom/rotate it.
7. Confirm the preview.
8. Tap **Print**.

## Permissions

The app uses Bluetooth permissions to list paired devices and connect to the printer:

- `BLUETOOTH_CONNECT`
- `BLUETOOTH_SCAN`
- legacy Bluetooth permissions for older Android versions
