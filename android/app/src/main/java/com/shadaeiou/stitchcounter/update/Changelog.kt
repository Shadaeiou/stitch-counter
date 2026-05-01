package com.shadaeiou.stitchcounter.update

/**
 * Human-readable changelog. Each entry maps to the [versionCode] (which is the
 * git commit count at build time). When you add a new feature, prepend an
 * entry with the next versionCode and a short bulleted list of changes.
 */
data class ChangelogEntry(
    val versionCode: Int,
    val versionName: String,
    val notes: List<String>,
)

val Changelog: List<ChangelogEntry> = listOf(
    ChangelogEntry(
        versionCode = 27,
        versionName = "0.1.27",
        notes = listOf(
            "Fix release build: PatternFetcher photo-marker regex used invalid Kotlin string escapes (\\[, \\]). Switched the pattern to a raw string literal so compileReleaseKotlin no longer fails.",
        ),
    ),
    ChangelogEntry(
        versionCode = 26,
        versionName = "0.1.26",
        notes = listOf(
            "Removed the Eastern time clock from the counter screen.",
        ),
    ),
    ChangelogEntry(
        versionCode = 25,
        versionName = "0.1.25",
        notes = listOf(
            "Pattern import now shows photo thumbnails inline.",
            "Counter screen briefly displayed an Eastern time clock (removed in build 26).",
        ),
    ),
    ChangelogEntry(
        versionCode = 24,
        versionName = "0.1.24",
        notes = listOf(
            "Internal: documented the concurrent-session push workflow in CLAUDE.md (fetch + rebase before pushing to main, never force-push, back-fill changelog entries for upstream commits). No user-facing behavior change.",
        ),
    ),
    ChangelogEntry(
        versionCode = 23,
        versionName = "0.1.23",
        notes = listOf(
            "Internal: added a CLAUDE.md at the repo root capturing the working agreements (push to main, never corrupt persisted notes / counts / annotations, keep the changelog up to date, etc.). No user-facing behavior change.",
        ),
    ),
    ChangelogEntry(
        versionCode = 22,
        versionName = "0.1.22",
        notes = listOf(
            "Imported patterns: Save action moved to a floating action button.",
            "Imported patterns: comment / blog sections are stripped before display.",
        ),
    ),
    ChangelogEntry(
        versionCode = 21,
        versionName = "0.1.21",
        notes = listOf(
            "Internal merge commit (no user-facing changes).",
        ),
    ),
    ChangelogEntry(
        versionCode = 20,
        versionName = "0.1.20",
        notes = listOf(
            "New Pattern screen: import a knitting pattern by URL and view it inside the app.",
            "Pinch-to-zoom now works on the PDF even while a pen / eraser tool is selected.",
            "Eraser icon updated to a custom drawable.",
        ),
    ),
    ChangelogEntry(
        versionCode = 19,
        versionName = "0.1.19",
        notes = listOf(
            "PDF toolbar moved into the upper-right corner of the PDF area as a translucent overlay over the PDF.",
            "Toolbar now contains Upload PDF, Pen (long-press for color/thickness), Eraser, Invert colors, and Remove PDF.",
            "The Remove PDF button is gone from the page-nav row at the top.",
            "Toolbar is hidden whenever the PDF pane itself is hidden (no PDF loaded, or hidden via Hide PDF on the main toolbar).",
        ),
    ),
    ChangelogEntry(
        versionCode = 18,
        versionName = "0.1.18",
        notes = listOf(
            "New PDF-screen toolbar at the bottom of the PDF pane with Upload, Pen (long-press for color/thickness), Eraser, and Invert colors.",
            "Main bottom toolbar slimmed down to Hide/Show PDF, Notes, Lock, and Settings (plus Upload only when no PDF has been loaded yet).",
        ),
    ),
    ChangelogEntry(
        versionCode = 17,
        versionName = "0.1.17",
        notes = listOf(
            "Notes screen: tap a note to edit its text in a dialog. Long-press still pins / unpins.",
        ),
    ),
    ChangelogEntry(
        versionCode = 16,
        versionName = "0.1.16",
        notes = listOf(
            "Counter click is louder and more audible: ToneGenerator volume maxed (100/100) and switched to a louder PBX click tone with a longer duration so it carries over music playing in another app.",
        ),
    ),
    ChangelogEntry(
        versionCode = 15,
        versionName = "0.1.15",
        notes = listOf(
            "Add a What's new changelog under the build number in Settings.",
        ),
    ),
    ChangelogEntry(
        versionCode = 14,
        versionName = "0.1.14",
        notes = listOf(
            "Audible ToneGenerator click on counter increment (no longer relies on system touch-sound setting).",
            "Increment now flashes white; decrement still flashes red.",
            "Removed the label text box above the counter number.",
            "Pinned notes on the counter screen auto-scale to fit and cap at 80 characters with an ellipsis. Full text is preserved on the Notes screen.",
        ),
    ),
    ChangelogEntry(
        versionCode = 13,
        versionName = "0.1.13",
        notes = listOf(
            "Pen strokes now anchor to the PDF page so they stay put when you zoom or pan.",
            "Brighter, longer counter flash and click sound on every increment.",
            "Draggable divider between the counter and PDF panes.",
            "Toolbar toggle to hide the PDF temporarily without losing annotations.",
            "Label edit field auto-commits on focus loss, so the keyboard no longer reappears after navigating away.",
            "Full-screen Notes screen with multiple notes, pin/unpin (long-press), and pinned notes ribbon on the counter.",
            "Programmable knit/purl indicator next to the counter (Tune button to edit pattern).",
        ),
    ),
    ChangelogEntry(
        versionCode = 12,
        versionName = "0.1.12",
        notes = listOf(
            "Pen color and thickness panel now opens via long-press on the pen toolbar button (auto-hides on the next stroke).",
            "Undo / redo buttons appear on the PDF when the pen is selected.",
            "Pen thickness slider now goes up to 36 px.",
            "Fixed the History sheet close (X) button being pushed off-screen.",
        ),
    ),
    ChangelogEntry(
        versionCode = 11,
        versionName = "0.1.11",
        notes = listOf(
            "Pen color picker (red, black, blue) and thickness slider on the PDF.",
            "Strokes stored in PDF coordinates so they follow zoom and pan.",
            "More vibrant dark counter background palette.",
        ),
    ),
    ChangelogEntry(
        versionCode = 10,
        versionName = "0.1.10",
        notes = listOf(
            "Reset button on the counter actually fires now (was being eaten by the gesture box).",
            "Counter label tap fixed; label is larger.",
            "Counter number text forced to white on every background.",
            "Settings: choose a darkened counter background color.",
            "PDF viewer clips overflow so it can't paint over the counter.",
            "Remove PDF now asks for confirmation and the button has a visible border.",
        ),
    ),
    ChangelogEntry(
        versionCode = 9,
        versionName = "0.1.9",
        notes = listOf(
            "Small reset button on the counter screen.",
            "PDF viewer is hidden until a PDF is loaded; counter takes the full screen otherwise.",
            "Pen never writes in the counter section.",
            "New control to remove the loaded PDF.",
        ),
    ),
    ChangelogEntry(
        versionCode = 8,
        versionName = "0.1.8",
        notes = listOf(
            "Internal: explicit ListSerializer for stored strokes (compatibility fix).",
        ),
    ),
    ChangelogEntry(
        versionCode = 7,
        versionName = "0.1.7",
        notes = listOf(
            "Pen and eraser tools on the PDF viewer.",
            "Notes sheet for free-form pattern notes.",
            "Real haptics; keep-screen-on while the app is foregrounded.",
            "Volume key counter input (configurable in Settings).",
        ),
    ),
    ChangelogEntry(
        versionCode = 3,
        versionName = "0.1.3",
        notes = listOf(
            "Counter polish: history sheet, color flash, debounce.",
            "PDF fullscreen toggle (later replaced by the divider).",
        ),
    ),
    ChangelogEntry(
        versionCode = 1,
        versionName = "0.1.1",
        notes = listOf(
            "Initial Phase 1 scaffold: counter, project store, basic UI, CI.",
        ),
    ),
)
