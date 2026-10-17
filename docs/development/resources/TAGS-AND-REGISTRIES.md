# Tags and registry snapshots

Tag loading and the original snapshot references retain the `398a0078` source
pin; the native member-view changes below were reviewed at `f92ea29d`. Java
retains registry objects, holders, element values and their codecs; Rust owns
tag parsing and resolution plans and the published identity/tag data used by
synchronization. See [verification](VERIFICATION.md) for the separate
author-reported checkpoints and their limits.

## Tag loading

[`TagLoader`](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/main/java/net/minecraft/tags/TagLoader.java#L39-L100)
reads each tag file's bytes from the resource manager and passes them to
[`content/tags`](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/main/rust/content/tags/mod.rs),
which decodes the files and applies `replace`. It then orders tags as
`DependencySorter` did and plans the element lookups. Java performs those lookups in the planned
order, because a registration-time lookup can create holders, and Rust then
resolves the member lookup indices. Java reconstructs the value lists, removes
duplicate values in encounter order, and logs
`Couldn't load tag … as it is missing following references: …` for omitted
tags with missing required references.

The parsed files and plan live behind a native handle owned by `TagLoader.Loaded`.
Its Cleaner releases the handle when the wrapper becomes reclaimable;
[planning and resolution do not consume it](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/main/rust/content/tags/ffi.rs#L23-L124).
This is garbage-collection-driven cleanup, not an explicit close at the end of
`build`. Native result buffers are copied into Java and freed by
[`NativeTags`](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/main/java/net/minecraft/tags/NativeTags.java#L39-L45).

Constraints:

- Output and lookup order use the Java `HashMap` bucket-order model in
  [`core/java_hash.rs`](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/main/rust/core/java_hash.rs#L1-L10).
  Tags enter the map through `computeIfAbsent`, which prepends within a bucket;
  `put` appends. Choosing the wrong mode changes binding order. The helper does
  not model buckets converted to collision trees, so this is not an arbitrary
  identifier/collision compatibility guarantee.
- A self-reference alone fails resolution as a missing reference. A dependent
  tag can trigger Java's endless cycle-check recursion depending on map order;
  Rust detects that case and Java throws `StackOverflowError`. The
  [focused fixture](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/main/rust/content/tags/tests.rs#L55-L69)
  distinguishes these cases.
- `DependencySorter` itself stays in Java; `FontManager` still uses it.

## Registry identity snapshot

[`NativeRegistrySnapshot`](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/main/java/net/minecraft/core/NativeRegistrySnapshot.java#L333-L411)
publishes a registry set lazily on its first query to
[`content/registry`](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/main/rust/content/registry/mod.rs):
each registry's elements by network ID, each element's known pack, and its
tags as ID lists. Callers must keep the element set frozen; the snapshot copies
Java's IDs and registration data rather than taking ownership of live gameplay
objects. Two synchronization consumers use it:

- [`RegistrySynchronization.packRegistries`](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/main/java/net/minecraft/core/RegistrySynchronization.java#L28-L74) asks which elements the client
  already has from its known packs; only those elements' keys are sent. It now
  takes the `LayeredRegistryAccess`. Element data is still encoded by the
  Java element codecs.
- [`TagNetworkSerialization.serializeTagsToNetwork`](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/main/java/net/minecraft/tags/TagNetworkSerialization.java#L20-L48) takes each network-safe
  registry's tag ID lists from the snapshot. Java still writes the payload and
  resolves received IDs back to holders.

A snapshot is keyed by a stable access:
[`LayeredRegistryAccess.compositeAccess()`](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/main/java/net/minecraft/core/LayeredRegistryAccess.java#L50-L85)
returns the stored object, while `getAccessFrom` builds a new one per call.
Java's weak-key cache and Cleaner tie the native context to that access's
lifetime; cleanup is not an explicit reload/close operation. Each actual
publish creates a new generation. Rust rejects queries naming a retired
generation, and shares unchanged element/tag data between generations through
[`Arc` ownership](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/main/rust/content/registry/mod.rs#L103-L161).

[`MappedRegistry.refreshTagsInHolders`](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/main/java/net/minecraft/core/MappedRegistry.java#L352-L363)
reports tag rebinds. The next query checks registry versions and walks the tags
only of rebound registries. An identical rebind publishes nothing; a changed
registry publishes a delta referring to previous names and member lists where
possible. Elements and IDs carry over in Rust.

Java keeps known-pack masks per registry and client known-pack set, and tag maps
until that registry's published tags change. Known masks are shared arrays that
callers must not mutate.

The network consumer now reads immutable Rust member arrays directly via
[`NativeRegistryTagView`](https://github.com/HungLo2020/MattMC/blob/f92ea29dde0199a9ea65006e216ac92570b3b02b/src/main/java/net/minecraft/core/NativeRegistryTagView.java).
[`tag_view.rs`](https://github.com/HungLo2020/MattMC/blob/f92ea29dde0199a9ea65006e216ac92570b3b02b/src/main/rust/content/registry/tag_view.rs)
pins the registry's published tag set behind one CPU lease per nonempty payload
map and exports 16-byte pointer/count descriptors. An empty tag-name set returns
`Map.of()` without acquiring a lease. Java builds an unmodifiable map of names
and read-only `IntList` views instead of reconstructing a flattened result
buffer and copying each result list. Primitive packet writing reads those
views; receive-side decoding and holder resolution remain Java-owned.
The [publication cache](https://github.com/HungLo2020/MattMC/blob/f92ea29dde0199a9ea65006e216ac92570b3b02b/src/main/java/net/minecraft/core/NativeRegistrySnapshot.java)
still retains `sentIds` arrays, registry references and
known-pack masks, so this does not remove all Java copies of tag IDs. No GPU
handle or rendering ownership crosses this boundary.

Retagging retires query generations, but acquired payload views keep their
original IDs even after the registry context is released. Each member list
retains the lease, and its memory segment is read-only; an automatic arena
eventually releases the lease after its views become unreachable, without a
deterministic close point. Each lease pins the whole tag set, not the registry
elements; avoid retaining an isolated member view indefinitely if its payload
is finished. New maps share unchanged native member arrays through `Arc`;
identical rebinds retain the existing map. Holder binding, the Java scan that
publishes IDs, registry objects and element codecs remain migration work.
See the [verification record](VERIFICATION.md) for tested reload/lease behavior
and the separate resource and gameplay performance limits.

During publication, unbound tags or a tag holding a non-reference value are
recorded as that registry's serialization error. `networkTags` raises it when
the registry's network tags are requested, preserving the Java message rather
than making unrelated identity/known-pack queries fail.

The payload map's iteration order is Java's: tag names are inserted in
`getTags()` order. That order comes from `TagKey` hashes, and `ResourceKey`
uses identity hash codes, so the wire order of colliding tags varies between
JVM runs in Frozen too. Compare payload contents across runs, and check order
against Java's algorithm in the same JVM, as the
[observer does](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/DevUtils/tests/resources/ResourceStackReference.java#L603-L638).

## Troubleshooting

- *`Registry snapshot has no registry`*: the access passed was not the one the
  registries came from. Pass `compositeAccess()` of the same layered access.
- *`Registry snapshot of … is stale`*:
  [`packRegistries` detected a known-mask length mismatch](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/main/java/net/minecraft/core/RegistrySynchronization.java#L54-L58).
  Check that the snapshot and registry come from the same frozen set. This is
  a size check, not a general guard against same-size element changes.
- *`Registry snapshot generation retired during a query`*: a native query used
  an old context/generation pair. Use the Java snapshot API and the current
  stable access; do not retain native generation handles across publications.
