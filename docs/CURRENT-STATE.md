# Current state

## Active references

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

- untouched inherited fork baseline: `d1a592351c866f9a28ceef00b59e591ee773f3d5`
- reviewed historical M1 reference: `fba84a33a94d451af09b983bcb04416c97ff64cf`
- frozen staged/local-file prototype: `69e34a5346f6ce47580f49ed867c9951bfd338bc`
- completed M0.5 preparation: `ad38412a2173f849a0fc8e867030da8a78965c9c`
- M1A compatibility/output branch: `codex/m1a-compat-output`
- completed M1B storage foundation: `40091ee32f412c1208e9016fca288b8d4f902dfa` on `codex/m1b-media-assets`
- current implementation branch: `codex/m1c-local-import`

Target stack:

- Minecraft 1.21.1
- Java 21
- CC:Tweaked 1.120.0
- NeoForge 21.1.247 baseline
- NeoForge 21.1.248 compatibility
- future SPR 1.21.1-1.5.1 compatibility

## Product identity

The mod upgrades the normal CC:Tweaked speaker into a programmable ComputerCraft audio peripheral. Java exposes technical audio capabilities; Lua owns application policy such as sequencing, playlists, alarms, notifications, and similar behavior.

Technical source categories remain:

- standard CC:T speaker behavior;
- HQ raw/feed PCM;
- finite encoded media with a truthful timeline;
- live/open-ended network streams later.

## M1A inherited behavior

The current branch retains the M1A single-speaker compatibility/output work:

- standard `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty` delegate to CC:T's real `SpeakerPeripheral`;
- native notes remain independent;
- one incompatible HQ continuous source replaces the prior HQ source; Java does not queue a playlist;
- `audioStatus()` follows the current HQ source instead of stale terminal state from another subsystem;
- `audioStop()` stops the current HQ source;
- `speakMaxSamples()` reports the real 131072-sample contiguous table ceiling;
- HQ RAW has separate `hqspeaker_audio_empty` admission/backpressure behavior;
- RAW admission is bounded by the inherited 16-packet queue plus 135872 outstanding samples and drains at 2400 samples/server tick;
- ownership-changing calls on one physical speaker run one at a time.

M1A Minecraft acceptance remains pending until its runtime scripts are actually run in-game.

## M1B completed storage foundation

M1B completed the reusable encoded-file primitive at exact head `40091ee32f412c1208e9016fca288b8d4f902dfa`.

`MediaAssetStore` now provides:

- UUID media identity independent of speakers;
- exact-size disk-backed import through `.part` then atomic `.media` publication;
- bounded copy buffers rather than whole-file RAM reads;
- caller-supplied per-asset and total quotas with pre-copy reservation;
- `retain`/`release` reference lifetime;
- final-reference file deletion;
- seekable encoded-file reads;
- startup orphan pruning;
- a root OS file lock;
- shutdown/import race handling and retryable close cleanup.

The exact M1B head passed the Java 21 NeoForge 21.1.247/21.1.248 CI matrix including tests, package verification, and candidate-JAR upload.

See `docs/M1B-MEDIA-ASSETS.md`.

## M1C local-file import state

M1C connects ComputerCraft-visible files to the M1B server-wide asset store.

The current local-file flow is now:

```text
CC filesystem file
    -> temporary writable speaker staging mount
    -> shared server MediaAsset UUID
    -> prepared-owner reference
    -> playback reference when started
```

Important current behavior:

- `HQMediaStaging` owns the writable ComputerCraft mount; `HQFiniteMediaServer` no longer owns staging;
- `ServerMediaAssets` owns one shared `MediaAssetStore` per running `MinecraftServer`;
- current per-asset/staging ceiling is 512 MiB;
- the total shared-store cap is currently an interim 2 GiB implementation value and is **not** a settled product policy;
- successful prepare imports encoded bytes into the server-wide store and returns an asset UUID;
- prepared ownership is tracked by ComputerCraft computer ID;
- detaching the preparing computer releases prepared references it still owns;
- `audioPlayPrepared` takes its own playback reference before returning success;
- releasing the preparation reference therefore does not stop or delete an active playback;
- the same asset UUID may deliberately be played by another speaker because media identity is server-wide rather than speaker-owned;
- server shutdown cleans speaker/prepared/playback references first, then closes the shared media store;
- a failed store close remains reachable so cleanup can be retried.

New peripheral capabilities:

- `audioPrepareStaged(path [, consume]) -> assetId`
- `audioPlayPrepared(assetId [, volume]) -> boolean`
- `audioReleasePrepared(assetId) -> boolean`
- existing `audioMountPath()` / `audioMaxStagedBytes()`

The bundled `hqspeaker` Lua module now exposes:

- `prepareFile(speaker, path)`
- `playPrepared(speaker, assetId [, options])`
- `releasePrepared(speaker, assetId)`
- `playFile(speaker, path [, options])` as prepare -> play -> release convenience behavior.

The helper checks file size before copying and removes partial staging files when `fs.copy` fails. Once shared-asset import succeeds, inability to delete temporary staging does not invalidate the valid asset; Java logs the cleanup failure and Lua retries deleting the staged path.

See `docs/M1C-LOCAL-IMPORT.md`.

## Transitional finite playback still present

M1C changes file ownership/import but deliberately does not pretend the old finite sender is final.

Prepared assets currently bridge into `HQFiniteMediaServer`, which still uses prototype behavior:

- fixed player recipients captured at playback start;
- server-pushed begin/chunk/end whole-file transfer;
- client READY/STARTED/ENDED reports affecting the playback clock;
- renderer-observation timeout;
- no dynamic late join from authoritative server state.

A prepared playback keeps its own asset reference while active and releases it on stop/end/error/failed transfer. `audioStatus()` identifies prepared playback with `assetId`.

M1E replaces client renderer authority with a server-owned finite clock. M1F replaces the fixed-recipient push transfer with bounded client-pulled asset ranges.

## Multi-speaker boundary

The inherited `*All` / `*At` helpers still bypass the modern single-speaker ownership path and retain the old expected-group/tap design. They are scheduled for replacement in M1J rather than being patched onto architecture already marked for removal.

## Runtime/testing state

Pure Java coverage includes the existing M1/M1A tests plus M1B asset import, quotas, reference lifetime, concurrent reservations, crash cleanup, root locking, and shutdown/import behavior.

Runtime scripts relevant now:

- `scripts/p0_cc_speaker_contract.lua`
- `scripts/m1a_output_contract.lua [optional-small-mp3]`
- `scripts/m1c_local_import_test.lua <path-to-mp3/ogg/wav>`

The M1C script checks prepare/release, released-asset rejection, prepared playback, independent playback reference lifetime, `audioStatus().assetId`, and the `hqspeaker.playFile` convenience path.

None of these should be reported as a runtime PASS until they are actually executed successfully in Minecraft on the target stack.

## Next implementation milestones

- M1D: server media format/duration analysis without whole-track PCM decode;
- M1E: server-authoritative finite playback state/clock/EOF;
- M1F: bounded client-pulled asset transfer and worker-thread IO;
- M1G/H: reusable client cache and hardened incremental decode;
- M1I/J: dynamic range rendering and shared multispeaker assets/sync clocks.

Other retained issues such as old finite byte APIs, live HLS/TS behavior, sound-category normalization, SPR integration, and the license metadata mismatch remain later roadmap work.
