# M1A — compatibility and output ownership

## Scope

M1A stabilizes the normal physical `computercraft:speaker` boundary before the finite-media asset rewrite begins.

This milestone does **not** repair the prototype staged-file transport, the old retained-PCM finite engine, live streaming, or the inherited `*All` / `*At` synchronization helpers. Those are replaced or migrated by later milestones in `ROADMAP.md`.

## Standard CC:T speaker methods

The composite peripheral delegates the standard methods to the exact CC:T 1.120.0 `SpeakerPeripheral` already owned and ticked by `SpeakerBlockEntity`:

- `playNote`
- `playSound`
- `playAudio`
- `stop`
- native `speaker_audio_empty`

This is source-implemented. Runtime acceptance still requires `scripts/p0_cc_speaker_contract.lua`.

Important consequences:

- note instruments, note limits, sound identifiers, signed 8-bit `playAudio`, native single-buffer backpressure, previous native audio volume, and native stop semantics come from CC:T rather than HQ approximations;
- `playNote` remains independent of HQ continuous playback;
- HQ no longer emits synthetic `speaker_audio_empty` events.

## HQ continuous-source ownership

For the normal single physical speaker, M1A tracks one HQ continuous owner:

- RAW (`speakPCM`)
- legacy finite byte input (`speakMp3`, `speakOgg`, `speakWav`, etc.)
- staged finite prototype (`audioPlayStaged`)
- live stream intent (`speakStream`, `speakHLS`, `speakTS`)

Starting a new incompatible HQ source replaces the previous HQ source. There is no Java playlist and no automatic song queue.

Repeated `speakPCM` calls while RAW owns the speaker are continuation of the same feed.

An explicit HQ start also requests native CC:T continuous audio/sound to stop. CC:T notes are not cleared by native `stop()`, so note independence is preserved.

While an HQ continuous source is active, standard `playSound` and `playAudio` return `false` rather than overlapping it. Once HQ ownership ends, the native methods operate normally again.

## Stop semantics

- standard `stop()` stops both the native CC:T sound/audio state and the current HQ continuous source;
- `speakStop()` also performs the full physical-speaker stop for compatibility with the inherited HQ API;
- `audioStop()` truthfully stops whichever HQ continuous source currently owns the speaker, including RAW and stream intent;
- RAW/live still do not gain fake finite seek/duration/loop controls.

## HQ RAW backpressure

The inherited single-speaker server queue is bounded at 16 entries.

`speakPCM()` keeps its boolean acceptance contract. If it returns `false`, that calling computer becomes a waiter. Once the actual server queue again has capacity, M1A emits:

```text
hqspeaker_audio_empty
```

This event is intentionally separate from CC:T's native `speaker_audio_empty`, whose meaning remains exclusively tied to standard `playAudio`.

A script can therefore use:

```lua
while not speaker.speakPCM(samples) do
    os.pullEvent("hqspeaker_audio_empty")
end
```

M1A also corrects `speakMaxSamples()` at the composite boundary to the real contiguous table limit of `131072` samples.

## RAW renderer lifetime

The inherited client RAW stream otherwise remains alive and produces silence indefinitely after input stops.

M1A tracks accepted RAW sample duration in server ticks. After:

1. the inherited server queue is empty;
2. the accepted sample duration has elapsed; and
3. a short 20-tick idle grace passes,

it sends the inherited HQ stop and releases RAW ownership.

Tick-based accounting is deliberate: pausing an integrated server does not age queued audio out through wall-clock time.

This is a source-level lifecycle fix. Its audible timing still needs Minecraft runtime validation.

## Status/control routing

`audioStatus()` is routed by the current HQ owner instead of whichever old subsystem happens to retain terminal state.

RAW reports capability truth rather than fake finite fields:

- `kind = "raw"`
- no seek
- no loop
- no finite pause claim

No owner reports `kind = "none"` / `state = "idle"`.

Finite controls only route to the finite owner which actually owns playback. `audioStop()` is the exception because stop is meaningful for every HQ continuous source.

## Deliberately deferred

M1A does not retrofit the inherited multi-speaker helpers. Methods such as `speakPCMAll`, `speakMp3All`, `speakPCMAt`, and related controls still bypass the composite and therefore still follow the legacy implementation.

This is intentional. M1J replaces the old expected-count/group-transfer architecture with shared assets/timelines and per-speaker positional renderers. Patching the old helpers here would preserve architecture we have already decided to remove.

Also deferred:

- asset/playback separation and large-file range transfer (M1B+);
- migration of old finite byte APIs onto the new finite engine;
- truthful live-stream lifecycle/reconnect behavior;
- richer RAW pause semantics;
- sound-category normalization;
- SPR integration.

## Acceptance

Automated build/packaging must remain green on:

- NeoForge 21.1.247
- NeoForge 21.1.248

Runtime acceptance requires, at minimum:

```text
scripts/p0_cc_speaker_contract.lua
scripts/m1a_output_contract.lua [optional-small-mp3]
```

Until those are run in Minecraft, M1A compatibility should be described as **source-implemented / runtime-pending**, not runtime-proven.
