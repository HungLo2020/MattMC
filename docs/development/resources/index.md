# Resource packs and content registries

Bundled pack access, resource-stack resolution and registry identity snapshots
now have Rust owners
([issue #771](https://github.com/HungLo2020/MattMC/issues/771)). Rust owns pack
file access, `pack.mcmeta` format and compatibility rules, pack discovery and
selection order, the resource manager's stacks and filters, tag resolution, and
the registry identity snapshot used for client synchronization. The client
resource reload's texture atlases and block/item model geometry are also built
in Rust at
[`71762d60`](https://github.com/HungLo2020/MattMC/commit/71762d602195e0cc5ca29006a2bed1603b53eab8).
Java keeps the `PackResources`/`ResourceManager` API that callers use,
metadata section codecs, element codecs, item definitions, the baked model and
atlas objects its consumers use, reload orchestration and the registries' Java
objects. Filter regex matching and planned element lookups still invoke Java.
Identity snapshots support synchronization; they are not authoritative typed
content registries or a shared model/atlas/mesh reload generation.

- [Pack access and repository](PACKS.md): directory, ZIP and vanilla packs,
  `pack.mcmeta` formats and overlays, discovery and selection.
- [Resource manager](RESOURCE-MANAGER.md): namespace stacks, filters, sidecar
  metadata, lifetimes and reload behavior.
- [Tags and registry snapshots](TAGS-AND-REGISTRIES.md): tag loading and
  resolution, and the identity snapshot behind registry and tag sync.
- [Texture atlases](ATLASES.md): sprite sources, PNG decoding, Sodium
  preprocessing, stitching and mip levels.
- [Block and item models](MODELS.md): model and blockstate files, discovery,
  texture resolution and baking.
- [Recipe-crafted advancements](RECIPE-ADVANCEMENTS.md): loaded recipe keys,
  stable criterion names and headless output/award verification.
- [Verification](VERIFICATION.md): the parity and performance drivers, what
  they cover and what they do not.

## Where the code lives

| Subsystem | Rust | Java view |
| --- | --- | --- |
| Pack content access | [`assets/packs/pack.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/assets/packs/pack.rs), [`zip.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/assets/packs/zip.rs) | [`NativePackResources`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/server/packs/NativePackResources.java), [`VanillaPackResources`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/server/packs/VanillaPackResources.java) |
| `pack.mcmeta` formats, overlays, compatibility | [`assets/packs/metadata.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/assets/packs/metadata.rs) | [`NativePackFormats`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/server/packs/metadata/pack/NativePackFormats.java) |
| Discovery and selection | [`assets/repository.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/assets/repository.rs) | [`FolderRepositorySource`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/server/packs/repository/FolderRepositorySource.java), `PackRepository`, `Pack.Position` |
| Resource manager | [`assets/resources/manager.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/assets/resources/manager.rs) | [`MultiPackResourceManager`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/server/packs/resources/MultiPackResourceManager.java) |
| Tags | [`content/tags/`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/content/tags/mod.rs) | [`TagLoader`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/tags/TagLoader.java), [`NativeTags`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/tags/NativeTags.java) |
| Texture atlases | [`assets/textures/`](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/src/main/rust/assets/textures/mod.rs) | [`SpriteLoader`](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/src/main/java/net/minecraft/client/renderer/texture/SpriteLoader.java), [`NativeAtlasBuild`](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/src/main/java/net/minecraft/client/renderer/texture/NativeAtlasBuild.java) |
| Models | [`assets/models/`](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/src/main/rust/assets/models/mod.rs) | [`ModelManager`](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/src/main/java/net/minecraft/client/resources/model/ModelManager.java), [`NativeModels`](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/src/main/java/net/minecraft/client/resources/model/NativeModels.java) |
| Registry identity | [`content/registry/`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/content/registry/mod.rs) | [`NativeRegistrySnapshot`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/core/NativeRegistrySnapshot.java) |
| Java behavior helpers | [`core/id.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/core/id.rs), [`core/java_hash.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/core/java_hash.rs), [`core/java_log.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/core/java_log.rs), [`core/json.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/core/json.rs), [`core/dfu.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/core/dfu.rs), [`core/parallel.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/core/parallel.rs) | [`NativeLog`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/util/NativeLog.java) |

`PathPackResources`, `FilePackResources`, `CompositePackResources`,
`AbstractPackResources`, `FallbackResourceManager` and `PackDetector` were
removed. Bundled pack implementations use `NativePackResources` (including
its vanilla subclass). `MultiPackResourceManager` explicitly casts each input
to that type; an arbitrary implementation of `PackResources` is not adapted
through a generic fallback. See [manager boundaries](RESOURCE-MANAGER.md)
before introducing a custom producer.

The atlas/model handoff additionally admits only the native manager, a
reloadable manager wrapping it, or the empty manager. Custom manager wrappers
are not adapted; see [native admission](RESOURCE-MANAGER.md#native-atlas-and-model-admission).
Atlas pixels and model records are copied into Java-owned objects before the
temporary native build/session is released. Java still orchestrates reloads,
item-model baking and publication, and retained sprite wrappers do not keep
closed images alive across reloads.

The Java atlas pipeline (`Stitcher`, `MipmapGenerator`, the `texture/atlas`
sprite sources) and model pipeline (`BlockModel`, `BlockElement*`,
`FaceBakery`, `ItemModelGenerator`, `ModelDiscovery`, `BlockStateModelLoader`,
blockstate variants and multipart conditions) were removed.

The resource-stack [verification record](VERIFICATION.md#latest-run) belongs to uncommitted
work on `437e2bc1` with library `05bde869`, not a fresh runtime acceptance of
integrated source `398a0078`. Its warm ratio-of-medians gate permits up to 10%
regression and leaves cold retag ungated; it does not certify live reload/UI,
server connection or custom packs.

See the bounded [#771 source review](https://github.com/HungLo2020/MattMC/issues/771#issuecomment-6112078929);
resource migration and acceptance remain open. The separate
[atlas](VERIFICATION.md#atlases) and [model](VERIFICATION.md#models) reports
cover CPU preparation and timing. Their drivers compare semantics with the
last-Java Reference and use Frozen for timing; they do not supply fresh GPU,
live-reload or custom-producer acceptance.

## Tests

```sh
# Rust unit tests for these subsystems
(cd src/main/rust && cargo test --lib -- assets:: content::tags content::registry core::)

# Java tests that use native packs, tags and the registry snapshot
./gradlew -PmattmcRustProfile=release test -x testRustNative \
  --tests 'net.minecraft.core.NativeRegistrySnapshotTest' \
  --tests 'net.vulkanic.world.*Resource*' --tests 'net.vulkanic.world.SemanticTexturePackPrecedenceTest'

# Parity and performance (see Verification)
python3 DevUtils/tests/resources/VerifyResourceStacks.py --output build/resource-stack --pairs 5
python3 DevUtils/tests/textures/VerifyAtlases.py --output build/atlas-verification
python3 DevUtils/tests/models/VerifyModels.py --output build/model-verification
```

See [testing setup](../tooling/TESTING.md) for the JDK and native build the
Java tests need.
