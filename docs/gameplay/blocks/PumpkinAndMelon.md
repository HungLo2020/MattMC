# Pumpkin and Melon farming

**Pumpkins** (`minecraft:pumpkin`) and **Melons** (`minecraft:melon`) grow as separate fruit blocks beside a planted stem. Harvest the fruit and leave the stem for another crop. Their growing space and harvesting controls differ from [Wheat](Wheat.md).

## Finding a starting crop

Pumpkin patches are wired into many bundled biomes, including Plains and Forest. They attempt to place Pumpkins in air above Grass Blocks. Melon patches are wired into Jungle and Bamboo Jungle, with a [less frequent placement variant][sparse-placement] in Sparse Jungle; they require replaceable, fluid-free space above Grass Blocks. These checks establish natural starting sources, but do not guarantee a patch in any particular chunk. [Plains][plains] · [Forest][forest] · [Pumpkin patch][pumpkin-patch] · [Jungle][jungle] · [Bamboo Jungle][bamboo] · [Sparse Jungle][sparse] · [Melon patch][melon-patch]

Collect fruit and make [Pumpkin Seeds](../items/PumpkinSeeds.md) or [Melon Seeds](../items/MelonSeeds.md). The item pages own the seed recipes. Breaking an ordinary Melon supplies [Melon Slices](../items/MelonSlice.md); use one of those slices for the seed recipe.

## Laying out a plot

1. Plant the appropriate seeds on **Farmland**
2. Leave at least one of the stem's four horizontal neighboring spaces **air**, with **Farmland or a Dirt-tag block underneath**
3. Provide **raw brightness 9 or higher at the stem** for natural growth and fruit attempts
4. Keep the stem's Farmland intact and harvest fruit without breaking the stem

Dirt, Grass Block, Coarse Dirt, Podzol, and Moss Block are examples of valid fruit-floor blocks. Sand is not accepted by this fruit-growth check. More clear neighboring spaces give a mature stem more valid directions when it chooses where to try growing fruit. [Stem behavior][stem] · [Dirt tag][dirt] · [Planting items][items]

The stem's own support must be Farmland. Its survival check does **not** impose Wheat's brightness-8 rule: low light pauses natural progress rather than failing the stem's checked support condition. Water is not an immediate planting requirement, but hydrated Farmland improves growth; see [Farmland](Farmland.md) for hydration range and protection from trampling. Both ordinary and attached stems are in the tag that keeps dry Farmland from reverting solely because it has no maintained crop. [Vegetation survival][vegetation] · [Growth calculation][growth] · [Farmland maintenance][farmland] · [Maintaining crops][maintains]

## Stem growth and Bone Meal

A stem progresses through ages **0–7**. On an eligible random tick, its growth-speed calculation uses the Farmland below and in the surrounding **3 × 3 soil area**. Hydrated soil contributes more than dry soil. Same-type ordinary stems on both horizontal axes, or on a diagonal, halve that calculated speed. The actual check is random, so there is no guaranteed growth time. [Stem growth][stem] · [Soil and crowding][growth]

At age 7, a successful growth check chooses **one random horizontal direction**. If that square is air with suitable ground, the game places one fruit and changes the stem into an attached stem pointing toward it. A blocked direction wastes that attempt, even if another side is clear. [Fruit creation][stem]

Bone Meal advances an immature stem by **2–5 stages**, capped at 7. Reaching age 7 immediately runs one ordinary growth attempt, so fruit is **not guaranteed**: brightness, the random check, chosen direction, and space still matter. An already age-7 stem and an attached stem are not valid Bone Meal targets. [Bone Meal behavior][stem] · [Target and consumption checks][bonemeal]

## Harvesting and keeping the stem

Break the fruit block for its yield. Removing the fruit that an attached stem points toward changes that stem back to **age 7**, ready for later fruit attempts. You do not need to plant a new seed for each fruit harvest. [Attached-stem update][attached]

| Block broken | Ordinary result before explosion losses |
| --- | --- |
| Pumpkin | **1 Pumpkin** |
| Melon without Silk Touch | **3–7 Melon Slices** before Fortune; Fortune can add slices, with the final count capped at **9** |
| Melon with Silk Touch | **1 Melon block** |
| Pumpkin or Melon stem | **0–3 matching seeds**; later ages improve the chance, and attached stems use the mature chance |

