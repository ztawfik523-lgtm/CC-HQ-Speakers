# Current state

Updated: 2026-09-27

Frozen product/source checkpoint: `c61b052beee03ec0f36fed725fb37483bfb57d83`  
API/docs freeze: `bb0d68c7031bf97c7c7efc10c6458992222cf394`  
Runtime-tested candidate checkpoint: `84bce876106345553155aa1dcab72a45c72f3360`  
Runtime-tested candidate CI: `36238699768` — PASS  
Runtime-tested artifact: `10905071824`  
Runtime-tested JAR SHA-256: `32e6f0956da581295819bd97c6b94c42d2689ca8071894baf4c88fbd5277d9d8`  
Current master-runner checkpoint: `7b8e4d4a095611bf89d05726c4824a95edcea3ba`  
Current master-runner CI: `36285305761` — PASS  
Build baseline: NeoForge **21.1.247 only**  
Protocol: **v10**, 9 payloads.

## Build/release policy

Build, test and package only against NeoForge 21.1.247. The artifact declares `[21.1,21.2)`; do not restore a duplicate 21.1.248 build.

The current runtime candidate already contains the previously proven fixes for native CC:T Sable-world positioning, OpenAL-safe diagnostics, correct R1 attribution and F3+T recovery classification.

## Product state

Only the normal `computercraft:speaker` is upgraded. The standalone HQ block is removed. Native CC:T behavior remains delegated to the real CC:T speaker peripheral.

Frozen playback surface:

- native CC:T note/sound/DFPWM;
- modern finite MP3 + supported common WAV;
- prepared immutable finite media;
- multispeaker finite shared authority with endpoint-local render/transport/gain/mute;
- signed-16 mono 48-kHz RAW;
- MP3/ICY radio singular/All/At with strict start-time membership.

HLS, MPEG-TS, OGG/generic whole-file aliases, duplicate finite engine and stale legacy controls remain removed.

All HQ positional paths resolve Sable Companion -> VS2 -> static block center.

## Runtime evidence now established

A1-A19 and R1-R9 have all passed in real Minecraft on the selected 21.1.247 candidate.

That confirms native CC:T client channels on Sable, finite MP3/WAV rendering, 2-speaker synchronization, endpoint-local controls, continuous RAW continuation, loop recovery, listener leave/rejoin and F3+T recovery.

Attempt 7 then ran all current target checks:

- **C1 Sable:** the runner marked FAIL, but the actual tracking evidence is good. Requested and actual OpenAL movement matched over roughly 52-53 blocks and both sources ended PLAYING with zero decoder failures. The failure came from an unrelated historical STOPPED transition produced during listener relevance leave/rejoin. This is a harness assertion bug;
- **C2 SPR:** unresolved. The diagnostic saw SPR environment activity, but open-air and wall direct gain/HF remained 1.0000. The current hook does not prove that `SoundPhysics.processSound` actually ran, so the cause is not established;
- **C3 radio:** PASS, including sustained grouped playback, metadata, strict late membership, rerun membership, singular and indexed paths;
- **C4 8+ finite:** real failure. One of eight client sources started about 2.219 seconds late;
- **C4 8+ RAW:** not reached and remains untested.

## Concrete C4 finite defect

The current finite transport permits 2 in-flight requests per client source, while the server admits only 4 outstanding range requests per player.

With 8 endpoints, the client can attempt up to 16 requests at once. Server `OVER_LIMIT` admission currently produces no response. The client therefore retains that request as pending until its 2-second request expiry, then retries.

Attempt 7's delayed source started around 2334 ms while the other seven started around 115-145 ms, closely matching that contract. This is the current finite scale blocker.

Do not misclassify this specific attempt as an OpenAL source-pool failure. A separate >8 streamed-source capacity question still exists and must be measured after the range bug is fixed.

## SPR state

Current built-in SPR diagnostics observe `setEnvironment` values. That is no longer enough for final acceptance because it cannot distinguish a full world/ray `processSound` evaluation from another environment-write path.

Before changing playback architecture, add diagnostics that record actual SPR `processSound` invocation/results and explicitly test whether the obstruction is in the normal client world or inside Sable sub-level geometry.

The older `ztawfik523-lgtm/cchq-soundphysics-compat` project is a valuable architecture/reference implementation, not a drop-in dependency for today's protocol. Its architecture will be re-evaluated rather than copied blindly.

## Next work, in order

1. fix C1 so the Sable movement scenario judges movement/tracking only;
2. fix the finite range admission/retry contract exposed by C4;
3. split/sequence scale evidence so an 8+ finite failure cannot prevent 8+ RAW evidence from running;
4. add direct SPR `processSound` diagnostics and isolate static-world vs Sable-world geometry;
5. re-evaluate SPR integration architecture using the old compat project plus the freedom of the current fork;
6. rerun only the affected target checks, then complete >8 source-capacity testing if the product target truly includes more than eight simultaneous speakers.

Do not declare release acceptance complete until those items are resolved.
