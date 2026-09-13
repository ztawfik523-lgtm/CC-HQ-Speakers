# Roadmap

## Product rule

Build a better programmable ComputerCraft speaker peripheral. Lua owns application meaning/policy; Java exposes truthful audio capabilities.

Do not add permanent music/effect/notification roles or a Java playlist manager.

## Current sequencing note — 2026-09-13

M1E and M1F are complete at source/test/CI/package level.

M1F final source/test candidate:

`d0acd41df690d02c9813ecd7e84d3115b44f6a3f`

Final M1F CI:

`34763362365`

Current preparation branch:

`codex/m1g-preparation`

M1G has been re-audited and prepared in documentation, but **implementation has not started**.

The current M1F documentation head `8b86d2d1977a23c1c9aeb30a996d3375a05a5b80` passed both NeoForge targets in run `34763711105`; a requested fresh rerun also passed both target jobs.

## Foundation

### M1A — CC:T compatibility/output ownership

Standard `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty` stay delegated to CC:T. HQ RAW uses bounded separate ownership/backpressure.

### M1B — reusable server MediaAssets

Server-side encoded assets are UUID-addressed, disk-backed, quota-controlled, reference-counted, and seekably readable.

### M1C — ComputerCraft local-file import

CC files use temporary writable staging only to import immutable reusable server assets. Lua helpers expose prepare/play/release and `playFile`.

### M1D — server media analysis

Server analysis proves finite format/duration/coarse seek facts without whole-track PCM decode. Historical analyzer breadth does not define final support.

## M1E — server-authoritative finite timeline

**Complete at source/test/CI level.**

Server owns finite semantic truth: generation, PLAYING/PAUSED/ENDED/ERROR, position/duration, pause/resume, seek, loop, volume, natural EOF, and server errors.

Final focused Minecraft M1E acceptance was explicitly skipped/unrecorded.

## M1F — demand-driven finite encoded transport

**Complete at source/test/CI/package and deterministic/component level.**

Final candidate:

`d0acd41df690d02c9813ecd7e84d3115b44f6a3f`

Implemented/finalized:

- protocol v5 bounded range request/data;
- server-selected encoded anchors;
- first demand waits for authoritative STATE;
- source/asset/generation/bounds/relevance validation;
- bounded background reads and outstanding work;
- in-flight MediaAsset lifetime/retry safety;
- stale completion discard;
- retry-safe shutdown before store close;
- arbitrary re-anchor;
- DATA_AVAILABLE / NEED_DATA / TRUE_ASSET_EOF / CANCELLED_OR_STALE;
- forward sliding consume/discard preserving unread prefetch;
- bounded refill across files much larger than one client window;
- no modern complete-song client file;
- no modern CHUNK/END whole-file transport;
- no `audioPlayStaged()` prototype route.

Focused real-Minecraft M1F transport acceptance remains unrecorded. M1F audibility was not required.

## M1G — core progressive finite engine: MP3 + common WAV

**Prepared; not started.**

Preparation documents:

- `PRE-M1G-PREPARATION.md`
- `HANDOFF-2026-09-13-PRE-M1G.md`

Goal:

```text
M1F bounded sliding encoded window
    -> cancelable progressive decoder/converter worker
    -> bounded mono PCM queue
    -> one positional Minecraft renderer
```

Non-negotiable requirements:

- progressive MP3 from M1F encoded data;
- `NEED_DATA` never becomes decoder EOF;
- MP3 Layer III reservoir pre-roll;
- decoder restart on semantic seek even if coarse byte anchor is unchanged;
- common WAV conversion from bounded data;
- mono/stereo only; downmix stereo to mono;
- reject unsupported/broader historical WAV shapes;
- decoded memory bounded independently of duration;
- no sound-thread network/disk/codec blocking;
- cancellation/replacement safety;
- actual audible positional Minecraft playback;
- server M1E timeline remains canonical.

Current exact packaged MP3 decoder candidate is JLayer `1.0.1.4`; inherited live code proves frame-by-frame use in the shipped dependency path. Do not rebuild modern finite playback around JavaSound/mp3spi whole-file behavior.

### M1G owner decision gates before implementation

Do not choose these silently:

1. **Renderer path** — Minecraft `AudioStream`/SoundManager vs direct Channel/OpenAL queue.
2. **WAV layout ownership** — server-normalized layout sent to client vs client progressive WAV parsing.
3. **Finite sample rate** — preserve source rate vs normalize to 48 kHz.

Full tradeoffs are in `PRE-M1G-PREPARATION.md`.

## M1H — dynamic listener lifecycle/recovery

- discover players entering range after playback starts;
- proactive leave cleanup;
- return/rejoin current server time;
- dimension/world/resource reload recovery;
- robust underrun rejoin;
- final VS2 moving-speaker listener lifecycle.

M1G supplies a safe local engine; M1H supplies the full listener discovery/recovery policy.

## M1I — gated native FLAC

Optional. Only advertise native FLAC after analyzer, progressive decode, seek/rejoin, malformed-input, bounded-memory, package, and runtime proof. No Ogg-FLAC.

## M1J — multispeaker shared clocks

Correctness first: shared server sync clocks without expected-global-member barriers, while each physical speaker keeps its own mono positional renderer.

## M1K — active-session sharing optimization

After M1J correctness, share identical active encoded/decode work where safe without persistent client caching or collapsing physical renderers.

## M1L — legacy finite API migration/removal

Migrate worthwhile compatibility frontends to the new asset/transport/decoder engine; remove obsolete whole-file/whole-PCM finite state and old format promises.

## M1M — HQ RAW finalization

Keep bounded RAW producer semantics and separate `hqspeaker_audio_empty`; no fake finite timeline.

## M1N — SoundEngine/OpenAL integration cleanup

Correct category/gain, reload lifecycle, stale channel cleanup, attenuation/VS2 movement, one mono positional source per physical speaker.

## M1O/P/Q — hardening, package verification, consolidated runtime acceptance

Stress bounded queues/memory/network/lifecycle, keep both NeoForge targets green, then run final integrated Minecraft acceptance.

## M2 — Sound Physics Remastered

Integrate frozen SPR compatibility after finite positional rendering is stable.

## M3 — live/open-ended streams

Rebuild live MP3/HLS/TS with truthful live semantics and bounded resources. Do not force finite semantics onto live sources.

## M4 — release cleanup

Finalize public API/docs, obsolete/dead paths, custom HQ block decision, license provenance, and packaging.
