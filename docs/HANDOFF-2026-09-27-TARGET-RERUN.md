# Handoff — target rerun and ordinary SPR integration

Date: 2026-09-27

This is the authoritative handoff for the next chat.

## Repository state

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`  
Branch: `codex/m1j-multispeaker`

Source/recheck checkpoint before this handoff-doc commit:

- `87d08d3a62857dfeba16756591560eaa0e62d193`
- CI run: `36293523518` — **PASS**
- artifact: `10922887347`
- artifact name: `hqspeaker-neoforge-21.1.247`
- workflow artifact archive digest: `sha256:865d6e8a6a78437fe906abc6041f6a3b823947f6db7552ef93026148b17271c6`

Build/test/package only NeoForge **21.1.247**. Do not restore a separate 21.1.248 build.

The older runtime-proven product candidate remains:

- checkpoint `84bce876106345553155aa1dcab72a45c72f3360`
- CI `36238699768`
- artifact `10905071824`
- runtime-tested JAR SHA-256 `32e6f0956da581295819bd97c6b94c42d2689ca8071894baf4c88fbd5277d9d8`

Do **not** use that older JAR for the new target rerun: it does not contain the new target-harness, finite-admission and SPR-diagnostic fixes.

## Frozen product decisions

Do not casually redesign these unless runtime evidence proves a release blocker:

- only the normal `computercraft:speaker` is upgraded;
- native CC:T calls stay delegated;
- finite MP3 + supported common WAV;
- shared finite authority, per-endpoint renderer/state;
- start-time multispeaker membership snapshot;
- shared finite pause/resume/seek/loop/ordinary stop;
- endpoint-local gain/mute and `audioStopAt(index)`;
- RAW = signed-16 mono 48 kHz, bounded producer queue/backpressure;
- radio = MP3/ICY only, strict start-time membership, rerun to add a speaker;
- `isStreaming()` means server stream ownership/request state, not audibility proof;
- position resolver stays Sable Companion -> VS2 -> static center;
- diagnostics stay in the release JAR, dormant unless enabled;
- protocol remains v10 / 9 payloads.

The selected runtime target is singleplayer + Sable/Aeronautics + Sound Physics Remastered. Dedicated-server/multiplayer and VS2 runtime are not current acceptance targets.

## Runtime evidence already established

A1-A19 are PASS and R1-R9 are real-Minecraft PASS on the runtime-tested candidate.

Attempt 7 target findings:

- C1 runner FAIL was a false negative. Requested and actual OpenAL movement matched at ~52-53 blocks; both sources ended PLAYING with zero decoder failures. Historical STOPPED transitions came from listener relevance leave/rejoin.
- C2 was not valid ordinary-SPR evidence because the wall was a **Sable wall** and the old diagnostic observed only `setEnvironment`.
- C3 radio/membership was a clean PASS.
- C4 finite had seven sources start around 115-145 ms and one around 2334 ms: ~2219 ms skew. RAW never ran because the finite assertion aborted the combined test.

## Source work completed after attempt 7

Commits after the initial SPR architecture review:

- `e744cdb8c55572ba02f0c8b316c2baf9317cc7cb` — correct C1 assertion, split C4 finite/RAW subchecks, add real SPR `processSound` diagnostics;
- `8ebb3e1f76b8873a570e837f7ee93c4f8707b1ce` — fair shared finite-range admission budget;
- `ae2e4a72aecc78c6664c455c0c3ddaf773dbbbc3` — C2 distinguishes live long-running refresh from fresh restart;
- `8e5a03e5cbfe40c5d250a9d00b023ee009a257e2` — require direct SPR evidence for finite/radio/RAW target paths;
- `32b6a91e6b424a0e24b5a2615f3e9fe0c479f36b` — scope SPR environment attribution only to the active HQ `processSound`;
- `92208c3a369cd1e124dcef46c400ba167405645a` — rename C3 so `--resume` cannot reuse the old radio PASS without new SPR-path evidence;
- `af90f2b40a9cbc0eb98cfb820a9eb037c04a6de3` — clear pending SPR observations on OpenAL-source reuse to prevent stale attribution;
- `7a3cb23d51fa3c1514415bc9e6369096f5056259` and `87d08d3a62857dfeba16756591560eaa0e62d193` — documentation/state lock.

Every associated CI run was green; the cumulative run is `36293523518`.

### Finite C4 fix

The demonstrated bug was client/server admission composition:

- each endpoint could independently hold up to two requests;
- eight endpoints could therefore try up to 16;
- server accepts only four outstanding requests per player;
- silent `OVER_LIMIT` left client requests pending until the 2-second timeout.

Current client scheduling instead shares the server's four-request budget across local finite endpoints and distributes slots fairly, while still allowing two requests for one endpoint when spare capacity exists.

Protocol/server limits were not expanded.

### SPR diagnostic fix

Current diagnostics now observe the exact SPR 1.21.1-1.5.1 `processSound(..., SoundSource, ResourceLocation, boolean)` call.

Evidence is scoped to `hqspeaker:hq_audio_source`, correlated with the active process scope, and pending observations are cleared on source reuse. This prevents unrelated sounds or recycled OpenAL IDs from producing fake HQ SPR evidence.

No acoustic behavior override has been added yet.

## SPR architecture decision

Keep this simple.

SPR is optional and **client-side only**:

- the HQ server and network protocol do not know or care whether a client uses SPR;
- clients without SPR keep normal HQ playback;
- native Minecraft/CC:T sounds are SPR's responsibility; do not create fixes for SPR policy choices such as RECORDS handling;
- our custom HQ finite/RAW/radio sounds already use Minecraft channels under BLOCKS;
- the old `cchq-soundphysics-compat` repository is research for acoustic quality/performance ideas, not an architecture to port wholesale;
- do not add custom OpenAL ownership, smoothing, diffraction, an adaptive acoustic scheduler, reflected-position stabilization or Sable-aware raycasting unless a direct test demonstrates the need.

Sable-wall acoustics are explicitly deferred. Ordinary SPR integration must be proven first.

## Exact next runtime step

Install the JAR from artifact `10922887347`.

Use the existing attempt-7 acceptance log with `--resume` and the same test media:

- WAV: `cchq-speaker-runtime-test-48k-mono.wav`
- WAV SHA-256: `c9284031a32053cd0d2256d3a7a3490e815a2cd45d32498d6b77e5da1b712cea`
- MP3: `cchq-speaker-runtime-test-48k-mono.mp3`
- MP3 SHA-256: `9ebff3111b7c3b307382164dd42fd39b1d9e1aa07814900242570f2c64887426`
- radio: `https://stream.nightride.fm/nightride.mp3`

