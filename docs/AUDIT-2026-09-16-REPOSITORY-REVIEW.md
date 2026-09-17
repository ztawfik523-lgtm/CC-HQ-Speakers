# Full-repository implementation review — 2026-09-16

**Type:** read-only audit. No existing source, resource, build, script, or
document was modified. This file is the only addition.

**Reviewed tree:** `2862a87509b1d80bc90e4ab947044a109522151f`
("docs: resolve M1G range and loop policy"), branch
`codex/m1g-progressive-finite-decode`. Upstream `main` is 383 commits behind and
300 files different, so the reviewed branch is the real working tip.

**Scope:** all tracked files — `build.gradle`, `settings.gradle`,
`gradle.properties`, `.github/workflows/build.yml`, all **65** main-source classes,
all **20** test classes (**2,618** lines), all resources, all Lua scripts, all
docs, and repository metadata read from GitHub.

## Method limits (per `AGENTS.md` §Evidence)

- **Nothing was compiled or executed.** The sandbox has no JDK (`java -version` →
  not found; `./gradlew --offline help` fails). Every statement below is from
  static reading and is an *implementation fact*, never an *acceptance pass*.
- Every `file:line` citation in this document was re-read at review time. §12
  lists claims an earlier draft made that did **not** survive that re-reading and
  were removed — recorded so nobody re-derives them.
- **Post-review addendum (2026-09-17):** the report was then checked as a claim set
  by the project owner. §13 records which findings were confirmed and promoted to
  KIs, which claims were wrong, and the further errors found while re-verifying.
  The body sections already incorporate those corrections.
- Upstream CC:Tweaked `1.21.x` source was cloned and read directly rather than
  recalled. NeoForge payload threading is taken from `FACT-AUDIT-012`, not
  re-derived.
- Repository/CI facts come from `gh api` (branches, run counts, commit diffs).

**Tags:** **NEW** = absent from `docs/KNOWN-ISSUES.md` (KI-001…KI-061) and
`docs/FUTURE-CLEANUP.md` at this commit. **EXTENDS KI-0nn** = tracked, but scope
or severity is understated. **CONFIRMS KI-0nn** = re-derived with line evidence.

`docs/KNOWN-ISSUES.md` is already unusually honest and detailed. This review spends
most of its length on **NEW** items, which cluster in the inherited legacy path,
the peripheral/composite ownership and locking model, packaging, CI, and
cross-document consistency — the areas the M1x audits have not been pointed at.

---

## 1. Executive summary

The M1G modern pipeline (`HQFiniteMediaServer` → `FiniteRangeWindow` →
`FiniteEncodedInputStream` → progressive decoders → `FinitePcmQueue` →
`FinitePcmAudioStream` → `FiniteSpeakerSound`) is coherent and well-factored. I
found **no new blocking defect inside it**; its seven tracked issues
(KI-051/053/054/055/056/057/058/060/061) are correctly prioritised.

New findings concentrate in four places:

1. **Locking and main-thread budget.** Static per-speaker monitors are acquired by
   the server main thread *every tick* and by CC:T computer threads that can block
   on **network I/O** (§2.1).
2. **The inherited legacy path**, still fully reachable from Lua: sine-wave note
   synthesis that ignores its arguments (§2.6), decode that materialises before it
   bounds (§2.3), live HLS that cannot play past its first playlist window (§2.4),
   and ownership transfer that destroys playback before it knows whether the new
   request is acceptable (§2.8).
3. **Packaging and product surface**: the mod's own block/item are registered and
   CI-verified but unobtainable in game (§2.7), and two advertised format lists
   contradict the decoders and `docs/SOURCES.md` (§2.5).
4. **Process artefacts**: a four-way contradiction about whether KI-051 is decided
   (§6.1), a stale acceptance marker (§6.3), and CI that builds two full NeoForge
   targets for every documentation commit (§5.1).

| # | Finding | Tag | Severity |
|---|---|---|---|
| 1 | Server main thread acquires per-speaker monitors every tick; a computer thread can hold one across blocking DNS | NEW | **Blocking** |
| 2 | `MediaAssetStore` root lock can be orphaned at shutdown, bricking the media root for the JVM's lifetime | EXTENDS KI-054 | **Blocking** |
| 3 | `beginReplacingHQ` stops current playback before capacity/start validation, so a rejected call still destroys the playing track | NEW | High |
| 4 | Live HLS silently stops after the first playlist window | NEW | High |
| 5 | `speakSupportedFiles()` / `audioSupportedFiniteFiles()` / `getStreamFormats()` advertise formats no decoder supports | NEW | High |
| 6 | `playNoteAll` / `playSoundAll` / `playNoteAt` / `playSoundAt` synthesise a sine and ignore instrument and sound name | NEW | High |
| 7 | `hqspeaker:hq_speaker` block/item registered, packaged, CI-verified — no recipe, loot table, creative tab, or block model | NEW | High |
| 8 | `HQAudioStream.decode()` enforces its 64 MiB cap only after fully materialising the decoded stream | NEW | Medium |
| 9 | Four current documents contradict each other on whether KI-051 is decided | NEW | Medium |
| 10 | CI has no path filter: 392 runs, and the last 50 commits are docs-only | NEW | Medium |

---

## 2. Blocking and high severity

### 2.1 Server main thread blocks on computer threads that can block on DNS — NEW

Three individually innocuous choices combine into a server-wide freeze.

**A — the main thread acquires every speaker's monitor, every tick.**
`HQSpeakerMod.onServerTick` (`HQSpeakerMod.java:58-63`, `ServerTickEvent.Post`):

```java
ServerMediaAssets.tickPendingReleases();   // synchronized on the class monitor
HQSpeakerPeripheral.tickAllActive();       // iterates the static ACTIVE_SPEAKERS set
HQFiniteMediaServer.tickAll();             // -> tick() is `private synchronized`  (HQFiniteMediaServer.java:280)
HQSpeakerCompositePeripheral.tickAll();    // -> tickOwnership() is `private synchronized` (HQSpeakerCompositePeripheral.java:101)
```

`tickOwnership()` early-returns unless `owner == Owner.RAW` (`:101-102`), but **it
must acquire the monitor to find that out**. `HQFiniteMediaServer.tick()`
(`:280-286`) does little under its monitor — read the session field, terminal
check, and only on natural end `finalizeNaturalEnd` → `releaseAssetReference` →
`MediaAssetReleaseQueue.release` (`:41`, `synchronized`) → `Files.deleteIfExists`
(`MediaAssetStore.java:185`) — but it is still a monitor the main thread acquires
every tick while computer threads can hold it via `callMethod` (`:206`) into the
`synchronized` finite controls (`pause` `:172`, `seek` `:196`, `setVolume` `:220`,
…). Separately, `ServerMediaAssets.tickPendingReleases()`
(`ServerMediaAssets.java:47-51`, `static synchronized`) performs the periodic
deletion retry on the main thread every `RELEASE_RETRY_TICKS`.

**B — CC:T computer threads acquire the same monitors.**
`HQSpeakerCompositePeripheral.callMethod` is `synchronized` (`:206`) and is entered
on a **computer thread** for every dynamically-dispatched name, which includes
`speakStream`, `speakHLS`, `speakTS`, `speakMp3`, `speakWav`, `audioPrepareStaged`,
`audioPlayPrepared`. `cleanup()` (`:146`) and `tickOwnership()` (`:101`) share the
monitor. Every `HQFiniteMediaServer` mutator is `synchronized` too —
`playPrepared` (`:118`), `pause` (`:172`), `resume` (`:186`), `seek` (`:196`),
`setVolume` (`:220`), `setLooping` (`:235`), `stop` (`:249`), `status` (`:260`),
`acceptRangeRequest0` (`:294`), `completeRange` (`:320`), `acceptStatus0` (`:343`).

**C — one of those paths performs blocking DNS.**
`speakStream`/`speakHLS`/`speakTS` are in `STREAM_START`
(`HQSpeakerCompositePeripheral.java:45`) → `invokeLegacy` →
`HQSpeakerPeripheral.startStreamAtTick` → `validateStreamUrl`
(`HQSpeakerPeripheral.java:1080` call site, `:1226` definition) →
`InetAddress.getAllByName(host)` (`:1250`). A synchronous resolver call with no
timeout and no cache, executed **while holding the composite monitor**.

**Consequence.** `speaker.speakStream("http://slow-or-blackholed-host/…")` makes a
computer thread hold the composite monitor for the duration of DNS resolution
(commonly 5–30 s with resolver retries). The server main thread then stalls in
`tickAll()` → `tickOwnership()`. That is a whole-server freeze reachable by any
player with a speaker and a URL — a griefing primitive on a public server, and a
reproducible "my server hung" report on a single-player world.

The same *shape* (weaker worst case) applies to:

- `cleanup()` (`:146`, `synchronized`) — reached from
  `HQSpeakerPeripheralProvider.forget`/`forgetLevel`/`clearAll` on the server
  thread during chunk-removal, level-unload and server-stop, so a computer thread
  parked in DNS stalls those lifecycle transitions too.
- `ServerMediaAssets.closeServer` → `FiniteRangeReadService.close()` →
  `awaitTermination(10 s)` (`FiniteRangeReadService.java:43,177`) on the server
  thread (§2.2).

**Recommended shape.** Restore the invariant *the main thread must never wait on a
monitor a computer thread can hold across I/O*:

1. Narrow the monitors. `tickOwnership()` needs only `owner`/generation state —
   `owner` is already `volatile` (`:75`), so the tick path may not need the monitor
   at all.
2. Resolve DNS asynchronously, off the monitor. Blocking a computer thread is
   acceptable; blocking the main thread is not.
3. Have `tickAll()` use `ReentrantLock.tryLock()` so a busy speaker skips its tick
   instead of stalling the server.
4. M1L's legacy removal deletes `speakStream`/`speakHLS`/`speakTS`, removing most
   exposure — but item 1 is still worth doing for the M1G monitor itself.

This deserves its own KI; it is orthogonal to KI-051…KI-061.

### 2.2 `MediaAssetStore` root lock can be orphaned, bricking the media root — EXTENDS KI-054

KI-054 records that a failed `MediaAssetStore.close()` "may leak `.part` or final
media and a stopped-server reference". There is a second, worse path it does not
mention, and the retry logic added for it is unreachable exactly when needed.

`ServerMediaAssets.closeServer` (`ServerMediaAssets.java:59-68`):

```java
assets.rangeReads.close();      // :64  — can throw IOException
assets.releases.retryPending(); // :65  — cannot throw (per-item catch)
assets.store.close();           // :66  — the KI-054 retry logic lives here
SERVERS.remove(server, assets); // :67
```

`FiniteRangeReadService.close()` throws if the range executor does not terminate
within `DEFAULT_SHUTDOWN_WAIT_MILLIS = 10_000` (`:43`, `:177`) or if per-player
accounting is non-empty after draining. A computer thread parked in a range read,
or a decoder worker that has not yet observed cancellation, is enough. **If `:64`
throws, `:66` never runs:**

1. `MediaAssetStore.close()` (`:341`) is never entered, so the root lock is never
   released — `releaseRootLockIfReady()` (`:314`) is only reachable from there.
   The lock survives for the JVM's lifetime.
2. `SERVERS.remove(server, assets)` (`:67`) never runs → strong reference to the
   stopped `MinecraftServer` (the leak KI-054 describes, by a different door).
3. On the next integrated-server start, `ServerMediaAssets.get` builds a new store
   on the same root. `tryLock()` on the still-held channel throws
   `OverlappingFileLockException`, surfaced as
   `"media asset store directory is already in use"`. Media playback is dead until
   the player fully quits Minecraft.

The KI-054 retry cannot help: it is gated on `closeCleanupDone` (`:315`), which is
set only inside the `toDelete != null` branch (`:363`), inside a `close()` that is
never called.

The shutdown *ordering* is correct and deliberate — `HQSpeakerMod.onServerStopped`
(`ServerStoppedEvent`, `:73-83`) calls `HQSpeakerPeripheralProvider.clearAll()` at
`:77` (releasing prepared/playback references) **before**
`ServerMediaAssets.closeServer(event.getServer())` at `:79`, with a comment at `:76`
saying exactly that. What is missing is consequence handling: the `IOException` from
`:79` is caught at `:80` and reduced to

```java
warn("could not close server media asset store: " + e.getMessage());   // :81
```

So the failure *is* visible in the log — good — but the warning text gives no hint
that the JVM is now in a state where **the next integrated server will fail to
initialise media assets entirely** (item 3 above), and nothing attempts recovery.
An operator reading `latest.log` sees a one-line warning at shutdown and then,
minutes later, an unrelated `"media asset store directory is already in use"` on the
next world load, with no connection drawn between them.

**Recommended shape:** wrap `:64-67` so `assets.store.close()` and
`SERVERS.remove(server, assets)` run in a `finally`; give `MediaAssetStore` an
explicit `releaseRootLock()` independent of deletion success; and reconsider
whether a non-drained range-service accounting should abort the whole store close
(warn + force shutdown seems closer to intent). Separately, a 10 s
`awaitTermination` on the server thread is itself a shutdown-hang risk — prefer
`shutdownNow()` with a short wait, since the store is closing anyway.

### 2.3 Ownership transfer destroys playback before validation — NEW

`HQSpeakerCompositePeripheral.startRaw` (`:256-275`):

```java
if (owner != Owner.RAW) beginReplacingHQ(Owner.RAW);   // :257

int samples = contiguousRawSamples(args);              // :259
boolean validSizedChunk = samples > 0 && samples <= HQ_RAW_MAX_SAMPLES;
if (validSizedChunk && (legacy.speakQueueSize() >= HQ_RAW_QUEUE_LIMIT
        || !rawLifetime.canAccept(samples, HQ_RAW_BUFFER_SAMPLES))) {
    rawCapacityWaiters.put(computer, samples);
    return MethodResult.of(false);                     // :264  — rejected, but playback is already gone
}
```

`beginReplacingHQ` (`:286-290`) calls `stopCurrentHQ()` then `vanilla.stop()`, and
`stopCurrentHQ()` (`:292-301`) dispatches `finite.stop()` or `legacy.speakStop()`
and resets `owner = Owner.NONE`.

