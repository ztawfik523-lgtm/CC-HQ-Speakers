# CC:HQ Speakers — runtime-ready handoff

Updated: 2026-09-21

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`  
Branch: `codex/m1j-multispeaker`  
Frozen source: `c61b052beee03ec0f36fed725fb37483bfb57d83`  
API/docs freeze: `bb0d68c7031bf97c7c7efc10c6458992222cf394`  
Protocol: v10.

Start with:

1. `API-FREEZE-V10.md`
2. `CURRENT-STATE.md`
3. `KNOWN-ISSUES.md`
4. `RUNTIME-ACCEPTANCE-V10.md`
5. `RUNTIME-RESULTS-V10.md`

The public v10 surface is frozen. Do not redesign it during runtime testing unless a release-blocking defect is proven.

Key contracts:

- normal CC:T speaker only;
- finite MP3/common WAV;
- finite shared multispeaker authority;
- `audioStopAt` is endpoint-local;
- RAW signed-16 mono 48 kHz, max 131072 samples/call;
- MP3/ICY radio singular/All/At;
- grouped radio is strict snapshot/no automatic membership;
- HLS/TS are removed;
- all HQ positional paths use Sable -> VS2 -> static;
- `isStreaming` is server-side ownership/request state, not client-connect proof.

Run the runtime matrix exactly as documented, distinguish automated/API PASS from audible/manual PASS, and record failures with exact NeoForge version, setup, logs and profiles.
