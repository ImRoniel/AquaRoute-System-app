<div align="center">

  <h1>⚓ AquaRoute System</h1>
  <p><strong>Next-Generation Real-Time Water Transportation Tracking & Dynamic ETA Platform</strong></p>

  <p>
    <a href="https://github.com/ImRoniel/AquaRoute-System-app/stargazers"><img src="https://img.shields.io/github/stars/ImRoniel/AquaRoute-System-app?style=for-the-badge&logo=github&color=00B4D8" alt="Stars"></a>
    <a href="https://github.com/ImRoniel/AquaRoute-System-app/network/members"><img src="https://img.shields.io/github/forks/ImRoniel/AquaRoute-System-app?style=for-the-badge&logo=github&color=0077B6" alt="Forks"></a>
    <a href="https://github.com/ImRoniel/AquaRoute-System-app/issues"><img src="https://img.shields.io/github/issues/ImRoniel/AquaRoute-System-app?style=for-the-badge&logo=github&color=90E0EF" alt="Issues"></a>
    <a href="https://github.com/ImRoniel/AquaRoute-System-app/blob/main/LICENSE"><img src="https://img.shields.io/github/license/ImRoniel/AquaRoute-System-app?style=for-the-badge&logo=open-source-initiative&color=7209B7" alt="License"></a>
  </p>

  <p>
    <img src="https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android">
    <img src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin">
    <img src="https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=android&logoColor=white" alt="Jetpack Compose">
    <img src="https://img.shields.io/badge/Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white" alt="Gradle">
    <img src="https://img.shields.io/badge/Material%203-757575?style=for-the-badge&logo=materialdesign&logoColor=white" alt="Material 3">
  </p>

</div>

---

## 📖 Overview

**AquaRoute** is an open-source, real-time ferry tracking and ETA system designed to modernize maritime passenger transport. By synthesizing vessel telemetry, dynamic arrival estimation algorithms, status monitoring, and real-time weather overlays, AquaRoute presents commuters and harbor operators with a centralized, data-driven decision platform.

Navigating water transportation often suffers from unpredictable delays, sparse tracking information, and adverse weather conditions. AquaRoute bridges this gap with an intuitive, map-centric mobile interface built natively for Android using state-of-the-art UI and reactive state architectures.

> [!NOTE]
> **AquaRoute** is engineered targeting **Android 14 (API Level 34)** with backward compatibility extending down to **Android 5.0 (API Level 21)**, powered by **Jetpack Compose** and **Kotlin Coroutines**.

---

## ✨ Key Features

<table>
  <tr>
    <td width="50%">
      <h3>📍 Real-Time Vessel Telemetry</h3>
      <p>Continuous geospatial mapping of ferry locations, trajectories, heading directions, and live transit speeds across active water corridors.</p>
    </td>
    <td width="50%">
      <h3>⏱️ Dynamic ETA Calculation Engine</h3>
      <p>Adaptive arrival estimation accounting for live vessel velocity, marine traffic, dock congestion, and historical route performance.</p>
    </td>
  </tr>
  <tr>
    <td width="50%">
      <h3>🌤️ Live Weather & Marine Overlays</h3>
      <p>Integrated meteorological and water condition overlays detailing wind velocity, wave height, tide levels, and visibility alerts.</p>
    </td>
    <td width="50%">
      <h3>📢 Instant Status & Delay Alerts</h3>
      <p>Real-time notifications regarding vessel maintenance, terminal delays, route changes, emergency cancellations, and boarding alerts.</p>
    </td>
  </tr>
  <tr>
    <td width="50%">
      <h3>🎨 Modern Jetpack Compose UI</h3>
      <p>Designed with Material 3 principles featuring fluid micro-animations, customizable dark/light themes, and responsive layout scaling.</p>
    </td>
    <td width="50%">
      <h3>📡 Low-Bandwidth & Offline Resilience</h3>
      <p>Smart local caching strategies that keep terminal timetables, harbor maps, and critical route info accessible under poor connectivity.</p>
    </td>
  </tr>
</table>

---

## 🛠️ Tech Stack

