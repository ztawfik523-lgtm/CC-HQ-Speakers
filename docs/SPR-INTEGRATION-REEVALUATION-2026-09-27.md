# Sound Physics integration re-evaluation — 2026-09-27

Status: ordinary SPR integration phase selected; Sable-wall acoustics are deferred. Diagnostic source changes are implemented, but no acoustic behavior override is selected yet.

## Current scope decision

Do not treat this as a compatibility-mod rewrite.

For the current phase:

- HQ keeps its existing Minecraft/SoundManager playback architecture;
- SPR remains optional and client-side only;
- ordinary Minecraft/CC:T sound handling is left to SPR and is not an HQ problem;
- custom HQ finite/RAW/radio paths are measured directly through SPR;
- Sable-wall geometry is deliberately out of scope until ordinary SPR behavior is proven;
- no smoothing, diffraction, custom OpenAL ownership or scheduler is added without a demonstrated runtime need.

The current branch records exact `processSound` calls. C2 now checks the smallest compatibility requirement: one HQ source in open air versus a fresh restart behind a **normal-world** wall.

## Why this is being revisited

The old `cchq-soundphysics-compat` project was built around the older HQ fork. The current CC:HQ fork now owns its finite, RAW and radio renderers and can change them directly, so the old compatibility architecture is no longer a constraint.

The goal is to keep the parts which solved real acoustic problems while avoiding duplicate audio/lifecycle machinery when Minecraft's existing channel path is already sufficient.

## What current CC:HQ already gives us

All three custom HQ paths now reach Minecraft as normal positional BLOCKS-category streaming sounds:

- finite: `FiniteSpeakerSound` + `FinitePcmAudioStream`;
- RAW/radio: `HQSpeakerSound` + `HQAudioStream`;
- actual Minecraft `Channel` access is already available through `PlayStreamingSourceEvent` and `HQSoundChannelControl`;
- Sable world position is already resolved before/while the sound moves;
- F3+T, volume, stop, pause/recovery and client-channel diagnostics already work through Minecraft's SoundManager lifecycle.

SPR's own `Channel.play()` mixin normally invokes `SoundPhysics.onPlaySound`, so a newly-created HQ Minecraft channel should already receive SPR's initial processing. Attempt 7's current diagnostics are not strong enough to prove the full `processSound` path, because they observe only `setEnvironment`.

## What is actually missing / uncertain

1. **Direct proof of full SPR processing.** Add diagnostics at `SoundPhysics.processSound`, not only `setEnvironment`.
2. **Long-lived reevaluation follows SPR policy.** SPR's moving-sound reevaluation is optional. HQ should match that upstream behavior rather than add an HQ-only refresh loop by default.
3. **Sable geometry.** Projecting a speaker's position into world space does not make SPR's normal client-world raycaster see blocks stored in a moving Sable sub-level. Static-world walls and Sable walls must be tested separately.
4. **Reflected source-position persistence.** SPR may reposition an OpenAL source to a reflected direction; HQ/Minecraft position updates can later write the physical source position again. This matters only if full SPR directional-reflection fidelity is required.
5. **Scale above vanilla streaming-pool capacity.** Minecraft 1.21.1 partitions OpenAL channels into static and streaming pools. The streaming pool is `clamp(sqrt(totalChannels), 2, 8)`, so Minecraft-owned streaming sounds cannot exceed eight simultaneous streaming channels without changing that pool. This is separate from attempt 7's proven finite range-admission bug.

## What the old compatibility project still teaches us

The old project contains several layers which should be separated instead of ported as one unit.

### Obsolete for the current fork

- old whole-file HQ payload interception;
- duplicate whole-file decode/cache ownership;
- direct OpenAL source/buffer lifecycle used to replace that old payload path;
- old synchronized-start coordinator;
- old transport/session workarounds.

Current CC:HQ already owns these responsibilities more cleanly.

### Potentially reusable concepts

- explicit sound-thread call into SPR with a known HQ source ID, category and sound identifier;
- capture of SPR environment and reflected-position outputs;
- optional smoothing / retained acoustic state;
- progressive direct occlusion;
- HF/spectral balancing;
- V7.1 opening/diffraction behavior;
- exact ray reuse / room refresh scheduling if profiling later proves it useful.

