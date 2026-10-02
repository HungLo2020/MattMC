# Bamboo

**Bamboo** (`minecraft:bamboo`) is a tall, harvestable plant used for [Sticks](../items/Stick.md), [Scaffolding](Scaffolding.md), and Blocks of Bamboo. The same Bamboo item plants a small **shoot** on suitable ground or extends an existing stalk. [Block registrations][blocks] · [Item registration][item] · [Placement][plant]

## Finding and collecting

**Bamboo Jungle** is one verified natural source: its biome features include a placed Bamboo feature, which resolves to the active Bamboo generator. The generator checks available space and suitable ground before placing its stalks. This is a checked source, not a complete acquisition or biome list. [Biome][biome] · [Placed feature][placed] · [Configured feature][configured] · [Feature registration][feature-reg] · [Generator][feature]

Ordinary mining of either a stalk block or a Bamboo shoot returns **one Bamboo item**, with an explosion-survival condition. Neither requires a tool, Silk Touch, or Fortune; neither loot table has a Fortune multiplier. [Stalk loot][loot] · [Shoot loot][shoot-loot] · [Properties][blocks] · [Drop gate][gate]

An **unbroken sword instantly mines** both stalks and shoots through the sword's instant-mining tag. Axes also receive their normal tagged mining speed on stalks. A broken tool loses its special mining speed. Both the shoot and stalk have **hardness 1**. [Sword tag][sword-tag] · [Sword rule][sword] · [Axe tag][axe] · [Broken-tool speed][broken] · [Final properties][blocks] · [Property setters][strength]

For repeat harvests, leave the lowest growing stalk and cut above it. Unsupported stalks above the cut schedule a support check **one tick later** and break with drops. The remaining growing stalk can produce a new top. [Support updates][updates] · [Growth][grow] · [Mining dispatch][break]

## Planting and support

The destination must contain **no fluid**. Planting on suitable ground normally produces `minecraft:bamboo_sapling`, the shoot form, rather than a full stalk. Placing Bamboo over an existing shoot/stalk extends the plant; adding a stalk above a shoot also turns that shoot into a stalk. [Stalk placement][plant] · [Shoot updates][shoot]

The bundled support tag accepts these blocks directly below the plant:

| Group | Supported blocks |
| --- | --- |
| Sand | Sand, Red Sand, Suspicious Sand |
| Dirt group | Dirt, Grass Block, Podzol, Coarse Dirt, Mycelium, Rooted Dirt, Moss Block, Pale Moss Block, Mud, Muddy Mangrove Roots |
| Gravel | Gravel, Suspicious Gravel |
| Bamboo | Bamboo stalk or Bamboo shoot |

Nearby water is not required, and Farmland is absent from this tag. Support must remain valid; the shoot immediately becomes air through its neighbor update when support fails, while a stalk uses its scheduled destruction check. [Support tag][support] · [Sand group][sand] · [Dirt group][dirt] · [Stalk survival][updates] · [Shoot survival][shoot]

A shoot has no collision. A stalk has a narrow **3/16-block-wide, full-height collision column**, shifted by its positional offset; its leaves can have a larger selection outline. It is not a full solid cube. [Properties][blocks] · [Shapes][stalk]

## Growth and Bone Meal

A growing shoot or stalk attempts to extend on **one third of its random ticks**, provided the space above is air and has **raw brightness at least 9**. These are random-tick attempts, not a fixed growth timer. [Shoot growth][shoot] · [Stalk growth][grow] · [Server random ticks][random]

A continuously growing planted stalk can finish at **12–16 blocks tall**. On each growth step that reaches height 12–15, the new top has a **25% chance** to enter its finished stage; a new top at height 16 always finishes. Finished tops stop ordinary random growth. These are growth limits, not a height claim for every generated or manually built stalk. Ordinary item placement does not apply the 16-block growth cap. [Finished-stage choice][mature] · [Growth cap][grow] · [Placement][plant]

**Bone Meal works**, with different checks for the two forms:

- A shoot with air above it grows a stalk above itself, then becomes part of the stalk
- On an unfinished stalk shorter than 16 blocks, Bone Meal attempts **one or two upward growth steps**, stopping if the top finishes, reaches the height cap, or meets an obstruction

