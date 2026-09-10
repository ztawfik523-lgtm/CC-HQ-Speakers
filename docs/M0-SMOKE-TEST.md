# M0 consolidated Minecraft smoke test

Status: completed on 2026-09-09. No second M0 session is required.

## Candidate

- Mod: `build/libs/hqspeaker-1.1.4-1.21.1-neoforge.jar`
- Stack: Minecraft 1.21.1, Java 21, CC:Tweaked 1.120.0, NeoForge 21.1.247
- Rebuild command: `./gradlew clean build -PneoForgeVersion=21.1.247 --no-daemon`
- SHA-256: `7bb76de736a148e35aa300a46fd6c2657af0128d110529f57dd5382516f22066`

This hash identifies the runtime-tested candidate. A later resource-only M0
cleanup removed a stale refmap declaration and supplied the registered HQ
speaker block/item models; the final candidate hash is recorded below after
rebuild.

## Recorded result

Runtime stack observed in the supplied logs:

- Minecraft 1.21.1
- Microsoft OpenJDK 21.0.7
- NeoForge 21.1.247
- CC:Tweaked `cc-tweaked-1.21.1-forge-1.120.0.jar`
- CC:HQ Speakers `hqspeaker-1.1.4-1.21.1-neoforge.jar`

Passed runtime observations:

- client and integrated server loaded without an HQ crash;
- the HQ mixin applied and the enhanced Lua method surface was registered;
- `playAudio` signed 8-bit and `speakPCM` signed 16-bit both returned true and
  were audible;
- volume changes were audible;
- a 2,871,486-byte finite MP3 returned true and sounded normal;
- disconnect and integrated-server shutdown completed cleanly.

Skipped or inconclusive observations:

- OGG was skipped;
- only one speaker was attached, so multi-speaker/sync was skipped;
- the stream prompt received a local filename instead of a stream type/URL;
- WAV, generic finite audio, looping, and audible stop were obscured by the
  still-playing MP3;
- active-playback disconnect/rejoin and resource reload were not recorded.

The supplied logs contained no HQ payload-decode, codec, mixin-application, or
uncaught-thread failure. They did expose a stale HQ refmap declaration and
missing models for the registered HQ block/item; both were corrected after the
run. Other warnings concerned the test instance, other mods, input handling, or
a stale world mod list.

## Final post-cleanup candidate

- SHA-256: `60cceaeb4b9ea3993c5333243625f43e39ea4c92178bab99e07fd33acda11895`

The post-cleanup change is resource-only. It has automated dual-target build
coverage but was not the exact byte-for-byte JAR used in the client run.

## Reusable procedure

1. Install the candidate JAR in the exact ATM10 instance. Ensure only CC:T 1.120.0 is present.
2. Place one small known-good MP3, OGG, and WAV (each below 8 MiB) on the test computer. A second WAV/AIFF/AU file is optional for the generic path.
3. Attach one advanced computer to one speaker. For the group check, attach at least two speakers to the same computer if practical.
4. Copy `scripts/m0-smoke.lua` into the computer as `/m0-smoke.lua`.
5. Start with a fresh game log and keep the computer near the player.

## Run

1. Execute `/m0-smoke.lua` once.
2. Answer each observation prompt. Blank file paths skip unavailable codecs; use `-` to skip only the generic-file check.
3. Supply one known-good MP3, HLS, or MPEG-TS URL when prompted. Metadata is optional because not every endpoint sends ICY metadata.
4. After the helper finishes, start the URL stream once more, disconnect and rejoin, then record whether audio/resources clean up.
5. Start finite playback once more, trigger the client resource reload (`F3+T`), and record whether playback/resources clean up.

The loop check is a baseline observation only. Source evidence already proves inherited loop transport is incomplete; do not spend time trying to make it pass.

## Expected log markers

- `[HQSpeaker] HQSpeaker mod loaded`
- `[HQSpeaker] Network registered with 3 payloads.`
- `[HQSpeaker] HQSpeakerClientHandler: Started playback`
- `[HQSpeaker] HQAudioStream: started ...` for streams
- `[HQSpeaker] HQSpeaker: stopped at ...`
- No payload decode, mixin application, JavaSound, STBVorbis, or uncaught-thread errors.

## Return one report

Paste these together in one response:

1. `/m0-smoke-results.txt` from the ComputerCraft computer;
2. relevant `latest.log` lines from `[M0-SMOKE] BEGIN` through cleanup/reload;
3. any crash report;
4. ATM10 version and exact CC:T/NeoForge filenames;
5. short notes for disconnect/rejoin and `F3+T` cleanup.
