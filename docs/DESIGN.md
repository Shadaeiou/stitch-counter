# Build "Loop" — an Android stitch counter app with PDF viewer + auto-updater

## What we're building

A minimal, gesture-first Android app for tracking stitch counts while knitting, with a PDF viewer for following patterns. Single-screen split layout: counter on top half, PDF on bottom half, toolbar at the bottom.

**Target:** Android 10+ (minSdk 29, targetSdk 35), Kotlin + Jetpack Compose, single-activity, edge-to-edge, dark theme by default.

- **App ID:** `com.burkelitton.loop` (confirm if you want different)
- **Package name:** `loop`
- **Display name:** Loop

---

## STOP — confirm prerequisites before writing any code

I need you to ask me about these BEFORE generating files. Do not start coding until I confirm:

1. **GitHub repo for the auto-updater.** What's the owner/repo slug, and is it public or private? If private, the auto-updater needs a backend proxy — flag this and we'll discuss.

2. **Release signing keystore.** The auto-updater requires a stable release keystore. Have I generated `release.jks` and added the four GitHub Actions secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`)? If not, walk me through it before continuing — we cannot ship updates without this.

3. **First-install boundary.** The first signed APK from CI cannot install over a debug-signed APK on the device. Confirm I understand I'll need to uninstall once during the cutover. (For Loop this is a greenfield app so it's a non-issue, but confirm.)

If any of these aren't ready, stop and tell me what to do first.

---

## Feature spec — MVP

### Counter (top half of screen)

- Big number, centered, ~180sp, light weight, tabular numerals
- Editable label above the number (e.g. "Heel Flap Row"), persisted
- Optional context line below ("Row 7 of 20") — leave the slot, wire later
- **Tap anywhere in the counter area** → increment by 1
  - Subtle green tint flash (~150ms ease-out) on the whole area
  - Light haptic tick
  - Debounce 250ms to ignore accidental double-taps
- **Long-press (400ms, stationary)** → decrement by 1
  - At 200ms, fade in a "−1" indicator over the number so the user knows decrement is about to fire and can lift to cancel
  - Heavier haptic on fire
  - Red tint flash
  - If finger moves >10px, cancel the long-press
  - At 0, don't go negative — shake animation + heavy haptic
- **Pull down from the counter area (>80px)** → opens history sheet
  - History sheet slides over the counter only, not the PDF
  - Lists recent actions with timestamps (newest first, ~50 entries)
  - "Undo Last" and "Reset to 0" buttons
  - Reset shows an undo snackbar for 4s
- **Lock toggle in toolbar** → counter ignores all input until unlocked
  - Visual: dim the counter area
- **Optional volume keys** → increment / decrement
  - Override `dispatchKeyEvent` in MainActivity, push to ViewModel
  - Make it a togglable setting (default ON), persist in DataStore

### PDF viewer (bottom half)

- Uses Android's built-in `PdfRenderer` for MVP
- Pinch-zoom + pan
- Page navigation (swipe horizontally or arrow buttons)
- Tap a page to expand to full screen, tap again to return to split
- "Invert colors" toggle for dark-mode PDF reading (apply a color matrix filter to the rendered bitmap)
- For MVP: **NO annotation/pen yet.** Stub the pen and eraser toolbar buttons but have them show a "Coming soon" toast. Annotation is the hard part and I want to ship the counter first.

### Toolbar (bottom, ~56dp)

Six buttons: Upload PDF, Pen (stub), Eraser (stub), Notes (stub), Invert colors, Lock.

### Persistence

- Room database for projects + counter state
  - `Project(id, name, label, count, currentPage, pdfUri, createdAt, updatedAt)`
  - `HistoryEntry(id, projectId, type: 'up'|'down'|'reset', fromCount, toCount, timestamp)`
- DataStore for app preferences:
  - `autoUpdateEnabled` (default true)
  - `volumeKeysEnabled` (default true)
  - `currentProjectId`
- PDFs: copy to app's internal storage on import (don't rely on the external URI surviving), reference by file path
- Use SAF (Storage Access Framework) `OpenDocument` for PDF picking — no broad storage permissions

### Other Android-specific bits

- `enableEdgeToEdge()` in onCreate, handle insets so the flash reaches the system bars
- `keepScreenOn` modifier on the root composable
- Single activity, Compose Nav for any future screens
- Material 3 theming, dark by default

---

## Project structure

```
android/
  app/
    build.gradle.kts
    src/main/
      AndroidManifest.xml
      java/com/burkelitton/loop/
        MainActivity.kt
        LoopApp.kt                     # @HiltAndroidApp or manual DI
        ui/
          counter/CounterScreen.kt
          counter/CounterArea.kt       # the gesture-heavy composable
          counter/HistorySheet.kt
          pdf/PdfViewer.kt
          toolbar/Toolbar.kt
          theme/                        # Color, Theme, Typography
        data/
          db/AppDatabase.kt
          db/ProjectDao.kt
          db/HistoryDao.kt
          db/entities/Project.kt
          db/entities/HistoryEntry.kt
          prefs/UserPrefs.kt            # DataStore wrapper
          repo/ProjectRepository.kt
        viewmodel/CounterViewModel.kt
        update/Updater.kt               # the auto-updater
        update/UpdateInfo.kt
  build.gradle.kts
  settings.gradle.kts
  gradle/libs.versions.toml             # version catalog
