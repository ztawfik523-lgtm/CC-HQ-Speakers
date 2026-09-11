# Known issues / product gaps

Severity here is project priority, not a claim of exploitability.

Status labels distinguish source work from Minecraft runtime evidence. A source-implemented item is not considered runtime-proven until the relevant acceptance script passes on the target stack.

## M1A source-implemented / runtime pending

### KI-001 — standard CC:T speaker contract was not preserved

**Status: source-implemented in M1A; runtime acceptance pending.**

The normal physical speaker now delegates `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty` to CC:T 1.120.0's actual `SpeakerPeripheral` through the composite boundary.

This replaces the inherited fake note/sound implementations for the exposed standard methods. Runtime acceptance remains `scripts/p0_cc_speaker_contract.lua`.

### KI-002 — `speaker_audio_empty` was not real native backpressure

**Status: source-implemented in M1A; runtime acceptance pending.**

Legacy HQ synthetic `speaker_audio_empty` events are filtered. Native CC:T `playAudio` and its real `speaker_audio_empty` event remain owned by CC:T.

HQ `speakPCM` has its own queue and now uses a separate `hqspeaker_audio_empty` event only after a producer actually received `false` and queue capacity becomes available again.

### KI-003 — finite/raw source ownership could desynchronize

**Status: source-implemented for the normal single-speaker M1A path; legacy multi-speaker helpers deferred.**

The composite now tracks one explicit HQ continuous owner. A new incompatible HQ start replaces the previous HQ source; repeated `speakPCM` calls continue one RAW feed; status/control routing follows the current owner instead of stale terminal state.

Inherited `*All` / `*At` helpers still bypass this boundary and are replaced later by M1I rather than retrofitted here.

### KI-005 — raw renderer could remain alive after data drains

**Status: source lifecycle implemented for the normal M1A path; runtime timing pending.**

Accepted RAW sample duration is tracked in server ticks. Once the outbound queue is empty, the accepted duration has drained, and a short idle grace elapses, M1A issues the inherited HQ stop and releases RAW ownership.

Full client range-entry/range-exit lifecycle is still M1H work; see KI-004.

### KI-020 — provider cache could retain stale world state

**Status: source-resolved in M0.5.**

Composite cache entries are deterministically evicted on CC speaker removal, server Level unload, and server shutdown. The weak-key map is no longer relied on as the lifecycle mechanism.

## Active high priority

### KI-004 — range-local invalidation can leave stale client renderer state

Audio packets are intentionally range-local. The inherited HQ stop/control path is also currently range-local, so a client which previously rendered HQ audio can walk away before a later invalidation.

The accepted target is **not** a permanent historical listener list. M1H replaces this with dynamic range/tracking state: entering range receives current server state, leaving range destroys/parks the local renderer, and returning rejoins only if the server still says playback is active.

A tiny broad stop invalidation may be used where necessary as a safety mechanism, but it must not become playback-recipient ownership bookkeeping.

### KI-006 — retained/prototype loop disable breaks wrapped logical position

Looping position is modulo duration. The retained-M1 path did not first rebase to the current wrapped position when disabling loop.

The prototype `FinitePlaybackClock` demonstrated the corrected behavior. The final fix belongs in the server-authoritative finite engine rather than further polishing the retained engine.

### KI-007 — prototype finite anchor has no failover

The prototype can make the first successful renderer server authority. That entire model is scheduled for deletion in M1E: the server owns the finite clock regardless of client renderers.

### KI-008 — prototype later STARTED generation can skip earlier server state

Legacy/prototype `promoteLocked` behavior allows a reporting client generation to influence canonical queue order.

M1E removes client STARTED authority and Java-side finite queue semantics rather than repairing this mechanism.

### KI-009 — prototype no-renderer playback depends on renderer observation

The staged prototype has READY/STARTED observation and timeout behavior.

M1E removes renderer-observation authority. Finite playback will progress on the server even with no nearby client, and late listeners will join current state.

### KI-010 — retained finite exact-duration seek edge

The retained-M1 path can try to prime an empty renderer after seeking exactly to duration.

`FinitePlaybackClock` covers the desired semantic end behavior. Final implementation belongs to the server-authoritative finite engine.

### KI-011 — legacy finite decoder work queue is unbounded

