# Known issues / product gaps

Severity here is project priority, not a security claim. Green source/CI is not Minecraft runtime proof.

Current checkpoint: **M1E + M1F complete at source/test/CI/package level; M1G integrated engine in progress.**

Current green integrated M1G source checkpoint: `957832348eaa6e497282d923f2312c9c7d7c550f`.

CI `34778546164` passed NeoForge 21.1.247 and 21.1.248 including tests, package verification, and artifact upload.

Repository/source audits on 2026-09-14 found KI-053 through KI-061 below. Those audits changed documentation only; no source fix was made.

## Runtime evidence gaps

### KI-036 — final M1E manual runtime PASS is not recorded

**Intentionally skipped by project owner; do not claim PASS.**

### KI-037 — M1F focused Minecraft transport acceptance is not recorded

**Active evidence gap, not a source/test blocker.** M1F source/test/CI/package/component work is complete.

### KI-046 — M1G audible Minecraft acceptance is not recorded

**Active.** The progressive decoder/renderer is integrated in source, but there is no recorded focused Minecraft audible PASS. CI cannot prove audibility, attenuation, channel lifecycle, starvation/refill, seek quality, or loop-boundary behavior.

## Resolved by M1E/M1F

- KI-040: projection escaping canonical transitions — resolved by best-effort per-recipient projection.
- KI-041: `playPrepared()` ghost-session rollback — resolved.
- KI-042: failed final MediaAsset release losing retry ownership — resolved for active prepared/playback/range paths.
- KI-045: finite server ERROR advancing time — resolved; ERROR freezes position.
- KI-043: encoded window not progressively consumable — resolved by bounded forward sliding.
- KI-044: incomplete M1F deterministic matrix — resolved at component level.
- KI-026: modern prepared client requiring a complete song file — resolved.
- KI-027: finite asset reads on server tick — resolved by bounded range IO workers.
- KI-030: no demand-driven finite protocol — resolved.
- KI-031: missing encoded data indistinguishable from EOF — resolved.
- KI-038: `audioPlayStaged()` prototype route — removed.
- KI-039: modern CHUNK/END whole-file path — removed.

## M1G source-resolved items

### KI-021 — modern finite format surface needed narrowing

**Resolved for modern prepared/local playback.** New prepared assets are gated to MP3 or supported common WAV. Historical OGG/AIFF/AU support may still exist in inherited code and does not define the modern prepared surface.

### KI-032 — MP3 Layer III seek/rejoin needs pre-roll

**Resolved in source.** Server anchors use E1 conservative earlier seek points and JLayer decoding discards pre-target PCM. Audible seek proof remains part of KI-046/KI-055.

### KI-033 — historical WAV acceptance broader than converter target

**Resolved for modern prepared playback.** Common WAV is narrowed to U8/S16/S24/S32/F32 mono/stereo, classic RIFF/WAVE or the selected narrow PCM/float WAVEX subset.

### KI-047 / KI-048 / KI-049 / KI-050 — client integration, WAV decoder, MP3 decoder, renderer

**Resolved in source.** `HQFiniteMediaClient` owns decoder epochs; `ProgressiveWavDecoder`, `ProgressiveMp3Decoder`, `FinitePcmQueue`, `FinitePcmAudioStream`, and `FiniteSpeakerSound` are integrated. Focused component/runtime proof is still incomplete under KI-052/KI-055.

## Active high priority — M1G

### KI-051 — looping finite playback needs an authoritative loop-wrap rejoin policy

**Active owner decision.** The server clock already wraps canonically. After local physical EOF during a looping session, the client needs a defined way to obtain/create the next decoder epoch.

Options:

- L1 — client EOF requests fresh authoritative STATE;
- L2 — server detects canonical wrap and proactively projects STATE;
- L3 — client predicts/restarts locally and reconciles later.

See `CURRENT-STATE.md` and `HANDOFF-2026-09-13-M1G-START.md`. Do not choose silently.

### KI-052 — focused integrated M1G control/lifecycle proof is incomplete

Source paths exist for pause/resume, seek/replacement, volume, stop, catch-up discard, decoder cancellation, and renderer-close cancellation. Missing proof includes real MP3 integration, repeated seeks, long starvation/refill, renderer adapter lifecycle, loop-wrap behavior after KI-051, and actual positional output.

### KI-053 — same-anchor STATE can reset a slid encoded window without restarting the decoder

**Active source correctness issue. No fix has been applied.**

`HQFiniteMediaClient.state0()` resets the `FiniteRangeWindow` whenever the server anchor is outside the client's current window. If the coarse anchor itself is unchanged, `restart` can remain false, so an already-advanced decoder keeps its cursor while the window is reset backward to old bytes.

