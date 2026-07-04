# PRD: Cadence — learning-paced read-aloud reader
### Claude Code build spec — hand this to a Claude Code session and execute phase by phase

<!-- Working name "Cadence": reading at the right cadence for retention. -->

| Field | Value |
|---|---|
| **ID** | SPARK-CADENCE-01 |
| **Owner** | Vinod |
| **Status** | Approved for build |
| **Stack** | Kotlin Multiplatform + Compose Multiplatform |
| **Targets** | `androidApp` (Android) · `iosApp` (iOS) · `desktopApp` (JVM, runs on macOS) |
| **Repo** | `cadence` |
| **CLAUDE.md** | Authored in Phase 0 — seed content in Appendix A |
| **Priority** | P0 |

---

## How to drive this with Claude Code

Execute in phase order. **Do not skip the Phase 0 gate.** After each phase, stop and let the human review at the stated checkpoint before proceeding.

1. **Read this whole doc first**, then read `CLAUDE.md` once it exists.
2. **Phase 0** is test-first: implement the pacing engine in `commonMain` against the unit tests in §5/T1 *before any UI or platform code*. The engine is the product; everything else is plumbing.
3. Build **one vertical slice at a time** (the task list in §5 is already sliced to merge-sized diffs). Each task names its files and its `expect`/`actual` boundary.
4. Never call a platform TTS or PDF API outside the two adapter boundaries (`Speaker`, `PdfExtractor`). If you find yourself importing `AVSpeechSynthesizer` or `TextToSpeech` anywhere in `commonMain`, stop — that's a constraint violation.
5. Prefer real running verification over assertion: the Phase 0 gate is "a hardcoded sentence list reads aloud on desktop with audible, correctly-sized pauses."

---

## 0. Frozen Constraints — DO NOT VIOLATE

**Architecture invariants (these are the CLAUDE.md rules — see Appendix A):**
- The **pacing engine is pure Kotlin in `commonMain`** (`core.pacing`). Input = structured units; output = a timed schedule. It imports **no** TTS, PDF, Compose, or platform API. It is the only exhaustively unit-tested module.
- **TTS and PDF are the only two platform boundaries**, reached solely through `expect class Speaker` and `expect class PdfExtractor`. No platform speech/PDF call exists anywhere else.
- **Learning Mode speed is bounded by evidence, not preference.** Effective narration rate in Learning Mode is hard-capped at **1.5× the density baseline** (§3 R2). The engine must *refuse* to exceed it in Learning Mode regardless of user settings. Rationale: past ~1.5×, retention drops and long-term-memory encoding declines — it's a safety rail.
- **On-device only.** A document a user opens never leaves the device. Zero network calls carry document content. No accounts, no backend, no analytics in v1.

**Must follow existing pattern:**
- Project shape mirrors **PeekLUT**: shared `commonMain` core, thin `androidMain`/`iosMain`/`jvmMain` actuals, Compose Multiplatform UI in `commonMain`.

**Explicit non-goals (do NOT build):**
- No RSVP / word-flashing "speed reading" — it harms retention and contradicts the product thesis.
- No OCR of scanned/image-only PDFs (detect and warn instead).
- No EPUB/DOCX/mobi in v1 (PDF text-layer only).
- No cloud TTS voices in v1 (native voices only; cloud is Phase 2, opt-in).

---

## 1. Problem & Context

Dense PDFs get skimmed at ~238 wpm and forgotten; naive TTS either drones at one flat rate or gets pushed to 2×, where comprehension drops 22–35% and long-term encoding falls off. Cadence reads a PDF aloud at a **learning-optimized cadence**: a comfortable ~150 wpm baseline, complexity-adaptive slowdown on dense passages, structural pauses for chunking, and periodic **active-recall prompts** (retrieval practice — the real retention multiplier). Target user: a serious self-learner working through technical PDFs who wants to *retain*, not just consume. Evidence base: Brysbaert 2019 (reading-rate meta-analysis) and time-compression/retention studies (1.5× ≈ ceiling for retention).

---

## 2. Scope

**In scope:** open a local text-layer PDF → extract text with structure (paragraph/section) → read aloud via native TTS driven by the pacing engine → complexity-adaptive rate + structural pauses + active-recall prompts → follow-along sentence highlight → transport + settings controls. On-device.

