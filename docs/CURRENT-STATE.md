# Current state

Updated: 2026-09-22

Frozen source checkpoint: `c61b052beee03ec0f36fed725fb37483bfb57d83`  
Source-freeze CI: `35655973164`.  
API/docs freeze: `bb0d68c7031bf97c7c7efc10c6458992222cf394`.  
Runtime-prep branch head: `a234ba02b80532daf32f6849061b76f23c0eb4d3` / CI `35657182389`.  
Protocol: **v10**, 9 payloads.

Historical dual-version CI passed both 21.1.247 and 21.1.248. Current release policy builds one artifact against NeoForge 21.1.247 and uses it across the supported `[21.1,21.2)` metadata range.

## Product

Only the normal `computercraft:speaker` is upgraded. Standalone HQ block is removed. Internal custom audio uses `hqspeaker:hq_audio_source`. License is MPL-2.0.

Frozen supported paths:

- native CC:T speaker behavior;
- modern finite MP3 + supported common WAV;
- modern finite multispeaker shared authority;
- signed-16 mono 48-kHz RAW;
- MP3/ICY radio singular/All/At with strict no-auto-membership grouping.

HLS, MPEG-TS, OGG/generic whole-file aliases, duplicate finite engine and stale legacy control aliases are removed.

All HQ positional paths use Sable Companion -> VS2 -> static block-center resolution.

## Final pre-freeze hardening

The cleanup/rethink pass closed:

- radio/finite server-thread commit affinity;
- cancellable radio startup and drained client state;
- v10 handshake registration;
- HLS/TS and expected-count radio removal;
- double-applied radio volume;
- non-mutating discovery reads;
- stricter radio URL filtering;
- dead legacy control/helper bodies;
- movement inconsistency between finite and RAW/radio;
- source-wide stale-reference/import/TODO sweep.

## Runtime readiness

The source/API is frozen. Runtime scripts and the ordered acceptance matrix are prepared and committed at `a234ba02b80532daf32f6849061b76f23c0eb4d3`. Use `RUNTIME-ACCEPTANCE-V10.md` and record results in `RUNTIME-RESULTS-V10.md`.

Runtime testing has **not** been declared complete. Remaining evidence is Minecraft-only: listener/recovery, moving Sable/VS2 sources, real 2/4/8+ synchronization and stress, RAW audibility/backpressure timing, strict radio synchronization/late membership, malformed/bounds testing, Sound Physics Remastered and realistic performance.
