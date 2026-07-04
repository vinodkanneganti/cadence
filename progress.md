# Cadence — build progress log

A running record of how this project is being built from `PRD-cadence-claude-code.md`.
Every meaningful step, decision, and command is logged here. Newest phase at the bottom.

- **Repo:** https://github.com/vinodkanneganti/cadence (`main`)
- **Spec:** [PRD-cadence-claude-code.md](PRD-cadence-claude-code.md) · **Invariants:** [CLAUDE.md](CLAUDE.md)
- **Stack:** Kotlin Multiplatform + Compose Multiplatform · desktop-first for Phase 0

---

## Conventions

- Build in PRD task order: T0 → T1 (Phase 0 gate) → T2 → T3 → (T4,T5) → T6 → T7 → T8.
- Stop for human review at each phase gate (✋).
- `core.pacing` stays pure (stdlib only). All TTS/PDF go through the `Speaker` / `PdfExtractor` boundaries.
- This file is updated at every step.

---

## Session 1 — 2026-07-04 — Phase 0 (T0–T3)

Goal chosen with the user: build **through the Phase 0 gate**, **desktop JVM first**, after **checking the toolchain**.

### Step 0 — Read the PRD & scope the work

```bash
ls -la /Users/bhavani/Developer/projects/cadence      # only PRD-cadence-claude-code.md present
```

Read the full PRD. Confirmed scope, frozen constraints, pacing algorithm, acceptance criteria, and task breakdown.

**Decisions (via clarifying questions):**
- Scope this session: through the Phase 0 gate.
- First target: desktop JVM only (Android/iOS deferred to Phase 1).
- Toolchain: unknown → probe before scaffolding.

### Step 1 — Toolchain probe

```bash
java -version              # OpenJDK 21.0.11 (Temurin)  ✅
javac -version             # javac 21.0.11              ✅
echo "$JAVA_HOME"          # (unset)
gradle -version            # Gradle 9.5.1               ✅ (wrapper pinned separately below)
kotlin -version            # not installed (fine; Gradle brings the compiler)
which say                  # /usr/bin/say               ✅ desktop TTS
git --version              # git 2.54.0                 ✅
echo "$ANDROID_HOME"       # unset; ~/Library/Android/sdk absent  ❌ (only needed in Phase 1)
xcodebuild -version        # Xcode 26.6                 ✅ (iOS, Phase 1)
which brew                 # /opt/homebrew/bin/brew     ✅
```

**Conclusion:** everything the desktop-first Phase 0 needs is present. Android SDK is the only gap and isn't needed until Phase 1.

### Step 2 — T0: scaffold the KMP project

```bash
git init -q               # folder was not a git repo
```

Created project files:
- `settings.gradle.kts` — root `cadence`, includes `:shared`, desktop-first (Android/iOS modules added in Phase 1).
- `gradle/libs.versions.toml` — version catalog: Kotlin 2.1.21, Compose MP 1.8.2 (coroutines added later).
- `build.gradle.kts` (root) — plugins declared `apply false`.
- `gradle.properties` — JVM args, caching on, config-cache off.
- `shared/build.gradle.kts` — KMP module, `jvm("desktop")` target only, Compose deps, desktop application entry `studio.sparkcube.cadence.MainKt`, DMG distribution.
- `CLAUDE.md` — architecture invariants seeded verbatim from PRD Appendix A + build-status note.
- `README.md`, `.gitignore` (ignores `build/`, `.gradle/`, `.idea/`, etc.).

Created source tree under `shared/src/`:
- `commonMain/…/core/model/` — `Boundary`, `Unit`, `Step`, `Density`, `Mode`.
- `commonMain/…/App.kt` — placeholder Compose shell (later replaced with the demo).
- `desktopMain/…/main.kt` — Compose desktop window entry point.

**Design note:** the model class is named `Unit` to match the PRD glossary. Files with `() -> Unit` callbacks (Speaker/Player) deliberately do **not** import it, to avoid shadowing `kotlin.Unit`.

### Step 3 — T1: pacing engine, test-first (Phase 0 gate)

