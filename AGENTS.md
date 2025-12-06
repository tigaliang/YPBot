# YPBot Architecture

This document describes the architecture for the YPBot project.

## Overview

The YPBot system consists of two primary parts:
- Android App
- Arduino Firmware

## Android App

**Location**: `YPBot-app/`

The Android App is the user interface for the YPBot system. It is built with Jetpack Compose and uses the Bluetooth API to communicate with the Arduino Firmware.

### Project Structure

```
YPBot-app/
├── app/                          # Main application module
│   └── src/
│       ├── main/                 # Main source code
│       │   ├── java/me/ypphy/ypbot/
│       │   │   ├── bluetooth/    # Bluetooth communication module
│       │   │   ├── data/         # Data layer
│       │   │   │   └── model/   # Data models
│       │   │   └── ui/          # UI layer
│       │   │       ├── screen/  # Screen composables
│       │   │       ├── components/  # Reusable UI components
│       │   │       ├── viewmodel/   # ViewModel for state management
│       │   │       ├── state/       # UI state definitions
│       │   │       └── theme/       # Material Design 3 theme
│       │   └── res/              # Android resources
│       │       ├── drawable/     # Drawable resources
│       │       ├── layout/       # XML layouts (legacy)
│       │       ├── mipmap-*/     # App icons
│       │       ├── values/        # String, color, theme resources
│       │       └── xml/          # XML configuration files
│       ├── androidTest/          # Android instrumentation tests
│       └── test/                 # Unit tests
├── build.gradle.kts              # Project build configuration
├── settings.gradle.kts           # Gradle settings
└── gradle.properties             # Gradle properties
```

### Debugging

Everytime you make changes to the Android App, you should run the following commands to build and install the app on your device under the `YPBot-app/` directory:

```
./gradlew build && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Arduino Firmware

**Location**: `YPBot-miniAuto/app_control/`

The Arduino Firmware is the firmware for the YPBot system. It is built with Arduino IDE and uses the Bluetooth API to communicate with the Android App.

## Documentation Convention

- **Do not generate extra markdown files** unless explicitly requested
- AGENTS.md serves as the single source of truth for technical/architectural documentation
- README.md serves as the single source of truth for the project features and usage instructions