So a computer that is playing a prepared finite track and calls `speakPCM` with a
chunk that will be **rejected for capacity** first destroys the finite session and
then returns `false`. Per `docs/LUA-API.md`, `false` from `speakPCM` means "buffer
full — wait for `hqspeaker_audio_empty` and retry", i.e. a *transient, retryable*
answer. The caller will retry, and by then the previous track is gone. The correct
reading of `false` is therefore not the documented one.

The same ordering appears in `audioPlayPrepared` (`:188-193`):
`beginReplacingHQ(Owner.STAGED_FINITE)` at `:189` runs **before**
`finite.playPrepared(...)` at `:190`, which can throw `LuaException` for
`"unknown or released media asset"`, `"media asset has not been analyzed"`,
`"HQ media services are unavailable"` (`HQFiniteMediaServer.java:124-141`). Any of
those leaves the previous playback stopped and nothing started — a failed
`audioPlayPrepared` is destructive.

**Recommended shape:** validate first, then transfer ownership. `startRaw` should
compute `samples`/`validSizedChunk` and return `false` for capacity *before*
calling `beginReplacingHQ`. `audioPlayPrepared` should either validate the asset
(pre-existence + analysed metadata) before `beginReplacingHQ`, or restore the
previous session on failure. Both are small reorderings with no protocol impact.

### 2.4 Live HLS stops producing audio after the first playlist window — NEW

`StreamingAudioSource.streamHLS` (`:244-282`):

```java
List<HLSPlaylistParser.MediaSegment> segs = currentPlaylist.segments;   // :265
while (currentSegmentIndex < segs.size() && !stopped.get()) {           // :266
    HLSPlaylistParser.MediaSegment seg = segs.get(currentSegmentIndex); // :267
    …
    currentSegmentIndex++;                                              // :273
}

if (currentPlaylist.isLive()) {
    Thread.sleep((long)(currentPlaylist.targetDuration * 500));         // :277
    currentPlaylist = null;                                             // :278
} else { break; }
```

`currentSegmentIndex` (`:57`, initialised 0) is a **monotonic counter over the
current playlist's list index and is never reset**. `currentPlaylist = null` forces
a re-fetch, and the fresh playlist's segments are re-based to index 0. A live HLS
stream advertises a sliding window (typically 3–6 segments), so once the counter
has passed the window length, `currentSegmentIndex < segs.size()` is permanently
false and the inner loop never runs again. **Live HLS plays its first window and
then goes silent forever** — the source stays open, `speakStatus()` keeps reporting
`streaming=true`, ICY metadata keeps arriving, and nothing logs an error.

Three related issues in the same method:

- Pacing is `Thread.sleep(targetDuration * 500)` (`:277`) — half the target
  duration — while the refresh threshold is a hardcoded 5000 ms (`:248`).
  `MediaSegment.duration` (`HLSPlaylistParser.java:47`) is parsed per segment and
  never used, so per-segment pacing is impossible.
- `stopped` is only tested in loop conditions, never during the sleep, so **stop
  latency for live HLS is up to `targetDuration * 500` ms**.
- The refresh branch (`:247-248`) requires `currentPlaylist.isLive()`; for a VOD
  playlist the loop `break`s at `:280`, which is correct. The bug is live-only.

`streamTS` (`:285-292`) has the mirror-image memory problem:
`TSDemuxer.demux(InputStream)` (`TSDemuxer.java:93`) returns
`List<AudioFrame>` holding **all** audio for the whole stream, so a live TS feed
accumulates in memory until the source is closed. `TSDemuxer` is a plain
`public class` (`:14`) — not `Closeable` — and the instance created at `:288` is
never released.

**Recommended shape:** identify segments by a stable absolute number derived from
`#EXT-X-MEDIA-SEQUENCE + listIndex`, keep a small ring of already-played absolute
numbers, and on each refresh play the set difference. Use `MediaSegment.duration`
for pacing. Make `TSDemuxer` incremental (`demuxNext`) with a hard cap rather than
whole-stream buffering.

This is M3 work per `ROADMAP.md`, but worth recording now: **`speakHLS` is
advertised in `docs/LUA-API.md`, in `getStreamFormats()`
(`HQSpeakerPeripheral.java:489`, `"supportsLive": true`), and in
`neoforge.mods.toml`, and it does not work.** Unlike the M1G issues, this failure
is silent, which is exactly the trap `docs/CURRENT-STATE.md` §1 warns about.

### 2.5 The advertised codec surface contradicts the implementation and `docs/SOURCES.md` — NEW

Three Lua-visible lists, none matching M1G reality:

| Surface | Value | Source |
|---|---|---|
| `speaker.audioSupportedFiniteFiles()` | `wav, ogg, mp3, aiff, aif, au, snd` | `HQSpeakerCompositePeripheral.java:46`, returned `:214` |
| `speaker.speakSupportedFiles()` | `wav, ogg, mp3, aiff, aif, au, snd, mp2, mp4, m4a, aac` | `HQSpeakerPeripheral.java:475` |
| `speaker.getStreamFormats()` | TS `"audioCodecs": {"AAC","MP3"}`, MP3 stream `"extensions": {".mp3",".mp2"}` | `HQSpeakerPeripheral.java:488-490` |
| **What the modern path accepts** | **MP3 and common WAV only** | `ModernFiniteMediaAnalyzer.analyze` (`:18-31`) |

`ModernFiniteMediaAnalyzer.analyze` (`:18-31`) is the gate: RIFF/WAVE →
`CommonWavAnalyzer.analyze`; otherwise `FiniteMediaAnalyzer.analyze` and then
`if (metadata.format() != FiniteMediaFormat.MP3) throw new IOException("modern
finite playback supports MP3 and common WAV only")`.

`mp4`, `m4a`, `aac`, `mp2` have no decoder anywhere in the repository.
`FiniteMediaAnalyzerTest.rejectsUnsupportedMp4RatherThanTrustingName` exists
precisely to prove MP4 is rejected rather than trusted by extension — so the test
suite and `speakSupportedFiles()` assert opposite things. `ogg`, `aiff`, `aif`,
`au`, `snd` are reachable **only** through the inherited JavaSound/mp3spi legacy
path, which `FACT-M1G-011` and `docs/LUA-API.md` §"Inherited legacy HQ APIs" say is
not the modern engine and is M1L removal material.