.github/
  workflows/build.yml
```

Use a Gradle version catalog. Use kotlinx.serialization for the updater's JSON parsing (already required by the updater spec). Use OkHttp for the HTTP client. Use Hilt or manual DI — your call, but keep it lean; this app is small.

---

## Auto-updater — spec to implement verbatim

The auto-updater follows the GitHub Releases pattern: CI publishes a signed APK per push to main, the app polls the GitHub API on launch, and offers to download + install when a newer versionCode is available.

### Tag scheme

`v<versionName>+<versionCode>` (e.g. `v0.1.42+42`). versionCode comes from `git rev-list --count HEAD`. versionName is `0.1.<versionCode>` to start.

### CI workflow (`.github/workflows/build.yml`)

Add to the existing build workflow (after the build step):

```yaml
- name: Decode signing keystore
  if: github.ref == 'refs/heads/main' || startsWith(github.ref, 'refs/tags/v')
  run: |
    if [ -z "${{ secrets.KEYSTORE_BASE64 }}" ]; then
      echo "::error::KEYSTORE_BASE64 not set" && exit 1
    fi
    echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > "$RUNNER_TEMP/release.jks"
    echo "RELEASE_KEYSTORE_PATH=$RUNNER_TEMP/release.jks" >> "$GITHUB_ENV"
    echo "RELEASE_KEYSTORE_PASSWORD=${{ secrets.KEYSTORE_PASSWORD }}" >> "$GITHUB_ENV"
    echo "RELEASE_KEY_ALIAS=${{ secrets.KEY_ALIAS }}" >> "$GITHUB_ENV"
    echo "RELEASE_KEY_PASSWORD=${{ secrets.KEY_PASSWORD }}" >> "$GITHUB_ENV"

- name: Build release APK
  working-directory: android
  run: ./gradlew :app:assembleRelease

- name: Compute version
  id: ver
  run: |
    CODE=$(git rev-list --count HEAD)
    NAME="0.1.$CODE"
    echo "tag=v${NAME}+${CODE}" >> "$GITHUB_OUTPUT"
    echo "name=$NAME" >> "$GITHUB_OUTPUT"
    echo "code=$CODE" >> "$GITHUB_OUTPUT"

- name: Publish release
  if: github.ref == 'refs/heads/main'
  uses: softprops/action-gh-release@v2
  with:
    tag_name: ${{ steps.ver.outputs.tag }}
    name: ${{ steps.ver.outputs.name }} (build ${{ steps.ver.outputs.code }})
    files: android/app/build/outputs/apk/release/app-release.apk
    body: "Automated build from ${{ github.sha }}"
    # IMPORTANT: do NOT set prerelease: true. /releases/latest skips
    # prereleases, so marking these prerelease silently breaks the app's
    # "latest" lookup unless the app falls back to /releases.
