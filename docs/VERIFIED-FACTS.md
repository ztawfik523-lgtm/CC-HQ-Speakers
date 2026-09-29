# Verified facts

Updated: 2026-09-29

## Current source/CI facts

- active implementation checkpoint: `ac4548749bd16ab161eae9f233e89cb43ed4c0ce`;
- CI `36467453786` passed;
- artifact `10989724714`, JAR SHA-256 `c4240e252bbc57c3ef767b215f82b3ecd4cba368ebcfaa937b78d442a01adf66`;
- protocol is **v11 with exactly 9 payloads**;
- build/test/package baseline is NeoForge 21.1.247 only;
- metadata accepts `[21.1,21.2)`;
- only `computercraft:speaker` is the block product;
- standalone HQ block, HLS/TS and retired generic whole-file aliases remain removed.

Source/CI also verifies the current implementation contains:

- server-authoritative volume -> gain/range anchor resolution;
- explicit range override + Lua error validation;
- dynamic per-source server/client audible range;
- HQ-only long-lived SPR refresh scheduling;
- progressive 17/9-probe direct occlusion;
- smoothing and reflection stabilization;
- private per-source HQ EFX with native SPR fallback.

These are source facts, not yet full runtime acceptance.

## Runtime-confirmed regression facts

Historical v10 runtime evidence established:

- A1-A19 and R1-R9;
- native CC:T sound/DFPWM client channels with Sable world-space positioning;
- finite MP3/WAV real OpenAL rendering;
- 2-speaker finite synchronization and endpoint-local controls;
- continuous RAW continuation;
- loop recovery;
- listener leave/rejoin;
- F3+T finite recovery;
- C1 Sable source tracking;
- C3 grouped MP3/ICY radio + metadata + strict late membership;
- C4 RAW at eight speakers;
- focused A8/A9/A18 rejection behavior.

The final v10 C4 finite rerun removed the old ~2.2-second admission-stall fingerprint. Real channel-start spread was about 58.7 ms; reconstructed finite catch-up alignment was only a few milliseconds. The older queue-relative OpenAL-offset failure was a diagnostic metric error, not proof of bad media alignment.

## SPR root-cause fact

Before the v11 compatibility implementation, the same continuously-playing HQ source did not update wall occlusion while listener geometry changed. Enabling SPR's global Update Moving Sounds made it update. This established missing ongoing reevaluation as the practical root cause.

The v11 source implements an HQ-only refresh/acoustic path so the global SPR option can remain OFF. That new path still needs focused runtime proof.

## Current acceptance status

The branch is source/CI green but **not v11 runtime accepted**.

Required next evidence is defined in `RUNTIME-ACCEPTANCE-V11.md`, starting with `v11_c2_spr`.