`docs/SOURCES.md` already states the policy ("Do not claim AAC/MP4 support merely
from file extensions… M1G narrows prepared finite local media to MP3 or supported
common WAV"), so the code contradicts a locked source-provenance decision. And
`speakSupportedFiles` is intercepted by `callMethod` at
`HQSpeakerCompositePeripheral.java:214` — it never delegates to CC:T, so this is
entirely HQ-owned surface with no compatibility reason to exist.

**Recommended shape:** derive both lists from the same predicate
`ModernFiniteMediaAnalyzer` uses, and make the legacy extensions report their real
(legacy) capability or be removed in M1L. A program that trusts
`audioSupportedFiniteFiles()` currently gets a promise the engine rejects.

### 2.6 `playNoteAll` / `playSoundAll` / `playNoteAt` / `playSoundAt` fake their output — NEW

`STANDARD` is exactly `Set.of("playNote", "playSound", "playAudio", "stop")`
(`HQSpeakerCompositePeripheral.java:37`). Every other name routes to
`HQSpeakerPeripheral`'s own implementations, including the pre-M1A synthesizers:

`playNoteAll` (`HQSpeakerPeripheral.java:771-789`) **ignores `instrument`
completely** and emits a hard-coded sine whose frequency derives only from `pitch`:

```java
int samples = (int)(SPEAKER_SAMPLE_RATE * 0.8);                       // :775
float freq = (float)(440.0 * Math.pow(2.0, (pitch - 9.0) / 12.0));    // :776
…
short sample = (short)(Math.sin(angle) * 32767 * 0.6);                // :780
```

`playSoundAll` (`:791-793`) **discards `soundName`** and forwards to
`playNoteAll(computer, "harp", …)`. A program asking every speaker for
`minecraft:entity.pig.ambient` gets a 0.8-second sine tone. `playNoteAt` (`:976`)
and `playSoundAt` (`:981`) delegate to the legacy `playNote` (`:277`) /
`playSound` (`:297`) — the same synthesizer, not the vanilla implementations the
singular methods now route to.

Additional contract deviations on Lua-visible legacy methods:

- `playNote(String instrument, double volume, double pitch)` (`:277`) takes
  **non-optional** volume and pitch. Upstream CC:T 1.120.0 is
  `playNote(ILuaContext, String, Optional<Double>, Optional<Double>)` (verified in
  cloned upstream `SpeakerPeripheral.java`). The two surfaces are not
  signature-compatible.
- The legacy path honours neither CC:T's per-tick note limit, nor its instrument
  registry, nor the jukebox-song exclusion upstream `playSound` applies.

`docs/CC-T-COMPATIBILITY-CONTRACT.md` claims "the source implementation for
standard CC behavior no longer attempts to synthesize or duplicate notes,
Minecraft sounds, DFPWM buffering" — true for the four singular standard methods,
false for the `*All`/`*At` family that sits beside them on the same peripheral.

**Recommended shape:** cheapest correct move is to route the `*All`/`*At`
note/sound methods through the same `standard.callMethod(...)` path the singular
methods use, iterating speakers. If multi-speaker note sync is not an M1G goal,
next cheapest is an explicit
`LuaException("not supported in M1G; use playNote per speaker")`. Silently-wrong
audio is the worst option because it passes a smoke test that only asks "did it
make a sound".

### 2.7 The mod's own block and item are unreachable in game — NEW

`HQSpeakerRegistry` registers `hqspeaker:hq_speaker` as a block, block-entity type,
and item; CI asserts all three plus the item model are in the jar. But:

- **No data-pack content at all.** `src/main/resources/data` contains exactly one
  path: `data/computercraft/lua/rom/modules/main/hqspeaker.lua`. There is no
  `data/hqspeaker/loot_table/**` (breaking the block drops nothing) and no
  `data/hqspeaker/recipe/**` (it cannot be crafted).
- **No creative-mode tab entry.** No `CreativeModeTab` registration and no
  `BuildCreativeModeTabContentsEvent` listener in `HQSpeakerRegistry` or
  `HQSpeakerMod`, so it is not in any creative inventory either.
- **No block model.** `assets/hqspeaker/models/block/` does not exist; the
  blockstate's single variant points at `hqspeaker:block/hq_speaker`, so placing it
  would render the missing-model cube.
- **The block entity never creates its peripheral.**
  `HQSpeakerBlockEntity.getOrCreatePeripheral()` (`:22-26`) has **zero callers
  anywhere in the repository**, so `peripheral` stays `null`, `serverTick()`
  (`:17-19`) is a permanent no-op, and `setRemoved()` (`:30-32`) does nothing.
  `HQSpeakerBlockEntity` is registered dead code. It also has no
  `getUpdateTag`/`getUpdatePacket`, so it would not sync to clients even if wired.

The shipped product is fine — the feature is the mixin over
`computercraft:speaker`. But the registered block is then either vestigial or a
planned-but-unwired second product, and those have opposite correct actions.

**Recommended shape:** decide and record it. If `hq_speaker` is not a product,
remove the block/item/block-entity/blockstate/item-model registrations **and the
five CI assertions guarding them** — they currently give false confidence that a
shipped feature is verified. If it is a product, it needs a loot table, a recipe
or creative-tab entry, a block model, a `getOrCreatePeripheral()` call site, and
client sync.

**Verified as fine, recorded to prevent re-litigation:** there is no registered
`SoundEvent` for `hqspeaker:hq_speaker`. `FiniteSpeakerSound`
(`client/FiniteSpeakerSound.java:16-17`) and the legacy `HQSpeakerSound`
(`HQSpeakerClientHandler.java:32`) both build the `ResourceLocation` directly, and
`assets/hqspeaker/sounds.json` declares `hq_speaker` with `"stream": true` plus a
present `sounds/hqspeaker/hq_speaker.ogg`. `docs/M0-SMOKE-TEST.md` records that
`playAudio`, `speakPCM`, and a 2,871,486-byte finite MP3 were **audible** through
this path on 2026-09-09, so sounds.json provisioning alone is sufficient under
NeoForge 1.21.1. `docs/VERIFIED-FACTS.md` would be a good home for that fact.

---

## 3. Medium severity

### 3.1 `FiniteSpeakerSound.updatePosition` is never called — NEW

`client/FiniteSpeakerSound.java:38-42` defines
`void updatePosition(float x, float y, float z)`. A whole-repository grep for
`updatePosition` returns exactly three hits: this declaration, the legacy
`HQSpeakerSound.updatePosition` declaration (`HQSpeakerClientHandler.java:638`),
and the legacy call site (`:546`). **The M1G renderer's position updater has no
caller**, so `FiniteSpeakerSound` freezes its source position at the value captured
when the renderer started (`HQFiniteMediaClient.tryStartRenderer`, `:305-308`).

The legacy path does it correctly in `tickPosition`
(`HQSpeakerClientHandler.java:532-551`): it resolves the VS2 ship matrix and calls
`sound.updatePosition(...)` at `:546`. Note it early-returns unless VS2 is loaded
(`:536`), which is right — static speakers do not move. So the M1G gap is scoped
precisely to **speakers on moving Valkyrien Skies ships**: the sound keeps playing
from its original world position for the whole track. Invisible in a static
single-player world, which is why it has not surfaced.

Position coordinates travel on the wire **once, in BEGIN**:
`HQFiniteMediaBeginPacket` carries `x/y/z` and `blockX/blockY/blockZ` (`:45-46`).
`HQFiniteMediaStatePacket` carries **no coordinates at all** (`:34-45`: source,
mediaId, generation, state, position-seconds, duration, volume, looping,
anchorOffset, anchorTime, error). So supporting moving speakers needs a *new*
mechanism, not merely a call to the existing updater: either client-side per-tick
VS2 resolution from BEGIN's block coordinates — exactly what the legacy
`tickPosition` does client-side at `HQSpeakerClientHandler:532-551` — or a
server-pushed position update. The uncalled `updatePosition`
(`FiniteSpeakerSound:38-42`) is a real gap, but it is the hook, not the fix.

**Recommended shape:** mirror `tickPosition` in `HQFiniteMediaClient.tick()`
(`:122-136`), calling `updatePosition` when the resolved world position moves by
more than an epsilon.

### 3.2 `hqspeaker_metadata` fires on every ICY block with no deduplication — NEW

The ICY path is fully wired and correctly authorised — `StreamingAudioSource`
parses metadata (`:583-590`) → `notifyMetadata` (`:493-498`) → the listener
installed in `HQAudioStream` (`:268-277`) or `SharedStreamingGroup` (`:117-124`) →
`IcyMetaPacket` → `handle` (`IcyMetaPacket.java:82-89`) →
`canAcceptIcyMetadata(sender)` (`HQSpeakerPeripheral.java:510-515`, which checks
stream-active, same level, and within `SPEAKER_RADIUS`) → `onIcyMetadata`.

`onIcyMetadata` (`:517-536`) then assigns the fields unconditionally, increments
`icyMetaSerial`, and at `:535`:

```java
for (IComputerAccess comp : attachedComputers) comp.queueEvent("hqspeaker_metadata", getStreamMeta());
```

**There is no comparison against the previous value.** Icecast servers commonly
emit an identical metadata block at every `icy-metaint` interval (typically
8–16 KiB, i.e. roughly every 0.5–1 s at 128 kbps), so every attached computer
receives a Lua event about twice a second, indefinitely, carrying an unchanged
title. A program doing `while true do os.pullEvent("hqspeaker_metadata") end` spins
forever, and `getStreamMetaSerial()` increments without the content changing — so
the serial cannot be used as a change indicator either.

**Recommended shape:** compare the parsed fields against the previous values and
return early when unchanged (still bumping a separate "last seen" timestamp if
useful). One `equals` check removes an unbounded event flood. This is the same
`queueEvent`-fan-out concern as §4.4, but here the fix is two lines.

### 3.3 MP3 analysis walks every frame on a computer thread with no early exit — NEW

`FiniteMediaAnalyzer.tryAnalyzeMp3` (`:291-320`) loops

```java
while (offset + 4L <= r.size) {
    Mp3Header header = parseMp3Header(r, offset);   // :306
    …
    offset += header.frameBytes;                    // :313
}
```

over **every frame in the file**. A grep for `xing`/`info`/`vbri` across
`FiniteMediaAnalyzer.java` returns nothing — there is no header shortcut. The walk
also builds seek hints (`SeekIndexBuilder` at `:303`, `:311`, `:319`), so it is not
pure waste; but it is unbounded work whose only other output is a duration that a
Xing/Info header would give directly.

Reached from `audioPrepareStaged` → `HQMediaStaging.prepareAsset`
(`HQMediaStaging.java:108`) → `ModernFiniteMediaAnalyzer.analyze`.
`audioPrepareStaged` is a plain `@LuaFunction` on the composite
(`HQSpeakerCompositePeripheral.java:176-180`) — **neither `synchronized` nor
`mainThread`** — so the walk runs on a **computer thread holding neither the
composite nor the finite-server monitor**; it takes the `ServerMediaAssets` class
monitor only briefly inside `get()` and the store monitor only during the import
copy. The impact is therefore a computer-thread stall — a Lua call that can run
for minutes — not the server-wide stall of §2.1. Still worth fixing (and still
amplified by the unbounded per-asset ceiling), but a different severity than an
earlier draft claimed. The per-asset ceiling is
`maxAssetMiB`, default `DEFAULT_MAX_ASSET_MIB = 512L`
(`config/HQSpeakerServerConfig.java:10`, registered `:29`; documented in
`docs/SERVER-CONFIG.md`). At 128 kbps / 48 kHz that is roughly
1.3 M frames → ~1.3 M `parseMp3Header` calls, each doing a 4-byte
`SeekableByteChannel` read. That is minutes of single-threaded work per prepare,
during which the main thread stalls in `tickAll()`, `acceptRangeRequest0` cannot
run (so already-playing sessions starve), and the computer is blocked.

`FiniteMediaAnalyzer` itself is careful and well tested (`FiniteMediaAnalyzerTest`,
394 lines — the largest test file in the repository). The problem is call-site
placement and the missing early exit.

**Recommended shape:** parse a Xing/Info/VBRI header for frame count and duration
when present, falling back to the full walk only when absent; read through a
buffered channel instead of 4-byte reads; and move `prepareAsset` off the shared
monitors so analysis latency cannot reach the main thread.

### 3.4 `MediaAssetStore.writeExact` can spin forever — NEW

`MediaAssetStore.java:255-280`:

```java
while (written < sizeBytes) {
    int read = source.read(buffer);
    if (read < 0) throw new EOFException(…);
    if (read == 0) { Thread.onSpinWait(); continue; }   // :266 — no counter
    …
}
```

Every other zero-read guard in this codebase is bounded:
`FiniteRangeReadService.readExact` throws after 32 (`:245-247`),
`ModernFiniteMediaAnalyzer.isRiffWave` after 16 (`:41-43`),
`FiniteMediaAnalyzer.Reader` after `MAX_ZERO_READS = 16` (`:436`, used `:528-531`).
This one has no counter at all.

It is also the copy loop for Lua staging uploads, executed on a computer thread
while holding the `MediaAssetStore` monitor — and
`HQFiniteMediaServer.tick()` reads store state under `synchronized(this)` from the
main thread. A zero-returning channel therefore becomes an unbounded main-thread
stall. `SeekableByteChannel.read` returning 0 without EOF is rare but not
impossible (interrupted/async channels, some overlay or network filesystems), and
the project's own defensive posture elsewhere says it is considered worth guarding.

**Recommended shape:** copy the existing bounded pattern (counter + `IOException`).
Three lines.

### 3.5 `ATOMIC_MOVE` has no fallback — NEW

`MediaAssetStore.java:118`:

```java
Files.move(part, media, StandardCopyOption.ATOMIC_MOVE);
```

`ATOMIC_MOVE` throws `AtomicMoveNotSupportedException` where the filesystem cannot
guarantee it — a world directory on one mount and storage on another, an overlay or
network filesystem, some Windows configurations. The failure escapes `importAsset`
(`:106`) → `HQMediaStaging.prepareAsset` → `audioPrepareStaged` as a
`LuaException`.

The surrounding catch (`:131-145`) is otherwise careful (it deletes the partially
moved file and releases the reservation), so the code is close to correct — it just
needs the standard two-step: try `ATOMIC_MOVE`, fall back to
`Files.move(part, media)` on `AtomicMoveNotSupportedException`. The class javadoc
(`:34-37`) describes the atomic rename as a design guarantee, so the fallback
should be logged rather than silent.

### 3.6 Resource leaks in the legacy client audio path — NEW

Individually small, collectively a long-session stability risk. All inherited, none
tracked:

- `StreamingAudioSource.streamTS` (`:286-288`) creates a `TSDemuxer` that is never
  released, and `demux(InputStream)` (`TSDemuxer.java:93`) buffers the whole stream
  (§2.4). `TSDemuxer` is not `Closeable`, so there is no idiom to follow — it needs
  one.
- `StreamingAudioSource.java:337` and `:359` —
  `AudioSystem.getAudioInputStream(pcmFmt, ais).readAllBytes()`: the *converted*
  `AudioInputStream` is never closed. The underlying stream is closed in a `finally`,
  the wrapper is not.
- `SharedStreamingGroup.java:195-201` — `forceClose()` calls
  `remove(groupId, this)` only when `running.compareAndSet(true, false)` succeeds.
  If the feed already stopped on its own, the entry stays in `SESSIONS` forever. A
  reconnecting player accumulates one dead group per previous session, each holding
  a bounded queue per speaker position.
- `SharedStreamingGroup.java:181` — `while (!q.offer(copy) && running.get()) sleep(1)`
  is a busy-wait that duplicates blocking-put logic implemented differently
  elsewhere in the same class. Two code paths, two semantics, one operation.
- `SharedStreamingGroup.Session` never times out waiting for `expectedTaps`; a
  speaker that fails to start leaves the session in `SESSIONS` permanently.

**Recommended shape:** one try-with-resources pass over the legacy client audio
path, plus an `idleSince` timestamp swept from
`HQSpeakerClientHandler.onDisconnected`/`stopAll` (`:101-110`). Genuinely M1L work,
but cheap and it removes a class of bug reports.

### 3.7 Static strong registries pin `Level`; the weak key is a documented fallback — NEW (corrected)

`HQSpeakerPeripheral` keeps `private final Level world` (`:42`) and registers
itself in **three static strong** collections at construction:
`COMPUTER_SPEAKERS` (`:36`), `ACTIVE_SPEAKERS` (`:37`), `SOURCE_SPEAKERS` (`:38`,
put at `:119`/`:136`); a fourth lives in the network layer
(`IcyMetaPacket.SPEAKER_REGISTRY`, §4.4). Entries leave only via
`stopLegacyPlayback` (`:210`), `cleanup()`, or the tick-time `getLevel() == null`
sweep — all driven by the provider's `forget`/`forgetLevel`/`clearAll` hooks. So
every live legacy peripheral pins its `Level` strongly from statics.

The provider cache is
`Collections.synchronizedMap(new WeakHashMap<Level, ConcurrentHashMap<BlockPos, composite>>())`
(`:22-23`). Because each composite's graph (`legacy.world` `:42`, `finite.level`
`:75`, staging) **strongly references the very `Level` that keys its entry**, a
weak key can never reclaim a live entry. The code comment at `:20-21` states this
deliberately: "The weak key is only a fallback: cached peripherals themselves
reference their Level, so deterministic Level/server lifecycle hooks must evict
this cache explicitly." So this is a **documented design with explicit eviction**,
not an accidentally defeated guarantee — an earlier draft misdescribed both the
mechanism (inventing a `legacy.composite` back-reference that does not exist) and
the intent (§12).

The residual exposure is eviction *granularity*. `forget` fires from the mixin's
`setRemoved` hook (block removal), `forgetLevel` from `LevelEvent.Unload`
(`HQSpeakerMod:67-71`), `clearAll` from `ServerStoppedEvent` (`:77`). In Minecraft
1.21.1 a chunk **unload** invokes `BlockEntity.onChunkUnloaded()`, not
`setRemoved()`, and upstream CC:T overrides only `setRemoved()`; so an ordinary
chunk unload/reload cycle leaves the position entry — and with it the composite,
legacy peripheral, finite server, staging, and their `Level` reference — resident
until the level unloads. Bounded per level, unbounded per chunk cycle within it.
*Confidence:* the `onChunkUnloaded`/`setRemoved` distinction is inferred from
upstream CC:T plus 1.21.1 lifecycle semantics and is **not** runtime-verified here.

**Recommended shape:** sweep on `ChunkEvent.Unload` (or hook `onChunkUnloaded` in
the mixin) to close the window; optionally key `SOURCE_SPEAKERS` by
`(ServerLevel, BlockPos)` instead of a random UUID so stale entries are findable
without a `cleanup()` call.

### 3.8 `HQAudioStream.decode()` materialises before it bounds — NEW

`HQAudioStream.java:426-432`:

```java
source = decoded.readAllBytes();                                        // :426
…
if (source.length > MAX_DECODED_PCM_BYTES) throw new IOException(…);    // :429
```

**What is *not* wrong** (worth stating, because the input side is well defended):
the encoded payload is capped at `HQSpeakerAudioPacket.MAX_BYTES = 8 MiB` at
construction (`:166`), at encode (`:210`), and — importantly — **at decode, before
allocation**, rejecting an oversized length with a `HQSpeakerMod.warn` and an
empty-array substitute (`:250-255`). `HQAudioStream.startFiniteDecode` re-checks the
same bound (`:292-295`), and `HQSpeakerClientHandler.isPacketSafe` (`:68-80`)
independently validates finiteness, URL length, payload length, and generation. So
this is **not** a remote arbitrary-allocation bug.

**What is wrong** is the ordering of the *output* bound. `MAX_DECODED_PCM_BYTES` is
64 MiB (`:34`) but `readAllBytes()` fully materialises first, so the cap is enforced
only after peak heap has been reached. Amplification is bounded by the 8 MiB input:
a high-compression MP3 expands roughly 10×, so one packet can transiently allocate
~80 MiB before `:429` rejects it. The converters then `readAllBytes()` **again** on
their output, so peak transient usage is roughly 2× the decoded size — on the order
of 150–200 MiB against a documented 64 MiB cap. The header-derived checks at
`:383`/`:387`/`:432` do not prevent this, because they read `samples`/`frames` from
the decoded stream's own header — metadata inside the untrusted payload, not a
measure of what the decoder will emit.

Two structural amplifiers:

- The decode executor is a **JVM-global single-thread executor** (`:38`). Every
  submitted task (`:299`, `DECODER.submit(...)`) captures its `raw` array (up to
  8 MiB) in the closure, so queued work retains its input while it waits.
- The queues feeding it are bounded **per structure, not globally**:
  `MAX_ACTIVE_SPEAKERS = 256` (`HQSpeakerClientHandler.java:36`) ×
  `MAX_FINITE_TRACKS_PER_SPEAKER = 16` (`:38`, enforced `:247`) = up to 4,096 live
  `HQAudioStream` instances. Worst case ~32 GiB of retained input funnelled through
  one thread. Both bounds log a `warn` on drop, so it is not silent — but there is
  no aggregate memory budget, and `pendingDecodes` (`HQAudioStream.java:45`) is a
  per-stream counter with no global counterpart.

**Recommended shape:** enforce the bound incrementally (copy into a growable buffer,
abort past `MAX_DECODED_PCM_BYTES`); decode once rather than twice; add a **global**
decode budget alongside the per-speaker caps; consider a small fixed pool instead of
one global thread. `CommonWavAnalyzer.parse` already demonstrates the correct
incremental pattern in this codebase.

### 3.9 The M1G client is diagnostically silent — NEW

`HQSpeakerClientHandler` (653 lines) logs `HQSpeakerMod.warn` on every drop path:
too many active speakers (`:45`), too many sync groups (`:52`), finite queue full
(`:248`), oversized payload (`HQSpeakerAudioPacket.java:251`), status-send failure
(`:577`), `tickPosition` failure (`:549`).

`HQFiniteMediaClient` (560 lines) contains **exactly one** `HQSpeakerMod.warn`/`log`
call. Every rejection path returns silently: `!packet.sensible()` (`:144`),
`MAX_SESSIONS` (`:145`), `anchorOutsideWindow` (`:185`), the `rendererStarted` latch
(`:295`), `terminal` (`:295`).

This matters more than it looks because of the `rendererStarted` latch
(KI-060/`FACT-AUDIT-011`): `:308` sets `session.rendererStarted = true` before
`SoundManager.play(sound)`, and `:295` blocks any retry. If the sound fails to
become active, the session is permanently unable to render **and says nothing**.
Worse, `SESSIONS` is capped at `MAX_SESSIONS = 128` (`:29`) and `:145` silently
refuses new sessions once it is full — so a client that accumulates zombie sessions
eventually stops playing audio entirely, with no log line to explain why.
`disconnect()` clears everything, so the exposure is one connection's lifetime.

