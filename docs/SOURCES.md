# Sources and provenance

Updated: 2026-09-29

Target stack:

- Minecraft 1.21.1
- Java 21
- NeoForge 21.1.247 build baseline
- mod metadata range `[21.1,21.2)`
- CC:Tweaked 1.120.0
- Sable Companion 1.6.0
- JLayer 1.0.1.4
- Sound Physics Remastered 1.21.1-1.5.1 for optional client acoustic integration

Current implementation checkpoint: `ac4548749bd16ab161eae9f233e89cb43ed4c0ce`.  
CI: `36467453786` — PASS.

Build/test/package only NeoForge 21.1.247. Do not maintain a second 21.1.248 build; compatibility is represented by the NeoForge metadata range.

CC:T target artifact: `cc.tweaked:cc-tweaked-1.21.1-forge:1.120.0`. Exact target CC:T source wins for native speaker semantics.

The SPR v11 integration was rechecked against the exact 1.21.1-1.5.1 method/field behavior relevant to `processSound`, `setEnvironment`, reflected positions, config values and shared EFX filter ownership.

Fork lineage: `tiktop101/CC-HQ-Speakers -> jvrcruzGAMES/CC-HQ-Speakers -> ztawfik523-lgtm/CC-HQ-Speakers`.

License: MPL-2.0.

JLayer is used by progressive finite MP3 decode and MP3/ICY radio. Sable Companion is used by the common moving-source resolver. mp3spi/Tritonus are removed.

Evidence priority:

1. real runtime acceptance for runtime-only behavior;
2. exact current source and target upstream source;
3. current authority docs;
4. deterministic CI;
5. historical milestone/handoff material.
