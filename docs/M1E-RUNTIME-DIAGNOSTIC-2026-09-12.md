# M1E runtime diagnostic — 2026-09-12

This document records the focused Minecraft runtime evidence gathered after the M1E source/test/CI checkpoint. It is diagnostic evidence, **not** an M1E runtime PASS and **not** a request to repair the temporary decoder before M1G.

## Runtime stack

Observed runtime:

- Minecraft 1.21.1
- Java 21.0.7
- NeoForge 21.1.247
- CC:Tweaked 1.120.0
- HQ Speaker `1.1.4-1.21.1-neoforge`
- diagnostic build from active branch `codex/m1e-server-authoritative-finite`

The active branch head used for the latest diagnostic build is:

`c7f5a70de4bade2f992591fcf8cdae9b28fe76a7` — `Add first-buffer PCM content diagnostics`

GitHub Actions run `34686003774` is green on NeoForge 21.1.247 and 21.1.248 for that head. As always, green CI does not prove audible runtime behavior.

## What the runtime logs show

Across repeated finite generations, the client reaches the same sequence:

1. receives finite BEGIN for the MP3;
2. receives authoritative server STATE = PLAYING;
3. receives the complete old whole-file transfer;
4. opens the JavaSound/mp3spi decoder;
5. reports READY;
6. receives a fresh authoritative STATE at the then-current server position;
7. submits the finite sound to Minecraft `SoundManager`;
8. Minecraft asks the stream for its first PCM buffer;
9. the decoder returns no PCM on that first read;
10. the client later reports diagnostic ERROR: `client decoder ended before authoritative server EOF`.

The important semantic observation is that the server continues its own authoritative state/control timeline despite the client decoder failure. The logs show PLAYING/PAUSED/resume/end/loop-related state traffic consistent with the M1E server-authority contract.

The logs do **not** contain the ComputerCraft terminal line:

```text
M1E server-authority contract passed
```

Therefore M1E must remain **Minecraft runtime acceptance pending** until that focused script is actually observed to pass. Do not infer a PASS from the diagnostic logs alone.

## Duration observation

For the tested MP3, the server-side analyzer reports:

```text
161.304 s
```

The old JavaSound/mp3spi client reports:

```text
322.584 s
```

The runtime operator confirmed the fixture is roughly 2:45 (~165 seconds), so the MP3SPI value is clearly wrong and must not become authoritative. The server value is in the expected range and remains the canonical timeline value. Small differences between encoded-frame duration and a player's displayed duration may be revisited later if needed; gapless delay/padding correction is not an M1E blocker.

## Proven defect in the temporary MP3 seek path

`FileFiniteAudioStream.JavaSoundDecoder.seek()` currently computes a target in decoded PCM-frame/byte terms and calls `decoded.skip(remaining)`.

The exact shipped mp3spi implementation behind the converted MP3 stream does not provide normal decoded-PCM-byte skip semantics. `DecodedMpegAudioInputStream.skip(long)` uses the requested number relative to compressed stream length to estimate MPEG frames, skips MPEG frames, and returns compressed bytes skipped.

The current bridge therefore mixes incompatible units during MP3 seeking.

There is a second correctness problem in the temporary seek helper: if the seek loop reaches EOF early, it can still clear `ended` and return the requested target, so a log saying `requested=X actual=X` does not prove the decoder really reached X.

The current renderer restart path also performs a redundant second seek on the same newly created stream.

These findings explain why the current JavaSound/mp3spi bridge should not be used as a correctness oracle for duration or seek behavior.

## Decision: do not repair this bridge before M1G

The project intentionally separates the remaining work:

- **M1E:** authoritative server finite semantics;
- **M1F:** client-pulled bounded encoded transport;
- **M1G:** real progressive MP3/common-WAV decode and audible rendering.

The temporary JavaSound/mp3spi decoder should remain architecturally visible so M1F does not block M1G's needs, but it is **not expected to work or work correctly before M1G**.

Do not spend pre-M1F work polishing `FileFiniteAudioStream`, its MP3 seek behavior, or its client-reported duration unless new evidence shows a server-authority dependency.

## M1F consequence

M1F will use a **clean break** for the modern prepared finite path:

- replace fixed-recipient whole-file push with client-requested encoded ranges;
- remove the modern prepared path's dependence on `.part/.media` client song files;
- keep client encoded memory bounded;
- expose a codec-agnostic in-memory range/window contract suitable for M1G;
- do not require PCM decoding or audible finite playback for M1F acceptance.

The M1F client data layer must still distinguish conceptually between:

- encoded data currently available;
- more data required/not arrived yet;
- true end of asset;
- cancelled/stale generation.

That distinction is preparation for M1G. It does not mean implementing the M1G decoder early.

## Evidence boundary

This document records diagnostics and agreed milestone boundaries only. It does not mark M1E complete, implement M1F, change protocol behavior, or fix the decoder.
