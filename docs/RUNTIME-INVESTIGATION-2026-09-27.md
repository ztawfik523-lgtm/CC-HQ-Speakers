# Runtime investigation — attempt 7 (2026-09-27)

This document preserves the forensic conclusions from the first complete C1-C4 target run. It is current authority for the failures below until replaced by newer runtime evidence.

## Overall result

Prior A1-A19 and R1-R9 PASS results were resumed.

Attempt 7 target summary:

- C1 Sable/Aeronautics: runner FAIL, but movement/tracking evidence is good;
- C2 Sound Physics Remastered: unresolved;
- C3 MP3/ICY radio + strict membership: PASS;
- C4 8+ scale: finite FAIL; RAW not reached.

The master summary was `auto=19/19 failed=0`, `runtime=9/9 failed=0`, `target=1/4 failed=3 skipped=0`.

## C1 — what actually happened

The long Sable movement run ended with both finite sources PLAYING and zero decoder failures.

Measured movement:

- source 1 requested: ~53.224 blocks; actual OpenAL: ~53.224;
- source 2 requested: ~52.267 blocks; actual OpenAL: ~52.267.

The failure was `OpenAL source stopped unexpectedly while playback should have remained active`, caused by one historical PLAYING->STOPPED transition in each source.

Minecraft logs show repeated BEGINs for the same playback ID/generation during the movement period. BEGIN is sent when a listener becomes relevant again, so the run crossed the finite 32-block relevance boundary and rejoined.

Conclusion: Sable position tracking itself worked. C1 must not reuse the generic uninterrupted-playback assertion.

## C2 — what is known and unknown

Open-air and wall snapshots both saw SPR-related environment observation, but:

- direct gain stayed 1.0000 -> 1.0000;
- direct HF stayed 1.0000 -> 1.0000;
- live gain/HF range stayed 0.

The current HQ diagnostic mixin records values when SPR reaches `setEnvironment`. This proves a write path was observed but not that `SoundPhysics.processSound` performed the intended world/ray evaluation for the HQ source.

A later manual visual-ray check occurred after the radio sources were already running. SPR 1.21.1 defaults `update_moving_sounds` to false, so enabling the visualizer after an already-started sound does not necessarily cause a fresh evaluation/ray render.

Possible geometry issue which must be isolated: SPR raycasts its client-world clone. A wall built in normal world geometry and a wall belonging to a Sable sub-level are not equivalent evidence.

Required next step: instrument actual SPR `processSound` calls/results, then run a known static-world wall test and a Sable-wall test separately if Sable obstruction is in target scope.

## C3 — clean pass

Grouped radio ran for 30 seconds with 2 initial speakers.

Captured baseline:

- channel-start skew ~1.91 ms;
- settled drift 0.00 ms;
- meaningful decoded PCM on both sources;
- ICY metadata present.

A newly attached speaker stayed idle in the already-running sealed group. After rerunning `speakStreamAll`, all three speakers joined. Singular `speakStream` and indexed `speakStreamAt(2)` also started exactly one intended source.

No product change is indicated by C3.

## C4 finite — concrete root cause

Eight server endpoints reached PLAYING immediately, but real client channel starts split into:

- seven sources around ~115-145 ms;
- one source around ~2334 ms.

Measured channel-start skew: **2219.15 ms**.

The delayed source's decoded content started around 2.265 s while the others started around 0.047 s.

Current transport contract:

- each finite client source can maintain 2 pending range requests;
- 8 endpoints can therefore attempt up to 16;
- server admission allows only 4 outstanding requests / 512 KiB per player;
- `FiniteRangeReadService.submit()` returns `OVER_LIMIT` once that budget is full;
- `HQFiniteMediaServer.acceptRangeRequest0()` does not send any response for `OVER_LIMIT`;
- the client still considers that range request pending;
- client request expiry is exactly 2 seconds;
- after expiry it can request again.

This matches the observed ~2.2 second late start extremely closely.

No `M1G finite renderer start failed`, `finite renderer did not remain active`, starvation/rejoin or decoder-failure warning accompanied the late source. That makes source-pool allocation a weaker explanation for this specific eight-speaker event.

### Required fix properties

The solution should:

- preserve bounded per-player/server IO;
- avoid silent admission drops that wait for timeout;
- make multiple endpoint sources progress fairly/promptly;
- be deterministic enough for synchronized group start.

Reasonable implementation choices can be evaluated later; the bug itself is established.

## C4 RAW

Not tested. The finite assertion threw before the RAW half ran.

Change the acceptance structure so independent finite and RAW scale evidence is recorded even if one fails.

## Separate >8 source-capacity question

Minecraft's streaming source pool can become relevant when simultaneous streamed sounds exceed its capacity.

That is not the established cause of the attempt-7 8-speaker delay. Fix the finite transport admission first, rerun exactly 8, then test >8 explicitly if the release is intended to guarantee more than eight simultaneous HQ sources.

## Sound Physics architecture input

The existing `ztawfik523-lgtm/cchq-soundphysics-compat` repository was built for the older HQ fork and contains substantial acoustic/OpenAL/SPR work.

It should be treated as a reference implementation, not automatically copied. The current fork can change its own renderer and lifecycle, so the next architecture pass may simplify, reuse selected pieces, or redesign from scratch.

No architecture choice is frozen by this runtime-investigation document.
