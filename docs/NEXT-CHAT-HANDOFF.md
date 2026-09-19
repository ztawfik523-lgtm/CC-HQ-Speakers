# CC:HQ Speakers — next-chat handoff

Updated: 2026-09-19

Use the full dated handoff: `HANDOFF-2026-09-19-M1H-START.md`

Repository: `ztawfik523-lgtm/CC-HQ-Speakers`

Completed-hardening branch: `codex/post-m1g-hardening`

Final post-M1G hardening source checkpoint: `3d30ce4564de749f32171666df65de739b08ad77`

Latest full verification: CI `35406595856` — PASS on NeoForge 21.1.247 and 21.1.248, including tests, packaged-mod verification, and artifacts.

M1G is closed, including focused NeoForge 21.1.247 audible/core runtime acceptance.

KI-062, KI-063, KI-054, and KI-064 are closed.

The next active milestone is **M1H**. Start with listener membership: late entry, proactive leave, return/rejoin at current server time, and disconnect/dimension pruning. Do not start with VS2.

Before source changes, create `codex/m1h-listener-lifecycle` from the current `codex/post-m1g-hardening` head after verifying it contains `3d30ce...`.

Ready-to-paste prompt: `NEXT-CHAT-PROMPT.md`

Do not reopen completed M1G/hardening work without a concrete regression.
