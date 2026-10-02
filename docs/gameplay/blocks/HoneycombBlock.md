# Honeycomb Block

**Honeycomb Block** is a decorative construction block with the exact block and item ID `minecraft:honeycomb_block`. Make it when you want its honeycomb texture: the bundled recipes do **not** unpack it back into loose Honeycomb. [Block registration][block] · [Item registration][item] · [Crafting recipe][recipe]

## Crafting and obtaining

Arrange **four [Honeycomb](../items/Honeycomb.md) in a 2 × 2 square to make one Honeycomb Block**. This fits the personal crafting grid. For collecting the ingredient, follow [Bee housing](BeeHousing.md#harvesting); the harvest produces loose Honeycomb, not Honeycomb Blocks. [Recipe][recipe] · [Harvest loot][harvest-loot]

The bundled recipe scan found no Honeycomb Block unpacking recipe and no recipe using Honeycomb Block as an ingredient. Keep loose Honeycomb for waxing, Candles and Beehives rather than treating this as reversible resource storage. See [Honeycomb uses](../items/Honeycomb.md#waxing-and-crafting) for those recipes and interactions. [Exact block recipe][recipe] · [Waxing item][wax] · [Candle recipe][candle] · [Beehive recipe][beehive]

**Separate inventory route:** this is an ordinary **Natural Blocks** category entry. The [inventory item browser](../mechanics/InventoryBrowser.md) provides its checked Survival and Creative insertion route independently of crafting. The checked natural-resource route here is harvesting the ingredient and crafting the block; this page does not claim a naturally generated Honeycomb Block source. [Category][category] · [Entry][entry]

## Placing and recovering

Honeycomb Block is a **full cube** with ordinary collision. It has no facing, axis or waterlogged state, and no attachment-support requirement; removing the block underneath does not make it fall. It has no storage menu or Bee occupants. [Plain Block registration][plain] · [Placement and empty state definition][state] [no-state] · [Shape, support and menu defaults][shape] · [Fluid default][fluid]

**Breaking it by hand returns one Honeycomb Block.** There is no correct-tool drop requirement, Silk Touch branch or Fortune multiplier. The ordinary pickaxe, axe, shovel and hoe mining tags do not assign it a special tool. Hardness and blast resistance are both **0.6**. [Registration][block] · [Player drop check][gate] [harvest] · [Complete loot][loot] · [One-item default][one-item] [one-count] · [Strength interpretation][strength] · [Mining tags][pickaxe] [axe][] [shovel][] [hoe]

An explosion can lose the item because its loot has the explosion-survival condition. Ordinary successful mining has no explosion radius and passes that condition. [Loot][loot] · [Condition][explosion]

## Honeycomb, Honey Blocks and waxing

These similarly named things serve different purposes:

- **Loose Honeycomb** is the ingredient and waxing tool; its custom item class handles supported copper and signs. The Honeycomb Block item is a normal BlockItem, so it does not substitute for that waxing interaction. [Item classes][honeycomb-item] [item][] [item-kind][] · [Waxing handlers][wax] · [Sign dispatch][sign]
- **Honeycomb Block** is the full construction cube on this page. Its item registration does not give it a food/consumable component. [Item registration][item]
- **[Honey Block](../items/HoneyBlock.md)** is separately registered as `minecraft:honey_block` with its own block class and reduced movement/jump factors. Honeycomb Block does not inherit those Honey Block settings. [Separate registrations][block]

For Bees, use a [Beehive or Bee Nest](BeeHousing.md). For reversible mineral packing, use [Resource Storage Blocks](ResourceStorageBlocks.md). Neither system belongs to this decorative block.

## Sources and verification

Source-reviewed at `b823010659d7b5095ed021b1c99cf85627e2082a` on 2026-10-02. Checked block/item classes, category entry, recipe and complete block loot, all bundled recipe JSON for reverse/input references, ordinary tool tags, inherited placement/collision/support/fluid rules and loose-Honeycomb interaction dispatch. No in-game crafting, mining, placement or waxing test was run. No exhaustive world-generation survey was performed. Data packs and later source changes may add recipes or change behavior.

[gate]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[shape]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L304-L326
[fluid]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L228-L230
[state]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Block.java#L435-L442
[explosion]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/storage/loot/predicates/ExplosionCondition.java#L27-L35
[strength]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1143-L1153
[one-item]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/storage/loot/entries/LootItem.java#L33-L36
[block]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Blocks.java#L5758-L5765
[item]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L2481
[recipe]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/recipe/crafting/honeycomb_block.json#L1-L15
[harvest-loot]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/loot_table/harvest/beehive.json#L1-L23
[wax]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/HoneycombItem.java#L82-L129
[candle]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/recipe/crafting/candle.json#L1-L16
[beehive]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/recipe/crafting/beehive.json#L1-L17
[category]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L752-L761
[entry]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1010-L1015
[plain]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Blocks.java#L7291-L7293
[loot]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/loot_table/blocks/honeycomb_block.json#L1-L21
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json#L1-L395
[honeycomb-item]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L2462
[sign]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/SignBlock.java#L90-L115
[no-state]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Block.java#L497-L498
[harvest]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[one-count]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/ItemStack.java#L248-L254
[axe]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/tags/block/mineable/axe.json#L1-L59
[shovel]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/tags/block/mineable/shovel.json#L1-L25
[hoe]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/tags/block/mineable/hoe.json#L1-L23
[item-kind]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L2750-L2755
