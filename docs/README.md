# Documentation index

Updated: 2026-09-19

## Start here

Current engineering state:

- M1E, M1F, and M1G are complete.
- Focused M1G audible/core Minecraft runtime acceptance passed on NeoForge 21.1.247.
- The selected post-M1G Option A hardening pass is complete.
- The next active milestone is **M1H — dynamic listener lifecycle/recovery**.

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

Current completed-hardening branch: `codex/post-m1g-hardening`

Final M1G source checkpoint: `fa679ffcb81a66fd99ab6be8e6d6b77895fbc542`

Final post-M1G hardening source checkpoint: `3d30ce4564de749f32171666df65de739b08ad77`

Latest full verification of that source tree:

- CI `35406595856`;
- NeoForge 21.1.247: PASS, artifact `10572925494`, SHA-256 `ad0d02119ad3a4a29de00220e195ad117214316b8739f8faca223ac4900f409c`;
- NeoForge 21.1.248: PASS, artifact `10573000385`, SHA-256 `aff2eaaccb56b04c12d25bc6774f27c52f45fae90212b0b5e771f90828e6012e`.

## Current authority order

1. `HANDOFF-2026-09-19-M1H-START.md`
2. `CURRENT-STATE.md`
3. `KNOWN-ISSUES.md`
4. `TESTING.md`
5. `VERIFIED-FACTS.md`
6. `ARCHITECTURE.md`
7. `ROADMAP.md`
8. `LUA-API.md`
9. `CC-T-COMPATIBILITY-CONTRACT.md`, `SERVER-CONFIG.md`, `SOURCES.md`
10. exact current source and CI

For a fresh ChatGPT conversation, use `NEXT-CHAT-PROMPT.md`.

`NEXT-CHAT-HANDOFF.md` and `CHAT-HANDOFF.md` are current pointers to the dated handoff.

## Completed modern finite stack

```text
ComputerCraft file
-> temporary staging import
-> immutable server MediaAsset
-> server-authoritative finite timeline
-> protocol v7 STATE decodeRevision + codec-aware anchor
-> bounded client range requests / off-thread server reads
-> bounded sliding encoded RAM
-> progressive JLayer MP3 or common-WAV decode
-> bounded mono S16 PCM at source rate
-> FinitePcmReadAdapter / FinitePcmAudioStream
-> one positional BLOCKS SoundManager source
```

Modern prepared support is MP3 + the documented supported common-WAV subset.

## Completed post-M1G hardening

KI-062, KI-063, KI-054, and KI-064 are closed.

- blocking stream DNS runs outside both the ownership monitor and command-order lock;
- newer playback/control commands supersede a normal stream still blocked in DNS;
- cleanup/lifecycle invalidation rejects stale stream admission;
- RAW/prepared replacement admits first, then stops the previous valid source;
- media-service shutdown begins early and failed cleanup remains retryable/root-lock safe;
- import zero-read no-progress is bounded;
- unsupported `ATOMIC_MOVE` has a same-root fallback before publication.

## M1H starts here

The first M1H gap is listener membership.

Current source sends BEGIN only to players relevant when playback starts. `HQFiniteMediaServer.tick()` only handles natural EOF; it does not discover newly relevant players. A late entrant therefore has no client session from which to send READY. Leaving range also does not proactively remove the client session.

Start M1H with:

1. admitted-listener tracking;
2. late entry into the fixed 32-block radius;
3. proactive leave cleanup;
4. return/rejoin at current canonical time;
5. disconnect/dimension pruning;
6. deterministic membership-transition tests.

Do not silently choose between proactive BEGIN+STATE and BEGIN followed by READY->STATE if that packet-sequence tradeoff materially changes the implementation.

Recovery/resource-reload/general-underrun work follows. Final moving-source/VS2 behavior is a separate M1H choice.

## Historical documents

Dated preparation, milestone, and handoff documents are historical evidence. Their internal “current” statements describe the checkpoint when written and do not override the authority order above.