```

Job needs `permissions: contents: write`.

The `versionCode` in `defaultConfig` should also be derived from `git rev-list --count HEAD` at configure time so local builds match CI.

### `app/build.gradle.kts` (signing)

Inside `android { ... }`:

```kotlin
signingConfigs {
    create("release") {
        val storePath = System.getenv("RELEASE_KEYSTORE_PATH")
        if (storePath != null && file(storePath).exists()) {
            storeFile = file(storePath)
            storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("RELEASE_KEY_ALIAS")
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
        }
    }
}
buildTypes {
    release {
        // existing minify/proguard config
        signingConfig = signingConfigs.findByName("release")
            ?.takeIf { it.storeFile != null }
            ?: signingConfigs.getByName("debug")
    }
}
```

The `?:` fallback means local `assembleRelease` works without secrets.

### `AndroidManifest.xml`

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

### `Updater.kt`

```kotlin
class Updater(
    private val context: Context,
    private val owner: String,
    private val repo: String,
) {
    private val client = OkHttpClient.Builder().build()
    private val json = Json { ignoreUnknownKeys = true }

    /** Scan recent releases and pick the highest-versionCode one that's
     * newer than the running build. Use /releases (returns prereleases
     * too) instead of /releases/latest (skips them silently). */
    suspend fun checkForUpdate(currentVersionCode: Int): UpdateInfo? =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/releases?per_page=10")
                .header("Accept", "application/vnd.github+json")
                .build()
            val res = runCatching { client.newCall(req).execute() }.getOrNull()
                ?: return@withContext null
            res.use {
                if (!it.isSuccessful) return@withContext null
                val body = it.body?.string() ?: return@withContext null
                val releases = runCatching {
                    json.decodeFromString<List<GithubRelease>>(body)
                }.getOrNull() ?: return@withContext null
                val best = releases.mapNotNull { rel ->
                    val parsed = parseVersionFromTag(rel.tagName) ?: return@mapNotNull null
                    val asset = rel.assets.firstOrNull { it.name.endsWith(".apk") }
                        ?: return@mapNotNull null
                    Triple(parsed, asset, rel)
                }.maxByOrNull { (p, _, _) -> p.code } ?: return@withContext null
                val (parsed, asset, release) = best
                if (parsed.code <= currentVersionCode) return@withContext null
                UpdateInfo(parsed.code, parsed.name, asset.url, release.body)
            }
        }

    fun startDownload(info: UpdateInfo): Long {
        val req = DownloadManager.Request(Uri.parse(info.downloadUrl))
            .setTitle("$repo ${info.versionName}")
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setDestinationInExternalFilesDir(
                context, Environment.DIRECTORY_DOWNLOADS,
                "$repo-${info.versionCode}.apk",
            )
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return dm.enqueue(req)
    }

    suspend fun awaitDownload(id: Long): DownloadResult = withContext(Dispatchers.IO) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val q = DownloadManager.Query().setFilterById(id)
        while (true) {
            dm.query(q).use { c ->
                if (c.moveToFirst()) {
                    when (c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS))) {
                        DownloadManager.STATUS_SUCCESSFUL -> return@withContext DownloadResult.Success
                        DownloadManager.STATUS_FAILED -> return@withContext DownloadResult.Failure(
                            "reason ${c.getInt(c.getColumnIndex(DownloadManager.COLUMN_REASON))}"
                        )
                    }
                } else return@withContext DownloadResult.Failure("download disappeared")
            }
            delay(500)
        }
        @Suppress("UNREACHABLE_CODE") DownloadResult.Failure("unreachable")
    }

    fun launchInstall(id: Long) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = dm.getUriForDownloadedFile(id) ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }
}

data class UpdateInfo(
    val versionCode: Int, val versionName: String,
    val downloadUrl: String, val notes: String?,
)
sealed class DownloadResult {
    object Success : DownloadResult()
    data class Failure(val reason: String) : DownloadResult()
}

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("body") val body: String? = null,
    @SerialName("assets") val assets: List<Asset> = emptyList(),
)
@Serializable
private data class Asset(
    @SerialName("name") val name: String,
    @SerialName("browser_download_url") val url: String,
)

