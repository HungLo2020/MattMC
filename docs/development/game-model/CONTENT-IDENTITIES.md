# Content identities

Rust owns three identity tables that save and network formats depend on:

- **Data component types** ([`content/component`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/content/component)):
  all 97 types in registry ID order, whether each is saved (`persistent`), and
  whether its save encoding is cached.
- **Entity data serializers** ([`content/entity/serializers.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/content/entity/serializers.rs)):
  the 38 serializers in network ID order.
- **Synchronized entity data** ([`content/entity/synched.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/content/entity/synched.rs)):
  every entity class that defines fields, its nearest defining superclass and
  each field's serializer. A class's first ID is one past its base's last.

Java's codecs stay Java bindings: Rust decides identity and order, Java
supplies the codec for each name.

## How Java projects it

[`NativeContentIdentities`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/core/NativeContentIdentities.java)
reads the tables from `mattmc_content_identity_export` (tables 0–2; table 3
holds [item definitions](ITEM-DEFINITIONS.md)). Each check fails registration
instead of drifting:

- `DataComponents.register` takes the type at the next registry ID. A wrong
  name or a persistence mismatch fails, and Rust's flag decides `cacheEncoding()`.
- `EntityDataSerializers` registers in Rust order by field name, and fails on
  missing or extra serializers.
- `SynchedEntityData.defineId` asks Rust for the next ID of the class. The
  serializer, the base class and the field count must match the catalog.
  `ClassTreeIdRegistry` no longer exists.

## Working on it

- **New component type:** append it to `component/catalog.rs` and register it
  last in `DataComponents`. Inserting earlier shifts network IDs.
- **New synchronized field:** add it to the class's entry in `synched.rs` in
  definition order. A new class needs its base, the nearest superclass with fields.

## Checks

```bash
cargo test --manifest-path src/main/rust/Cargo.toml --lib content::
./gradlew -PmattmcRustProfile=release test --tests '*NativeContentIdentitiesTest'
JAVA_HOME=/path/to/jdk-25 python3 DevUtils/tests/content/VerifyContent.py --output build/content-verification
```

[`ContentReference`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/content/ContentReference.java)
dumps every content section. `VerifyContent.py` compiles it once against
Frozen and runs it in alternating fresh JVMs on Frozen and Current, and the
full dumps must be identical. `--sections` limits the sections and
`--reference-repo` compares against another build, such as pre-change Current.
The reference must already be built at the commit you name; see
[native value checks](NATIVE-VALUES.md#checks).
The JUnit test and the Rust test compare against Frozen's recorded dump
(`src/test/resources/content/frozen-content-identities.txt.gz`).

## Verification (2026-10-13)

The source author reports that 97 component types, 38 serializers and 580
synchronized fields match Frozen and pre-change Current row for row
(`VerifyContent.py`, two alternating pairs), with passing Rust and JUnit
fixtures. These are reported results, not reruns by the documentation review.
See the shared [driver and acceptance limits](DATA-DEFINITIONS.md#verification-2026-10-13);
identity equality does not verify live synchronized-value storage or packet dispatch.
