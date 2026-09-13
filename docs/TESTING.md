# Testing

## Evidence rule

Prefer deterministic tests first, then focused Minecraft acceptance, then final batched integration acceptance.

A green Gradle/CI build proves compilation, the tests which actually exist, and package structure. It does **not** by itself prove audibility, renderer lifecycle, reload behavior, or positional sound.

Target matrix:

- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247
- NeoForge 21.1.248

## Final M1E/M1F checkpoints

### M1E

Final hardening code:

`521d4323d9216c8a99e8ec60426997c3330c4068`

CI `34757923455` passed both targets.

Final focused Minecraft M1E acceptance was explicitly skipped; no final runtime PASS is recorded.

### M1F

Final source/test candidate:

`d0acd41df690d02c9813ecd7e84d3115b44f6a3f`

CI `34763362365` passed both targets including all tests, package verification, and artifact upload.

Baseline 21.1.247 JAR SHA-256:

`2979b53f1c9903c491dda0cb3ba4a46cfff0ad4924910a974b3aaaff3e9acc32`

Focused Minecraft M1F transport acceptance is unrecorded.

The finalized-M1F documentation head and the owner-requested fresh rerun also passed both target jobs. This re-verifies source/package state, not a Minecraft runtime path.

## M1F deterministic/component evidence

The M1F finalization record contains the complete matrix. Core evidence remains:

- `FiniteRangeValidationTest` — identity/range/relevance/stale rules;
- `FiniteRangeReadServiceTest` — exact reads, worker execution, limits, asset lifetime, release retry, shutdown/cancellation;
- `FiniteRangeWindowTest` — anchor gating, availability states, stale data, sliding/refill, bounded memory;
- `FiniteRangeTransportTest` — integrated real MediaAsset read -> bounded client window -> slide/refill -> distant re-anchor proof.

## M1G status

M1G implementation has **started** on `codex/m1g-progressive-finite-decode`.

Locked architecture choices are recorded in `M1G-DESIGN-DECISIONS-2026-09-13.md`:

- A1 Minecraft `AudioStream` / `SoundManager` renderer;
- B1 server-normalized WAV layout;
- C1 preserve source sample rate;
- D1 narrow PCM/float WAVEX;
- E1 coarse conservative MP3 pre-roll.

## Green M1G start checkpoint

Current source checkpoint:

`fc99ec093528f1a8d6a975fab52c270e498dbb04`

CI:

`34773448121`

Both NeoForge 21.1.247 and 21.1.248 passed build, the full test suite, packaged-mod verification, and artifact upload.

Artifacts:

- 21.1.247 `10322129018`, ZIP SHA-256 `dc9fe86f88278faa259d821ddd0ba6e57baa1456cae10c27e5d5b93470ec8e85`;
- 21.1.248 `10323026559`, ZIP SHA-256 `89923941f23217360682f7f70c86b3872e634dc6b9923fd2d243312baabc3935`.

This checkpoint supersedes the earlier format-only checkpoint `7cca8a13...` for current M1G-start evidence.

### Format/layout/anchor tests now passing

- `CommonWavAnalyzerTest`
  - classic PCM/float normalization;
  - narrow WAVEX PCM/float;
  - valid/container-width rejection;
  - surround/companded/unusual-width rejection;
  - declared RIFF-container bound enforcement;
  - partial-frame data rejection;
  - channel reset to byte zero.
- `FiniteDecodeDescriptorTest`
  - MP3 mapping;
  - normalized WAV mapping;
  - historical OGG/AIFF/AU-style breadth cannot silently become modern decoder support;
  - WAV without normalized layout rejected.
- `FiniteDecodeAnchorSelectorTest`
  - E1 earlier MP3 pre-roll anchor;
  - selection does not assume sorted seek points;
  - exact WAV frame/time/byte mapping.

### Encoded-input tests now passing

`FiniteEncodedInputStreamTest` proves:

- temporary starvation waits on a worker instead of becoming EOF;
- accepted range bytes wake and resume the read;
- true asset EOF is the only normal `-1`;
- cancel wakes a blocked decoder epoch;
- progressive consumption advances the bounded M1F window;
- the implementation re-probes under the notification monitor to close the probe-to-wait lost-wakeup race.

This class is intentionally a decoder-worker primitive. It is not yet wired into `HQFiniteMediaClient`.

### PCM-queue tests now passing

`FinitePcmQueueTest` proves:

