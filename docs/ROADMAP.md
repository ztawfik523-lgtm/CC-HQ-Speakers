# Roadmap

Updated: 2026-09-21

The v10 public surface is frozen at `c61b052beee03ec0f36fed725fb37483bfb57d83`. Core source architecture is not the active workstream anymore.

## Completed

- native CC:T delegation and grouped/indexed native dispatch;
- modern finite MP3/common-WAV engine;
- prepared immutable server media assets;
- bounded range transport and progressive decode;
- finite listener/recovery/movement source work;
- multispeaker shared playback authority;
- stable multi-endpoint command coordination;
- RAW signed-16 48-kHz path with bounded backpressure;
- MP3/ICY radio singular/All/At with strict snapshot grouping;
- HLS/TS removal;
- protocol v10;
- standalone HQ block removal;
- MPL-2.0 metadata correction;
- dependency cleanup;
- dead legacy code/API cleanup;
- final source-wide stale-reference/import/TODO sweep;
- unified Sable -> VS2 -> static movement across all HQ positional paths.

## Active phase — runtime acceptance

Run the frozen v10 runtime matrix:

1. native CC:T regression;
2. finite singular lifecycle/controls;
3. finite 2/4/8+ multispeaker and concurrency;
4. listener/recovery lifecycle;
5. Sable/Aeronautics + VS2 movement;
6. RAW timing/backpressure/group behavior;
7. MP3/ICY direct + strict grouped radio;
8. malformed/extreme media and storage/range/worker bounds;
9. Sound Physics Remastered;
10. realistic performance and both NeoForge targets.

## After runtime

Only release-blocking defects should change the frozen API/semantics. Then:

- reconcile any bug-fix docs;
- final package/dependency verification;
- decide release version/changelog;
- promote/merge the real product branch to the intended default branch;
- cut release artifacts.

## Deferred feature bucket

Not part of this release freeze: OGG, FLAC, HLS, MPEG-TS, provider playback, shared finite decode fan-out, gapless playback, standalone HQ block, or Java application roles/playlists.
