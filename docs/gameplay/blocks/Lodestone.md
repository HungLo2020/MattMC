# Lodestone

A **Lodestone** (`minecraft:lodestone`) is a placed navigation marker. Use a [Compass](../items/Compass.md#lodestone-binding) on it to save its position and dimension in that Compass. The marker needs no charge or fuel, and using another Compass does not consume the block. The [Compass guide](../items/Compass.md) remains the guide to ordinary spawn navigation and needle behavior. [Registration][register] · [Binding interaction][bind]

## Crafting and loot

Craft **8 Chiseled Stone Bricks + 1 Iron Ingot → 1 Lodestone** in a 3 × 3 grid: put the Iron Ingot in the center and fill the other eight squares with Chiseled Stone Bricks. This snapshot uses **Iron**, so save Netherite for other recipes. Chiseled Stone Bricks can themselves be crafted from **two Stone Brick Slabs stacked vertically → one Chiseled Stone Bricks block**. [Lodestone recipe][recipe] · [Chiseled Stone Bricks recipe][chiseled] · [Active recipe loader][recipes]

There are also two checked chest routes:

| Chest loot table | Lodestone result in the bundled table |
| --- | --- |
| Bastion bridge chest | A separate guaranteed pool gives **1 Lodestone** |
| Ruined Portal chest | A separate single roll gives **1–2 Lodestones with probability 2/3**, or none with probability 1/3 |

The portal odds follow the Lodestone entry's weight of 2 and the empty entry's default weight of 1. They apply to an untouched chest using that table, not to every chest near a portal or to a previously looted chest. The bridge guarantee likewise belongs to the **bridge loot table**, not every Bastion chest. [Bridge loot][bridge-loot] · [Portal loot][portal-loot] · [Default entry weight][weight] · [Weighted selection][loot-pool]

These are connected acquisition routes: the checked Bastion bridge entrance template contains a chest with `minecraft:chests/bastion_bridge`; the checked Ruined Portal templates contain `minecraft:chests/ruined_portal`. Structure placement loads the chest data, and opening the container unpacks the assigned table. [Bastion bridge template][bridge-template] · [Bridge template pool][bridge-pool] · [Example portal template][portal-template] · [Portal selection][portal-selection] · [Template data loading][template-load] · [Chest opening][chest-open] · [Loot-table loading and filling][chest-loot]

## Placing and recovering the block

Place the Lodestone at the position you want to mark, then bind each Compass there. It has hardness **3.5**, requires a correct tool for drops, and cannot be pushed or pulled by Pistons. There is no facing, charge level or block inventory to configure. [Block registration][register] · [Piston handling][piston]

Mine it with an **unbroken Pickaxe** to recover **one Lodestone**. The checked built-in pickaxe materials all qualify, including **Wood and Gold**: Lodestone is pickaxe-mineable and absent from the resolved tool-tier denial tags. A bare hand or wrong tool does not recover the block. Silk Touch is unnecessary and Fortune does not add copies. The loot also has an explosion-survival condition, so explosion recovery is not guaranteed. [Pickaxe tag][pickaxe] · [Tool material rules][tool-material] · [Wood and Gold exclusions][wood-denials] · [Gold denials][gold-denials] · [Tier requirement tags][stone-tier] · [Iron tier][iron-tier] · [Diamond tier][diamond-tier] · [Broken-tool check][stack-tool] · [Active harvest rule][player-tool] · [Harvest][harvest] · [Block loot][loot]

Recovering the block does **not** carry its bound Compasses to a new location. Binding data lives on each Compass and records coordinates, not ownership of a particular Lodestone item. Rebind after moving the marker. [Binding data][bind] · [Stored coordinate fields][global-pos] · [Target checks][tracker]

## Binding a Compass

1. Place the Lodestone at your intended destination
2. Hold an ordinary Compass and use it on the block
3. Keep the resulting **Lodestone Compass** and travel within that same dimension

With exactly **one held Compass in Survival**, the existing item is updated in place. From a larger stack, the interaction consumes one Compass and adds the bound result to inventory; if there is no room, it drops that result beside the player. In Creative/infinite-materials play, the original is retained and the bound result is added or dropped. The block itself is unchanged, so repeat this for other Compasses or players. [Binding transaction][bind] · [Active server interaction][use-dispatch] · [Item use dispatch][stack-use]

The result stays the same `minecraft:compass` item type, with a Lodestone Tracker component. That component causes its Lodestone Compass display name and glint. Using an already bound Compass on a different Lodestone replaces the target, following the same one-item/stack handling. Keep separately named Compasses if you want several destinations. [Name, glint and rebinding][bind]

## Stored targets, dimensions and broken markers

The normal binding stores a **dimension plus block position**, with tracking enabled. The component is persistent and synchronized to the client; the normal item-stack and player-inventory save/load paths preserve it. Rejoining does not intentionally reset a valid binding. There is no time limit in the tracker. [Tracker data][tracker] · [Persistent component registration][component] · [Coordinate codec][global-pos] · [Item-stack data][stack-data] · [Player save/load][player-save] · [Inventory save/load][inventory-save] · [Slot codec][slot-codec]

The bound target works only while the Compass is in the **same dimension** as that saved target. An Overworld marker does not become a Nether marker by dividing coordinates, and a Nether marker can be used for navigation within the Nether. A different dimension or absent target gives the client's spinning/no-valid-target behavior. The direction calculation is horizontal; it does not lead around obstacles or identify a safe arrival height. [Target selection and dimension validity][angle] · [Registered client property][angle-registration] · [Angle caller][angle-caller]

The server checks carried Compasses through their inventory tick, including ordinary inventory slots rather than only the selected hotbar slot. For an ordinarily bound, tracked Compass:

- **In the target dimension:** the saved position must be in world bounds and still have a registered Lodestone point of interest; otherwise the target is cleared
- **In another dimension:** that tick leaves the saved target unchanged, even though the needle cannot point to it there
- **After the target is cleared:** the tracker component remains, so the Lodestone name/glint does not prove the destination is still valid. Place or find a Lodestone and bind the Compass again

[Inventory tick caller][inventory-tick] · [Item tick dispatch][stack-tick] · [Compass tick][bind] · [Validity and clearing][tracker] · [Lodestone point-of-interest registration][poi] · [Block-change updates][poi-update] · [Poi caller][poi-caller]

The saved target is a coordinate, not a unique block identity. Replacing a Lodestone at the same coordinate **before** the Compass has cleared its target can satisfy the check again. Once the target has been cleared, merely replacing the block does not restore the missing coordinates. A Compass carried in another dimension therefore need not lose its saved target at the instant the distant marker is broken. These are consequences of the checked tick conditions, not a promise of immediate notification when a block is destroyed. [Exact target checks][tracker]

## Practical marker setups

- **Mark a Nether portal's return area:** put a Lodestone near the portal on the Nether side and bind a Compass there. Use that Compass while exploring the Nether; it cannot point to that Nether marker from the Overworld. [Saved dimension][bind] · [Angle][angle]
- **Give a group a common destination:** each player binds a Compass to the same placed Lodestone. There is no charge consumed or single-player ownership field in that interaction. Keep the marker protected so a later inventory check does not clear targets after it is removed. [Binding][bind] · [Validation][tracker]
- **Move a base marker:** recover the block with a Pickaxe, place it at the new destination, and use each Compass on the new block. Carrying the recovered Lodestone item does not rewrite any Compass's old coordinates. [Recovery][loot] · [Harvest][harvest] · [Rebinding][bind] · [Tracker][tracker]
- **Diagnose a spinning Compass:** check the dimension first, then the marker. A glinting Lodestone Compass can still have an empty target after a failed validity check. Standing essentially at the target also fails the client's normal direction-validity test. [Client validity][angle] · [Target clearing][tracker] · [Glint][bind]

## Sources and verification

Source-reviewed on **2026-10-02** at `fc47f0654e74d1e5bd3b7f3522cc1e8117694418`. The review covered the exact recipes, recursively resolved tool tags, player mining and item-use callers, loot-table pools, decoded chest NBT, tracker persistence, inventory ticks, point-of-interest registration/removal and the client compass-direction property. All 13 bundled Ruined Portal templates were checked for the portal chest table; the Bastion bridge entrance was checked for its bridge table. No in-game crafting, chest generation, mining, binding, save/reload, dimension or marker-destruction test was run. Custom data and components can change recipes, drops or tracking; for example, an explicitly untracked custom component bypasses normal target clearing.

Related: [Lodestone item](../items/Lodestone.md) · [Compass navigation](../items/Compass.md) · [Stone](Stone.md) · [Nether](../dimensions/Nether.md) · [Workstations and utilities](catalog/workstations.md) · [Blocks](Blocks.md)

[register]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/Blocks.java#L5806-L5814
[recipe]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/recipe/crafting/lodestone.json#L1-L17
[chiseled]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/recipe/crafting/chiseled_stone_bricks.json#L1-L15
[bridge-loot]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/chests/bastion_bridge.json#L1-L20
[portal-loot]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/chests/ruined_portal.json#L278-L305
[weight]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/storage/loot/entries/LootPoolSingletonContainer.java#L45-L55
[loot-pool]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/storage/loot/LootPool.java#L60-L91
[bridge-template]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/structure/bastion/bridge/starting_pieces/entrance.nbt
[bridge-pool]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/worldgen/template_pool/bastion/bridge/starting_pieces.json#L1-L23
[portal-template]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/structure/ruined_portal/portal_1.nbt
[portal-selection]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/levelgen/structure/structures/RuinedPortalStructure.java#L103-L153
[template-load]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java#L280-L310
[chest-open]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/entity/RandomizableContainerBlockEntity.java#L84-L93
[chest-loot]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/RandomizableContainer.java#L50-L89
[piston]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java#L241-L258
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json#L218-L226
[tool-material]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L48
[wood-denials]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json#L1-L7
[gold-denials]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/tags/block/incorrect_for_gold_tool.json#L1-L7
[stone-tier]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json#L1-L82
[iron-tier]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json#L1-L15
[diamond-tier]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/tags/block/needs_diamond_tool.json#L1-L8
[stack-tool]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/ItemStack.java#L576-L589
[loot]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/blocks/lodestone.json#L1-L21
[bind]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/CompassItem.java#L21-L75
[use-dispatch]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L379-L394
[stack-use]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L371
[tracker]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/component/LodestoneTracker.java#L14-L40
[global-pos]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/core/GlobalPos.java#L12-L26
[component]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/core/component/DataComponents.java#L266-L268
[stack-data]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/ItemStack.java#L106-L116
[player-save]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/entity/player/Player.java#L659-L690
[inventory-save]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/entity/player/Inventory.java#L387-L404
[slot-codec]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/ItemStackWithSlot.java#L8-L14
[inventory-tick]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/entity/player/Inventory.java#L240-L247
[stack-tick]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/ItemStack.java#L703-L711
[angle]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/client/renderer/item/properties/numeric/CompassAngleState.java#L43-L124
[angle-registration]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/client/renderer/item/properties/numeric/RangeSelectItemModelProperties.java#L15-L23
[angle-caller]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/client/renderer/item/properties/numeric/CompassAngle.java#L24-L27
[poi]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiTypes.java#L136-L142
[poi-update]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/server/level/ServerLevel.java#L1418-L1435
[poi-caller]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/Level.java#L231-L245
[harvest]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L297
[player-tool]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[drop-dispatch]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/Block.java#L439-L443
[recipes]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L72-L88
