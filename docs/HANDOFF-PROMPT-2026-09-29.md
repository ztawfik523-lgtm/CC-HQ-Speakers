# Next-chat prompt — CC:HQ Speakers v11 runtime

Continue repository `ztawfik523-lgtm/CC-HQ-Speakers`, branch `codex/m1j-multispeaker`.

First read `docs/HANDOFF-2026-09-29-V11-RUNTIME.md`, then the current authority docs it lists.

Verify the current branch head and latest CI **once**. The implementation checkpoint handed off is `ac4548749bd16ab161eae9f233e89cb43ed4c0ce`, CI `36467453786` PASS, artifact `10989724714`, JAR SHA-256 `c4240e252bbc57c3ef767b215f82b3ecd4cba368ebcfaa937b78d442a01adf66`. Later commits may be docs-only.

Do not restart design/cleanup. Protocol is v11 with exactly 9 payloads. Build/test/package NeoForge 21.1.247 only.

Immediate task: run/analyze the focused `v11_c2_spr` runtime test with one normal-ground computer/speaker, one normal-world solid wall, and Sound Physics Remastered Update Moving Sounds OFF. Inspect `v11-c2-spr.log`, `latest.log` and `debug.log` carefully. The test must prove repeated SPR processSound, progressive probes, private per-source EFX and measurable wall occlusion on the same continuously-playing source.

If C2 passes, continue with the v11 volume/range/config and regression/8-speaker checks in `docs/RUNTIME-ACCEPTANCE-V11.md`.

Do not reopen settled product decisions unless runtime evidence demonstrates a concrete defect.