`docs/LUA-API.md` and `docs/README.md` both stress that green CI and accepted Lua
calls are not proof of audibility. A renderer that refuses to start without saying
so is the hardest possible failure to diagnose in the field, and
`docs/M1E-RUNTIME-DIAGNOSTIC` established the diagnostic pattern on the *server*
side without the M1G client ever receiving the equivalent.

**Recommended shape:** add `HQSpeakerMod.warn` at each rejection path with the
session source and reason; add a watchdog on `rendererStarted && !terminal` with no
STATE for N seconds; log (once) when `MAX_SESSIONS` causes a refusal.

---

## 4. API surface, testability, and structure

### 4.1 The Lua surface is ~90 methods; `docs/LUA-API.md` documents ~25 — NEW

`dynamicNames` (`HQSpeakerCompositePeripheral.java:83-87`) is
`legacyMethods.keySet()` (CC:T's `@LuaFunction` scan of `HQSpeakerPeripheral`) plus
`STANDARD`. Counting those declarations plus the six composite-owned `audio*`
methods, the exposed surface on a `"speaker"` peripheral is roughly **90 methods**:
4 standard, 6 composite `audio*`, 7 `FINITE_CONTROLS`, and ~73 legacy methods
including a full `*All`/`*At` cross-product (`speakMp3All`, `speakMp3At`,
`speakWavAll`, `speakHLSAll`, `speakTSAt`, `audioStatusAt`, `getSpeakerCount`,
`getSpeakers`, `getSpeakerPos`, `getPos`, `getPeripheralType`, …).

`docs/LUA-API.md` documents the modern 4 + 6 + 7 + 2 raw-feed functions, then covers
the remaining ~73 in one paragraph: "Older functions such as … still exist in
inherited code." The `*All`/`*At` family, `getSpeakerCount`, `getSpeakers`,
`getSpeakerPos`, `getPos`, and `getPeripheralType` are named nowhere in it.

That is a usability and correctness problem: `getPos()` returns a raw `BlockPos`,
`getSpeakers()` returns `Map.Entry<String,Vec3>[]`, and neither shape is contracted
anywhere — both are implementation-defined and version-fragile.

**Recommended shape:** either enumerate the legacy surface with an explicit
"unstable, M1L removal candidate" label, or shrink it. Given `docs/LUA-API.md`
§"Current implementation/evidence caveat" already tells readers not to trust
unlisted behaviour, shrinking is more consistent with the project's direction.
Either way `getPos`/`getSpeakers`/`getPeripheralType` should not be Lua-visible
without a documented shape.

### 4.2 Test coverage is excellent where it exists and absent where the risk is — NEW

The 2,618 lines of tests cover 20 pure/near-pure classes well:

| Tested | Lines | | Tested | Lines |
|---|---|---|---|---|
| `FiniteMediaAnalyzerTest` | 394 | | `FiniteRangeTransportTest` | 87 |
| `MediaAssetStoreTest` | 304 | | `FinitePlaybackStateMachineTest` | 84 |
| `FiniteRangeReadServiceTest` | 258 | | `FinitePlaybackClockTest` | 83 |
| `CommonWavAnalyzerTest` | 168 | | `FiniteDecodeAnchorSelectorTest` | 77 |
| `FiniteRangeWindowTest` | 166 | | `FiniteRangeValidationTest` | 68 |
| `FinitePcmQueueTest` | 127 | | `HLSPlaylistParserTest` | 58 |
| `ProgressiveWavDecoderTest` | 113 | | `MediaAssetReleaseQueueTest` | 56 |
| `RawFeedLifetimeTest` | 110 | | `FiniteDecodeDescriptorTest` | 47 |
| `FiniteAudioTrackTest` | 100 | | `ProgressiveMp3DecoderTest` | 45 |
| `FiniteEncodedInputStreamTest` | 92 | | `BestEffortProjectionTest` | 41 |
| `CommonWavPcmConverterTest` | 87 | | `MediaStorageLimitsTest` | 28 |
| | | | `FiniteMediaPathTest` | 25 |

`MediaAssetStoreTest` (304 lines) is a real asset — §3.4/§3.5 should be fixable
inside it. `docs/TESTING.md` explains the boundary deliberately and
`FACT-AUDIT-002` correctly flags `ProgressiveMp3DecoderTest` as testing
discard/downmix helpers rather than a real MP3 fixture (45 lines confirms it).

**Untested entirely**, and this is where every §2/§3 finding lives:

- `StreamingAudioSource` (21,717 bytes — the **largest** client audio file) and
  `TSDemuxer` (16,851 bytes) have **zero tests**, while `HLSPlaylistParser` — the
  smallest of the three streaming classes — has 58 lines. §2.4's live-HLS defect
  sits in the untested one.
- `HQSpeakerPeripheral` (60,480 bytes — the **largest** file in the repository),
  `HQSpeakerCompositePeripheral`, `HQFiniteMediaServer`, `HQMediaStaging`,
  `HQSpeakerPeripheralProvider`, `HQSpeakerBlockEntity`.
- `HQFiniteMediaClient`, `HQAudioStream`, `HQSpeakerClientHandler`,
  `FiniteSpeakerSound`, `HQSoundChannelControl`, `FinitePcmAudioStream`,
  `FileFiniteAudioStream`, `SharedStreamingGroup`.
- `ServerMediaAssets`, `ModernFiniteMediaAnalyzer`, `HQSpeakerServerConfig`,
  `FiniteMediaFormat`, `MediaSeekPoint`, `WavLayout`.
- All `network/HQ*Packet` codecs (only `BestEffortProjection` is tested), all
  `mixin/*`, `VS2TransformHelper`, `HQSpeakerMod`, `HQSpeakerRegistry`.

The classes are untestable *as written* because they cannot be instantiated without
Minecraft. **The single highest-value structural change in this repository is
extracting the pure logic**, which also unlocks regression tests for §2.2, §2.3,
§3.3, §3.4, §3.5 and the KI-053/056/057 cluster:

| Extract | From | Enables |
|---|---|---|
| `StreamUrlPolicy` | `HQSpeakerPeripheral.validateStreamUrl` (`:1226-1260`) | Deterministic SSRF/parsing tests; reuse by M3 |
| `MediaStoreLayout` | `MediaAssetStore` move/lock/spin | §2.2, §3.4, §3.5 regression tests |
| `FiniteClientSessionLogic` | `HQFiniteMediaClient.state0/control0/shouldPreloadBytes` | KI-053/056/057/060 regression tests |
| `PeripheralMethodRouter` | `HQSpeakerCompositePeripheral.callMethod` (`:206-254`) | Routing-table tests for STANDARD/FINITE_*/RAW/STREAM |
| `LegacyAudioTables` | `audioTableToPcmBytes` and the repeated `len++` loops | Bound and type-coercion tests |
| `HlsWindowTracker` | `StreamingAudioSource.streamHLS` (`:244-282`) | §2.4 regression test |

Note `audioTableToPcmBytes`'s counting idiom is duplicated at `:308`, `:708`,
`:801`, `:815`, `:988`, `:995` with **two different bounds**: `:308` uses
`SPEAKER_MAX_PCM = 192_000` (`:46`) while the others use
`SPEAKER_MAX_AUDIO_TABLE = 131_072` (`:51`). So `speakPCM` counts up to 192,000
samples and *then* throws `"table too large"` at `:1054` for anything above
131,072 — misleading message, dead bound. Meanwhile `speakMaxSamples()` (`:471`)
returns `SPEAKER_MAX_PCM` (192,000), but `callMethod` intercepts the name and
returns `HQ_RAW_MAX_SAMPLES` (131,072) at `HQSpeakerCompositePeripheral.java:213`.
Two constants for one limit, three different values reported in three places.

### 4.3 Dead and unreferenced code — NEW

Verified by whole-tree grep (counting files that mention the symbol; `1` means only
its own declaration file):

| Item | Size | Status |
|---|---|---|
| `client/FileFiniteAudioStream.java` | 339 lines | **main=1, test=0.** A complete parallel finite-file implementation with its own WAV parser, MP3 pre-scan, and `pcmStats()` diagnostics. Superseded by `FinitePcmAudioStream`/`FiniteEncodedInputStream`. |
| `peripheral/HQSpeakerCluster.java` | 40 lines | **main=1, test=0.** |
| `HQSpeakerBlockEntity.getOrCreatePeripheral()` | — | **Zero callers** (§2.7), which makes `HQSpeakerBlockEntity.getOrCreate()` and the whole `serverTick`/`setRemoved` body unreachable. |
| `FiniteSpeakerSound.updatePosition` | `:38-42` | **Zero callers** (§3.1). |
| `HQSpeakerPeripheral.SPEAKER_MAX_PCM` | `:46` | Superseded by the composite override (§4.2). |
| `pcmStats()` debug helpers | — | Never called. |

`FileFiniteAudioStream` alone is 339 lines of audio-format parsing that CI builds,
packages, and never exercises — and that a future contributor may mistake for the
live path. `docs/FUTURE-CLEANUP.md` tracks `HQAudioStream`'s dual decode path
(KI-003) but not these.

*Corrected from an earlier draft:* `MediaSeekPoint` is **not** dead — main=5, test=3,
populated by `FiniteMediaAnalyzer`'s `SeekIndexBuilder` (`:303`, `:319`).

### 4.4 Packet codecs are untested and use three conventions for optional strings — NEW

Each of the eleven payloads hand-rolls its own `StreamCodec` anonymous class. The
encoding itself is sound and defensively clamped — `HQFiniteMediaStatePacket` writes
`Math.max(1L, p.generation())` (`:36`) and `Math.max(0L, p.anchorOffset())` (`:42`),
and `IcyMetaPacket`'s constructor runs every string field through a `cap(...)` helper
(`:49-53`) against `MAX_FIELD_LEN = 256` (`:38`). What is missing is consistency and
coverage:

- **Three different conventions for an optional/absent string.**
  `HQSpeakerStatusPacket.java:37` uses `buf.writeUtf(packet.error, MAX_ERROR_CHARS)`;
  `HQFiniteMediaStatePacket.java:44` null-coalesces
  (`String e = p.error() == null ? "" : p.error();`); `IcyMetaPacket` caps in the
  constructor. All three mean "possibly-empty string" and none shares a helper.
- **No codec round-trip test anywhere.** Only `BestEffortProjection` is tested in the
  whole `network/` package (§4.2). A `writeX`/`readX` field-order drift — the single
  easiest mistake to make in this layer, and one that produces a
  `DecoderException` at runtime rather than a compile error — would pass CI today.
  These codecs are pure functions over a `ByteBuf` and are the cheapest thing in the
  repository to test.

A shared `PacketStrings.writeOptional/writeCapped` helper plus one round-trip test per
payload would close both gaps for well under a hundred lines.

`IcyMetaPacket.java:34-36` also declares

```java
public static final ConcurrentHashMap<UUID, HQSpeakerPeripheral> SPEAKER_REGISTRY = …
```

a **public static strong registry living in a network packet class**, used by
`handle` (`:85`) to find the target peripheral. That is a fourth static strong
reference path to `HQSpeakerPeripheral` (see §3.7), and the network layer is an
unexpected home for peripheral lookup — `HQSpeakerPeripheral.findBySource` already
exists for exactly this purpose.

`HQSpeakerStopPacket` (`:16-17`) registers its type as
`ResourceLocation.fromNamespaceAndPath(MOD_ID, "stop")`, giving the wire id
`hqspeaker:stop`, while every other id is prefixed (`hq_control`, `hq_status`,
`hq_finite_begin`, `hq_finite_state`, …). Cosmetic, but `hqspeaker:stop` is an
unusually generic id in a shared channel namespace, and renaming it later is a
protocol break — cheaper to fix now.

**Client→server volume is properly bounded**, contrary to what an earlier draft of
this review claimed. Of the eleven registered payloads (`HQSpeakerNetwork.java:17-28`,
and the logged count of 11 at `:30` is correct) four are server-bound:
`IcyMetaPacket`, `HQSpeakerStatusPacket`, `HQFiniteMediaStatusPacket`,
`HQFiniteMediaRangeRequestPacket`. All four are event-driven rather than per-tick:
`HQFiniteMediaClient.report(...)` is called only at READY (`:152`) and ERROR
(`:416`); `HQSpeakerClientHandler.report(...)` (`:570-579`) only on finite
transitions; range requests come from `pump()` (`:397`) and are rate-limited
server-side by `FiniteRangeValidation` (`MAX_OUTSTANDING_REQUESTS_PER_PLAYER = 4`,
`MAX_OUTSTANDING_BYTES_PER_PLAYER = 512 KiB`, `FiniteRangeLimits.java:11-14`) and
tested by `FiniteRangeReadServiceTest`. The only unthrottled server-bound traffic
is ICY, addressed in §3.2.

One real cost remains: `HQFiniteMediaStatusPacket.handle` (`:49-56`) logs at
**INFO** on every STATUS packet with string concatenation including the player name,
source UUID, generation, transition, position, and duration; and
`HQFiniteMediaBeginPacket.handle` (`:98`) does the same on every BEGIN. At two
STATUS packets per session these are bounded, so this is verbosity rather than a
defect — but `HQSpeakerMod.log` is an ungated `LOGGER.info`
(`HQSpeakerMod.java:92`) with no config switch, so a multiplayer server accumulates
them without recourse. `config/HQSpeakerServerConfig.java` would be the natural home
for a `verboseLogging` flag.

---

## 5. Build, packaging, CI, and repository hygiene

### 5.1 CI rebuilds two full NeoForge targets for every documentation commit — NEW

`.github/workflows/build.yml:3-5` triggers on bare `push:` and `pull_request:` with
**no branch filter and no path filter**. Measured from GitHub at review time:

- 392 workflow runs total.
- Last 100 runs: 76 success, **24 failure** — a 24% failure rate that deserves its
  own investigation.
- The last **50 commits** between the recorded source checkpoint `9578323`
  (2026-09-13T19:43Z) and HEAD `2862a87` (2026-09-13T23:37Z) touched **only**
  `AGENTS.md`, `NIMBALYST-BOOTSTRAP.md`, `README.md`, and `docs/*.md` (verified with
  `gh api compare`). Every one triggered two `./gradlew build` runs, two
  jar-verification steps, and two artifact uploads.

Each run does `setup-java` + full dependency resolution + ModDev runtime data
generation + two compilations + 2,618 lines of tests, for a markdown edit.

**Recommended shape:**

```yaml
on:
  push:
    paths-ignore: ['docs/**', '**.md', '.gitignore', 'LICENSE']
  pull_request:
    paths-ignore: ['docs/**', '**.md', '.gitignore', 'LICENSE']
concurrency:
  group: "${{ github.workflow }}-${{ github.ref }}"
  cancel-in-progress: true
```

plus a tiny separate `docs` job (markdown lint / link check) if documentation
gating is wanted. `permissions: contents: read` is already set at `:7-8`. This
should remove the large majority of the 392 runs.

Secondarily:
- Unfiltered `push` + `pull_request` means a PR from a branch in the same repo runs
  the whole matrix **twice** per commit.
- `gradle.properties:2` sets `org.gradle.daemon=false`, committed for everyone.
  That is right for CI (which already passes `--no-daemon`) and wrong for local
  development — move it into the workflow and delete it from `gradle.properties`.
- `gradle/wrapper/gradle-wrapper.properties` has no `distributionSha256Sum`, and
  `.github/` contains only `workflows/` — no `dependabot.yml`. For a build that
  also bundles three LGPL audio libraries, pinning the wrapper checksum is cheap.
- The jar-verification step greps for the literal
  `hqspeaker-1.1.4-1.21.1-neoforge.jar` rather than deriving it from
  `gradle.properties`' `jarVersion`, so a version bump and the assertions can drift.
- Five of those assertions guard `hqspeaker:hq_speaker` resources (§2.7) — a feature
  that cannot be obtained in game.

### 5.2 `maven-publish` is applied with no publication; `mods.toml` metadata is stale — EXTENDS KI-025

- `build.gradle:5` applies `maven-publish`, but there is no
  `publishing { publications { … } }` block and no workflow publishes. Dead plugin.
- `src/main/resources/META-INF/neoforge.mods.toml:3` declares
  `license="LGPL-3.0"` while `LICENSE` is MPL-2.0 (373 lines). Tracked as KI-025.
  Worth adding that this is not cosmetic: `build.gradle:51,57,63` jarJar three LGPL
  audio libraries (`mp3spi:1.9.5.4`, `jlayer:1.0.1.4`, `tritonus-share:0.3.7.4`), so
  the declared licence is what a downstream packager would rely on when reasoning
  about that bundling.
- The same file's `description` (from `:10`) advertises high-quality OGG Vorbis
  playback, packed audio archives, Internet radio streams, ICY metadata,
  HLS/MPEG-TS live audio, and raw PCM feed. Every one is legacy-only per
  `FACT-M1G-002`/`FACT-M1G-011` and `docs/LUA-API.md` — and HLS is broken (§2.4).
  This is the text shown in NeoForge's mod list, the most-read description in the
  project, and it describes the M1B-era feature set. KI-025 tracks only the licence.
- `mp3spi` and `tritonus-share` register `javax.sound.sampled.spi.AudioFileReader`
  services **globally in the JVM**. Bundling an SPI-providing library changes
  `AudioSystem.getAudioInputStream(...)` behaviour for every other mod and JVM
  consumer, not just this one. `docs/FUTURE-CLEANUP.md` correctly says these should
  only be removed after legacy callers are migrated; the global-SPI side effect is
  worth adding to that note as a reason to prioritise it.
- `build.gradle:47` uses `implementation 'cc.tweaked:cc-tweaked-1.21.1-forge:1.120.0'`.
  This is the `-forge` artifact on a NeoForge build, which reads oddly — but
  `docs/SOURCES.md` records the M0-tested runtime as
  `cc-tweaked-1.21.1-forge-1.120.0.jar`, so **compile and tested runtime match**.
  Recording it as consistent rather than as a defect (§9).

### 5.3 `main` is the default branch and 383 commits stale — NEW

`gh api` reports `default_branch = main`, 0 open issues, 15 branches (`main` + 14
`codex/*`). `main` is behind `codex/m1g-progressive-finite-decode` by 383 commits
across 300 files; `docs/SOURCES.md` describes it as the "Untouched fork baseline".

So the branch a visitor lands on does not contain the product, and every document
that says "Active branch: `codex/m1g-progressive-finite-decode`" compensates by
hand. There is no PR-based merge workflow, and 24 of the last 100 CI runs failed on
the working branch with no gate preventing the next commit.

**Recommended shape:** either promote the working branch to `main` (or merge
periodically) so `docs/` can stop carrying branch pointers, or state explicitly in
`README.md` that `main` is a frozen upstream baseline and add branch protection
requiring green CI.

### 5.4 The evidence model is sound but unverifiable from the repository — NEW

Six documents name `957832348eaa6e497282d923f2312c9c7d7c550f` as "the current green
integrated M1G source checkpoint" with CI run `34778546164`, and
`7ec70d4674b237f055d450e1290a652f7c23b65d` with CI `34780519972`.

Verified against GitHub: both commits and both runs **exist**, and the claim that
all 50 commits since `9578323` are documentation-only is **correct**. The evidence
model is sound and is not disputed here.

But HEAD's own run is `34790246835`, mentioned in no document, and a reader must
reconstruct "50 docs-only commits since the recorded checkpoint" by hand — which
needed `gh api`, because the working clone is shallow (`git rev-list --count HEAD`
= 1; `.git/shallow` lists `2862a87` and `d1a5923`).

**Recommended shape:** a short machine-generated `docs/EVIDENCE-CHECKPOINT.md` (or a
CI step appending to `CURRENT-STATE.md`) recording per push: HEAD SHA, whether the
push touched `src/` or `build*`, and run ID/conclusion. Then "current green source
checkpoint" is derivable rather than hand-maintained across six files, and the
docs-only invariant is enforced instead of asserted.

---

## 6. Documentation contradictions and stale markers

The project's documentation discipline is a genuine strength — `AGENTS.md` §"Ask the
owner" and §Evidence are better than most commercial projects. That makes these
inconsistencies more damaging than they would be elsewhere, because agents and
contributors are *instructed* to trust these documents as the current record.

### 6.1 Four current documents disagree on whether KI-051 is decided — NEW

HEAD is the commit titled **"docs: resolve M1G range and loop policy"**. It modified
exactly one file: `docs/KNOWN-ISSUES.md`. At HEAD:

| Document | What it says about KI-051 |
|---|---|
| `docs/KNOWN-ISSUES.md:64-70` (heading: "ordinary loop replay **is selected** but not implemented") | `:66` "**Owner policy selected; source work remains.**" `:68` "At local physical EOF, if authoritative state still says `looping=true`, the client should start the same media again… A normal restart gap is acceptable." Reinforced at `:210`. |
| `docs/CURRENT-STATE.md` §4 | "Looping is deliberately simple in M1G and is not gapless. … the owner selected ordinary replay." |
| `docs/M1G-SCOPE-DECISIONS-2026-09-14.md` §4 | "Looping: ordinary replay, no gapless scope … the simple form of the previously discussed **L4** direction." |
| **`README.md:70`** | "Loop-wrap policy **KI-051 remains an owner choice** between L1 client EOF refresh, L2 server wrap STATE, and L3 client local modulo/restart." |
| **`AGENTS.md:63-71`** (heading: "KI-051 — loop-wrap **owner choice**") | `:65` "**Before implementing loop-wrap behavior, ask the owner to choose:**" … `:71` "**Do not silently choose one.**" |
| **`docs/README.md:17`** | "**KI-051:** owner **must choose** loop-wrap rejoin architecture: L1 client EOF refresh, L2 proactive server wrap STATE, or L3 client local modulo/restart." |

`AGENTS.md` is the first file an agent is told to read, and it instructs the agent to
stop and ask a question the owner answered in the very commit being read.
`docs/README.md` is item 0 of the documentation read order. `README.md` is the
repository's front door. The same commit resolved KI-059 to "fixed delivery radius"
and *did* propagate that to `README.md`, so the miss is specific to KI-051.

There is also label drift: `M1G-SCOPE-DECISIONS` §4 calls the chosen option **L4**,
while `README.md`/`docs/README.md`/`KNOWN-ISSUES.md` frame the choice set as
**L1/L2/L3**, and `VERIFIED-FACTS.md` `FACT-M1G-NEXT-001` still says "Owner choice
remains L1 … L2 … L3". A fourth option name appears in exactly one document.

**Recommended shape:** propagate the resolution into `README.md`, `AGENTS.md`, and
`docs/README.md`; reconcile L1/L2/L3/L4 in one place and have the others link rather
than restate. More generally, the "current open items" list is duplicated across
**five** files plus `docs/NEXT-CHAT-HANDOFF.md`; making `KNOWN-ISSUES.md` the single
source and reducing the others to a link would prevent this class of drift
permanently.

### 6.2 `AGENTS.md` and `docs/README.md` omit five open KIs — NEW

Both list KI-051, KI-053, KI-055, KI-054. Neither lists **KI-056, KI-057, KI-058,
KI-060, KI-061** — which `README.md` and `docs/CURRENT-STATE.md` do list, and three
of which `README.md` calls "the highest-priority cluster". An agent following
`AGENTS.md`'s own read order will not learn that seek cancellation can fail a client
session (KI-056), that STATE can spuriously restart playback (KI-057), or that volume
changes do not affect audibility above 32 blocks (KI-058).

### 6.3 A stale acceptance marker would fail a correct run — NEW

`docs/M0-SMOKE-TEST.md:80` lists an expected log marker:

```
[HQSpeaker] Network registered with 3 payloads.
```

`HQSpeakerNetwork.java:30` actually logs
`"Network registered with M1G protocol v6 and 11 payloads."` — and 11 is correct
(seven client-bound + four server-bound registrations at `:17-28`).

`docs/M0-SMOKE-TEST.md` is marked "Status: completed on 2026-09-09. No second M0
session is required" yet also contains a §"Reusable procedure", so if it is ever
re-run for a regression the tester will look for a marker that cannot appear and may
record a false failure. More broadly, that run predates M1E–M1G entirely, so most of
its observations describe removed code paths — while its *audibility* observation is
still the only runtime evidence that the sounds.json-only mechanism works (§2.7).

**Recommended shape:** add a banner stating which protocol version and payload count
the document applies to, or move it under a `docs/history/` prefix.
`docs/SOURCES.md` already uses a "historical" marker convention well; M0 does not.

### 6.4 CONFIRMS KI-053 / KI-056 / KI-057 / KI-060, with source lines

`docs/ARCHITECTURE.md` and `FACT-M1G-008`/`FACT-M1G-011` describe the M1G client as
the authoritative renderer; all four issues are live defects in exactly that
description, and `FACT-AUDIT-001`/`006`/`007`/`008`/`011` record them precisely. No
inconsistency — recorded so the fix work has exact pointers:

- **KI-053.** `HQFiniteMediaClient.java:180`
  `boolean anchorChanged = !session.anchorReady || packet.anchorOffset() != session.anchorOffset;`
  and `:185` `if (!session.window.anchored() || anchorChanged || anchorOutsideWindow)`.
  Window re-anchoring is driven purely by offset comparison, with no revision input.
- **KI-056.** `:221-222` `session.cancelDecodeEpoch(); session.restartRequested = true;`
  with no `startDecodeEpoch()`; `:194` `session.restartRequested = false;` is the only
  clear and runs on the *next* STATE. Between them, `decoderFailed` (`:243-255`)
  compares only `session.decodeEpoch == epoch`, which still matches the cancelled
  worker.
- **KI-057.** `HQFiniteMediaServer.statePacket` (`:401-410`) recomputes the anchor
  from the current position on every STATE, so an exact WAV anchor legitimately
  changes during ordinary playback and the client reads that as a restart request.
- **KI-060.** `:308` `session.rendererStarted = true;` before
  `SoundManager.play(sound)`, with `:295` the latch that blocks retry and no clearing
  path on failure (§3.9).

**One untracked consequence worth adding to KI-057's scope:** because `anchorChanged`
also drives `restartRequested` consumption at `:194`, a STATE that changes the anchor
for a *legitimate* reason and a SEEK that changes it for a *semantic* reason are
indistinguishable. So the KI-051 loop-wrap choice and the KI-057 reanchor-revision
choice are **coupled**: implementing loop-wrap by re-anchoring to position 0 will look
identical to a seek. `docs/M1G-SCOPE-DECISIONS-2026-09-14.md` §1 already proposes
protocol v7 with an explicit revision; §4's loop decision should be recorded as
*depending on* §1, and currently is not.

### 6.5 The anchor is exactly what the wire carries — RETRACTED

An earlier draft claimed the `Session` constructor discards a richer
`SelectedAnchor` (frame index, skip frames, skip samples) and that STATE therefore
loses information the client re-derives by re-walking MP3 frames. **Both halves
were wrong** (§12). `FiniteDecodeAnchorSelector.Anchor` is
`record Anchor(long offset, double seconds)` (`:5-13`) — two fields; no frame or
skip data exists anywhere to discard — and `HQFiniteMediaStatePacket` carries
*both* of them (`anchorOffset` `:42`, `anchorTime` `:43`). The bare
`FiniteDecodeAnchorSelector.select(metadata, totalBytes, 0.0)` statement at
`HQFiniteMediaServer.java:71` is validation-only in effect and still deserves a
comment saying so, but there is **no protocol information loss and nothing for
protocol v7 to recover**. The client's frame scan from the anchor offset is the E1
conservative pre-roll design (`MP3_PRE_ROLL_SECONDS = 1.0`,
`FiniteDecodeAnchorSelector:5`), not reconstruction of discarded fields.

**No finding.** Recorded here so the retraction is as visible as the claim was.

### 6.6 Per-packet VS2 reflection on the server hot path — NEW

`HQFiniteMediaServer.isRelevant(ServerPlayer)` (`:430-435`) calls `computeWorldPos()`
(`:491-505`) for the speaker and `VS2TransformHelper.lookupWorldPosition(player)` for
the player, on every STATE broadcast, every range projection, every status response,
and every ICY relay. Both route through `VS2TransformHelper`, which does
`Class.forName(...)` **on every call** (`VS2TransformHelper.java:37`) plus up to five
uncached `getMethod` lookups (`:38`, `:63`, `:68`, `:75`, `:85`, `:91`, `:97`) and, as
a last resort, a `getMethods()` array scan (`:102`) — all to obtain one `Vec3`. Only
the *availability* probe is cached (`vs2Available`/`checkedForVS2` at `:10-11`,
`Class.forName` at `:19`); the class and method handles are not.

`getMethod` copies the class's method array and scans it linearly on every call. With
10 players in range and a STATE broadcast per authoritative transition, that is tens
of reflective class/method lookups per second per session, on the server main thread,
in a path whose purpose is to be cheap. `computeWorldPos()`'s result for the speaker
is also invariant between ticks unless the block or its ship moves, yet is recomputed
per packet; and `isRelevant` is called for the *same* player several times within one
`projectToClients`/`sendToRelevant` cycle (`:300`, `:327`, `:345`, `:395`, `:418`).

**Recommended shape:** cache the resolved `Class`/`Method` handles in static finals
(initialised once, `null` on failure), cache the speaker's world position per tick,
and compute each player's projected position once per broadcast rather than once per
relevance test. Pure performance change, no behavioural risk, and it makes the VS2
integration cheap enough to keep in the relevance path.

---

## 7. Smaller items worth folding into existing work

All **NEW** unless noted.

**Concurrency**

- `HQSpeakerPeripheral.java:36-42` mixes fully-qualified
  `java.util.concurrent.ConcurrentHashMap` declarations with normal imports for the
  same types elsewhere in the file. Consistent imports would make the static-state
  inventory readable at a glance — which matters, because static mutable state is the
  root of §2.1 and §3.7.
- `HQFiniteMediaServer` has two STATE projection paths with different relevance
  semantics: `sendStateToRelevant` (`:389`) and `sendState` (`:393`). Easy to call the
  wrong one; worth a comment or a merge.

**Client renderer**

- `HQFiniteMediaClient.applyRendererState` calls
  `HQSoundChannelControl.refreshBlocksVolume()` (`:373`) on every volume change,
  which writes `channel.linearAttenuationDistance`. KI-058 covers the *missing*
  distance update; the fact that `refreshBlocksVolume()` performs a *different*
  distance write from the one KI-058 asks for should be recorded in the same KI, so
  the fix does not add a second conflicting writer.
  `docs/M1G-SCOPE-DECISIONS-2026-09-14.md` §2 already specifies the intended contract
  (fixed HQ radius, not `max(volume,1) * attenuationDistance`) and notes
  `HQSoundChannelControl` exposes the underlying `Channel` — so the design answer
  exists and only the source work remains.
- `FiniteSpeakerSound` (`client/FiniteSpeakerSound.java`) is package-private and 55
  lines; it does **not** override `getSound()`, and neither does the legacy
  `HQSpeakerSound`. Both are consistent with vanilla. (An earlier draft claimed an
  override here; there is none — §12.)

**Storage**

- `MediaAssetStore.release(id)` (`:174-190`) performs `Files.deleteIfExists` under
  `synchronized(this)`, and the periodic retry runs as
  `ServerMediaAssets.tickPendingReleases()` (`:47-51`, `static synchronized`, every
  `RELEASE_RETRY_TICKS`) from `HQSpeakerMod.onServerTick:59` on the main thread,
  reaching `releases.retryPending()` (`:54-60`, synchronized on the queue instance)
  and `Files.deleteIfExists`. Filesystem deletion on the main thread, periodically,
  for every pending release. `docs/M1F-FINALIZATION-2026-09-13.md` explicitly
  accepted "deletion failures remain pending" as M1F scope, so the *retry* is intended;
  the *placement on the main thread* is not discussed.
- `ServerMediaAssets.resolveRootDir` resolves against
  `server.getWorldPath(LevelResource.ROOT)`. On a server whose world directory is
  read-only, or on a filesystem without `FileChannel.tryLock` support, media playback
  fails at store construction — and `ServerMediaAssets.get` swallows the cause
  (`catch (RuntimeException | IOException e) { throw new LuaException("failed to
  initialize media assets"); }`), so the message that distinguishes "read-only
  filesystem" from "already in use" (§2.2) from "permission denied" never reaches the
  log or Lua.
- `HQMediaStaging.attach()` (`:66-75`) tries `mountWritable` twice and then
  `if (location == null) return;` (`:71`) — a silent permanent no-op. Nothing logs it,
  and `mountPath` (`:95-101`) later surfaces it as
  `LuaException("HQ speaker media mount is unavailable")` with no cause. Combined with
  KI-061 (staging dirs never cleaned), the operator sees "mount unavailable" with no
  diagnostic. Recommend `HQSpeakerMod.warn` on the transition to attach-failed.

**Scripts**

- `scripts/hqspeaker.lua` — the shipped in-game module and the "recommended starting
  point" in `docs/LUA-API.md` — builds staged filenames from
  `os.epoch()` + `math.random(1000000)`. CC:T's `math.random` is not strongly seeded
  and `os.epoch()` is millisecond-resolution, so two concurrent programs on one
  computer can collide; the loser's `fs.copy` overwrites the winner's staged bytes and
  both then prepare the same path. Low probability, silently wrong result. `fs.copy`
  is also synchronous on the computer thread, so a large file blocks the program for
  the whole copy — worth documenting next to `hq.playFile`.
- `scripts/m1d_media_analysis_test.lua` expects "29 PASS / 0 FAIL / 1 INFO" against a
  format surface (MP3/MP2/OGG/AIFF/AU/SND/WAV/FLAC/packed/MP4/AAC) that M1G narrowed
  to MP3+WAV. `FACT-AUDIT-003` records this; adding a matching banner to the script
  itself (as the `m1a_*`, `m1c_*`, `m1_player_test.lua`, and `p0_*` scripts already
  have) would close the loop.
- `scripts/hls_stream_test.lua` exercises `speakHLS` — the path with the live-window
  defect in §2.4. Its header should carry a known-limitation note so a partial pass is
  not misread.

---

## 8. Prioritised remediation plan

Ordered by severity × cost-effectiveness, respecting the locked decision not to mix
inherited-legacy removal into M1G.

**Tier 0 — before further M1G source work (small, high value)**

1. **Resolve the KI-051 documentation contradiction** (§6.1) and propagate the missing
   KI-056/057/058/060/061 entries into `AGENTS.md` and `docs/README.md` (§6.2).
   Docs-only, ~1 hour, and it prevents an agent from re-asking a settled question or
   missing the highest-priority cluster.
2. **Add `paths-ignore` + `concurrency` to CI** (§5.1). A few lines; removes the large
   majority of 392 runs.
3. **Bound `MediaAssetStore.writeExact`'s spin** (§3.4) and **add the `ATOMIC_MOVE`
   fallback** (§3.5). Both are three-line changes copying patterns already used in the
   same package, and `MediaAssetStoreTest` (304 lines) is ready to hold the
   regressions.
4. **Reorder `startRaw`/`audioPlayPrepared` to validate before
   `beginReplacingHQ`** (§2.3). Local to `HQSpeakerCompositePeripheral`, no protocol
   impact, and it removes a destructive-on-failure API.
5. **Deduplicate `onIcyMetadata`** (§3.2). One `equals` check removes an unbounded
   Lua event flood.

**Tier 1 — M1G correctness cluster, with the new prerequisite**

6. **Make `closeServer` lock-release unconditional** (§2.2). Highest-severity storage
   bug; a strict escalation of KI-054; bricks media playback for the JVM's lifetime.
7. **Break the main-thread/computer-thread monitor coupling** (§2.1), starting with the
   cheapest step: narrow `tickOwnership()`/`HQFiniteMediaServer.tick()` so the
   per-tick main-thread path does not acquire monitors `callMethod` holds. Then move
   DNS resolution off the monitor.
8. Proceed with **protocol v7 / explicit re-anchor revision** (KI-053/056/057) as
   `M1G-SCOPE-DECISIONS` §1 directs — recording that §4's loop decision *depends on*
   §1 (§6.4) and that STATE currently loses the server's frame-exact anchor (§6.5).
9. **Wire `FiniteSpeakerSound.updatePosition`** (§3.1) and **add M1G client
   diagnostics** (§3.9). Both small; the second makes every other client bug
   diagnosable.

**Tier 2 — surface honesty**

10. **Reconcile the three advertised format lists with the real decoders** (§2.5) and
    **route or reject `playNoteAll`/`playSoundAll`/`playNoteAt`/`playSoundAt`** (§2.6).
    Both are user-visible correctness lies and both are cheap.
11. **Decide the fate of `hqspeaker:hq_speaker`** (§2.7) and align registrations, data
    pack, and the five CI assertions with that decision.
12. **Enforce `MAX_DECODED_PCM_BYTES` incrementally** and add a global decode budget
    (§3.8).

**Tier 3 — structure and hygiene**

13. **Extract the pure logic** per §4.2's table, starting with `FiniteClientSessionLogic`
    and `MediaStoreLayout`, because they unlock regression tests for Tier-1 items 6
    and 8.
14. **Delete dead code** (§4.3): `FileFiniteAudioStream` (339 lines),
    `HQSpeakerCluster`, `getOrCreatePeripheral`/`getOrCreate`, `pcmStats`,
    `SPEAKER_MAX_PCM`.
15. **Add packet-codec round-trip tests**, unify the three optional-string
    conventions, and move `IcyMetaPacket.SPEAKER_REGISTRY` out of the network layer
    (§4.4).
16. **Cache VS2 class/method handles and per-tick positions** (§6.6).
17. **Legacy client resource-leak pass** and `SharedStreamingGroup` session sweep (§3.6).
18. **Live HLS segment tracking** (§2.4) — M3 work, but record it as a KNOWN-ISSUE now
    so `speakHLS` is not described as working.
19. Repository hygiene (§5.2, §5.3, §5.4): remove `maven-publish`, fix `mods.toml`
    licence and description, pin the wrapper checksum, add Dependabot, derive the CI
    jar name from `gradle.properties`, decide the `main`-branch policy, automate the
    evidence checkpoint.

---

## 9. Verified correct — recorded so it is not re-litigated

An audit that lists only problems is not useful. These were checked and are right:

- **Payload threading.** No `.executesOn(HandlerThread.NETWORK)` anywhere, so all
  eleven handlers run on the main thread, and the logged count (11) matches the seven
  client-bound + four server-bound registrations at `HQSpeakerNetwork.java:17-30`.
  Consistent with `FACT-AUDIT-012`. The `Minecraft.getInstance().execute(...)` hops in
  `HQSpeakerClientHandler` (`:257`, `:261`, `:386`, `:462`, `:479`) and
  `context.enqueueWork(...)` in `HQFiniteMediaStatusPacket` (`:49`) are therefore
  redundant rather than racy — they cost a tick of latency on some transitions and
  cannot introduce a threading bug.
- **Composite method routing.** `STANDARD` (`:37`) forwards through CC:T's own
  `getMethodNames()` index, and the cancellable HEAD inject on
  `SpeakerBlockEntity.peripheral()` returns the composite through both the direct call
  and CC:T's `PeripheralLookup`. The mixin's
  `@Shadow(remap = false) @Final private SpeakerPeripheral peripheral` and
  `@Inject(method = "setRemoved", remap = false)` both match upstream, which declares
  `setRemoved()` directly and holds `peripheral` as `private final`. Verified against
  cloned upstream CC:T 1.21.x source.
- **Dynamic-method exposure.** CC:T's `MethodSupplierImpl` merges `@LuaFunction`-
  annotated methods with `IDynamicPeripheral.getMethodNames()` and dispatches the
  latter by index, so the composite's six own `audio*` methods **are** Lua-visible and
  `dynamicNames[method]` (`:208`) is correct. `dynamicNames` is built once in the
  constructor (`:83-87`) and never mutated, and `getMethodNames()` returns
  `dynamicNames.clone()` (`:92`), so the two stay consistent. `STANDARD`'s four names
  already exist in `legacyMethods.keySet()`, so the `LinkedHashSet` collapses them and
  routing goes to vanilla as intended.
- **Standard CC:T semantics** (`playNote`/`playSound`/`playAudio`/`stop`) inherit
  upstream exactly, including the 1.120.0 `pitchA.orElse(1.0)` discrepancy that
  `docs/CC-T-COMPATIBILITY-CONTRACT.md` documents, the jukebox-song exclusion, and
  `clampVolume`. Verified against upstream `SpeakerPeripheral.java`.
- **Client-side input bounds are correct and defensive.** `HQSpeakerAudioPacket` caps
  payload at `MAX_BYTES = 8 MiB` and URL at `MAX_URL_CHARS = 512` at construction
  (`:166-170`), at encode (`:205-210`), and **at decode before allocation**, with a
  warning and an empty-array substitute on violation (`:250-255`).
  `HQSpeakerClientHandler.isPacketSafe` (`:68-80`) independently re-validates
  finiteness, URL length, payload length, and generation.
- **Legacy client structures are bounded and loud.** `MAX_ACTIVE_SPEAKERS = 256`,
  `MAX_SYNC_GROUPS = 128`, `MAX_FINITE_TRACKS_PER_SPEAKER = 16`, each with a
  `HQSpeakerMod.warn` on drop (`:45`, `:52`, `:248`). Sync-group arming (`:166-176`)
  correctly requires all expected members present *and* ready before starting, uses
  `max(maxStartTick, now + 1)` so a group never arms in the past, and `canRemove()`
  (`:145-151`) refuses to collect a group with live members. This is the model the
  M1G client should copy (§3.9).
- **`RawFeedLifetime` is a model for the rest of the codebase.** Pure, `synchronized`,
  documented (`:4-14`), and it tracks outstanding audio **in samples** rather than
  packet count so the producer is paced by actual audio duration
  (`tick(boolean queueHasData)` at `:42-50`, with `SAMPLES_PER_TICK` and
  `GRACE_TICKS`); `drainTicks()` (`:58-60`) rounds up correctly. Tested by
  `RawFeedLifetimeTest` (110 lines). The M1G client's renderer/epoch logic would
  benefit from the same discipline.
- **Server shutdown ordering is correct.** `HQSpeakerMod.onServerStopped` (`:73-83`)
  clears peripheral/composite caches at `:77` — releasing prepared and playback
  references — *before* `ServerMediaAssets.closeServer` at `:79`, with an explanatory
  comment at `:76`. `onLevelUnload` (`:67-71`) additionally evicts Level-keyed
  composites before the Level can go stale and correctly filters on
  `!level.isClientSide`. §2.2 is about consequence handling, not ordering.
- **Server→client packets are authorised.** `HQSpeakerStatusPacket.handle` (`:72-77`)
  requires a `ServerPlayer` and routes through `acceptPlaybackStatus`, which checks
  `speakerSource.equals(packet.source)` plus `canAcceptPlaybackStatus(sender)`
  (`HQSpeakerPeripheral.java:538+`). `IcyMetaPacket.handle` (`:82-89`) requires
  `canAcceptIcyMetadata(sender)` (`:510-515`: stream active, same level, within
  `SPEAKER_RADIUS`). Neither trusts the sender.
- **Status/report traffic is event-driven, not per-tick.** `HQFiniteMediaClient.report`
  fires only at READY (`:152`) and ERROR (`:416`);
  `HQSpeakerClientHandler.report` (`:570-579`) only on finite transitions and guards
  against reporting for an already-dequeued playback.
- **`FiniteRangeWindow` design.** The four-state availability model,
  `TRUE_ASSET_EOF`-only terminal signal, `reset()` clearing `trueEof`, and the
  cancel/notify handoff are correct and tested (`FiniteRangeWindowTest`, 166 lines).
  `probe()` (`:201-217`) already uses `present.nextClearBit(index)` at `:213` — so the
  fix for `nextRequest()`'s linear scan (`:141-144`, over a window up to
  `CLIENT_WINDOW_BYTES = 512 KiB`, called from `pump()` per client tick per session)
  is sitting in the same file, six methods away.
