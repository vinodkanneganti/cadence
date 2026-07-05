# Cadence

Learning-paced read-aloud reader. Opens a text-layer PDF and reads it aloud at a
learning-optimized cadence: a comfortable ~150 wpm baseline, complexity-adaptive
slowdown on dense passages, structural pauses for chunking, and periodic active-recall
prompts. On-device only.

Kotlin Multiplatform + Compose Multiplatform. Targets: Android · iOS · desktop (JVM/macOS).

## Spec
See [PRD-cadence-claude-code.md](PRD-cadence-claude-code.md) for the full product spec
and [CLAUDE.md](CLAUDE.md) for the architecture invariants.

## Build status
**Phase 1 — trustworthy core.** Real PDF extraction, native TTS on all three targets,
full Compose UI, bookmarks and session resume, and Learning Mode are all built and running.

### Run the desktop app (macOS)
```
./gradlew :shared:run
```

### Run the engine unit tests
```
./gradlew :shared:desktopTest
```

---

## Codebase

Almost all of the project lives inside the `:shared` Gradle module, which compiles to three
targets via Kotlin Multiplatform (the thin `:androidApp` module and the `iosApp` Xcode project
just host it — see below):

```
shared/src/
├── commonMain/        ← engine, UI, models, expect declarations
├── commonTest/        ← platform-agnostic unit tests
├── desktopMain/       ← JVM/macOS actuals (Apache PDFBox, macOS say, file bookmarks)
├── desktopTest/       ← integration tests that generate real PDFs in-memory via PDFBox
├── androidMain/       ← Android actuals (TextToSpeech, PdfBox-Android, file bookmarks)
└── iosMain/           ← iOS actuals (AVSpeechSynthesizer, PDFKit, Documents dir bookmarks)
```

The two hard rules that shape every file: **`core.pacing` is pure** (zero platform
imports), and **`Speaker` / `PdfExtractor` are the only platform boundaries** — no
platform speech or PDF API exists anywhere else.

---

### Common (`commonMain`)

Everything shared across all three targets. Divided into the core engine, the platform
boundary declarations, and the Compose UI.

#### Core engine (`core.pacing`)

The heart of the product. Pure Kotlin, stdlib only — no TTS, PDF, Compose, or platform
import may ever appear here.

