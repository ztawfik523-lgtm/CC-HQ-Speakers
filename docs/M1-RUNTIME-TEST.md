# M1 consolidated runtime test

Run one Minecraft 1.21.1 client with Java 21, CC:T 1.120.0, NeoForge 21.1.247,
and the final M1 candidate JAR. Use three short finite files copied onto the
computer as `m1.mp3`, `m1.ogg`, and `m1.wav`. The MP3 must be no longer than
eight seconds so three complete loop cycles stay quick. Give two files
different sample rates if practical.

Copy `scripts/m1_player_test.lua` to the ComputerCraft computer, attach one HQ
speaker, and run:

```text
m1_player_test m1.mp3 m1.ogg m1.wav
```

The helper performs the automated-in-Lua portion in one session: status and
observation transitions, duration/position, pause freeze, resume, live volume,
forward/backward seek, paused seek, loop wrap/disable, natural end, both raw PCM
conventions, multiple finite calls, and stop-while-loading.

During the helper's prompts also check:

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
6. Optionally start one known MP3/ICY URL with `speakStream`, verify metadata if
   provided, and stop it. HLS/TS need only be repeated here if a regression is
   suspected.
7. Disconnect/rejoin and shut down the world. Confirm no HQ
   exception or lingering audio.

Record exact commit, JAR SHA-256, pass/fail for each section, and the relevant
client log. A failure should get one focused retest after an automated/source
diagnosis, not a new series of broad launches.
