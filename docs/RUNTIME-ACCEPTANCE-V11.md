# Runtime acceptance — protocol v11

Updated: 2026-09-29

This is the active runtime plan for the current v11 candidate. The older v10 acceptance/results documents are historical regression evidence only.

## Candidate baseline

Implementation checkpoint: `ac4548749bd16ab161eae9f233e89cb43ed4c0ce`  
CI: `36467453786` — PASS  
Artifact: `10989724714` — `hqspeaker-neoforge-21.1.247`  
JAR SHA-256: `c4240e252bbc57c3ef767b215f82b3ecd4cba368ebcfaa937b78d442a01adf66`  
Protocol: v11 / exactly 9 payloads.

If branch head has moved, verify current head/CI once. Documentation-only commits after the implementation checkpoint do not require restarting source design.

## Already established regression evidence

Historical v10 runtime evidence:

- A1-A19 PASS;
- R1-R9 PASS;
- C1 Sable tracking PASS;
- C3 radio/metadata/strict-membership PASS;
- C4 RAW PASS at eight speakers;
- corrected C4 finite catch-up result;
- A8/A9/A18 focused rejection PASS.

These remain useful regressions but do not by themselves approve v11.

## V11-1 — live SPR/acoustics

Setup:

- normal Minecraft ground, not Sable;
- one computer and one speaker;
- one solid normal-world wall;
- SPR Update Moving Sounds OFF.

Run:

```
v11_c2_spr /cchq-speaker-runtime-test-48k-mono.mp3
```

Expected automatic PASS requirements:

- SPR detected;
- exact HQ `processSound` path observed;
- long-lived source reevaluated with global moving-sounds OFF;
- progressive HQ direct-occlusion path runs and completes a full 17-probe refresh;
- private per-source HQ EFX direct filter exists and is applied;
- moving behind the wall causes additional processing/progressive work;
- the same continuously-playing source becomes measurably more occluded.

Required logs after the run:

- `/v11-c2-spr.log`;
- Minecraft `latest.log`;
- Minecraft `debug.log`.

Do not close C2 from listening alone.

## V11-2 — volume/range

Check representative values:

- `volume=0`;
- `0.5`;
- `1.0`;
- `1.5`;
- `2.0`;
- one interpolated value such as `1.75`;
- `3.0`.

The server defaults are:

```text
volume: 0    .5    1    1.5   2    2.5   3
gain:   0   .17   .34   .50  .67  .84   1
range:  0    12    29    48   70    96  132
```

Verify:

- status reports the expected logical volume, resolved gain and resolved range;
- explicit `range` overrides automatic range but not gain;
- `audioSetRange()` with no value returns finite playback to automatic range;
- invalid/non-finite/out-of-server-limit HQ values produce Lua errors rather than clamping.

## V11-3 — live config reload

While one source is already playing:

1. record its resolved gain/range;
2. change/reload audio tuning;
3. verify the current source remains unchanged;
4. start a new finite/RAW/radio source;
5. verify the new source uses the reloaded profile.

No computer or script restart is part of this rule.

## V11-4 — regressions and scale

Recheck:

- finite MP3/WAV;
- RAW continuation/backpressure;
- MP3/ICY radio + metadata + strict membership;
- pause/resume/seek/loop;
- endpoint-local volume/range/mute;
- Sable source tracking;
- listener leave/rejoin and F3+T recovery as needed;
- eight-speaker finite and RAW behavior with v11 packet fields.

For simultaneous SPR sources, specifically verify one occluded source does not muffle a clear source. Diagnostics should show distinct private filter state.

## V11-5 — long-range characterization

The product may be audible beyond SPR's default safe cloned-world neighborhood. Characterize representative >60-block playback/occlusion.

Do not create a special far-range fallback unless runtime evidence demonstrates a concrete problem worth fixing.

## Acceptance rule

Release acceptance is complete only when the v11-specific checks pass on the selected singleplayer + Sable/Aeronautics + SPR target and the runtime-tested artifact is frozen in the docs.

Outside this target: dedicated multiplayer, VS2 runtime, separate NeoForge 21.1.248 build and >8 streamed-source guarantee.
