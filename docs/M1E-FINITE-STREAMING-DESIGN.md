# Finite streaming design — M1E authority + M1F transport + M1G decoder boundary

This is the accepted finite-streaming architecture.

Current implementation status:

- M1E server authority: source/test/CI complete;
- M1F bounded encoded transport: source/test/CI/package complete;
- M1G progressive decoder/renderer: next, not started;
- M1H full dynamic listener lifecycle: later.

For exact M1F completion evidence read `M1F-FINALIZATION-2026-09-13.md`.

## User-facing target

```text
ComputerCraft file
-> server-owned MediaAsset
-> server-owned playback clock/state
-> relevant client requests bounded encoded pieces it needs
-> client keeps bounded sliding temporary RAM
-> decoder produces bounded mono PCM
-> positional speaker renderer
```

The server does not wait for client rendering before canonical time begins. Large songs do not require complete client song files.

## M1E semantic authority

The server owns:

- generation;
- PLAYING / PAUSED / ENDED / ERROR;
- duration/position;
- pause/resume;
- seek;
- loop;
- volume;
- natural EOF;
- terminal server errors.

Client READY requests fresh server truth. Client ERROR is diagnostic only.

Projection failure cannot undo canonical server state. Asset ownership/release is retry-safe.

The final focused Minecraft M1E acceptance was explicitly skipped by the owner and remains unrecorded.

## M1F encoded transport

Protocol v5 uses:

```text
client -> server:
source + generation + asset + offset + bounded length

server -> client:
source + generation + asset + offset + encoded bytes
```

STATE also carries:

```text
anchorOffset + anchorTime
```

The client waits for STATE before first byte demand.

Current tuning bounds:

- range: max 128 KiB;
- one-source encoded window: 512 KiB;
- per-player in-flight requests: max 4;
- per-player in-flight bytes: max 512 KiB;
- server IO workers: 2;
- server IO queue: 64.

These are implementation values, not product-level guarantees.

## Server request/lifetime contract

Final M1F satisfies:

- exact active source/generation/asset identity;
- valid bounded offset/length;
- connected/current player in same dimension and relevance radius;
- bounded outstanding work;
- playback/source ownership still valid;
- separate in-flight MediaAsset lifetime while async IO runs;
- stale completion discard after replacement/leave/disconnect;
- off-thread exact reads;
- cancellation accounting/ref cleanup;
- deferred final-release retry ownership;
- range workers stopped before media store close.

Pure validation rules are tested separately from Minecraft object plumbing. The integrated fake consumer proves actual MediaAsset range reads into the bounded client window.

## Client encoded-data contract

The client boundary distinguishes:

- DATA_AVAILABLE;
- NEED_DATA;
- TRUE_ASSET_EOF;
- CANCELLED_OR_STALE.

Temporary network starvation must never become physical EOF.

A fresh `FiniteRangeWindow` is unanchored. STATE installs the authoritative anchor.

The window supports:

- arbitrary reset/re-anchor;
- forward `advanceTo(...)` consumption;
- consumed-prefix discard;
- preservation of unread overlap;
- preservation only of still-useful whole in-flight requests;
- new tail demand/refill;
- bounded memory independent of track length;
- in-place sliding rather than full-window reallocation on each small consume.

## M1F component proof

The final fake-consumer proof is:

```text
2 MiB MediaAsset
-> non-zero range request
-> exact asynchronous server read
-> bounded client window
-> consume/discard prefix
-> continue/refill beyond one full window
-> jump to distant offset
-> obsolete old region rejected
-> exact bytes verified
-> memory stays bounded
```

This closes the original M1F deterministic transport target.

Focused real-Minecraft M1F transport acceptance remains unrecorded; do not infer it from CI.

## Seeking

Canonical seek remains server-owned:

```text
seek(T)
-> server clock becomes T
-> STATE selects encoded anchor at/before T
-> client resets demand to that anchor
-> M1G decoder later decodes/pre-rolls to audible target
```

Generation does not change for ordinary seek because the encoded asset bytes remain immutable.

### MP3

Server analysis records coarse MP3 encoded seek points. M1G owns exact Layer III pre-roll/reservoir reconstruction.

### WAV

Current analyzer records useful WAV facts but final common-WAV direct layout/time-to-byte metadata remains M1G work.

The M1F range protocol already supports arbitrary offsets and does not need redesign for common WAV.

## No persistent client song cache

The final architecture does not use:

- `.part` song accumulation;
- completed client `.media` song files;
- client LRU music library;
- persistent sparse cache;
- cross-restart partial-download resume.

## Removed prototype path

The project-specific `audioPlayStaged()` command/direct-staged path remains removed.

Staging is import plumbing only:

```text
CC file -> temporary staging -> immutable MediaAsset
```

## M1G boundary

M1G is next and owns:

```text
bounded/sliding encoded window
-> decoder/converter worker
-> bounded mono PCM queue
-> positional Minecraft/OpenAL renderer
```

Specifically:

- progressive MP3 decode;
- starvation/refill behavior;
- MP3 pre-roll;
- common WAV layout/conversion;
- mono/stereo-to-mono output;
- bounded PCM;
- decoder cancellation;
- audible rendering;
- final active format narrowing.

Do not restore or repair the obsolete complete-file JavaSound/mp3spi bridge as the new engine.

## Dynamic listener boundary — M1H

M1F validates current relevance on each range request/completion. Full listener lifecycle remains M1H:

- late entrants;
- proactive leave cleanup;
- return/rejoin at current server time;
- dimension/world/reload recovery;
- underrun refill/rejoin;
- VS2 movement lifecycle.

Do not reintroduce fixed historical recipient capture.