The retained decoder executor is single-threaded with an unbounded task queue. Lifecycle tokens reject stale results but do not cancel/remove queued stale work.

Legacy finite byte APIs will migrate onto the new bounded finite engine rather than keeping this architecture.

### KI-012 — legacy whole decoded PCM allocation is duration-scaled

OGG/JavaSound legacy decode can materialize the complete decoded result before the old 64 MiB validation.

The staged prototype proved incremental decode concepts. M1G makes bounded encoded buffering and bounded mono PCM production the final finite architecture.

### KI-026 — transitional finite client still requires a complete local encoded file

The current staged client writes the entire encoded asset to a local `.part`/completed file and only constructs `FileFiniteAudioStream` after the full transfer completes.

This is no longer target architecture. M1F/M1G replace it with bounded server range streaming into temporary client RAM and progressive decode. The final path does not maintain a persistent client song cache or require a complete client file before first audio.

### KI-013 — stream volume is applied twice

`StreamingAudioSource.queuePCM()` scales decoded PCM by stream volume and the renderer applies packet volume again.

Desired result: one logical gain stage plus normal Minecraft category/master scaling. Streaming remains low priority until local finite media is solid.

### KI-014 — HLS live window progression can stall

`EXT-X-MEDIA-SEQUENCE` is parsed but not used to identify newly appeared segments. A persistent list index can reach the old playlist size and then skip all same-sized refreshed windows.

### KI-015 — direct TS path is not incrementally streaming

Direct `streamTS()` collects the demuxer's full `List<AudioFrame>` before queueing playback. Endless TS input therefore need not produce output and can grow memory.

### KI-016 — unsupported TS audio may be treated as PCM

`decodeAudioFrame` can return compressed frame bytes unchanged after unsupported JavaSound decode.

Desired result: unsupported codecs fail clearly; compressed bytes are never mislabeled as PCM.

### KI-017 — stream server state is intent, not renderer/network truth

`streamActive` describes server intent rather than confirmed client network/decode/render state. Live pause/reconnect-resume is also not implemented.

### KI-018 — inherited partial multi-speaker sync can wait forever

Expected group size is global while packet delivery is range-local. A client may receive only a subset but wait for the full count.

M1I removes expected-member barriers for final finite sync: synchronized playbacks reference a shared clock and the client renders whichever physical speakers are currently relevant.

### KI-019 — shared stream session can leak if it never starts

`forceClose()` removes a shared stream session only inside a successful `running.compareAndSet(true, false)`. A session which never reached expected taps can remain in the static map.

Streaming/group cleanup remains later work.

## Medium / cleanup

### KI-021 — advertised finite formats historically exceeded exact bundled decoder evidence

**Status: source-resolved for the frozen M1D advertisement; final format scope is being narrowed further.**

Earlier code advertised MP4/M4A/AAC without dedicated decoder evidence. Frozen M1D narrowed `speakSupportedFiles()` to formats its then-current analyzer/client path could support.

The final product target is narrower again: MP3, common WAV, and native FLAC after exact proof. OGG/AIFF/AU historical M1D support does not need to survive the replacement streaming engine.

### KI-022 — separate HQ block/group architecture appears unused or duplicated

The repo registers `hqspeaker:hq_speaker` while the actual product path upgrades normal CC speakers. Legacy All/At behavior is also duplicated inside `HQSpeakerPeripheral`.

Prove these paths are intentionally supported or remove/quarantine them before release.

### KI-023 — local finite media still has an inherited 8 MiB byte-input path

Legacy `speakMp3`/`speakOgg`/`speakWav` byte APIs still use the inherited one-shot limit and old finite engine.

The limit is **not** the target limit for CC filesystem playback. M1B–M1G replace local-file transport/decode with reusable server assets, bounded client-requested streaming, bounded temporary client RAM, and progressive decoding. Legacy byte methods later become compatibility frontends where they still make sense for the narrowed format set.

### KI-024 — documentation/testing can become stale during the redesign

P0/M0.5 established separate fact/decision/architecture/current-state documents. M1E cleanup realigns those documents with the server-authoritative, no-client-cache finite streaming plan while preserving frozen M1D facts as historical evidence.

### KI-025 — license metadata mismatch

Repository `LICENSE` is MPL-2.0 while NeoForge metadata declares LGPL-3.0.

Resolve provenance before public release; do not silently relicense.
