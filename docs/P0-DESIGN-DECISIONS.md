# P0 design decisions — superseded by accepted redesign

This file is retained as historical context for the options considered during P0. The original D1-D4 choices are **no longer unresolved** and must not be used as current implementation guidance.

The current target architecture is defined in `docs/ROADMAP.md` and summarized below.

## D1 — heterogeneous HQ playback

Historical options considered ordered queueing, replacement, and simultaneous independent instances.

Accepted direction:

- Java does **not** own a playlist or automatic finite queue;
- a new incompatible HQ continuous playback replaces the previous HQ continuous playback;
- repeated chunks belonging to one raw feed remain one feed;
- Lua owns sequencing, playlists, priorities, and retry policy;
- standard CC:T methods preserve CC:T's own semantics instead of being rewritten to match HQ policy.

Reason: application policy belongs in Lua. An accidental replacement call is a Lua program bug, not something the Java peripheral should hide with a secret queue.

## D2 — stop/control recipient ownership

Historical options considered dimension-wide invalidation versus tracking all playback recipients.

Accepted direction:

- do **not** keep a permanent historical listener/recipient set;
- server playback state is authoritative;
- clients dynamically create or destroy positional renderers according to current range/tracking relevance;
- when a client becomes relevant again it receives the current state/generation and joins at the current position;
- temporary transfer requests may contain player/request context, but that is not playback ownership.

This removes the need to remember everyone who once heard a speaker.

## D3 — finite decoder backlog/cancellation

Historical options assumed the retained-whole-PCM finite decoder would remain and debated bounded executor variants.

Accepted direction:

- the old whole-file/whole-PCM finite engine is not the large-file target;
- finite media becomes a reusable encoded asset;
- playback uses disk-backed client cache plus incremental decoding and bounded workers;
- old byte-taking finite APIs are later migrated onto the same asset/playback engine;
- obsolete whole-track decoder queue logic is removed rather than polished.

## D4 — partial multi-speaker synchronization

Historical options considered per-player group membership, client timeouts, or sending every group member to the union of listeners.

Accepted direction:

- no expected-global-member or expected-tap barrier;
- synchronized speaker playbacks reference a shared sync clock;
- the encoded asset is transferred/cached once per client and decoded/shared where practical;
- each audible physical speaker still gets its own positional Minecraft/OpenAL renderer;
- a client renders whichever speakers are currently relevant and may join the shared clock at any time;
- a physical speaker may later leave the shared clock if Lua pauses/seeks/replaces it independently.

This is the required basis for correct spatial audio and future Sound Physics Remastered integration.

## Large finite transfer decision

The old roadmap treated larger finite media as a future choice between chunked transfer plus whole decode or incremental decode.

Accepted direction:

- client-pulled bounded byte-range transfer;
- sub-1-MiB payload chunks (current target 256 KiB);
- reusable disk-backed encoded client asset cache;
- incremental finite decode;
- no whole decoded track retained as the large-file architecture.

The next client range request provides natural pacing/acknowledgement without a permanent server-side listener transfer list.

## Remaining open choices

Only these materially different choices remain open in the current roadmap:

1. first-play timing: advance the server playback clock immediately or wait for initial nearby readiness;
2. progressive playback while downloading: M1 may require complete encoded cache first; M2 may add prebuffered progressive finite playback.

Do not revive the old D1-D4 alternatives unless new runtime/source evidence exposes a problem with the accepted architecture.
