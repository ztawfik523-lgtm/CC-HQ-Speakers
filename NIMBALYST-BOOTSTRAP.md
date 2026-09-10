# Nimbalyst bootstrap

Recommended local folder:
`D:\Games\Codex\CC-HQ-Speakers-repo`

Recommended first branch:
`codex/bootstrap-atm10-player`

Create a brand-new Nimbalyst project pointed at this repository root. Do not reuse the old HighAudio project memory.

Project context should remember:
- fork improvement, not clean-room rewrite;
- user priorities: larger media, real player controls, reliable loop, SPR;
- preserve codecs/streaming/old Lua compatibility;
- exact stack: MC 1.21.1 / Java 21 / CC:T 1.120.0 / NF 21.1.247 + .248;
- existing SPR V7.1 work is prior evidence;
- reuse HighAudio facts selectively, not its architecture wholesale;
- batch manual tests.


Before treating the imported docs as canonical, the bootstrap agent must verify
source-grounded claims against the checked-out baseline and correct the docs if
the local source differs. The docs are a starting knowledge pack, not a license
to override the repository.

First substantial task:
1. inspect inherited source;
2. verify/adapt this doc pack against actual code;
3. update exact target dependencies;
4. clean repo hygiene in a separate change;
5. establish .247/.248 build validation;
6. do not redesign the player yet;
7. produce a baseline JAR and one consolidated smoke-test plan.
