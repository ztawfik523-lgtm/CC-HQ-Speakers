# Current state

Updated: 2026-09-28

## Working v11 candidate — not yet runtime accepted

The branch has moved past the historical v10 runtime candidate documented below.

Current source checkpoint before this docs update: `ac4548749bd16ab161eae9f233e89cb43ed4c0ce`  
Current source CI: `36467453786` — PASS  
Current artifact: `10989724714` (`hqspeaker-neoforge-21.1.247`)  
Current JAR SHA-256: `c4240e252bbc57c3ef767b215f82b3ecd4cba368ebcfaa937b78d442a01adf66`  
Protocol: **v11**, still 9 payloads.  
Build baseline: NeoForge **21.1.247 only**.

Implemented since the v10 runtime checkpoint:

- server-configurable anchor curves for logical volume -> gain and automatic range;
- default volume 1.5, selected gain anchors `0/.17/.34/.50/.67/.84/1`, selected range anchors `0/12/29/48/70/96/132`, default explicit-range ceiling 256;
- Lua errors for invalid/out-of-server-limit HQ volume/range requests;
- explicit range overrides plus endpoint-local finite range controls;
- per-source range used end-to-end for finite listener membership, RAW/radio delivery and client attenuation;
- live config rule: active source keeps its immutable profile, next source uses the current config;
- HQ-only movement-gated SPR reevaluation with global SPR moving-sound updates OFF;
- accepted progressive 17/9-probe direct occlusion, smoothing and reflected-position stabilization;
- **private per-source HQ EFX filters** while preserving SPR room/reverb targets and aux effect slots.

The 2026-09-28 full recheck found and fixed several issues before runtime testing: shared SPR filter contamination, reflected-position feedback into the scheduler, native reflection persistence, RAW-All profile-lifetime mismatch, premature progressive-path clamping and a legacy packet path that could derive tuning outside server authority.

This candidate is **source/CI verified only**. Do not call v11 runtime acceptance complete until the focused v11 C2/acoustic test and the volume/range/config/multispeaker rechecks pass.

## Historical v10 runtime evidence

Frozen product/source checkpoint: `c61b052beee03ec0f36fed725fb37483bfb57d83`  
API/docs freeze: `bb0d68c7031bf97c7c7efc10c6458992222cf394`  
Runtime-tested candidate checkpoint: `84bce876106345553155aa1dcab72a45c72f3360`  
Runtime-tested candidate CI: `36238699768` — PASS  
Runtime-tested artifact: `10905071824`  
Runtime-tested JAR SHA-256: `32e6f0956da581295819bd97c6b94c42d2689ca8071894baf4c88fbd5277d9d8`  
Current master-runner checkpoint: `7b8e4d4a095611bf89d05726c4824a95edcea3ba`  
Current master-runner CI: `36285305761` — PASS  
Current source/recheck checkpoint: `87d08d3a62857dfeba16756591560eaa0e62d193`  
Current source/recheck CI: `36293523518` — PASS  
Current source/recheck artifact: `10922887347` (`hqspeaker-neoforge-21.1.247`)  
Build baseline: NeoForge **21.1.247 only**  
Protocol: **v10**, 9 payloads.

## Build/release policy

Build, test and package only against NeoForge 21.1.247. The artifact declares `[21.1,21.2)`; do not restore a duplicate 21.1.248 build.

The current runtime candidate already contains the previously proven fixes for native CC:T Sable-world positioning, OpenAL-safe diagnostics, correct R1 attribution and F3+T recovery classification.

## Product state

Only the normal `computercraft:speaker` is upgraded. The standalone HQ block is removed. Native CC:T behavior remains delegated to the real CC:T speaker peripheral.

Frozen playback surface:

- native CC:T note/sound/DFPWM;
- modern finite MP3 + supported common WAV;
- prepared immutable finite media;
- multispeaker finite shared authority with endpoint-local render/transport/gain/mute;
- signed-16 mono 48-kHz RAW;
- MP3/ICY radio singular/All/At with strict start-time membership.

HLS, MPEG-TS, OGG/generic whole-file aliases, duplicate finite engine and stale legacy controls remain removed.

All HQ positional paths resolve Sable Companion -> VS2 -> static block center.

## Runtime evidence now established

A1-A19 and R1-R9 have all passed in real Minecraft on the selected 21.1.247 candidate.

