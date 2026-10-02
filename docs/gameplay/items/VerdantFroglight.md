# Verdant Froglight

**Verdant Froglight** (`minecraft:verdant_froglight`) is a full lighting block with **light level 15**. Its ordinary production route depends on a Frog's variant. [Item registration][items] · [Block and light][blocks]

## Obtaining

Let a **cold Frog** eat a **smallest-size Magma Cube**. When that attack kills the cube and mob loot is enabled, the bundled loot branch returns **one Verdant Froglight**. The Frog must be able to reach and attack its prey; a larger cube is not eligible for the ordinary eating behavior. [Prey and size check][frog] · [Accepted prey][prey] · [Active eating behavior][frog-ai] · [Attack callback][tongue] · [Variant-specific loot][magma-loot] · [Mob-loot rule][mob-loot]

Use [Frog variants](../mobs/Frog.md#variants-and-where-to-grow-tadpoles) to raise the required Frog and [Magma Cube drops](../mobs/MagmaCube.md#drops-and-froglights) for the production rules. No Verdant Froglight crafting recipe was found in the checked [bundled recipe data][recipes]. Its inventory item is also listed in Creative contents. [Creative listing][creative]

## Usage

Place it for solid lighting or decoration. The clicked face selects the pillar-texture axis; orientation does not change its light level. See [Froglight placement](../blocks/LuminousBlocks.md#blocks-and-light) for shared behavior. [Axis placement][axis] · [Light registration][blocks]

## Behavior

With ordinary block drops enabled, mining it returns **one matching Verdant Froglight**, including by hand. The registration has no required-tool drop gate, and the self-drop table has no Silk Touch or Fortune branch. Its explosion-survival condition is separate from normal mining. [Registration][blocks] · [Harvest gate][gate] · [Block loot][loot] · [Block-drop rule][drops]

## Notes

Source-reviewed on **2026-10-02** at `eaeeffdeb9220de7d839af7c84a047693249c2f7`. No in-game Frog, loot, mining, placement, or lighting test was run. Data packs and game rules can alter the checked results.

Related: [All Froglights](../blocks/LuminousBlocks.md#froglights) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/item/Items.java
[blocks]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/level/block/Blocks.java
[frog]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/animal/frog/Frog.java#L357-L359
[prey]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/resources/data/minecraft/tags/entity_type/frog_food.json
[frog-ai]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/animal/frog/FrogAi.java#L197-L204
[tongue]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/animal/frog/ShootTongue.java#L87-L98
[magma-loot]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/resources/data/minecraft/loot_table/entities/magma_cube.json
[mob-loot]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L568
[recipes]: https://github.com/HungLo2020/MattMC/tree/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/resources/data/minecraft/recipe
[creative]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[axis]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/level/block/RotatedPillarBlock.java#L53-L55
[gate]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[loot]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/resources/data/minecraft/loot_table/blocks/verdant_froglight.json
[drops]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/level/block/Block.java#L410-L415
