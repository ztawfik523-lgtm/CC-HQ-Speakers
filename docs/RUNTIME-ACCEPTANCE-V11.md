# Runtime acceptance — protocol v11

Updated: 2026-10-01

This is the active runtime plan for the audited v11 candidate. The older v10 acceptance/results documents are historical regression evidence only.

## Candidate baseline

Candidate checkpoint: `cd9a3f67ac3449df975c00240d83ee8e33239fc9`  
Last production-code commit in that candidate: `11a22e9f122ebf511744ac0aa1b17512fd69b6eb`  
CI: `36783159001` — PASS  
Artifact: `11128792552` — `hqspeaker-neoforge-21.1.247`  
JAR SHA-256: `ce91a67e8c20d82bb7e1bcc82bee94a025edfec426cecede2035fdfda4337d76`  
Protocol: v11 / exactly 9 payloads.  
Build/test/package target: NeoForge 21.1.247 only.

Commits after the candidate checkpoint are documentation-only unless explicitly stated otherwise.

## Evidence already established

Historical v10 runtime evidence remains useful regression history:

- A1-A19 PASS;
- R1-R9 PASS;
- C1 Sable tracking PASS;
- C3 grouped radio/metadata/strict-membership PASS;
- C4 RAW PASS at eight speakers;
- corrected C4 finite catch-up result;
- A8/A9/A18 focused rejection PASS.

Focused v11 C2 is also PASS and does not need to be rerun.

C2 proved on one continuous finite source with SPR Update Moving Sounds OFF:

- same source UUID/group from open LOS through wall sample;
- channel starts stayed 1; detach/rejoin/EOF/playing->stopped stayed 0;
- real SPR `processSound` calls increased 3 -> 24;
- progressive calls increased 2 -> 23;
- full refreshes increased 1 -> 12;
- paths increased 26 -> 303;
- private EFX applies increased 2 -> 23;
- private EFX fallback remained 0;
- private direct filter remained stable;
- open direct gain/HF/raw occlusion = 1.0000 / 1.0000 / 0.0000;
- wall direct gain/HF/raw occlusion = 0.8533 / 0.3451 / 1.1250.

Later cleanup did not change the SPR scheduler, progressive occlusion, smoothing, reflection stabilization or private-EFX implementation.

## Remaining runtime acceptance

Use **one exact candidate JAR, one Minecraft launch, two integrated tests**. Do not restart Minecraft between Test 1 and Test 2 unless the game itself fails.

SPR visual/debug ray output is not required. Keep Sound Physics Remastered installed; keep SPR **Update Moving Sounds OFF** for Test 1.

### Test 1 — normal world / exactly two speakers

Tracked script: `scripts/v11_runtime_1.lua`

Run:

```
v11_runtime_1 /cchq-speaker-runtime-test-48k-mono.mp3
```

The script self-verifies:

- representative interpolation: `1.75 -> gain 0.585 / auto range 59`;
- explicit range override and return to automatic range;
- invalid/out-of-range/non-finite Lua rejection;
- endpoint-local volume/range/mute;
- pause/resume reaching the real renderer;
- live audio-config reload:
  - already-running source keeps its original profile;
  - newly-started source uses the reloaded profile;
  - the script requires the config to be restored before it can pass;
- simultaneous clear + occluded SPR sources:
  - both enter the real SPR path;
  - both use private EFX;
  - direct filter IDs are distinct;
  - the occluded source is measurably more muffled;
- F3+T:
  - server playback authority survives;
  - client channels are recreated;
  - finite renderer rejoins;
  - PCM resumes;
  - private filters are re-isolated;
- representative 70-80 block playback/occlusion with automatic range 96.

Manual actions are limited to what Minecraft cannot automate: one config edit/restore, clear-vs-wall positioning, F3+T once, and walking to the prepared 70-80 block point.

Success ends with:

```
[PASS] v11 runtime test 1
```

Required output: `/v11-runtime-1.log`.

### Test 2 — Sable/Aeronautics / exactly eight speakers

Tracked script: `scripts/v11_runtime_2.lua`

Run:

```
v11_runtime_2 /cchq-speaker-runtime-test-48k-mono.mp3
```

The default radio URL is `https://stream.nightride.fm/nightride.mp3`; an alternate MP3/ICY URL may be supplied as the second argument.

The script self-verifies:

- eight-speaker finite playback with v11 tuning/status fields;
- no old ~2.2-second finite admission-stall fingerprint;
- finite catch-up alignment;
- Sable movement on the same playback, including real client/OpenAL source movement;
- eight-speaker continuous multi-chunk RAW;
- RAW backpressure/continuation on one channel per endpoint;
- RAW drain and PCM-delivery evidence;
- grouped eight-speaker MP3/ICY radio;
- meaningful decoded PCM and settled synchronization;
- actual ICY metadata arrival;
- zero decoder failures.

The only required physical action is the prompted Sable move/rotation.

Success ends with:

```
[PASS] v11 runtime test 2
```

Required output: `/v11-runtime-2.log`.

## Logs to retain

After both tests, retain:

- `/v11-runtime-1.log`;
- `/v11-runtime-2.log`;
- Minecraft `latest.log`;
- Minecraft `debug.log`.

## Acceptance rule

Release acceptance is complete when both integrated tests pass on the exact candidate above.

Outside this target: dedicated multiplayer, direct VS2 runtime acceptance, separate NeoForge 21.1.248 build and >8 streamed-source guarantee.