- **`FiniteRangeReadService` accounting** is genuinely defensive: per-player
  request-rate and byte windows (`MAX_OUTSTANDING_REQUESTS_PER_PLAYER = 4`,
  `MAX_OUTSTANDING_BYTES_PER_PLAYER = 512 KiB`), bounded `readExact` zero-read counter
  (`:245-247`), a bounded server IO pool (`SERVER_IO_THREADS = 2`,
  `SERVER_IO_QUEUE = 64`), and a `Closeable` that refuses to close with live accounts.
  Tested by `FiniteRangeReadServiceTest` (258 lines).
- **`FinitePcmQueue`** producer/consumer contract, `drainDiscard` accounting, and
  wakeup are correct and tested (`FinitePcmQueueTest`, 127 lines).
- **`FinitePlaybackStateMachine` / `FinitePlaybackClock`** are a clean, pure, tested
  model of the server timeline (84 + 83 lines), and `BestEffortProjection` is
  deterministic and tested.
- **`CommonWavAnalyzer`** gates to mono/stereo at analysis time
  (`:116`, `"common WAV supports mono or stereo only"`), validates block alignment
  against the representation (`:133-137`), and is tested (168 lines). So the modern
  WAV path cannot reach the decoder with an unsupported channel count.
- **`FiniteMediaAnalyzer`** is careful, incremental, bounded
  (`MAX_ZERO_READS = 16`, `:436`/`:528`), and the best-tested code in the repository
  (394 lines). It rejects MP4 by content rather than trusting the extension
  (`rejectsUnsupportedMp4RatherThanTrustingName`), returns the channel to position 0
  on both success and failure, and builds bounded coarse seek hints even for very long
  Ogg timelines.