**Non-goals:** RSVP; max-speed listening; OCR; EPUB/DOCX; cloud sync/accounts/library; cloud voices (v1).

**Out of scope (later):** neural voices (Phase 2), exported study highlights, cross-session spaced-repetition of recall prompts, multi-column/footnote-aware layout parsing.

---

## 3. Requirements

### P0 — must ship
- **R1. PDF text + structure extraction.** Text-layer PDF → ordered units, sentence-segmented, each tagged `SENTENCE | PARAGRAPH | SECTION`. Headings detected by relative font height + short line length. Scanned PDF → `hasTextLayer=false`.
- **R2. Learning-paced narration.** Read one sentence per TTS utterance; each sentence's rate and following pause come from the pacing engine. Learning Mode clamps effective rate to ≤ 1.5× the active density baseline.
- **R3. Complexity-adaptive slowdown.** Denser units read slower per the §Pacing Algorithm; lighter units may rise toward the cap.
- **R4. Structural pauses.** Pause after each unit, sized by boundary: `SECTION > PARAGRAPH > SENTENCE > 0`. Section boundary increments the section counter.
- **R5. Active-recall prompts.** Every **2 sections or 5 minutes (whichever first)**, pause playback and show a recall overlay; resume only on user action.
- **R6. Follow-along highlight.** The sentence currently spoken is highlighted and kept in view.
- **R7. Transport controls.** Play/pause, prev/next sentence, next section. Keyboard (desktop): space = play/pause, ←/→ = prev/next sentence.
- **R8. On-device only.** No network requests carry document text.

### P1 — should ship
- **R9. Density selector.** `AUTO | DENSE | STANDARD | LIGHT` shifts the baseline/cap/min envelope (§Pacing Algorithm). AUTO picks from the document's median complexity.
- **R10. Base-pace + voice controls.** Nudge baseline within safe bounds; pick among installed native voices.
- **R11. Progress + in-session resume.** Show % through document + current section; skips update the playhead.
- **R12. Scanned-PDF detection.** No text layer → clear message ("This PDF has no selectable text — OCR isn't supported yet"). Never fail silently.

### P2 — nice to have
- **R13. Pace ribbon.** Timeline of unit durations + upcoming pauses with a sweeping playhead (signature element).
- **R14. Adaptation ramp.** Optionally start ~15% slower and ease to baseline over the first ~90 seconds.

---

## Pacing Algorithm (implement exactly — do not improvise)

All in `core.pacing`. Pure functions, fully unit-tested.

**Constants**
```
BASELINE_WPM        = 150     // comfortable narration anchor (STANDARD)
MIN_WPM             = 110
LEARNING_CAP_MULT   = 1.5     // Learning Mode hard ceiling = baseline * 1.5
FREE_CAP_MULT       = 2.0     // non-learning mode ceiling
BASE_VOICE_WPM      = 175     // calibration: assumed wpm of a mobile voice at rate 1.0
RATE_MIN, RATE_MAX  = 0.4, 2.2

PAUSE_MS = { SENTENCE: 250, PARAGRAPH: 700, SECTION: 1500 }
PAUSE_SCALE = { LEARNING: 1.0, FREE: 0.4 }   // multiply PAUSE_MS by this

DENSITY = {                         // baselineWpm, capMult, minWpm
  DENSE:    { base: 130, capMult: 1.3, min: 100 },
  STANDARD: { base: 150, capMult: 1.5, min: 110 },
  LIGHT:    { base: 165, capMult: 1.6, min: 130 },
}
RECALL = { everyNSections: 2, everyMinutes: 5 }   // whichever fires first
```

**Complexity score** (per unit, 0..1):
```
words        = tokenize(text)                 // split on whitespace; strip punctuation
meanWordLen  = mean(len(w) for w in words)
syllPerWord  = mean(estimateSyllables(w) for w in words)
tokenCount   = words.size

wordLenNorm  = clamp((meanWordLen - 4.0) / (7.0 - 4.0), 0, 1)
syllNorm     = clamp((syllPerWord - 1.3) / (2.2 - 1.3), 0, 1)
lengthNorm   = clamp((tokenCount   - 8)   / (30  - 8),  0, 1)

complexity   = 0.45*syllNorm + 0.35*wordLenNorm + 0.20*lengthNorm
```

