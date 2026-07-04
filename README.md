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
**Phase 0 — desktop-first.** The pure pacing engine (`core.pacing`) is built test-first,
then proven audibly on desktop via macOS `say`. Android/iOS targets land in Phase 1.

### Run the desktop app (macOS)
```
./gradlew :shared:run
```

### Run the engine unit tests
```
./gradlew :shared:desktopTest
```
