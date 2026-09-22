# Sources and provenance

Updated: 2026-09-22

Target stack:

- Minecraft 1.21.1
- Java 21
- NeoForge 21.1.247 baseline / 21.1.248 compatibility
- CC:Tweaked 1.120.0
- Sable Companion 1.6.0
- JLayer 1.0.1.4

Frozen source: `c61b052beee03ec0f36fed725fb37483bfb57d83`  
Source-freeze CI: `35655973164`.  
Runtime-prep head: `a234ba02b80532daf32f6849061b76f23c0eb4d3` / CI `35657182389`.

CC:T target artifact: `cc.tweaked:cc-tweaked-1.21.1-forge:1.120.0`. Exact target CC:T source wins for native speaker semantics.

Fork lineage: `tiktop101/CC-HQ-Speakers -> jvrcruzGAMES/CC-HQ-Speakers -> ztawfik523-lgtm/CC-HQ-Speakers`.

License: MPL-2.0.

JLayer is used by progressive finite MP3 decode and MP3/ICY radio. Sable Companion is used by the common moving-source resolver. mp3spi/Tritonus are removed.

Current evidence priority: runtime acceptance when behavior is inherently runtime-only; exact frozen source; API freeze/current-state/testing docs; deterministic CI; historical milestone material last.
