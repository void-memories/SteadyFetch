# SteadyFetch

[![Unit Tests](https://img.shields.io/github/actions/workflow/status/void-memories/SteadyFetch/unit-tests.yml?label=Unit%20Tests&logo=github)](https://github.com/void-memories/SteadyFetch/actions/workflows/unit-tests.yml)
[![JitPack](https://jitpack.io/v/void-memories/SteadyFetch.svg)](https://jitpack.io/#void-memories/SteadyFetch)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin)](https://kotlinlang.org/)
[![Android API](https://img.shields.io/badge/API-28%2B-3DDC84?logo=android)](#)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](./LICENSE)

**Reliable parallel + resumable downloads for Android, written in Kotlin.**

SteadyFetch is a Kotlin SDK for Android that provides reliable, resumable downloads. It handles chunking, storage checks, notifications, and foreground service requirements so your app can focus on product logic instead of download plumbing.

---

## Table of Contents

1. [Feature Highlights](#feature-highlights)
2. [Quick Facts](#quick-facts)
3. [Installation](#installation)
4. [Quickstart (5 minutes)](#quickstart-5-minutes)
5. [Usage Example](#usage-example)
6. [API Reference](#api-reference)
7. [How It Works (High Level)](#how-it-works-high-level)
8. [FAQ](#faq)
9. [Roadmap](#roadmap)
10. [Contributing](#contributing)
11. [License](#license)

---

## Feature Highlights

- **Parallel chunk downloads** – Splits files into chunks and fetches them concurrently for faster throughput.
- **Foreground-friendly execution** – Keeps long transfers alive via a dedicated foreground service + notification flow.
- **Resume support** – Persists progress and resumes exactly where a transfer stopped (app kill, process death, or network drop).
- **Checksum + storage validation** – Validates remote metadata and ensures sufficient storage before writing.
- **Well-tested core** – Unit tests cover the controller, networking layer, chunk math, and error propagation.

---

## Demo

Download surviving app kills + network changes with chunk-level progress:

https://github.com/user-attachments/assets/2b9f9384-eac8-4a22-932f-2f8728a2870b

---

## Quick Facts

- **Use case** – Resumable downloads with chunk-level progress and safe foreground execution.
- **Tech stack** – Kotlin, OkHttp, Android Service + Notification APIs.
- **Minimum OS** – Android 9 (API 28); builds against SDK 36.
- **Distribution** – Published via JitPack under `dev.namn.steady-fetch:steady-fetch`.
- **Status** – Early-stage, actively evolving. APIs may change before `1.0.0`.

---

## Installation

Add JitPack once in your **root** `settings.gradle.kts`:

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

Pull in the dependency in your **app module**:

```kotlin
dependencies {
    implementation("dev.namn.steady-fetch:steady-fetch:<version>")
}
```

> 🔎 Latest versions are listed on JitPack:  
> https://jitpack.io/#void-memories/SteadyFetch

---

## Quickstart (5 minutes)

### 1. Initialize in your `Application`

```kotlin
class SteadyFetchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SteadyFetch.initialize(this)
    }
}
```

Remember to register your `Application` class in `AndroidManifest.xml`:

```xml
<application
    android:name=".SteadyFetchApp"
    ... >
    ...
</application>
```

### 2. Queue a download

```kotlin
val downloadId = SteadyFetch.queueDownload(
    request = DownloadRequest(
        url = "https://files.example.com/iso/latest.iso",
        fileName = "latest.iso",
        downloadDir = File(context.filesDir, "downloads")
    ),
    callback = object : SteadyFetchCallback {
        override fun onSuccess() {
            // Update UI, notify user, etc.
        }

        override fun onUpdate(progress: DownloadProgress) {
            // E.g. show progress in a notification or Compose UI
        }

        override fun onError(error: DownloadError) {
            // Log & show an error state
        }
    }
)
```

### 3. Cancel if needed

```kotlin
SteadyFetch.cancelDownload(downloadId)
```

That’s enough to get a resilient, resumable download up and running.

---

## Usage Example

SteadyFetch is designed to be called from your UI or domain layer, while the heavy lifting runs in a dedicated service.

A typical flow:

1. User taps a “Download” button.
2. You call `SteadyFetch.queueDownload()` with a `DownloadRequest`.
3. You observe `SteadyFetchCallback.onUpdate()` and update your UI.
4. On success or error, you update local state / DB accordingly.

Example with a simple wrapper:

```kotlin
fun startIsoDownload(context: Context) {
    val downloadsDir = File(context.filesDir, "downloads")

    val request = DownloadRequest(
        url = "https://files.example.com/iso/latest.iso",
        fileName = "latest.iso",
        downloadDir = downloadsDir
    )

    val callback = object : SteadyFetchCallback {
        override fun onSuccess() {
            // Maybe emit an event to your ViewModel or show a snackbar
        }

        override fun onUpdate(progress: DownloadProgress) {
            // progress.percent, progress.bytesDownloaded, etc.
        }

        override fun onError(error: DownloadError) {
            // Map error to something user-friendly
        }
    }

    SteadyFetch.queueDownload(request, callback)
}
```

All public entry points live under the `dev.namn.steady_fetch` package and are backed by unit tests (MockK, Robolectric, MockWebServer).

---

## API Reference

### Public APIs

#### `SteadyFetch.initialize(application: Application)`

Initializes the SteadyFetch SDK. Must be called once before using any other APIs, typically in your `Application.onCreate()`.

**Parameters:**
- `application: Application` - Your application instance

**Throws:** `IllegalStateException` if initialization fails

---

#### `SteadyFetch.queueDownload(request: DownloadRequest, callback: SteadyFetchCallback): Long`

Enqueues a new download and returns a unique download ID. The download runs asynchronously in a foreground service.

**Parameters:**
- `request: DownloadRequest` - Download configuration (see [Data Models](#data-models))
- `callback: SteadyFetchCallback` - Callback interface for progress updates and completion

**Returns:** `Long` - Unique download ID that can be used to cancel the download

**Throws:** `IllegalStateException` if SteadyFetch is not initialized

---

#### `SteadyFetch.cancelDownload(downloadId: Long): Boolean`

Cancels an active download by its ID.

**Parameters:**
- `downloadId: Long` - The download ID returned from `queueDownload()`

**Returns:** `Boolean` - `true` if a download was found and cancelled, `false` otherwise

---

### Data Models

#### `DownloadRequest`

Configuration object for a download request.

```kotlin
data class DownloadRequest(
    val url: String,                                    // Required: URL of the file to download
    val headers: Map<String, String> = emptyMap(),      // Optional: Custom HTTP headers (e.g., auth tokens)
    val maxParallelDownloads: Int = 4,                 // Optional: Max concurrent chunks (1-25, default 4)
    val downloadDir: File,                              // Required: Directory where the file will be saved
    val fileName: String,                               // Required: Name for the final downloaded file
)
```

**Fields:**
- `url` - The HTTP/HTTPS URL of the file to download
- `headers` - Optional map of HTTP headers (useful for authentication, custom user agents, etc.)
- `maxParallelDownloads` - Maximum number of chunks to download concurrently. Must be between 1 and 25. Higher values may improve speed on fast connections but can overwhelm slower networks.
- `downloadDir` - The directory where the file will be saved. Must be writable. The directory will be created if it doesn't exist.
- `fileName` - The name of the final file after download completes (e.g., `"video.mp4"`, `"document.pdf"`)

---

#### `SteadyFetchCallback`

Interface for receiving download progress updates and completion notifications.

```kotlin
interface SteadyFetchCallback {
    fun onSuccess()
    fun onUpdate(progress: DownloadProgress)
    fun onError(error: DownloadError)
}
```

**Methods:**
- `onSuccess()` - Called when the download completes successfully. The file is available at `downloadDir/fileName`.
- `onUpdate(progress: DownloadProgress)` - Called periodically with progress updates. See `DownloadProgress` below for details.
- `onError(error: DownloadError)` - Called when the download fails. See `DownloadError` below for details.

---

#### `DownloadProgress`

Snapshot of the current download state, including overall progress and per-chunk status.

```kotlin
data class DownloadProgress(
    val status: DownloadStatus,                         // Current overall download status
    val progress: Float,                                // Overall progress (0.0 to 1.0)
    val chunkProgress: List<DownloadChunkProgress>     // Per-chunk progress details
)
```

**Fields:**
- `status` - Current status of the download (see `DownloadStatus` enum below)
- `progress` - Overall progress as a float between 0.0 (0%) and 1.0 (100%)
- `chunkProgress` - List of progress information for each chunk (empty for non-chunked downloads)

---

#### `DownloadChunkProgress`

Progress information for a single chunk.

```kotlin
data class DownloadChunkProgress(
    val status: DownloadStatus,                         // Status of this chunk
    val name: String,                                   // Chunk filename (e.g., "file.iso.part01")
    val progress: Float                                  // Chunk progress (0.0 to 1.0)
)
```

**Fields:**
- `status` - Current status of this chunk
- `name` - The temporary filename of this chunk (useful for debugging)
- `progress` - Progress of this chunk as a float between 0.0 and 1.0

---

#### `DownloadError`

Error information when a download fails.

```kotlin
data class DownloadError(
    val code: Int,                                      // Error code (HTTP status code if available, or -1)
    val message: String                                 // Human-readable error message
)
```

**Fields:**
- `code` - Error code. If the error is HTTP-related, this will be the HTTP status code (e.g., 404, 500). Otherwise, it may be -1 for generic errors, 499 for cancellations, 400 for bad requests, or 500 for internal errors.
- `message` - Descriptive error message explaining what went wrong

---

#### `DownloadStatus`

Enum representing the current state of a download or chunk.

```kotlin
enum class DownloadStatus {
    QUEUED,    // Download is queued but not yet started
    RUNNING,   // Download is actively in progress
    FAILED,    // Download has failed
    SUCCESS    // Download completed successfully
}
```

**Values:**
- `QUEUED` - The download is queued and waiting to start
- `RUNNING` - The download is actively transferring data
- `FAILED` - The download encountered an error and stopped
- `SUCCESS` - The download completed successfully

---

## How It Works (High Level)

At a high level, SteadyFetch:

1. **Inspects the remote file** – Uses a HEAD/metadata request to determine file size and validate the response.
2. **Splits into chunks** – Computes chunk ranges and builds `Range`-based HTTP requests.
3. **Downloads in parallel** – Fetches chunks concurrently using OkHttp and writes them to temporary files.
4. **Validates & merges** – Optionally checks integrity, then merges chunks into the final file.
5. **Survives process death** – Persists download state and resumes where it left off when the app or process restarts.
6. **Runs in a foreground service** – Uses a dedicated service + ongoing notification to comply with Android’s background execution limits.

You don’t have to manage threads, chunk math, or foreground-service boilerplate yourself.

---

## Roadmap

Planned / potential features:

- [x] Parallel chunk downloads
- [x] Resume support
- [x] Foreground-service based execution
- [ ] Progress notification helpers
- [ ] Configurable retry/backoff policy

If you have a use case that isn’t covered yet, please open an issue.

---

## Contributing

Contributions, bug reports, and feature requests are very welcome.

- **Bugs** – Open an issue with steps to reproduce and logs if possible.
- **Features** – Open an issue first so we can discuss the design.
- **PRs** – Try to include unit tests where it makes sense and keep changes focused.

If you’re unsure whether something belongs in core or should be an extension, feel free to open a “question” issue.

---

## License

SteadyFetch is distributed under the [MIT License](./LICENSE).

If you use this library in production, consider linking back to this repository so others can discover it.
