# Persistent item component codecs

Rust owns structural parsing for 50 components inside native item patches.
The additional 15 routes described here run through `DataComponentPatch.CODEC`,
`ItemStack.CODEC`, `SINGLE_ITEM_CODEC`, nested containers and text hover items
when their input uses exact JSON or NBT ops and passes the admission rules below.
Their standalone Java save codecs remain original; item-default bootstrap
continues to use those codecs. Packet streams and gameplay remain Java.

Schemas live in [`items.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/content/data/items.rs)
and [`item_components.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/content/data/item_components.rs).
Java projects their decoded values through
[`NativeItemStacks`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/item/NativeItemStacks.java),
`NativeItemComponents` and
[`NativeItemComponentValues`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/item/NativeItemComponentValues.java).

## Current boundary

The original 35 native components include primitive values, text, lore,
containers and fireworks. The next 15 component routes are:

| Components | Rust owns | Original Java leaves |
|---|---|---|
| `food`, `enchantable`, `damage_resistant` | Fields, defaults, ranges and hashed tag syntax | None |
| `repairable` | Required item-set field | Item holder-set codec |
| `trim`, `banner_patterns` | Records, lists and dye names | Material, pattern and banner-pattern holders |
| `attribute_modifiers` | Entry list, modifier fields and slot default | Attribute holders and display codec |
| `potion_contents`, `suspicious_stew_effects` | Records, alternative form, lists and defaults | Potion/effect holders and effect-instance codec |
| `written_book_content` | Book fields, title/filterable structure and page list | Restricted component codec for each page |
| `bees` | Occupant list and tick fields | Typed entity-NBT codec |
| `break_sound`, `instrument`, `jukebox_playable`, `provides_trim_material` | Native component routing/projection | Sound or complete `EitherHolder` codec |

The last row provides typed routing, without transferring holder resolution. These codecs
retain their original registry behavior. `enchantments`, `stored_enchantments`
and `lock` remain Java codecs: holder-key maps need their own ownership
boundary, and `lock` delegates to the deferred item predicate.

## Constraints when editing

- Standalone native ownership remains unfinished. The original Java codecs
  preserve full decode remainders, lifecycle metadata, encoder prefixes and
  generic `DynamicOps` behavior. A future transfer must preserve these results
  through the native bridge and pass per-component timings; parse-only parity
  is insufficient. Avoid a second Java decode to reconstruct lost metadata.
- For tree inputs, custom ops and unproven registry providers retain the
  original complete component callback inside
  patches, once on the original source node. Mode bit 2 selects this fallback
  for only these 15 routes. Its reserved decoded variant tag `0xFFFF` identifies
  a complete Java component rather than a native schema's delegated holder.
  Value codecs and definition tree/file entry points propagate this mode.
  The custom-ops file export is separate, preserving the original file ABI.
- Tree admission happens before conversion. `RegistryAccess.freeze()` records
  successful same-instance finalization of exact standard registry classes in
  a privately constructed final snapshot. Only its standard `RegistryOps`
  adapter carries this provenance. Public immutable-access constructors, custom
  providers, registry subclasses and arbitrary wrappers cannot grant it;
  admission never calls their lookup/getter methods. Immutable map membership
  alone does not prove registry finalization.
- JSON trees also require inert scalar classes. The structural walk calls no
  numeric conversion: it accepts exact boxed primitive, `LazilyParsedNumber`,
  `BigInteger` and `BigDecimal` classes. Custom `Number` implementations and
  subclasses decline before serialization. Original component decoding then
  reads the original live nodes, preserving getter-driven map/list changes,
  callback order, partial results and exception prefixes. This gate only
  controls these 15 routes; it does not repair older bridge conversion behavior.
- File decoding with exact ops parses Rust-owned bytes, without caller-owned
  JSON nodes or custom numbers. It can retain native structure while resolving
  holders through the loader's registration context. Custom parent ops use the
  complete component callback, including when a getter mutates a live node that
  those ops exposed. Do not apply file admission to caller-owned tree inputs.
- Component codec construction must not initialize `NativeItemStacks`: its
  projection table needs already registered component types.
- Append Java leaf ordinals in the paired Rust and Java tables; keep existing
  value kinds and ordinals stable. Startup checks that the Java
  projections and Rust native-component names agree.
- Retain the registered component type's `cacheEncoding` wrapper. Stack
  encoding must use that wrapper for cached types, preserving the first equal
  JSON encoding and NBT copy behavior.
- Run original Java leaves synchronously, once, at their field position.
  Preserve partials, error order, callback reentry and the exact thrown object.
  Buffer growth must copy a saved answer instead of replaying a callback.
- `EitherHolder` validates resource-key syntax before checking permission to
  retain unresolved keys. Only item-definition bootstrap opts in through
  `RegistryOps.keepsUnresolvedKeys`; ordinary contexts keep the original
  validation errors and rejection behavior. Encoding and stream codecs use
  their existing forms.
