# Verified facts

Facts only. Recommendations and unresolved choices belong elsewhere.

When behavior changed after the reviewed M1 reference, historical facts are explicitly scoped to that reference instead of being called current.

## Repository/platform

### FACT-REPO-001

Repository:

`ztawfik523-lgtm/CC-HQ-Speakers`

Untouched fork baseline:

`d1a592351c866f9a28ceef00b59e591ee773f3d5`

Reviewed historical M1 reference:

`fba84a33a94d451af09b983bcb04416c97ff64cf`

Frozen staged/local-file prototype reference:

`69e34a5346f6ce47580f49ed867c9951bfd338bc`

Completed M0.5 preparation reference:

`ad38412a2173f849a0fc8e867030da8a78965c9c`

Current implementation branch:

`codex/m1a-compat-output`

### FACT-PLATFORM-001

Target stack:

- Minecraft 1.21.1
- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247 baseline
- NeoForge 21.1.248 compatibility

Build dependency:

`cc.tweaked:cc-tweaked-1.21.1-forge:1.120.0`

### FACT-PLATFORM-002

The exact M0.5 HEAD completed GitHub Actions successfully on NeoForge 21.1.247 and 21.1.248. The workflow runs `clean build` on Java 21 and verifies required packaged mod resources, including the bundled ComputerCraft ROM module.

Current M1A source changes continue to run through the same two-version matrix. A green CI build is build/test/package evidence, not Minecraft runtime proof.

### FACT-PLATFORM-003

Current `HQSpeakerNetwork` registers ten custom payload types:

Client-bound:

- legacy/HQ audio;
- legacy/HQ stop;
- legacy finite/player control;
- staged finite begin;
- staged finite chunk;
- staged finite end;
- staged finite control.

Server-bound:

- ICY metadata;
- legacy finite/player status;
- staged finite status.

The staged finite payload family belongs to the frozen/prototype architecture and is scheduled for later replacement by the asset/range design.

## CC:T 1.120.0 base speaker contract

Source basis: exact CC:Tweaked 1.120.0 for Minecraft 1.21.1, tag `v1.21.1-1.120.0`, plus official speaker documentation.

### FACT-CCT-001

The normal peripheral type is `speaker`.

### FACT-CCT-002

`playNote(instrument [, volume [, pitch]])` accepts optional volume/pitch, resolves a real note-block instrument, validates the instrument, and is subject to the configured per-tick note limit.

The official documentation says omitted pitch defaults to `12`, while exact 1.120.0 source uses `pitchA.orElse(1.0)`. This is an upstream source/documentation discrepancy.

### FACT-CCT-003

`playSound(name [, volume [, pitch]])` resolves a Minecraft/modded sound identifier, accepts optional volume/pitch, rejects jukebox-song sound events, and returns false when native sound/audio conflict prevents playback.

### FACT-CCT-004

`playAudio(audio [, volume])` accepts signed 8-bit samples, maximum `128 * 1024` samples per call, at 48 kHz. It has one pending DFPWM buffer and returns false when another cannot be accepted.

If volume is omitted, native DFPWM state retains the previous `playAudio` volume.

### FACT-CCT-005

`speaker_audio_empty` is emitted after native pending audio is pulled/freed and another standard `playAudio` buffer may be accepted.

### FACT-CCT-006

`stop()` is a standard speaker method. Exact source sets a `shouldStop` flag; `SpeakerPeripheral.update()` processes that flag on a later server tick, clears native DFPWM/latest arbitrary sound state, and sends native stop when appropriate.

Pending note events are stored separately and are not cleared by `SpeakerPeripheral.stop()`.

### FACT-CCT-007

`IDynamicPeripheral.callMethod` may be called from ComputerCraft computer/Lua threads, and a single peripheral may be used by more than one computer. Main-thread Lua functions are wrapped by CC:T into a queued main-thread task/result rather than ordinary dynamic `callMethod` execution.

## Historical reviewed-M1 HQ facts

These facts describe the reviewed `fba84a3` lineage and explain the defects M1A is replacing. They are not claims about the current composite surface.

### FACT-HIST-001

The inherited `HQSpeakerPeripheral.playNote` ignored its instrument argument and synthesized a sine wave into the HQ PCM queue. Its inherited `playSound` ignored the requested sound identifier and reused that generated-note path.

### FACT-HIST-002

The inherited `HQSpeakerPeripheral` exposed `speakStop()` / `audioStop()` but did not itself provide the standard CC:T `stop()` implementation.

### FACT-HIST-003

The inherited server queue is an `ArrayBlockingQueue` of 16 `SpeakerChunk`s, and `speakerTick()` polls one chunk per server tick.

