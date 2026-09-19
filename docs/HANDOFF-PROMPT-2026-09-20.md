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

Latest source checkpoint before documentation closeout: `00b07db41c003363cef60ef8ec4134fa387c421c`.

CI `35474944518` passed NeoForge 21.1.247 and 21.1.248 with tests/package verification/artifacts.

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
- play/pause/resume/seek/loop/stop are shared;
- volume/mute are endpoint-local; All applies to the surviving playback snapshot;
- RAW is separate signed-16 48-kHz producer-fed PCM with bounded backpressure;
- RAW All preflights the group and has no expected-member barrier;
- optional live MP3/HLS/TS/ICY remains separate;
- grouped optional live streams still use legacy sync expected-count machinery, so that code is not globally dead;
- JLayer and Sable Companion remain embedded; mp3spi/Tritonus are removed;
- M1H/M1J focused Minecraft runtime checks are deferred, not rejected.

Important unresolved source/runtime risk: `HQFiniteMediaServer.isRelevant(...)` still requires `player.level() == level` after projecting moving speaker coordinates. A Sable sublevel speaker may fail listener membership for a parent-world player even if projected geometry is correct. Do not claim this is resolved without testing/fixing it.

Likely next non-runtime task is dead standard All/At cleanup, but recheck first:

- `HQSpeakerCompositePeripheral` already intercepts grouped/indexed standard note/sound/audio and dispatches to real CC:T speakers;
- old wrong bodies remain in `HQSpeakerPeripheral`;
- standalone direct-peripheral block path is gone;
- verify exact references; if truly unreachable, remove them and helpers which become unreferenced;
- do NOT delete `SyncDispatch` / packet sync fields / client `SyncGroupState` wholesale because grouped optional live helpers still use them.

After that, continue RAW/public API release cleanup and exact dead-code/dependency/docs/repository hygiene.

Do not start FLAC/OGG/provider playback/shared decode fan-out unless a fresh decision justifies it.

Do not insist on a runtime test immediately unless necessary for the current source decision. The owner previously deferred runtime testing during source work; keep the backlog accurate and batch integrated testing later.

For meaningful design tradeoffs, explain practical options and let me choose. Do not overcomplicate hypothetical edge cases.

Before calling a source slice complete:

- run both NeoForge 21.1.247 and 21.1.248 CI;
- distinguish CI/package evidence from Minecraft runtime proof;
- adversarially reread ownership/lifecycle/concurrency paths;
- update current-authority docs if behavior changed.
