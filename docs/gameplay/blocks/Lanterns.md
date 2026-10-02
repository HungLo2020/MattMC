# Lanterns and Soul Lanterns

**Lanterns emit light level 15; Soul Lanterns emit 10.** Both can stand on a support or hang beneath one, including while waterlogged. They give steady light without fuel or a redstone switch. This guide covers `minecraft:lantern` and `minecraft:soul_lantern`; each is one block and one item, with its standing/hanging choice stored as a block state. [Registrations][reg-lanterns] · [Items][items] · [Block implementation][lantern]

## Crafting and obtaining

Use a 3 × 3 Crafting Table grid with **eight Iron Nuggets surrounding the named torch**:

| Output | Center ingredient | Yield |
| --- | --- | ---: |
| `minecraft:lantern` | 1 ordinary Torch | 1 Lantern [recipe][recipe-lantern] |
| `minecraft:soul_lantern` | 1 Soul Torch | 1 Soul Lantern [recipe][recipe-soul_lantern] |

Copper or Redstone Torches do not substitute for those exact ingredients. See [Torch](Torch.md#crafting) for the ordinary ingredient; [Soul Torch](../items/SoulTorch.md) is a separate item. [Lantern recipe][recipe-lantern] · [Soul Lantern recipe][recipe-soul_lantern]

A level-2 **Apprentice Librarian** can also select a base-price **1 Emerald → 1 Lantern** trade. It appears in both the normal and experimental Librarian offer lists. It is one possible offer, not a guaranteed purchase from every Librarian. See [Villagers](../mobs/Villager.md) for profession levels and price changes. [Trade entries][trades] · [Active normal/experimental selection][villager] · [Random offer selection][trade-choice]

## Floor and ceiling placement

Place a Lantern on a block that supports the **center of its top face**, or hang it beneath a block that supports the **center of its bottom face**. The placement code considers only vertical attachment directions; there is no separate horizontal wall-mounted Lantern form. A block having some collision is not enough by itself: the center-support test must pass. [Placement and survival][lantern] · [Center-support test][support]

The fixture has a small solid collision shape around its body and handle, rather than a full cube or an empty collision shape. The hanging version is shifted upward by one-sixteenth of a block. Both have hardness **3.5** and the same support rules. Their state has only `hanging` and `waterlogged`; it has no rotation, facing, fuel, or lit/unlit property. [Shapes and states][lantern] · [Collision defaults][default] · [Registrations][reg-lanterns]

Removing the relevant floor or ceiling makes the survival check fail on that support-side update, and the Lantern breaks. With normal block drops enabled, it returns its matching item. Neither hanging position nor stored Water is copied into the item; the next placement chooses its state again. [Support updates][lantern] · [Destruction dispatch][support-drop] · [Lantern loot][loot-lantern] · [Soul Lantern loot][loot-soul_lantern]

## Water and light

Placing either Lantern in **source Water** sets its waterlogged state. This checks for `Fluids.WATER` specifically, not every flowing-water fluid type. A Water Bucket can fill a placed Lantern, and an empty Bucket can collect that stored source while leaving a supported Lantern in place. Waterlogging schedules normal water updates, but does not change the registered light value. [Placement and fluid state][lantern] · [Bucket callbacks][waterlogged] · [Constant brightness][reg-lanterns]

A waterlogged Soul Lantern still emits **10**, and an ordinary Lantern **15**. Redstone power does not toggle either one. For controllable lighting use a [Redstone Lamp](RedstoneLamp.md) or [Copper Bulb](CopperLighting.md#copper-bulbs). [Block callbacks][lantern] · [Light registration][reg-lanterns]

## Harvesting

**Hand mining recovers one matching Lantern in this MattMC source.** Both registrations omit the correct-tool drop gate. They are in the pickaxe mining tag through the lantern tag, so a pickaxe speeds breaking, but it is not required for their ordinary self-drop. [Registrations][reg-lanterns] · [Pickaxe tag][pickaxe] · [Lantern tag][lantern-tag] · [Player gate][gate] · [Mining dispatch][harvest]

Silk Touch is unnecessary, Fortune does not add more items, and the ordinary loot tables do not preserve extra state. Each table also has an explosion-survival condition, so do not treat explosive destruction as guaranteed recovery. Item spawning follows `doTileDrops`. [Lantern loot][loot-lantern] · [Soul Lantern loot][loot-soul_lantern] · [Drop dispatch][drops]

## Soul Lantern and Piglins

Soul Lantern belongs to the **Piglin repellent block tag**, while ordinary Lantern does not. The ordinary Piglin sensor records nearby tagged repellents and its avoidance behavior uses that memory. This is a mob behavior interaction, separate from brightness; it is not a guarantee that every hostile mob stays away. See [Piglins](../mobs/Piglin.md) for their other behavior. [Repellent tag][piglin-tag] · [Sensor][piglin-sensor] · [Avoidance behavior][piglin-ai]

## Simple placement example

A source-based example, **not gameplay-tested**: place a solid ceiling block over a walkway, then use a Lantern beneath it. Keep that supporting block when rearranging the build, or collect the Lantern before removing it. For an underwater fixture, use a valid floor or ceiling support and place the Lantern in source Water; an empty Bucket can later remove its stored Water without collecting the Lantern. [Placement][lantern] · [Bucket behavior][waterlogged]

Related: [Torch](Torch.md) · [Full luminous blocks](LuminousBlocks.md) · [Copper Lighting](CopperLighting.md) · [Redstone Lamp](RedstoneLamp.md) · [Lantern item](../items/Lantern.md) · [Soul Lantern item](../items/SoulLantern.md)

## Sources and verification

Checked against pinned MattMC registrations, active placement/water/support callbacks, recipes, mining tags, loot, and trade/AI paths on **2026-10-02**. No gameplay mining, waterlogging, support-loss, or mob-avoidance test was run.

[reg-lanterns]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/Blocks.java#L5385-L5408
[items]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/Items.java
[lantern]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/LanternBlock.java
[recipe-lantern]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/recipe/crafting/lantern.json
[recipe-soul_lantern]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/recipe/crafting/soul_lantern.json
[trades]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java
[villager]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/entity/npc/Villager.java#L819-L843
[trade-choice]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L233
[support]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/Block.java#L323-L328
[default]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L338
[support-drop]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/Block.java#L213-L233
[loot-lantern]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/loot_table/blocks/lantern.json
[loot-soul_lantern]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/loot_table/blocks/soul_lantern.json
[waterlogged]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[lantern-tag]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/tags/block/lanterns.json
[gate]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[harvest]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L297
[drops]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/Block.java#L364-L420
[piglin-tag]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/tags/block/piglin_repellents.json
[piglin-sensor]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/entity/ai/sensing/PiglinSpecificSensor.java
[piglin-ai]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java
