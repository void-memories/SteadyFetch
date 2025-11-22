# SteadyFetch

[![Release Check](https://img.shields.io/github/actions/workflow/status/void-memories/SteadyFetch/ci.yml?label=Release%20Check&logo=github)](https://github.com/void-memories/SteadyFetch/actions/workflows/ci.yml)
[![JitPack](https://jitpack.io/v/void-memories/SteadyFetch.svg)](https://jitpack.io/#void-memories/SteadyFetch)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin)](https://kotlinlang.org/)
[![Android API](https://img.shields.io/badge/API-28%2B-3DDC84?logo=android)](#)

> _"One download stream? That’s cute. SteadyFetch opens the vault doors with multi-connection precision."_  

SteadyFetch is a Kotlin-based Android SDK that turns downloads into an orchestrated op. It slices large payloads into coordinated chunks, persists progress, and recovers gracefully when networks misbehave. Use it to ship resumable, foreground-aware downloads without rewriting the transport stack for every app.

---

## 📡 Index

1. [Signal at a Glance](#signal-at-a-glance)
2. [Bootstrapping the SDK](#bootstrapping-the-sdk)
3. [Quick Deploy](#quick-deploy)
4. [Feature Loadout](#feature-loadout)
5. [Development Logbook](#development-logbook)
6. [Release Ritual](#release-ritual)
7. [FAQ](#faq-from-the-ops-vault)
8. [Field Notes & Contributions](#field-notes--contributions)
9. [License](#license)

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
        destination = File(context.filesDir, "latest.iso")
    ),
    callback = object : SteadyFetchCallback {
        override fun onChunkProgress(update: ChunkProgress) { /* UI pulse */ }
        override fun onCompleted(result: DownloadResult.Success) { /* Mission accomplished */ }
        override fun onError(error: DownloadResult.Failure) { /* Re-arm or abort */ }
    }
)

// Abort when needed
SteadyFetch.cancelDownload(downloadId)
```

> All primary entry points live under `dev.namn.steady_fetch` and are unit-tested (MockK, Robolectric, MockWebServer) to ensure deterministic runs.

---

## 🧩 Feature Loadout

- **Chunked Execution** – `ChunkManager`, `ChunkProgressManager`, and `FileManager` coordinate segmented downloads with persistent offsets.
- **Foreground Service Ready** – `DownloadForegroundService` + `DownloadNotificationManager` keep Android happy during long operations.
- **Pluggable Networking** – OkHttp-backed IO with extension points for custom interceptors.
- **Callback Grid** – `SteadyFetchCallback` broadcasts chunk progress, completion, and failure signals.
- **DI Friendly** – `DependencyContainer` keeps stateful collaborators injectable for tests or custom wiring.

---

## 📔 Development Logbook

| Command | Purpose |
|---------|---------|
| `./gradlew steady_fetch:lintDebug` | Static analysis before committing |
| `./gradlew steady_fetch:testReleaseUnitTest` | Unit test suite |
| `./gradlew steady_fetch:publishReleasePublicationToMavenLocal` | Local Maven smoke-test (used by CI + JitPack) |

Gradle caching is enabled globally (`org.gradle.caching=true`), so repeated runs stay lean.

---

## 🛰️ Release Ritual

1. Push your changes to `main`.
2. Trigger **Release Check** in GitHub Actions → supply `release_version` (e.g., `1.2.0`).
3. Workflow runs lint, tests, and validates the Maven publication with that version injected via `RELEASE_VERSION`.
4. Tag the commit (`git tag v1.2.0 && git push origin v1.2.0`) so JitPack indexes the build.

> JitPack additionally reads `jitpack.yml`, compiling with OpenJDK 17 and running the same publication task to stage the artifact.

---

## ❔ FAQ (From the Ops Vault)

- **Why JitPack?**  
  Keeps distribution frictionless—consumers only need a Git tag.

- **Can I customize notifications?**  
  Swap out or extend `DownloadNotificationManager` with your own channels/style.

- **What about multi-tenant downloads?**  
  The `SteadyFetch` facade is thread-safe; queue as many concurrent requests as you like and cancel them individually by ID.

---

## 🗒️ Field Notes & Contributions

Pull requests and issue reports are welcome. Please include:

1. A failing unit test (or a new one) that demonstrates the bug/feature.
2. Explain the scenario in the PR description—log excerpts and repro steps earn extra hacker cred.

---

## 📜 License

TBD – until formalized, treat the repository as “look but don’t ship commercially.” Open an issue if you need clarity.

---

Stay sharp, patch often, and may your downloads never drop.  

`$ tail -f /var/log/steady-fetch`

