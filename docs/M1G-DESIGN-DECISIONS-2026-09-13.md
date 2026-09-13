# M1G design decisions — 2026-09-13

## Status

The pre-M1G owner decision gates are resolved. M1G implementation may proceed from these decisions.

Implementation branch:

`codex/m1g-progressive-finite-decode`

Preparation base:

`aa3943ca60e087fef2e6a4fe0cf38f0635dfcffb`

M1E and M1F evidence boundaries remain unchanged. Green CI is not a Minecraft runtime PASS.

## Locked decisions

### A1 — Minecraft AudioStream / SoundManager renderer

Modern finite PCM will reach Minecraft through a custom `AudioStream` and the normal positional `SoundManager`/`Channel` path.

Why:

- substantially less lifecycle/resource complexity than owning raw OpenAL sources/buffers;
- preserves normal Minecraft BLOCKS-category and attenuation behavior;
- stays on the same `Channel` path that Sound Physics Remastered already intercepts;
- leaves lower-level OpenAL cleanup/optimization in the later M1N/M2 work instead of pulling it into M1G.

Important implementation constraints:

- renderer-facing `AudioStream.read(...)` must never wait for network ranges or codec work;
- temporary starvation is not EOF;
- the HQ PCM queue controls actual ahead-of-time buffering even if Minecraft requests larger streaming reads;
- normal renderer reads should return relatively small time-bounded PCM chunks rather than blindly filling Minecraft's maximum request;
- seek/replacement/stop should discard the old local renderer/channel epoch rather than trying to salvage already queued stale PCM.

### B1 — server-normalized WAV layout

The server analyzer resolves common-WAV physical layout once and carries a normalized descriptor to relevant clients.

The client still performs cheap sanity validation, but does not run a second full RIFF parser.

Normalized layout facts should include at least:

- sample representation;
- sample rate;
- channels;
- block alignment;
- data offset;
- data length.

This allows exact WAV byte/time mapping and avoids reparsing the same immutable asset on every listener.

### C1 — preserve source sample rate

M1G will normalize decoded finite PCM to mono signed 16-bit samples while preserving the source sample rate.

Examples:

```text
44.1 kHz source -> mono S16 @ 44.1 kHz
48 kHz source   -> mono S16 @ 48 kHz
22.05 kHz source -> mono S16 @ 22.05 kHz
```

Do not add a finite 48 kHz resampler in M1G. OpenAL/Minecraft already receives the PCM sample rate and the device/mixer can perform the required output-rate conversion.

### D1 — narrow WAVE_FORMAT_EXTENSIBLE support

Treat `WAVE_FORMAT_EXTENSIBLE` as another standard header representation of the same common PCM/float WAV data M1G already intends to support.

Accept only:

- mono or stereo;
- PCM or IEEE-float subformat GUID;
- unsigned 8-bit PCM;
- signed 16/24/32-bit PCM;
- 32-bit IEEE float;
- initially `validBits == containerBits`.

Reject at M1G:

- >2 channels;
- compressed/telephony subformats;
- A-law / mu-law;
- 64-bit float;
- 20-in-24 / 20-in-32 / 24-in-32 or other differing valid/container widths;
- arbitrary non-PCM/non-float extensible GUIDs.

This is compatibility for common PCM/float WAV, not a general WAV codec expansion.

### E1 — coarse safe MP3 pre-roll

Do not add fine reservoir-specific seek metadata/scanners in M1G.

For MP3 seek/rejoin, choose a conservative earlier real MP3 seek point, decode silently from there, and discard PCM until the audible target/current server time.

Initial policy:

```text
safeTime = max(0, targetTime - approximately 1 second)
anchor = newest existing MP3 seek point at or before safeTime
```

Current seek points are coarse, so this can decode several seconds of extra MP3 after an occasional seek/rejoin. That bounded overhead is preferred over the complexity and failure surface of custom reservoir-aware seek metadata.

JLayer's Layer III decoder naturally rebuilds reservoir history while decoding forward; frames without enough prior main-data history can produce no PCM until the reservoir is usable.

## Additional correctness decisions

### Semantic seek always creates a new local decoder epoch

Even when the server-selected encoded anchor byte is unchanged, a SEEK invalidates:

- codec state;
- pre-roll/discard target;
- queued PCM;
- renderer state.

Encoded-byte identity must not be used as decoder-state identity.

### Decoder EOF is not server EOF

Physical decoder EOF means the local encoded asset ended. Canonical ENDED/loop behavior remains server-owned under M1E.

Temporary M1F `NEED_DATA` must never be translated into decoder EOF.

### One physical speaker remains one mono positional source

Stereo finite media is safely downmixed to mono. Java does not infer music/effect/notification roles from the file type or API path.

## M1G implementation order

1. Normalize the modern finite format descriptor to MP3/common WAV and add validated WAV layout metadata.
2. Add D1 server analyzer support and exact WAV byte/time mapping.
3. Add a cancelable starvation-aware encoded-input bridge over the M1F window.
4. Add a bounded PCM queue with backpressure and nonblocking renderer-facing reads.
5. Add progressive common-WAV conversion.
6. Add progressive JLayer MP3 decode with E1 pre-roll and audible-target discard.
7. Add A1 positional Minecraft renderer lifecycle.
8. Integrate pause/resume/seek/loop/volume/stop with server-authoritative state.
9. Complete deterministic/component acceptance, then focused Minecraft audible acceptance.

## Boundaries retained

M1H still owns full late listener discovery, proactive leave cleanup, return/rejoin policy, dimension/resource-reload recovery, robust underrun rejoin, and final VS2 listener lifecycle.

M1I still owns gated native FLAC.

M1N/M2 still own broader OpenAL cleanup and Sound Physics Remastered integration.
