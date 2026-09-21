# Known issues and risk register

Updated: 2026-09-21

Only current unresolved/deferred items are listed here. Retired HLS/TS and expected-count live issues are closed by removal/redesign.

## Runtime evidence still required

### KI-RUNTIME-001 — finite listener lifecycle

Verify outside-at-start, enter, leave, re-enter at current time, dimension/disconnect cleanup and terminal/replacement cleanup in Minecraft.

### KI-RUNTIME-002 — renderer/reload/starvation recovery

Verify resource reload, renderer/SoundEngine loss and sustained starvation in Minecraft.

### KI-RUNTIME-003 — moving speakers

All HQ positional paths now use Sable -> VS2 -> static source resolution. Verify actual Sable/Aeronautics and VS2 movement in-game.

### KI-RUNTIME-005 — finite multispeaker

Verify real 2/4/8+ speaker synchronization, shared controls, endpoint-local gain/mute, `audioStopAt`, endpoint removal/replacement and concurrent controls.

### KI-RUNTIME-006 — MP3/ICY radio

Verify direct and grouped radio audibility, strict snapshot behavior, long-run drift, late-member rejection/rerun, metadata, bad URL/EOF/network failure and stop/replacement behavior.

### KI-RUNTIME-007 — RAW

Verify signed-16 audibility, repeated feed behavior, backpressure/retry event, All preflight/common start, drain ownership and moving-source behavior.

### KI-PERF-006 — finite per-endpoint decode cost

Finite playback intentionally keeps independent endpoint decoders/renderers. Profile realistic 2/4/8+ loads before considering shared finite decode fan-out.

### KI-RELEASE-010 — integrated acceptance

Before release, run malformed/extreme media, storage/range/worker bounds, repeated replacement/control stress, Sound Physics Remastered, and final checks on both NeoForge targets.

### KI-REPO-011 — release branch hygiene

The product branch is `codex/m1j-multispeaker`; GitHub default `main` remains historical until explicitly promoted/merged for release.

## Frozen semantics, not issues

- `audioStopAt` is endpoint-local.
- grouped radio has no automatic membership.
- `isStreaming` means server-side stream ownership/request state, not confirmed client audibility.
- HLS/TS are removed.
- shared finite decode fan-out is not selected.
