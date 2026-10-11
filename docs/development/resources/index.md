# Resource packs and content registries

Resource-pack loading and content-registry identity run in Rust
([issue #771](https://github.com/HungLo2020/MattMC/issues/771)). Rust owns pack
file access, `pack.mcmeta` format and compatibility rules, pack discovery and
selection order, the resource manager's stacks and filters, tag resolution, and
the registry identity snapshot used for client synchronization. Java keeps the
`PackResources`/`ResourceManager` API that callers use, metadata section codecs,
element codecs, model baking, and the registries' Java objects.

- [Pack access and repository](PACKS.md): directory, ZIP and vanilla packs,
  `pack.mcmeta` formats and overlays, discovery and selection.
- [Resource manager](RESOURCE-MANAGER.md): namespace stacks, filters, sidecar
  metadata, lifetimes and reload behavior.
- [Tags and registry snapshots](TAGS-AND-REGISTRIES.md): tag loading and
  resolution, and the identity snapshot behind registry and tag sync.
- [Verification](VERIFICATION.md): the Frozen parity and performance driver,
  what it covers and what it does not.

## Where the code lives

| Subsystem | Rust | Java view |
| --- | --- | --- |
| Pack content access | [`assets/packs/pack.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/assets/packs/pack.rs), [`zip.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/assets/packs/zip.rs) | [`NativePackResources`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/server/packs/NativePackResources.java), [`VanillaPackResources`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/server/packs/VanillaPackResources.java) |
| `pack.mcmeta` formats, overlays, compatibility | [`assets/packs/metadata.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/assets/packs/metadata.rs) | [`NativePackFormats`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/server/packs/metadata/pack/NativePackFormats.java) |
| Discovery and selection | [`assets/repository.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/assets/repository.rs) | [`FolderRepositorySource`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/server/packs/repository/FolderRepositorySource.java), `PackRepository`, `Pack.Position` |
| Resource manager | [`assets/resources/manager.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/assets/resources/manager.rs) | [`MultiPackResourceManager`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/server/packs/resources/MultiPackResourceManager.java) |
| Tags | [`content/tags/`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/content/tags/mod.rs) | [`TagLoader`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/tags/TagLoader.java), [`NativeTags`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/tags/NativeTags.java) |
| Registry identity | [`content/registry/`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/content/registry/mod.rs) | [`NativeRegistrySnapshot`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/core/NativeRegistrySnapshot.java) |
| Java behavior helpers | [`core/id.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/core/id.rs), [`core/java_hash.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/core/java_hash.rs), [`core/java_log.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/core/java_log.rs) | [`NativeLog`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/util/NativeLog.java) |

`PathPackResources`, `FilePackResources`, `CompositePackResources`,
`AbstractPackResources`, `FallbackResourceManager` and `PackDetector` were
removed. `NativePackResources` is the only `PackResources` implementation;
`MultiPackResourceManager` requires it.

## Tests

```sh
# Rust unit tests for these subsystems
(cd src/main/rust && cargo test --lib -- assets:: content::tags content::registry core::id core::java_hash)

# Java tests that use native packs, tags and the registry snapshot
./gradlew -PmattmcRustProfile=release test -x testRustNative \
  --tests 'net.minecraft.core.NativeRegistrySnapshotTest' \
  --tests 'net.vulkanic.world.*Resource*' --tests 'net.vulkanic.world.SemanticTexturePackPrecedenceTest'

# Frozen parity and performance (see Verification)
python3 DevUtils/tests/resources/VerifyResourceStacks.py --output build/resource-stack --pairs 5
```

See [testing setup](../tooling/TESTING.md) for the JDK and native build the
Java tests need.