Command shape:

```
v10_acceptance <mp3> <wav> <radio-url> --resume
```

Use a **normal Minecraft-world wall** for C2. Do not use a wall that belongs to a Sable sublevel.

## How to interpret the next run

### C1

Expected result: clean PASS.

If movement still matches but C1 fails, inspect the exact new assertion before changing Sable/product code. The old movement implementation already had strong evidence.

### C2

This test now answers three different questions automatically:

1. did SPR actually call `processSound` on the HQ finite source?
2. while the same long-running source keeps playing, does SPR process it again after the listener moves behind a normal-world wall?
3. after restarting behind that wall, does SPR produce measurable occlusion?

Decision:

- live source updates + restart occludes -> **do not add any SPR behavior code**;
- restart occludes but live source does not update/react -> add the **smallest client-only HQ refresh path**, then rerun C2;
- restart itself does not occlude -> do not assume “long-running refresh” is the problem; investigate the normal-world setup / SPR call/result first.

Do not involve Sable geometry in this diagnosis.

### C3

C3 has a new name and must run fresh.

It must re-prove radio/membership behavior and also prove the radio HQ source entered SPR. Do not reuse the historical C3 PASS as sufficient for the new diagnostic requirement.

### C4

Finite and RAW now report independently.

Finite should show that the ~2.2-second late-start fingerprint is gone. If a similar ~2-second outlier remains, inspect the new shared scheduler/range-request evidence before touching renderer/channel architecture.

RAW must finally produce real 8+ evidence and must show direct SPR processing for the active RAW sources.