### 📱 Mobile Application Core
* **Language:** [Kotlin 1.9.0](https://kotlinlang.org/)
* **Target SDK:** Android 14 (API Level 34)
* **Min SDK:** Android 5.0 (API Level 21)
* **UI Framework:** [Jetpack Compose](https://developer.android.com/jetpack/compose) (BOM `2023.03.00`)
* **Design System:** Material Design 3 (`androidx.compose.material3`)

### ⚡ Architecture & Reactive Runtime
* **Asynchronous Engine:** Kotlin Coroutines & Flow
* **Core KTX:** `androidx.core:core-ktx:1.12.0`
* **Lifecycle Management:** `androidx.lifecycle:lifecycle-runtime-ktx:2.6.2`
* **Activity Layer:** `androidx.activity:activity-compose:1.8.2`

### 🏗️ Build & Quality Assurance
* **Build System:** Gradle 8.5 with Android Gradle Plugin (AGP) 8.1.4
* **Unit Testing:** JUnit 4 (`4.13.2`)
* **UI & Integration Testing:** Espresso (`3.5.1`) & Compose UI Test (`androidx.compose.ui:ui-test-junit4`)

---

## 🏗️ Architecture & Data Flow

AquaRoute follows a modern, unidirectional data flow (UDF) architecture leveraging Jetpack Compose, ViewModel state management, and real-time telemetry providers:

```mermaid
flowchart TD
    subgraph Telemetry & Data Feeds
        A[🚢 Ferry GPS Telemetry Sensors]
        B[🌤️ Marine Weather API Services]
        C[⚓ Harbor Terminal Systems]
    end

    subgraph Data Pipeline & Core Engine
        D[📡 Real-Time Data Ingestion Engine]
        E[⚙️ AquaRoute Dynamic ETA Calculator]
        F[💾 Local Cache & Offline Data Sync]
    end

    subgraph Presentation & State
        G[📊 ViewModel & Reactive State Flow]
        H[🎨 Jetpack Compose UI Layer]
    end

    subgraph Passenger Experience
        I[📍 Interactive Map Visualization]
        J[⏱️ Live Terminal Arrival Timetables]
        K[⚠️ Safety Alerts & Weather Overlay]
    end

    A -->|Live Coordinates & Speed| D
    B -->|Wind, Waves & Storm Alerts| D
    C -->|Schedule & Gate Status| D

    D --> E
    E --> F
    F --> G
    G --> H

    H --> I
    H --> J
    H --> K
```

---

## 🚀 Getting Started

Follow these steps to set up the development environment, build, and run **AquaRoute** on your local machine or emulator.

### 📋 Prerequisites

Ensure your environment meets the following requirements:
* **JDK:** Java Development Kit 17 or JDK 11 (configured for Gradle 8.5)
* **Android Studio:** Android Studio Hedgehog (2023.1.1) or newer
* **Android SDK:** API Level 34 installed via SDK Manager
* **Device / Emulator:** Android 5.0 (Lollipop) or higher (API 21+)
* **Build Tool:** Git 2.x+

---

### 📥 Installation & Setup

1. **Clone the Repository:**
   ```bash
   git clone https://github.com/ImRoniel/AquaRoute-System-app.git
   cd AquaRoute-System-app
   ```

2. **Configure Local Environment Settings:**
   Ensure `local.properties` exists in the project root directory and points to your local Android SDK installation:
   ```properties
   sdk.dir=C\:\\Users\\<YourUsername>\\AppData\\Local\\Android\\Sdk
   ```

> [!TIP]
> On macOS or Linux systems, your `sdk.dir` typically defaults to `/Users/<YourUsername>/Library/Android/sdk` or `/home/<YourUsername>/Android/Sdk`.

---

### ⚙️ Environment Configuration

If integrating third-party map or weather services, create or update `local.properties` or environment variables:

```properties
# System Properties Setup (Optional / Extensible)
MAPS_API_KEY=your_google_maps_api_key_here
WEATHER_API_KEY=your_weather_api_key_here
AQUAROUTE_SERVER_URL=https://api.aquaroute.internal
```

---

### 🏃 Running the Application

#### Using Gradle CLI:

* **Build Debug APK:**
  ```bash
  ./gradlew assembleDebug
  ```
  *(The compiled APK will be located at `app/build/outputs/apk/debug/app-debug.apk`)*

* **Run Unit Tests:**
  ```bash
  ./gradlew test
  ```

* **Run Android Instrumentation & UI Tests:**
  ```bash
  ./gradlew connectedCheck
  ```

#### Using Android Studio:
1. Open Android Studio and select **Open an Existing Project**.
2. Select the `AquaRoute-System-app` directory.
3. Allow Gradle to synchronize dependencies.
4. Select `app` in the run configurations dropdown, choose an emulator or connected target device, and press **Run (Shift + F10)**.

---

## 📂 Project Structure

```text
AquaRoute-System-app/
├── app/                            # Primary Android Application Module
│   ├── build.gradle                # Application dependencies, SDK targets, & Compose configs
│   ├── proguard-rules.pro          # ProGuard rules for release builds
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml # App components, permissions, & activity declarations
│       │   ├── kotlin/com/aquaroute/
│       │   │   ├── MainActivity.kt # Entry point Activity hosting Jetpack Compose UI
│       │   │   └── ui/             # Composable UI components, screens, & theme rules
│       │   └── res/                # Drawables, mipmaps, strings, & XML resources
│       ├── test/                   # JVM Unit Tests (JUnit 4)
│       └── androidTest/            # Instrumentation Tests (Espresso & Compose UI Test)
├── gradle/                         # Gradle Wrapper executables & configuration
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── .gitignore                      # Git ignore patterns for Android/Gradle builds
├── build.gradle                    # Top-level build configuration & plugin declarations
├── gradle.properties               # Global JVM memory options & AndroidX flags
├── gradlew                         # Shell script for Gradle Wrapper (macOS/Linux)
├── gradlew.bat                     # Batch script for Gradle Wrapper (Windows)
├── settings.gradle                 # Module declarations and plugin repository configuration
├── LICENSE                         # Official MIT License terms
└── README.md                       # Master project documentation
```

---

## 🤝 Contributing

Contributions to **AquaRoute** are warmly welcomed! Whether you are reporting bugs, improving dynamic ETA algorithms, enhancing Jetpack Compose views, or suggesting new features:

1. **Fork the Repository** on GitHub.
2. **Create a Feature Branch:**
   ```bash
   git checkout -b feature/amazing-feature
   ```
3. **Commit your Changes:**
   ```bash
   git commit -m "feat: Add dynamic wave height overlay to map screen"
   ```
4. **Push to the Branch:**
   ```bash
   git push origin feature/amazing-feature
   ```
5. **Open a Pull Request** describing your proposed changes and reference any related issues.

---

## 📜 License

Distributed under the **MIT License**.

```text
MIT License

Copyright (c) 2026 Roniel C. Carbon

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
```

For full details, please refer to the [LICENSE](LICENSE) file.

---

<div align="center">
  <sub>Built with ❤️ by <a href="https://github.com/ImRoniel">Roniel C. Carbon</a> & the open-source community.</sub>
</div>