Implemented `core.pacing` exactly per the PRD §Pacing Algorithm — stdlib only:
- `Constants.kt` — `PacingConstants`, `DensityPreset` + `DENSITY_PRESETS`, `RecallConfig`.
- `Complexity.kt` — `tokenize`, `estimateSyllables` (English heuristic), `complexityOf`.
- `PacingEngine.kt` — `schedule(units, density, mode)`, `targetWpm(...)`, `autoDensity(...)`.

Tests written from the AC table + T1 seeds:
- `PacingEngineTest.kt` — AC2/AC3/AC4/AC10, T1 dense/light seed bands, monotonicity, per-density floor/ceiling. (10 tests)
- `ComplexityTest.kt` — syllable edges, tokenize, bounds, density ordering. (4 tests)

Generated the Gradle wrapper (pinned for Kotlin 2.1 / Compose 1.8 compatibility) and ran the tests:

```bash
gradle wrapper --gradle-version 8.11.1 --distribution-type bin   # BUILD SUCCESSFUL
./gradlew :shared:desktopTest --console=plain                    # BUILD SUCCESSFUL
# verified counts from JUnit XML:
grep -h -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' \
  shared/build/test-results/desktopTest/*.xml
# => tests=10 failures=0 (PacingEngine), tests=4 failures=0 (Complexity)
```

**Result:** 14 tests green. Engine proven.

**Finding (flagged for review):** the PRD §6 golden-path example wpm numbers do not match its own
formula. The low-complexity branch rises hard toward the LEARNING cap (225 for STANDARD), so easy
sentences read ~211 wpm where the example shows 170. Implemented the literal algorithm per
"implement exactly — do not improvise"; did not silently change it.

### Step 4 — T2: Speaker boundary + desktop `say` actual

- `commonMain/…/core/tts/Speaker.kt` — `expect class Speaker() : SpeechOutput` (the sole TTS boundary).
- `commonMain/…/core/tts/SpeechOutput.kt` — interface introduced so the Player is testable with a fake
  while `Speaker` remains the only real speech path.
- `desktopMain/…/core/tts/Speaker.desktop.kt` — actual using `ProcessBuilder("say","-r",wpm,text)`,
  non-blocking with `onDone`/`onError`, cancel-safe `stop()`, and voice listing via `say -v '?'`.

Verified `say -r` honors the engine's wpm without playing audio (rendered to file, compared durations):

```bash
d=$(mktemp -d); cd "$d"
say -r 110 -o slow.aiff "Backpropagation computes gradients via the chain rule."
say -r 220 -o fast.aiff "Backpropagation computes gradients via the chain rule."
afinfo slow.aiff   # estimated duration: 3.534875 sec  (110 wpm)
afinfo fast.aiff   # estimated duration: 2.519229 sec  (220 wpm)
say -v '?' | wc -l # 184 voices installed
```

**Result:** rate maps through correctly (slower wpm → longer clip), monotonic.

### Step 5 — T3: Player scheduler + Phase 0 demo

- `commonMain/…/core/player/Player.kt` — walks `List<Step>` (speak → onDone → delay `pauseMsAfter` → next),
  emits `onUnitStart` / `onSectionBoundary`, exposes `play/pause/toggle/next/prev/nextSection`.
  Uses coroutines; speech wrapped via `suspendCancellableCoroutine`.
- `commonMain/…/App.kt` — replaced placeholder with the demo: paper-toned reading card, active-sentence
  highlight (AC6), per-step wpm/pause readout, transport buttons, and space/←/→ keyboard control (AC7).

Build/dependency changes:
- Added `coroutines = 1.9.0` to the catalog: `kotlinx-coroutines-core` (commonMain),
  `kotlinx-coroutines-swing` (desktopMain), `kotlinx-coroutines-test` (commonTest).
- Added Kotlin compiler flag `-Xexpect-actual-classes` to silence the expect/actual Beta advisory.

```bash
./gradlew :shared:compileKotlinDesktop --console=plain   # BUILD SUCCESSFUL (only Beta warning, since silenced)
```

`PlayerTest.kt` written with a `FakeSpeech` (`SpeechOutput`) driven under `runTest` virtual time:
order, section counting, pause-time honoring, pause/stop, next, nextSection. (5 tests)

Test iteration:

```bash
./gradlew :shared:desktopTest --console=plain
# 1) compile error: Unresolved reference 'currentTime'  -> fixed to testScheduler.currentTime
./gradlew :shared:desktopTest --console=plain
# 2) 2 failures: UncompletedCoroutinesError in the two skip tests (fake never completes the held
#    utterance -> leaked coroutine). Fixed by calling player.pause() at end of those tests.
./gradlew :shared:desktopTest --console=plain --rerun-tasks   # BUILD SUCCESSFUL
grep -h -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' \
  shared/build/test-results/desktopTest/*.xml
# => Complexity tests=4/0, Player tests=5/0, PacingEngine tests=10/0
```

**Result:** 19 tests green.

Startup smoke check (launch the desktop app, confirm no exceptions, then stop):

```bash
./gradlew :shared:run --console=plain > /tmp/cadence_run.log 2>&1 &   # window came up, reached ":shared:run"
grep -iE 'exception|caused by' /tmp/cadence_run.log                  # none
pkill -f 'studio.sparkcube.cadence.MainKt'; pkill -f 'shared:run'    # stopped
```

**Result:** app boots cleanly. **Phase 0 build complete — ✋ awaiting human feel-check** (`./gradlew :shared:run`, press Play/Space).

### Step 6 — Commit & push

```bash
git add -A
git commit -F <message>          # 0981b8a  "Phase 0: scaffold Cadence KMP project and prove the pacing engine"  (30 files)
git remote add origin https://github.com/vinodkanneganti/cadence.git
git push -u origin main          # [new branch] main -> main
# verified remote HEAD sha == 0981b8ac... via api.github.com
```

Authenticated via the existing macOS keychain HTTPS credential (no `gh login` needed).

### Status after Session 1

| Task | State |
|---|---|
| T0 scaffold | ✅ desktop target builds & launches |
| T1 pacing engine (Phase 0 gate) | ✅ 14 tests, pure, exact-to-spec |
| T2 Speaker (desktop `say`) | ✅ rate mapping verified |
| T3 Player + demo | ✅ 5 tests; app runs |
| **Phase 0 gate** | **✋ built; awaiting human audible review** |

**Open decisions for the human:**
1. Golden-path pacing slope — keep the fast easy-sentence pace (literal formula) or soften the low-complexity slope?

**Next (Phase 1, on go):** T4 `PdfExtractor` → T5 `RecallScheduler` → T6/T7 UI → Android/iOS `Speaker` actuals (needs Android SDK + AVSpeech rate mapping).

---

## Session 2 — 2026-07-04 — Voice selection in the demo

The Phase 0 demo used macOS's default `say` voice (female, e.g. Samantha). Added a voice picker
so the voice can be changed. This is R10 (voice controls) brought forward into the demo; the
`Speaker` boundary already exposed `voices()` / `setVoice(id)`, so this was UI-only.

**Changes (`App.kt`):**
- Load installed voices on start via `speaker.voices()`, sorted English-first then by name.
- Added a **Voice** dropdown (`DropdownMenu`) listing `name · locale`; selecting one calls
  `speaker.setVoice(id)` so subsequent utterances use it.
- Added a **Preview** button that speaks a short sample in the selected voice. Preview/selection
  first call `player.pause()` so the player's in-flight utterance isn't orphaned by the preview's
  internal `stop()` (avoids a hung coroutine).
- Shows the installed-voice count.

```bash
./gradlew :shared:compileKotlinDesktop --console=plain   # BUILD SUCCESSFUL
# smoke launch:
./gradlew :shared:run --console=plain > /tmp/cadence_run2.log 2>&1 &
grep -iE 'exception|caused by' /tmp/cadence_run2.log      # none
pkill -f 'studio.sparkcube.cadence.MainKt'                # stopped
```

**Result:** app launches with the picker; no exceptions. 184 voices available on this Mac.

**How to change the voice (for the user):** run `./gradlew :shared:run`, click the **Voice**
button, pick a voice (e.g. *Alex · en_US* male, *Daniel · en_GB*), and it auto-previews. Press
**Play** to hear the document in that voice. Note: on recent macOS many high-quality voices only
appear after downloading them in **System Settings → Accessibility → Spoken Content → System
Voice → Manage Voices**; once downloaded they show up in this list automatically.

