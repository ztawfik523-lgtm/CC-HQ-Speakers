# Sources and provenance

Updated: 2026-09-17

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

Primary speaker source:

`projects/common/src/main/java/dan200/computercraft/shared/peripheral/speaker/SpeakerPeripheral.java`

Official docs:

`https://tweaked.cc/peripheral/speaker.html`

Use the exact target source when implementation behavior matters. Current docs are useful for API semantics, but exact source wins for version-specific internals.

## Fork provenance

Repository:

`ztawfik523-lgtm/CC-HQ-Speakers`

Lineage:

`tiktop101/CC-HQ-Speakers -> jvrcruzGAMES/CC-HQ-Speakers -> ztawfik523-lgtm/CC-HQ-Speakers`

Untouched fork baseline:

`d1a592351c866f9a28ceef00b59e591ee773f3d5`

M1 reviewed reference:

`fba84a33a94d451af09b983bcb04416c97ff64cf`

Final M1G source checkpoint:

`fa679ffcb81a66fd99ab6be8e6d6b77895fbc542`

Final source CI: `35297026277`, green on NeoForge 21.1.247 and 21.1.248. Closeout commits after that checkpoint are documentation-only.

## Codec dependencies

Build currently packages:

- `com.googlecode.soundlibs:mp3spi:1.9.5.4`
- `com.googlecode.soundlibs:jlayer:1.0.1.4`
- `com.googlecode.soundlibs:tritonus-share:0.3.7.4`

**Modern prepared MP3 playback uses JLayer progressively.** mp3spi/Tritonus remain because inherited legacy paths still exist; their presence is not evidence that modern prepared playback uses the old JavaSound bridge.

Historical/inherited OGG finite decode uses LWJGL STBVorbis from the Minecraft/LWJGL stack. OGG does not define the current modern prepared support surface.

Current modern prepared support is MP3 + supported common WAV only. Do not claim AAC/MP4/M4A/MP2 support merely because inherited capability lists advertise extensions or because JavaSound providers are packaged.

## HighAudio research

See:

`research/HIGHAUDIO-TRANSFERABLE-FINDINGS.md`

Use only transferable facts such as sound-thread behavior, PCM alignment, STB experiments, and source reservation evidence. Do not import HighAudio's application architecture wholesale.

## Sound Physics Remastered

Existing compat repository:

`ztawfik523-lgtm/cchq-soundphysics-compat`

Frozen V7.1 acoustic baseline:

`ffcf5f6e05d85b69f1f1dff8cfae1b082b71604d`

Approved V7.1 JAR SHA-256:

`30d457c2a52672f893b1076938e2fdea3f41759173dfd843ff652bd490692101`

Current project direction:

- M1G keeps a fixed 32-block core modern-finite range;
- HQ volume changes gain, not core range;
- later SPR compatibility owns intentional acoustic/range extension and matching transport relevance;
- do not build a speculative SPR range/plugin layer into M1G.

See `research/SPR-INTEGRATION-BASELINE.md`.

## Repository audit evidence

PR #1 (`docs: add corrected 2026-09-16 repository implementation review`) is supporting audit evidence, not current authority.

Its corrected report explicitly records first-draft retractions. Current project docs and exact source take precedence.

Important rechecked facts which should not be lost:

- `FiniteDecodeAnchorSelector.Anchor` is exactly `(offset, seconds)`;
- modern STATE does not carry live x/y/z;
- `audioPrepareStaged(...)` is not synchronized on the composite monitor;
- the confirmed shared-monitor/DNS path is dynamic legacy stream dispatch;
- `HQSpeakerPeripheral` has no composite back-reference;
- provider cache lifetime intentionally depends on explicit lifecycle eviction;
- inherited HTTP stream paths close their streams.

## Evidence policy

Order:

1. exact runtime evidence for behavior which requires runtime proof;
2. exact current source;
3. `VERIFIED-FACTS.md`;
4. `CURRENT-STATE.md`, `M1G-SCOPE-DECISIONS-2026-09-14.md`, `KNOWN-ISSUES.md`, `TESTING.md`;
5. current architecture/roadmap/API/config docs;
6. corrected audit/research evidence;
7. historical milestone/handoff notes.

Keep facts, owner-selected decisions, recommendations, and unresolved choices separate.
