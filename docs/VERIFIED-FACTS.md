# Verified facts

Facts only. Recommendations belong in architecture/roadmap.

## Repository/platform

### FACT-FORK-001
This project is `ztawfik523-lgtm/CC-HQ-Speakers`, forked from `jvrcruzGAMES/CC-HQ-Speakers`, with original lineage from `tiktop101/CC-HQ-Speakers`.

### FACT-FORK-002
The untouched fork baseline commit is:

`d1a592351c866f9a28ceef00b59e591ee773f3d5`

### FACT-PLATFORM-001
The inherited port uses Minecraft 1.21.1, Java 21, NeoForge 21.1.211, and CC:T 1.113.1.

### FACT-PLATFORM-002
This fork targets Minecraft 1.21.1, Java 21, CC:T 1.120.0, NeoForge 21.1.247 baseline, and NeoForge 21.1.248 compatibility.

### FACT-PLATFORM-003 — resolved CC:T artifact
The published and Gradle-resolved runtime artifact for CC:T 1.120.0 on the
Minecraft 1.21.1 NeoForge stack is:

`cc.tweaked:cc-tweaked-1.21.1-forge:1.120.0`

The upstream project retains the `forge` artifact name for its NeoForge build;
there is no separate `-neoforge` Maven coordinate for this target.

### FACT-PLATFORM-004 — exact NeoForge matrix
The build defaults to NeoForge `21.1.247`. The `neoForgeVersion` Gradle property
selects another exact compatible target, including `21.1.248`.

Clean `build` runs succeeded on both exact versions with Java 21. Dedicated
development-server runs on both versions loaded CC:T 1.120.0 and HQ Speakers,
registered all three payloads, and reached the Minecraft `Done` marker.

### FACT-PLATFORM-005 — NeoForge 21.1.247 client baseline
On 2026-09-09, a client run loaded Minecraft 1.21.1 on Java 21.0.7 with
NeoForge 21.1.247, CC:T 1.120.0, and CC:HQ Speakers 1.1.4. The HQ speaker mixin
applied and ComputerCraft generated wrappers for the inherited HQ Lua methods.
This was the exact target stack in a small test instance, not evidence for the
entire ATM10 mod set.

## Inherited CC:HQ behavior/source

### FACT-CCHQ-001 — finite encoded limit
`HQSpeakerPeripheral.SPEAKER_MAX_AUDIO` is `8 * 1024 * 1024`.

### FACT-CCHQ-002 — network finite limit
`HQSpeakerAudioPacket.MAX_BYTES` is also `8 * 1024 * 1024`.

Finite media packets carry the encoded content in a `byte[]`.

### FACT-CCHQ-003 — current looping state is not transported
`HQSpeakerPeripheral` has a server-side `looping` boolean and `setLooping(boolean)` updates it.

`HQSpeakerAudioPacket` contains no looping field.

`HQSpeakerClientHandler.HQSpeakerSound` explicitly sets its Minecraft `looping` field to `false`.

### FACT-CCHQ-004 — current finite playing query is not client renderer truth
`HQSpeakerPeripheral.speakIsPlaying()` returns:

`!speakerQueue.isEmpty() || streamActive.get()`

It does not query `HQSpeakerClientHandler` or Minecraft's client-side active sound state.

### FACT-CCHQ-005 — finite decode worker exists
`HQAudioStream` uses a single-thread executor whose thread is named `HQSpeaker-Decoder`.

### FACT-CCHQ-006 — finite decode is whole-file
The inherited OGG path uses `STBVorbis.stb_vorbis_decode_memory`.

The generic JavaSound path converts via `AudioInputStream` and reads the decoded result using `readAllBytes()`.

### FACT-CCHQ-007 — decoded finite limit
`HQAudioStream.MAX_DECODED_PCM_BYTES` is `64 * 1024 * 1024`.

### FACT-CCHQ-008 — stream waiting can return silence
`HQAudioStream.read()` can return a direct silence buffer while finite decode/prebuffer data is not yet available.

### FACT-CCHQ-009 — client renderer state exists
`HQSpeakerClientHandler` tracks client `SpeakerState` objects containing an `HQAudioStream`, `HQSpeakerSound`, last packet, streaming state, start tick, and sync-group state.


### FACT-CCHQ-010 — CC:T artifact coupling
The inherited NeoForge build depends on the Forge-suffixed CC:Tweaked artifact:

`cc.tweaked:cc-tweaked-1.21.1-forge:1.113.1`

M0 confirmed that the 1.120.0 artifact retains the same Forge-suffixed module
name. See FACT-PLATFORM-003.

### FACT-CCHQ-011 — distinct PCM conventions
The inherited Lua API exposes both `playAudio(IArguments)` and
`speakPCM(IArguments)`.

