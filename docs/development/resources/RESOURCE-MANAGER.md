# Resource manager

[`manager.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/assets/resources/manager.rs)
ports `MultiPackResourceManager` and `FallbackResourceManager`. Java's
[`MultiPackResourceManager`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/server/packs/resources/MultiPackResourceManager.java)
keeps the `ResourceManager` API: it hands Rust the packs' native handles and
wraps Rust's answers as `Resource` objects that read lazily through the pack.

## Behavior to preserve

- **Stacks.** Each namespace keeps a stack of packs, lowest first, in the order
  vanilla built them. When a pack's `filter` blocks a namespace the pack does
  not contain, the pack adds a filter-only entry to that namespace's stack.
- **Filters.** `ResourceFilterSection` patterns are Java regexes, so Rust calls
  Java to check them (an upcall registered per manager by token). A pattern
  without `path` blocks every path, and one without `namespace` blocks every
  namespace; this is vanilla behavior, so fixtures that combine fields in one
  pattern block more than they appear to.
- **Sidecar metadata.** A resource's `.mcmeta` comes from the same pack unless
  a higher filter blocks the sidecar. `getResource` finds it lazily, as vanilla does.
- **Order.** Listings return resources in `ResourceLocation` order (path, then
  namespace). Java's location predicates run on Rust's results, and
  `getNamespaces` keeps vanilla's `HashSet` order.
- **Messages.** A lookup blocked by a filter warns
  `Resource X not found, but was filtered by pack Y`.

## Lifetimes and reloads

A reload builds a new manager over newly opened packs; the previous manager
keeps answering until its owner drops it. The native manager and its filter
token are released when the Java object is collected (`Cleaner`). `close()`
closes the manager's packs, which only releases archive files (see
[pack access](PACKS.md)). A `Resource` from a closed manager can still be
read; its archive reopens.

Rust keeps no global manager state, so concurrent reloads and the client and
integrated server's managers do not interact. Filter upcalls happen only on
the Java thread that queries the manager.

## Troubleshooting

- *A resource is missing only with some packs enabled*: search the log for
  `was filtered by pack` and check that pack's `filter` patterns.
- *Listing order differs from Frozen*: Java predicates must not depend on
  call order. Rust sorts first, then Java filters.
