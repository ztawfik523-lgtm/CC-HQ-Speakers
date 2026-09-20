# Sources and provenance

Updated: 2026-09-20

## Target dependencies

- Minecraft 1.21.1
- Java 21
- NeoForge 21.1.247 baseline
- NeoForge 21.1.248 compatibility
- CC:Tweaked 1.120.0

CC:T artifact: `cc.tweaked:cc-tweaked-1.21.1-forge:1.120.0`

## CC:T speaker basis

Exact target release: tag `v1.21.1-1.120.0`, release commit `98f3a71`.

Primary upstream class: `projects/common/src/main/java/dan200/computercraft/shared/peripheral/speaker/SpeakerPeripheral.java`.

Exact target source wins for version-specific behavior.

## Fork provenance

`tiktop101/CC-HQ-Speakers -> jvrcruzGAMES/CC-HQ-Speakers -> ztawfik523-lgtm/CC-HQ-Speakers`

The original and intermediate repositories carry an MPL-2.0 `LICENSE`. Their mod metadata also contained an inconsistent LGPL-3.0 label. This fork keeps MPL-2.0 and corrected NeoForge metadata.

## Current source checkpoint

Current: `fe880002b387f329d39a7372af521f1eacce559a` / CI `35529480491` / both NeoForge targets PASS.

Important earlier checkpoints:

- M1G `fa679ffcb81a66fd99ab6be8e6d6b77895fbc542`
- post-M1G hardening `3d30ce4564de749f32171666df65de739b08ad77`
- M1H-1 `84e7bab99009e5871934a960908945ceb00a10a9`
- M1H-2 `aa72f0d2fc9f8cde53cd956389beca1743d06165`
- M1H-3 `5cd6d6ddcad4b5b4887b903f471de0f2f812795c`
- M1J first shared-playback checkpoint `b557773b9c6f6b8029aec132a1706f0d8da914bd`
- finite teardown `fcb6670dd818412c15509129105aa7f54be9d5ba`

## Embedded dependencies

JLayer `1.0.1.4` is used directly by modern progressive MP3 and optional live MP3 streaming.

Sable Companion `1.6.0` is used for Sable/Aeronautics sublevel position projection.

Removed: mp3spi, tritonus-share.

## Sound Physics Remastered

Existing compatibility/research repository: `ztawfik523-lgtm/cchq-soundphysics-compat`.

Current finite rendering uses normal Minecraft SoundManager sources. Runtime compatibility/performance remains part of the integrated backlog.

## Evidence policy

Priority: focused runtime evidence when required; exact current source; `VERIFIED-FACTS.md`; current state/issues/testing; current architecture/roadmap/API/config; research/audit evidence; historical milestone/handoff docs.
