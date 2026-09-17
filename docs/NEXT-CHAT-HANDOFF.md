# CC:HQ Speakers — next-chat handoff

Updated: 2026-09-17

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

Current branch: `codex/m1g-progressive-finite-decode`

Current green integrated M1G **source** checkpoint: `957832348eaa6e497282d923f2312c9c7d7c550f`.

Source CI `34778546164` passed NeoForge 21.1.247 and 21.1.248 including build/tests/package verification/artifact upload.

Documentation/audit work after that checkpoint has not changed implementation source.

## Read first

1. `CURRENT-STATE.md`
2. `M1G-SCOPE-DECISIONS-2026-09-14.md`
3. `KNOWN-ISSUES.md`
4. `TESTING.md`
5. `VERIFIED-FACTS.md`
6. `FUTURE-CLEANUP.md`
7. `ARCHITECTURE.md`
8. `ROADMAP.md`
9. exact current source/CI

Historical handoffs/preparation docs preserve earlier checkpoints and do not override current records.

## Current status

M1E and M1F remain complete at source/test/CI/package level. M1E final focused Minecraft acceptance was skipped/unrecorded; M1F focused Minecraft transport acceptance is unrecorded.

M1G progressive decode/render is **integrated in source**. Do not describe `HQFiniteMediaClient` as transport-only and do not route modern prepared playback back through the old complete-file bridge.

Current modern finite pipeline:

```text
server MediaAsset
-> canonical server playback state
-> protocol v6 descriptor + codec-aware STATE anchor
-> M1F bounded ranges / sliding encoded window
-> FiniteEncodedInputStream
-> ProgressiveWavDecoder or ProgressiveMp3Decoder
-> bounded FinitePcmQueue
-> FinitePcmAudioStream
-> FiniteSpeakerSound / SoundManager / BLOCKS
```

Locked A1/B1/C1/D1/E1 decisions remain unchanged.

## Settled M1G scope

These are owner-selected and should not be reopened casually:

### Explicit decoder/re-anchor revision

Protocol v6 currently infers restart intent from time-derived anchors. The selected replacement is an explicit server-authoritative decoder/re-anchor revision, likely protocol v7.

Required semantics:

- new media => new generation;
- semantic seek => revision changes;
- ordinary STATE/pause/resume/volume/loop snapshots preserve a healthy decoder;
- STATE is sufficient for semantic seek/reanchor correctness;
- local rebuild of a missing decoder does not require the server revision to change;
- stale worker identity is invalidated before cancellation can wake/report.

The remaining protocol-shape choice is only whether finite CONTROL packets survive as optional latency hints or are removed in favor of STATE as sole transition authority.

### Fixed 32-block core radius

M1G will not implement volume-dependent network relevance or dynamic audible range.

- modern finite server delivery/relevance stays fixed at 32 blocks unless explicitly changed;
- distance attenuation applies inside that range;
- HQ volume changes gain, not the core radius;
- volume >1 must not silently enlarge the modern finite channel attenuation distance;
- SPR compatibility later owns deliberate range/acoustic extension and matching transport relevance.

### Global volume zero

Canonical server time continues.

Local client work should hibernate while HQ volume is exactly zero: cancel decoder/renderer, stop range requests, retain session metadata, and rebuild/rejoin current authoritative time on unmute.

Client-local MASTER/BLOCKS mute is different and must not change server transport policy.

### Looping

Looping means normal “play the same thing again.”

At local physical EOF, if authoritative state still says `looping=true`, start a fresh local decoder/render iteration from the beginning. A normal restart gap is acceptable.

No gapless MP3, LAME/Xing padding trim, loop-head prefetch just to hide the boundary, permanent-source loop architecture, or SPR loop-continuity work in M1G.

## Highest-priority M1G source cluster

### KI-053 — same-anchor window rewind

An ordinary STATE can reset an already-slid encoded window while preserving the existing decoder cursor.

### KI-056 — expected seek cancellation race

Current `CONTROL SEEK` can cancel a worker before its identity is invalidated; expected cancellation can race into fatal `decoderFailed()`.

### KI-057 — STATE snapshot versus decoder-reanchor intent

Current STATE carries a time-derived anchor but no explicit decoder-reanchor revision. Ordinary state updates can restart healthy playback, while same-anchor semantic seek still depends on the preceding CONTROL path.

Solve KI-053/056/057 together with the selected explicit revision. Do not patch one symptom in isolation.

## Renderer/range/loop work

### KI-058

Install/retain the selected fixed 32-block linear attenuation distance on the live modern finite channel while volume changes gain.

### KI-060

