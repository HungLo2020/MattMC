# Item definitions

Rust owns every registered item's definition
([`content/item`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/content/item)):
all 1,897 items in registry ID order (vanilla, TaCZ, weathering copper and the
other integrated content). Java declares only each item's behavior factory:
`registerItem("trowel", TrowelItem::new)`, `registerBlock(Blocks.STONE)`.

## What a definition holds

- **Identity:** the name and, by catalog position, the registry ID.
- **Block binding:** the block a `BlockItem` places, plus aliases such as wall
  torches, wall signs and cauldron variants. Rust fills `Item.BY_BLOCK`.
- **Description prefix** (`block.` or `item.`), **crafting remainder** and
  **required features**.
- **Default components:** the common set (former `COMMON_ITEM_COMPONENTS`)
  and each item's own, as SNBT in their save encoding. `item_name` and
  `item_model` follow from the description and name.

`Item.Properties` is now a read-only projection. Its builder methods and the
code that only fed them (`ToolMaterial`, `ArmorMaterial(s)`, `Foods`,
`Consumables`, the tool constructors' property chains) are gone.

## How Java projects it

[`NativeItems`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/item/NativeItems.java)
reads table 3. Registration happens in two steps:

1. `Items.registerItem` takes the definition at the next registry ID. A wrong
   name fails. It builds the item from its description, remainder and features,
   then checks the placed block and fills `BY_BLOCK`.
2. `BuiltInRegistries.bootStrap` calls `NativeItems.bindComponents` once all
   built-in registries have their contents and before they freeze. Rust parses
   each SNBT value
   ([`snbt.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/content/item/snbt.rs))
   and hands Java NBT, which the component type's own save codec decodes.
   Values must bind first because codecs look entries up by name, and tag
   references must exist before tags freeze.

Items share one value per distinct component and SNBT (533 values for
1,728 item components). The distinct values decode in parallel, and
registry lookups go through one lock (`SerialGetter`). Tag sets live in
identity maps, so the result doesn't depend on decode order. A value that
builds a stack of an item not yet bound, such as a use remainder, decodes
afterwards in registry order.

Reading `Item.components()` before step 2 throws. `SpawnEggItem` builds its
type index lazily for this reason, and `eggs()` lists egg items without
reading components.

Components can name entries of data-driven registries (jukebox songs, trim
materials, instruments, chicken variants) that do not exist at bootstrap.
The definition decode keeps those as `EitherHolder` keys. Only this
bootstrap context opts in, through `RegistryInfoLookup.keepsUnresolvedKeys`;
saves and the network reject bare keys as before.

## Working on it

- **Add an item:** append its entry to `item/catalog.rs` with only what differs
  from the defaults, then register it last in `Items` with its factory.
- **Change a component:** edit the SNBT. Use the save form `/data get` shows.
  `PotDecorations.EMPTY` is stored as `[]` because its save form names bricks,
  which register later.

## Checks

```bash
cargo test --manifest-path src/main/rust/Cargo.toml --lib content::item
./gradlew -PmattmcRustProfile=release test --tests '*NativeItemsTest'
JAVA_HOME=/path/to/jdk-25 python3 DevUtils/tests/content/VerifyContent.py --sections items item_stacks --output build/item-verification
```

- The Rust test rebuilds every item row of Frozen's dump
  (`src/test/resources/content/frozen-items.txt.gz`).
- `NativeItemsTest` compares the live registry with that dump. It also checks
  that each stored component re-encodes to its SNBT, that order mismatches are
  rejected and that ordinary save contexts still reject bare keys.
- `NativeItemsTest` also checks Rust's SNBT parse of every catalog value
  against Java's `TagParser`.
- The `items` section of `VerifyContent` covers definitions and components.
  `item_stacks` encodes each item's default stack and an edited stack (name,
  repair cost, damage, a removed default) in save and network form.

## Verification (2026-10-13)

- Pre-change Current and Frozen item dumps were identical, and the catalog was
  generated from them.
- On the final code, the `items` (23,014 rows) and `item_stacks` (3,792 rows,
  all round-tripping) sections match Frozen and pre-change Current in
  alternating JVMs.
- Bootstrap timing is in [data-driven definitions](DATA-DEFINITIONS.md#performance).