That can cause redundant refetch/latency and can make a live decoder cursor stale if it no longer fits the reset fixed-size window.

A semantic seek is different and must still create a new decoder epoch even when the selected coarse anchor byte is unchanged.

Required regression coverage: slide forward, apply an ordinary same-anchor STATE without rewind/failure, then separately prove same-anchor semantic SEEK still restarts decoder state.

### KI-056 — SEEK cancellation can report expected cancellation as a fatal decoder failure

**Active source correctness issue found by the deeper 2026-09-14 client audit. No fix has been applied.**

`CONTROL SEEK` calls `cancelDecodeEpoch()` and only then sets `restartRequested`. `cancelDecodeEpoch()` stops/closes the renderer, cancels the task/input/PCM queue, but does not invalidate `session.decodeEpoch`. An old decoder worker which wakes because of that expected cancellation can therefore schedule `decoderFailed(session, oldEpoch, ...)` before the post-seek authoritative STATE creates the replacement epoch. At that moment the session is still nonterminal and `decodeEpoch` still matches, so the client can fail and remove a perfectly valid playback during a normal seek.

Any fix must invalidate the old worker token **before** cancellation can wake the worker, and needs a deterministic cancellation-order regression.

### KI-057 — STATE does not distinguish a timeline snapshot from decoder re-anchor intent

**Active protocol/client coordination issue. No fix has been applied.**

Every authoritative STATE carries a time-derived codec anchor. Current client logic treats `anchorChanged` as a reason to reset/restart the decode epoch. The server also sends STATE after ordinary pause/resume/volume/loop controls. For WAV the exact frame anchor changes with time, so even a normal volume change can tear down and recreate a healthy decoder/renderer. For MP3 this happens when the coarse seek point advances.

The opposite failure also exists: semantic seek correctness for a same coarse anchor currently depends on the preceding `CONTROL SEEK` setting `restartRequested`. If that best-effort control projection fails while the authoritative STATE still arrives, STATE alone does not identify the operation as a required fresh codec epoch.

This is broader than KI-053. The implementation needs an explicit distinction between ordinary state reconciliation and authoritative decoder re-anchor/revision. A protocol-level decode/reanchor revision is one possible design; a smaller client-only patch has less churn but leaves more ordering/projection coupling. The owner should choose the intended approach before coding the fix.

### KI-058 — live modern-finite volume updates leave attenuation distance stale

**Active renderer correctness issue. No fix has been applied.**

`HQFiniteMediaClient.setVolume()` mutates `FiniteSpeakerSound.volume` and calls `SoundManager.updateSourceVolume(BLOCKS, slider)`, but it never updates the live OpenAL/Minecraft channel's linear attenuation distance.

CC:T 1.120.0 explicitly works around this Minecraft behavior in its own `SpeakerInstance`: when speaker volume changes it calls `channel.linearAttenuation(Math.max(volume, 1) * sound.getSound().getAttenuationDistance())` because SoundEngine refreshes gain but leaves attenuation stale.

Modern finite volume changes should either mirror that behavior or deliberately document a different volume/range contract. Runtime acceptance must check both gain and audible distance when changing volume above and below 1.

### KI-059 — fixed 32-block server relevance conflicts with the supported 0..3 volume range

**Active product/transport decision. No fix has been applied.**

`HQFiniteMediaServer` uses a fixed `SPEAKER_RADIUS = 32.0` for BEGIN/STATE/range relevance. Minecraft/CC:T speaker volume supports values through 3, where values above 1 primarily extend audible distance; with the normal 16-block attenuation base, volume 3 can be audible to about 48 blocks. A player between 32 and 48 blocks can therefore be within the renderer's intended audible range yet never receive modern finite BEGIN/ranges.

Reasonable fixes have real tradeoffs: use a fixed maximum transport radius (simple, extra network/decode work), make relevance dynamically follow volume (efficient but pulls late-entry/leave lifecycle into the problem), or deliberately cap modern finite audible range to the transport radius (simpler but diverges from normal speaker volume semantics). Do not choose silently.

### KI-060 — renderer startup is latched before SoundManager proves the sound actually started

**Active renderer robustness issue. No fix has been applied.**

`tryStartRenderer()` sets `rendererStarted = true` immediately before calling `SoundManager.play(sound)`. There is no later path which clears/retries that latch if Minecraft never allocates/starts the sound. This matters especially for the known Minecraft interaction where a sound started at effective volume zero may never actually start; a later `audioSetVolume()` can then leave the finite session permanently silent locally even though the server clock keeps advancing.

Minecraft exposes `SoundInstance.canStartSilent()` specifically for long-lived sounds which should be allowed to start while currently inaudible. Modern finite playback therefore has two reasonable implementation directions: allow the streaming sound to start silent and continue consuming at canonical time, or defer local renderer creation while inaudible and catch up before starting when unmuted. The owner should choose the desired resource/complexity tradeoff; the current one-way `rendererStarted` latch needs fixing either way.