## After the target rerun

Do not redesign speculatively.

- If C1/C2/C3/C4 all pass, update runtime results and move toward release closure.
- If C2 alone proves stale long-running acoustics, implement only the small client-only refresh behavior.
- If exactly-8 finite/RAW pass, treat >8 as a separate test. Minecraft's streaming pool may become a distinct limitation above eight.
- Do not work on Sable-wall acoustics until ordinary SPR integration is settled.

## User working style / project rules

The user wants practical progress, not speculative future-proofing.

- Think critically and recheck before changing architecture.
- Do not be excessively conservative about release diagnostics; dormant diagnostic code in the release JAR is intentional.
- Avoid abstract/overtechnical explanations when reporting results.
- When there are real architectural alternatives with meaningful tradeoffs, present the main choices and let the user choose.
- Do not make the user manually judge PASS/FAIL when diagnostics can judge it.
- Never restore a duplicate NeoForge 21.1.248 build; build 21.1.247 only.

## New-chat prompt

Copy the following into the next chat:

```text
Continue the CC:HQ Speakers project from the current handoff.

Repository: ztawfik523-lgtm/CC-HQ-Speakers
Branch: codex/m1j-multispeaker

FIRST read docs/HANDOFF-2026-09-27-TARGET-RERUN.md in full, then read CURRENT-STATE.md, KNOWN-ISSUES.md, RUNTIME-ACCEPTANCE-V10.md, RUNTIME-RESULTS-V10.md, SPR-INTEGRATION-REEVALUATION-2026-09-27.md, ARCHITECTURE.md and ROADMAP.md. Verify the branch HEAD and CI state before doing anything.

Important current source checkpoint before the handoff-doc commit:
87d08d3a62857dfeba16756591560eaa0e62d193
CI 36293523518 PASS
artifact 10922887347 (hqspeaker-neoforge-21.1.247)

Do not build 21.1.248. Build/test/package 21.1.247 only.

A1-A19 and R1-R9 are already runtime-passed. Attempt 7 found:
- C1 was a harness false negative; real Sable movement was good.
- C2 used a Sable wall and insufficient old diagnostics, so it was not valid ordinary-SPR evidence.
- C3 radio/membership passed.
- C4 finite had one ~2.2-second late source; RAW never ran.

The branch now contains:
- corrected C1 final-health/movement assertion;
- fair shared finite range-request scheduling under the server's 4-request/player budget;
- independent C4 finite and RAW subchecks;
- direct HQ-only SPR processSound diagnostics with stale/recycled-source protection;
- C2 normal-world open -> live wall -> restart-wall test;
- fresh C3 radio + SPR-path requirement;
- SPR-path requirement for 8+ RAW.

SPR scope is deliberately simple:
- SPR is optional/client-side only; server/protocol remain SPR-independent.
- leave native Minecraft/CC:T SPR behavior alone;
- custom HQ finite/RAW/radio already use Minecraft channels;
- do not port the old compat architecture wholesale;
- old cchq-soundphysics-compat is acoustic research/reference only;
- do not add refresh/smoothing/diffraction/custom OpenAL/reflected-position/Sable-raycast systems unless runtime evidence demonstrates the specific need;
- Sable-wall acoustics are deferred.

NEXT ACTION:
Use artifact 10922887347 and rerun C1-C4 with the existing attempt-7 log using --resume. Use a NORMAL MINECRAFT-WORLD WALL for C2, not a Sable wall. Use the existing 40 s WAV/MP3 media and https://stream.nightride.fm/nightride.mp3.

Interpret C2 carefully:
- if the live long-running source updates and the restart occludes: add nothing;
- if restart occludes but live source stays stale: implement only the smallest client-only HQ refresh path, then rerun;
- if restart does not occlude either: investigate SPR/setup/diagnostics first, do not assume refresh is the problem.

For C4, finite and RAW now report independently. Verify the ~2.2 s finite outlier is gone and collect the missing 8+ RAW evidence.

Think deeply, recheck everything, and do not rush. Do not invent new problems or fixes without evidence. Keep explanations concrete and simple.
```