Its inherited synthetic `speaker_audio_empty` scheduling is based on the HQ packet queue threshold rather than CC:T native DFPWM capacity.

### FACT-HIST-004

Legacy finite byte input is capped at 8 MiB and enters the inherited whole-packet/whole-decoded-PCM path.

### FACT-HIST-005

Legacy `HQAudioStream` finite decode uses a single-thread decoder executor. OGG retained decode uses STB Vorbis memory decode; JavaSound-supported finite media uses whole converted reads. The retained decoded PCM path has a 64 MiB post-decode cap.

## Current M1A compatibility/output facts

### FACT-M1A-001

`ComputerCraftSpeakerBlockEntityMixin` exposes an `HQSpeakerCompositePeripheral` for the normal CC:T speaker while retaining the original CC:T `SpeakerPeripheral` owned by `SpeakerBlockEntity`.

The exposed peripheral type remains `speaker`.

### FACT-M1A-002

For exposed standard method names, the composite calls the original CC:T `SpeakerPeripheral` for:

- `playNote`;
- `playSound`;
- `playAudio`;
- `stop`.

The inherited fake HQ methods with overlapping names remain present inside the legacy object but are not the composite's standard-method dispatch target.

### FACT-M1A-003

The legacy HQ object is attached through an `IComputerAccess` proxy which suppresses its synthetic `speaker_audio_empty`. The original CC:T peripheral is attached to the real computer access, so native `speaker_audio_empty` remains sourced by CC:T.

### FACT-M1A-004

The normal single-speaker composite tracks one HQ continuous owner from these technical categories:

- RAW;
- legacy finite;
- staged finite prototype;
- stream intent;
- none.

Starting a new incompatible HQ source stops the prior HQ source. Repeated accepted `speakPCM` calls while RAW owns the output continue the same RAW feed.

### FACT-M1A-005

An HQ continuous-source start also calls native CC:T `stop()`. Exact CC:T notes are stored separately from native sound/DFPWM state, so this does not clear pending note events.

While an HQ continuous source reports active, the composite returns false for exposed standard `playSound` / `playAudio` instead of dispatching them into overlapping native continuous audio.

### FACT-M1A-006

The composite exposes `speakMaxSamples()` as `131072`, matching the inherited contiguous table conversion ceiling.

Legacy source still contains the older `SPEAKER_MAX_PCM = 192000` constant, but the actual table converter rejects lengths above `131072`.

### FACT-M1A-007

M1A uses a separate `hqspeaker_audio_empty` event for HQ `speakPCM` admission.

The composite checks both:

- the inherited 16-entry server packet queue; and
- a duration-based outstanding RAW allowance.

The current duration allowance is `135872` samples: one maximum legal `131072`-sample call plus `4800` samples/100 ms of headroom.

For a valid-sized `speakPCM` call rejected for capacity, the composite remembers that computer's requested sample count. It emits `hqspeaker_audio_empty` only once both the packet queue and the duration allowance can fit that requested count again.

Empty and over-limit table lengths go through inherited validation and throw. Individual sample values are validated by the inherited converter only once a call reaches conversion, so a valid-sized call can be rejected for capacity before value-level validation runs.

### FACT-M1A-008

`RawFeedLifetime` is a pure Java server-tick state model which tracks exact outstanding accepted RAW samples.

At 48 kHz and 20 server ticks/s it subtracts `2400` outstanding samples per server tick. Its derived `drainTicks()` is the ceiling of outstanding samples divided by `2400`.

It does not request source closure while the inherited outbound packet queue still has data, and after outstanding samples reach zero it requires a 20-tick idle grace before closure.

`RawFeedLifetimeTest` covers exact outstanding-sample accounting, tiny-chunk accumulation without per-call tick rounding, capacity checks, drain timing, queue gating, idle-grace reset, clear, and invalid capacity/sample arguments.

### FACT-M1A-009

The legacy client `HQAudioStream` RAW path has a bounded 64-chunk queue and drops a newly received RAW PCM chunk if that queue is already full.

M1A server-side sample-duration admission is intended to prevent normal producers from sending multi-second accepted chunks every server tick and building an unbounded delay. It is not a per-client acknowledgement protocol; pathological client/network conditions can still cause bounded client-side dropping.

### FACT-M1A-010

M1A makes calls which can change the composite's current owner run one at a time on the same physical speaker. This prevents two connected ComputerCraft computers from interleaving `stop previous`, `start requested`, and `set owner` operations.

### FACT-M1A-011

`audioStatus()` is routed by current composite owner. RAW status is reported as RAW and does not claim finite seek/loop capabilities. `audioStop()` ends whichever HQ continuous owner is current; standard `stop()` additionally requests native CC:T stop.

### FACT-M1A-012