**estimateSyllables(word)** — English heuristic:
```
w = word.lowercase().filter(isLetter)
if w.isEmpty(): return 1
groups = count of maximal runs of [aeiouy] in w
if w.endsWith("e") and not w.endsWith("le"): groups -= 1   // silent e
return max(1, groups)
```

**Target wpm** for a unit, given the active density preset and mode:
```
base = density.base
cap  = if (mode == LEARNING) base * LEARNING_CAP_MULT
       else                  base * FREE_CAP_MULT          // never exceed density.capMult in LEARNING; see clamp
learningCap = base * density.capMult                        // per-density learning ceiling
if (mode == LEARNING) cap = min(cap, learningCap)

if (complexity >= 0.5)
    wpm = base - ((complexity - 0.5) / 0.5) * (base - density.min)   // base → min
else
    wpm = base + ((0.5 - complexity) / 0.5) * (cap - base)          // base → cap

targetWpm = clamp(wpm, density.min, cap)
```

**Rate conversion (mobile)**: `rate = clamp(targetWpm / BASE_VOICE_WPM, RATE_MIN, RATE_MAX)`
**Rate (desktop/macOS)**: pass `targetWpm` directly to `say -r <wpm>` (exact; no calibration needed).

**Schedule**: engine emits `List<Step>` where
`Step { text: String, targetWpm: Int, pauseMsAfter: Int, boundary: Boundary }`
and `pauseMsAfter = PAUSE_MS[boundary] * PAUSE_SCALE[mode]`.

---

## 4. Acceptance Criteria (each maps 1:1 to a test)

| # | Given | When | Then | Maps to |
|---|---|---|---|---|
| AC1 | text-layer PDF, 3 headings / 20 paragraphs | opened | units ordered, sentence-segmented, ≥3 tagged `SECTION` | R1 |
| AC2 | Learning Mode, STANDARD (base 150) | any unit scheduled | `targetWpm ∈ [110, 225]` | R2 |
| AC3 | dense unit vs light unit | both scheduled | `targetWpm(dense) < targetWpm(light)` | R3 |
| AC4 | units of each boundary type | schedule built | `pauseMsAfter(SECTION) > PARAGRAPH > SENTENCE > 0` | R4 |
| AC5 | recall = 2 sections | 2nd section boundary crossed | playback pauses, recall overlay shown, no TTS until continue | R5 |
| AC6 | playback active | a unit begins | exactly that unit carries the active-highlight state | R6 |
| AC7 | desktop, playback active | space pressed | toggles pause/resume within one unit boundary | R7 |
| AC8 | any document | full session | zero network requests carry document text | R8 |
| AC9 | image-only PDF | opened | "no selectable text / OCR unsupported" shown; no silent failure | R12 |
| AC10 | STANDARD, mode LEARNING, user sets base-pace to max | any unit | `targetWpm ≤ 225` still holds (cap wins over user) | R2 |

**Engine unit-test seeds (T1):** feed known sentences and assert exact `targetWpm` bands, e.g. a 26-word sentence of long technical words scores `complexity ≥ 0.7` → `targetWpm ≤ 130` (STANDARD); a 6-word plain sentence scores `complexity ≤ 0.25` → `targetWpm ≥ 190`.

---

## 5. Task Breakdown (diff-sized; build in this order)

**Order:** T0 → **T1 (Phase 0 gate)** → T2 → T3 → (T4, T5 parallel) → T6 → T7 → T8

### T0 — Scaffold KMP + Compose Multiplatform project
- Init the Compose Multiplatform template (Android + iOS + desktop JVM). Package root `studio.sparkcube.cadence`.
- Source sets: `commonMain` (engine, player, models, Compose UI, `expect` decls), `androidMain`, `iosMain`, `jvmMain`.
- Author `CLAUDE.md` from Appendix A. Author `PRD` link in README.
- **Done when:** all three targets build and launch an empty screen.

### T1 — Pacing engine (pure) — **PHASE 0 GATE, TEST-FIRST**
- **Files:** `core/pacing/PacingEngine.kt`, `core/pacing/Complexity.kt`, `core/model/Unit.kt`, `core/model/Step.kt`, `core/pacing/PacingEngineTest.kt`
- **Does:** implements the §Pacing Algorithm exactly. `fun schedule(units: List<Unit>, density: Density, mode: Mode): List<Step>`.
- **Constraint:** no imports outside kotlin stdlib. No Compose, no platform.
- **Done when:** AC2, AC3, AC4, AC10 + the T1 seed tests are green. **STOP for human review.**