Harden `rendererStarted`: it is currently latched before `SoundManager.play()` proves the sound became active. Support the selected global-volume-zero hibernation and sensible client-local silent-start/retry behavior.

### KI-051

Implement ordinary replay after local EOF while authoritative looping remains enabled. The architecture choice is settled; source work remains.

## Cross-cutting findings from the rechecked repository audit

### KI-062 — blocking DNS under shared composite monitor

Dynamic legacy stream calls pass through synchronized composite dispatch and can perform synchronous `InetAddress.getAllByName(...)` while holding that monitor.

The server tick's `tickOwnership()` and synchronized composite `cleanup()` use the same monitor. Provider `forget`, `forgetLevel`, and `clearAll` reach cleanup during block removal, Level unload, and server stop. A DNS-parked computer thread can therefore stall server tick/lifecycle cleanup waiting on that composite.

Fix the monitor/I/O boundary without turning this into an M3 stream rewrite.

### KI-063 — failed replacement can destroy current playback

RAW/prepared replacement transfers/stops current ownership before all new-source admission/failure conditions are known. Rejected/failed replacement should leave valid current playback alive unless destructive replacement is explicitly intended.

### KI-054 — shutdown lock lifetime

Shutdown can lose completed-file deletion retry state. More seriously, a `FiniteRangeReadService.close()` failure can occur before `MediaAssetStore.close()`, leaving the store root lock and stopped-server registry entry alive in the JVM.

### KI-064 — import progress/rename hardening

`MediaAssetStore.writeExact()` can spin indefinitely on repeated zero-byte reads; import has no fallback when `ATOMIC_MOVE` is unsupported.

## Working sequence after the latest review

There are meaningful sequencing tradeoffs, so do not silently turn this into a permanent milestone rule. The current working order is:

1. remove the KI-062 server-stall hazard before relying on legacy stream calls;
2. implement the M1G explicit decoder/reanchor revision and fix KI-053/056/057 as one patch family;
3. implement fixed attenuation + volume-zero/renderer-start behavior;
4. implement ordinary replay;
5. close real-MP3 / `FinitePcmAudioStream` / cancellation/seek evidence gaps;
6. fix KI-061 staging cleanup;
7. group KI-063 and KI-054/KI-064 around the above according to patch cohesion, while not losing them;
8. run both NeoForge targets and focused Minecraft audible acceptance.

If choosing instead to do a broader safety-first hardening batch (KI-062/063/054/064) before v7, record that decision explicitly. Both sequences are defensible; do not accidentally scope-creep HLS/legacy cleanup into either.

## M1H / VS2 correction

Modern STATE does **not** carry world coordinates. BEGIN carries initial world position and block coordinates. `FiniteSpeakerSound.updatePosition(...)` exists but modern M1G does not call it after renderer creation.

A new position-update packet is not automatically required. The legacy client already recomputes VS2 ship position from block coordinates each tick. M1H may mirror that client-side transform using BEGIN block coordinates, or add explicit authoritative position updates if later lifecycle needs justify the extra protocol. Leave that tradeoff for M1H.

## Audit claims that were retracted

Do not repeat these as facts:

- no richer MP3 frame/skip fields exist beyond anchor `(offset, seconds)`;
- STATE does not carry live x/y/z;
- `audioPrepareStaged(...)` is not synchronized on the composite monitor;
- `HQSpeakerPeripheral` has no composite back-reference; provider cache lifetime is intentionally controlled by explicit eviction hooks;
- `HQFiniteMediaServer.tick()` does not itself perform the player/fanout work originally claimed;
- inherited HTTP stream paths do close their streams;
- pending release retries are driven by `ServerMediaAssets.tickPendingReleases()`.

## Evidence gaps

- no real-MP3 progressive JLayer integration test across range sliding/starvation/pre-roll;
- no focused `FinitePcmAudioStreamTest`;
- selected ordinary loop replay unimplemented;
- focused audible M1G Minecraft PASS unrecorded;
- staging cleanup unfixed;
- cross-cutting KI-062/063/054/064 unfixed.

Historical scripts such as `m1d_media_analysis_test.lua` are not current modern-prepared M1G gates.

## Later work — do not pull into M1G

- full M1H late-entry/leave/rejoin/dimension/reload/general-underrun/VS2 lifecycle;
- SPR compatibility/range/acoustics;
- native FLAC;
- inherited HLS/TS repair;
- legacy `*All`/`*At` cleanup;
- broad legacy finite migration;
- separate `hqspeaker:hq_speaker` product/registry decision;
- CI/repository hygiene and release/license cleanup.