- empty live queue = STARVED, not EOF;
- renderer read is nonblocking;
- normal PCM drains exactly before EOF;
- decoder producer backpressure blocks only when the fixed-capacity ring is full and resumes when data is consumed;
- cancel discards stale PCM and wakes a blocked producer;
- mono-S16 sample alignment is enforced.

This queue is not yet wired into an audible renderer.

## Remaining M1G deterministic/component evidence

### Modern descriptor/wire + anchors

Prove:

- wire descriptor carries only MP3/common WAV modern formats;
- WAV layout bounds/sanity survive encode/decode;
- server STATE uses exact WAV frame anchors;
- MP3 STATE uses the conservative E1 pre-roll anchor;
- semantic seek invalidates the local decode epoch even if the encoded anchor byte is unchanged.

### Client epoch integration

Prove:

- range arrival signals the active encoded input;
- seek/replacement/stop cancels the old encoded input and PCM queue;
- stale workers cannot publish to the new epoch;
- M1F request pumping continues as decoder consumption advances the encoded window;
- client control/STATE ordering cannot accidentally preserve old codec state.

### MP3 progressive decoder

Using the exact packaged JLayer path, prove:

- decode begins from bounded M1F bytes rather than a complete file;
- decoded PCM becomes non-silent for a known fixture;
- decoding continues across several range-window slides/refills;
- temporary transport starvation pauses the decoder worker and later resumes;
- encoded EOF and cancellation are distinguished;
- output PCM queue remains bounded;
- stale output after seek/replacement is discarded;
- E1 seek/pre-roll starts earlier and suppresses pre-target audible PCM;
- semantic seek restarts decoder state even if the coarse byte anchor is unchanged.

### Common WAV converter

For each supported representation, use deterministic sample vectors and prove conversion values:

- unsigned 8-bit PCM mono/stereo;
- signed 16-bit PCM mono/stereo;
- signed 24-bit PCM mono/stereo;
- signed 32-bit PCM mono/stereo;
- 32-bit IEEE float mono/stereo;
- correct stereo-to-mono downmix;
- safe clamp/quantization to mono S16;
- source sample rate is preserved;
- malformed/truncated runtime data fails boundedly.

### Renderer adapter

Deterministic tests should cover all pure lifecycle behavior possible without a Minecraft sound engine:

- start only after required prebuffer/format availability;
- bounded time-sized `AudioStream.read(...)` output rather than blindly filling a huge request;
- STARVED produces nonblocking local underrun behavior rather than codec/network EOF;
- pause/resume state projection;
- seek/replacement renderer invalidation;
- volume updates do not mutate decode identity;
- stop closes local resources;
- no renderer method blocks on network/disk/codec work.

Do not claim positional/audible PASS from these tests.

## Focused Minecraft M1G acceptance

A real client/server run is required before calling M1G audibly proven.

At minimum record:

1. modern `hq.playFile()` MP3 becomes audible;
2. supported common WAV becomes audible;
3. playback begins after bounded prebuffer rather than complete-track transfer;
4. long-track encoded + decoded RAM remains bounded;
5. pause/resume follows server state;
6. forward/backward seek audibly rejoins current server time;
7. MP3 seek works through E1 pre-roll rather than decoder corruption;
8. loop remains synchronized to server semantics;
9. stop/replacement cancels old sound promptly;
10. temporary starvation/refill does not become permanent EOF;
11. sound is positional/attenuated from the physical `computercraft:speaker`;
12. standard CC:T `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty` compatibility remains intact;
13. no complete client song `.part/.media` file is created.

Record exact commit, JAR SHA-256, NeoForge/CC:T versions, fixture facts, pass/fail sections, relevant client/server logs, test-instance type, and network compression state if throughput is measured.

## M1H evidence boundary

Do not require M1G to prove the full dynamic listener lifecycle. Late entry, proactive leave cleanup, return/rejoin, resource/dimension recovery, robust underrun rejoin, and final VS2 movement lifecycle remain M1H.

M1G should prove safe local cancellation/restart primitives that M1H can drive later.

## Current evidence language

```text
M1E source/test/CI: PASS
M1E final focused Minecraft: skipped / no recorded PASS
M1F source/test/CI/package: PASS
M1F deterministic/component acceptance: PASS
M1F focused Minecraft transport: not recorded
M1G preparation: complete
M1G implementation: STARTED / in progress
M1G format/layout/anchor + encoded-input + PCM-queue checkpoint: PASS on both targets
M1G progressive decoder/renderer: not integrated yet
M1G audible runtime PASS: not recorded
```
