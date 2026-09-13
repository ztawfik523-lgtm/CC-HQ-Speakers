# Roadmap

## Product rule

Build a better programmable ComputerCraft speaker peripheral. Lua owns application meaning/policy; Java exposes truthful audio capabilities.

Do not add permanent music/effect/notification roles or a Java playlist manager.

## Current sequencing note — 2026-09-14

M1E and M1F are complete at source/test/CI/package level.

M1F final source/test candidate: `d0acd41df690d02c9813ecd7e84d3115b44f6a3f`, CI `34763362365`.

M1G is in progress on `codex/m1g-progressive-finite-decode`.

Current green integrated M1G source checkpoint: `957832348eaa6e497282d923f2312c9c7d7c550f`, CI `34778546164`.

The 2026-09-14 audits changed documentation only and recorded KI-053 through KI-061; source remains at the integrated checkpoint for implementation purposes.

## Foundation

### M1A — CC:T compatibility/output ownership

Standard `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty` remain delegated to CC:T. HQ RAW uses bounded separate ownership/backpressure.

### M1B — reusable server MediaAssets

Server-side encoded assets are UUID-addressed, disk-backed, quota-controlled, reference-counted, and seekably readable.

### M1C — ComputerCraft local-file import

CC files use temporary writable staging only to import immutable reusable server assets. Lua helpers expose prepare/play/release and `playFile`.

### M1D — server media analysis

Server analysis proves finite format/duration/coarse seek facts without whole-track PCM decode. Historical analyzer breadth does not define current modern support.

## M1E — server-authoritative finite timeline

**Complete at source/test/CI level.** Server owns generation/state/time/controls/natural EOF/server errors. Final focused Minecraft acceptance was explicitly skipped/unrecorded.

## M1F — demand-driven finite encoded transport

**Complete at source/test/CI/package and deterministic/component level.**

Current transport uses bounded range request/data, 128 KiB max responses, a 512 KiB client encoded window, background server IO, stale completion rejection, arbitrary re-anchor, forward sliding, and no modern whole-song client file/CHUNK-END path.

Focused real-Minecraft M1F transport acceptance remains unrecorded.

## M1G — core progressive finite engine: MP3 + common WAV

**Integrated in source; correctness/evidence work remains.**

Locked decisions:

- A1 — Minecraft `AudioStream` / normal `SoundManager` renderer;
- B1 — server-normalized common-WAV layout;
- C1 — preserve source sample rate;
- D1 — narrow PCM/float WAVEX;
- E1 — conservative MP3 pre-roll from an earlier analyzed seek point.

Current source pipeline:

```text
M1F bounded sliding encoded window
-> starvation-aware decoder input
-> progressive WAV/JLayer MP3 decoder
-> bounded mono S16 PCM queue
-> nonblocking Minecraft AudioStream
-> positional BLOCKS SoundManager source
```

Integrated work includes protocol v6 descriptor/anchors, common-WAV conversion, JLayer MP3 decode/pre-target discard, decode epochs/cancellation, bounded prebuffer/catch-up, renderer integration, and pause/resume/volume projection.

### Remaining M1G work — revised after the deeper audit

