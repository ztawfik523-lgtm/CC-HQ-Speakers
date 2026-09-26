# Roadmap

Updated: 2026-09-26

The v10 product/API semantics are frozen. Built-in diagnostic instrumentation and the automatic master acceptance runner are implemented. Core architecture is not the active workstream.

Current diagnostic/runtime-test checkpoint: `37755ccdb34ac72a27797dc2e6581463e85cbcd7`  
CI: `36205257110` — PASS on NeoForge 21.1.247.

## Completed

- native CC:T delegation and grouped/indexed native dispatch;
- modern finite MP3/common-WAV engine;
- immutable prepared server media assets;
- bounded finite range transport and progressive decode;
- finite listener/recovery source work;
- multispeaker shared playback authority;
- stable multi-endpoint command coordination;
- RAW signed-16 48-kHz path with bounded backpressure;
- producer-fed RAW OpenAL continuation fix;
- MP3/ICY radio singular/All/At with strict snapshot grouping;
- HLS/TS removal;
- protocol v10 / 9 payloads;
- standalone HQ block removal;
- dependency/API/dead-code cleanup;
- unified Sable -> VS2 -> static movement resolution;
- built-in dormant client/OpenAL diagnostics;
- automatic diagnostic master runner;
- Cobalt syntax compilation of the shipped master Lua script;
- single NeoForge 21.1.247 build/release policy.

## Active work — one runtime acceptance

Run `scripts/v10_acceptance.lua` against the current 21.1.247-built JAR.

The selected target scope is:

1. deterministic/API/control/bounds/security matrix;
2. native CC:T real client channels;
3. finite MP3/WAV renderer behavior and shared multispeaker timing;
4. RAW continuation/backpressure/client delivery;
5. range rejoin and F3+T recovery;
6. Sable/Aeronautics tracking;
7. Sound Physics Remastered processing;
8. MP3/ICY grouped radio and strict late membership if a direct URL is supplied;
9. 8+ speaker stress;
10. dimension leave/rejoin when the source can remain loaded.

Dedicated-server/multiplayer and VS2 are outside the chosen release acceptance target. A second NeoForge 21.1.248 build is also not part of the plan.

## After runtime PASS

If the target-scope master test passes:

- record the final runtime evidence and JAR hash;
- reconcile any bug-fix documentation;
- final package/dependency verification;
- decide release version/changelog;
- promote/merge the product branch to the intended default branch;
- cut the release artifact.

If a test fails, isolate and fix only the concrete failure, then rerun the necessary master section/full master as appropriate.

## Deferred feature bucket

Not part of this release: OGG, FLAC, HLS, MPEG-TS, provider playback, shared finite decode fan-out, gapless playback, standalone HQ block, or application-level playlist/radio management.