### KI-054 — `MediaAssetStore.close()` does not retain failed shutdown deletions for retry

**Active low-frequency shutdown cleanup issue. No fix has been applied.**

Normal final release keeps bookkeeping alive if file deletion fails. `MediaAssetStore.close()` instead clears completed entries before deletion attempts and marks close cleanup complete even when a deletion throws, so a later `close()` cannot retry those completed files.

There is an additional shutdown-lifetime consequence: `ServerMediaAssets.closeServer()` removes the stopped server from its static registry only after `store.close()` succeeds. If `store.close()` throws, registry removal is skipped; the server-stop hook currently catches/logs that exception and does not schedule another close. In a long-lived JVM/integrated-server restart scenario, the stopped server/services can therefore remain strongly reachable until process exit unless another explicit retry occurs.

Next startup orphan pruning should normally recover the managed files, so this is not active playback corruption. It still needs explicit shutdown-deletion-failure and failed-close registry-lifetime coverage before being called resolved.

### KI-055 — current test/script surface mixes modern M1G acceptance with historical legacy tests

**Active testing/documentation gap.**

The Java suite strongly covers transport, common-WAV analysis/conversion, starvation-aware encoded input, PCM queue behavior, and server state. It does not currently run a known real MP3 fixture through the full JLayer progressive decoder across window progression/starvation, and there is no focused `FinitePcmAudioStreamTest` in the current client test tree.

Several Lua scripts are historical/legacy. In particular `scripts/m1d_media_analysis_test.lua` expects the old broad M1D prepared format surface and must not be used as a current M1G pass/fail gate. `m1_player_test.lua` and `p0_finite_regression.lua` primarily exercise inherited byte-taking finite APIs, not the modern prepared path.

`TESTING.md` is the authoritative current matrix until those scripts are replaced/reworked.

### KI-061 — per-speaker staging mounts can leave unreachable files on disk

**Active storage-lifecycle issue found by the 2026-09-14 storage sweep. No fix has been applied.**

Each `HQMediaStaging` instance creates a persistent ComputerCraft save-directory mount under a fresh random path such as `hqspeaker/staging/<uuid>`. `cleanup()` detaches computers and releases prepared-asset ownership, but it does not clear arbitrary files still present in that writable staging mount.

CC:T 1.120.0 implements `createSaveDirMount()` as a normal persistent `WritableFileMount` rooted at the requested server-storage subdirectory. Unmounting or dropping the Java mount object does not delete that directory. A program using the low-level staging API, a failed/interrupted copy, or any other leftover staged file can therefore become unreachable when the speaker/composite is destroyed and later recreated with a new random staging ID. Repeated speaker lifecycle churn can accumulate those files across restarts.

The high-level `hq.playFile()` path normally deletes/consumes its temporary staging file, so this is not ordinary successful-playback corruption. It is still a disk-leak/lifecycle gap and is potentially more practically reachable than the shutdown-only KI-054 edge.

A likely fix is to clear the mount contents when the whole `HQMediaStaging` object is being cleaned up, after all attached computers are unmounted. Do not clear the shared mount on one computer's ordinary `detach()`. Add failure handling and deterministic coverage for leftover-file cleanup.

## Active listener lifecycle — M1H

### KI-004 — complete leave/re-enter lifecycle is not final

M1H owns proactive out-of-range cleanup, late-entry discovery, return/rejoin, dimension/world/resource-reload recovery, robust general underrun rejoin, and final VS2 movement lifecycle.

## Gated/later work

- KI-034: native FLAC remains gated M1I work.
- KI-018: inherited expected-member multispeaker barrier can deadlock partial listeners — M1J.
- KI-011 / KI-012 / KI-023: inherited finite engine/APIs remain legacy — M1L.
- KI-013 through KI-019: inherited live/HLS/TS/OpenAL lifecycle/gain issues — M3/later cleanup.
- KI-022: separate `hqspeaker:hq_speaker` registration remains a release-cleanup decision.
- KI-025: top-level `LICENSE` is MPL-2.0 while NeoForge metadata declares LGPL-3.0; resolve provenance before public release and do not silently relicense.

## Reminders

- M1E final Minecraft PASS: skipped/unrecorded.
- M1F focused Minecraft transport PASS: unrecorded.
- M1G integrated source is green, but audible runtime PASS is unrecorded.
- KI-053/KI-054/KI-056/KI-057/KI-058/KI-059/KI-060/KI-061 are documented findings/decisions only; no implementation fix has been applied.
- Historical docs/scripts may preserve earlier contracts; current state/testing/facts documents override them.