**Note / limitation:** the picker lists real installed voices only; there's no "reset to system
default" item yet (would need a small `Speaker.clearVoice()` addition to the boundary). Deferred.

---

## Session 3 — 2026-07-04 — Phase 1 begins: T4 PdfExtractor

Moving into Phase 1 (Trustworthy core). Still desktop-first: Android SDK isn't installed, so the
Android/iOS `PdfExtractor`/`Speaker` actuals are deferred; all Phase-1 logic is built and verified
on the desktop target.

**Design:** keep the hard-to-test PDF parsing thin in the platform actual; put the real logic in
pure, unit-tested `commonMain` — same philosophy as the pacing engine.

**New files:**
- `commonMain/…/core/pdf/PdfModels.kt` — `PdfLine(text, height, y)`, `ExtractResult(units, hasTextLayer)`.
- `commonMain/…/core/pdf/SentenceSplitter.kt` — pure sentence splitter with decimal / abbreviation
  ("Dr.", "e.g.", "i.e.", "etc.") / single-initial ("J. Smith") guards; errs toward under-splitting.
- `commonMain/…/core/pdf/PdfStructure.kt` — pure structure builder: heading→SECTION (height ≥ 1.25×
  page median AND < 12 words), paragraph break on vertical gap > 1.6× median line advance, last
  sentence of a paragraph → PARAGRAPH else SENTENCE.
- `commonMain/…/core/pdf/PdfExtractor.kt` — `expect class PdfExtractor { suspend fun extract(bytes) }`.
- `desktopMain/…/core/pdf/PdfExtractor.desktop.kt` — Apache PDFBox actual: a `PDFTextStripper`
  subclass collects positioned lines (font-size + y), then hands off to the pure builder. Empty text
  → `hasTextLayer = false`.

**Dependency:** added Apache PDFBox 2.0.32 (Apache-2.0) to `desktopMain` + the version catalog.

**Tests (pure, commonTest):**
- `SentenceSplitterTest` (7) — decimals, abbreviations, e.g., single initials, ?/!, whole/empty.
- `PdfStructureTest` (6) — tall-short→SECTION, tall-long≠SECTION, paragraph boundary, big-gap split,
  3-heading ordering/count, empty input.

**Test (integration, desktopTest):**
- `PdfExtractorDesktopTest` (2) — generates real PDFs in-memory with PDFBox and runs the actual
  extractor: a structured text PDF (**AC1** — ordered, ≥3 SECTION, sentence-segmented) and an
  image-only PDF (**AC9** — `hasTextLayer=false`, no units).

```bash
./gradlew :shared:desktopTest --console=plain
# first run: 2 failures in PdfStructureTest — the 2–3 line fixtures skewed the *median* height/gap
# (an even-length-median artifact on tiny inputs, not a logic bug; the real-PDF test passed).
# Fixed by making those fixtures body-dominated (realistic pages). Re-ran:
# => 34 tests green: engine 10, complexity 4, player 5, sentence 7, structure 6, pdf-integration 2
```

**Result:** T4 complete. AC1 + AC9 verified against real PDFBox output.

**v1 limitations (documented):** multi-column/footnote order is best-effort y-then-x; paragraphs may
merge across page boundaries (y resets per page). Matches the PRD's stated scope.

---

## Session 4 — 2026-07-04 — T5 RecallScheduler

Pure, clock-injectable scheduler for active-recall prompts.

**New file:** `commonMain/…/core/recall/RecallScheduler.kt`
- Fires `onRecallDue` on **2 sections OR 5 minutes, whichever first** (defaults from `RecallConfig`).
- `onSectionBoundary()` counts sections; `onTick()` checks the elapsed-time trigger (driven by the
  app, e.g. per unit start or a 1 Hz ticker); `nowMs` is injected for testability.
- Once due, further triggers are ignored until `continueReading()` resets counters + the time anchor.

**Tests (pure, commonTest):** `RecallSchedulerTest` (5) — fires after 2 sections (AC5 logic), fires
after 5 min, whichever-first with no double-fire, continue resets and can re-fire, time trigger
counts from the last continue.

```bash
./gradlew :shared:desktopTest --console=plain   # BUILD SUCCESSFUL
# => 39 tests green (engine 10, complexity 4, player 5, sentence 7, structure 6, pdf 2, recall 5)
```