- **jarJar version constraints** (`build.gradle:51-67`) use `strictly` + `prefer` with
  an explanatory comment, deliberately avoiding dynamic version-list resolution while
  preserving negotiation metadata. That is better than most mods do.
- **The CC:T dependency matches the tested runtime.** `build.gradle:47`
  (`cc-tweaked-1.21.1-forge:1.120.0`) is the same artifact `docs/SOURCES.md` records
  from the M0 run, and `neoforge.mods.toml:33-35` declares the matching
  `versionRange="[1.120.0,1.121)"`.
- **No `TODO`/`FIXME`/`XXX`/`printStackTrace`/`System.out` in main sources.** Verified
  by grep. `System.err` appears only in `VS2TransformHelper` as a one-time capability
  warning, which is correct.
- **The "50 commits since `9578323` are docs-only" claim** in `docs/README.md` is
  accurate; verified with `gh api compare`.

---

## 10. Appendix — exact pointers

| Finding | File:line |
|---|---|
| Main-thread per-tick monitor acquisition | `HQSpeakerMod.java:58-63`; `HQSpeakerCompositePeripheral.java:101,108`; `HQFiniteMediaServer.java:280` |
| `callMethod` holds the composite monitor on a computer thread | `HQSpeakerCompositePeripheral.java:206`; name sets `:37-46` |
| Blocking DNS under that monitor | `HQSpeakerPeripheral.java:1080` → `:1226` → `:1250` |
| Orphaned root lock | `ServerMediaAssets.java:59-68` (`:64` before `:66`); `FiniteRangeReadService.java:43,177`; `MediaAssetStore.java:174,314,341,363`; warning-only catch `HQSpeakerMod.java:79-82` |
| Ownership transfer before validation | `HQSpeakerCompositePeripheral.java:189,257,261-264,286-301`; `HQFiniteMediaServer.java:124-141` |
| Live HLS monotonic segment index | `StreamingAudioSource.java:57,244,265-278`; hardcoded 5000 ms `:248`; unused `MediaSegment.duration` `HLSPlaylistParser.java:47` |
| Whole-stream TS buffering; `TSDemuxer` not closeable | `StreamingAudioSource.java:285-292`; `TSDemuxer.java:14,64,93` |
| Three contradictory format lists | `HQSpeakerCompositePeripheral.java:46,214`; `HQSpeakerPeripheral.java:475,488-490`; gate at `ModernFiniteMediaAnalyzer.java:18-31` |
| Sine-wave note synthesis ignoring instrument/name | `HQSpeakerPeripheral.java:771-789` (`:776`), `:791-793`, `:277`, `:297`, `:976`, `:981` |
| Non-optional legacy `playNote` args | `HQSpeakerPeripheral.java:277` |
| Block/item unreachable in game | `HQSpeakerRegistry.java`; absent `src/main/resources/data/hqspeaker/**`; absent `assets/hqspeaker/models/block/**`; `HQSpeakerBlockEntity.java:17-32` (zero callers) |
| `updatePosition` never called; position is BEGIN-only on the wire | `client/FiniteSpeakerSound.java:38-42`; legacy analogue `HQSpeakerClientHandler.java:532-551` (call `:546`); BEGIN coords `HQFiniteMediaBeginPacket.java:45-46`; no coords in STATE `HQFiniteMediaStatePacket.java:34-45` |
| ICY event flood, no dedup | `HQSpeakerPeripheral.java:517-536` (event `:535`); source `StreamingAudioSource.java:493-498,583-590`; send `HQAudioStream.java:268-277`, `SharedStreamingGroup.java:117-124`; authz `IcyMetaPacket.java:82-89` |
| Full MP3 frame walk on a computer thread | `FiniteMediaAnalyzer.java:291-320`; `ModernFiniteMediaAnalyzer.java:18-31`; `HQMediaStaging.java:108`; limit in `config/HQSpeakerServerConfig.java` |
| Unbounded zero-read spin | `MediaAssetStore.java:255-280` (spin `:266`); bounded analogues `FiniteRangeReadService.java:245-247`, `ModernFiniteMediaAnalyzer.java:41-43`, `FiniteMediaAnalyzer.java:436,528-531` |
| `ATOMIC_MOVE` without fallback | `MediaAssetStore.java:118` (catch `:131-145`; javadoc `:34-37`) |
| Output cap enforced after materialisation | `HQAudioStream.java:426` then `:429`; cap `:34`; global executor `:38`; submit `:299`; per-structure caps `HQSpeakerClientHandler.java:36,38,247` |
| Correct input-side bound (contrast) | `HQSpeakerAudioPacket.java:166-170,205-210,250-255`; `HQAudioStream.java:292-295` |
| Legacy resource leaks | `StreamingAudioSource.java:337,359`; `SharedStreamingGroup.java:181,195-201` |
| Static strong registries pin `Level`; weak key is a documented fallback, eviction is per-level | `HQSpeakerPeripheral.java:36-38,42,119,136,210`; `HQSpeakerPeripheralProvider.java:20-23,24,34,48,60,71` |
| M1G client diagnostic silence | `HQFiniteMediaClient.java:144,145,185,295,308` (1 log call total); contrast `HQSpeakerClientHandler.java:45,52,248,549,577` |
| `nextRequest` linear scan vs `nextClearBit` idiom | `FiniteRangeWindow.java:136-158` (scan `:141-144`); `probe` `:201-217` (`:213`) |
| Anchor is `(offset, seconds)` and both ride on STATE — retracted, no loss (§6.5) | `FiniteDecodeAnchorSelector.java:5-13`; `HQFiniteMediaStatePacket.java:42-43`; unused validation-only call `HQFiniteMediaServer.java:71` |
| Per-packet VS2 reflection | `HQFiniteMediaServer.java:430-435,491-505`; `VS2TransformHelper.java:10-11,19,37,38,63,68,75,85,91,97,102` |
| Untested codecs; three optional-string conventions; static registry in a packet class | `HQSpeakerStatusPacket.java:37`; `HQFiniteMediaStatePacket.java:36,42,44`; `IcyMetaPacket.java:34-36,38,49-53,85`; no codec test in `src/test/.../network/` beyond `BestEffortProjectionTest` |
| Over-generic wire id | `HQSpeakerStopPacket.java:16-17` (`hqspeaker:stop`) |
| Ungated INFO logging | `HQSpeakerMod.java:92-94`; `HQFiniteMediaStatusPacket.java:49-56`; `HQFiniteMediaBeginPacket.java:98` |
| Stale acceptance marker | `docs/M0-SMOKE-TEST.md:80` vs `HQSpeakerNetwork.java:30` |
| KI-051 contradiction | `README.md`, `AGENTS.md`, `docs/README.md` vs `docs/KNOWN-ISSUES.md:64-70` + `:210`, `docs/CURRENT-STATE.md` §4, `docs/M1G-SCOPE-DECISIONS-2026-09-14.md` §4 |
| CI has no path filter | `.github/workflows/build.yml:3-5`; 392 runs; 76/24 success/failure in the last 100 |
| `main` 383 commits behind | GitHub compare `main...codex/m1g-progressive-finite-decode` |
| Dead code | `client/FileFiniteAudioStream.java` (339 lines, main=1/test=0); `peripheral/HQSpeakerCluster.java` (40 lines, main=1/test=0) |
| Two bounds for one limit | `HQSpeakerPeripheral.java:46,51,308,471,1054`; `HQSpeakerCompositePeripheral.java:49,213` |
| Staged-filename collision | `scripts/hqspeaker.lua` (`os.epoch()` + `math.random`) |

