# SteadyFetch

[![Unit Tests](https://img.shields.io/github/actions/workflow/status/void-memories/SteadyFetch/unit-tests.yml?label=Unit%20Tests&logo=github)](https://github.com/void-memories/SteadyFetch/actions/workflows/unit-tests.yml)
[![JitPack](https://jitpack.io/v/void-memories/SteadyFetch.svg)](https://jitpack.io/#void-memories/SteadyFetch)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin)](https://kotlinlang.org/)
[![Android API](https://img.shields.io/badge/API-28%2B-3DDC84?logo=android)](#)

SteadyFetch is a Kotlin SDK for Android that provides reliable, resumable downloads. It handles chunking, storage validation, notifications, and foreground service requirements so your app can focus on business logic.

## Feature Highlights

- **Parallel chunk downloads** – Splits files into chunks and fetches them concurrently.
- **Foreground-friendly execution** – Keeps long transfers alive through a dedicated service + notification flow.
- **Resume support** – Persists state and resumes exactly where a transfer stopped.
- **Checksum + storage validation** – Validates remote metadata and available storage before writing.
- **Well-tested core** – Unit tests cover the controller, networking layer, chunk math, and error propagation.

---

## Demo

https://github.com/user-attachments/assets/f14753d8-9445-4ed9-ba48-8abceb415216

---

## Table of Contents

1. [Feature Highlights](#feature-highlights)
2. [Quick Facts](#quick-facts)
3. [Setup](#setup)
4. [Usage Example](#usage-example)
5. [License](#license)

---

## Quick Facts

- **Use case** – Resumable downloads with chunk-level progress.
- **Tech stack** – Kotlin, OkHttp, Android Service and Notification APIs.
- **Minimum OS** – Android 9 (API 28); builds against SDK 36.
- **Distribution** – Published via JitPack under `dev.namn.steady-fetch:steady-fetch`.

---

## Setup

Add JitPack once in `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}
```

Pull in the dependency in your module:

```kotlin
dependencies {
    implementation("dev.namn.steady-fetch:steady-fetch:<version>")
}
```

Latest versions are listed on [jitpack.io/#void-memories/SteadyFetch](https://jitpack.io/#void-memories/SteadyFetch). Until an official release ships, use `0.1.0-SNAPSHOT`.

---

## Usage Example

```kotlin
class SteadyFetchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SteadyFetch.initialize(this)
    }
}

val downloadId = SteadyFetch.queueDownload(
    request = DownloadRequest(
        url = "https://files.example.com/iso/latest.iso",
        fileName = "latest.iso",
        downloadDir = File(context.filesDir, "downloads")
    ),
    callback = object : SteadyFetchCallback {
        override fun onSuccess() { /* update UI */ }
        override fun onUpdate(progress: DownloadProgress) { /* show progress */ }
        override fun onError(error: DownloadError) { /* handle failure */ }
    }
)

SteadyFetch.cancelDownload(downloadId)
```

All entry points live under `dev.namn.steady_fetch` and are backed by unit tests (MockK, Robolectric, MockWebServer).

---

## License

SteadyFetch is distributed under the [MIT License](./LICENSE). Include the copyright and license notice in any
redistribution.
