# Documentation index

Updated: 2026-09-20

## Current authority

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`  
Active branch: `codex/m1j-multispeaker`

Latest source checkpoint before documentation closeout:

- `00b07db41c003363cef60ef8ec4134fa387c421c`
- CI `35474944518` — PASS on NeoForge 21.1.247 and 21.1.248

Read current documents in this order:

1. `HANDOFF-2026-09-20-POST-CONVERGENCE.md`
2. `CURRENT-STATE.md`
3. `KNOWN-ISSUES.md`
4. `TESTING.md`
5. `VERIFIED-FACTS.md`
6. `ARCHITECTURE.md`
7. `ROADMAP.md`
8. `LUA-API.md`
9. `CC-T-COMPATIBILITY-CONTRACT.md`
10. `SERVER-CONFIG.md`
11. `SOURCES.md`
12. exact current source and CI

Fresh-chat prompt:

- `HANDOFF-PROMPT-2026-09-20.md`
- `NEXT-CHAT-PROMPT.md` points to the same current prompt.
- `NEXT-CHAT-HANDOFF.md` points to the current dated handoff.

## Current project state

M1E through M1J are complete at source/test/CI/package level.

The supported finite engine is now one modern implementation:

```text
ComputerCraft file / MP3-WAV compatibility bytes
-> immutable server MediaAsset
-> server-authoritative shared playback
-> protocol v9 bounded range transport
-> progressive MP3/common-WAV decode
-> shared client timeline
-> independent physical positional endpoints
```

RAW remains a separate producer-fed PCM path.

Optional live MP3/HLS/TS/ICY remains separate and is not a core release requirement.

The normal `computercraft:speaker` is the only block product. The inherited standalone HQ block is gone. Internal custom audio uses `hqspeaker:hq_audio_source`.

License is MPL-2.0.

## Historical documentation

Older dated handoffs, milestone design notes, M0/M1A-M1G reports and research files preserve what was known or selected at those times. Do not rewrite them to make them look current. When they conflict with the authority list above, the current authority and exact source win.
