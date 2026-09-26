# Runtime results — protocol v10

Updated: 2026-09-26

Fill this during the final diagnostic master run.

## Build

- runtime-tested candidate checkpoint: `84bce876106345553155aa1dcab72a45c72f3360`
- runtime-tested candidate CI: `36238699768` — PASS
- runtime-tested artifact: `10905071824`
- runtime-tested JAR SHA-256: `32e6f0956da581295819bd97c6b94c42d2689ca8071894baf4c88fbd5277d9d8`
- current master-runner checkpoint: `1360eb2f04d0e22420175040ce2e3735c4e9b294`
- current master-runner CI: `36240136369` — PASS on retry; first attempt failed only because NeoForge Maven returned HTTP 502
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
| C1 dimension leave/rejoin | INCOMPLETE / NO VERDICT | current Sable sub-level/computer did not stay stable across dimension transition; runner stopped before it could judge recovery |
| C2 Sable/Aeronautics tracking | PENDING | target check |
| C3 Sound Physics Remastered | PENDING | target check |
| C4 grouped MP3/ICY radio | PENDING | requires direct radio URL |
| C5 strict radio membership | PENDING | requires direct radio URL + extra speaker |
| C6 8+ speaker scale stress | PENDING | finite + RAW |

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

## Failures

For each failure record:

- exact test name;
- diagnostic message;
- relevant measured values;
- reproduction steps;
- `latest.log` excerpt if relevant;
- whether the failure repeats.

## Release verdict

Do not fill until the master runner has completed.

- master result:
- target-scope acceptance:
- release blocker(s):
- release-ready: yes / no
