# Future cleanup

Updated: 2026-09-20

This is the current cleanup parking lot after finite convergence.

## Near-term source cleanup

The dead standard grouped/indexed bodies, dead singular fake standard bodies, shadowed singular legacy `speakPCM`, and their newly unreferenced wrappers are removed. The composite explicitly owns the supported standard and RAW public names.

Remaining source cleanup should be exact-reference-driven: remove only helpers/aliases proven unreachable or obsolete.

The stale undocumented controls `speakStopAll`, `speakStopAt`, `speakVolumeAll`, and `setLoopingAll` have been removed; do not re-add them merely for speculative script compatibility.

Do **not** mechanically delete `SyncDispatch` / client sync-group code: grouped optional live-stream helpers still use it.

## RAW/API cleanup

Current RAW admission is already bounded and unified.

Remaining goals:

- keep signed-16/48-kHz behavior concise and truthful (public and legacy RAW table caps are now consistently 131072 samples);
- keep `hqspeaker_audio_empty` tied to observed backpressure;
- keep native `speaker_audio_empty` separate;
- avoid fake seek/duration/loop for RAW.

## Optional live containment

Live MP3/HLS/TS/ICY is not core.

If retained, fix HLS refreshed-window progression, reconsider expected-member group start behavior, keep DNS validation off server-sensitive locks, and verify URL/worker/shutdown limits.

If not worth maintaining, retirement is preferable to letting this legacy path constrain core design.

## Already removed

- `FileFiniteAudioStream`
- `HQSpeakerCluster`
- `FiniteAudioTrack`
- duplicate finite server/client/control/status code
- old finite entry points
- standalone block path
- mp3spi/Tritonus

Remaining candidates should be verified by exact reference search immediately before deletion.

## Documentation/script cleanup

Current authority begins with `HANDOFF-2026-09-20-POST-CONVERGENCE.md`, `CURRENT-STATE.md`, `KNOWN-ISSUES.md`, `TESTING.md` and `VERIFIED-FACTS.md`.

Historical dated milestone documents should remain historical.

Old runtime Lua scripts which target retired APIs should be labeled historical or replaced by current acceptance scripts before release.

## CI/repository hygiene

Current workflow builds NeoForge 21.1.247 and 21.1.248 on every push/PR.

Possible cleanup:

- concurrency cancellation;
- docs-only optimization;
- default branch pointing at the real product line when ready;
- release artifact naming/versioning;
- changelog/release notes.

## Release gate

Before public release:

- final exact-source dead-code pass;
- package/dependency verification;
- current public API freeze;
- integrated Minecraft acceptance/stress;
- both NeoForge targets;
- Sound Physics Remastered regression/performance;
- movement/multispeaker backlog.
