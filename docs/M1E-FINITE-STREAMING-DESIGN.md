# Finite streaming design — M1E authority + M1F transport + M1G decode/render

Updated: 2026-09-17

This document records the accepted finite-streaming architecture across M1E/M1F/M1G. For issue/evidence status, `CURRENT-STATE.md`, `KNOWN-ISSUES.md`, `TESTING.md`, and exact source take precedence.

Current implementation status:

- M1E server authority: source/test/CI complete; final focused Minecraft acceptance skipped/unrecorded;
- M1F bounded encoded transport: source/test/CI/package/component complete; focused Minecraft transport acceptance unrecorded;
- M1G progressive MP3/common-WAV decode + positional renderer: **integrated in source**, with correctness/evidence work remaining;
- M1H full dynamic listener/moving-source/rejoin lifecycle: later.

Current green integrated M1G source checkpoint: `957832348eaa6e497282d923f2312c9c7d7c550f`.

## User-facing architecture

```text
ComputerCraft file
-> temporary staging/import
-> reusable server MediaAsset
-> server-owned playback clock/state
-> relevant client requests bounded encoded pieces it needs
-> bounded sliding client encoded RAM
-> progressive MP3/common-WAV decoder worker
-> bounded mono S16 PCM at source rate
-> Minecraft AudioStream / positional BLOCKS renderer
```

The server does not wait for client rendering before canonical time begins. Large songs do not require complete client song files or whole-track decoded PCM.

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

Projection failure cannot undo canonical server state.

The final focused Minecraft M1E acceptance was explicitly skipped and remains unrecorded.

## M1F encoded transport

M1F introduced bounded request/data transport. Current modern protocol is **v6** because M1G later added the decode descriptor; the basic range contract remains:

```text
client -> server:
source + generation + asset + offset + bounded length

server -> client:
source + generation + asset + offset + encoded bytes
```

Authoritative STATE currently carries:

```text
position/duration/state/volume/looping
anchorOffset + anchorTime
```

Current tuning bounds:

- range: max 128 KiB;
- one-source encoded window: 512 KiB;
- per-player in-flight requests: max 4;
- per-player in-flight bytes: max 512 KiB;
- server IO workers: 2;
- server IO queue: 64.

These are implementation values, not product guarantees.

## Server request/lifetime contract

Modern range handling enforces active source/generation/asset identity, bounded offsets/lengths, current player/relevance, bounded outstanding work, separate in-flight asset lifetime, stale completion rejection, off-thread exact reads, cancellation accounting, and deferred release ownership.

### Current shutdown caveat — KI-054

The intended shutdown ordering is range IO -> store close, but current failure handling is not fully hardened. `FiniteRangeReadService.close()` can throw before `MediaAssetStore.close()`, leaving the store root lock and static server-assets entry alive in the JVM. Completed-file deletion failure can also lose retry bookkeeping during store close.

Therefore “workers stop before store close” is a design requirement, not proof that every failure path is currently safe.

## Client encoded-data contract

The client boundary distinguishes:

- DATA_AVAILABLE;
- NEED_DATA;
- TRUE_ASSET_EOF;
- CANCELLED_OR_STALE.

Temporary network starvation must never become physical EOF.

`FiniteRangeWindow` supports authoritative reset/re-anchor and bounded forward sliding/discard while preserving useful unread overlap/prefetch.

## M1G progressive decode/render path

Current source integrates:

```text
FiniteRangeWindow
-> FiniteEncodedInputStream
-> ProgressiveWavDecoder / ProgressiveMp3Decoder
-> bounded FinitePcmQueue
-> FinitePcmAudioStream
-> FiniteSpeakerSound / SoundManager / BLOCKS
```

### Encoded input

`FiniteEncodedInputStream` is decoder-worker-only. NEED_DATA waits/refills away from game/audio threads; true asset EOF alone becomes normal stream EOF; cancellation/stale state aborts the decode epoch.

### MP3

JLayer decodes frames progressively. The server selects E1 conservative earlier seek points; the client decodes forward and discards PCM before the audible target/current server time.

`FiniteDecodeAnchorSelector.Anchor` is exactly `(offset, seconds)`. Both fields already go on STATE; there are no hidden frame/skip fields being discarded.

### WAV

The server supplies normalized common-WAV layout. Current modern support is U8/S16/S24/S32/F32 mono/stereo, classic RIFF/WAVE plus the selected narrow PCM/float WAVEX subset. Stereo downmixs to mono S16 at source sample rate.

### PCM/render

`FinitePcmQueue` keeps decoded PCM bounded with producer backpressure. `FinitePcmAudioStream` performs no network/disk/codec work; temporary empty PCM becomes short bounded silence rather than terminal EOF.

`FiniteSpeakerSound` is one positional BLOCKS source per physical speaker.

