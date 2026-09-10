# Testing policy

Adapted from the useful HighAudio testing policy.

## Goal
Use as few manual Minecraft launches as practical.

## Automatic
Run frequently:
- Gradle build
- NeoForge 21.1.247 build
- NeoForge 21.1.248 compatibility build
- unit tests where cheap
- static resource/metadata checks
- server/client smoke where useful
- source/bytecode inspection where it answers the question

Exact M0 commands:

```text
./gradlew clean build -PneoForgeVersion=21.1.247 --no-daemon
./gradlew clean build -PneoForgeVersion=21.1.248 --no-daemon
```

The GitHub Actions matrix runs both commands and checks the packaged mod
metadata, mixin config, and three jar-in-jar codec dependencies.

## Manual
Batch tests into one broad, instrumented session.

Do not request a launch for docs-only changes, behavior-neutral refactors, or every tiny patch.

## Suggested milestone gates
M0: MP3, OGG, WAV, stream URL, stop, loop, representative multi-speaker path.
M1: play, pause, resume, seek, position/duration, volume, repeat, stop, replay, reload/disconnect.
M2: long media, memory diagnostics, EOF, malformed input, cancellation.
M3: SPR direct/occluded/opening behavior plus player lifecycle and no V7.1 regression.

## Evidence
Record exact commit, JAR SHA-256, exact stack, scenarios, pass/fail, logs, and what the run proves.

The single M0 client run is recorded in `M0-SMOKE-TEST.md`. Its helper now
stops finite playback between cases and gives numbered stream choices so a
future targeted rerun does not queue tests behind a long track.

The M1 player candidate has one consolidated client procedure and Lua helper in
`M1-RUNTIME-TEST.md` and `scripts/m1_player_test.lua`. Run it only after both
exact NeoForge builds, unit tests, packaging checks, and dedicated-server
classloading are green.

Current M1 automated result: clean `build` passed on NeoForge 21.1.247 and
21.1.248; five `FiniteAudioTrackTest` cases passed; and the 21.1.247 dedicated
development server reached `Done` after registering five payloads.