**Result:** T5 scheduler logic complete + tested. The end-to-end AC5 behaviour (pause playback +
show the recall overlay + resume on Continue) is wired into the UI in T6/T7.

---

## Session 5 — 2026-07-04 — T6/T7 reading surface + controls (Phase 1 UI)

Wired everything into a real reader: open a PDF, follow along, controls, and the recall overlay.

**Engine addition (R10 base-pace):** `PacingEngine.schedule(..., basePaceOffset = 0)` shifts the
narration baseline, but the safety **cap stays anchored to the ORIGINAL density baseline**, so the
Learning cap still wins (AC10). New test `basePaceOffset_liftsRateButCapStillWins`.

**New commonMain UI (Compose):**
- `ui/Theme.kt` — paper-toned palette.
- `ui/ReaderState.kt` — state holder coordinating PacingEngine + Player + PdfExtractor +
  RecallScheduler; observable Compose state (units/steps/activeIndex/playing/section/elapsed/
  density/mode/basePace/voices/recallDue/error). `PickedPdf(name, bytes)`. Elapsed + recall timing
  use `kotlin.time.TimeSource.Monotonic` (reading time); a 1 Hz ticker drives `recall.onTick()`.
- `ui/ReaderScreen.kt` — header (doc name, %, Open), reading surface (`LazyColumn`, active-sentence
  highlight, auto-scroll, paragraph/section pause markers), plus empty/scanned/error/loading states.
- `ui/RecallOverlay.kt` — "Rest / what were the key points…" modal, Continue resumes (**AC5** e2e).
- `ui/EmptyState.kt` — first-run, scanned-PDF message (**AC9** e2e / R12), and error states.
- `ui/TransportBar.kt` — prev/play-pause/next/section, live wpm + elapsed + section + progress.
- `ui/ControlRail.kt` — density (AUTO/DENSE/STANDARD/LIGHT), mode (LEARNING/FREE), base-pace slider
  ("Learning cap active" hint), voice dropdown + preview.
- `App.kt` — hosts `ReaderScreen`, wires keyboard (space/←/→, **R7**), takes a `pickPdf` callback.
- `desktopMain/main.kt` — supplies `pickPdf` via an AWT `FileDialog` (shown on the EDT, bytes read
  on IO); larger default window.

**Fix:** JVM signature clash — `var density` auto-generates `setDensity`, which collided with a
`fun setDensity`. Renamed the mutators to `selectDensity` / `selectMode` / `nudgeBasePace`.

**Sample doc:** added `samples/cadence-sample.pdf` — a structured 3-section ML primer generated via
PDFBox (headings at 20pt so section detection + recall actually fire). A first attempt with
`cupsfilter` produced a uniform-font PDF (SECTION=0), so it was replaced with the PDFBox version;
the one-shot generator was then removed so the test suite doesn't write repo files.

```bash
./gradlew :shared:compileKotlinDesktop --console=plain   # fixed setter clash → BUILD SUCCESSFUL
./gradlew :shared:desktopTest --console=plain            # 40 tests, 0 failures
./gradlew :shared:run … &                                # full reader UI launches, no exceptions
grep -rniE 'java\.net|http|URL\(|Socket|okhttp|ktor' shared/src/{commonMain,desktopMain}  # none → AC8 holds
```

**Result:** T6/T7 complete on desktop. All of Phase 1's logic is built and verified; AC5/AC6/AC7/
AC8/AC9 covered end-to-end on the desktop target.

**How to try it:** `./gradlew :shared:run` → **Open PDF** → pick `samples/cadence-sample.pdf` (or any
text PDF) → **Play**. Change Density/Mode/Base-pace/Voice live; after 2 sections the recall overlay
appears; **Continue** resumes.

### Phase 1 status

| Task | State |
|---|---|
| T4 PdfExtractor | ✅ AC1 + AC9 (real PDFs) |
| T5 RecallScheduler | ✅ 5 tests |
| T6/T7 Reading UI + controls | ✅ desktop; AC5/6/7 e2e |
| On-device (AC8) | ✅ no networking in sources |
| Android/iOS `Speaker`/`PdfExtractor` actuals | ⏳ deferred — needs Android SDK + device/sim |
| **Phase 1 gate** | **✋ built on desktop; awaiting human feel-check + TestFlight/dmg decision** |

