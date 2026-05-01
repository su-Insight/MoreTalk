<div align="center">

<img width="150" height="150" alt="MoreTalk Logo" src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" />

<div style="border: 2px solid #3B82F6; padding: 20px; margin: 20px; display: inline-block; clip-path: polygon(0 0, 15% 0, 15% 5%, 85% 5%, 85% 0, 100% 0, 100% 100%, 85% 100%, 85% 95%, 15% 95%, 15% 100%, 0 100%);">
    <h1 style="margin: 0;">MoreTalk</h1>
    <p style="color: #64748b; font-size: 1.1em;"><b>A Minimalist Android Launcher Custom-Tailored for Elders</b></p>
</div>

[![Android API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=24)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-MIT-green.svg?style=flat)](LICENSE)

</div>

<p align="center">
  <a href="README_en.md">English</a> | <a href="README.md">简体中文</a>
</p>

<p align="center">
  <a href="UPDATE_LOG.md">Update Log / 更新日志</a>
</p>

---

## 📖 Project Vision

**MoreTalk** was born from a deep reflection on the "Digital Divide." Smartphones should not be a barrier in the lives of the elderly. We achieve a seamless experience through **"Extreme Subtraction"**:
- **REMOVE** complex QR scans, payments, ads, and redundant settings.
- **RESHAPE** core communication paths, turning complex WeChat video calls into **one-click access**.
- **ASSIST** using Accessibility automation to complete all "intermediate steps" for seniors, ensuring every care is delivered instantly.

---

## ✨ Core Features

* **🏠 Minimalist Large-Icon Desktop**: Locked layout with extra-large grids to prevent accidental touches or unintentional app deletions.
* **👥 One-Click Family Connect**: Directly displays "Video Call" and "Voice Call" buttons on contact cards, eliminating the need to navigate through WeChat.
* **💬 Automated Smart Dialing**: Underlying Accessibility Service automatically simulates clicks on WeChat menus, pop-ups, and confirmation buttons.
* **🌤️ Automated Voice Weather**: Supports silent background refreshing and hourly voice broadcasts, keeping seniors informed without needing to read the screen.
* **📅 Large-Font Lunar Calendar**: Prominently displays both Lunar and Gregorian dates, aligning with the daily habits of traditional elders.
* **🌐 Cloud-Free by Design**: All data is stored locally with support for contact import and export, so switching phones is worry-free. You retain full control over your data at all times, ensuring absolute security.
* **💥 Data Encryption**: Phone numbers are encrypted using cryptographic algorithms throughout their entire lifecycle during operation, providing an additional layer of data protection.
---

## 🛠️ Tech Stack

### Core Technology
- **Language**: Kotlin
- **Min SDK**: Android 7.0 (API 24)
- **Compile SDK**: Android 14 (API 36)
- **Target SDK**: Android 14 (API 36)
- **App Version**: v1.0.0 (build 1)
- **Java Version**: Java 11

### Core Frameworks
- **`AccessibilityService`**: For UI tree traversal and automated click simulation in WeChat.
- **`Coroutine + Flow`**: Reactive handling of weather data fetching and UI updates.
- **`Retrofit + GSON`**: Powers remote Weather API and Lunar conversion interfaces.
- **`FusedLocationProvider`**: Minimalist logic for geographical location acquisition.
- **`RecyclerView`**: Efficient display for lists and grids.
- **`Material Design`**: Modernized UI design standards for seniors.
- **`TextToSpeech`**: Integrated voice broadcast functionality.

### Key Dependencies
| Library | Version | Purpose |
|--------|------|------|
| `AndroidX Core KTX` | Latest | Core Android extensions |
| `AndroidX AppCompat` | Latest | Compatibility support |
| `Material Components` | Latest | Material Design UI components |
| `Retrofit` | 2.9.0 | Networking framework |
| `GSON` | 2.10.1 | JSON parsing |
| `Kotlin Coroutines` | 1.7.3 | Asynchronous programming |
| `Play Services Location` | 21.1.0 | Location services |
| `RecyclerView` | 1.3.2 | List rendering |
| `Lunar Library` | 1.7.7 | Lunar calendar calculation |

