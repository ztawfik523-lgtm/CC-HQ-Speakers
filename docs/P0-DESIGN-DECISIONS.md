# P0 design decisions

These are the remaining architecture choices with meaningful tradeoffs.

They are intentionally **not decided in this document**. The user should choose after reviewing the options.

Routine implementation details should not be escalated.

---

## D1 — heterogeneous raw + finite submissions on one physical speaker

Question:

What happens when one HQ speaker receives raw/feed audio while finite media is active, or finite media while raw/feed playback is active?

This is not a "music vs effect" question. Both are arbitrary Lua-controlled audio.

### Option A — one ordered non-stream output sequence

Raw submissions and finite items preserve call order on one physical output.

Example:

```text
speakMp3(A)
playAudio(B)
speakOgg(C)

audible order: A -> B -> C
```

Pros:

- closest to inherited CC:HQ's one-output/queue mental model;
- deterministic call ordering;
- no semantic roles;
- Lua can build queues naturally.

Cons:

- CC:T `playAudio` still needs its own single-buffer backpressure contract;
- raw/finite lifecycle must coexist without pretending raw chunks are seekable finite tracks;
- queue/state implementation is more complex than replacement.

### Option B — conflicting source type replaces/stops current HQ playback

A new raw/finite source stops the currently incompatible source and becomes active.

Pros:

- much simpler lifecycle;
- no hidden backlog;
- state/status easier to reason about.

Cons:

- materially changes inherited CC:HQ queue behavior;
- a small raw call can unexpectedly destroy a long finite playback;
- scripts must implement more arbitration themselves.

### Option C — independent concurrent playback instances

Raw and finite audio may render simultaneously and require independent addressing/controls.

Pros:

- maximum flexibility;
- Lua can explicitly build overlays/mixing behavior.

Cons:

- requires playback IDs/handles or another addressing model;
- stop/status/volume/seek APIs become multi-instance;
- larger architectural change, closer to a general audio engine;
- far beyond the current single-speaker M1 model.

Decision:

`UNRESOLVED`

Compatibility pressure:

A is closest to inherited CC:HQ. C is the most capable but largest departure. Do not infer a decision from that observation.

---

## D2 — who receives stop/control invalidation?

Problem:

A player can receive playback while near a speaker and later move out of the 32-block send radius. If stop/control is sent only to currently-nearby players, that client can retain stale playback.

### Option A — send small stop/control packets to all players in the dimension

Clients which never saw the source simply ignore the UUID.

Pros:

- simple;
- robust against range changes;
- little server state;
- stop invalidation cannot miss a former listener in the dimension.

Cons:

- extra tiny packets to unrelated players;
- less precise.

### Option B — track playback recipients and target them

Record which player UUIDs received/observed each playback/session and send later controls/stops to that set.

Pros:

- precise;
- scales traffic with actual listeners;
- useful foundation for renderer authority/failover.

Cons:

- additional session/recipient lifecycle;
- must handle disconnect/reconnect and cleanup correctly;
- more state to test.

Decision:

`UNRESOLVED`

---

## D3 — finite decoder backlog/cancellation

Problem:

Current global single-thread executor has an unbounded queue. Stale queued decode jobs retain encoded bytes and still consume CPU before lifecycle rejection.

### Option A — bounded single-thread executor, no active Future cancellation

Reject new decode work when the bounded queue is full; stale tasks already admitted finish and are discarded by generation/lifecycle checks.

Pros:

- small change;
- hard cap on backlog;
- easy to reason about.

Cons:

- rapid replacement can still waste CPU on admitted stale work;
- a stale long decode can delay a newer valid track.

### Option B — bounded executor + per-track Future cancellation/removal

Keep a handle for each decode job and cancel/remove obsolete work on stop/replacement.

Pros:

- stronger resource cleanup;
- replacement becomes responsive;
- better behavior before M2 larger-media work.

Cons:

- more lifecycle bookkeeping;
- cancellation cannot safely interrupt every native/codec operation at arbitrary points without care;
- requires tests around cancellation races.

Decision:

`UNRESOLVED`

---

## D4 — partial multi-speaker sync when a listener receives only part of a group

Problem:

Server group size describes all selected speakers, but each speaker's audio packet is range-filtered independently. A client can receive 2 of 4 packets while being told to wait for 4.

### Option A — server computes per-player delivered membership

For each listener, synchronize the subset of group members which will actually be delivered to that player.

Pros:

- preserves strict synchronization for every sound the listener can receive;
- no arbitrary client timeout;
- clean semantic result.

Cons:

- requires coordinated group dispatch rather than independent per-speaker ticks;
- larger server-side sync refactor.

### Option B — client bounded timeout then starts the received subset

Client waits briefly for the declared group, then starts whatever members it has.

Pros:

- smallest implementation;
- robust against packet/range asymmetry;
- no server group coordinator required.

Cons:

- start timing becomes timeout-dependent;
- a late packet may miss the group;
- weakens strict sync semantics.

### Option C — send group packets to the union of listeners for all members

Any client eligible for one member receives the full group's packets; positional attenuation determines audibility.

Pros:

- client always receives declared group membership;
- strict group start is simple.

Cons:

- more network/decoder work;
- clients may decode sources well outside normal per-speaker range;
- changes current locality/resource behavior.

Decision:

`UNRESOLVED`

---

## After the decisions

Once D1-D4 are chosen, encode them in:

- `ARCHITECTURE.md`;
- `CURRENT-STATE.md` as implementation target, not current fact;
- `P0-TEST-MATRIX.md`;
- targeted Java/state-machine tests;
- Lua runtime acceptance scripts.

Do not start implementation by silently filling in these choices.
