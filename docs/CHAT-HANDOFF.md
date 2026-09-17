# CC:HQ Speakers — handoff pointer

Updated: 2026-09-17

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

Current branch: `codex/m1g-progressive-finite-decode`

Current green integrated **source** checkpoint: `957832348eaa6e497282d923f2312c9c7d7c550f` / CI `34778546164`.

Documentation/audit commits after that checkpoint have not changed implementation source.

## Read first

1. `CURRENT-STATE.md`
2. `M1G-SCOPE-DECISIONS-2026-09-14.md`
3. `KNOWN-ISSUES.md`
4. `TESTING.md`
5. `VERIFIED-FACTS.md`
6. `FUTURE-CLEANUP.md`
7. `NEXT-CHAT-HANDOFF.md`
8. exact current source/CI

Historical milestone/preparation/handoff docs preserve their checkpoint history and do not override these current records.

## Current state

M1G progressive MP3/common-WAV decode, bounded PCM, and Minecraft positional rendering are integrated in source. The modern prepared path does not use the inherited complete-file bridge.

Focused audible Minecraft M1G acceptance is still unrecorded.

Current implementation remains protocol **v6**. The selected next design is an explicit server-authoritative decoder/re-anchor revision, likely v7.

## Settled M1G policy

Do **not** reopen these unless the owner explicitly changes scope:

- explicit decoder/re-anchor revision; ordinary STATE must not restart a healthy decoder just because its time-derived anchor moved;
- fixed 32-block modern-finite core listening/delivery radius;
- HQ volume changes gain/loudness, not core range;
- global HQ volume zero keeps server time running but hibernates local decode/render/range requests until unmuted;
- looping is ordinary replay after local EOF while authoritative state still says looping; a normal restart gap is acceptable;
- no gapless MP3/LAME padding work, permanent-source loop engineering, dynamic volume-aware range, or SPR integration in M1G;
- future Sound Physics Remastered work owns intentional extended range/acoustics and matching transport relevance.

One implementation-shape choice remains for protocol v7: retain PAUSE/RESUME/SEEK/SET_VOLUME/SET_LOOP CONTROL packets only as optional latency hints, or remove that duplicate path and make STATE the sole correctness authority.

## Highest-impact open findings

- **KI-053 / KI-056 / KI-057:** decoder window/cancellation/reanchor cluster; solve coherently with the explicit revision.
- **KI-058 / KI-060:** enforce the selected fixed attenuation distance and harden renderer start/volume-zero/local-silent behavior.
- **KI-051:** selected ordinary replay is not implemented yet.
- **KI-055:** real-MP3 progressive integration and focused `FinitePcmAudioStream` coverage are missing.
- **KI-061:** per-speaker staging cleanup can leave unreachable files.
- **KI-062:** synchronized dynamic stream dispatch can hold the composite monitor across blocking DNS while server tick and synchronized lifecycle cleanup may wait on the same monitor.
- **KI-063:** rejected/failed RAW or prepared replacement can destroy valid current playback before the replacement is admitted.
- **KI-054:** shutdown can lose deletion retry state or fail before media-store close, leaving the root lock/registry alive in the JVM.
- **KI-064:** `MediaAssetStore` import can spin indefinitely on repeated zero reads and has no non-atomic move fallback.

The practical working sequence from the latest review is to eliminate the KI-062 server-stall hazard before relying on the legacy stream path, then complete the M1G v7 decoder/reanchor cluster. KI-063 and KI-054/KI-064 may be grouped around that work according to patch cohesion; this is sequencing guidance, not a new product contract.

## Audit corrections that must not regress

The September 16 repository audit was rechecked. Preserve these corrections:

- `FiniteDecodeAnchorSelector.Anchor` is exactly `(offset, seconds)`; there are no richer frame/skip fields being thrown away.
- modern finite STATE does not carry x/y/z; BEGIN carries initial world/block coordinates.
- `audioPrepareStaged(...)` is not synchronized on the composite monitor; the confirmed blocking-monitor problem is the synchronized dynamic stream path.
- `HQSpeakerPeripheral` has no composite back-reference. The provider's WeakHashMap explicitly relies on deterministic lifecycle eviction because cached values reference their Level.
- `HQFiniteMediaServer.tick()` was overstated in the first audit draft; do not claim it iterates/project players every tick.
- inherited HTTP stream paths close their streams; do not reintroduce the retracted leak claim.
- pending release retries are driven by `ServerMediaAssets.tickPendingReleases()`.

## M1H/VS2 note

Modern `FiniteSpeakerSound.updatePosition(...)` exists but has no M1G call site after renderer creation. STATE has no live coordinates.

A new wire position packet is not automatically required: the legacy client already recomputes VS2 ship-transformed positions from block coordinates each tick, and modern BEGIN already carries block coordinates. M1H may reuse that client-side pattern or add explicit server position updates if later requirements justify them. Do not silently choose now.

## Later work that must not derail M1G

Confirmed but later: inherited HLS refreshed-window progression, misleading legacy format lists, legacy `*All`/`*At` note/sound semantics, the separate incomplete `hqspeaker:hq_speaker` surface, CI hygiene, legacy decoder/provider cleanup, license provenance, and SPR integration.
