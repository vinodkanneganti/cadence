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
