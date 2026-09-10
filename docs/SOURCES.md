# Sources and provenance

## Fork lineage
- Original: `tiktop101/CC-HQ-Speakers`
- NeoForge port: `jvrcruzGAMES/CC-HQ-Speakers`
- This fork: `ztawfik523-lgtm/CC-HQ-Speakers`
- inherited baseline commit: `d1a592351c866f9a28ceef00b59e591ee773f3d5`

## Platform
- CC:T exact target: `cc-tweaked/CC-Tweaked` tag `v1.21.1-1.120.0`
- Upstream dependency instructions: `https://github.com/cc-tweaked/CC-Tweaked#using`
- Upstream release: `https://github.com/cc-tweaked/CC-Tweaked/releases/tag/v1.21.1-1.120.0`
- Published Maven module: `https://maven.squiddev.cc/cc/tweaked/cc-tweaked-1.21.1-forge/1.120.0/`
- Resolved coordinate: `cc.tweaked:cc-tweaked-1.21.1-forge:1.120.0`
- NeoForge targets: 21.1.247 and 21.1.248
- Minecraft audio classes of interest: `SoundEngine`, `SoundManager`, `AudioStream`, `Channel`, `Library`

M1 channel control was checked against the locally resolved Minecraft 1.21.1
mapped classes. The relevant exact members are `SoundManager.soundEngine`,
`SoundEngine.instanceToChannel`, `ChannelAccess.ChannelHandle.execute`,
`Channel.pause`, `Channel.unpause`, and `SoundManager.updateSourceVolume`.
No external-version audio API was used as evidence for these hooks.

## Codec/runtime
- LWJGL/STBVorbis: inherited OGG path and HighAudio incremental-decode research
- mp3spi / JLayer / Tritonus: inherited MP3/JavaSound path

Local Gradle resolution selected the module's Java 21 runtime variant and
downloaded `cc-tweaked-1.21.1-forge-1.120.0.jar` (SHA-1 path component
`d992be398f5d28df278aa790b71dbf29f60ca5b4`). Both exact NeoForge development
servers discovered it as mod id `computercraft`, version `1.120.0`.

## SPR
- upstream: `henkelmax/sound-physics-remastered`
- target: Minecraft 1.21.1 / SPR 1.5.1
- project work: `ztawfik523-lgtm/cchq-soundphysics-compat`

## HighAudio
- `ztawfik523-lgtm/cctweakedhighaudio`
Reuse exact-stack facts, testing discipline, STB/OpenAL findings and provenance discipline.
Do not automatically reuse its custom upload/content/session/transport architecture.

## License rule
If code is copied or materially adapted, record source file/path, exact source commit/tag and license, and preserve required notices.

The inherited repository has a top-level MPL-2.0 license while mod metadata says LGPL-3.0; resolve deliberately before release.