| File | Role |
|---|---|
| `core/pacing/Constants.kt` | All numeric constants from the PRD: `BASELINE_WPM = 150`, `LEARNING_CAP_MULT = 1.5`, pause durations per boundary type, pause scale per mode, and three `DensityPreset` envelopes (DENSE / STANDARD / LIGHT). |
| `core/pacing/Complexity.kt` | Scores a sentence 0–1 using syllable density (45 %), word length (35 %), and token count (20 %). Includes the PRD-specified English syllable heuristic (`estimateSyllables`) and punctuation-stripping tokeniser. |
| `core/pacing/PacingEngine.kt` | `schedule(units, density, mode, basePaceOffset) → List<Step>`. Resolves the density preset (or derives it automatically from the document's median complexity), then maps each unit's complexity score to a `targetWpm` and a `pauseMsAfter`. In LEARNING mode the engine **refuses** to exceed `base × 1.5` regardless of user settings. The `basePaceOffset` parameter lets the user nudge ±25 wpm within the safe envelope. |

**How the wpm curve works:** complexity ≥ 0.5 maps linearly from `base` down to `min`;
complexity < 0.5 maps from `base` up to `cap`. The Learning Mode cap is the lesser of
`base × 1.5` and `base × density.capMult`.

#### Data models (`core/model`)

Plain data types; no logic.

| File | What it defines |
|---|---|
| `Boundary.kt` | `SENTENCE` / `PARAGRAPH` / `SECTION` — tags each reading unit and determines the structural pause that follows it. |
| `Mode.kt` | `LEARNING` (rate-capped, full pauses) vs `FREE` (relaxed cap, pauses scaled to 40 % of full length). |
| `Density.kt` | `AUTO` / `DENSE` / `STANDARD` / `LIGHT` — selects the wpm envelope. AUTO picks from the document's median complexity. |
| `Unit.kt` | Input to the engine: `text + boundary`. Named to match the PRD glossary; files using `() → kotlin.Unit` callbacks must not import it to avoid shadowing. |
| `Step.kt` | Output of the engine: `text + targetWpm + pauseMsAfter + boundary`. Consumed by the Player. |

#### PDF pipeline (`core/pdf`)

The pure, platform-agnostic side of PDF processing. The platform actuals convert PDF
bytes into positioned lines; everything below is shared.

| File | Role |
|---|---|
| `PdfExtractor.kt` | `expect class` — declares `suspend fun extract(bytes): ExtractResult`. Each platform provides its own `actual`. |
| `PdfModels.kt` | `PdfLine` (text + font height + y position + page), `BuildResult`, and `ExtractResult`. |
| `PdfStructure.kt` | Turns a list of `PdfLine`s into boundary-tagged `Unit`s. Heading detection: line height ≥ 1.25× the page median AND < 12 words → `SECTION`. Paragraph break: vertical gap > 1.6× the median line advance. Within a paragraph, each sentence is `SENTENCE` except the last which carries `PARAGRAPH`. Also exposes `firstUnitIndexForPage` for page-jump navigation. |
| `SentenceSplitter.kt` | Splits a prose block into sentences. Guards against false splits on decimals (`3.14`), abbreviations (`Dr.`, `e.g.`, `etc.`), and single initials (`J. Smith`). Errs toward under-splitting per the PRD. |

#### TTS boundary (`core/tts`)

| File | Role |
|---|---|
| `SpeechOutput.kt` | Interface the Player depends on: `speak(text, targetWpm, onDone, onError)`, `stop()`, `voices()`, `setVoice()`. Exists so tests can supply a fake without real audio. |
| `Speaker.kt` | `expect class Speaker() : SpeechOutput` — the sole TTS platform boundary. One sentence per `speak` call; the Player owns inter-unit pauses. |

#### Player (`core/player`)

`Player.kt` walks a `List<Step>` using Kotlin coroutines:
`speak → onDone → delay(pauseMsAfter) → next step`. Uses
`suspendCancellableCoroutine` to bridge the callback-based `Speaker.speak()` into
the coroutine world. Exposes `play()`, `pause()`, `toggle()`, `next()`, `prev()`,
`nextSection()`, and `seekTo(index)`. Fires `onUnitStart(index)` for UI
highlighting and `onSectionBoundary(count)` for the recall scheduler.

#### Recall scheduler (`core/recall`)

`RecallScheduler.kt` fires an active-recall prompt on whichever comes first: every
2 section boundaries or every 5 minutes of elapsed reading time. Pure and
clock-injectable (`nowMs: () → Long`) so it is fully unit-testable. The app wires
`onRecallDue` to pause the player and show the `RecallOverlay`; calling
`continueReading()` resets all counters.

#### Bookmarks (`core/bookmark`)

| File | Role |
|---|---|
| `Bookmark.kt` | `Bookmark` — the single automatic resume-point per document (`docId`, `unitIndex`, `updatedAt`, `filePath`). `UserBookmark` — a user-curated spot (label, snippet, page). `BookmarkStore` interface + `NoopBookmarkStore` fallback. |
| `UserBookmark.kt` | Data class for a named, user-added bookmark with a text snippet and page number. |
| `BookmarkCodec.kt` | Pure serializer for both record types: tab-separated lines, field-escaped for tabs and newlines. Testable independently of file I/O. |

#### UI — Compose Multiplatform (`ui/`)

All UI lives in `commonMain`; it composes on all three targets unchanged.

| File | Role |
|---|---|
| `Theme.kt` | `Palette` data class with semantic color slots. `LightPalette` / `DarkPalette` swap wholesale. `LocalPalette` is a `CompositionLocal` so all composables can read the active palette. `ReadingFont` enum (Serif / Sans). |
| `ReaderState.kt` | The view-model. Coordinates `PdfExtractor`, `PacingEngine`, `Player`, `RecallScheduler`, and `BookmarkStore`. All observable state is `mutableStateOf`. Handles `open()`, `togglePlay()`, `next/prev/nextSection()`, `jumpToPage()`, density/mode/pace nudge, voice selection, dark mode, font size, and bookmarks. Runs a 1 Hz coroutine ticker to update elapsed time, drive `recall.onTick()`, and checkpoint the resume bookmark every 5 s. |
| `ReaderScreen.kt` | Root screen composable. Switches between loading spinner, error message, empty state, scanned-PDF warning, and the full reading surface. Hosts the `TopBar` (doc title, Open button, ☆ bookmarks, Aa settings), a `LazyColumn` reading surface (auto-scrolls to the active sentence), and a `BottomBar` (transport controls, page counter, live wpm). Overlays `RecallOverlay` and popover panels on top. |
| `RecallOverlay.kt` | Full-screen dim + centred card: "Rest. Before you continue — what were the key points of the last section?" with a Continue button. Blocks the reading surface until dismissed. |
| `SettingsPanel.kt` | Narration + appearance controls in a top-right popover: light/dark toggle, reading font size slider, font family (Serif/Sans), density selector (AUTO/DENSE/STANDARD/LIGHT), mode toggle (LEARNING/FREE), base-pace slider (±25 wpm), and voice dropdown with a Preview button. |
| `BookmarksPanel.kt` | Add / list / jump to / delete user bookmarks in a top-right popover. |
| `EmptyState.kt` | First-run / no-document state with an "Open a PDF" call-to-action; also holds the scanned-PDF and error messages. |

#### App root (`App.kt`)

`App()` is the shared Compose entry point. It accepts platform-provided lambdas:
`pickPdf` (opens the OS file picker), `bookmarks` (the platform bookmark store),
`now` (wall-clock millis for bookmark ordering), and `initialPdf` (auto-reopens the
last session). Wires keyboard transport (Space / ← / →) and provides `LocalPalette`
via `CompositionLocalProvider`.

#### Tests (`commonTest`)

| Test file | What it covers |
|---|---|
| `ComplexityTest.kt` | Syllable estimation edge cases, tokenisation, complexity bounds [0,1], and monotonicity with density. |
| `PacingEngineTest.kt` | AC2 (wpm stays in [110, 225]), AC3 (dense < light), AC4 (pause ordering), AC10 (learning cap wins), monotonicity of targetWpm with complexity, per-density floor/ceiling. |
| `PlayerTest.kt` | Walk order, virtual-time pause accounting, pause/resume, `next()`, `nextSection()` — using a `FakeSpeech` stub. |
| `RecallSchedulerTest.kt` | Section-count trigger, elapsed-time trigger, `continueReading()` reset. |
| `PdfStructureTest.kt` | Heading detection, paragraph gap splitting, sentence boundary assignment. |
| `SentenceSplitterTest.kt` | Abbreviation guards, decimal guards, single-initial guards, under-splitting preference. |
| `BookmarkCodecTest.kt` | Round-trip encode/decode for both `Bookmark` and `UserBookmark`; tab/newline escape handling; upsert semantics. |

---

### Desktop (`desktopMain`)

JVM target, macOS only in v1. Entry point: `main.kt` opens a 1000 × 720 Compose
Desktop window and calls `App()`.

| File | Role |
|---|---|
| `main.kt` | `fun main() = application { Window(...) { App(...) } }`. Wires an AWT `FileDialog` as `pickPdf`, `FileBookmarkStore` as `bookmarks`, and `System.currentTimeMillis` as `now`. Auto-reopens the last bookmarked document on launch via `initialPdf`. |
| `core/tts/Speaker.desktop.kt` | TTS via the macOS `say` command (`ProcessBuilder("say", "-r", "$targetWpm", text)`). `targetWpm` passes straight through — `say -r` accepts exact wpm, so no calibration factor is needed. Uses `AtomicReference<Process?>` to cancel in-flight utterances safely. `voices()` parses `say -v ?`. Falls back gracefully if `say` is unavailable (non-macOS JVM). |
| `core/pdf/PdfExtractor.desktop.kt` | PDF extraction via **Apache PDFBox** (Apache-2.0). Subclasses `PDFTextStripper` to collect positioned lines (`text + fontSizeInPt + yDirAdj + pageNo`), then hands the list to the pure `PdfStructure.build()`. Runs on `Dispatchers.IO`. |
| `core/bookmark/FileBookmarkStore.desktop.kt` | Reads/writes two tab-separated files under `~/.cadence/` (`bookmarks.tsv` for auto resume-points, `user-bookmarks.tsv` for user-curated spots). Serialisation delegated to the pure `BookmarkCodec`. I/O failures degrade silently to "no bookmark". |

---

### Android (`androidMain`)

| File | Role |
|---|---|
| `AndroidApp.kt` | Singleton that holds the application `Context` needed by all Android actuals. Call `AndroidApp.init(ctx)` once from `MainActivity` before any UI is composed. Also initialises `PDFBoxResourceLoader`. |
| `core/tts/Speaker.android.kt` | TTS via Android `TextToSpeech`. Initialises asynchronously; a `speak()` call that arrives before init is held as a `Pending` request and fired on init. Rate mapped as `setSpeechRate(targetWpm / BASE_VOICE_WPM)`. Selects the Google TTS engine explicitly if installed (works around devices with no default engine set). Uses `QUEUE_FLUSH` so each sentence cancels the previous. |
| `core/pdf/PdfExtractor.android.kt` | PDF extraction via **PdfBox-Android** (Apache-2.0 port). Same thin design as the desktop actual: subclasses `PDFTextStripper` to collect positioned lines, then calls the pure `PdfStructure.build()`. Runs on `Dispatchers.IO`. |
| `core/bookmark/AndroidBookmarkStore.kt` | Bookmark store backed by two files under `context.filesDir`. Identical API to `FileBookmarkStore`; `BookmarkCodec` is shared. |

The Android app module (`:androidApp`) hosts a standard `MainActivity` that calls
`AndroidApp.init()`, creates an `AndroidBookmarkStore`, and calls `setContent { App(...) }`.
The `pickPdf` lambda there launches an `ActivityResultLauncher` for `ACTION_OPEN_DOCUMENT`.

---

### iOS (`iosMain`)

| File | Role |
|---|---|
| `MainViewController.kt` | Kotlin/Native entry point exported to Xcode as `fun MainViewController(): UIViewController`. Instantiates `App()` via `ComposeUIViewController`, wiring `IosBookmarkStore`, `CFAbsoluteTimeGetCurrent` for wall-clock millis, and `pickPdfIos()` as the document picker. |
| `PdfPicker.ios.kt` | Presents a `UIDocumentPickerViewController` (filtered to `UTTypePDF`) as a `suspendCancellableCoroutine`. Security-scoped resource access is handled correctly. Holds the delegate in a global to prevent it being garbage-collected while the sheet is open. |
| `core/tts/Speaker.ios.kt` | TTS via `AVSpeechSynthesizer`. Sets `AVAudioSession` category to `.playback` so speech is audible with the silent switch on. Rate mapping: `avRate = AVSpeechUtteranceDefaultSpeechRate × (targetWpm / BASE_VOICE_WPM)`, clamped to the platform min/max (the PRD "rate-is-not-wpm" trap). |
| `core/pdf/PdfExtractor.ios.kt` | PDF extraction via **PDFKit** (`PDFDocument`, `PDFPage.string`). PDFKit does not expose per-glyph font metrics without heavy interop, so heading detection is text-heuristic: a short line (< 12 words) that either does not end with terminal punctuation or starts with a digit is treated as a heading by assigning it a synthetic height of 20 pt (vs 12 pt for body text). The pure `PdfStructure.build()` then detects it as a `SECTION` via the height ratio check. Runs on `Dispatchers.Default`. |
| `core/bookmark/IosBookmarkStore.kt` | Bookmark store backed by two files in the app's Documents directory (`NSDocumentDirectory`), falling back to `NSTemporaryDirectory()`. Uses `NSString` Foundation interop for file reads/writes. Reuses `BookmarkCodec` for serialisation. |
