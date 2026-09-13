# Current state

## Checkpoint

Active branch: `codex/m1g-progressive-finite-decode`.

M1E server-authoritative finite semantics and M1F bounded demand-driven encoded transport are complete at source/test/CI/package level.

Current green integrated M1G source checkpoint: `957832348eaa6e497282d923f2312c9c7d7c550f`.

CI `34778546164` passed NeoForge 21.1.247 and 21.1.248 including build, tests, package verification, and artifact upload. Documentation checkpoint `7ec70d4674b237f055d450e1290a652f7c23b65d` also passed both targets in CI `34780519972`.

Repository/source audits on 2026-09-14 reconciled stale documentation and found additional client-sync, renderer and storage-lifecycle issues. No implementation change was made by those audits.

Green CI is not Minecraft runtime proof. Focused audible M1G Minecraft acceptance remains unrecorded.

## Current modern finite pipeline

```text
server MediaAsset
-> server-authoritative finite timeline
-> protocol v6 descriptor + codec-aware STATE anchor
-> bounded client-requested encoded ranges
-> bounded sliding encoded RAM
-> starvation-aware decoder-worker input
-> progressive common-WAV or JLayer MP3 decode
-> bounded mono S16 PCM queue at source rate
-> nonblocking Minecraft AudioStream
-> one positional BLOCKS SoundManager source
```

The inherited complete-file JavaSound/mp3spi bridge is not the modern prepared engine.

## Locked M1G decisions

- A1: normal Minecraft `AudioStream` / `SoundManager` renderer.
- B1: server-normalized common-WAV layout.
- C1: preserve source sample rate.
- D1: narrow PCM/float `WAVE_FORMAT_EXTENSIBLE` support.
- E1: conservative MP3 pre-roll from an earlier analyzed seek point.

One physical speaker remains one mono positional source. Lua owns application meaning/policy.

## Integrated source

M1G source includes the MP3/common-WAV prepared gate, protocol-v6 decode descriptor, exact WAV/E1 MP3 anchors, starvation-aware `FiniteEncodedInputStream`, bounded `FinitePcmQueue`, progressive WAV conversion, progressive JLayer MP3 decoding, decoder-epoch cancellation, bounded prebuffer/catch-up, `FinitePcmAudioStream`, and positional `FiniteSpeakerSound` through `SoundSource.BLOCKS`.

Pause/resume/volume use the Minecraft channel-control path. Seek/replacement/stop discard old decoder/PCM/renderer state. Temporary PCM starvation becomes bounded silence rather than fake EOF.

## Current correctness/evidence blockers

The deepest current issue is not just KI-053 by itself: decoder re-anchor intent is not represented explicitly enough.

- **KI-053:** an ordinary same-anchor STATE can reset an already-slid encoded window without restarting the decoder, potentially putting the live cursor outside the reset window.
- **KI-056:** `CONTROL SEEK` cancels the current decoder before invalidating its epoch token, so the expected cancellation can race into `decoderFailed()` and kill a valid session before replacement STATE arrives.
- **KI-057:** STATE currently acts as both a timeline snapshot and an implicit re-anchor command. A changed time-derived anchor can restart healthy playback after ordinary controls (especially exact WAV anchors), while a same-anchor semantic seek still depends on the preceding CONTROL packet to communicate restart intent. This should be solved as one coherent snapshot-vs-reanchor/decoder-revision design rather than by patching only KI-053.
- **KI-058:** live modern-finite volume changes update gain state but not the live channel attenuation distance. CC:T 1.120.0 has an explicit `linearAttenuation(...)` workaround for the same Minecraft behavior.
- **KI-059:** the server uses a fixed 32-block finite relevance radius although supported volume reaches 3 and normal 16-block attenuation semantics can make volume 3 audible to about 48 blocks. The owner must choose fixed-max delivery, dynamic relevance/lifecycle, or an intentional audible-range cap.
- **KI-060:** renderer startup is latched before `SoundManager` proves the sound actually started. Minecraft supports `SoundInstance.canStartSilent()` for long-lived silent sounds, so the owner should choose between keeping an inaudible stream active or deferring local start and catching up on unmute.
- **KI-055:** there is still no real-MP3 progressive JLayer fixture test across range progression/starvation and no focused `FinitePcmAudioStreamTest`; historical Lua scripts are not modern prepared-path proof.
- **KI-061:** every speaker gets a random persistent ComputerCraft staging save-directory, but staging cleanup does not clear leftover files. Low-level/interrupted staging can therefore accumulate unreachable files across speaker recreation/restarts.
- **KI-054:** shutdown deletion failure loses completed-file retry bookkeeping and can skip `ServerMediaAssets` registry removal. This is lower-frequency shutdown hardening.

