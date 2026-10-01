# Magma Cream

**Magma Cream** (`minecraft:magma_cream`) is used to brew **Fire Resistance** and craft [Magma Blocks](MagmaBlock.md). Craft it from renewable ingredients or collect it from larger [Magma Cubes](../mobs/MagmaCube.md). The ordinary item stacks to **64**. [Item registration][items] · [Default item components][components]

## Crafting

Combine **1 [Blaze Powder](BlazePowder.md) + 1 [Slimeball](Slimeball.md)** in any arrangement to make **1 Magma Cream**. This two-ingredient recipe fits the inventory crafting grid. [Magma Cream recipe][cream-recipe]

## Other sources

With mob loot enabled, **medium and large Magma Cubes** can drop **0–1 Magma Cream** before Looting. The base count is rolled from −2 through 1, giving a **25% chance of one cream without Looting**. Looting can raise the maximum to **4 at Looting III**, but a kill can still give none. This pool does not require player attribution, excludes frog kills, and does not apply to the smallest cubes. See [Magma Cube](../mobs/MagmaCube.md#drops-and-froglights) for the full size and drop conditions. [Entity loot][loot] · [Count rolls][uniform] · [Looting bonus][looting] · [Empty-count handling][stack]

Bundled bastion chest loot also includes Magma Cream: **2–6 per selected entry** in the `bastion_other` table and **3–8 per selected entry** in `bastion_treasure`. These are entry amounts, not guaranteed totals for every chest. The treasure layout's center templates assign the treasure table to actual chests. [Other-chest loot][other-chest] · [Treasure loot][treasure-chest] · [Treasure chest template][chest-template] · [Template block-entity loading][template-load]

## Fire Resistance brewing

1. Make **Awkward Potion** from Water Bottle and Nether Wart
2. Brew it with **Magma Cream** for **Potion of Fire Resistance**, lasting **3 minutes** in its ordinary drinkable form
3. Add **Redstone Dust** to that Fire Resistance potion for the extended **8-minute** form

The durations assume normal 20-TPS ticking. The stand also needs **Blaze Powder as fuel**; the powder used to craft cream is a separate ingredient cost. **Magma Cream added directly to Water Bottle makes Mundane Potion**, not Fire Resistance. The checked list has no Glowstone recipe for Fire Resistance II. [Registered mixes and start-mix behavior][brewing] · [Potion durations][potions] · [Brewing setup](../brewing/Brewing.md)

Fire Resistance rejects fire-tagged damage. It does **not** protect against a Magma Cube's separate physical contact attack. Read the [Magma Cube guide](../mobs/MagmaCube.md#sizes-and-combat) before relying on a potion in a cube-filled area. [Effect damage check][effects] · [Mob-attack damage type][damage-type] · [Fire-damage tag][fire-tag]

## Magma Block crafting

Arrange **4 Magma Cream in a 2×2 square** to craft **1 [Magma Block](MagmaBlock.md)**. This recipe fits the inventory grid. [Magma Block recipe][block-recipe]

Related: [Magma Cube](../mobs/MagmaCube.md) · [Slimeball](Slimeball.md) · [Brewing](../brewing/Brewing.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-01** at `4285adff2e35307c277a3a5bf54ebd064aa5e64b`. No in-game chest, drop-rate, crafting, or potion test was run. Recipe and loot data were traced to the active loaders, and the brewing list to server initialization. Active data packs and later builds can change availability. [Recipe loading][recipe-load] · [Loot loading][loot-load] · [Server brewing initialization][brewing-init]

[items]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/Items.java
[components]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L390
[cream-recipe]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/magma_cream.json
[loot]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/entities/magma_cube.json
[uniform]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/storage/loot/providers/number/UniformGenerator.java
[looting]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java#L64-L80
[stack]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/ItemStack.java#L1054-L1069
[other-chest]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/chests/bastion_other.json
[treasure-chest]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/chests/bastion_treasure.json
[chest-template]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/structure/bastion/treasure/bases/centers/center_0.nbt
[template-load]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java#L263-L312
[brewing]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java
[potions]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/alchemy/Potions.java#L26-L31
[effects]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/LivingEntity.java
[damage-type]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/damage_type/mob_attack.json
[fire-tag]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/damage_type/is_fire.json
[block-recipe]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/magma_block.json
[recipe-load]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L59-L90
[loot-load]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/server/ReloadableServerRegistries.java
[brewing-init]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/server/MinecraftServer.java#L338-L340
