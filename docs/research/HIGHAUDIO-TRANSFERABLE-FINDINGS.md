# Transferable HighAudio findings

Keep the expensive research; do not import the whole architecture.

## Useful
- `AudioStream.read()` exact-stack sound-thread observation.
- one returned buffer -> one OpenAL upload/queue path in the inspected runtime.
- never drop PCM tails; preserve complete frame alignment.
- STBVorbis incremental decode/seek/length/error/close results.
- strong native encoded-input lifetime through close.
- vanilla 8-stream measurement and proven total-preserving 16-stream rebalance.
- batched manual-test policy.

## Do not transplant by default
- custom file upload protocol
- SHA-256 ContentId store
- HighAudio server session framework
- custom content transfer protocol
- GenericSource product architecture
- full HighAudio milestone system

Rule: reuse a HighAudio fact/technique when it directly solves a CC:HQ bug.


## MP3 seek/duration caveat

HighAudio research already identified MP3 as a harder finite-media case than
Ogg for truthful duration/seek behavior.

For MP3, exact duration and efficient seeking may depend on optional VBR
metadata such as Xing/VBRI or require scanning/indexing the stream. Do not
assume every MP3 can cheaply provide exact total duration or random seek.

By contrast, the tested Ogg/STBVorbis path exposed total sample count and exact
sample seek without full PCM predecode.

When M1 reaches MP3 duration/seek:
- treat this as known prior research;
- verify behavior against the actual inherited JLayer/mp3spi path;
- do not force false "exact" semantics merely to make the API uniform.
