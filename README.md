# SteadyFetch

[![Release Check](https://img.shields.io/github/actions/workflow/status/void-memories/SteadyFetch/ci.yml?label=Release%20Check&logo=github)](https://github.com/void-memories/SteadyFetch/actions/workflows/ci.yml)
[![JitPack](https://jitpack.io/v/void-memories/SteadyFetch.svg)](https://jitpack.io/#void-memories/SteadyFetch)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin)](https://kotlinlang.org/)
[![Android API](https://img.shields.io/badge/API-28%2B-3DDC84?logo=android)](#)

> _"One download stream? That’s cute. SteadyFetch opens the vault doors with multi-connection precision."_  

SteadyFetch is a Kotlin-based Android SDK that turns downloads into an orchestrated op. It slices large payloads into coordinated chunks, persists progress, and recovers gracefully when networks misbehave. Use it to ship resumable, foreground-aware downloads without rewriting the transport stack for every app.

### 🎯 Feature Signal

- **Uninterrupted missions** – Foreground service keeps long transfers alive even through app kills or background restrictions.
- **State persistence** – Chunk progress and metadata survive process death so downloads resume exactly where they stopped.
- **Automatic resume** – Interrupted transfers reconnect, validate remote metadata, and continue without re-downloading bytes.
- **Multi-connection mesh** – Parallel chunking squeezes every drop out of fast networks.
- **Battle-tested** – MockK, Robolectric, and MockWebServer suites cover callbacks, chunk math, IO, and failure paths.

---

## 📡 Demo

https://github.com/user-attachments/assets/f14753d8-9445-4ed9-ba48-8abceb415216

---

## 📡 Index

1. [Feature Signal](#-feature-signal)
2. [Signal at a Glance](#-signal-at-a-glance)
3. [Bootstrapping the SDK](#%EF%B8%8F-bootstrapping-the-sdk)
4. [Quick Deploy](#-quick-deploy)
5. [License](#-license)

---

## ⚡ Signal at a Glance

- **Mission profile** – Resumable, multi-connection downloads with chunk-level tracking.
- **Stack** – Kotlin, OkHttp, Android Service + Notification integration.
- **Targets** – Android 9 (API 28) and above, compiled with SDK 36.
- **Status lights** – `Release Check` GitHub Action runs lint/tests + publication smoke-test.
- **Distribution** – Consumable straight from JitPack under `dev.namn.steady-fetch:steady-fetch`.

---

## 🛠️ Bootstrapping the SDK

### 1. Wire up the repository

Add JitPack to your settings once:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}
```

### 2. Pull in the dependency

```kotlin
// module build.gradle.kts
dependencies {
    implementation("dev.namn.steady-fetch:steady-fetch:<version>")
}
```

Latest release versions live at [jitpack.io/#void-memories/SteadyFetch](https://jitpack.io/#void-memories/SteadyFetch). Until a release drops, the default artifact is `0.1.0-SNAPSHOT`.

---

## 🚀 Quick Deploy

```kotlin
// App.kt
class SteadyFetchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SteadyFetch.initialize(this) // arm the internal controller + services
    }
}

// Somewhere in your feature module
val downloadId = SteadyFetch.queueDownload(
    request = DownloadRequest(
        url = "https://files.example.com/iso/latest.iso",
        fileName = "latest.iso",
        downloadDir = File(context.filesDir, "downloads")
    ),
    callback = object : SteadyFetchCallback {
        override fun onSuccess() { /* Mission accomplished */ }
        override fun onUpdate(progress: DownloadProgress) { /* UI pulse */ }
        override fun onError(error: DownloadError) { /* Re-arm or abort */ }
    }
)

// Abort when needed
SteadyFetch.cancelDownload(downloadId)
```

> All primary entry points live under `dev.namn.steady_fetch` and are unit-tested (MockK, Robolectric, MockWebServer) to ensure deterministic runs.

---

## 📜 License

SteadyFetch is distributed under the [MIT License](./LICENSE). Feel free to use it in commercial and open-source apps—just keep the copyright + permission notice intact.
