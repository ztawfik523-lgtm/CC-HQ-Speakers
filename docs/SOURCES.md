# Sources and provenance

## Exact target dependencies

- Minecraft 1.21.1
- Java 21
- NeoForge 21.1.247 baseline
- NeoForge 21.1.248 compatibility
- CC:Tweaked 1.120.0

CC:T Maven artifact used by this fork:

`cc.tweaked:cc-tweaked-1.21.1-forge:1.120.0`

## CC:T speaker contract

Exact target release:

- tag: `v1.21.1-1.120.0`
- release commit: `98f3a71`

Upstream source:

`cc-tweaked/CC-Tweaked`

`projects/common/src/main/java/dan200/computercraft/shared/peripheral/speaker/SpeakerPeripheral.java`

Official docs:

`https://tweaked.cc/peripheral/speaker.html`

Use the exact target source when implementation behavior matters. Current docs are useful for API semantics, but source wins for version-specific internals.

## Fork provenance

Repository:

`ztawfik523-lgtm/CC-HQ-Speakers`

Lineage:

`tiktop101/CC-HQ-Speakers -> jvrcruzGAMES/CC-HQ-Speakers -> ztawfik523-lgtm/CC-HQ-Speakers`

Untouched fork baseline:

`d1a592351c866f9a28ceef00b59e591ee773f3d5`

M1 reviewed reference:

`fba84a33a94d451af09b983bcb04416c97ff64cf`

## Codec dependencies

Build currently packages:

- `com.googlecode.soundlibs:mp3spi:1.9.5.4`
- `com.googlecode.soundlibs:jlayer:1.0.1.4`
- `com.googlecode.soundlibs:tritonus-share:0.3.7.4`

OGG finite decode uses LWJGL STBVorbis from the Minecraft/LWJGL stack.

Do not claim AAC/MP4 support merely from file extensions; require an exact decoder/runtime proof.

## HighAudio research

See:

`research/HIGHAUDIO-TRANSFERABLE-FINDINGS.md`

Use only transferable facts such as sound-thread behavior, PCM alignment, STB experiments, and source reservation evidence.

## Sound Physics Remastered

Existing compat repository:

`ztawfik523-lgtm/cchq-soundphysics-compat`

Frozen V7.1 acoustic baseline:

`ffcf5f6e05d85b69f1f1dff8cfae1b082b71604d`

Approved V7.1 JAR SHA-256:

`30d457c2a52672f893b1076938e2fdea3f41759173dfd843ff652bd490692101`

## Evidence policy

Order:

1. exact runtime;
2. exact current source;
3. `VERIFIED-FACTS.md`;
4. current-state/contract docs;
5. architecture/roadmap;
6. historical notes.

Keep facts and recommendations separate.
