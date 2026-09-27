# Runtime results — protocol v10

Updated: 2026-09-27

Fill this during the final diagnostic master run.

## Build

- runtime-tested candidate checkpoint: `84bce876106345553155aa1dcab72a45c72f3360`
- runtime-tested candidate CI: `36238699768` — PASS
- runtime-tested artifact: `10905071824`
- runtime-tested JAR SHA-256: `32e6f0956da581295819bd97c6b94c42d2689ca8071894baf4c88fbd5277d9d8`
- current master-runner checkpoint: `7b8e4d4a095611bf89d05726c4824a95edcea3ba`
- current master-runner CI: `36285305761` — PASS
- current CI artifact: `10920218268`; extracted JAR contents are identical to the runtime-tested candidate JAR (archive metadata differs)
- previous locked pre-runtime checkpoint: `37755ccdb34ac72a27797dc2e6581463e85cbcd7`
- built against NeoForge: **21.1.247**
- supported NeoForge metadata range: **[21.1,21.2)**
- Minecraft: 1.21.1
- CC:Tweaked: 1.120.0
- Sable/Aeronautics: Sable 2.0.5 / Create Aeronautics 1.3.2 confirmed in attempt 3
- Sound Physics Remastered: 1.21.1-1.5.1 confirmed loaded in attempt 3
- direct MP3/ICY radio URL supplied: `https://stream.nightride.fm/nightride.mp3`

Do not create a separate 21.1.248 artifact.

## Master acceptance

Run:

```
v10_acceptance <mp3> <wav> [direct-mp3-or-icy-url] [--resume]
```

| Result | Status | Notes |
| --- | --- | --- |
| A1-A19 deterministic | PASS (attempts 2-6) | complete deterministic core repeatedly passed on exactly 2 speakers |
| R1 native client channels | PASS (attempt 6) | real native static + DFPWM client channels observed |
| R2 MP3 pause/resume renderer | PASS (attempt 6) | real OpenAL pause/resume + PCM continuity |
| R3 WAV renderer continuity | PASS (attempt 6) | real WAV renderer stayed healthy |
| R4 finite multispeaker sync | PASS (attempt 6) | 2 real client sources synchronized within limits |
| R5 endpoint-local client controls | PASS (attempt 6) | stop/gain/mute isolation reached client |
| R6 continuous RAW | PASS (attempt 6) | producer-fed continuation fix runtime-confirmed |
| R7 loop-boundary recovery/sync | PASS (attempt 6) | authoritative loop recovery + sync passed |
| R8 range leave/rejoin | PASS (attempt 6) | real leave/rejoin recovery passed; attempt 5 had one transient 65.34 ms baseline drift failure before the clean rerun |
| R9 F3+T recovery | PASS (attempt 6) | clean baseline, real sound-engine reload, authoritative rejoin, decoderFailures=0 |
| C1 Sable/Aeronautics tracking | HARNESS FAIL / PRODUCT EVIDENCE GOOD (attempt 7) | requested and actual OpenAL movement matched over ~52-53 blocks; failure came from historical STOPPED transition after listener leave/rejoin, which is not a Sable-tracking defect |
| C2 Sound Physics Remastered | INVESTIGATION REQUIRED (attempt 7) | HQ sources were touched by SPR diagnostics, but open-air and wall direct gain/HF both stayed 1.0000; current hook does not prove an actual `processSound` ray evaluation |
| C3 MP3/ICY radio + strict membership | PASS (attempt 7) | sustained grouped radio, metadata, sealed late membership, rerun membership, singular and indexed paths all passed |
| C4 8+ speaker scale stress | FAIL — FINITE RANGE ADMISSION (attempt 7) | 8 finite endpoints reached server PLAYING, but one client source started ~2.219 s late; RAW half was not reached |

A **TARGET FULL PASS** requires every required runtime diagnostic and every selected target-scope check to pass. A **CORE PASS / TARGET INCOMPLETE** means the automatic core passed but one or more target checks were skipped.

## Evidence to keep

- `/v10-acceptance.log`
- Minecraft `latest.log` on any failure
- exact JAR hash
- any unusual event while performing a requested physical action

The operator performs requested actions but does not assign PASS/FAIL; built-in diagnostics do.

## Attempt 1 — 2026-09-26

