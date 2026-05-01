# CLAUDE.md

Durable instructions for Claude Code sessions in this repo.

## Branching & deployment

- **Always commit and push directly to `main`.** Do NOT use feature branches.
  CI on `main` builds a signed release APK and publishes a GitHub release on
  every push (see `.github/workflows/build.yml`). The in-app auto-updater
  fetches that release. Anything that doesn't land on `main` cannot be
  tested by the user.
- After every change the user asks for: commit AND push to `main`. If a local
  feature branch already exists for the session, also push that branch so
  stop-hooks don't complain — but `main` is the canonical target.
- `versionCode` = `git rev-list --count HEAD`, `versionName` = `"0.1.${gitCount}"`.
  Each commit on `main` is therefore a new release. Keep commits self-contained.
- **Multiple Claude Code sessions push to this repo concurrently.** Expect
  `main` to advance under your feet between when you start work and when
  you push. Workflow:
  1. Before pushing, run `git fetch origin main`.
  2. If `origin/main` is ahead, rebase your local commits onto it
     (`git rebase origin/main`). Do NOT merge — keep history linear.
  3. After rebasing, recompute the changelog `versionCode` to match the
     new tip + 1 (since `git rev-list --count HEAD` includes the upstream
     commits you just incorporated).
  4. If upstream commits added user-visible changes without a changelog
     entry, back-fill those entries before you push so the in-app
     "What's new" stays correct.
  5. Push to `main` normally; if it still rejects, fetch + rebase again.
- Never `--force` push to `main`, never rewrite published commits on
  `main`, never bypass hooks (`--no-verify`). Force-push to your local
  feature/tracking branch is fine if needed after rebasing.

## Persistent storage — DO NOT CORRUPT

The app stores real user data on device. **Code changes must never silently
delete or corrupt this data.** The user's notes, counts, project state, and
PDF annotations have to survive every release.

Persistence surfaces:

1. **Room database** — `android/app/src/main/java/com/shadaeiou/stitchcounter/data/db/AppDatabase.kt`
   File: `stitch-counter.db` in app's internal storage.
   Tables: `projects` (counts, label, current page, pdf path, JSON notes,
   knit pattern), `history` (counter undo entries), `annotations` (per-page
   pen strokes as JSON).

2. **DataStore preferences** — `android/app/src/main/java/com/shadaeiou/stitchcounter/data/prefs/UserPrefs.kt`
   File: `user_prefs` (auto-update flag, volume keys flag, current project id,
   counter background ARGB).

3. **PDF files on disk** — `app.filesDir/pdfs/<timestamp>.pdf` (copied from the
   user-picked Uri at upload time). `Project.pdfPath` references them.

### Rules for changes that touch storage

- **Never increase `AppDatabase.version` while leaving `fallbackToDestructiveMigration()`
  enabled for production.** That combination wipes the user's database. The
  builder currently uses `fallbackToDestructiveMigration()` as a holdover from
  early development. Before bumping the version again, write a real
  `Migration(from, to)` and add it via `.addMigrations(...)`. Remove
  `fallbackToDestructiveMigration()` once migrations are in place, or at minimum
  flag any version bump in the PR description so the user can opt in.
- **Adding a column?** Use `ALTER TABLE ADD COLUMN` in a migration with a
  default value. Do not rely on destructive fallback.
- **Renaming or removing a column?** Multi-step: copy data into a new column,
  ship migration, only remove the old column in a later release once the user
  has updated.
- **Notes are stored as JSON in `Project.notes`** (see `data/notes/NoteItem.kt`).
  `parseNotes` already falls back to wrapping legacy plain-text in a single
  `NoteItem`. Preserve that fallback path forever — it's the migration story
  for users who never installed JSON-aware builds.
- **Strokes are stored as JSON in `annotations.strokesJson`** (see
  `ui/pdf/Stroke.kt` / `strokesFromJson`). Use `kotlinx.serialization` with
  explicit `ListSerializer` (we hit a real bug without it; see commit
  `0ab8db9`). If you change the `Stroke` schema, keep the deserializer
  backward compatible (`ignoreUnknownKeys`, optional fields with defaults).