- `playAudio` accepts CC:T-style signed 8-bit samples (`-128..127`) and converts
  them to the internal signed 16-bit little-endian path.
- `speakPCM` accepts signed 16-bit samples (`-32768..32767`).

The group/all/index variants preserve the same distinction.

### FACT-CCHQ-012 — baseline cleanup coverage
Server peripheral detach/block cleanup clears queues/stream flags and broadcasts
a stop packet. Client stop closes the Minecraft sound and audio source. The
baseline source has no explicit client disconnect or resource-reload cleanup
hook; those cases remain runtime smoke-test observations.

### FACT-CCHQ-013 — M0 audible client observations
The M0 NeoForge 21.1.247 client run audibly exercised signed 8-bit
`playAudio`, signed 16-bit `speakPCM`, volume changes, and a 2,871,486-byte
finite MP3. Both PCM calls returned true; the tester reported the signed 16-bit
tone sounded cleaner than the signed 8-bit tone. Finite MP3 playback sounded
normal. World disconnect and integrated-server shutdown completed without an
HQ exception in the supplied logs.

The same run did not establish audible OGG, URL streaming, multi-speaker/sync,
or resource-reload behavior. Stop, WAV, generic finite playback, and looping
were inconclusive because a long MP3 remained active. These are not recorded as
runtime-verified successes.

## M1 player-core source facts

### FACT-M1-001 - finite generations and retained PCM
Finite media packets carry a per-speaker generation. Each client logical track
retains fully decoded signed-16-bit mono PCM and its exact sample rate. Renderer
cursors are frame-aligned views over those retained bytes.

### FACT-M1-002 - real channel pause and category-aware volume
The M1 client accesses the active `ChannelHandle` through client-only Mixin
accessors. Pause/resume call `Channel.pause()` / `Channel.unpause()`. Live volume
updates the sound value and calls `SoundManager.updateSourceVolume` with the
unchanged BLOCKS slider value, preserving Minecraft category and master scaling.

### FACT-M1-003 - finite status protocol
Client status is transition-driven and generation-validated. The server rejects
unknown generations, wrong-world or out-of-range senders, non-finite/out-of-range
timing values, and oversized errors. No renderer confirmation leaves
`observed=false` and does not start the position clock.

### FACT-M1-004 - seek and loop
Seek creates a fresh renderer cursor at a clamped retained-PCM frame and stops
the prior Minecraft sound so queued old buffers are discarded. Loop rewinds the
cursor without retransmission or decode and does not use SoundInstance looping.

## HighAudio transferable facts

### FACT-MC-001
On the exact HighAudio target stack, `AudioStream.read()` was observed running on the `Sound engine` thread.

### FACT-MC-002
In the tested 48 kHz/16-bit/mono HighAudio path, Minecraft requested 96,000-byte reads.

### FACT-MC-003
HighAudio source/runtime investigation found one non-null read result mapping to one SoundBuffer/OpenAL upload/queue operation in the inspected path.

### FACT-MC-004
Vanilla Minecraft streaming reservation measured eight streams on the tested baseline runtime.

### FACT-MC-005
A total-preserving reservation rebalance was real-client proven to provide 16 Minecraft-owned streaming channels on that tested runtime.

Do not apply the reservation change during bootstrap unless CC:HQ demonstrates the same need.

## Codec facts from HighAudio

### FACT-CODEC-001
The tested LWJGL/STBVorbis path supported open-memory decode, info, total samples, incremental interleaved-short decode, seek, error reporting, and close.

### FACT-CODEC-002
Repeated 257-frame incremental reads reconstructed the tested OGG files.

### FACT-CODEC-003
The isolated STB experiment observed native heap corruption when direct encoded input was not kept sufficiently reachable. Strong retention through native close plus explicit reachability fencing eliminated the observed corruption across recorded stress repetitions.

This is project evidence, not a universal JVM theorem.

## SPR

### FACT-SPR-001
Frozen approved V7.1 acoustic baseline:

`ffcf5f6e05d85b69f1f1dff8cfae1b082b71604d`

### FACT-SPR-002
Approved V7.1 runtime JAR SHA-256:

`30d457c2a52672f893b1076938e2fdea3f41759173dfd843ff652bd490692101`

### FACT-SPR-003
Later compat hardening includes bounded decoder work, decoded-cache limits, stale session/source-generation checks, OpenAL cleanup hardening, and pause/resume/stopAll/emergencyShutdown integration.

## License

### FACT-LICENSE-001
GitHub identifies the repository-level license as MPL-2.0, while inherited mod metadata declares LGPL-3.0.

Resolve deliberately before release; do not silently relicense inherited source.
