# Ready-to-paste prompt — frozen v10 runtime phase

Continue the CC:HQ Speakers project in `ztawfik523-lgtm/CC-HQ-Speakers`, branch `codex/m1j-multispeaker`.

The source/API cleanup phase is frozen. Read exact current GitHub source/CI, then read:

1. `docs/API-FREEZE-V10.md`
2. `docs/CURRENT-STATE.md`
3. `docs/KNOWN-ISSUES.md`
4. `docs/RUNTIME-ACCEPTANCE-V10.md`
5. `docs/RUNTIME-RESULTS-V10.md`
6. `docs/TESTING.md`

Frozen source checkpoint: `c61b052beee03ec0f36fed725fb37483bfb57d83`. Protocol v10.

Do not add/remove public methods or redesign semantics unless runtime testing proves a release-blocking bug.

Important contracts:

- only normal `computercraft:speaker`;
- MP3/common-WAV finite engine;
- finite multispeaker shared authority;
- `audioStopAt(index)` stops only that endpoint;
- RAW signed-16 mono 48 kHz, max 131072 samples/call;
- MP3/ICY radio direct/At/All;
- grouped radio strict snapshot with no auto late membership; rerun to add speakers;
- HLS/TS removed;
- all HQ paths use Sable -> VS2 -> static movement;
- `isStreaming` is server-side request state, not confirmed client audibility.

Next work is runtime acceptance only. Use the prepared scripts and record exact evidence. If a test fails, diagnose against the frozen contract before changing architecture.
