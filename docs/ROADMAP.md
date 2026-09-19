# Roadmap

## Product rule

Build a better programmable ComputerCraft speaker peripheral. Lua owns application meaning/policy; Java exposes truthful audio capabilities.

Do not add permanent music/effect/notification roles or a Java playlist manager.

## Current sequencing note — updated 2026-09-19

M1G is complete, including focused NeoForge 21.1.247 audible/core runtime acceptance.

The selected Option A post-M1G hardening pass is also complete:

- source checkpoint `3d30ce4564de749f32171666df65de739b08ad77`;
- CI `35406595856`;
- NeoForge 21.1.247 and 21.1.248 both green with deterministic tests, packaged-mod verification, and artifact upload;
- KI-062, KI-063, KI-054, and KI-064 resolved.

The next active milestone is **M1H — dynamic listener lifecycle/recovery**.

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

**Complete at source/test/CI/package/component level.**

Final source checkpoint: `fa679ffcb81a66fd99ab6be8e6d6b77895fbc542`.

Final CI: `35297026277`, both NeoForge 21.1.247 and 21.1.248 green.

Completed contract:

- A1 Minecraft `AudioStream` / normal `SoundManager` renderer;
- B1 server-normalized common-WAV layout;
- C1 source-rate preservation;
- D1 narrow PCM/float WAVEX;
- E1 conservative MP3 pre-roll;
- protocol v7 explicit server-authoritative decoder/re-anchor revision;
- ordinary STATE preserves healthy decode state; semantic seek increments revision;
- nonterminal finite CONTROL projection removed; STATE is the authority and STOP remains explicit;
- fixed 32-block delivery/attenuation contract with HQ volume changing gain rather than radius;
- global HQ volume zero hibernates local transport/decode/rendering while canonical server time continues;
- renderer-start/lost-renderer recovery plus `canStartSilent()` for client-local mute;
- ordinary non-gapless replay after physical EOF while authoritative looping remains enabled;
- real JLayer MP3 fixture coverage across starvation/sliding/pre-target discard;
- pure renderer-read policy coverage for DATA/starvation/EOF/cancel;
- whole-owner staging leftover cleanup.

M1G intentionally does **not** include gapless MP3 metadata handling, permanent-source loop engineering, dynamic volume-aware listener membership, general late-entry/rejoin lifecycle, final VS2 movement, or Sound Physics Remastered acoustic/range integration.

Focused M1G audible/core Minecraft runtime acceptance passed on NeoForge 21.1.247 on 2026-09-19; KI-046 is resolved. NeoForge 21.1.248 remains CI/package verified rather than manually runtime-verified.

### Post-M1G cross-cutting hardening

**Complete.**

- KI-062: DNS runs outside both the ownership monitor and command-order lock; validated single-speaker stream commit rejoins short command ordering only after DNS returns, so server tick/cleanup and main-thread prepared playback cannot wait behind DNS. Newer playback/control commands supersede an older normal stream still waiting on DNS.
- KI-063: RAW/prepared replacement admits the replacement before destructive ownership transfer.
- KI-054: shutdown starts range-worker cancellation early, retains failed cleanup for retry, and can recover the media-root lock on a later integrated-server start.
- KI-064: bounded zero-read handling and unsupported-atomic-move fallback are implemented and tested.

Checkpoint: `3d30ce4564de749f32171666df65de739b08ad77`, CI `35406434097`.

## M1H — dynamic listener lifecycle/recovery

### M1H-1 — listener membership

Start here:

- track admitted players for each active modern finite session;
- discover late entry into the fixed 32-block radius;
- bootstrap the current generation/current canonical time;
- proactively clean up players leaving range;
- re-admit on return;
- prune disconnect/dimension changes;
- add deterministic no-spam transition tests plus focused Minecraft walk-in/walk-out/re-entry acceptance.

Current source already relevance-checks READY and range requests, but `HQFiniteMediaServer.tick()` does not maintain listeners.

Late-entry packet sequencing remains a real choice: proactive BEGIN+STATE, or BEGIN followed by existing READY->STATE. Do not silently choose if the tradeoff matters.

### M1H-2 — recovery

Verify resource/sound-engine reload, existing renderer-loss rejoin, long-underrun current-time recovery, and remaining world/dimension lifecycle edges.

### M1H-3 — moving source / VS2

Two viable approaches remain: mirror the inherited client-side ship transform from BEGIN block coordinates, or add explicit authoritative position updates. Do not silently choose between them.

The fixed M1G radius means dynamic volume-aware listener membership is **not** pulled forward.

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

Includes remaining practical malformed/extreme-media bounds, stress bounded queues/memory/network/lifecycle, keep both NeoForge targets green, then run final integrated Minecraft acceptance.

KI-061 was closed in M1G. KI-054/KI-064 were closed by the post-M1G Option A hardening pass.

## M2 — Sound Physics Remastered

Integrate frozen SPR compatibility after finite positional rendering is stable.

SPR owns intentional acoustic/range extension and matching transport relevance. M1G should keep its fixed-range policy localized so M2 can replace/extend it without redesigning the finite protocol.

## M3 — live/open-ended streams

Rebuild live MP3/HLS/TS with truthful live semantics and bounded resources. The inherited HLS refreshed-playlist index bug is confirmed and belongs here, not in M1G.

## M4 — release cleanup

Finalize public API/docs, obsolete/dead paths, custom HQ block decision, license provenance, CI/repository hygiene, and packaging.