- runner reached A9 in about 9.3 seconds;
- A1-A8 passed;
- A9 successfully rejected empty/out-of-range/non-number/non-finite RAW, non-finite volume, out-of-range speaker index, and maxSamples+1 before the runner itself failed;
- harness failure: `/v10_acceptance.lua:887: attempt to call global 'collectgarbage' (a nil value)`;
- fix: removed the unsupported call and added a regression guard against reintroducing it;
- Minecraft log also exposed `java.util.Optional` being returned directly by `getStreamUrl()`; CC:T converted that unknown Java type to nil;
- fix: `getStreamUrl()` now returns zero Lua values for inactive/nil and one string value when active, with a regression check;
- startup evidence showed 4 attached speakers. The corrected master runner now requires exactly 2 at startup so the 2-speaker core and later membership/8+ transitions are unambiguous;
- Sound Physics Remastered was not loaded in that launch, so C3 could not have produced a target full pass;
- no R1-R9/C1-C6 client diagnostic result was reached; those remain pending.

## Attempt 2 — 2026-09-26

- startup correctly saw exactly 2 attached speakers on the Sable contraption;
- A1-A19 all passed; the complete deterministic core is now runtime-confirmed;
- diagnostics enabled successfully, then R1 failed with `clientSeen=false`, `sourceCount=0`, and no OpenAL renderer/vendor evidence because no native CC:T sound channel was created;
- root cause: the real CC:T block speaker publishes its raw block-centre coordinates for native `playNote`, `playSound`, and `playAudio`. Inside a Sable sub-level those are plot-space coordinates (for this run around x/z 20,481,0xx), not the contraption's actual world-space position, so Minecraft culls the native source before a real client channel exists;
- fix: a narrow mixin replaces only CC:T's returned `SpeakerPosition` with the existing `MovingSourcePosition` Sable/VS2/static resolver. The real CC:T peripheral still owns native playback, buffering, packets, events, and stop semantics;
- static speakers retain the same block-centre coordinates through the resolver fallback;
- NeoForge 21.1.247 CI passed with the position fix;
- R2-R9 and C1-C6 were not reached and remain pending.

## Attempt 3 — 2026-09-26

- startup again saw exactly 2 attached speakers and A1-A19 all passed;
- the native Sable position fix worked: diagnostics became client-visible and observed OpenAL Soft, integrated singleplayer, Sound Physics Remastered, and 2 real native CC:T sources;
- native source positions were real world-space coordinates around x 53 / y 229 / z -24 rather than Sable plot-space coordinates around x/z 20,481,0xx;
- one real `native-static` source (CC:T `playSound`) and one real streaming `native` source (CC:T DFPWM `playAudio`) were observed healthy;
- R1 still failed because the runner incorrectly expected `playNote` to appear as a second `SpeakerSound`-backed static source. CC:T implements `playNote` with Minecraft's ordinary `ClientboundSoundPacket`, so it is not safely attributable through the SpeakerSound hook; A4 continues to cover native `playNote` deterministically while R1 now checks attributable real client channels for `playSound` and `playAudio`;
- `latest.log` also showed repeated OpenAL `Invalid enumerated parameter value` errors only after diagnostics enabled. Root cause was our diagnostic use of `alGetSourcei(AL_DIRECT_FILTER)`; OpenAL Soft treats that property as non-queryable;
- fix: diagnostics no longer query `AL_DIRECT_FILTER`. An optional SPR mixin records the exact direct gain/cutoff values when Sound Physics applies `setEnvironment`, so C3 remains automatic without contaminating OpenAL's error state;
- regression tests now guard both the CraftOS runner contract and the forbidden OpenAL getter;
- NeoForge 21.1.247 CI passed with the complete R1/SPR diagnostic fix;
- R2-R9 and C1-C6 were not reached and remain pending.

## Attempt 4 — 2026-09-26