The stem loot can produce **no seeds**, even when mature. It has no Fortune bonus. Preserving a healthy stem is therefore safer than breaking and replanting it after each fruit. The Pumpkin drop uses an explosion-survival condition; Melon slices and stem seeds use explosion decay. [Pumpkin loot][pumpkin-loot] · [Melon loot][melon-loot] · [Pumpkin stem][pumpkin-stem-loot] · [Melon stem][melon-stem-loot] · [Attached Pumpkin stem][attached-pumpkin-loot] · [Attached Melon stem][attached-melon-loot]

Pumpkin and Melon are in the Axe mining tag, and neither registration requires a correct tool for drops. Their hardness and blast resistance are both **1**. A tool is not required for the basic yield, although Silk Touch or Fortune changes Melon drops as shown above. [Registrations][registry] · [Axe tag][axe]

These plants and fruits do **not** inherit MattMC's empty-hand crop reset or **3 × 3 hoe harvesting**. A hoe is useful for preparing soil, but use ordinary block harvesting for fruit. [Wheat-style harvest controls][crop] · [Registered block types][registry]

## Carving a Pumpkin

Use **Shears** on an uncarved Pumpkin to replace it with a [Carved Pumpkin](../items/CarvedPumpkin.md) and drop **4 Pumpkin Seeds**. The action normally attempts **one point of Shears wear**, subject to normal durability modifiers. It changes the placed block rather than giving an additional uncarved Pumpkin. [Carving interaction][carve] · [Carving loot][carve-loot]

**Fully worn Shears:** the Pumpkin block handler checks Shears identity and runs before the generic broken-item guard. The durability handler ignores further wear once the retained stack is broken, so this carving route remains reachable in the checked source. See [Durability and broken items](../mechanics/Durability.md); this exception has not been tested in-game and should not be generalized to other Shears uses. [Carving handler][carve] · [Server routing][use] · [Item-stack guard][broken-guard] · [Wear handling][wear]

Related: [Pumpkin item](../items/Pumpkin.md) · [Melon item](../items/Melon.md) · [Melon Slice](../items/MelonSlice.md) · [Sugar Cane](SugarCane.md) · [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-01** at `4285adff2e35307c277a3a5bf54ebd064aa5e64b`. No in-game generation, layout comparison, Bone Meal, harvesting, or carving test was run. Recipes and loot describe bundled data; data packs can replace them.

The review checked the registered block types, their current growth and neighbor-update signatures, [block-state dispatch][dispatch], and the server's [random-tick][ticks] and [block-use][use] callers. [Registry loading][worldgen] and [biome decoration][decoration] connect the natural-patch resources to generation. [Loot loading][loot-loading] connects the checked block and carving tables to server drops.

[plains]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/biome/plains.json
[forest]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/biome/forest.json
[pumpkin-patch]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/configured_feature/patch_pumpkin.json
[jungle]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/biome/jungle.json
[bamboo]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/biome/bamboo_jungle.json
[sparse]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/biome/sparse_jungle.json
[melon-patch]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/configured_feature/patch_melon.json
[stem]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/StemBlock.java
[attached]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/AttachedStemBlock.java#L63-L87
[dirt]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/block/dirt.json
[items]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/Items.java#L1746-L1749
[vegetation]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L26-L50
[growth]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/CropBlock.java#L105-L149
[farmland]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/FarmBlock.java#L95-L130
[maintains]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/block/maintains_farmland.json
[bonemeal]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/BoneMealItem.java#L66-L85
[pumpkin-loot]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/blocks/pumpkin.json
[melon-loot]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/blocks/melon.json
[pumpkin-stem-loot]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/blocks/pumpkin_stem.json
[melon-stem-loot]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/blocks/melon_stem.json
[attached-pumpkin-loot]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/blocks/attached_pumpkin_stem.json
[attached-melon-loot]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/blocks/attached_melon_stem.json
[registry]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/Blocks.java#L2332-L2378
[axe]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[crop]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/CropBlock.java#L193-L249
[carve]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/PumpkinBlock.java
[carve-loot]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/carve/pumpkin.json
[dispatch]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L765-L837
[ticks]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/server/level/ServerLevel.java#L487-L504
[use]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L340-L399
[worldgen]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L106
[decoration]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L308-L400
[loot-loading]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L48-L75
[sparse-placement]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/placed_feature/patch_melon_sparse.json
[broken-guard]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L370
[wear]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/ItemStack.java#L449-L485