See `KNOWN-ISSUES.md` and `TESTING.md`. These findings are documented only; source is still at the green integrated checkpoint above.

## Remaining owner decisions

### Decoder snapshot/re-anchor model

Two broad approaches are reasonable:

- **Minimal v6/client patch:** keep protocol v6, make ordinary STATE preserve a healthy epoch, make expected cancellation invalidate the local epoch before waking workers, and continue relying on CONTROL SEEK for semantic seek intent. Smaller change, but more ordering/projection coupling remains.
- **Explicit decode/reanchor revision:** add a server-authoritative decode/reanchor revision (protocol update) which changes only when a fresh codec epoch is required. STATE becomes self-sufficient for seek/rejoin intent, while ordinary pause/resume/volume/loop snapshots do not restart a healthy decoder. More protocol/test churn, but cleaner semantics and a better base for loop/rejoin work.

Do not silently choose between these.

### Loop-wrap rejoin

The server clock already owns canonical looping. After local physical EOF during a looping session, choose one architecture before implementing loop-wrap behavior:

- **L1:** client EOF requests fresh authoritative STATE. Strongest authority model; possible roundtrip/prebuffer gap.
- **L2:** server detects canonical wraps and proactively projects STATE. Potentially tighter boundary; more server wrap/fanout state.
- **L3:** client predicts/restarts locally and reconciles later. Lowest latency; weaker server-authority purity and added drift/reconciliation state.

### Volume/range behavior

Decide whether modern finite volume should mirror normal Minecraft/CC:T distance semantics through volume 3. If yes, the fixed 32-block server relevance rule must change or be replaced by a lifecycle-aware policy.

For volume 0, canonical server time already continues. Two local renderer implementations are reasonable:

- allow `FiniteSpeakerSound` to start silent and keep consuming in sync;
- defer renderer creation while inaudible and catch up to current server time on unmute.

The first is simpler but keeps an audio source active while silent; the second saves that source but adds lifecycle/catch-up logic.

## Evidence boundaries

```text
M1E final focused Minecraft acceptance: skipped / unrecorded
M1F focused Minecraft transport acceptance: unrecorded
M1G integrated source/tests/package: green at 957832348eaa6e497282d923f2312c9c7d7c550f
M1G decoder snapshot/reanchor correctness: open KI-053/KI-056/KI-057
M1G live volume/range/start correctness: open KI-058/KI-059/KI-060
M1G staging lifecycle cleanup: open KI-061
M1G real-MP3 progressive integration coverage: incomplete
M1G focused FinitePcmAudioStream coverage: incomplete
M1G loop-wrap rejoin: unresolved owner choice
M1G audible Minecraft PASS: unrecorded
```

Full late-entry/leave-return/dimension-reload/general-underrun/final-VS2 lifecycle remains M1H unless a subset is intentionally pulled forward for dynamic volume-aware relevance.

## Read order

1. `CURRENT-STATE.md`
2. `KNOWN-ISSUES.md`
3. `TESTING.md`
4. `VERIFIED-FACTS.md`
5. `HANDOFF-2026-09-13-M1G-START.md`
6. `M1G-DESIGN-DECISIONS-2026-09-13.md`
7. `ROADMAP.md`
8. `LUA-API.md`
9. `M1F-FINALIZATION-2026-09-13.md`
10. exact current source and CI

Historical milestone/handoff documents preserve checkpoint history and do not override current records.
