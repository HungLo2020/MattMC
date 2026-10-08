# Items (proposal)

> Proposal; not implemented. See the [game model index](index.md).

## Today

The 2026-10-07 source inventory contains **1,897 item registrations**:

- 1,687 individual `Item` fields in
  [`Items.java`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/item/Items.java),
  minus five aliases of already registered TaCZ items
- 191 TaCZ registrations from the definition lists: 63 guns, 29 ammo items and
  99 attachments
- 24 registrations from three eight-member `WeatheringCopperItems` groups

These are registration-site counts, not a new runtime dump. The original
proposal's 83 `Item` subclasses is a historical source-survey figure.
[`DataComponents.java`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/core/component/DataComponents.java)
declares 97 `DataComponentType` fields for food, tools, durability,
enchantments, equippable data and other properties. Since 1.20.5 much item data
and behavior is component-driven, while specialized hooks still live in item
classes. `Item.Properties` mostly sets components. An `ItemStack` is an item,
a count, and a patch of component changes over the item's default components.

## Proposal

```rust
pub struct ItemDef {
    pub defaults: ComponentMap,             // the prototype, frozen at startup
    pub behavior: &'static dyn ItemBehavior,
    pub block: Option<BlockId>,             // BlockItem: the block it places
}

pub struct ItemStack {
    pub item: ItemId,
    pub count: i32,
    pub patch: Option<Box<ComponentPatch>>, // None: no changes from defaults
}
```

- An `ItemStack` without changes is 16 bytes and allocation-free. Most stacks
  in chests and inventories are like that.
- **Reading a component** checks the patch, then falls back to the defaults:
  `stack.get(&components::MAX_DAMAGE)`. A patch entry can also *remove* a
  default, as Java's empty `Optional` does.
- **Typed component keys**: `Component<T>` is a constant with a registry ID
  and a value type, so `get` returns `Option<&T>` and a wrong type does not
  compile. The patch is a small sorted vector of `(ComponentTypeId,
  Option<ComponentValue>)`. `ComponentValue` is an enum with one variant per
  value type: no `Any`, serializable, and with exact codecs for saving and
  networking.
- **Behavior** (`ItemBehavior`) has the same shape as blocks: defaults for
  every hook (`use_on`, `use`, `finish_using`, `inventory_tick`,
  `hurt_enemy`, `mine_block`, …). Most items keep the default and rely on
  components (food, tools, equipment are component-driven systems). Families
  such as buckets, bows, potions and spawn eggs get one implementation each.
  `BlockItem` is a generic behavior configured with its `BlockId`.

## Shared value types with block entities

Many data components describe the same things block-entity components hold:
container contents, custom name, bees, lock, banner patterns. Using **the
same Rust value types** for both makes the item ↔ block-entity transfer a
copy. Breaking a shulker box puts its `Inventory` into the item's
`container` component, and placing it puts it back.

## Item stacks in components

An `Inventory` component is a fixed-size `Vec<ItemStack>` (or a small
`SmallVec`), not a list of objects. Moving items is moving 16-byte values.
