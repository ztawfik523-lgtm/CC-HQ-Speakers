# Roadmap

## Product rule

Build a better programmable ComputerCraft speaker peripheral. Lua owns application meaning/policy; Java exposes truthful audio capabilities.

Do not add permanent music/effect/notification roles or a Java playlist manager.

## Current sequencing note — updated 2026-09-19

M1G is complete, including focused NeoForge 21.1.247 audible/core runtime acceptance.

The selected Option A post-M1G hardening pass is also complete:

- source checkpoint `e836dfac702dcc438fa0366dc2fba2132b5140c1`;
- CI `35404646105`;
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

Focused real-Minecraft audible proof remains unrecorded under KI-046 and should be included in later consolidated runtime acceptance rather than rewriting CI as audio proof.

### Post-M1G cross-cutting hardening

**Complete.**

- KI-062: DNS no longer holds the ownership monitor required by server tick/cleanup; a separate Lua-command lock preserves user-command ordering.
- KI-063: RAW/prepared replacement admits the replacement before destructive ownership transfer.
- KI-054: shutdown starts range-worker cancellation early, retains failed cleanup for retry, and can recover the media-root lock on a later integrated-server start.
- KI-064: bounded zero-read handling and unsupported-atomic-move fallback are implemented and tested.

Checkpoint: `e836dfac702dcc438fa0366dc2fba2132b5140c1`, CI `35404646105`.

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

Includes remaining practical malformed/extreme-media bounds, stress bounded queues/memory/network/lifecycle, keep both NeoForge targets green, then run final integrated Minecraft acceptance.

KI-061 was closed in M1G. KI-054/KI-064 were closed by the post-M1G Option A hardening pass.

## M2 — Sound Physics Remastered

Integrate frozen SPR compatibility after finite positional rendering is stable.

SPR owns intentional acoustic/range extension and matching transport relevance. M1G should keep its fixed-range policy localized so M2 can replace/extend it without redesigning the finite protocol.

## M3 — live/open-ended streams

Rebuild live MP3/HLS/TS with truthful live semantics and bounded resources. The inherited HLS refreshed-playlist index bug is confirmed and belongs here, not in M1G.

## M4 — release cleanup

Finalize public API/docs, obsolete/dead paths, custom HQ block decision, license provenance, CI/repository hygiene, and packaging.
