# M1G design decisions — 2026-09-13

> **Historical decision checkpoint with still-valid A1/B1/C1/D1/E1 choices.**
>
> This file preserves the original M1G design gates and rationale. It does **not** override later owner decisions in `M1G-SCOPE-DECISIONS-2026-09-14.md`, `CURRENT-STATE.md`, `KNOWN-ISSUES.md`, `TESTING.md`, `VERIFIED-FACTS.md`, or exact current source.
>
> Later selected M1G scope: explicit server-authoritative decoder/re-anchor revision (current source is still v6); fixed 32-block core modern-finite radius with volume changing gain rather than range; global-volume-zero local hibernation while canonical server time continues; ordinary non-gapless replay after local EOF. Do not reopen old loop/range/reanchor alternatives from historical material unless the owner explicitly changes scope.

## Status at this checkpoint

The pre-M1G owner decision gates were resolved. M1G implementation proceeded from these decisions.

Implementation branch:

`codex/m1g-progressive-finite-decode`

Preparation base:

`aa3943ca60e087fef2e6a4fe0cf38f0635dfcffb`

M1E and M1F evidence boundaries remained unchanged. Green CI was not a Minecraft runtime PASS.

## Locked decisions which remain valid

### A1 — Minecraft AudioStream / SoundManager renderer

Modern finite PCM reaches Minecraft through a custom `AudioStream` and the normal positional `SoundManager`/`Channel` path.

Why:

- substantially less lifecycle/resource complexity than owning raw OpenAL sources/buffers;
- preserves normal Minecraft BLOCKS-category and attenuation behavior;
- stays on the same `Channel` path that Sound Physics Remastered already intercepts;
- leaves lower-level OpenAL cleanup/optimization in later M1N/M2 work instead of pulling it into M1G.

Important implementation constraints:

- renderer-facing `AudioStream.read(...)` must never wait for network ranges or codec work;
- temporary starvation is not EOF;
- the HQ PCM queue controls actual ahead-of-time buffering even if Minecraft requests larger streaming reads;
- normal renderer reads should return relatively small time-bounded PCM chunks rather than blindly filling Minecraft's maximum request;
- seek/replacement/stop should discard the old local renderer/channel epoch rather than trying to salvage already queued stale PCM.

### B1 — server-normalized WAV layout

The server analyzer resolves common-WAV physical layout once and carries a normalized descriptor to relevant clients.

The client still performs cheap sanity validation, but does not run a second full RIFF parser.

Normalized layout facts include at least:

- sample representation;
- sample rate;
- channels;
- block alignment;
- data offset;
- data length.

This allows exact WAV byte/time mapping and avoids reparsing the same immutable asset on every listener.

### C1 — preserve source sample rate

M1G normalizes decoded finite PCM to mono signed 16-bit samples while preserving the source sample rate.

Examples:

```text
44.1 kHz source -> mono S16 @ 44.1 kHz
48 kHz source   -> mono S16 @ 48 kHz
22.05 kHz source -> mono S16 @ 22.05 kHz
```

Do not add a finite 48 kHz resampler in M1G. OpenAL/Minecraft already receives the PCM sample rate and the device/mixer can perform required output-rate conversion.

### D1 — narrow WAVE_FORMAT_EXTENSIBLE support

Treat `WAVE_FORMAT_EXTENSIBLE` as another standard header representation of the same common PCM/float WAV data M1G supports.

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

Current seek points are coarse, so this can decode several seconds of extra MP3 after an occasional seek/rejoin. That bounded overhead is preferred over custom reservoir-aware metadata complexity.

JLayer's Layer III decoder naturally rebuilds reservoir history while decoding forward; frames without enough prior main-data history can produce no PCM until the reservoir is usable.

Current-source clarification from the later audit recheck: `FiniteDecodeAnchorSelector.Anchor` contains exactly `(offset, seconds)`. There are no richer frame/skip fields being computed and discarded.

## Additional correctness decisions which remain valid

### Semantic seek always creates a new local decoder epoch

Even when the server-selected encoded anchor byte is unchanged, a SEEK invalidates:

- codec state;
- pre-roll/discard target;
- queued PCM;
- renderer state.

Encoded-byte identity must not be used as decoder-state identity.

Later scope refined how this intent should be represented: use an explicit server-authoritative decoder/re-anchor revision rather than inferring it from anchor movement or relying on CONTROL SEEK ordering.

### Decoder EOF is not server EOF

Physical decoder EOF means the local encoded asset ended. Canonical state remains server-owned under M1E.

Temporary M1F `NEED_DATA` must never be translated into decoder EOF.

Later loop policy is ordinary local replay when authoritative state still says `looping=true`; a restart gap is acceptable.

### One physical speaker remains one mono positional source

Stereo finite media is safely downmixed to mono. Java does not infer music/effect/notification roles from file type or API path.

## Original M1G implementation order

The original order was:

1. normalize the modern finite format descriptor to MP3/common WAV and add validated WAV layout metadata;
2. add D1 server analyzer support and exact WAV byte/time mapping;
3. add a cancelable starvation-aware encoded-input bridge over the M1F window;
4. add a bounded PCM queue with backpressure and nonblocking renderer-facing reads;
5. add progressive common-WAV conversion;
6. add progressive JLayer MP3 decode with E1 pre-roll and audible-target discard;
7. add A1 positional Minecraft renderer lifecycle;
8. integrate pause/resume/seek/loop/volume/stop with server-authoritative state;
9. complete deterministic/component acceptance, then focused Minecraft audible acceptance.

Much of this is now integrated at source checkpoint `957832348eaa6e497282d923f2312c9c7d7c550f`. Current remaining work and sequencing are documented in `CURRENT-STATE.md` and `ROADMAP.md`; do not use this original order as the present task list.

## Boundaries retained

M1H still owns full late listener discovery, proactive leave cleanup, return/rejoin policy, dimension/resource-reload recovery, robust underrun rejoin, and final VS2 moving-source lifecycle.

M1I still owns gated native FLAC.

M1N/M2 still own broader OpenAL cleanup and Sound Physics Remastered integration. Later SPR compatibility owns intentional acoustic/range extension and matching transport relevance; M1G keeps its fixed core range.