### T2 — Speaker adapter (platform boundary)
- **Files:** `core/tts/Speaker.kt` (`expect class`), `androidMain/.../Speaker.kt`, `iosMain/.../Speaker.kt`, `jvmMain/.../Speaker.kt`
- **API:** `voices(): List<VoiceInfo>`, `setVoice(id)`, `speak(text, targetWpm, onDone, onError)`, `stop()`.
- **Actuals:** Android `TextToSpeech` + `UtteranceProgressListener`, `setSpeechRate(targetWpm/BASE_VOICE_WPM)`. iOS/macOS-native `AVSpeechSynthesizer` (see failure-modes for the AV rate mapping trap). Desktop JVM (macOS) `ProcessBuilder("say","-r","$targetWpm", text)`.
- **Rule:** **one sentence per utterance** (engine owns inter-unit pauses).
- **Done when:** a hardcoded 3-sentence list reads aloud on desktop with audible pauses.

### T3 — Player / scheduler
- **Files:** `core/player/Player.kt`
- **Depends on:** T1, T2. Walks `List<Step>`: speak → onDone → wait `pauseMsAfter` → next. Emits `onUnitStart(index)`, `onSectionBoundary(n)`. Exposes `play/pause/next/prev/nextSection`.
- **Done when:** AC6, AC7 pass.

### T4 — PdfExtractor (platform boundary)
- **Files:** `core/pdf/PdfExtractor.kt` (`expect`), Android actual (PdfBox-Android, Apache-2.0), iOS actual (PDFKit), desktop actual (Apache PDFBox).
- **Does:** `suspend fun extract(bytes): ExtractResult` → ordered units + `hasTextLayer`. Heading = line whose max item height ≥ 1.25× page median AND < 12 words → `SECTION`. Large vertical gap → `PARAGRAPH`. Sentence splitter with abbreviation + decimal guards.
- **Done when:** AC1, AC9 pass.

### T5 — RecallScheduler
- **Files:** `core/recall/RecallScheduler.kt`. Subscribes to section boundaries + wall clock; fires `recallDue` on 2-sections-or-5-min; player pauses until `continue()`.
- **Done when:** AC5 passes.

### T6 — Reading surface UI (Compose Multiplatform, `commonMain`)
- **Files:** `ui/ReaderScreen.kt`, `ui/RecallOverlay.kt`, `ui/EmptyState.kt`
- Paper-toned reading card, active-sentence highlight, upcoming-pause markers, recall overlay, drop/open state, scanned-PDF message.

### T7 — Transport + controls UI
- **Files:** `ui/TransportBar.kt`, `ui/ControlRail.kt`
- Play/pause/skip, live wpm + elapsed + section readouts, density selector, base-pace, recall interval, voice picker.

### T8 — Pace ribbon (P2 signature)
- **Files:** `ui/PaceRibbon.kt` — step durations + pauses as a timeline with a sweeping playhead.

---

## 6. Golden-Path Example

```
Extraction:
  "Backpropagation computes gradients via the chain rule."  → SENTENCE
  "It is the workhorse of modern deep learning."            → PARAGRAPH
  "2  Optimization"                                          → SECTION

schedule(units, STANDARD, LEARNING):
  Step("Backpropagation computes gradients via the chain rule.", 132, 250,  SENTENCE)
  Step("It is the workhorse of modern deep learning.",           170, 700,  PARAGRAPH)
  Step("2  Optimization",                                        150, 1500, SECTION)

Player speaks each (rate = wpm/175 on mobile; `say -r wpm` on macOS), highlights
the active unit, waits pauseMsAfter. After the 2nd SECTION boundary the
RecallScheduler fires → pause → overlay:

    Rest.
    Before you continue — what were the key points of the last section?
    [ Continue ]
```

---

## 7. Failure Modes & Known Traps