- A holder-key map cannot use the shared string-keyed schema as a substitute
  for holder equality, alias detection or key-object diagnostics.

## Verification

```bash
./gradlew test -x testRustNative --tests '*NativeItemComponentValuesTest' --tests '*EitherHolderTest' --tests '*NativeItemsTest'
JAVA_HOME=/path/to/jdk-25 python3 DevUtils/tests/content/VerifyContent.py --observer ValueFuzzReference --sections item_components --output build/component-oracle
JAVA_HOME=/path/to/jdk-25 python3 DevUtils/tests/content/VerifyContent.py --observer ValueFuzzReference --sections item_component_contract --output build/component-contract-oracle
JAVA_HOME=/path/to/jdk-25 python3 DevUtils/tests/content/TimeContent.py --observer ItemComponentTiming --pairs 9 --rounds 500 --output build/component-timing
./gradlew -PmattmcRustProfile=release test parityTest slowTest
python3 DevUtils/RunWiki.py check
```

The component oracle compiles unchanged against Frozen and Current. Its
registry-aware fixtures require a successful example for every included type,
then mutate roots and fields across six routes in JSON and NBT. Rows retain
raw errors, partial encodings and ordered output. The separate full content
dump covers every shipped item's default components.

`item_component_contract` additionally records full decode remainders, source
identity, lifecycle and encode results. `ComponentCodecContractReference.java`
is another unchanged observer for standalone and nested custom-ops errors,
partials, list lifecycle and encoder prefixes. Compile it against each runtime
and pass an output file. `ComponentFileOpsReference.java` checks matching
definition file/tree errors and component callback counts; Frozen uses its
original codec on the file's parsed JSON. Preserve and classify differences already present in
the sealed pre-change runtime; they are not passes against Frozen. The current
bulk bridge still has older full-result metadata differences outside these
15 standalone codecs.

`ComponentMutationContractReference.java` loads actual data-driven registries
and compares custom lookup factories and provider wrappers, live map/list
mutation, invalid results, structural-list exceptions, getter failure prefixes,
reentry, definition file/tree paths and custom numeric conversion. Compile the
same source against all three runtimes and retain raw output. The focused tests
also verify admission performs no unknown calls and that a real standard
finalized context and file route still execute native component parsing.

The two excluded enchantment maps can iterate differently between JVMs
because holder keys use identity hashes. Keep raw outputs and establish that
each observed difference is solely that map's entry order before comparing
contents. Do not sort record fields, errors or partial results to hide a
different outcome.

`ItemComponentTiming` measures direct and explicit-stack-patch decoding and
stack encoding for each of the 15 families, with cold totals and the same
32-operation batches on every runtime. It warms the measured body for at least
three seconds and until compilation has been quiet for 500ms. Every epoch
runs for at least two seconds; retain all raw epochs, including compiler-active
ones. The reported steady value is the median of three compiler-quiet epochs
per JVM. Failure to obtain three within nine attempts is an unverified run.
Compare matched Frozen and a sealed pre-change runtime, without concurrent
builds. Its checksum checks fixture contents; it does not certify gameplay,
packet compatibility, rendering or a performance budget by itself. See the
[native value verification limits](NATIVE-VALUES.md#verification-2026-10-14).

## Recorded verification and performance debt (2026-10-15)

On `dc84c628` plus this change, the release check passed: 2,191 ordinary
tests (six skipped), 186 parity tests, 11 slow tests, and 2,884 native tests
(nine ignored). The sealed three-way campaign includes 121 mutation cases,
full component results and the broader definition/content observers. Older
bridge metadata and mutation-error diagnostic differences remain classified
separately; this does not establish complete codec parity with Frozen.

Nine matched JVMs per runtime, each with three compiler-quiet epochs and no
pressure flags, measured the same 15 families in 32-stack batches. Relative
to the sealed pre-change `683ab835` runtime, stack decoding improved by
2.9–36.0%. Paired median ratios against Frozen `7a4d1817` were 1.249–1.780;
13 confidence intervals were wholly above 1.25 and two straddled it.
These retain the [approved temporary performance debt](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18955160),
with [updated exact-build measurements](https://github.com/HungLo2020/MattMC/discussions/888#discussioncomment-18956968).
They are not a Frozen-budget pass. The release JNI identity is
`ae6ed8f606b14fea988930c1e519e9fe921eb70d46c7502c5c3b737c9df14f65`;
the local source, class, image and raw-sample receipts are under
`build/component-s20-r6-final-check-evidence`,
`build/component-s20-r6-final-release-oracles` and
`build/component-s20-r6-quiet-nine`.

The exit path is connected native item/component state shared by its producers,
mutations, copies and loading/saving consumers, replacing transient input-tree
conversion and Java projection. That larger ownership change remains unassigned.
It must preserve the full codec/callback and lifetime contracts and meet the
applicable Frozen budgets on these same workloads before retiring this debt.