- startup again saw exactly 2 attached speakers and A1-A19 all passed;
- R1-R8 all passed automatically, including the previously unconfirmed continuous RAW client-delivery fix in R6 and real range leave/rejoin in R8;
- R9 started a healthy looping 2-speaker finite group, but the first action-gate text already told the operator to press F3+T. The reload was triggered about 1.8 seconds after playback start, while the runner was still collecting its required 2-second pre-reload baseline;
- because F3+T happened inside the baseline window, the eventual `resource reload baseline` snapshot already contained the reload/rejoin history and could not serve as a clean pre-reload baseline;
- the Minecraft log confirms a real resource reload: the ResourceManager reload began at 14:16:08.355, both finite renderer streams were closed for recovery at 14:16:09.645-09.646, and OpenAL/Sound Physics/sound engine were reinitialized at 14:16:12.892-12.896;
- the finite decoder cancellation caused by renderer teardown was already handled by the product as an authoritative rejoin and both server endpoints remained in the same playing playback. However, diagnostics incremented `decoderFailures` before checking the expected renderer-close cancellation path, so the recovered reload was incorrectly labeled a decoder failure;
- fix 1: expected renderer-close decoder cancellation now counts only as recovery/rejoin; `decoderFailures` is incremented only for a real decoder exception which is not the renderer-close recovery path;
- fix 2: R9 now explicitly says ENTER only prepares the baseline and **DO NOT press F3+T yet**; after the clean baseline is captured it shows a second `BASELINE READY -- NOW` prompt;
- regression tests guard both the cancellation classification and the R9 baseline-before-action ordering;
- the brief OS-level "Not Responding" observation is consistent with the heavy F3+T resource/shader/sound rebuild in this modpack; the logs show the render thread resumed and the sound engine reinitialized, with no HQSpeaker crash or deadlock;
- NeoForge 21.1.247 CI passed with the complete R9 harness/diagnostic fixes;
- R9 still requires one clean rerun on the corrected artifact; C1-C6 remain pending because the run stopped at R9.

## Attempt 5 — 2026-09-26

- same runtime candidate and exactly 2 attached speakers;
- A1-A19 and R1-R7 passed again;
- R8 failed before the physical range action because its initial 2-speaker baseline recorded a transient maximum canonical/logical drift of 65.34 ms against the 50 ms limit;
- both sources otherwise had real playing OpenAL channels, zero decoder failures and identical PCM-read totals; a fresh full rerun was used instead of weakening the limit;
- no source/product change was made from this one-off R8 result.

## Attempt 6 — 2026-09-26

- same runtime-tested JAR; A1-A19 and R1-R8 passed;
- R9 F3+T passed cleanly after the corrected two-stage prompt and renderer-close diagnostic classification: the recovered snapshot had one real sound-engine reload, both sources playing, recovery rejoins present and decoderFailures=0;
- C1 then started a healthy looping 2-speaker playback and the player changed to the Nether;
- the master log ended during the dimension transition with an idle finite-state event and never recorded C1 PASS/FAIL/SKIP, so C1 has **no diagnostic verdict**;
- Minecraft logs show the dimension transition, finite renderer teardown/rejoin attempts, Sable reporting unknown sub-level tracking removals, and both HQ speaker peripherals attaching to computer 1 again after return. That means the Sable-hosted test computer/sub-level did not satisfy C1's keep-loaded prerequisite in this setup;
- the operator stopped after repeated prior reruns rather than restarting the entire suite again;
- the master runner now supports opt-in `--resume`: it reads prior PASS lines from the existing master log, reuses those results, and reruns only unfinished/failed/skipped checks. This avoids repeating A1-A19/R1-R9 after an environment check interrupts the computer.

## Attempt 7 — 2026-09-27

- the run resumed prior PASS results for A1-A19 and R1-R9 and executed all four current target checks;
- final target summary was `auto=19/19`, `runtime=9/9`, `target=1/4`, with C3 passing and C1/C2/C4 recorded as failures;
- C1's recorded failure is an acceptance-harness error, not evidence that Sable tracking failed. Both finite sources ended PLAYING with zero decoder failures; requested movement was about 53.224 / 52.267 blocks and actual OpenAL movement was about 53.224 / 52.267 blocks. The assertion tripped only because the long movement run included expected listener relevance leave/rejoin history and therefore one historical PLAYING->STOPPED sample. The same playback ID received repeated BEGINs while the player moved out of/into the 32-block relevance radius. C1 must stop inheriting the continuous-playback assertion and then be rerun for a clean recorded PASS;
- C2 observed `soundPhysicsProcessed=true` / one environment sample per HQ source, but both open-air and wall measurements remained direct gain `1.0000` and HF `1.0000`. This proves only that the current diagnostic hook saw SPR's environment-write path; it does **not** prove that SPR actually executed its world/ray `processSound` path for the HQ source. The user's later visual-ray check happened after the radio sources had already started, and SPR 1.21.1 defaults `update_moving_sounds` to false, so absence of newly-rendered rays at that point is not conclusive. Next diagnostic work must record actual `SoundPhysics.processSound` calls and distinguish static-world geometry from Sable-sublevel geometry before changing playback architecture;
- C3 passed completely. The 2-speaker grouped radio ran for 30 seconds with about 1.91 ms channel-start skew and 0.00 ms settled drift, metadata arrived, a late third speaker stayed out of the sealed running group, rerunning admitted it, and singular/indexed radio starts passed;
- C4 finite exposed a real scale bug. Eight endpoints entered server PLAYING immediately, but seven real client channels started around 115-145 ms while one source started around 2334 ms, producing 2219.15 ms channel-start skew. The delayed source's decoded content began around 2.265 s while the others began around 0.047 s;
- source-level reconstruction matches that delay: each finite client may hold 2 in-flight range requests, so 8 endpoints can attempt 16 requests, while `FiniteRangeReadService` permits only 4 outstanding requests per player. `OVER_LIMIT` is currently silently dropped by `HQFiniteMediaServer.acceptRangeRequest0()`, leaving the client request marked pending until `REQUEST_TIMEOUT_NANOS = 2_000_000_000` expires. The observed ~2.2 s late start is therefore strongly attributable to this admission/retry contract;
- no `M1G` renderer-start/rejoin warning, finite starvation warning or HQ decoder failure accompanied the C4 delay, which argues against the eighth Minecraft channel simply failing to allocate in this run;
- C4's RAW section never executed because the finite assertion threw first. 8+ RAW therefore remains untested;
- there is a separate architecture risk for **more than eight** HQ sounds because Minecraft's streamed-source pool is small. This is not yet the demonstrated cause of attempt 7's eight-speaker failure and must be tested independently after the range-admission bug is fixed;
- the old `cchq-soundphysics-compat` project is now an explicit architecture research input. It was built against the older fork and is not drop-in compatible with current finite/RAW/radio paths. Reuse or redesign must be decided after re-evaluating the current fork, with simplicity preferred where it achieves the same correctness.

