# Testing

Updated: 2026-09-20

## Evidence rule

CI proves compilation, deterministic tests and package structure. It does **not** prove audibility, Minecraft SoundManager/OpenAL lifecycle, movement behavior or real-world performance.

Target stack:

- Java 21
- Minecraft 1.21.1
- CC:Tweaked 1.120.0
- NeoForge 21.1.247
- NeoForge 21.1.248

## Current green source checkpoint

`2377aae3bde94d3393f21525697198fb8645f354`  
CI `35480935418`

Both supported NeoForge targets passed build/tests/package verification/artifact upload.

This checkpoint includes modern finite v9, M1H/M1J source work, finite convergence/teardown, RAW admission refactor, dependency cleanup, standalone-block removal, MPL metadata correction, internal sound-resource rename, completed modern/core multi-endpoint coordination hardening, and dead singular legacy playback cleanup with the public composite method surface preserved.

`OrderedMultiLockTest` deterministically covers opposite target ordering, overlapping target groups, complete lock ownership during the transaction, and fail-fast rejection when a caller enters with a target lock already held. Full `HQSpeakerCompositePeripheral` behavior still depends on Minecraft/CC:T integration, so green CI is not a substitute for the deferred real concurrent-control stress pass.

## Major checkpoints

- M1G: `fa679ffcb81a66fd99ab6be8e6d6b77895fbc542` / CI `35297026277` / focused audible core PASS on NeoForge 21.1.247.
- post-M1G hardening: `3d30ce4564de749f32171666df65de739b08ad77` / CI `35406595856`.
- M1H-1: `84e7bab99009e5871934a960908945ceb00a10a9` / CI `35410830197`.
- M1H-2: `aa72f0d2fc9f8cde53cd956389beca1743d06165` / CI `35411480844`.
- M1H-3: `5cd6d6ddcad4b5b4887b903f471de0f2f812795c` / CI `35451236630`.
- M1J first shared-playback checkpoint: `b557773b9c6f6b8029aec132a1706f0d8da914bd` / CI `35466635285`.
- finite teardown: `fcb6670dd818412c15509129105aa7f54be9d5ba` / CI `35470940030`.
- RAW barrier/admission: `b6866ce99810f1b449d0066c3d25cf6bc7d3baaf` / CI `35471254482` and `c728a9076073e7a19a7a016f8eaf8e82c6ce68ac` / CI `35471357327`.
- standalone block/license: `ce12a8bca2d68e7a6ebfaf106c4206508c26bb98` / CI `35474632162`.
- internal sound rename: `00b07db41c003363cef60ef8ec4134fa387c421c` / CI `35474944518`.
- finite endpoint lock-order hardening: `8349d0883c2521506bbfdaac4546981c1e31af3e` / CI `35477934035`.
- composite multispeaker transaction/control hardening: `68314efe2e7ccbaa73e273044389ea43fea70530` / CI `35478810268`.
- dead singular legacy playback cleanup / RAW cap normalization: `2377aae3bde94d3393f21525697198fb8645f354` / CI `35480935418`.

## Package verification

Checks:

- `META-INF/neoforge.mods.toml`
- mixins config
- Jar-in-Jar metadata
- JLayer 1.0.1.4
- Sable Companion 1.6.0
- bundled ComputerCraft Lua module

## Deferred focused Minecraft matrix

### Listener membership

Outside-at-start, enter within 32 blocks, no restart spam while staying, leave cleanup, re-entry at current time, dimension/disconnect cleanup, terminal/replacement cleanup.

### Recovery

Resource reload, renderer/SoundEngine loss, READY retry, sustained starvation, pause/mute/restart interactions.

### Movement

Sable/Aeronautics moving sublevel, VS2, static regression, and server relevance following movement. The prior parent-world Level-identity concern is closed at source-model level; runtime still needs to prove the projected movement path behaves correctly in Minecraft.

### Multispeaker

Use 2, 4 and 8+ speakers where practical. Verify one shared timeline, relevant endpoint subset, late entry, group pause/resume/seek/loop, endpoint-local volume/mute, All gain/mute snapshot semantics, endpoint removal/replacement, and detaching one endpoint via new playback.

### RAW

Verify signed-16 audibility, repeated chunks as one feed, false on capacity rejection, `hqspeaker_audio_empty` after observed rejection, All preflight, common start tick with no expected-member deadlock, and ownership release after drain/grace.

### Bounds/stress

Malformed/extreme MP3/WAV, long-media memory bounds, range-request bounds, worker shutdown/restart, many listeners/speakers, repeated start/stop/replace/seek.

### Sound Physics Remastered

Verify normal SoundManager processing and profile many simultaneous sources. Do not add custom raytracing unless runtime proves a gap.

## Optional live tests

Only if live features remain supported/promoted: MP3 URL/ICY, HLS refresh across windows, TS, grouped-stream partial-listener behavior, shutdown/DNS failure.

## Historical scripts

Older scripts may target retired APIs. Treat them as historical until reviewed against the current v9 API. Before release, create/refresh a concise current acceptance script set.
