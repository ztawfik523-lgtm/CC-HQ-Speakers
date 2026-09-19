# CC:HQ Speakers — agent guide

Updated: 2026-09-19

## Current state

Active branch: `codex/m1j-multispeaker`.

M1E through M1H are complete at source/test/CI/package level. M1H-1 listener membership, M1H-2 recovery, and M1H-3 Sable/VS2 moving-source support are implemented. Focused M1H Minecraft runtime checks remain deferred to the runtime backlog for now; the owner did not reject later runtime validation. M1J modern finite multispeaker is the active source milestone.

Current source checkpoint for M1H-3: `5cd6d6ddcad4b5b4887b903f471de0f2f812795c`, CI `35451236630`.

Current branch head may contain documentation-only commits after that source checkpoint. Verify exact source and CI before claiming runtime behavior.

## How to make design decisions

Do not default to either the simpler design or the more general design.

When multiple approaches are reasonable, compare what each one actually buys the project. Weigh current implementation cost, maintenance burden, runtime/performance cost, new failure modes, debugging surface, reversibility, and migration cost against the realistic likelihood and value of the future cases the more adaptable design would cover.

Future flexibility is valuable only when the future need is plausible enough or expensive enough to retrofit later. Treat realistic near-term needs differently from hypothetical edge cases.

A simple design should not win merely because it is simple. A complex design should not win merely because it is more flexible or elegant.

Explain which option appears better justified by the tradeoff and why, but for meaningful tradeoffs leave the final choice to the owner. Handle minor implementation details yourself.

The roadmap is a planning tool, not immutable law. Reorder, merge, split, defer, or scrap roadmap items when a fresh comparison shows a better sequence. Explain the reason and preserve completed evidence/history.

## Communication style

Keep explanations practical, concise, and concrete.

Prefer normal paragraphs over tall stacks of one-sentence lines. Do not repeat the same idea in several forms just to make the response longer.

Avoid abstract architecture language and extreme implementation detail unless it is needed for the decision at hand. Explain what changes in-game, what changes in the code, what it costs, and what can go wrong.

The owner can understand technical material; do not over-explain straightforward points.

When presenting options, keep the comparison short enough to scan, but include the tradeoffs that materially affect the choice.

## Product rules

This is a programmable ComputerCraft speaker peripheral. Lua owns application meaning and policy. Do not add permanent Java music/effect/notification roles.

Preserve standard CC:T `playNote`, `playSound`, `playAudio`, `stop`, and native `speaker_audio_empty`. HQ RAW uses separate `hqspeaker_audio_empty`.

One physical speaker remains one mono positional source.

Modern prepared finite playback is server-authoritative, bounded, progressive, and currently supports MP3 plus supported common WAV. Protocol v7 uses STATE as the nonterminal authority and explicit STOP for removal.

The fixed modern core radius is 32 blocks. HQ volume changes gain, not that core radius. Sound Physics Remastered work may intentionally extend acoustics/range later.

M1H moving-source support uses local Sable/Aeronautics and VS2 transforms. Do not add continuous position packets or a universal movement framework unless a concrete future requirement makes that tradeoff worthwhile.

## Source-work rules

Before changing source, read:

1. `docs/CURRENT-STATE.md`
2. `docs/KNOWN-ISSUES.md`
3. `docs/TESTING.md`
4. `docs/VERIFIED-FACTS.md`
5. `docs/ARCHITECTURE.md`
6. `docs/ROADMAP.md`
7. the exact current source and latest CI

Historical milestone/handoff documents preserve history and do not override current records.

For source changes: implement the selected behavior fully, add deterministic tests when they genuinely test the behavior, run both supported NeoForge CI targets, verify packaging when relevant, and adversarially reread the changed lifecycle paths before calling the work complete.

Do not invent artificial test plumbing merely to satisfy a checkbox when compilation/package/runtime evidence is the correct proof.

Green CI is not Minecraft runtime proof. Keep source/CI/package evidence separate from focused in-game acceptance.

## Current architecture boundaries

Current protocol-v7 source owns finite playback generation, timeline, controls, seek/reanchor revision, natural EOF, and terminal errors per speaker. M1J is refactoring canonical playback facts into one thread-safe shared playback authority referenced by independent physical speaker endpoints.

Client owns bounded encoded-window consumption, progressive decode, PCM buffering, SoundManager rendering, and local recovery/rejoin.

Temporary starvation is not EOF. Seek/replacement/stop must invalidate stale encoded waits, decoder workers, PCM, and renderer state.

M1H listener admission is dynamic. Late listeners receive the current authoritative state; leaving relevance gets cleanup; returning rejoins at current server time.

M1H recovery rejoins current server time after renderer loss or sustained starvation without inventing a new server revision.

M1H moving-source support resolves Sable/Aeronautics-style sublevels first, VS2 second, otherwise normal block center. Native ordinary Create contraption lifecycle is not part of this support unless a concrete requirement justifies separate work.

## Remaining roadmap areas

M1J modern finite multispeaker is active. The selected model is one shared playback authority plus independent physical speaker endpoints. The start-time member set is a snapshot; endpoint loss/removal must not fail the remaining group; there is no expected-global-member client barrier.

After correctness, multispeaker decode/network sharing is a profiling decision gate rather than a promised milestone. Then converge worthwhile legacy APIs onto the modern engine, evaluate codecs individually, run integrated compatibility/stress work, and perform release cleanup.

Direct radio/ICY/HLS/TS is no longer a core roadmap requirement. Spotify/YouTube/provider-backed playback is future research and must be treated as provider integration, not generic URL streaming.

Known later issues include the inherited multispeaker expected-member barrier, misleading legacy capability lists, incorrect legacy `playNoteAll`/`playSoundAll` semantics, inherited live/HLS/TS defects, license provenance, and release hygiene.
