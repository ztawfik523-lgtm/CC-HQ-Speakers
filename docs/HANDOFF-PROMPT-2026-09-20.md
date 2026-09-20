# Ready-to-paste handoff prompt — 2026-09-20

Paste the text below into a fresh chat in this project.

---

We are continuing the CC:HQ Speakers project in repository `ztawfik523-lgtm/CC-HQ-Speakers`.

Read the project context, then inspect the **current GitHub branch/source/CI** before changing anything. Do not rely only on old chat summaries.

Start with:

1. `docs/HANDOFF-2026-09-20-POST-CONVERGENCE.md`
2. `docs/CURRENT-STATE.md`
3. `docs/KNOWN-ISSUES.md`
4. `docs/TESTING.md`
5. `docs/VERIFIED-FACTS.md`
6. `docs/ARCHITECTURE.md`
7. `docs/ROADMAP.md`
8. `docs/LUA-API.md`
9. exact current source and latest CI

Active branch at handoff: `codex/m1j-multispeaker`.

Current source checkpoint: `b3005f8b33525c237df53f08bb5b09c6b96809fc`.

CI `35529710352` passed NeoForge 21.1.247 and 21.1.248 with deterministic tests, packaged-mod verification, and artifact upload.

Current network protocol is v9 with 9 payloads.

Preserve these core facts:

- product only upgrades normal `computercraft:speaker`;
- standalone `hqspeaker:hq_speaker` block is removed;
- internal custom audio uses `hqspeaker:hq_audio_source`;
- license is MPL-2.0;
- modern finite supports MP3 + common supported WAV;
- `speakMp3/speakWav` singular/All/At are modern-engine compatibility frontends;
- OGG/generic whole-file aliases and duplicate finite engine are removed;
- modern multispeaker uses one shared authority with independent physical endpoints and no expected-global-member barrier;
- play/pause/resume/seek/loop and ordinary/All stop are shared; `audioStopAt(index)` intentionally detaches/stops only that selected endpoint;
- volume/mute are endpoint-local; All applies to the surviving playback snapshot;
- RAW is separate signed-16 48-kHz producer-fed PCM with bounded backpressure;
- RAW All preflights the group and has no expected-member barrier;
- optional live MP3/HLS/TS/ICY remains separate;
- grouped optional live streams still use legacy sync expected-count machinery, so that code is not globally dead;
- JLayer and Sable Companion remain embedded; mp3spi/Tritonus are removed;
- M1H/M1J focused Minecraft runtime checks are deferred, not rejected.

The earlier Sable parent-world Level-identity concern has been closed by source-model verification: Sable sub-level contents live in plots owned by the parent Minecraft `Level`, so `player.level() == level` is the correct same-dimension guard after position projection. Focused Sable/Aeronautics movement runtime testing is still deferred.

Dead grouped/indexed implementation cleanup is now complete:

- `ef2a917de429c34409ae7866f59cb50f3c191aa1` removed the obsolete standard `playNote/playSound/playAudio` All/At bodies after explicitly preserving those names in the composite;
- `395a41c1c91a4d1efa41cee9db89ba02fe767785` removed the obsolete legacy `speakPCMAll/speakPCMAt` bodies and their now-unreferenced helper wrappers;
- the composite remains the public standard/RAW All/At authority;
- `SyncDispatch` / packet sync fields / client `SyncGroupState` remain because grouped optional live helpers still use them.

Core modern/multispeaker concurrency hardening is complete in source through `68314efe2e7ccbaa73e273044389ea43fea70530`: relevant target speakers are reserved together in one stable order for finite/RAW group replacement and modern/core targeted controls, delayed older stream starts are invalidated on affected targets, and shared playback identity is rechecked after reservation.

`audioStopAt(index)` is resolved as endpoint-local detach/stop; do not change it back to a shared stop.

Dead singular legacy fake standard playback and the shadowed legacy `speakPCM` body were removed at `2377aae3bde94d3393f21525697198fb8645f354`; the composite explicitly preserves the supported public method names and RAW max is 131072 throughout.

The obsolete undocumented aliases `speakStopAll`, `speakStopAt`, `speakVolumeAll`, and `setLoopingAll` were removed at `fe880002b387f329d39a7372af521f1eacce559a`; do not re-add them for speculative script compatibility.

Continue exact dead-code/dependency/docs/repository hygiene. Optional grouped live remains the next meaningful scope decision because it still carries legacy ownership/sync/HLS/TS debt.

Do not start FLAC/OGG/provider playback/shared decode fan-out unless a fresh decision justifies it.

Do not insist on a runtime test immediately unless necessary for the current source decision. The owner previously deferred runtime testing during source work; keep the backlog accurate and batch integrated testing later.

For meaningful design tradeoffs, explain practical options and let me choose. Do not overcomplicate hypothetical edge cases.

Before calling a source slice complete:

- run both NeoForge 21.1.247 and 21.1.248 CI;
- distinguish CI/package evidence from Minecraft runtime proof;
- adversarially reread ownership/lifecycle/concurrency paths;
- update current-authority docs if behavior changed.