---

## 11. Suggested KNOWN-ISSUES additions

If the owner wants these in the existing register rather than in this document, these
are the items I would add, in §8's priority order:

1. Main-thread/computer-thread monitor coupling with blocking DNS under the composite
   monitor (§2.1).
2. `closeServer` can orphan the `MediaAssetStore` root lock and brick the media root
   for the JVM (§2.2) — or fold into KI-054 with the wider scope.
3. `beginReplacingHQ` destroys current playback before capacity/start validation, so a
   rejected `speakPCM` or failed `audioPlayPrepared` is destructive (§2.3).
4. Live HLS stops after the first playlist window; stop latency is up to half the
   target duration (§2.4).
5. Advertised codec lists contradict the real decoders, `docs/SOURCES.md`, and
   `FiniteMediaAnalyzerTest` (§2.5).
6. `playNoteAll`/`playSoundAll`/`playNoteAt`/`playSoundAt` synthesise a sine and
   ignore instrument/sound name (§2.6).
7. `hqspeaker:hq_speaker` is registered, packaged, CI-verified, and unobtainable (§2.7).
8. `HQAudioStream.decode()` materialises before bounding; no global decode budget (§3.8).
9. `FiniteSpeakerSound.updatePosition` has no caller (§3.1).
10. `onIcyMetadata` queues a Lua event per ICY block with no dedup (§3.2).
11. `HQFiniteMediaClient` is diagnostically silent, including the `MAX_SESSIONS`
    refusal (§3.9).
12. `FiniteRangeWindow.nextRequest` linear scan, with the fix idiom already in
    `probe()` (§10).
13. Static strong registries pin `Level`; the chunk-unload window keeps per-position
    entries (composite + legacy + finite + staging + `Level`) resident until level
    unload (§3.7).
14. `MediaAssetStore.writeExact` unbounded spin; `ATOMIC_MOVE` has no fallback
    (§3.4, §3.5).
15. `FiniteSpeakerSound` position is frozen at BEGIN coordinates; moving-speaker
    support needs either client-side VS2 resolution (legacy `tickPosition` pattern)
    or a new wire position update (§3.1).
16. Per-packet VS2 reflection with uncached `Class.forName`/`getMethod` (§6.6).
17. No packet-codec round-trip tests; three conventions for optional strings; a
    public static peripheral registry inside `IcyMetaPacket` (§4.4).
18. CI has no path filter; `main` is the stale default branch; evidence checkpoints are
    hand-maintained across six files (§5.1, §5.3, §5.4).
19. KI-051 is recorded as both decided and undecided in current documents;
    `AGENTS.md`/`docs/README.md` omit five open KIs (§6.1, §6.2).
20. Dead code: `FileFiniteAudioStream`, `HQSpeakerCluster`,
    `getOrCreatePeripheral`/`getOrCreate`, `pcmStats`, `SPEAKER_MAX_PCM` (§4.3).
21. `StreamingAudioSource` and `TSDemuxer` — the two largest streaming classes — have
    zero tests (§4.2).

Nothing in this document was fixed. No existing file was modified.

---

## 12. Claims an earlier draft made that did not survive verification

Recorded for audit integrity, so that nobody re-derives them from a partial reading.
Each was checked against source and **retracted**:

