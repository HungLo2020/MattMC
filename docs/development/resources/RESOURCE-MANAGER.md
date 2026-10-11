# Resource manager

[`manager.rs`](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/main/rust/assets/resources/manager.rs)
ports `MultiPackResourceManager` and `FallbackResourceManager`. Java's
[`MultiPackResourceManager`](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/main/java/net/minecraft/server/packs/resources/MultiPackResourceManager.java)
keeps the `ResourceManager` API: it hands Rust the packs' native handles and
wraps Rust's answers as `Resource` objects that read lazily through the pack.

Source-reviewed at
[`398a0078`](https://github.com/HungLo2020/MattMC/commit/398a00782007587c779264aaa62222036b8a9361).
The constructor casts each supplied `PackResources` to `NativePackResources`;
there is no adapter or fallback for another implementation. Use the
[native pack suppliers](PACKS.md#pack-kinds) when adding pack sources.

## Behavior to preserve

- **Stacks.** Each namespace keeps the supplied packs in lowest-priority-first
  order. A pack filtering a namespace it does not contain adds a filter-only
  entry for that namespace if another pack exposes it. A filter blocks lower
  packs, not the filtering pack's own resources.
- **Filters.** Rust calls Java for the namespace and path checks in
  [`ResourceFilterSection`](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/main/java/net/minecraft/server/packs/resources/ResourceFilterSection.java).
  Each check separately uses `anyMatch` across the block list, so the matching
  namespace and path need not come from the same entry. A missing field matches
  everything for that dimension. The regexes use
  [`Pattern.asPredicate`](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/main/java/net/minecraft/util/ResourceLocationPattern.java),
  so use anchors when a whole namespace or path must match.
- **Sidecar metadata.** `getResource` lazily searches for `.mcmeta` in the
  highest-priority pack at or above the resource's position, stopping at a
  sidecar filter. `listResources` also allows a higher pack's sidecar, but
  resolves its location during listing. `getResourceStack` and
  `listResourceStacks` instead pair each entry with its own pack's sidecar,
  subject to higher filters. Keep these single-result and stack rules distinct.
- **Order.** Listings return resources in `ResourceLocation` order (path, then
  namespace). Java's location predicates run after Rust resolves and sorts the
  results; they do not control Rust's traversal or sidecar pairing.
  `getNamespaces` returns an unmodifiable Java `HashSet`, not a sorted set.
- **Messages.** A lookup blocked by a filter warns
  `Resource X not found, but was filtered by pack Y`; stack lookup warns only
  if it has no results. Listings do not emit that per-resource warning.

## Lifetimes and reloads

[`ReloadableResourceManager.createReload`](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/main/java/net/minecraft/server/packs/resources/ReloadableResourceManager.java)
closes the previous manager's packs, replaces the manager with one built from
the supplied list, then starts its listeners. `MultiPackResourceManager.close()`
does not destroy its native manager or unregister its filters: a `Cleaner`
action is registered to release those when the Java manager becomes eligible
for cleanup. The Java manager retains its pack objects; the
[`native manager`](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/main/rust/assets/resources/ffi.rs)
also clones their `Arc<Pack>` ownership.

Returned [`Resource` objects](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/main/java/net/minecraft/server/packs/resources/Resource.java)
retain their source pack and suppliers. A `getResource` result's lazy metadata
supplier additionally captures the manager; stack/list results retain the
already selected sidecar pack through their metadata supplier. Closing releases
cached archive state, so a retained resource can reopen an unchanged, readable
archive. It is not a snapshot of files across reloads or a guarantee that reads
succeed after files change. Do not detach raw handles from their Java owners;
see [pack ownership](PACKS.md#pack-kinds).

Filter calls run synchronously in native manager construction or queries,
including a later lazy metadata lookup. Namespace stacks belong to each manager,
but the bridge uses a process-wide callback pointer and Java's
[`NativePacks` token registry](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/main/java/net/minecraft/server/packs/NativePacks.java#L494).
Managers supplied the same pack objects also share those packs' archive state.
These ownership boundaries do not establish general safety for overlapping
reloads, close/query races or arbitrary Java callbacks. See
[verification limits](VERIFICATION.md#limits) before claiming runtime coverage.

## Troubleshooting

- *A resource is missing only with some packs enabled*: search the log for
  `was filtered by pack` and check that pack's `filter` patterns. Absence of
  that warning does not rule out filtering during a listing.
- *Listing order differs from Frozen*: Java predicates must not depend on
  call order. Rust sorts first, then Java filters.
- *A sidecar comes from an unexpected pack*: check whether the caller used a
  single-result or stack API, then inspect higher packs' `.mcmeta` files and
  sidecar filters.
