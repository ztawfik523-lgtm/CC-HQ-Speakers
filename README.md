# CC:HQ Speakers

CC:HQ Speakers upgrades the normal CC:Tweaked `computercraft:speaker` with higher-quality programmable audio while preserving CC:T's native speaker behavior.

Target: Minecraft 1.21.1, Java 21, CC:Tweaked 1.120.0, NeoForge 21.1.x. Build/test/package only **NeoForge 21.1.247**; the resulting mod declares `[21.1,21.2)`.

## Current candidate

The active branch is `codex/m1j-multispeaker`.

Current implementation checkpoint: `ac4548749bd16ab161eae9f233e89cb43ed4c0ce`  
CI: `36467453786` — PASS  
Artifact: `10989724714` — `hqspeaker-neoforge-21.1.247`  
JAR SHA-256: `c4240e252bbc57c3ef767b215f82b3ecd4cba368ebcfaa937b78d442a01adf66`  
Network protocol: **v11**, still exactly 9 payloads.

Later commits on the branch may be documentation-only. Verify current head/CI once before continuing, but do not restart design work merely because the docs head moved.

The v11 implementation now includes:

- continuous logical volume `0..3`, with `1.5` as normal and `3` as maximum;
- server-configurable gain and automatic-range anchor curves;
- default automatic ranges `0 / 12 / 29 / 48 / 70 / 96 / 132` blocks;
- optional explicit range in blocks, default server ceiling 256;
- Lua errors for invalid/out-of-server-limit HQ volume/range requests;
- live server-config reload for **new playback only**; already-running sources keep their starting profile;
- server-resolved gain/range carried through protocol v11 and used for client attenuation plus server listener/delivery relevance;
- HQ-only Sound Physics Remastered reevaluation for long-lived HQ sounds while global SPR **Update Moving Sounds remains OFF**;
- the accepted progressive 17/9-probe direct-occlusion model, smoothing and reflected-position stabilization;
- private per-source HQ EFX filters so simultaneous HQ sources do not share SPR's mutable direct/send filters.

## Runtime status

Historical v10 evidence already established A1-A19, R1-R9, C1 Sable tracking, C3 grouped radio/membership, C4 RAW, the corrected C4 finite catch-up result and the focused rejection checks.

Those results remain regression evidence, but **v11 itself is not yet runtime accepted** because gain/range transport and the SPR/acoustic path changed.

The first required v11 runtime test is:

```
v11_c2_spr /cchq-speaker-runtime-test-48k-mono.mp3
```

Use one normal-world speaker/computer and a solid normal-world wall, with SPR **Update Moving Sounds OFF**. The script automatically requires repeated SPR processing, progressive probes, private HQ EFX and measurable wall occlusion on the same continuously-playing source.

After C2 passes, recheck volume/range/config behavior and finite/RAW/radio/multispeaker regressions before release acceptance.

See `docs/HANDOFF-2026-09-29-V11-RUNTIME.md` for the current handoff and `docs/RUNTIME-ACCEPTANCE-V11.md` for the active runtime plan.
