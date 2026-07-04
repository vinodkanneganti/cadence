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

---

## Build status / current phase
- **Phase 0 (in progress): desktop JVM target only.** Android/iOS targets + their
  `Speaker`/`PdfExtractor` actuals are added in Phase 1 once their toolchains are set up.
- The `jvm("desktop")` source set is `desktopMain` (the PRD's `jvmMain`). Its `Speaker`
  actual shells out to macOS `say -r <targetWpm>` (exact wpm, no calibration needed).

## Full spec
See `PRD-cadence-claude-code.md` for the complete PRD, the exact pacing algorithm,
acceptance criteria, and the task breakdown (T0–T8).