| Retracted claim | What the source actually shows |
|---|---|
| `HQSpeakerClientHandler.handle` duplicates a `switch (packet.getType())` in a live and a dead `isSameThread()` branch, hiding OGG/PACKED/ICY_META handling | There is no `handle` method, no `switch` on packet type, and no `isSameThread()` test in this class. `HQSpeakerAudioPacket.handle` (`:272-274`) is a one-line forward to `HQSpeakerClientHandler.receive` (`:42-59`), which dispatches by `AudioFormat` through `SpeakerState.push`/`pushFinite`. All formats are handled. |
| A malicious server can make the client allocate an arbitrary `new byte[len]` before validation | `HQSpeakerAudioPacket.java:250-255` validates `len` against `MAX_BYTES` **before** `new byte[len]` at `:257`, warning and substituting an empty array on violation. |
| `IcyMetaPacket` is registered but nothing ever sends it, so ICY metadata is always empty | It is sent from `HQAudioStream.java:268-277` and `SharedStreamingGroup.java:117-124` via fully-qualified constructors. The whole ICY path works and is authorised (§3.2 is about dedup, not reachability). |
| The client sends one STATUS packet per tick per session (up to 128/tick) | `HQFiniteMediaClient.report` is called only at READY (`:152`) and ERROR (`:416`). Status traffic is event-driven. |
| `HQFiniteMediaServer.canPlay` (`:268`) is the modern format gate | No `canPlay` method exists. The gate is `ModernFiniteMediaAnalyzer.analyze` (`:18-31`), applied at prepare time; `playPrepared` (`:118-167`) checks only asset existence and analysed metadata. |
| `playPrepared` accepts multi-channel files and the failure escapes to the client decoder | `CommonWavAnalyzer.java:116` rejects anything outside mono/stereo at **analysis** time, and MP3 cannot exceed two channels. There is no client-side channel failure to escape. |
| `speakMp3`/`speakWav`/`speakFile` read server-side files via `readServerFile`/`Files.walk` | No such methods exist. These take `IArguments` and read the audio as a **Lua table** (`HQSpeakerPeripheral.java:314-327`), bounded by `SPEAKER_MAX_AUDIO = 8 MiB` (`:47`). There is no server-side file walk. |
| `audioMountPath` performs `Files.createDirectories` and a root `tryLock` | `HQMediaStaging.mountPath` (`:95-101`) only reads a binding map and returns a string. The filesystem work is in `prepareAsset` (`:108`). |
| `FiniteSpeakerSound` overrides `getSound()` to `return this`, unlike `HQSpeakerSound` | Neither class overrides `getSound()`. `FiniteSpeakerSound` is 55 lines and overrides only `isStopped`, `tick`, and `getStream`. |
| `MediaAssetStore` has no tests | `MediaAssetStoreTest` is 304 lines — the second-largest test file in the repository. |
| `TSDemuxer` is well tested (187 lines) | `TSDemuxer` has **no test at all**; `src/test/.../client/audio/` contains only `HLSPlaylistParserTest.java`. This became a finding instead (§4.2). |
| `FiniteMediaAnalyzer` has a `parseXingLikeHeader` that requires a `framesPresent` flag, tested by `testParseXingInfoWithoutFramesFlagIsRejected` | No such method or test exists (grep for `xing`/`info`/`VBRI` returns nothing). The absence of a Xing shortcut is the §3.3 finding. `FiniteMediaAnalyzerTest`'s real tests are listed at `:26-178`. |
| `MediaSeekPoint` is declared but never populated | main=5, test=3; populated by `FiniteMediaAnalyzer`'s `SeekIndexBuilder` (`:303`, `:319`) and returned in `MediaMetadata`. |
| `TSDemuxer` is `Closeable` and used in try-with-resources while `StreamingAudioSource` is not | `TSDemuxer` is a plain `public class` (`:14`) implementing nothing; the instance at `StreamingAudioSource.java:288` is never released. |
| `StreamingAudioSource` refreshes on a hardcoded `REFRESH_MILLIS = 3000` | No such constant. Refresh is `now - lastPlaylistFetch > 5000` (`:248`) plus `Thread.sleep(targetDuration * 500)` (`:277`). |
| `HLSPlaylistParser.MediaSegment` exposes `extinf` | The field is `duration` (`HLSPlaylistParser.java:47`), and it is parsed but unused. |
| `build.gradle` applies `maven-publish` at `:24`, jarJar at `:105-107`, the CC:T dep at `:95-96`; `gradle.properties` sets `daemon=false` at `:9`; `mods.toml` declares the licence at `:28` | Actual lines: `maven-publish` `build.gradle:5`, jarJar `:51,57,63`, CC:T `:47`, `useJUnitPlatform()` `:73`; `gradle.properties:2`; `neoforge.mods.toml:3` (licence) and `:10` (description). |
| The compile-only CC:T dependency is a different artifact from the tested runtime | `build.gradle:47` (`cc-tweaked-1.21.1-forge:1.120.0`) matches the runtime jar `docs/SOURCES.md` records. Moved to §9 as verified-correct. |
| The workflow lacks `permissions: contents: read` | It is already present at `.github/workflows/build.yml:7-8`. |
| `FiniteRangeLimits` defines `MAX_PREPARED_AUDIO_BYTES = 512 MiB` | No such constant. `FiniteRangeLimits` defines range/window/IO bounds only; the 512 MiB per-asset ceiling is `maxAssetMiB` in `config/HQSpeakerServerConfig.java`. |
| The repository has 44 main-source classes and 15 test classes | 65 main-source classes and 20 test classes (2,618 lines). |
| `HQSpeakerMod.onServerStopping` calls `MediaAssetReleaseQueue.clearAllForTesting()` before `closeServer`, so `retryPending()` has nothing to drain | No `onServerStopping` and no `clearAllForTesting` exist. The real handler is `onServerStopped` (`ServerStoppedEvent`, `HQSpeakerMod.java:73-83`), and its ordering — `clearAll()` at `:77` then `closeServer()` at `:79` — is **correct and deliberate**, with a comment at `:76`. §2.2 was rewritten around the warning-only catch at `:80-82` instead. |
| `RawFeedLifetime.tick()` decrements a fixed tick count regardless of actual PCM drain, giving wrong `hqspeaker_audio_empty` timing | `tick(boolean queueHasData)` (`RawFeedLifetime.java:42-50`) is explicitly **sample-based** (`outstandingSamples -= SAMPLES_PER_TICK`) with an idle-grace model, and the class javadoc (`:4-14`) documents that design choice. It is tested (110 lines). Moved to §9 as verified-correct. |
| `HQSpeakerPeripheral.playerLock` guards only a timestamp, with `lastPlayerPosition` accessed outside it | `playerLock` is declared at `:62` and used in roughly a dozen `synchronized` blocks (`:243`, `:352`, `:364`, `:377`, `:400`, `:415`, `:432`, `:447`, `:459`, …). The claim was not substantiated and was withdrawn. |
| `RAW_TARGET_TICKS` is the RAW pacing constant | No such constant. The real ones are `RawFeedLifetime.SAMPLES_PER_TICK` and `GRACE_TICKS`. |
| Four packets hand-roll near-identical `Optional`/`OptionalDouble`/`OptionalLong` present-flag framing, so ~60 lines could be removed | **No `Optional` framing exists in any packet.** `HQSpeakerControlPacket` writes `VarLong`/`Enum`/`Double` plainly (`:28-30`); `HQSpeakerStatusPacket` uses `writeUtf(error, MAX_ERROR_CHARS)` (`:37`); `HQFiniteMediaStatePacket` null-coalesces (`:44`) and clamps with `Math.max` (`:36`, `:42`); `IcyMetaPacket` caps in its constructor (`:49-53`). §4.4 was rewritten around the real issue: three different optional-string conventions and zero codec round-trip tests. |
| `docs/KNOWN-ISSUES.md:223-227` records the KI-051 decision | `KNOWN-ISSUES.md` is 211 lines. The KI-051 entry is at `:64-70` and the summary line at `:210`. |

| `HQSpeakerPeripheral` holds a `.composite` back-reference, giving `SOURCE_SPEAKERS -> legacy -> composite -> vanilla`, so the weak cache is "defeated" | No such field exists (`grep composite` in `HQSpeakerPeripheral.java` returns nothing). The real mechanism is that the `WeakHashMap` **value graph references its own key** (`legacy.world` `:42`, `finite.level` `:75`), and the provider comment at `:20-21` documents the weak key as an intentional fallback with explicit eviction. §3.7 rewritten. |
| `HQFiniteMediaServer.tick()` iterates players and projects packets under its monitor every tick, reaching the release queue each tick | The body is six lines (`:280-286`): session read, terminal check, and release-queue reach only on natural end. The periodic deletion retry is `ServerMediaAssets.tickPendingReleases` (`:47-51`), every `RELEASE_RETRY_TICKS`, not every tick. |
| HTTP connections for playlists/segments are not consistently `disconnect()`ed | All checked paths close their streams: `streamMP3`'s `finally` closes `bitstream`/`rawStream` (`:236-238`); `streamTS` (`:288`), `playAACSegment` (`:317-320`) and `HLSPlaylistParser.fetchUrl` (`:197-199`) use try-with-resources. Never calling `disconnect()` is normal when streams are closed. Bullet removed. |
| `MediaAssetReleaseQueue.tickPendingReleases()` runs under a static monitor every tick | The static method is `ServerMediaAssets.tickPendingReleases` (`:47-51`); the queue's `retryPending` (`:54-60`) is instance-synchronized; cadence is `RELEASE_RETRY_TICKS`. |

Several other line numbers in the earlier draft were off by tens of lines because they
were carried from reading notes rather than re-checked; every citation in §1–§11 above
was re-read against the working tree at review time.

---

## 13. Post-review addendum — 2026-09-17

After publication the report was treated as a **claim set to verify**, not as
gospel, by the project owner. This section records the outcome so the document
matches reality as of branch HEAD `73b929294de1393d883799fdad47a78320e18e56`.

### 13.1 Findings confirmed and promoted by the owner

- **KI-062** — §2.1, server-tick / computer-thread monitor coupling with synchronous
  DNS. Confirmed as described; recorded as the most concerning new finding because
  it can freeze the whole server.
- **KI-063** — §2.3, replacement-before-admission destroys valid current playback.
- **KI-064** — §3.4 + §3.5, storage-import zero-read spin and missing
  `ATOMIC_MOVE` fallback.
- **KI-054 upgraded** — §2.2's escalation is accepted: an early range-service
  shutdown failure can leave the media-store root lock alive, not merely skip a
  deletion retry.
- Also confirmed without new KI numbers: the live-HLS window bug (§2.4), the
  misleading legacy format lists (§2.5), and the bogus legacy
  `playNoteAll`/`playSoundAll` behaviour (§2.6).

### 13.2 Claims the owner rejected and my re-verification confirmed were wrong

1. **§6.5 (anchor information loss).** `FiniteDecodeAnchorSelector.Anchor` is
   `record Anchor(long offset, double seconds)`; STATE carries both fields. There
   were never frame/skip fields to discard and nothing for protocol v7 to recover.
   Section rewritten as a retraction.
2. **§3.1 (STATE carries projected positions).** Coordinates exist only in BEGIN
   (`:45-46`); STATE has none. The moving-speaker problem is real but needs a
   position-update mechanism (or client-side VS2 resolution), not a missing call
   against existing STATE data. Section corrected.
3. **§2.1/§3.3 (`audioPrepareStaged` under the composite monitor).** It is a plain
   `@LuaFunction` (`HQSpeakerCompositePeripheral:176-180`), neither `synchronized`
   nor `mainThread`; only `audioPlayPrepared` (`:187-188`) is both. The dangerous
   monitor/I/O combination is the dynamic `synchronized` path reached through
   `callMethod` — the stream calls. Sections corrected; §3.3's severity reduced to
   a computer-thread stall.

### 13.3 Further errors found while re-verifying (neither party had caught them)

4. **§3.7 invented a `legacy.composite` back-reference.** It does not exist. The
   weak-key immortality comes from the value graph referencing its own key `Level`,
   and the provider comment (`:20-21`) documents this as an intentional fallback
   with explicit eviction — so "the weak-reference design is defeated" was the
   wrong framing; the real residual is the chunk-unload eviction window. Rewritten.
5. **§2.1 overstated `HQFiniteMediaServer.tick()`.** Its body is six lines
   (`:280-286`); no per-tick player iteration or projection under the monitor.
6. **§3.6's HTTP-connection bullet** was wrong: every checked path closes its
   streams (`:236-238` finally; `:288`, `:317-320`, `HLSPlaylistParser:197-199`
   try-with-resources). Removed.
7. **§7 misnamed the deletion-retry path** as `MediaAssetReleaseQueue.tickPendingReleases`
   running every tick; it is `ServerMediaAssets.tickPendingReleases`
   (`:47-51`, `static synchronized`) at `RELEASE_RETRY_TICKS` cadence. Corrected.

All seven are logged in §12 alongside the earlier retractions.

### 13.4 Documentation findings are now historical

The owner reconciled the documentation directly (within the audit's permission
scope) in the commits up to `73b9292`: root `README.md`, `docs/README.md`,
`AGENTS.md`, `docs/VERIFIED-FACTS.md`, `docs/KNOWN-ISSUES.md`,
`docs/FUTURE-CLEANUP.md`, and `docs/CURRENT-STATE.md` now record KI-062…KI-064,
include the KI-054 root-lock escalation, drop the stale L1/L2/L3 loop-choice
wording, and guard against the two incorrect audit claims above. **§6.1 and §6.2
therefore describe the tree at `2862a87` and are historical**, kept for the record
of what the audit saw.

### 13.5 Owner meta-claims verified independently

`gh api compare 957832348e…73b929294d` reports **57 commits, 18 files, all
documentation/project-guidance** — no `src/`, test, resource, Lua, Gradle, or CI
implementation file changed. The owner's statement that the implementation remains
exactly at the known-green source checkpoint is confirmed.

### 13.6 One refinement to the owner's framing

For moving speakers, a *wire* position update is not strictly required: the legacy
path resolves VS2 ship transforms **client-side** from the packet's block
coordinates (`HQSpeakerClientHandler.tickPosition`, `:532-551`), and BEGIN already
carries `blockX/blockY/blockZ`. Either mechanism — client-side resolution mirroring
`tickPosition`, or a server-pushed update — closes §3.1; the audit's error was
claiming the data was already on STATE, not in proposing `updatePosition` wiring.

### 13.7 Sequencing

The owner's two options (safety-first: KI-062 + KI-063 + KI-054 hardening before
protocol v7; or M1G-first with KI-062 as a small pre-patch) are a product decision
and are not adjudicated here. §8's remediation order is **superseded** by the
owner's KI-062/063/064 + KI-054-upgraded prioritisation and by the explicit
decision not to mix HLS, legacy `*All` methods, the separate HQ block, CI cleanup,
or legacy decoder cleanup into the M1G batch. §8 is retained only as the original
proposal of record.
