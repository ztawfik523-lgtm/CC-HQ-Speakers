# Handoff — 2026-09-29 — v11 runtime validation

## Start here

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`  
Branch: `codex/m1j-multispeaker`

Read, in order:

1. `docs/CURRENT-STATE.md`
2. `docs/RUNTIME-ACCEPTANCE-V11.md`
3. `docs/KNOWN-ISSUES.md`
4. `docs/ROADMAP.md`
5. `docs/LUA-API.md`
6. `docs/SERVER-CONFIG.md`
7. `docs/ARCHITECTURE.md`
8. `docs/TESTING.md`

Do not restart broad architecture/design cleanup. The next phase is runtime validation of the implemented v11 candidate.

## Implementation checkpoint

Implementation source: `ac4548749bd16ab161eae9f233e89cb43ed4c0ce`  
CI: `36467453786` — PASS  
Artifact: `10989724714` — `hqspeaker-neoforge-21.1.247`  
JAR SHA-256: `c4240e252bbc57c3ef767b215f82b3ecd4cba368ebcfaa937b78d442a01adf66`.

Protocol: **v11**, still exactly 9 payloads.

Commits after the implementation checkpoint may be documentation-only. On a new chat, verify current branch head and latest CI once, then continue. Do not assume a newer docs commit means the implementation changed.

Build/test/package only NeoForge **21.1.247**. Metadata stays `[21.1,21.2)`; do not build a separate 21.1.248 artifact.

## Product decisions already made

Do not reopen these unless runtime evidence shows a concrete defect.

- only normal `computercraft:speaker` is upgraded;
- native CC:T speaker methods remain native behavior;
- finite MP3 + supported WAV only;
- RAW signed-16 mono 48 kHz;
- radio MP3/ICY only;
- finite multispeaker membership is a start snapshot;
- grouped radio membership is strict snapshot/rerun;
- pause/resume/seek/loop and ordinary shared stop are shared finite controls;
- volume/range/mute are endpoint-local;
- `audioStopAt(index)` stops only that endpoint;
- diagnostics stay in the release JAR but are dormant unless enabled;
- Sable Companion -> VS2 -> static center position resolver;
- dedicated multiplayer and VS2 runtime are outside current acceptance;
- exactly eight streamed HQ sources are the selected scale acceptance target; >8 is a separate question.

## V11 volume/range contract

Logical volume is continuous `0..3`.

- `1.5` = normal.
- `3` = maximum.
- invalid/out-of-server-limit requests throw Lua errors.

Fixed input anchors:

```text
0, 0.5, 1, 1.5, 2, 2.5, 3
```

Selected gain outputs:

```text
0, 0.17, 0.34, 0.50, 0.67, 0.84, 1.0
```

Selected automatic ranges:

```text
0, 12, 29, 48, 70, 96, 132 blocks
```

Interpolation is linear between anchors.

Explicit range is in blocks and overrides only automatic range. Default server max explicit range is 256.

Server config is authoritative. Clients receive resolved gain/range.

Live reload rule:

```text
active source keeps its starting profile
next finite / RAW / radio source uses current config
```

Continuous RAW keeps its profile until that RAW lifetime ends/stops. No per-computer snapshot.

## SPR/acoustic implementation

Target: Sound Physics Remastered 1.21.1-1.5.1.

Root cause established before implementation: long-lived HQ sources were processed at start but did not update as listener geometry changed unless SPR's global Update Moving Sounds was enabled. User confirmed turning that global option ON made HQ wall occlusion update, but global moving-sound updates are too expensive with Sable.

Current HQ-only scheduler:

- displacement since last successful refresh;
- ~0.15 block ordinary threshold;
- >=100 ms moving cadence;
- >=1 block urgent;
- settle refresh after ~250 ms stillness when residual movement remains;
- fixed 1 second safety refresh;
- starts/resumes urgent;
- one expensive HQ SPR refresh globally at a time;
- no overdue burst/catch-up;
- physical source position, not reflected render position.

Each due call uses full normal SPR `processSound`.

Accepted acoustic tuning ported from the runtime-approved old compat behavior:

- full 17 probes: center + 8 inner 0.20 + 8 outer 0.49;
- adaptive partial refreshes: center+inner / center+outer = 9 fresh paths;
- center weight 4, inner 1, outer 0.5, denominator 16;
- ring scale `0.20 + 0.80*smoothstep(center)`;
- direct cutoff scale 0.35;
- direct gain scale 0.50;
- muffling alpha 0.30;
- clear cutoff alpha 0.18 log-space;
- clear gain alpha 0.16 log-space;
- room/reverb alpha 0.22;
- reflection threshold 0.45;
- reflection blend 0.35;
- max reflection offset 2.5;
- redirect alpha 0.22;
- clear-to-real alpha 0.28;
- flip-to-center alpha 0.35.

SPR remains authority for room/reverb/reflection computation.

### Private EFX decision

This is settled: HQ sources need private per-source direct/send low-pass filters.

Exact SPR 1.21.1-1.5.1 source uses one shared mutable filter set in stock `setEnvironment`. Historical compat runtime already showed cross-speaker contamination and showed that attach-once filter mutation can break muffling.

Current v11 therefore:

- creates private filters only after source state is PLAYING/PAUSED;
- reattaches direct/send filters on every environment application;
- retains SPR native aux effect slots/reverb targets;
- falls back to stock SPR environment application if private EFX cannot be applied;
- suppresses raw reflected position only when HQ stabilization owns it;
- preserves native reflected position in fallback/strict modes.

Do not remove private EFX as "cleanup" without new runtime evidence.

## Source recheck fixes already completed

Before handing this off, source/CI rechecks found and fixed:

- old fixed 32-block range still affecting server delivery/relevance;
- client attenuation consistency;
- RAW `All` with endpoints that began under different config snapshots;
- scheduler accidentally reading reflection-stabilized render position;
- native reflected position being reset by Minecraft ticks;
- progressive samples being capped too early;
- legacy packet path deriving tuning client-side/outside server authority;
- shared SPR filter contamination;
- private filter lifecycle/reuse cleanup;
- active SPR aux-slot validation;
- diagnostics now require progressive work + private filter evidence.

CI is green after all of those fixes.

## Runtime evidence already established

Historical v10:

- A1-A19 PASS;
- R1-R9 PASS;
- C1 Sable PASS;
- C3 radio/membership PASS;
- C4 RAW PASS at eight;
- C4 finite old ~2.2 s admission stall removed, corrected catch-up alignment millisecond-scale;
- A8/A9/A18 rejection PASS.

Do not rerun all of that before the focused v11 C2 test. v11 needs targeted rechecks because the gain/range and SPR paths changed.

## First next action

Run the focused v11 C2 test.

Use:

```
delete v11_c2_spr
wget https://raw.githubusercontent.com/ztawfik523-lgtm/CC-HQ-Speakers/codex/m1j-multispeaker/scripts/v11_c2_spr.lua v11_c2_spr
v11_c2_spr /cchq-speaker-runtime-test-48k-mono.mp3
```

Setup:

- one computer + one speaker on normal Minecraft ground;
- one solid normal-world wall;
- SPR Update Moving Sounds OFF;
- start open side with clear line of sight;
- follow script prompts.

Afterward inspect **all** of:

- `/v11-c2-spr.log`;
- Minecraft `latest.log`;
- Minecraft `debug.log`.

Do not mark C2 closed unless the script passes and logs do not reveal a hidden failure.

## After C2

If C2 passes, continue in this order:

1. volume/range anchors + interpolation + explicit override;
2. Lua error boundaries;
3. live server-config reload/new-source rule;
4. finite/RAW/radio gain+range regressions;
5. 8-speaker finite + RAW;
6. simultaneous clear/occluded SPR sources to prove no private-filter cross-contamination;
7. long-range SPR characterization.

Only after those pass should docs/API be frozen as the runtime-tested v11 release candidate.

## User/project preferences

Keep explanations concrete and short. Avoid speculative future-proofing or extra architecture unless a demonstrated problem needs it. Diagnostics in the release JAR are fine. Prefer automated PASS/FAIL evidence over asking the user to judge audio manually. When there are meaningful alternatives, present the tradeoff briefly and let the user choose; do not invent unnecessary alternatives.
