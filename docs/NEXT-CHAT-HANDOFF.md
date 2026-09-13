# CC:HQ Speakers — next-chat handoff

Date: 2026-09-13

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

Current branch: `codex/m1g-preparation`

Preparation base: `8b86d2d1977a23c1c9aeb30a996d3375a05a5b80`

Final M1F source/test candidate: `d0acd41df690d02c9813ecd7e84d3115b44f6a3f`

Final M1F CI: `34763362365`

## Current status

M1E and M1F are complete at source/test/CI/package level.

M1E focused final Minecraft acceptance was explicitly skipped and remains unrecorded.

M1F focused real-Minecraft range-transport acceptance remains unrecorded. M1F audibility was not required.

M1G has been prepared/re-audited but **implementation has not started**.

## Fresh verification

The previous uncertainty around docs-head run `34763711105` is closed: both NeoForge 21.1.247 and 21.1.248 ultimately passed.

At the owner's request the workflow was run again. Fresh rerun jobs `103742611713` (.247) and `103742612481` (.248) both passed build/tests/package verification/artifact upload.

Do not call this a Minecraft runtime PASS.

## Product framing

This is a programmable upgrade to the normal CC:T `computercraft:speaker`, not a built-in music player.

Lua decides application meaning. Java models technical capabilities only. One physical speaker remains one mono positional source.

Preserve standard CC:T `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty` behavior.

## Modern finite path at the M1G boundary

```text
CC file
-> immutable server MediaAsset
-> server-owned playback timeline
-> server-selected encoded anchor
-> M1F bounded range transport
-> M1F bounded sliding encoded window
-> M1G decoder/converter + bounded PCM + positional renderer
```

`HQFiniteMediaClient` is currently transport-only and is the clean modern M1G insertion point.

Do not route modern prepared playback back through `FileFiniteAudioStream`, whole-payload `HQAudioStream` finite decode, or `FiniteAudioTrack` whole-track PCM.

## MP3 starting facts

- exact packaged JLayer version: `1.0.1.4`;
- inherited live MP3 code proves frame-by-frame JLayer use in this project;
- temporary M1F `NEED_DATA` must wait/refill on a decoder worker, never become `InputStream` EOF;
- Layer III seek/rejoin requires earlier decode anchor + silent pre-roll;
- semantic seek restarts codec state even when coarse encoded anchor is unchanged.

## WAV starting facts

Current analyzer is historically broader than final target and current `MediaMetadata` lacks a normalized final WAV layout descriptor.

Final M1G target is common RIFF/WAVE only:

- mono/stereo;
- unsigned 8 PCM;
- signed 16/24/32 PCM;
- 32-bit IEEE float;
- stereo downmix to mono;
- reject >2 channels, companded/compressed/telephony WAV, 64-bit float, and unusual widths.

## Owner decision gates before M1G code

Do not choose silently. Read full tradeoffs in `PRE-M1G-PREPARATION.md`.

1. **Renderer**
   - A1: Minecraft `AudioStream` + normal SoundManager;
   - A2: direct Channel/OpenAL buffer queue.

2. **WAV layout**
   - B1: normalize on server and carry layout to client;
   - B2: progressively parse layout on client from M1F bytes.

3. **Sample rate**
   - C1: preserve source rate;
   - C2: normalize all finite PCM to 48 kHz.

M1G Java/resource implementation must wait for these choices.

## Non-negotiable M1G rules

- no whole encoded client file;
- no whole decoded PCM track;
- encoded/decoded memory bounded independently of duration;
- network/disk/codec work off game/audio threads;
- temporary starvation != EOF;
- stale decode/PCM discarded on seek/replacement/stop;
- server M1E state remains canonical;
- renderer failure is local/diagnostic;
- one physical speaker = one mono positional source.

## M1H boundary

Full late-entry/proactive-leave/return-rejoin/dimension-resource recovery/robust-underrun/VS2 listener lifecycle remains M1H.

## Read order

1. `docs/HANDOFF-2026-09-13-PRE-M1G.md`
2. `docs/PRE-M1G-PREPARATION.md`
3. `docs/M1F-FINALIZATION-2026-09-13.md`
4. `docs/CURRENT-STATE.md`
5. `docs/KNOWN-ISSUES.md`
6. `docs/TESTING.md`
7. `docs/VERIFIED-FACTS.md`
8. `docs/ROADMAP.md`
9. `docs/M1E-FINITE-STREAMING-DESIGN.md`
10. `docs/LUA-API.md`
11. exact current source/CI

Two old `ARCHITECTURE.md` transitional statements are stale: the current M1F max range is 128 KiB, and modern prepared transport no longer uses whole-file push/client `.part/.media` bridging. Current finalization/preparation docs override those paragraphs.
