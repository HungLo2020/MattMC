# Tags and registry snapshots

## Tag loading

[`TagLoader`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/tags/TagLoader.java)
reads each tag file's bytes from the resource manager and passes them to
[`content/tags`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/content/tags/mod.rs),
which decodes the files and applies `replace`. It then orders tags as
`DependencySorter` did and plans the element lookups. Java performs those lookups in the planned
order, because a registration-time lookup can create holders, and Rust then
resolves each tag's members. Java logs
`Couldn't load tag … as it is missing following references: …` as before.

Constraints:

- Output and lookup order follow Java `HashMap` iteration, which
  [`core/java_hash.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/core/java_hash.rs)
  reproduces. Tags enter the map through `computeIfAbsent`, which prepends
  within a bucket; `put` appends. Choosing the wrong mode changes binding order.
- A tag that includes itself overflowed Java's stack. Rust reports that case
  and Java throws `StackOverflowError`, so callers fail the same way.
- `DependencySorter` itself stays in Java; `FontManager` still uses it.

## Registry identity snapshot

[`NativeRegistrySnapshot`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/core/NativeRegistrySnapshot.java)
publishes a frozen registry set to [`content/registry`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/content/registry/mod.rs):
each registry's elements by network ID, each element's known pack, and its
tags as ID lists. Two consumers use it:

- `RegistrySynchronization.packRegistries` asks which elements the client
  already has from its known packs; only those elements' keys are sent. It now
  takes the `LayeredRegistryAccess`. Element data is still encoded by the
  Java element codecs.
- `TagNetworkSerialization.serializeTagsToNetwork` takes each network-safe
  registry's tag ID lists from the snapshot.

A snapshot is keyed by a stable access (`LayeredRegistryAccess.compositeAccess()`;
`getAccessFrom` builds a new object per call) and released when that access is
collected. Each publish is a new generation; queries against a retired one fail
instead of returning stale IDs. `MappedRegistry.refreshTagsInHolders` reports
tag rebinds. The next query walks only the rebound registries and publishes a
delta: registries whose tags did not change are skipped, and changed ones refer
to the previous generation's members and names wherever those are unchanged.
Elements and IDs carry over in Rust. Java keeps decoded answers (known masks
per registry set; tag maps until their registry changes), reusing a tag's list
only when it still matches Rust's members.

Errors stay where Java raised them: a registry with unbound tags, or a tag
holding an unregistered value, fails only when its network tags are requested,
with Java's message.

The payload map's iteration order is Java's: tag names are inserted in
`getTags()` order. That order comes from `TagKey` hashes, and `ResourceKey`
uses identity hash codes, so the wire order of colliding tags varies between
JVM runs in Frozen too. Compare payload contents across runs, and check order
against Java's algorithm in the same JVM.

## Troubleshooting

- *`Registry snapshot has no registry`*: the access passed was not the one the
  registries came from. Pass `compositeAccess()` of the same layered access.
- *`Registry snapshot of … is stale`*: a registry's size changed after its set
  was published. Registries must be frozen before synchronization.