The frozen V7.1 result remains a useful acoustic reference, not a requirement to retain its implementation structure.

## Important simplification found during recheck

SPR uses reusable OpenAL EFX filter objects, but OpenAL EFX applies filter parameters to a source when the filter is attached. Changing the filter object later does not retroactively alter an already-attached source until it is reattached.

Therefore the old project's private per-source filter objects are **not required merely to prevent one later source from overwriting another source's already-applied filter**.

Private EFX can still be useful for smoothing, retained state, failure isolation or custom post-processing, but basic HQ->SPR compatibility does not need to start there.

## Architecture options

### Option A — minimal Minecraft-owned integration

Keep all current Minecraft channels.

Keep the existing Minecraft channel path and the small optional diagnostic bridge which:

- receives the actual HQ channel/OpenAL source ID from the existing streaming-source event;
- lets SPR perform its normal channel-start processing;
- observes the real `processSound` call and environment result;
- adds no HQ-only refresh loop unless a later requirement proves one is necessary.

SPR remains responsible for its own environment calculation, filters, reverb and reflected-position write.

**Tradeoff:** smallest change and best lifecycle compatibility, but Sable-sublevel geometry and reflected-position persistence remain separate issues. Minecraft's <=8 streaming-pool ceiling also remains.

### Option B — Minecraft-owned channels + HQ-managed SPR state

Keep current SoundManager/AudioStream ownership, but capture SPR's calculated environment/reflected position and let HQ retain/reapply selected results.

This can add:

- explicit HQ-only refresh cadence;
- stable reflected-position reapplication;
- smoothing if actually useful;
- selected V7.1 direct-occlusion/diffraction post-processing.

**Tradeoff:** still avoids custom OpenAL PCM/source ownership, but introduces more acoustic state and hooks. It should only be added for measured problems that Option A does not solve.

### Option C — direct OpenAL ownership again

Replace/bypass Minecraft streaming channels for HQ and own OpenAL sources/buffers directly, similar to the old compat.

**Tradeoff:** removes Minecraft's streaming-pool ceiling and gives maximum source/start control, but duplicates lifecycle, reload, pause/resume, volume, cleanup and stream pumping which the current fork already solved. This should not be the default merely because the old compat used it.

A simpler alternative for the >8 issue is to change Minecraft's streaming/static channel partition while keeping Minecraft channel ownership, if runtime testing confirms >8 is a required target and the device has sufficient total OpenAL sources.

## Sable-aware acoustics is orthogonal

If SPR processing works behind a static-world wall but not behind a Sable wall, the renderer architecture is not the root issue. The missing piece is geometry visibility.

Sable Companion exposes sub-level pose, intersection and world/local projection helpers. A possible targeted design is:

- mark only HQ-triggered SPR evaluations with a sound-thread context;
- intercept SPR's raycast helper only during that HQ context;
- raycast the normal world plus intersecting Sable sub-levels by transforming the relevant ray segment into each sub-level's plot/local coordinates;
- return the nearest valid hit in world distance.

This would let the upstream SPR algorithm remain responsible for occlusion/reverb while supplying it the geometry it otherwise cannot see.

Do not implement this until a static-world vs Sable-wall diagnostic proves it is needed.

## Suggested investigation sequence before selecting an architecture

1. Run standalone C2 with one speaker and a normal-world wall.
2. Prove direct `processSound` + environment application in open air and after a fresh behind-wall start.
3. If fresh behind-wall occlusion works, ordinary SPR integration is sufficient for the selected release target.
4. Do not add HQ-only live refresh, reflected-position stabilization, smoothing, diffraction or caching without a separate demonstrated requirement.
5. Keep Sable-wall geometry and >8 channel ownership as separate later questions.

## Current architectural leaning (not a frozen decision)

The current fork removes the strongest reason the old compat needed a replacement OpenAL player: HQ audio already lives on real Minecraft channels.

That makes **Option A the smallest credible baseline**, with Option B as measured enhancements and Option C reserved for a proven channel-ownership limitation. This is a starting hypothesis, not a selected implementation.
