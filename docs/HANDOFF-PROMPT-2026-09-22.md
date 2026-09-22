# Ready-to-paste prompt — CC:HQ Speakers frozen-v10 runtime phase

Continue the **CC:HQ Speakers** project in GitHub repository `ztawfik523-lgtm/CC-HQ-Speakers`, active branch `codex/m1j-multispeaker`.

This chat follows a completed cleanup/API-freeze pass. **Do not restart broad cleanup, rethink, or source-audit loops.** First fetch the current branch head and latest CI once. Then read the current authority and begin runtime acceptance unless you find a concrete inconsistency.

Exact checkpoints before the handoff-doc commit:

- frozen source: `c61b052beee03ec0f36fed725fb37483bfb57d83`
- source-freeze CI: `35655973164` — NeoForge 21.1.247 + 21.1.248 PASS
- API/docs freeze: `bb0d68c7031bf97c7c7efc10c6458992222cf394`
- runtime-prep checkpoint: `a234ba02b80532daf32f6849061b76f23c0eb4d3`
- runtime-prep CI: `35657182389` — both targets PASS
- protocol: **v10**, 9 payloads

Read, in order:

1. `docs/HANDOFF-2026-09-22-RUNTIME.md`
2. `docs/API-FREEZE-V10.md`
3. `docs/CURRENT-STATE.md`
4. `docs/KNOWN-ISSUES.md`
5. `docs/RUNTIME-ACCEPTANCE-V10.md`
6. `docs/RUNTIME-RESULTS-V10.md`
7. `docs/TESTING.md`
8. `docs/VERIFIED-FACTS.md`
9. exact current source only when needed to diagnose a concrete test result

The public v10 API/semantics are frozen. Do not add/remove methods or redesign behavior unless runtime testing proves a release-blocking bug. Preserve:

- only normal `computercraft:speaker`;
- native CC:T speaker behavior delegated to the real CC:T `SpeakerPeripheral`;
- finite MP3 + supported common WAV;
- shared finite multispeaker authority with start-time endpoint snapshot;
- shared pause/resume/seek/loop and ordinary/All stop;
- endpoint-local volume/mute;
- `audioStopAt(index)` stops only the selected endpoint;
- RAW signed-16 mono 48 kHz, max 131072 samples/call, bounded backpressure;
- MP3/ICY radio `speakStream` / All / At;
- grouped radio strict snapshot with no automatic late membership; rerun the command to add a speaker;
- one shared decoder/prebuffer per client-local grouped radio session;
- HLS/TS removed;
- all HQ source positioning uses Sable -> VS2 -> static;
- `isStreaming` is server-side request/ownership state, not confirmed client audibility;
- protocol v10.

The cleanup pass already fixed protocol registration, server-thread commits, radio cancellation/lifecycle, double radio gain, URL filtering, stale legacy controls/helpers, read-only routing, and movement consistency. Do not repeat those audits without new evidence.

**Next task is runtime acceptance.** Start with Phase 0 in `docs/RUNTIME-ACCEPTANCE-V10.md` on NeoForge 21.1.247 using the prepared scripts. Record results in `docs/RUNTIME-RESULTS-V10.md`. If something fails, diagnose that exact failure against the frozen contract before changing source. Continue through multispeaker, listener/recovery, Sable/VS2 movement, RAW, radio, malformed/bounds, Sound Physics Remastered, performance, then 21.1.248 confirmation.

Keep source/CI evidence separate from Minecraft runtime proof. For performance issues, ask for/use a Spark profile rather than guessing.

When there are meaningful design tradeoffs caused by a proven runtime defect, explain the practical options and let me choose. Do not introduce speculative features or redesigns.
