# Current state

Updated: 2026-09-21

Frozen source checkpoint: `c61b052beee03ec0f36fed725fb37483bfb57d83`  
CI: `35655973164` — both supported NeoForge targets PASS.  
Protocol: **v10**, 9 payloads.

## Product

Only the normal `computercraft:speaker` is upgraded. The standalone HQ block is gone. Internal custom audio uses `hqspeaker:hq_audio_source`. License is MPL-2.0.

## Implemented

Modern finite MP3/common-WAV playback is server-authoritative, uses immutable prepared assets, bounded encoded range transport, progressive client decode, one shared multispeaker authority, dynamic finite listener membership/recovery, endpoint-local gain/mute, and start-time endpoint snapshots.

RAW is separate signed-16 mono 48-kHz producer-fed PCM with max 131072 samples/call and bounded backpressure.

MP3/ICY radio is supported in singular/All/At form. Grouped radio uses strict snapshot membership and one shared client decoder/prebuffer. Late/new speakers do not auto-join.

All HQ positional output paths now share the Sable -> VS2 -> static movement resolver.

## Recent pre-freeze hardening

The final cleanup/rethink pass:

- moved radio and finite world/network commits onto the server thread;
- made blocking radio startup cancellable and kept DNS outside sensitive locks;
- hardened radio URL policy and client/server revalidation;
- retired drained client radio state;
- removed HLS/TS and expected-member radio barriers;
- fixed v10 network registration;
- removed dead legacy control/helper bodies;
- fixed radio gain being applied twice;
- made speaker discovery reads non-mutating;
- unified RAW/radio movement with finite movement;
- completed a 67-class mechanical stale-reference/import/TODO sweep.

## Evidence boundary

Source/test/CI/package work is frozen. Focused Minecraft proof is still required for listener/recovery/movement, 2/4/8+ multispeaker behavior, strict radio synchronization, RAW timing/backpressure, malformed/extreme media, Sound Physics Remastered and realistic performance.
