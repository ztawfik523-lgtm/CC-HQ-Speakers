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
Current source/recheck checkpoint: `87d08d3a62857dfeba16756591560eaa0e62d193`  
Current source/recheck CI: `36293523518` — PASS  
Current source/recheck artifact: `10922887347` (`hqspeaker-neoforge-21.1.247`)  
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

Attempt 7 proved that the old client/server admission composition was wrong: 8 endpoints could attempt up to 16 range requests while the server admitted only 4 per player, and silently dropped over-limit requests waited for the 2-second loss timeout.

A source fix is now implemented on the branch:

- all local finite endpoints share the same `MAX_OUTSTANDING_REQUESTS_PER_PLAYER` budget the server enforces;
- the client still allows up to 2 requests per source when there is spare capacity;
- shared slots are issued fairly, preferring endpoints which have issued fewer requests, so early endpoints cannot monopolize the player budget;
- protocol v10 and the 9-payload shape are unchanged;
- the 2-second timeout remains only as loss/recovery protection, not the normal admission mechanism.

This is the smallest fix for the demonstrated bug. It is **CI-built but not yet runtime-proven**; C4 must confirm the ~2.2-second outlier is gone.

## SPR state

The current branch now proves actual SPR processing instead of inferring it from `setEnvironment` alone.

Implemented diagnostics:

- an optional client-only mixin observes the exact SPR 1.21.1-1.5.1 `processSound` overload;
- process observations are scoped specifically to `hqspeaker:hq_audio_source`, so unrelated Minecraft/mod sounds and recycled OpenAL source ids cannot create false HQ evidence;
- diagnostics record process-call count, source position, category, sound id, optional reflected position and the resulting environment writes;
- finite, radio and RAW target checks now require direct SPR process evidence.

C2 has also been changed to use **ordinary Minecraft-world geometry only**. It first measures open air, then keeps the same HQ sound running while the listener moves behind a normal-world wall, and finally restarts behind that wall. This distinguishes startup processing from stale long-running acoustics.

No acoustic refresh system has been added yet. If the live-wall phase proves stale while the restarted wall works, the next product change will be a small **client-only HQ refresh path**. The server and network protocol remain completely SPR-independent.

Sable-wall acoustics are explicitly deferred from this phase.

## Next work, in order

The cumulative NeoForge 21.1.247 source/recheck build is green. The next step is runtime evidence, not more source changes.

1. install artifact `10922887347` from CI run `36293523518` and rerun the target checks using the existing master log with `--resume`;
2. C1 must record a clean PASS with the corrected final-health/movement assertion;
3. C2 must prove direct SPR processing behind a **normal-world** wall and report whether the same long-running source refreshes before restart;
4. C3 is intentionally renamed so `--resume` reruns radio and proves the radio path also enters SPR;
5. C4 must run both finite and RAW independently; finite validates the new shared range scheduler and RAW finally gets its missing 8+ evidence;
6. only if C2 proves long-running HQ acoustics stale, add the smallest client-only HQ refresh behavior and rerun C2;
7. only after exactly-8 scale is clean should >8 Minecraft streaming-channel capacity be tested separately;
8. Sable-wall acoustics remain deferred until ordinary SPR integration is complete.

Do not declare release acceptance complete until those items are resolved.
