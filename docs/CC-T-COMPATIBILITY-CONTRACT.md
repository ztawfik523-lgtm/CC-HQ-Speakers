# CC:Tweaked 1.120.0 speaker compatibility contract

Updated: 2026-09-20

## Scope

The mod upgrades CC:Tweaked's normal `speaker` peripheral and still exposes peripheral type `speaker`. Standard CC:T behavior is mandatory; HQ APIs are extensions.

The only block product is `computercraft:speaker`.

## Implementation boundary

The mixin retains CC:T's actual `SpeakerPeripheral` and wraps it with `HQSpeakerCompositePeripheral`. Standard behavior is delegated rather than reimplemented.

## Standard singular methods

`playNote` delegates to CC:T's real implementation, preserving the actual instrument, validation, limits and return semantics.

`playSound` delegates to CC:T when no HQ continuous source owns the physical output. If an HQ continuous source is active, arbitrary native sound is rejected rather than overlapped.

`playAudio` delegates to CC:T's real DFPWM path when no HQ continuous source owns the output, preserving signed 8-bit input, 48 kHz, 131072 sample call limit, native buffer/backpressure, `speaker_audio_empty` and volume state.

`stop` stops native arbitrary sound/audio and the current HQ continuous owner while retaining native note semantics.

## Standard grouped/indexed helpers

The composite intercepts `playNoteAll/playSoundAll/playAudioAll` and `playNoteAt/playSoundAt/playAudioAt` and invokes the real CC:T speaker at each selected endpoint.

The old fake grouped/indexed standard bodies and the dead singular fake standard bodies have been removed from `HQSpeakerPeripheral`; the composite is the supported standard CC:T surface.

## Native vs HQ backpressure

Native CC:T: `speaker_audio_empty`.

HQ RAW: `hqspeaker_audio_empty`.

HQ RAW emits its event only to a producer which observed `speakPCM(...) == false` and later has capacity.

## HQ continuous ownership

One HQ continuous owner exists at a time: RAW, modern finite, or optional live stream.

Replacement should admit/validate the new source before ending valid current output where practical.

Native notes remain independent.

## HQ RAW

Separate from native `playAudio`:

- signed 16-bit;
- 48 kHz;
- max 131072 samples/call;
- bounded queue/backpressure;
- singular/All/At;
- no fake seek/duration/loop.

## Modern finite

Separate extension:

- MP3/common WAV;
- fixed 32-block core relevance/delivery;
- volume affects gain, not core radius;
- pause/resume/seek/loop and ordinary/All stop;
- `audioStopAt(index)` intentionally detaches only the selected physical endpoint;
- multispeaker shared authority;
- endpoint-local volume/mute.

## Compatibility evidence

Source/CI proves delegation and packaging. Final public release should still include focused native CC:T runtime regression tests for standard singular/grouped/indexed behavior and ownership collisions.