That confirms native CC:T client channels on Sable, finite MP3/WAV rendering, 2-speaker synchronization, endpoint-local controls, continuous RAW continuation, loop recovery, listener leave/rejoin and F3+T recovery.

The latest target rerun used the same candidate JAR and narrowed the remaining work substantially:

- **A core:** A1-A7, A10-A17 and A19 passed. A8, A9 and A18 were interrupted only because the runner's error logger called a missing `shorten()` helper; a focused standalone rejection recheck now covers those three checks;
- **R1-R9:** all nine real-client/OpenAL checks passed again;
- **C1 Sable:** PASS. Both sources tracked roughly 65 blocks of requested/actual movement and ended healthy;
- **C2 SPR:** skipped because a usable ordinary-world wall was not available during that run. A one-speaker standalone C2 now requires both the computer and speaker to be placed on normal Minecraft ground, completely outside Sable;
- **C3 radio:** PASS with the updated direct SPR requirement, including strict membership rerun to eight speakers;
- **C4 8+ finite:** the old ~2.2-second admission-stall fingerprint is gone. The eight real channel starts were within about 58.7 ms. The runner's later 81.29 ms failure came from treating OpenAL's streamed-buffer-relative offset as an absolute song clock; reconstructing the shared media-zero time from channel start plus finite catch-up gives about 3.9 ms alignment;
- **C4 8+ RAW:** PASS, with about 11 ms channel-start spread and 0 ms settled drift.

## Concrete C4 finite defect

Attempt 7 proved that the old client/server admission composition was wrong: 8 endpoints could attempt up to 16 range requests while the server admitted only 4 per player, and silently dropped over-limit requests waited for the 2-second loss timeout.

A source fix is now implemented on the branch:

- all local finite endpoints share the same `MAX_OUTSTANDING_REQUESTS_PER_PLAYER` budget the server enforces;
- the client still allows up to 2 requests per source when there is spare capacity;
- shared slots are issued fairly, preferring endpoints which have issued fewer requests, so early endpoints cannot monopolize the player budget;
- protocol v10 and the 9-payload shape are unchanged;
- the 2-second timeout remains only as loss/recovery protection, not the normal admission mechanism.

This is the smallest fix for the demonstrated bug, and the latest runtime rerun confirms the ~2.2-second outlier is gone. The corrected acceptance logic now judges finite **catch-up alignment** instead of queue-relative OpenAL offset. Small millisecond-scale start differences are acceptable when the later endpoint joins the correct media position; a separate 1-second guard still catches obvious startup stalls.

## SPR state

The current branch now proves actual SPR processing instead of inferring it from `setEnvironment` alone.

Implemented diagnostics:

- an optional client-only mixin observes the exact SPR 1.21.1-1.5.1 `processSound` overload;
- process observations are scoped specifically to `hqspeaker:hq_audio_source`, so unrelated Minecraft/mod sounds and recycled OpenAL source ids cannot create false HQ evidence;
- diagnostics record process-call count, source position, category, sound id, optional reflected position and the resulting environment writes;
- finite, radio and RAW target checks now require direct SPR process evidence.

C2 uses **ordinary Minecraft-world geometry only** and now tests the smallest useful compatibility contract: one HQ finite source is measured in open air, then restarted behind the same normal-world wall. The computer and speaker must both be normal Minecraft-world blocks, not part of a Sable contraption.

No HQ-only acoustic refresh system is planned from this test. SPR's own moving-sound setting controls whether long-running sounds are periodically reevaluated; HQ should match normal SPR behavior instead of adding special refresh policy by default. The server and network protocol remain completely SPR-independent.

Sable-wall acoustics are explicitly deferred from this phase.

## Next work, in order

No further product/JAR change is currently indicated. The remaining work is focused runtime evidence:

1. run `v10_rejection_recheck` to record clean A8/A9/A18 PASS results with the fixed logger;
2. run standalone `v10_c2_spr` with one normal-ground speaker/computer setup and a solid normal-world wall;
3. run standalone `v10_c4_finite` with the existing 8+ speaker setup to record the corrected catch-up-alignment verdict;
4. if those pass, close the selected release-target acceptance;
5. keep >8 streamed-source capacity and Sable-wall acoustics as separate later questions, not blockers for the selected target.

Do not declare release acceptance complete until those items are resolved.