Bone Meal growth does not use the natural-growth brightness threshold. Clear space above the stalk before using it: the stalk's target check can accept Bone Meal even when an obstruction prevents the growth step, consuming the item without adding height. A finished top is not a valid target. [Shoot Bone Meal][shoot] · [Stalk Bone Meal][bone] · [Item consumption][bone-use]

## Selected uses and fuel

| Use | Where to find the checked recipe or behavior |
| --- | --- |
| Sticks | The [Stick recipe section](../items/Stick.md#crafting) owns the Bamboo-to-Stick recipe |
| Scaffolding | [String's selected recipes](../items/String.md#selected-crafting-recipes) owns the recipe; [Scaffolding](Scaffolding.md) explains placement and support |
| Block of Bamboo | Craft **9 Bamboo** in a 3 × 3 Crafting Table grid to make **1 [Block of Bamboo](BambooBlocks.md#block-of-bamboo)**; the recipe is shapeless but needs nine ingredient slots |
| Pot display | Add Bamboo to an empty [Flower Pot](FlowerPot.md); the potted form does not grow into a stalk |

[Stick recipe][stick-recipe] · [Scaffolding recipe][scaffold-recipe] · [Block recipe][block-recipe] · [Potted registration][pot]

Blocks of Bamboo also feed the Bamboo Planks recipe. This page covers the raw plant and its selected resource routes; it does not establish every Bamboo wood variant's recipes or behavior. [Plank input recipe][planks-recipe]

One raw Bamboo item provides **50 default Furnace burn ticks**, one quarter of a 200-tick recipe. Four supply one uninterrupted 200-tick operation. Use [Furnace fuel planning](Furnace.md#fuel-planning) for batching and partial progress. Bamboo stalks are also registered as flammable, so fire can destroy a growing supply. [Default fuel][fuel] · [Server fuel setup][fuel-load] · [Fire registration][fire]

Related: [Bamboo item](../items/Bamboo.md) · [Scaffolding](Scaffolding.md) · [Cactus](Cactus.md) · [Flower Pot](FlowerPot.md) · [Blocks](Blocks.md) · [Items](../items/Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Checked plant/shoot/item registrations, the Bamboo Jungle generation chain, expanded support/mining tags, both loot tables, growth and Bone Meal dispatch, selected recipes, fuel, and fire registration. No in-game planting, growth, harvesting, collision, crafting, or fuel test was run. Data packs can change recipes, tags, and loot. Bamboo wood construction variants remain outside this plant guide's scope.

[blocks]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L5189-L5221
[item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L382
[plant]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/BambooStalkBlock.java#L80-L112
[biome]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/bamboo_jungle.json#L85-L86
[placed]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/placed_feature/bamboo.json
[configured]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/configured_feature/bamboo_some_podzol.json
[feature-reg]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L121
[feature]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/levelgen/feature/BambooFeature.java
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/bamboo.json
[shoot-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/bamboo_sapling.json
[gate]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[sword-tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/sword_instantly_mines.json
[sword]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ToolMaterial.java#L62-L79
[axe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[broken]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ItemStack.java#L374-L379
[strength]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1143-L1153
[updates]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/BambooStalkBlock.java#L106-L154
[grow]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/BambooStalkBlock.java#L115-L134
[break]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[shoot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/BambooSaplingBlock.java
[support]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/bamboo_plantable_on.json
[sand]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/sand.json
[dirt]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/dirt.json
[stalk]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/BambooStalkBlock.java
[random]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerLevel.java#L487-L507
[mature]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/BambooStalkBlock.java#L189-L208
[bone]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/BambooStalkBlock.java#L157-L187
[bone-use]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L77
[stick-recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/stick_from_bamboo_item.json
[scaffold-recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/scaffolding.json
[block-recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/bamboo_block.json
[pot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L5221
[planks-recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/bamboo_planks.json
[fuel]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L96
[fuel-load]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/MinecraftServer.java#L340
[fire]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/FireBlock.java#L485-L486
