# Entity type definitions

Rust owns the identity and every intrinsic setting of all 239 registered entity
types: [`content/entity`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/content/entity).
Java's `EntityType` objects are projections of those definitions. Java declares
only each type's behavior factory: `register("zombie", Zombie::new)`.

## What a definition holds

- **Identity:** the name (path in the `minecraft` namespace) and, by catalog
  position, the registry ID. Network IDs, save keys, description IDs
  (`entity.minecraft.<name>`) and default loot tables (`entities/<name>`) follow
  from these.
- **Settings:** mob category, serialize/summon/fire-immune/spawn-far/peaceful
  flags, immune blocks, width and height, eye height, attachment points,
  spawn-dimensions scale, client tracking range, update interval, whether the
  type has a loot table, and required feature flags.

Each catalog entry lists only what differs from `base`, the defaults the former
`EntityType.Builder` applied: category-dependent spawn-far, 0.6 x 1.8 size, eye
height at 85% of the height, tracking range 5 and update interval 3. Attachment
kinds without explicit points use Java's fallbacks (passenger and name tag at
the height, vehicle at the feet, Warden chest at half the height). Rust computes
the defaults and fallbacks with the same float arithmetic.

## How Java projects it

[`NativeEntityTypes`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/entity/NativeEntityTypes.java)
reads one table from `mattmc_entity_types_export` when `EntityType` initializes.
`EntityType.register` takes the definition at the next registry ID. If its name
differs or the catalog ID is out of range, registration fails. The separate
Java fixture checks full registry/catalog count equality; the per-entry production
guard does not itself prove that every trailing catalog entry was consumed. It then builds the
`EntityType` with resolved dimensions and attachments. An unknown immune block
or feature flag also fails registration. `EntityType.Builder` no longer exists.

## Working on this slice

- **Add a type:** append its entry to `catalog.rs`, then register it last in
  `EntityType` with its factory. Inserting earlier shifts later registry IDs,
  which changes network IDs; saves use names and are unaffected.
- **Change a setting:** edit only the catalog entry. Rerun the checks below and
  replace the Frozen fixture only for a deliberate, documented divergence.
- **Values are exact.** Floats in the catalog are Java's printed values, which
  round-trip exactly. The checks compare bit patterns, not approximations.

## Checks

```bash
cargo test --manifest-path src/main/rust/Cargo.toml --lib content::entity
./gradlew -PmattmcRustProfile=release test --tests '*NativeEntityTypesTest'
JAVA_HOME=/path/to/jdk-25 python3 DevUtils/tests/content/VerifyEntityTypes.py --output build/entity-type-verification
```

- The Rust test compares every definition, including derived eye heights and
  attachment points, with Frozen's recorded dump
  (`src/test/resources/content/frozen-entity-types.txt.gz`).
- `NativeEntityTypesTest` bootstraps Current and compares the live registry with
  the same dump. It also checks that out-of-order registration is rejected.
- `VerifyEntityTypes.py` compiles
  [`EntityTypeReference`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/content/EntityTypeReference.java)
  once against Frozen's classpath and runs it in alternating fresh JVMs on
  Frozen and Current. The full dumps must be identical. It covers definitions
  only, not gameplay, saving or rendering.

The live driver records source/observer identities but does not fingerprint the
loaded native library or compiled observer. Frozen classes must already exist;
bootstrap timing covers `Bootstrap.bootStrap()`, not isolated entity registration.
Definition equality does not verify factory behavior, simulation, save/load or
rendering lifecycles. Preserve exact-binary guards separately when required.

## Verification (2026-10-13)

The following are implementation-author reports at
[`3423523`](https://github.com/HungLo2020/MattMC/commit/34235237568a44a99ef5ba2cd2c29b5d5a6c6852).
This documentation review inspected source and test definitions without rerunning
those suites, JVM measurements or gameplay captures.

- Pre-change Current and Frozen dumps were identical for all 239 types; the
  catalog was generated from that dump.
- After the change, `VerifyEntityTypes.py` passed against Frozen (five
  alternating pairs, all 239 types identical), as did both Rust tests and both
  Java tests. Changing one tracking range in the catalog made the Rust fixture
  test fail.
- **Bootstrap** (fresh JVM, five alternating pairs, medians): 3,539 ms against
  3,543 ms for pre-change Current (commit `94e748593`), and 3,590 ms against
  3,610 ms for Frozen. Registration runs once at startup; entity type reads stay
  plain Java fields.
- **Gameplay** (small DH fixture, moving camera, three alternating runs per side
  against pre-change Current): every run completed with about 120 live entities
  and the same pre-existing log errors on both sides. Median frame time was
  5.99 ms against 6.13 ms, with 1.4–4.7 GB of swap, so this does not establish a gain or absence of regression under broad
  matched acceptance. This predecessor comparison is not a Frozen gameplay
  matrix or sustained-memory proof. Runs: `artifacts/performance/realworld-20261011/dhab7-*`.