- **PDF files** referenced by `Project.pdfPath`: don't delete the file from
  disk just because `setPdfPath(null)` was called from the UI. Today
  `ProjectRepository.setPdf` only nulls the path; if you ever add cleanup,
  make sure it doesn't delete a file that another project still references.
- **Never wipe `_redoStack`, `_pinnedNotes`, or any other in-memory state in a
  way that loses persisted data.** ViewModel state can be cleared; database
  state cannot.
- When in doubt, **ask the user before shipping anything that bumps the DB
  version, deletes a file, or changes a serialized format**.

## Changelog

`update/Changelog.kt` is the source of truth for "What's new" in Settings.
Whenever you ship a user-visible change:

1. Prepend a new `ChangelogEntry(versionCode = N, versionName = "0.1.N", notes = listOf(...))`
   where `N` is the versionCode that this commit will produce — i.e. the
   current `git rev-list --count HEAD` + 1 (since the commit you're about to
   make is one ahead of HEAD).
2. Use short, plain-language bullets. The user reads this in Settings.
3. Internal-only changes (refactors, build fixes) can either be omitted or
   labeled clearly so the user understands they're not new features.

The Settings screen automatically pulls the entry whose `versionCode`
matches `BuildConfig.VERSION_CODE`, so getting the number right is what
makes the "What's new" card render correctly on the running build.

## Project layout

```
android/                      Gradle Android project root
  app/src/main/java/com/shadaeiou/stitchcounter/
    MainActivity.kt           Sets up the screen state machine (Main / Notes / Settings)
    StitchCounterApp.kt       App class, exposes haptics + repository
    data/
      db/                     Room entities, DAOs, AppDatabase
      notes/NoteItem.kt       Serializable note model + JSON parse/encode
      prefs/UserPrefs.kt      DataStore-backed preferences
      repo/ProjectRepository.kt  Single repository façade
    ui/
      MainScreen.kt           Counter + PDF split, pen panel, dialogs
      counter/                CounterArea, HistorySheet
      pdf/                    PdfViewer, Stroke serialization, PdfToolbar
      notes/NotesScreen.kt    Full-screen notes UI
      settings/SettingsScreen.kt
      toolbar/Toolbar.kt      Bottom toolbar
      theme/                  Colors, type, theme
    update/
      Changelog.kt            App changelog (above)
      Updater.kt              GitHub release download + install
.github/workflows/build.yml   CI: assembleRelease + GH release on push to main
```

## Compose / Android conventions

- Compose BOM `2024.12.01` (Kotlin 2.1.0, AGP 8.7.3, JDK 17). Avoid using
  experimental APIs that aren't in this BOM.
- Material 3 only. Don't pull in `androidx.compose.material:material` 1.x.
- The release build uses signing config from env vars (`RELEASE_KEYSTORE_PATH`,
  `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`)
  populated in CI from secrets. Don't commit a keystore.

## Build verification

- The sandbox cannot fetch AGP/Compose deps, so `gradle compileDebugKotlin`
  fails locally. Don't keep retrying it. Push to `main` and rely on the
  CI build to surface compile errors.
- For pure-Kotlin or doc-only changes, you can still trace through the diff
  manually before committing.

## Behavioral guardrails

- **Counter UX is sacred.** The single-tap → +1 → click sound + white flash
  flow has been tuned by the user. Long-press → -1 → red flash. Pull-down →
  history sheet. Don't change those mappings without an explicit ask.
- **Pen strokes are anchored to PDF coordinates** (normalized 0..1 of the
  un-transformed bitmap), not screen coordinates. The `graphicsLayer`
  applied to the PDF + stroke layer must use `TransformOrigin(0f, 0f)` so
  the inverse mapping in `toPagePoint` stays exact.
- **Never re-add the label text box above the counter number.** The user
  removed it intentionally in build 14.
- **The Notes screen shows full text.** Pinned notes on the counter are
  capped (currently 80 chars) and auto-shrunk to fit. Never apply caps on
  the Notes screen.
- The user prefers concise, focused commit messages. Look at recent commits
  for the established voice.
