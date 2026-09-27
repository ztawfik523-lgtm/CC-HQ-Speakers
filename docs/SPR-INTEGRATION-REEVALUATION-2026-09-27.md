# Sound Physics integration re-evaluation — 2026-09-27

Status: architecture research only; no product source change is selected by this document.

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
2. **Reliable reevaluation for long-lived HQ sources.** SPR 1.21.1 defaults `update_moving_sounds=false`. HQ speakers can move with Sable and the listener can move while the same source remains alive, so one startup evaluation can become stale.
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

Add a small optional client bridge which:

- receives the actual HQ channel/OpenAL source ID from the existing streaming-source event;
- explicitly invokes SPR `processSound` for HQ at channel start;
- reevaluates only HQ sources when source/listener movement or a modest periodic refresh requires it;
- records direct process-call diagnostics.

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

1. Add `processSound` call/result diagnostics only.
2. Re-run C2 against a known static-world wall.
3. Re-run against a Sable-sublevel wall if that behavior is part of the target.
4. If initial processing works but long-lived movement goes stale, prototype Option A's HQ-only refresh and measure cost at 1/2/8 sources.
5. Add reflected-position retention only if a direct test shows the current Minecraft position updater breaks a desired SPR effect.
6. Fix the independent finite range-admission bug before interpreting 8-source acoustic performance.
7. If >8 simultaneous playback is required, compare a small Minecraft streaming-pool repartition against direct OpenAL ownership before choosing the more invasive model.
8. Only then decide which V7.1 acoustic enhancements are worth porting.

## Current architectural leaning (not a frozen decision)

The current fork removes the strongest reason the old compat needed a replacement OpenAL player: HQ audio already lives on real Minecraft channels.

That makes **Option A the smallest credible baseline**, with Option B as measured enhancements and Option C reserved for a proven channel-ownership limitation. This is a starting hypothesis, not a selected implementation.