### Architecture
- **MVVM**: Model-View-ViewModel
- **Repository Pattern**: Data access abstraction
- **Adapter Pattern**: Binding UI components with data
- **Singleton**: Global service management

---


## 🌐Operation Interface - Test Device (MI 12s)
<img src="docs/images/item-001.png" height="500" />
<img src="docs/images/item-002.png" height="500" />
<img src="docs/images/item-003.png" height="500" />
<img src="docs/images/item-004.png" height="500" />
<img src="docs/images/item-005.png" height="500" />
<img src="docs/images/item-006.png" height="500" />
<img src="docs/images/item-007.png" height="500" />


## 🚀 Deployment & Installation

### ✨ Get the Latest Features & Optimization Notifications (Recommended)

<img width="960" alt="MoreTalk Star guide" src="docs/images/star-guide.png" />


### 1. Developer Setup
* **Environment**: Install **Android Studio** (Jellyfish or later recommended).
* **Source Code**:
  ```bash
  git clone [https://github.com/su-Insight/MoreTalk.git](https://github.com/su-Insight/MoreTalk.git)
  cd MoreTalk
  ```

* **Build**: Open the project in Android Studio, wait for Gradle sync, and click Run 'app' to install on a physical device (Accessibility Service requires a real device environment).


### 2. Download Binary (APK)
* [Download the latest official APK (v1.1.0)](https://github.com/su-Insight/MoreTalk/releases/tag/v1.1.0)

### 3. Critical Configuration (Guide for Family Members ⚠️)
To ensure the app controls WeChat smoothly, please help the elders complete these manual settings:

- [ ] **Set as Default Launcher**: Go to `Settings -> Apps -> Default Apps -> Home app`, and select **MoreTalk**.
- [ ] **Enable Accessibility**: Go to `Settings -> Accessibility -> Installed Services`, find **MoreTalk Smart Assistant** and turn it **ON**.
- [ ] **Contact Entry**: When adding a contact, the **WeChat Alias** must exactly match the "Remark" set in the WeChat App (The system matches the first search result).
- [ ] **Enable Location**: Ensure the app has "Always Allow" location permissions for automated weather updates.

---

## 📁 Project Structure

* **`ui/activity/MainActivity.kt`**: Desktop layout, time/weather widgets, and state management.
* **`service/WechatAccessibilityService.kt`**: **The Core Engine**. Handles UI tree parsing, automated navigation, and gesture simulation.
* **`data/WeatherManager.kt`**: Silent weather fetching logic for non-intrusive updates.
* **`ui/adapter/ContactAdapter.kt`**: Extra-large font contact list optimized for senior vision.

---

## 🎨 UI & Logo Design Philosophy

* **Minimalism**: Clean interface without shadows or gradients. High-contrast solid colors distinguish functional areas to reduce cognitive load.
* **Visual Guidance**: The logo combines a communication bubble with a `+` sign, representing "More Connection" and "Simpler Communication."
* **Senior-Friendly**: All touch targets are enlarged to accommodate the tactile habits of the elderly.

---

## 💡 Deployment Tip: AndroidManifest Configuration

It is recommended to set `singleInstance` for the `MainActivity` in `AndroidManifest.xml` to ensure system stability and prevent users from accidentally exiting the desktop:

```xml
<activity
    android:name=".ui.activity.MainActivity"
    android:launchMode="singleInstance"
    android:label="@string/app_name"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.HOME" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```

## 🤝 Contribution & Feedback

**MoreTalk - Technology should not be a barrier for the elderly.**

If you find this project meaningful, please give it a ⭐️ **Star**. It is the driving force for our continuous optimization.

If you have better suggestions for simplification, feel free to submit a **Pull Request**.

### ☕ Support the Project

If this project has been helpful to you, you are also welcome to buy the author a coffee. Your support helps keep MoreTalk improving.

<p align="center">
  <img src="docs/images/donation-qr.jpg" alt="MoreTalk donation QR code" width="360" />
</p>
