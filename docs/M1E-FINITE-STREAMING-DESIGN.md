# Finite streaming design — M1E authority + M1F transport + M1G decoder boundary

This remains the accepted finite-streaming **architecture**. The 2026-09-13 reevaluation changed milestone completion status, not the main design.

For current implementation findings, read `M1E-M1F-REEVALUATION-2026-09-13.md` first.

## User-facing target

```text
ComputerCraft file
-> server-owned MediaAsset
-> server-owned playback clock/state
-> relevant client requests bounded encoded pieces it needs
-> client keeps bounded temporary RAM
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
- natural EOF.

Client READY requests fresh server truth. Client ERROR is diagnostic only.

This semantic design remains present in current source.

### Reevaluated implementation hardening

The architecture also requires client delivery to remain a projection of canonical state, not a possible cause of canonical transition failure.

Current source still needs hardening for:

- per-player packet-send runtime failure isolation;
- atomic prepared-start rollback;
- retry-safe playback asset release.

These are implementation failure-path issues, not a reason to abandon server authority.

## M1F encoded transport

Current source implements protocol v5 range transport:

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

- range response: max 128 KiB;
- one-source encoded byte window: 512 KiB;
- per-player in-flight requests: max 4;
- per-player in-flight bytes: max 512 KiB;
- server IO workers: 2;
- server IO queue: 64.

These are implementation values, not product-level guarantees.

## Server request/lifetime contract

Range work should satisfy:

- active source/generation/asset identity;
- valid bounded offset/length;
- connected player in same current relevance domain;
- bounded outstanding work;
- playback/source ownership still valid;
- separate in-flight MediaAsset lifetime while async IO runs;
- stale completion discard after replacement/leave/disconnect;
- background reads do not outlive the media store.

Current source implements much of this normal path, including off-thread reads, per-player limits, in-flight retain, and current-session/relevance recheck before send.

The reevaluation reopened deterministic acceptance proof for the complete list and found a rare final-release retry gap in range-task cleanup.

## Client encoded-data contract

The client boundary must distinguish:

- DATA_AVAILABLE;
- NEED_DATA;
- TRUE_ASSET_EOF;
- CANCELLED_OR_STALE.

Temporary network starvation must never become physical EOF.

Current `FiniteRangeWindow` implements those availability states and arbitrary re-anchor/reset.

### Progressive-window requirement clarified by reevaluation

For M1G to consume the window cleanly, the encoded buffer should also support advancing/discarding already-consumed prefix data while retaining useful unread prefetched bytes and opening capacity for future ranges.

Current `FiniteRangeWindow` does not yet expose that sliding/consume operation. Its `reset(anchorOffset)` discards the whole current window.

A decoder can technically progress by consuming an entire fixed window and resetting at its end, but that creates hard refill boundaries and is weaker than the originally intended sliding producer/consumer contract.

This is a client-buffer API gap, **not** a reason to change protocol v5.

## Original M1F proof target remains valid

A deterministic fake consumer should be able to prove:

```text
request non-zero range
-> verify exact bytes
-> consume/discard prefix
-> continue/refill beyond one window while bounded
-> jump to distant offset
-> obsolete data gone
-> memory bounded
```

Current tests prove non-zero range, exact bytes, bounded allocation, re-anchor, stale rejection, retry, and missing-vs-EOF. They do not yet prove true sliding consume/refill beyond one window.

## Seeking

Canonical seek remains server-owned:

```text
seek(T)
-> server clock becomes T
-> STATE selects encoded anchor at/before T
-> client demands data from that anchor
-> M1G decoder later decodes/pre-rolls to audible target
```

Generation does not change for ordinary seek because the encoded asset bytes remain immutable.

### MP3

Current server analysis records coarse MP3 encoded seek points. M1G owns exact Layer III pre-roll/reservoir reconstruction.

### WAV

Current analyzer records WAV audio-data start, not final direct common-WAV time-to-byte layout. M1G must add the layout metadata required for exact range selection/conversion.

The M1F range protocol already supports arbitrary offsets and should not need redesign for WAV.

## No persistent client song cache

The final architecture does not use:

- `.part` song accumulation;
- completed client `.media` song files;
- client LRU music library;
- persistent sparse cache;
- cross-restart partial-download resume.

M1F current source removed the modern `.part/.media` path.

## Removed prototype path

The project-specific `audioPlayStaged()` command and direct staged playback path remain removed.

Staging is import plumbing only:

```text
CC file -> temporary staging -> immutable MediaAsset
```

## M1G boundary

M1G has not started and should not begin until the owner chooses how to close/defer the reopened M1E/M1F issues.

Target:

```text
bounded/sliding encoded window
-> decoder/converter worker
-> bounded mono PCM queue
-> positional Minecraft/OpenAL renderer
```

M1G owns:

- progressive MP3 decode;
- starvation/refill behavior;
- MP3 pre-roll;
- common WAV layout/conversion;
- mono/stereo-to-mono output;
- bounded PCM;
- decoder cancellation;
- audible rendering;
- final active format narrowing.

## Dynamic listener boundary — M1H

Current M1F revalidates relevance on range admission/completion, but full listener lifecycle remains M1H:

- late entrants;
- proactive leave cleanup;
- return/rejoin at current server time;
- dimension/world/reload recovery;
- underrun refill/rejoin;
- VS2 movement lifecycle.

Do not reintroduce fixed historical recipient capture.

## Current status language

Architecture:

**accepted.**

M1E normal-path semantics:

**implemented; hardening/acceptance reopened.**

M1F range architecture:

**implemented and CI-green; acceptance/completeness reopened.**

M1G:

**not started.**
