# Pre-M1F preparation checkpoint

This document is the current **preparation-only** checkpoint before any attempt to finish M1E runtime acceptance or start M1F implementation.

It exists to prevent a future implementation chat from reopening already-settled questions, repairing disposable bridge code, or mistaking diagnostic evidence for milestone completion.

## Do not implement yet

At this checkpoint:

- do **not** change M1E server semantics;
- do **not** declare M1E Minecraft-runtime PASS;
- do **not** implement M1F packets, buffers, executors, or range IO yet;
- do **not** fix/rewrite the temporary finite decoder yet;
- do **not** start M1G decode/render work early.

Preparation/doc cleanup is allowed. Implementation resumes only when explicitly requested.

## Exact active branch state

Repository:

`ztawfik523-lgtm/CC-HQ-Speakers`

Active branch:

`codex/m1e-server-authoritative-finite`

Original M1E semantic implementation checkpoint:

`d0e66ab9135359627086c13647d5241ad778643f`

Original M1E source/test/package CI:

`34658958488` — green on NeoForge 21.1.247 and 21.1.248.

The branch later gained runtime-diagnostic Java commits. The pre-preparation diagnostic head was:

`c7f5a70de4bade2f992591fcf8cdae9b28fe76a7`

Latest CI for that diagnostic head:

`34686003774` — green on NeoForge 21.1.247 and 21.1.248.

Do not call commits after `d0e66ab...` documentation-only. Several later commits changed Java solely to add runtime diagnostics. The M1E architecture/authority model did not change, but the branch head is code-bearing.

## M1E status

M1E's semantic goal remains server-authoritative finite playback:

- successful play starts the server clock immediately;
- server owns state, position, duration, pause/resume, seek, loop, volume, and EOF;
- client renderer readiness/failure cannot rewrite canonical truth;
- client ERROR is diagnostic only;
- no server LOADING state waits for client buffering.

The focused runtime script remains:

```text
scripts/m1e_server_authority_test.lua <small-mp3-or-wav>
```

The latest runtime logs provide strong partial evidence: the server continues authoritative transitions while the old client decoder fails. However the logs do not contain the terminal success line from the script, so M1E remains runtime-pending.

See `M1E-RUNTIME-DIAGNOSTIC-2026-09-12.md` for the exact diagnostic boundary.

## Decoder decision

The current prepared finite client uses the temporary whole-file JavaSound/mp3spi bridge. Runtime evidence shows that bridge is not trustworthy for MP3 seeking or duration:

- first PCM read can terminate immediately after the bridge seek;
- its MP3 seek path mixes decoded-PCM byte assumptions with mp3spi encoded-frame/byte skip behavior;
- it can report a requested seek target even after an incomplete/EOF seek;
- it performs a redundant second seek during renderer restart;
- MP3SPI reported 322.584 seconds for a roughly 2:45 fixture while the server analyzer reported 161.304 seconds.

Decision:

**keep the decoder in mind architecturally, but do not expect it to work or work properly before M1G.**

M1E is not a decoder milestone. M1F is not a decoder milestone. Do not spend those milestones polishing the bridge unless a new problem directly blocks their own contracts.

## M1F sequencing decision

The milestone order remains:

```text
M1E — server authority
    -> M1F — bounded demand-driven encoded transport
    -> M1G — progressive MP3/common-WAV decode + audible renderer
```

Do not pull M1G into M1F just to preserve audible playback during the transition.

## M1F clean-break decision

For the modern prepared finite path, M1F should make a **clean break** from the old whole-file transport instead of running both systems in parallel.

Once M1F implementation starts, its target shape is:

```text
server MediaAsset
    -> client range demand
    -> validated bounded async server read
    -> bounded range response
    -> bounded client encoded RAM/window
    -> future M1G consumer
```

The modern prepared path should no longer require:

- fixed recipients captured only at play start;
- server tick-thread whole-file reads;
- push of the entire encoded asset;
- client `.part` or `.media` song files;
- a complete local file before useful client work can begin;
- `FileFiniteAudioStream` as the prepared path's transport consumer.

Legacy classes may remain in the repository until their scheduled cleanup/migration milestone. A clean break means they stop defining the modern prepared path, not that unrelated legacy code must be deleted during M1F.

## M1F acceptance boundary

M1F PASS must **not** mean “the song is audible.” Audible progressive MP3/WAV is M1G.

M1F must prove the transport itself:

- bounded request offset/length validation;
- active source/generation/asset validation;
- current player connection/dimension/relevance checks;
- bounded outstanding request count and bytes;
- bounded response packets;
- file reads off the server tick on a bounded IO executor/queue;
- safe retained asset lifetime while async work is in flight;
- stale completion discarded after replacement, leave, or disconnect;
- cancellation releases accounting/refs;
- arbitrary encoded offsets work for seek/rejoin;
- client encoded RAM remains bounded independently of asset size;
- no persistent client song cache/files are required;
- exact returned bytes correspond to the requested server asset region.

A deterministic fake/test consumer is sufficient for M1F. It does not need to decode audio.

## M1F client window contract to preserve for M1G

Even without a codec decoder, M1F must not expose “bytes not here yet” as permanent EOF.

The client-side encoded data layer should make these states distinguishable in its design/API/tests:

```text
DATA_AVAILABLE
NEED_DATA / NOT_ARRIVED_YET
TRUE_ASSET_EOF
CANCELLED_OR_STALE
```

Names are not frozen here; the semantic distinction is.

This is important because M1G's progressive MP3 decoder must wait/refill on temporary starvation rather than terminate the stream.

## Seek/anchor boundary

M1F remains generic byte-range transport, but it must be compatible with server-selected codec/layout anchors:

- asset ID;
- generation/source identity;
- encoded byte offset;
- anchor media time;
- only the codec/layout facts needed for a future decoder.

The client does not need the entire server seek index.

MP3 Layer III pre-roll/bit-reservoir reconstruction is **M1G**, not M1F. M1F only needs to make requesting the earlier anchor region possible.

## Duration authority

The server analyzer remains authoritative for finite timeline duration. The old client decoder's MP3SPI metadata is diagnostic only.

The tested fixture was roughly 2:45 according to the runtime operator. MP3SPI's 322.584-second result is therefore invalid. The server's 161.304-second encoded-frame duration is in the expected range and remains canonical; optional gapless/display-duration refinement can be handled later if warranted.

## What to read before implementation resumes

Read in this order:

1. `PRE-M1F-PREPARATION.md`
2. `M1E-RUNTIME-DIAGNOSTIC-2026-09-12.md`
3. `CURRENT-STATE.md`
4. `VERIFIED-FACTS.md`
5. `ARCHITECTURE.md`
6. `M1E-SERVER-AUTHORITY.md`
7. `M1E-FINITE-STREAMING-DESIGN.md`
8. `ROADMAP.md`
9. `KNOWN-ISSUES.md`
10. `TESTING.md`
11. `FUTURE-CLEANUP.md`
12. current branch source and latest CI

`NEXT-CHAT-HANDOFF.md` and the dated handoffs remain valuable deep context, but this preparation document overrides stale branch-history wording in older handoffs.

## Explicit non-decisions

Preparation does not freeze the exact Java class names for M1F range/window components, exact packet size, exact IO-pool sizing, or exact decoder API for M1G. Those details should be chosen when implementation begins, with tests and profiling where appropriate.

Preparation also does not change the later choices around FLAC, multispeaker fan-out optimization, SPR packaging, custom HQ block removal, or licensing provenance.
