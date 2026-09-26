# Next-chat prompt — CC:HQ Speakers

Continue the project in repository `ztawfik523-lgtm/CC-HQ-Speakers`, branch `codex/m1j-multispeaker`.

First read `docs/HANDOFF-2026-09-26-DIAGNOSTIC-RUNTIME.md`, then the current authority docs it lists. Fetch the current branch head and latest CI once before doing work.

Important current checkpoint:
- source/test checkpoint before the documentation pass: `37755ccdb34ac72a27797dc2e6581463e85cbcd7`
- CI `36205257110`: PASS
- artifact `10892998704`
- JAR SHA-256 `0febd6eceb6f165582d514afc3086d8f6e8768c5be323f67e9573ac6203995ee`
- protocol v10, exactly 9 payloads

CRITICAL BUILD POLICY: build/test/package **only NeoForge 21.1.247**. The one artifact declares `[21.1,21.2)` and is used across supported 21.1.x. Do not restore a 21.1.248 CI job or make a second .248 JAR/runtime pass.

The project is past broad cleanup/design. Product semantics are frozen unless runtime evidence exposes a real release-blocking bug.

The release JAR now has built-in dormant diagnostics. The user-facing master is `scripts/v10_acceptance.lua`; there are no user-facing phases. It automatically judges actual client/OpenAL behavior. The user should only perform physical actions when prompted, not manually grade every sound.

Selected release-test scope:
- singleplayer
- Sable/Aeronautics
- Sound Physics Remastered
- native CC:T + finite MP3/WAV + RAW + MP3/ICY radio
- 2-speaker core and 8+ speaker scale
- range/dimension/F3+T recovery
- NO VS2 runtime requirement
- NO multiplayer/dedicated-server runtime requirement

The previous continuous-RAW client cutoff bug has a code fix and diagnostics, but do not call it runtime-confirmed until R6 passes the current master test.

Next task: help run the current master acceptance against the current 21.1.247-built artifact. If it fails, inspect `/v10-acceptance.log` and Minecraft `latest.log`, identify the exact failing metric/subsystem, fix only the concrete issue, then rerun. If it reaches TARGET FULL PASS, update runtime results and proceed to final release/package/branch work.

Keep live-testing replies direct. Prefer direct downloadable artifacts. Do not reintroduce phased testing, speculative future-proofing, automatic late radio membership, or broad redesign.