| Trap | Intended behavior |
|---|---|
| Scanned/image-only PDF | `hasTextLayer=false` → clear OCR-unsupported message (AC9) |
| **AVSpeechUtterance rate is 0..1, not a wpm multiplier** | Map empirically: `avRate = clamp(AVSpeechUtteranceDefaultSpeechRate * (targetWpm / BASE_VOICE_WPM), AVSpeechUtteranceMinimumSpeechRate, AVSpeechUtteranceMaximumSpeechRate)`. Calibrate `BASE_VOICE_WPM` per-voice if drift is audible. |
| iOS TTS won't play in silent mode | Set `AVAudioSession` category to `.playback` before speaking |
| Sentence splitter breaks on "e.g.", "Dr.", "3.14", "J. Smith" | Abbreviation + decimal + single-initial guards; err toward under-splitting |
| Multi-column / footnoted PDF garbles order | v1 best-effort y-then-x ordering; documented limitation |
| User maxes base-pace in Learning Mode | Engine cap wins (AC10); UI shows "Learning cap active" |
| Desktop `say` unavailable (non-macOS JVM) | Detect at startup; disable playback with a message (v1 desktop target is macOS) |

---

## 8. Glossary

- **Comfortable baseline** — ~150 wpm audiobook-comfortable rate; the 1.0 anchor for Learning Mode.
- **Learning cap** — hard ceiling of 1.5× baseline in Learning Mode; beyond it retention/encoding degrade.
- **Unit** — one segmented text piece with a boundary tag (`SENTENCE`/`PARAGRAPH`/`SECTION`).
- **Step** — a Unit plus engine-assigned `targetWpm` + `pauseMsAfter`.
- **Complexity score** — 0..1 difficulty (syllable density, word length, length) driving slowdown.
- **Active recall / retrieval practice** — pausing to make the learner retrieve prior material; primary retention multiplier.
- **RSVP** — word-flashing speed reading; explicit non-goal.
- **expect/actual** — KMP shared-API / per-platform-impl mechanism.

---

## Phase Gates (stop for human review at each ✋)

- **Phase 0 — Engine proven ✋** — T0, **T1 green**, T2/T3 read a hardcoded list aloud with correct pauses on desktop. Validates the *feel* before UI investment.
- **Phase 1 — Trustworthy core ✋** — T4, T5, T6, T7. Real PDFs, native TTS on all three targets, Learning Mode complete, on-device guarantee verified (AC8). Ship to TestFlight / internal Android / macOS dmg.
- **Phase 2 — Depth** — neural voice (opt-in), pace ribbon (T8), adaptation ramp, exported highlights, cross-session recall scheduling.

---

## Risk Table

| Risk | L | I | Mitigation |
|---|---|---|---|
| Native voices sound robotic → weak adoption | H | M | Phase 2 neural option; ship best native voice + good prosody via sentence-level utterances + structural pauses |
| PDF reading-order errors on complex layouts | M | M | v1 scoped to prose/text-layer; document limits; layout+OCR later |
| "Optimal" feels too slow to speed-listeners | M | M | Density selector + Free mode; keep the evidence-based cap **only** inside Learning Mode |
| macOS desktop TTS via `say` feels like a hack | L | L | It's exact-wpm and reliable; revisit native `macosMain` + AVSpeech in Phase 2 if a true .app is wanted |

---

## Appendix A — seed `CLAUDE.md` for the repo

```md
# Cadence — architecture invariants (do not violate)

## Frozen rules
- `core.pacing` is PURE. No imports beyond kotlin stdlib. No Compose, no platform,
  no TTS, no PDF. It is the product; it is exhaustively unit-tested.
- The ONLY platform boundaries are `expect class Speaker` (core.tts) and
  `expect class PdfExtractor` (core.pdf). No platform speech/PDF call exists elsewhere.
- Learning Mode narration rate is hard-capped at 1.5× the density baseline. The
  engine must refuse to exceed it. This is a retention safety rail, not a setting.
- On-device only: a user's document never leaves the device. No network call carries
  document content. No accounts, backend, or analytics in v1.

## Structure (mirror PeekLUT)
- commonMain: engine, player, models, Compose UI, expect decls
- androidMain / iosMain / jvmMain: thin actuals only

## Speak one sentence per utterance. The engine owns inter-unit pauses.

## Definition of done for any change touching core.pacing: unit tests green,
   no new imports outside stdlib.
```
