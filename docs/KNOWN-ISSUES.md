# Known issues / product gaps

Severity here is project priority, not a claim of exploitability.

## P0 blockers

### KI-001 — standard CC:T speaker contract is not preserved

Current replacement still reports type `speaker`, but:

- `playNote` ignores the requested instrument and requires arguments CC:T makes optional;
- `playSound` ignores the requested sound identifier;
- standard `stop()` is missing;
- `playAudio` default-volume/backpressure behavior differs from CC:T.

Desired result: HQ extends the normal speaker without breaking ordinary CC speaker programs.

### KI-002 — `speaker_audio_empty` is not real raw backpressure

Current server tick marks ready whenever the packet queue has fewer than four entries and can emit `speaker_audio_empty` repeatedly while idle.

The server packet queue is not the client's actual audible PCM capacity.

Desired result: `playAudio` acceptance and `speaker_audio_empty` form a truthful producer/backpressure contract.

### KI-003 — finite -> raw client/server desynchronization

Client raw-mode entry resets finite client playback, while server raw enqueue does not clear corresponding finite semantic tracks.

Desired result: chosen heterogeneous-submission semantics are applied coherently on both sides.

### KI-004 — old listeners can miss stop/control packets

Playback recipients are selected by current radius when each packet is sent. A client can receive playback, move away, then miss a later stop/control.

Desired result: every client which can own stale playback can receive invalidation/control.

## High priority

### KI-005 — raw renderer can remain alive after data drains

Raw `HQAudioStream` can continue returning waiting/silence rather than terminal EOF, keeping a Minecraft streaming sound/source alive after a short feed has ended.

Desired result: intentional raw idle/grace semantics with bounded resource ownership.

### KI-006 — loop disable breaks wrapped logical position

Looping position is modulo duration. Disabling loop does not first rebase to the current wrapped position.

Desired result: disabling loop preserves the current audible-cycle position.

### KI-007 — finite anchor has no failover

First successful renderer becomes the server anchor. Later PAUSED/RESUMED/SEEKED/ENDED reports from another renderer are rejected even if the anchor disappears.

### KI-008 — later STARTED generation can skip earlier server state

`promoteLocked` removes finite tracks before the reporting generation. Different clients may have received different generations.

Desired result: canonical queue order cannot be advanced by a client which missed an earlier item.

### KI-009 — no-renderer finite playback can remain loading indefinitely

Finite media is one-shot delivered and encoded bytes are not retained server-side for late resend.

Desired result: explicit no-observer policy (timeout/error, resend/session retention, or another deliberate behavior).

### KI-010 — seek exactly to duration has an empty-renderer edge

A non-looping seek to duration positions the retained cursor at EOF, but current client logic can still try to prime a renderer.

Desired result: clean `ended` transition without requiring an empty Minecraft stream to start.

### KI-011 — finite decoder work queue is unbounded

The decoder executor is single-threaded with an unbounded task queue. Lifecycle tokens reject stale results but do not cancel/remove queued stale jobs.

### KI-012 — decoded PCM cap is checked after whole allocation

OGG native decode and JavaSound `readAllBytes()` can materialize the complete decoded result before the 64 MiB validation.

Desired result: resource bounds apply before/until allocation, not only afterward.

### KI-013 — stream volume is applied twice

`StreamingAudioSource.queuePCM()` scales decoded PCM by stream volume and `HQSpeakerSound` applies packet volume again.

Desired result: one logical gain stage plus normal Minecraft category/master scaling.

### KI-014 — HLS live window progression can stall

`EXT-X-MEDIA-SEQUENCE` is parsed but not used to identify newly appeared segments. A persistent list index can reach the old playlist size and then skip all same-sized refreshed windows.

### KI-015 — direct TS path is not incrementally streaming

Direct `streamTS()` collects the demuxer's full `List<AudioFrame>` before queueing playback. Endless TS input therefore need not produce output and can grow memory.

### KI-016 — unsupported TS audio may be treated as PCM

`decodeAudioFrame` returns compressed frame bytes unchanged on `UnsupportedAudioFileException`.

Desired result: unsupported codecs fail clearly; compressed bytes are never mislabeled as PCM.

### KI-017 — stream server state is intent, not renderer/network truth

`speakIsPlaying`/`audioStatus` use server `streamActive`, which is not cleared by arbitrary client network/decode failure.

Live pause/reconnect-resume is also not implemented.

### KI-018 — partial multi-speaker sync can wait forever

Expected group size is global while packet delivery is range-local. A client may receive only a subset but wait for the full count.

Shared streaming groups have the same expected-tap problem.

### KI-019 — shared stream session can leak if it never starts

`forceClose()` removes the session only inside a successful `running.compareAndSet(true, false)`. A session which never reached expected taps can have `running=false` and remain in the static map.

### KI-020 — provider cache can retain stale world state

Provider cache key is dimension+block position while the value stores a concrete `Level`. `forget()` exists but has no current call site.

## Medium / cleanup

### KI-021 — advertised finite formats exceed bundled decoder evidence

`speakSupportedFiles()` advertises MP4/M4A/AAC. The repo bundles MP3SPI/JLayer/Tritonus and relies on JavaSound for generic formats; no dedicated AAC/MP4 decoder is bundled.

Do not advertise a format as supported without exact runtime/decoder evidence.

### KI-022 — separate HQ block/group architecture appears unused or duplicated

The repo registers `hqspeaker:hq_speaker` and contains `HQSpeakerGroupPeripheral`, while the actual product path replaces normal CC speakers and duplicates All/At methods on `HQSpeakerPeripheral`.

Prove these paths are intentionally supported or remove/quarantine them later.

### KI-023 — finite encoded media is capped at 8 MiB

The limit exists in both peripheral and packet code and whole encoded content is sent in one packet.

Do not fix by blindly raising constants.

### KI-024 — documentation/testing was stale

Prior docs mixed inherited pre-M1 facts with current M1 facts and described the project as an audio-player mod.

P0 rewrites the documentation around the programmable-peripheral model and adds a compatibility/test matrix.

### KI-025 — license metadata mismatch

Repository LICENSE is MPL-2.0 while NeoForge metadata declares LGPL-3.0.

Resolve provenance before public release.
