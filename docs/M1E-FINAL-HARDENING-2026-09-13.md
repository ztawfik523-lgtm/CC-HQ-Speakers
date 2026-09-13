# M1E final hardening — 2026-09-13

This is the current M1E completion record. It supersedes the reopened M1E status in `M1E-M1F-REEVALUATION-2026-09-13.md` while preserving that document as the audit which found the failure paths.

## Result

M1E's server-authoritative finite-playback implementation is complete at the source/test/CI level.

Final M1E code candidate:

`521d4323d9216c8a99e8ec60426997c3330c4068`

Branch:

`codex/m1e-final-hardening`

Exact CI:

`34757923455`

Both NeoForge 21.1.247 and 21.1.248 passed build, tests, packaged-mod verification, and artifact upload.

Baseline 21.1.247 artifact:

- artifact id: `10317494766`
- artifact ZIP SHA-256: `1a8231afab97b0374063301bd586e82c94f1306e39ccbf77ad048e6330d615a5`
- JAR: `hqspeaker-1.1.4-1.21.1-neoforge.jar`
- JAR SHA-256: `da7e537955afbed98e00ba09b89301005fc09fe951ca4b2903d5dc69cd977c82`

NeoForge 21.1.248 artifact:

- artifact id: `10317912658`
- artifact ZIP SHA-256: `d4d359bf276e5d8d3b615a52d4f4a748c58799102a7063711bda6df0d6f0b05e`

The packaged JAR still embeds exactly:

- JLayer `1.0.1.4`, compatibility range `[1.0.1.4,1.0.2)`;
- mp3spi `1.9.5.4`, compatibility range `[1.9.5.4,1.9.6)`;
- Tritonus Share `0.3.7.4`, compatibility range `[0.3.7.4,0.3.8)`.

The Gradle dependency declaration now prefers those exact bundled versions while preserving those Jar-in-Jar compatibility ranges, avoiding a dynamic-version metadata lookup which twice failed because NeoForged Maven returned HTTP 502.

## Canonical finite semantics

A successful prepared finite start is:

```text
server has analyzed MediaAsset
-> retain playback reference
-> construct finite session/state
-> install canonical PLAYING session
-> canonical clock is already running
-> project BEGIN/STATE/events to clients/computers best-effort
```

Server truth does not wait for client READY, decoder setup, renderer setup, or audibility.

Canonical states are:

- `PLAYING`
- `PAUSED`
- `ENDED`
- `ERROR`

The server owns:

- position and duration;
- pause/resume;
- seek;
- loop;
- volume;
- natural EOF;
- terminal server error state.

Non-looping `seek(duration)` ends at duration. Looping `seek(duration)` wraps to zero and remains active.

Client finite telemetry remains only:

- READY — request a fresh authoritative server snapshot;
- ERROR — diagnostic only.

A client decode/render/transport error cannot canonically end, pause, rewind, or otherwise own server playback.

## Reopened M1E issues and resolutions

### Packet/client projection could escape authoritative transitions — resolved

Client projection is now best-effort. Runtime delivery failure cannot abort a canonical server transition.

The final recipient hardening at `521d4323...` also isolates delivery per player: one broken nearby recipient cannot abort delivery to later recipients in the same projection loop.

Lua state-event delivery is likewise best-effort per attached computer.

### Prepared start could leave a ghost session — resolved

All construction/snapshot work which can throw is completed before `session = next` makes playback canonical.

If pre-install construction fails, the retained playback reference is handed to the retry-safe release owner and no session is installed.

After installation, the remaining work is best-effort projection only, so a client delivery failure cannot turn a successful canonical start into a thrown/half-installed start.

### Failed final asset release could be forgotten — resolved

`MediaAssetReleaseQueue` is the central retry owner for logical MediaAsset releases.

Playback, prepared/detached staging ownership, rejected imports, and M1F in-flight range references hand release responsibility to that queue. A transient final-file deletion failure remains queued instead of being forgotten.

Retries occur at a modest server-tick cadence rather than hammering a persistently failing filesystem.

Server shutdown drains range IO before store cleanup.

### ERROR state clock could continue advancing — resolved

`FinitePlaybackStateMachine.fail()` freezes the canonical position at the failure instant. Repeated status reads of an errored playback no longer show time advancing.

## Deterministic evidence

`FinitePlaybackStateMachineTest` covers the production semantic component for:

- immediate server-clock start;
- pause/resume;
- exact-duration non-loop END;
- exact-duration loop wrap;
- natural EOF;
- terminal ERROR position freeze;
- terminal control rejection;
- volume clamping/finite validation.

`FinitePlaybackClockTest` separately covers core time arithmetic and loop rebasing.

`BestEffortProjectionTest` proves runtime projection failures and even diagnostic/logging failures are contained.

`MediaAssetReleaseQueueTest` proves failed logical release ownership transfers to the retry queue and remains queued until a later release succeeds.

The exact final code candidate passed the complete project test suite on both target NeoForge versions.

## Recheck boundary

The final source recheck covered:

- `HQFiniteMediaServer` start/control/EOF/error/stop paths;
- `FinitePlaybackStateMachine` and `FinitePlaybackClock`;
- composite finite ownership and terminal replacement behavior;
- `MediaAssetStore` retain/release semantics;
- `MediaAssetReleaseQueue` retry ownership;
- `HQMediaStaging` prepared/detach/rejected-asset cleanup;
- `FiniteRangeReadService` in-flight retain/release and close ordering where it touches M1E asset lifetime;
- `ServerMediaAssets` retry cadence and shutdown ordering;
- server level/peripheral cleanup order;
- client finite status authority boundary;
- per-player and per-computer projection failure isolation;
- both-target CI and packaged dependency metadata.

No additional M1E correctness/design blocker was found in that pass.

## Runtime evidence boundary

The final strengthened Minecraft M1E acceptance script was **not** run. The project owner explicitly chose to skip it earlier.

Therefore record the final M1E status as:

```text
M1E source implementation: final
M1E deterministic tests: final / green
M1E both-target CI/package: PASS
M1E final manual Minecraft acceptance: skipped by owner / no recorded PASS
```

Do not rewrite the skipped runtime acceptance as a PASS.

The older 2026-09-12 runtime diagnostic remains supporting evidence that server authority continued while the temporary client MP3 bridge failed, but it is not the skipped final acceptance run.

## Boundary to M1F

Finishing M1E does not mark M1F complete.

M1F still has separate reopened work, chiefly:

- a true sliding consume/discard encoded window suitable for progressive M1G refill;
- the remaining deterministic M1F acceptance matrix;
- focused Minecraft range-transport acceptance, if/when the owner wants runtime proof.

Those are M1F issues and do not reopen the M1E server-authority milestone.
