# Adding content (proposal)

> Proposal; not implemented. See the [game model index](index.md).

## Current transition

New registered blocks need a [native definition](BLOCK-DEFINITIONS.md),
[physical settings](BLOCK-PHYSICS.md) and [intrinsic state rules](BLOCK-INTRINSICS.md),
plus the remaining Java behavior/codec/item bindings and assets. The single-file
builder below is still proposed.

Before these ownership slices, adding the three TaCZ workbenches took:
- edits to `Blocks.java`, `BlockTypes.java` (codec), `Items.java`,
  `CreativeModeTabs`, `MenuType`
- a block class, a menu, a recipe type and a screen
- per block: blockstate JSON, item JSON, block and item models, loot table,
  recipe and lang key

The remaining Java registries still require ordered bindings during migration.

## Proposed: one Rust file plus assets

```rust
// content/blocks/copper_lantern.rs
pub fn register(r: &mut ContentRegistrar) {
    r.block("copper_lantern")
        .properties([HANGING, WATERLOGGED])
        .strength(3.5)
        .sound(Sounds::LANTERN)
        .light(|_| 15)
        .shape(|s| if s.get(HANGING) { shapes::LANTERN_HANGING } else { shapes::LANTERN })
        .behavior(&Lantern)                  // reuse an existing family
        .item(ItemProps::default());         // BlockItem registered and ordered automatically
}
```

- The builder derives everything Java needed separate edits for: the codec,
  the `BlockItem`, the state table and per-state columns.
- Anything vanilla keeps in data files stays there, in the same formats:
  blockstates, models, loot tables, recipes, tags, lang and datapack
  registries. Existing tools, resource packs and datapacks keep working. Only
  what Java defines in code (`Blocks.java`, `Items.java`) becomes Rust.
- Integrated mod content (Alex's Caves, Alex's Mobs, TaCZ) is written the
  same way, in the same modules and the `minecraft` namespace.
- New behavior means one new type implementing `BlockBehavior` in its own
  file, with only the hooks that differ. The column flags and hook bitmask are
  derived automatically.
- A block entity is the block plus the components it attaches,
  `.anchored(|e| e.with(Inventory::new(27)).with(CustomName::none()))`, plus
  any new component or system.

## Guard rails that keep this easy

- **Startup validation** reports a missing model, loot table, lang key or
  blockstate variant per block, instead of a pink cube at runtime.
- **A registry dump** (`--dump-registries`) writes every block, state, item and
  column as JSON. CI diffs it so ID or behavior changes are always visible in
  review.
- **Order lists**: one file lists registration order. New entries go at the
  end unless a migration deliberately renumbers.
- **No central `match` to edit**: content registers itself through its module,
  collected by one `content::register_all` list.