## Decoder/re-anchor coordination — selected next protocol change

Current v6 still has KI-053/KI-056/KI-057:

- ordinary same-anchor STATE can reset an already-slid encoded window without restarting the live decoder;
- expected SEEK cancellation can wake/report before old worker identity is invalidated;
- time-derived anchor movement is overloaded as decoder-restart intent, while same-coarse-anchor semantic seek depends on the preceding CONTROL path.

Selected target is an explicit server-authoritative decoder/re-anchor revision, likely protocol v7:

- new media => new generation;
- semantic seek => revision increments;
- ordinary STATE/pause/resume/volume/loop snapshots preserve healthy decoder state;
- STATE alone is sufficient for seek/reanchor correctness;
- missing local decoder can rebuild without forcing revision change;
- old worker identity is invalidated before cancellation can wake/report.

One implementation-shape choice remains: retain finite CONTROL packets only as optional latency hints, or remove duplicate PAUSE/RESUME/SEEK/SET_VOLUME/SET_LOOP projection and let STATE become the sole transition authority.

## Seeking

Canonical seek remains server-owned:

```text
seek(T)
-> server clock becomes T
-> server selects encoded anchor at/before T
-> explicit decoder/reanchor revision identifies semantic discontinuity
-> client resets codec/window/PCM/renderer state as required
-> MP3 pre-roll/common-WAV decode rejoins the current server timeline
```

A semantic seek must restart codec state even if the selected encoded anchor byte is unchanged.

## Fixed modern finite range / volume

M1G deliberately uses a fixed **32-block** core listening/delivery radius rather than native CC:T-style volume-dependent range.

Selected contract:

- normal positional attenuation inside 32 blocks;
- HQ finite volume changes gain/loudness, not core range;
- volume >1 does not intentionally enlarge modern finite attenuation distance;
- future Sound Physics Remastered compatibility owns intentional acoustic/range extension and matching server relevance.

Current source relevance is already fixed 32 blocks, but KI-058 remains because the live Minecraft channel does not yet explicitly install/retain the selected fixed attenuation distance when volume changes.

## Volume zero

Selected global HQ volume-zero behavior:

- canonical server time keeps advancing;
- client retains session metadata but cancels local decoder/renderer;
- encoded range requests stop while globally muted;
- unmute rebuilds/reanchors to then-current authoritative time.

A player's local MASTER/BLOCKS slider is separate and must not change server transport. Renderer-start/local-silent behavior still needs KI-060 hardening.

## Looping

Looping is deliberately simple in M1G.

At local physical EOF, if authoritative state still says `looping=true`, start a fresh local decoder/render iteration from the beginning. A normal restart gap is acceptable.

M1G does not require sample-gapless MP3, LAME/Xing delay/padding trimming, loop-head prefetch solely to hide boundaries, a permanent OpenAL source, or special SPR loop continuity.

This selected ordinary replay behavior is not implemented yet (KI-051).

## No persistent client song cache

Modern prepared playback does not use:

- `.part` song accumulation;
- completed client `.media` song files;
- client LRU music library;
- persistent sparse cache;
- cross-restart partial-download resume.

Staging is import plumbing only:

```text
CC file -> temporary staging -> immutable server MediaAsset
```

`audioPlayStaged()` remains removed.

## Staging / storage caveats

- KI-061: whole staging-owner cleanup can leave arbitrary staging files unreachable under old random staging IDs.
- KI-064: asset import can spin indefinitely on repeated zero-byte reads and lacks unsupported-`ATOMIC_MOVE` fallback.

These do not change the finite-streaming architecture but remain active hardening work.

## Replacement / threading caveats

- KI-062: synchronized dynamic legacy stream dispatch can perform blocking DNS while holding a monitor needed by server tick ownership work and synchronized cleanup.
- KI-063: failed/rejected RAW or prepared replacement can stop valid current playback before the replacement is admitted.

These are cross-cutting ownership/safety issues, not reasons to redesign the finite transport pipeline.

## Dynamic listener / moving-source boundary — M1H

Current M1G range is fixed and requests/completions recheck relevance, but full lifecycle remains M1H:

- late entrants;
- proactive leave cleanup;
- return/rejoin at current server time;
- dimension/world/reload recovery;
- robust general underrun rejoin;
- final moving-source/VS2 lifecycle.

Modern BEGIN carries initial world position and block coordinates; STATE does not carry live x/y/z. The legacy client already recomputes VS2 ship position from block coordinates each tick, so M1H may mirror that client-side path or add explicit authoritative position updates. That later tradeoff remains open.

## Evidence boundary

M1G integrated source/tests/package is green at `957832348eaa6e497282d923f2312c9c7d7c550f`, but real-MP3 progressive integration coverage, focused `FinitePcmAudioStream` coverage, selected ordinary replay, and focused audible Minecraft acceptance remain incomplete.
