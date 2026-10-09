# Native block state policy

> Implemented. Release build, 31 native content tests, 60 focused Java
> regressions and five Frozen observer pairs pass. Full suites, lifecycle and
> reviewed image checks pass; vanilla still misses the performance floor.
> No isolated speedup is established.

[`definitions/policy.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/content/block/definitions/policy.rs)
owns random-tick eligibility, use of light-occlusion shapes, leaf classification
and block-entity markers for all 31,809 registered states. The ordered catalog
selects 25 shared typed rule sets. They reuse native physical settings, property
domains and canonical fluids; they do not inspect Java classes or resource names
at runtime.

Rules preserve mature-crop limits, lower/upper pitcher halves, leaf distance and
persistence, redstone ore lighting, bamboo stage, copper oxidation eligibility,
slab type and piston extension. Torchflower's maximum age remains 2 despite its
registered age domain ending at 1.

## Boundaries and lifetime

The definition registry computes one immutable byte per state during startup.
Its schema is version 5; CPU buffer selector 15 borrows that process-lifetime
column. Java validates its bounds and projects it into registered `BlockState`
views without per-query downcalls. Generic state constructors retain their
Java callbacks for unregistered and codec-created states.

Registry install format 9 no longer imports tick, leaf, block-entity or
light-empty-shape flags. Rust derives them alongside physical and fluid flags;
packets supplying those bits are rejected. Java still supplies motion/solid/custom
flags, occlusion faces, blocked light and the face truth table.

This is eligibility and classification, not the tick scheduler, crop/copper
world updates, block-entity storage or shape generation. Those owners remain
unfinished. Keep state-only rules independent of world, rendering and GPU owners.

## Editing and checking

Add explicit catalog policy bindings when adding blocks. Append identities
without changing existing state ranges, and use typed properties for rules.
Rebuild Java and Rust together after changing the definition schema or registry
install format; this schema is separate from the rendering ABI.

The binary test fixture records untouched Frozen's four public policy answers
in state-ID order. The native prototype matches every byte; four staged Java
adapters compile and 32 standalone content/core tests pass. Reference/provenance:
`build/block-policy-migration-draft/`. The integrated v10 observer matches all ten content digests against Frozen;
`build/map-policy-rejection-contract-verification-20261008/results.json` records the current
map/state-policy/glyph sources,
native-library integrity and unchanged reference identity. All ten digests match
in five fresh pairs. Combined bootstrap allocation is960.26 vs1067.78 MB
(~10.1% lower);bootstrap time is2.303 vs2.277s (~1.1% higher). These do not
isolate this policy slice or establish full-client performance. The final full workflow passes1,725 Java/2,389 Rust tests, all seven lifecycle
cases and reviewed vanilla/Iris+DH images. Overall acceptance remains failed
because vanilla misses the FPS/p99 floor. Shader+DH passes a fresh clean recheck;
DH timing is variable. See
[the current performance record](https://github.com/HungLo2020/MattMC/blob/master/SUMMARY.md).

Run `cargo test --manifest-path src/main/rust/Cargo.toml --locked --lib content::block`
and release Gradle tests for `NativeBlockPolicyTest` and `NativeBlockRegistryTest`.
The former also checks generic Java state construction. The v10
`DevUtils/tests/content/VerifyStateGraphs.py` observer adds the policy digest to
nine earlier content digests. Before publishing, run the normal
[real client and performance workflow](../rendering/RENDER-VERIFICATION.md).