private data class ParsedVersion(val code: Int, val name: String)
private fun parseVersionFromTag(tag: String): ParsedVersion? {
    val cleaned = tag.removePrefix("v")
    val plus = cleaned.lastIndexOf('+')
    if (plus < 1 || plus == cleaned.length - 1) return null
    val code = cleaned.substring(plus + 1).toIntOrNull() ?: return null
    return ParsedVersion(code, cleaned.substring(0, plus))
}
```

### Settings UI

A small Settings screen reachable from the toolbar (overflow menu or gear icon — your call). Contains:

- Toggle: "Auto-check for updates" (DataStore `autoUpdateEnabled`)
- Toggle: "Volume keys count" (DataStore `volumeKeysEnabled`)
- Button: "Check for updates" — on tap, show one of:
  - Checking…
  - Up to date (display `BuildConfig.VERSION_NAME`)
  - Available → dialog with versionName + body + Update/Later buttons
  - Error → surface the failure reason in the UI (do NOT silently return null — the user will be confused why nothing happens)

### Launch check

In MainActivity's top-level composable, gated by the toggle:

```kotlin
val autoUpdate by prefs.autoUpdateFlow.collectAsState(initial = false)
var pending by remember { mutableStateOf<UpdateInfo?>(null) }
LaunchedEffect(autoUpdate) {
    if (!autoUpdate) return@LaunchedEffect
    runCatching {
        Updater(context.applicationContext, "<owner>", "<repo>")
            .checkForUpdate(BuildConfig.VERSION_CODE)
    }.getOrNull()?.let { pending = it }
}
pending?.let { info ->
    // dialog — on confirm: startDownload → awaitDownload → launchInstall
    // on dismiss: clear pending
}
```

---

## Auto-updater gotchas to verify before claiming done

1. Run from outside any GitHub auth context:
   ```bash
   curl -sH "Accept: application/vnd.github+json" \
     "https://api.github.com/repos/<owner>/<repo>/releases?per_page=10"
   ```
   If it returns 404 the repo is private and the app's check WILL fail silently. Tell me before going further.

2. After the workflow lands a release, verify the release's tag matches `v<name>+<code>` exactly and an APK asset is attached.

3. The first install over an existing debug-signed APK will fail with "App not installed" / signature mismatch. This is expected. I'll uninstall once.

4. Downloads land in the app's external files dir, which doesn't require `WRITE_EXTERNAL_STORAGE`. DownloadManager handles the permission for the install URI grant.

5. Once installed: in OS Settings → Apps → Loop → Install unknown apps → toggle on, otherwise the install intent silently no-ops on Android 8+.

---

## Don't

- Don't use `/releases/latest` — it skips prereleases and returns 404 on private repos with no public releases.
- Don't bake a GitHub PAT into the app for private repos. Use a backend proxy if I insist on staying private.
- Don't suggest "make this auto-install silently" — sideload installs always show a system confirmation dialog. That's an OS guarantee short of device admin / system app status.

---

## Order of operations I want you to follow

1. Confirm prerequisites with me (repo slug, keystore status).
2. Scaffold the Gradle project + version catalog + manifest + signing config + CI workflow. **Pause and let me commit + verify CI produces a signed APK release before continuing.**
3. Build the counter UI with full gesture handling. This is the centerpiece — get it feeling right before moving on.
4. Wire Room + DataStore + ViewModel for persistence.
5. Add the PDF viewer (read-only, no annotation).
6. Add the toolbar + Settings screen.
7. Wire the Updater + launch check + Settings "Check for updates".
8. Final pass: edge-to-edge, keep-screen-on, dark theme polish.

After each numbered step, stop and summarize what you did so I can test before moving on.

---

## Reference: gesture timing constants

| Constant | Value |
|---|---|
| Tap debounce | 250ms |
| Long-press fire | 400ms |
| Decrement hint fade-in | 200ms |
| Long-press cancel on movement | 10px |
| Pull-down trigger | 80px |
| Flash duration | 150ms |
| Shake-on-zero duration | 200ms |
| Snackbar duration | 4000ms |

These are tuned defaults — expose them as private constants in `CounterArea.kt` so I can adjust without hunting.