1. **Choose and implement a coherent decoder snapshot/reanchor model** for KI-053/KI-056/KI-057. The main choice is a minimal v6/client-ordering patch versus an explicit server-authoritative decode/reanchor revision (likely protocol v7). Do not patch KI-053 in isolation.
2. **Fix cancellation ordering** so expected SEEK cancellation cannot race into a fatal decoder error; invalidate stale worker identity before cancellation wakes it.
3. **Make ordinary STATE reconciliation non-destructive** to a healthy decoder while still guaranteeing fresh codec state for semantic seek/rejoin even when the coarse encoded anchor is unchanged.
4. **Choose modern finite volume/range behavior** for KI-058/KI-059/KI-060: whether volume 0 keeps a silent renderer active or defers local start/catches up later, and whether volume 0..3 should preserve normal CC:T/Minecraft audible-distance semantics. Then fix live attenuation refresh, renderer-start retry/defer behavior, and server relevance accordingly.
5. **Choose loop-wrap rejoin policy KI-051**: L1 client EOF refresh, L2 server wrap STATE, or L3 client local modulo/restart. Implement loop on top of the corrected reanchor mechanism rather than creating a second restart path.
6. **Close deterministic evidence gaps**: real-MP3 JLayer decode across sliding/starvation/pre-roll, focused `FinitePcmAudioStream` tests, decoder cancellation/revision ordering, live volume/start behavior, and repeated seek stress.
7. **Close staging lifecycle leak KI-061** by reclaiming leftover files when the whole per-speaker staging owner is destroyed, with multi-attachment and cleanup-failure coverage. This may be done before or after focused playback fixes; unlike KI-054 it can accumulate during ordinary speaker lifecycle churn.
8. **Re-audit timing/cancellation/authority/storage** after the above changes and keep both NeoForge targets green/package-verified.
9. **Run focused real-Minecraft audible acceptance** for modern `hq.playFile()` MP3/common WAV, controls/seek/starvation, bounded memory, positional attenuation, live volume/range behavior, zero-volume behavior, stop/replacement, loop after KI-051, staging lifecycle, and standard CC:T compatibility.

M1G non-negotiables remain:

- encoded and decoded memory bounded independently of duration;
- temporary `NEED_DATA` is never decoder EOF;
- semantic seek/rejoin restarts codec state when required even if the coarse anchor byte is unchanged;
- ordinary server snapshots do not gratuitously restart a healthy decoder;
- no Minecraft/audio-thread network/disk/codec blocking;
- cancellation/replacement safety;
- one physical speaker remains one mono positional source;
- server M1E timeline remains canonical.

## M1H — dynamic listener lifecycle/recovery

- discover players entering range after playback starts;
- proactive leave cleanup;
- return/rejoin current server time;
- dimension/world/resource reload recovery;
- robust general underrun rejoin;
- final VS2 moving-speaker listener lifecycle.

If the owner chooses dynamic volume-dependent listener relevance for KI-059, the necessary enter/leave subset may intentionally move forward from M1H into M1G.

## M1I — gated native FLAC

Optional. Only advertise native FLAC after analyzer, progressive decode, seek/rejoin, malformed-input, bounded-memory, package, and runtime proof. No Ogg-FLAC.

## M1J — multispeaker shared clocks

Shared server sync clocks without expected-global-member barriers; each physical speaker keeps its own mono positional renderer.

## M1K — active-session sharing optimization

After M1J correctness, share identical active encoded/decode work where safe without persistent client caching or collapsing physical renderers.

## M1L — legacy finite API migration/removal

Migrate worthwhile compatibility frontends to the new asset/transport/decoder engine and remove obsolete whole-file/whole-PCM implementation/format promises.

## M1M — HQ RAW finalization

Keep bounded RAW producer semantics and separate `hqspeaker_audio_empty`; no fake finite timeline.

## M1N — SoundEngine/OpenAL integration cleanup

Final category/gain, reload lifecycle, stale-channel cleanup, attenuation/VS2 movement, and one-source-per-speaker cleanup.

## M1O/P/Q — hardening, package verification, consolidated runtime acceptance

Includes shutdown/storage hardening such as KI-054 (and any KI-061 residual cleanup if not closed in M1G), practical malformed/extreme-media bounds, stress bounded queues/memory/network/lifecycle, keep both NeoForge targets green, then run final integrated Minecraft acceptance.

## M2 — Sound Physics Remastered

Integrate frozen SPR compatibility after finite positional rendering is stable.

## M3 — live/open-ended streams

Rebuild live MP3/HLS/TS with truthful live semantics and bounded resources. Do not force finite semantics onto live sources.

## M4 — release cleanup

Finalize public API/docs, obsolete/dead paths, custom HQ block decision, license provenance, and packaging.
