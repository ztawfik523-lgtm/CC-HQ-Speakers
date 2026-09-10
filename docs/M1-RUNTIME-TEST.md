# M1 consolidated runtime test

Run one Minecraft 1.21.1 client with Java 21, CC:T 1.120.0, NeoForge 21.1.247,
and the final M1 candidate JAR. Generate three deterministic nominal eight-second
fixtures from the repository root with:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/generate_m1_test_audio.ps1
```

This writes `m1.mp3` (44.1 kHz), `m1.ogg` (48 kHz), and `m1.wav` (32 kHz) to
`build/m1-test-audio`. It requires FFmpeg; the generator prints the exact
Windows installation command if FFmpeg is missing. MP3 frame padding can make
its decoded duration differ from exactly 8.000 seconds by a few milliseconds;
the player and test correctly use the decoded duration.

Copy the three generated files and `scripts/m1_player_test.lua` to the
ComputerCraft computer, attach one HQ speaker, and run:

```text
m1_player_test m1.mp3 m1.ogg m1.wav
```

The helper performs the automated-in-Lua portion in one session: status and
observation transitions, duration/position, pause freeze, resume, live volume,
forward/backward seek, paused seek, loop wrap/disable, natural end, both raw PCM
conventions, multiple finite calls, and stop-while-loading. It pauses at one
clearly labelled manual phase. Only press F3+T at that prompt, then return to the
computer and press Enter; pressing it during another timed assertion can make
the test script fail even when playback recovery is working.

During the helper's manual phase check:

1. Change BLOCKS and MASTER sliders while live volume is below 1.0; both sliders
   must continue to scale the sound.
2. Press F3+T during finite playback; playback should re-prime without an HQ
   exception and position should remain useful.
3. Move a VS2-mounted speaker if available; spatial position must keep updating
   before and after pause and seek.
4. Listen to the queued MP3-to-OGG transition: there must be no overlap, and
   note any audible renderer-boundary gap. If the files use different sample
   rates, verify the second track has the correct pitch and speed.
5. If a second speaker is available, run one existing `speakMp3All` call and
   confirm synchronized start, then use `audioPauseAll` and `audioResumeAll`.
6. Copy `scripts/m1_stream_test.lua` to the computer and run
   `m1_stream_test`. It defaults to SomaFM's current direct 128 kbps MP3 Groove
   Salad endpoint and stops after 20 seconds (or on any key). Do not substitute
   a `.pls`, `.m3u`, AAC, or web-player URL: `speakStream` expects a direct MP3
   byte stream. You may pass another direct MP3 endpoint as its first argument.
   Verify clean audio and metadata if provided. HLS/TS need only be repeated
   here if a regression is suspected.
7. Disconnect/rejoin and shut down the world. Confirm no HQ
   exception or lingering audio.

Record exact commit, JAR SHA-256, pass/fail for each section, and the relevant
client log. A failure should get one focused retest after an automated/source
diagnosis, not a new series of broad launches.