**Next:** Phase 1 remainder needs Android SDK install (+ iOS on device) for the mobile actuals and
the AVSpeech rate-mapping trap. Then Phase 2 (neural voices, pace ribbon T8, adaptation ramp).

---

## Session 6 — 2026-07-04 — Tested together + two user-requested features (T9)

Ran the app together (`./gradlew :shared:run`); user opened a real PDF and it read aloud cleanly —
no exceptions in the app log. User then requested two features (both extend PRD R11):

### T9a — Jump to page
- `PdfLine.page` + PDFBox `currentPageNo` capture; `ExtractResult.pages` + `pageCount`.
- `PdfStructure.build` now returns `BuildResult(units, pages)` (page per unit) + pure
  `firstUnitIndexForPage(pages, page)` helper.
- `Player.seekTo(index)` (move playhead without auto-playing).
- `ReaderState.jumpToPage(page)` + `currentPage`; header shows "Page X / N" with a numeric
  field + Go (also fires on Enter).

### T9b — Resume bookmarks (persist + auto-resume on restart)
- `core/bookmark/`: `Bookmark`, `BookmarkStore` interface, `NoopBookmarkStore`, and a pure
  `BookmarkCodec` (tab-separated encode/decode with escaping + upsert). On-device only (R8).
- Desktop `FileBookmarkStore` → `~/.cadence/bookmarks.tsv` (best-effort; I/O failure degrades to
  "no bookmark").
- `PickedPdf.path`; `ReaderState` saves position (on pause, skip, jump, and a 5 s checkpoint while
  playing) and restores it when the same doc is reopened; shows a "Resumed at page X" banner.
- `main.kt`: on launch, `loadLast()` reopens the most recent document at its saved position;
  `App` gained `bookmarks` / `now` / `initialPdf` params (defaults keep it testable).

**Tests:** `BookmarkCodecTest` (5) + `PdfStructureTest` page tests (tracksPagePerUnit,
firstUnitIndexForPage). Two first-run failures were the same even-length-median artifact on
tiny/50-50 fixtures — fixed by making them body-dominated.

```bash
./gradlew :shared:compileKotlinDesktop   # BUILD SUCCESSFUL
./gradlew :shared:desktopTest            # 47 tests, 0 failures
```

**Result:** both features built + tested. Persistence file: `~/.cadence/bookmarks.tsv`.

Tested together: user opened `~/Desktop/slc-2up.pdf`, read to unit 45, closed; bookmark persisted
to `~/.cadence/bookmarks.tsv`; relaunch auto-reopened the doc at unit 45 (0 exceptions). Noted that
`slc-2up.pdf` is a 2-up layout → v1 best-effort ordering (documented limitation).

---

## Session 7 — 2026-07-04 — T10 user bookmark list

User-curated bookmarks (distinct from the single automatic resume point): add / list / jump / delete
important spots, persisted per document. On-device (R8).

- `core/bookmark/UserBookmark.kt` — `id, docId, label, snippet, unitIndex, page, createdAt`.
- `BookmarkStore` extended: `listBookmarks(docId)`, `addBookmark`, `removeBookmark`; `NoopBookmarkStore`
  no-ops them. `BookmarkCodec.encodeUser/decodeUser` (tab-separated + escaping).
- Desktop `FileBookmarkStore` keeps user bookmarks in a **separate** file
  `~/.cadence/user-bookmarks.tsv` (multiple per doc), leaving the resume file untouched.
- `ReaderState`: `bookmarkList` state; `addBookmark(label)` (blank label → text snippet → "Page N");
  `jumpToBookmark`, `removeBookmark`; list refreshes on open + mutations.
- `ui/BookmarkBar.kt` — a "Bookmarks" row under the header: optional name field + **＋ Add here**, then
  scrollable chips ("p{page} · {label}") — click a chip to jump, ✕ to delete.

**Tests:** `BookmarkCodecTest` +2 (user round-trip with tabs/newlines; skip malformed). 49 green.

```bash
./gradlew :shared:desktopTest   # 49 tests, 0 failures
```
