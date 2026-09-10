# Testing

## Rule

Prefer deterministic tests first, then batch real Minecraft acceptance.

A green Gradle build proves compilation/tests/package structure. It does not prove audible behavior, client renderer lifecycle, packet range behavior, or SoundEngine integration.

## Target matrix

Every source change intended for release must build/test on:

- NeoForge 21.1.247
- NeoForge 21.1.248
- Java 21
- CC:Tweaked 1.120.0

CI already runs both NeoForge builds.

## Test layers

### 1. Pure/unit

Use for:

- cursor/frame math;
- parser behavior;
- protocol validation;
- queue/state-machine transitions;
- generation/anchor logic once extracted;
- cancellation/backpressure policies.

### 2. Component/state-machine

Prefer small Java components over tests which need a full Minecraft client.

The highest-value future extractions are:

- raw buffer/backpressure state;
- finite server semantic queue/authority;
- sync-group membership;
- stream session state.

Do not refactor just to produce abstractions with no behavior benefit, but extracting state logic is justified when it turns a known race/lifecycle bug into a deterministic test.

### 3. Lua/Minecraft acceptance

Use actual CC:T peripherals for:

- standard method signatures/defaults;
- `speaker_audio_empty`;
- actual sounds/notes;
- SoundEngine pause/volume;
- F3+T;
- range movement;
- multi-client rendering;
- VS2;
- stream networking;
- shutdown/reconnect.

## P0

See `P0-TEST-MATRIX.md`.

New P0 runtime scripts intentionally describe the desired contract even when reviewed M1 currently fails it.

Do not "fix" a test by weakening a standard CC:T guarantee.

## Evidence recording

For a real-client acceptance run record:

- exact commit;
- JAR SHA-256;
- NeoForge/CC:T versions;
- pass/fail by section;
- relevant client/server log excerpt;
- whether the full ATM10 pack or a reduced exact-stack instance was used.

A failed broad test should produce a focused source diagnosis before another broad launch.