Detailed forensic notes are preserved in `docs/RUNTIME-INVESTIGATION-2026-09-27.md`.

## Acceptance-runner simplification — 2026-09-27

The product/runtime-tested JAR is unchanged. The master runner was simplified after reviewing the six live attempts:

- A1-A19 remain because they finish quickly and validate the real CC:T/Lua integration boundary;
- recovery scenarios no longer inherit unrelated synchronization thresholds;
- dedicated sync scenarios use three consecutive settled **current** playback-offset samples rather than a historical worst-ever drift value;
- independent failures are recorded with a full forensic snapshot, cleaned up and followed by later independent checks instead of terminating the whole suite;
- successful snapshots/logging are compact; detailed snapshots are failure-only;
- monitor/dashboard code and dead UI state were removed; multi-step actions print their second-stage prompts directly in the terminal;
- target checks are now C1-C4: Sable movement, Sound Physics, radio+membership and 8+ scale;
- the previous dimension action is no longer part of release acceptance;
- grouped radio continuity and strict late-membership are one scenario; singular/indexed radio paths remain lightweight checks inside it;
- `--resume` remains for genuine interruptions, while ordinary failures no longer require a restart.

The previous R8 audible-distance comments remain operator observations only; they are not used as a release verdict or public range contract.

## Failures

For each failure record:

- exact test name;
- diagnostic message;
- relevant measured values;
- reproduction steps;
- `latest.log` excerpt if relevant;
- whether the failure repeats.

## Post-attempt source checkpoint — 2026-09-27

No newer Minecraft runtime verdict exists yet; the attempt-7 table above remains historical evidence.

The branch has since implemented the exact follow-up work needed for the next run:

- C1 no longer rejects expected listener relevance leave/rejoin history;
- finite clients share the server's 4-request per-player admission budget fairly instead of independently attempting up to 16 requests at eight endpoints;
- C4 finite and RAW are independent subchecks;
- direct SPR `processSound` diagnostics are scoped to HQ custom sounds and correlated with their environment writes;
- C2 uses a normal-world wall and distinguishes live long-running refresh from a fresh behind-wall restart;
- C3 radio and C4 RAW now require direct SPR process evidence;
- C3 was renamed intentionally so `--resume` cannot reuse the old C3 PASS without the new SPR-path evidence.

These are source/CI changes only. C1/C2/C3/C4 must be rerun before any target status is upgraded.

## Release verdict

The current master has completed, but target acceptance is **not** release-ready.

- master result: A1-A19 PASS, R1-R9 PASS, C3 PASS; C1 harness correction required, C2 SPR architecture/diagnostic investigation required, C4 finite scale bug requires source fix, C4 RAW still untested;
- target-scope acceptance: incomplete;
- release blocker(s): finite range-admission/retry scale defect; unresolved current SPR integration behavior; missing post-fix 8+ RAW evidence;
- release-ready: **no**
