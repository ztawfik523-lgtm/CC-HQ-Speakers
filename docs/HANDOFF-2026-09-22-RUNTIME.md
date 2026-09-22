# CC:HQ Speakers — new-chat runtime handoff

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

## Runtime package already prepared

Use these current scripts:

- `scripts/p0_cc_speaker_contract.lua`
- `scripts/p0_finite_regression.lua <mp3>`
- `scripts/v10_core_acceptance.lua <mp3> [wav]`
- `scripts/v10_raw_acceptance.lua`
- `scripts/v10_multispeaker_stress.lua <mp3> [cycles]`
- `scripts/v10_radio_acceptance.lua [radio-url]`
- `scripts/v10_runtime_observer.lua [seconds]`

The full ordered procedure and pass criteria are in `docs/RUNTIME-ACCEPTANCE-V10.md`. Record evidence in `docs/RUNTIME-RESULTS-V10.md`.

## Runtime order

Start with NeoForge 21.1.247:

1. Phase 0 native/API/finite/RAW preflight.
2. 2/4/8+ finite multispeaker stress and endpoint-local controls.
3. listener enter/leave/rejoin + reload/recovery.
4. Sable/Aeronautics and VS2 movement for finite, RAW and radio.
5. RAW audibility/backpressure/group start.
6. direct + grouped MP3/ICY radio, strict late membership, long-run drift, metadata, bad URL/EOF/network loss.
7. malformed/extreme media, storage/range/worker bounds and repeated replacement stress.
8. Sound Physics Remastered.
9. realistic performance; use Spark when performance is questionable.
10. repeat required smoke/full confirmation on NeoForge 21.1.248 as described in the runtime plan.

If Phase 0 fails, stop and diagnose that concrete failure before broader runtime work.

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
