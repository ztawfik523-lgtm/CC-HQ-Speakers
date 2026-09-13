# M1E — server-authoritative finite playback

## Current status

M1E is complete at the source/test/CI level after the 2026-09-13 hardening pass.

Final M1E code candidate:

`521d4323d9216c8a99e8ec60426997c3330c4068`

Branch:

`codex/m1e-final-hardening`

Exact final code CI:

`34757923455`

Both NeoForge 21.1.247 and 21.1.248 passed build, tests, packaged-mod verification, and artifact upload.

The final strengthened Minecraft M1E acceptance script was previously skipped by explicit owner decision. Therefore there is **no recorded final M1E runtime PASS**. This remains an evidence boundary, not an open source blocker.

See `M1E-FINAL-HARDENING-2026-09-13.md` for the exact completion evidence.

## Product behavior

M1E makes prepared finite playback server-authoritative.

A successful start is conceptually:

```text
server has analyzed MediaAsset
-> retain one playback reference
-> construct playback state
-> install canonical PLAYING session
-> canonical server clock is already running
-> project state to clients/computers best-effort
```

No client READY, decoder setup, renderer setup, listener presence, or audible output is required for semantic playback to begin.

## Canonical states

The server semantic states are:

- `PLAYING`
- `PAUSED`
- `ENDED`
- `ERROR`

There is no canonical server LOADING state for client buffering.

`FinitePlaybackStateMachine` is the production semantic component used by `HQFiniteMediaServer` for these rules.

## Canonical controls and EOF

The server owns:

- position and duration;
- pause/resume;
- seek;
- loop;
- volume;
- natural EOF;
- terminal server errors.

Non-looping natural EOF is driven by the server clock reaching the known server-derived duration.

Exact-end semantics are:

- non-looping `audioSeek(duration)` -> `ENDED` at duration;
- looping `audioSeek(duration)` -> wrap to zero and remain active.

A server-side `ERROR` freezes the canonical position at the failure instant. Status reads after the error do not continue advancing time.

## Prepared-start atomicity

The final start path performs throwable construction and the initial status snapshot before the new session becomes canonical.

If that pre-install work fails:

- no new session is installed;
- the retained playback reference is handed to retry-safe release ownership;
- the call fails without leaving a ghost finite session.

After the session is installed, remaining BEGIN/STATE/Lua-event work is projection only and cannot turn a successful canonical start into an unowned/half-installed start.

## Client finite status

Client finite telemetry is deliberately non-authoritative:

- READY — requests a fresh current server STATE;
- ERROR — diagnostic only.

Client decoder/render/transport state cannot canonically end, pause, rewind, or otherwise own the finite server timeline.

## Client/network projection

Client delivery is best-effort projection of server truth.

Runtime packet-send failure cannot abort canonical server transitions. Broadcast delivery is isolated per relevant player, so one failing recipient does not prevent later relevant recipients from receiving the same projection.

ComputerCraft `hqspeaker_audio_state` event delivery is also best-effort per attached computer.

## Asset lifetime

A successful finite playback holds a separate MediaAsset playback reference from any ComputerCraft preparation reference.

`MediaAssetReleaseQueue` owns deferred logical release retries. If final file deletion temporarily fails, responsibility is transferred to the queue instead of being forgotten.

The active paths using this retry-safe rule include:

- finite playback references;
- explicit prepared-owner release;
- detached prepared-owner cleanup;
- rejected/detached-during-prepare cleanup;
- M1F in-flight range-read references.

Deferred releases are retried at a modest cadence.

Server shutdown ordering is:

```text
speaker/peripheral cleanup
-> finite + staging ownership released/transferred
-> range IO stopped/drained
-> MediaAssetStore close/cleanup
```

## Current protocol relationship

M1E originally existed with the temporary protocol-v4 whole-file bridge.

Current source uses M1F protocol v5, but the M1E authority contract is unchanged. STATE still carries canonical server truth and now also includes the M1F encoded anchor:

- source/media/generation;
- PLAYING / PAUSED / ENDED / ERROR;
- canonical position;
- duration;
- volume;
- looping;
- `anchorOffset`;
- `anchorTime`;
- optional server error detail.

The transport extension does not give clients authority over the timeline.

## Deterministic evidence

Current M1E-specific deterministic coverage includes:

### `FinitePlaybackStateMachineTest`

- immediate server-owned start;
- pause/resume;
- non-loop exact-duration END;
- loop exact-duration wrap;
- natural EOF;
- terminal control rejection;
- ERROR position freeze;
- volume clamping and finite-input validation.

### `FinitePlaybackClockTest`

- immediate progression without renderer handshake;
- pause/resume arithmetic;
- loop rebasing;
- exact-end math;
- natural EOF math.

### `BestEffortProjectionTest`

- successful projection;
- runtime projection failure containment;
- containment even if failure reporting itself throws.

### `MediaAssetReleaseQueueTest`

- failed final release transfers retry ownership;
- repeated failure remains queued;
- later success clears responsibility;
- genuinely missing asset is treated as resolved.

The complete project test suite passed on both supported NeoForge versions for the exact final M1E code candidate.

## Runtime evidence boundary

The 2026-09-12 runtime diagnostic showed server state/control behavior continuing while the obsolete client MP3 bridge failed after decoder setup. That remains useful authority-separation evidence.

It is not the final strengthened acceptance run.

The owner explicitly chose not to run that final M1E script, so record:

```text
M1E final manual Minecraft acceptance: skipped / no recorded PASS
```

Do not infer runtime PASS from CI.

## Decoder boundary

The obsolete JavaSound/mp3spi complete-file decoder is not the authority source and is not the current M1F prepared transport consumer.

M1G owns the replacement progressive decoder/audio path. Do not repair the obsolete bridge merely to make M1F temporarily audible.

## User-facing Lua path

Recommended finite-file helpers remain:

- `hq.playFile`
- `hq.prepareFile`
- `hq.preparedInfo`
- `hq.playPrepared`
- `hq.releasePrepared`

`audioPlayStaged()` was this project's temporary prototype API and has been removed from the current modern finite path.

## Boundary to M1F

M1E completion does not make M1F complete.

M1F still has separate work around the sliding encoded-window consumer boundary, its remaining deterministic acceptance matrix, and optional focused Minecraft range-transport acceptance.

Those issues do not reopen the M1E server-authority milestone.
