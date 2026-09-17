# Roadmap

## Product rule

Build a better programmable ComputerCraft speaker peripheral. Lua owns application meaning/policy; Java exposes truthful audio capabilities.

Do not add permanent music/effect/notification roles or a Java playlist manager.

## Current sequencing note — updated 2026-09-17

M1E and M1F are complete at source/test/CI/package level.

M1F final source/test candidate: `d0acd41df690d02c9813ecd7e84d3115b44f6a3f`, CI `34763362365`.

M1G is in progress on `codex/m1g-progressive-finite-decode`.

Current green integrated M1G source checkpoint: `957832348eaa6e497282d923f2312c9c7d7c550f`, CI `34778546164`.

Documentation/audit work after that checkpoint has not changed implementation source.

The September 16/17 repository review was re-verified against exact source. The findings that materially affect current sequencing are KI-062, KI-063, KI-064, and the stronger KI-054 shutdown-lock interpretation. Several initial audit claims were retracted; current docs record only the verified forms.

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

Locked media/renderer decisions:

- A1 — Minecraft `AudioStream` / normal `SoundManager` renderer;
- B1 — server-normalized common-WAV layout;
- C1 — preserve source sample rate;
- D1 — narrow PCM/float WAVEX;
- E1 — conservative MP3 pre-roll from an earlier analyzed seek point.

Selected later M1G scope decisions:

- explicit server-authoritative decoder/re-anchor revision; do not infer restart intent from codec-anchor movement;
- fixed 32-block core listening/delivery radius; HQ volume changes gain, not the core radius;
- global HQ volume zero keeps canonical server time advancing but hibernates local decode/render/range work;
- looping is ordinary replay after local physical EOF while authoritative looping remains enabled; a normal restart gap is acceptable;
- no gapless MP3, permanent-source loop engineering, dynamic volume-aware range, or SPR acoustic/range integration in M1G.

Current source pipeline:

```text
M1F bounded sliding encoded window
-> starvation-aware decoder input
-> progressive WAV/JLayer MP3 decoder
-> bounded mono S16 PCM queue
-> nonblocking Minecraft AudioStream
-> positional BLOCKS SoundManager source
```

Current implementation is still protocol v6. The explicit decoder/re-anchor revision is selected for the next protocol revision, likely v7, but has not been implemented.

### Immediate cross-cutting safety work

**KI-062 — shared monitor + blocking DNS** is the one broad-audit issue currently worth considering before the M1G protocol patch because it can stall the server main thread and lifecycle cleanup.

The confirmed path is synchronized dynamic stream dispatch holding the composite monitor while `InetAddress.getAllByName(...)` may block. `tickOwnership()` and synchronized composite `cleanup()` use the same monitor; cleanup is reached from provider removal/Level-unload/server-stop paths.

The narrow fix target is to preserve ownership ordering while moving blocking DNS/I/O outside that monitor or eliminating server-thread dependence on it. Do not turn this into an M3 live-stream rewrite.

**KI-063** replacement-before-admission is a separate ownership correctness issue. It can be fixed before or after the v7 cluster depending on patch cohesion; do not silently make failed replacement destructive.

**KI-054/KI-064** are storage/shutdown hardening and can be grouped when storage code is touched unless the root-lock shutdown risk is intentionally promoted earlier.

### Remaining M1G work

1. Fix KI-053/KI-056/KI-057 together using the selected explicit decoder/re-anchor revision. Ordinary STATE must be non-destructive; semantic seek must be self-describing; stale worker identity must be invalidated before cancellation wakes it.
2. Decide only the **implementation shape** of protocol v7: retain finite CONTROL packets as optional latency hints, or remove duplicate PAUSE/RESUME/SEEK/SET_VOLUME/SET_LOOP projection and make STATE the sole correctness authority. There is no compatibility need to preserve unreleased v6 internals.
3. Implement the selected fixed-radius renderer contract (KI-058): explicitly hold the modern finite attenuation distance at 32 blocks while live volume changes gain.
4. Implement selected global-volume-zero hibernation and harden renderer-start/local-silent behavior (KI-060).
5. Implement selected ordinary local replay for looping (KI-051). Do not reopen L1/L2/L3 or gapless/continuous-source design unless the owner changes scope.
6. Close deterministic evidence gaps: real-MP3 JLayer decode across sliding/starvation/pre-roll, focused `FinitePcmAudioStream` tests, decoder cancellation/revision ordering, repeated seeks, volume/start behavior, and simple replay.
7. Close staging lifecycle leak KI-061 by reclaiming leftovers only when the whole staging owner is destroyed, not on one-computer detach.
8. Re-audit timing/cancellation/authority/storage after source fixes; keep both NeoForge targets green/package-verified.
9. Run focused real-Minecraft audible acceptance for modern `hq.playFile()` MP3/common WAV, controls/seek/starvation, bounded memory, fixed-range positional attenuation, volume-zero/unmute, stop/replacement, ordinary replay, staging lifecycle, and standard CC:T compatibility.

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
- final VS2 moving-speaker lifecycle.

The fixed M1G radius means dynamic volume-aware listener membership is **not** pulled forward.

For VS2 movement, modern BEGIN already carries block coordinates and the legacy client already recomputes ship-transformed positions locally each tick. M1H may reuse that client-side pattern with no new position packet, or may introduce an authoritative position-update mechanism if later lifecycle requirements justify it. That tradeoff remains open for M1H.

## M1I — gated native FLAC

Optional. Only advertise native FLAC after analyzer, progressive decode, seek/rejoin, malformed-input, bounded-memory, package, and runtime proof. No Ogg-FLAC.

## M1J — multispeaker shared clocks

Shared server sync clocks without expected-global-member barriers; each physical speaker keeps its own mono positional renderer.

Legacy `*All` / `*At` helpers are not the target design and currently include known note/sound semantic mismatches.

## M1K — active-session sharing optimization

After M1J correctness, share identical active encoded/decode work where safe without persistent client caching or collapsing physical renderers.

## M1L — legacy finite API migration/removal

Migrate worthwhile compatibility frontends to the new asset/transport/decoder engine and remove obsolete whole-file/whole-PCM implementation/format promises. Make capability-reporting methods truthful about the engine they describe.

## M1M — HQ RAW finalization

Keep bounded RAW producer semantics and separate `hqspeaker_audio_empty`; no fake finite timeline.

## M1N — SoundEngine/OpenAL integration cleanup

Final category/gain, reload lifecycle, stale-channel cleanup, remaining attenuation/VS2 movement concerns, and one-source-per-speaker cleanup.

## M1O/P/Q — hardening, package verification, consolidated runtime acceptance

Includes shutdown/storage hardening such as KI-054/KI-064, practical malformed/extreme-media bounds, stress bounded queues/memory/network/lifecycle, keep both NeoForge targets green, then run final integrated Minecraft acceptance.

KI-061 remains earlier because staging leftovers can accumulate during ordinary speaker lifecycle churn.

## M2 — Sound Physics Remastered

Integrate frozen SPR compatibility after finite positional rendering is stable.

SPR owns intentional acoustic/range extension and matching transport relevance. M1G should keep its fixed-range policy localized so M2 can replace/extend it without redesigning the finite protocol.

## M3 — live/open-ended streams

Rebuild live MP3/HLS/TS with truthful live semantics and bounded resources. The inherited HLS refreshed-playlist index bug is confirmed and belongs here, not in M1G.

## M4 — release cleanup

Finalize public API/docs, obsolete/dead paths, custom HQ block decision, license provenance, CI/repository hygiene, and packaging.