The inherited `*All` / `*At` helpers still call legacy `HQSpeakerPeripheral` instances directly and therefore bypass the new single-speaker composite ownership boundary. Their old expected-group/tap architecture has not been migrated in M1A.

### FACT-M1A-013

The inherited HQ stop packet contains only a source UUID and its current legacy broadcast helper sends it to players within the 32-block HQ radius. Dynamic leave-range/re-enter-range renderer ownership is not yet implemented; that is later M1I work.

## Frozen staged finite prototype facts

### FACT-PROTO-001

The frozen prototype added a writable ComputerCraft mount and bundled `hqspeaker.lua` helper capable of copying a CC filesystem file into server-owned staging without Lua `readAll()`.

### FACT-PROTO-002

The prototype finite transfer uses begin/chunk/end client-bound payloads with 256 KiB chunks and a fixed recipient set captured for that session.

### FACT-PROTO-003

The prototype client stores encoded finite media on disk and has file-backed incremental finite decode paths instead of requiring complete decoded PCM retention for that staged path.

### FACT-PROTO-004

The prototype still uses client READY/STARTED/ENDED-style reports, renderer observation, fixed recipients, and per-speaker staged-media ownership. Those facts describe existing code, not the accepted final finite architecture.

## Retained finite facts

### FACT-FINITE-001

`FiniteAudioTrack` retains complete mono signed-16-bit PCM, exact sample rate, and frame-aligned cursor state. Renderer forks share retained PCM with independent cursors.

### FACT-FINITE-002

Legacy finite controls include PAUSE, RESUME, SEEK, SET_VOLUME, and SET_LOOP, and legacy status transitions include READY, STARTED, PAUSED, RESUMED, SEEKED, ENDED, and ERROR.

### FACT-FINITE-003

The retained/prototype lineage contains client-renderer authority concepts including anchor/successful renderer state and generation promotion. These remain in source until M1E replaces them with server-authoritative finite state.

## Stream facts

### FACT-STREAM-001

`StreamingAudioSource` has MP3_STREAM, HLS_STREAM, and TS_STREAM paths and a bounded PCM queue.

### FACT-STREAM-002

Stream volume is currently applied inside `StreamingAudioSource.queuePCM()` by scaling PCM samples, while the Minecraft HQ renderer also applies packet volume.

### FACT-STREAM-003

Live HLS parsing records `EXT-X-MEDIA-SEQUENCE`, while current stream progression uses a persistent segment index across refreshed playlists.

### FACT-STREAM-004

Direct TS obtains a complete `List<AudioFrame>` from `TSDemuxer.demux(InputStream)` before iterating playback output.

### FACT-STREAM-005

The existing TS decode path can return compressed frame data unchanged after unsupported JavaSound decode failure.

## Multi-speaker facts

### FACT-SYNC-001

Inherited All/At calls can assign a shared future start tick, sync-group UUID, and expected group size.

### FACT-SYNC-002

Legacy audio packet delivery is per physical speaker/radius while expected group size can be based on the full server-side member set.

### FACT-SYNC-003

`SharedStreamingGroup` waits for the expected tap count before beginning shared decode.

## Lifecycle facts

### FACT-LIFE-001

Current `HQSpeakerPeripheralProvider` keys its cache by concrete `Level` and block position and explicitly documents that weak keys alone are insufficient because cached composite values retain their Level.

### FACT-LIFE-002

M0.5 added deterministic provider eviction on:

- CC speaker block removal through the exact `SpeakerBlockEntity.setRemoved()` injection;
- server Level unload;
- server shutdown.

Composite cleanup also removes the M1A composite from its static active set.

## Test facts

### FACT-TEST-001

Current pure Java tests include:

- `FiniteAudioTrackTest`;
- `HLSPlaylistParserTest`;
- `FinitePlaybackClockTest`;
- `FiniteMediaPathTest`;
- `RawFeedLifetimeTest`.

### FACT-TEST-002

`scripts/p0_cc_speaker_contract.lua` is the standard CC:T runtime contract. It includes one short tick separation after native `stop()` because exact CC:T `stop()` sets a flag consumed by `SpeakerPeripheral.update()`.

### FACT-TEST-003

`scripts/m1a_output_contract.lua` is the M1A single-speaker runtime contract for HQ RAW ownership, packet-capacity and duration-capacity backpressure, stop/replacement, and native-method recovery.

Neither script should be reported as a runtime pass until it has actually been run successfully in Minecraft on the target stack.

## License

### FACT-LICENSE-001

Top-level repository `LICENSE` is MPL-2.0 while `neoforge.mods.toml` declares LGPL-3.0.

No source change in M0.5/M1A resolves that provenance mismatch.
