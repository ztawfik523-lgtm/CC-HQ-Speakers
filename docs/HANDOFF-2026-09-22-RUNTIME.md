# CC:HQ Speakers — new-chat runtime handoff

> **Historical:** Superseded by `HANDOFF-2026-09-26-DIAGNOSTIC-RUNTIME.md` and `HANDOFF-PROMPT-2026-09-26.md`. Keep this file only as checkpoint history.

Updated: 2026-09-22

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`  
Active branch: `codex/m1j-multispeaker`

## Exact checkpoints

- frozen source/API implementation: `c61b052beee03ec0f36fed725fb37483bfb57d83`
- source-freeze CI: `35655973164` — NeoForge 21.1.247 + 21.1.248 PASS
- API/docs freeze: `bb0d68c7031bf97c7c7efc10c6458992222cf394`
- runtime-prep checkpoint: `a234ba02b80532daf32f6849061b76f23c0eb4d3`
- runtime-prep CI: `35657182389` — both NeoForge targets PASS
- network protocol: **v10**, 9 payloads

The handoff/docs commit after `a234ba02...` is documentation-only. In a new chat, fetch the current branch head and latest CI once before work.

## Start here

Read in this order:

1. `docs/API-FREEZE-V10.md`
2. `docs/CURRENT-STATE.md`
3. `docs/KNOWN-ISSUES.md`
4. `docs/RUNTIME-ACCEPTANCE-V10.md`
5. `docs/RUNTIME-RESULTS-V10.md`
6. `docs/TESTING.md`
7. `docs/VERIFIED-FACTS.md`
8. `docs/ARCHITECTURE.md`
9. `docs/LUA-API.md`
10. exact current source/CI only as needed

Do **not** restart a broad cleanup/rethink cycle. That phase is closed. Verify current head/CI once, then move to runtime acceptance. Only return to source if a runtime test, build failure, security issue, or concrete inconsistency proves a bug.

## Frozen product contract

The normal `computercraft:speaker` is the only speaker block product. The standalone HQ block is removed. Internal custom audio uses `hqspeaker:hq_audio_source`. License is MPL-2.0.

Native CC:T:

- real CC:T `playNote`, `playSound`, `playAudio`, `stop`;
- native `speaker_audio_empty` remains CC:T-owned;
- All/At helpers select endpoints but still call real CC:T speakers.

Modern finite:

- MP3 + supported common WAV only;
- prepared immutable server assets and compatibility byte frontends share one modern engine;
- multispeaker uses one shared playback authority with independent physical endpoints;
- membership is a start-time endpoint snapshot;
- pause/resume/seek/loop and ordinary/All stop are shared;
- volume/mute are endpoint-local;
- `audioStopAt(index)` intentionally stops/detaches only that endpoint;
- no expected-global-member barrier.

RAW:

- signed-16 mono PCM, 48 kHz;
- max 131072 samples/call;
- bounded queue/backpressure;
- `hqspeaker_audio_empty` only after observed rejection;
- singular/All/At;
- All preflights the complete target snapshot and uses a common future start tick.

MP3/ICY radio:

- supported methods: `speakStream`, `speakStreamAll`, `speakStreamAt`;
- HLS and MPEG-TS are removed;
- grouped radio is strict snapshot/no automatic membership;
- each client accepts only members received before the seal deadline;
- grouped radio uses one shared decoder/prebuffer per client-local group;
- late/new speakers stay out until the stream command is rerun;
- radio gain is applied once at the Minecraft sound source;
- `isStreaming()` reports server-side requested/owned stream state, not proof every client is hearing the URL;
- URL policy is HTTP/HTTPS only, bounded, no userinfo, restricted ports, private/local/reserved targets blocked, redirects disabled.

Movement:

- all HQ positional paths use one resolver;
- order: Sable Companion -> VS2 -> static block center;
- this includes finite, RAW and radio;
- no continuous server position stream.

Protocol/package:

- protocol v10;
- 9 payloads;
- JLayer 1.0.1.4 embedded;
- Sable Companion 1.6.0 embedded;
- mp3spi/Tritonus removed.

Retired and should remain absent unless a fresh explicit product decision is made:

- OGG/generic whole-file aliases;
- HLS/TS;
- old expected-count stream barrier;
- stale legacy control aliases;
- duplicate finite engine;
- standalone HQ speaker block;
- shared finite decode fan-out;
- FLAC/provider/gapless/new playlist policy.

## Final pre-freeze hardening already completed

The last source pass fixed or closed:

- protocol-v10 handshake mismatch;
- drained/failed radio client-state cleanup;
- radio URL/lifecycle hardening;
- cancellable radio connection startup;
- radio and finite world/network commits on the server thread;
- dead legacy audio-control bodies/helpers;
- double-applied radio gain;
- read-only discovery accidentally superseding in-flight radio admission;
- stricter reserved-address filtering;
- movement inconsistency between finite and RAW/radio;
- source-wide stale v9/HLS/TS/expected-count references;
- obvious unused imports/TODO/FIXME/dead helper residue.

Do not redo these audits without new evidence.

## Current runtime package

Build/test/package only against **NeoForge 21.1.247**. The resulting JAR declares the NeoForge range `[21.1,21.2)` and is the one release artifact used across the supported 21.1.x line. Do not add a duplicate 21.1.248 CI job or a second runtime pass just because the loader patch version differs.

Use the single user-facing runner:

- `scripts/v10_acceptance.lua <mp3> <wav> [direct-mp3-or-icy-url]`

The normal release JAR contains dormant built-in diagnostics. The runner enables them only during acceptance and judges real client/OpenAL behavior automatically. Smaller scripts are developer isolation tools only if the master run identifies a concrete failure.

Chosen runtime scope:

- singleplayer;
- Sable/Aeronautics;
- Sound Physics Remastered;
- native CC:T, finite, RAW and MP3/ICY radio;
- 2-speaker core plus 8+ speaker scale stress;
- range, dimension and F3+T recovery.

Dedicated-server/multiplayer and VS2 are intentionally outside this acceptance scope.

The person running the test only performs physical actions Minecraft cannot perform itself: walk out of range, change dimension, press F3+T, move/rotate the Sable contraption, move behind the prepared wall, connect a late radio speaker and connect enough speakers to reach 8+. The diagnostics determine PASS/FAIL.

## Evidence boundary

CI is green but does **not** prove audibility, OpenAL/SoundManager lifecycle, real spatial sync, Sable/VS2 movement, SPR behavior or realistic performance.

Do not mark runtime acceptance complete from CI.

## Release work after runtime

If runtime acceptance passes:

- reconcile any bug-fix docs;
- final package/dependency verification;
- decide release version/changelog;
- promote/merge the real product branch to the intended default branch;
- cut release artifacts.

GitHub `main` is still historical and should not be treated as current product source until explicitly promoted.
