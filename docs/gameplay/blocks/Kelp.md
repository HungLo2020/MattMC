# Kelp

**Kelp** is a harvestable underwater plant. Its growing tip is `minecraft:kelp`, and the stem below is `minecraft:kelp_plant`; both return the same [Kelp item](../items/Kelp.md). Dry the harvest for [food](../items/DriedKelp.md) or pack it into [Dried Kelp Blocks](../items/DriedKelpBlock.md) for storage and fuel. [Registrations][kelp-blocks] · [Item][kelp-item] · [Tip loot][loot] · [Stem loot][stem-loot]

## Finding and harvesting

The bundled **Ocean** biome includes the Kelp placement that resolves to the active Kelp generator. It places a stem and growing tip in suitable underwater space. This is one verified starting source, not a complete biome or acquisition list. [Ocean features][ocean] · [Placement][placed] · [Configuration][configured] · [Feature registration][feature-reg] · [Generator][feature]

Both tip and stem break instantly, have no collision, and require **no harvesting tool**. Ordinary mining gives **one Kelp per block**, without a Silk Touch requirement or Fortune multiplier. The loot tables use explosion-survival conditions. [Properties][kelp-blocks] · [Tip loot][loot] · [Stem loot][stem-loot] · [Tool gate][gate] · [Mining dispatch][break]

For repeat harvests, leave a rooted section and break above it. Unsupported upper sections schedule a check one tick later and break; the remaining exposed stem becomes a new tip with a fresh random age. That tip can grow again when Water remains above. [Stem updates][stem] · [Support destruction][support] · [New-tip age][head]

## Planting and water

Use Kelp in water above **another Kelp section or a block with a sturdy upper face**. **Magma Blocks are explicitly excluded** as a supporting block. No light threshold appears in the planting or growth checks. [Attachment and placement][kelp] · [Shared support][support]

The item-placement predicate requires water-tagged fluid with **amount 8**. A normal Water source qualifies; the predicate does not require the fluid's source flag, so full-strength falling water can also meet it. Weaker horizontal flow does not meet this placement test. [Placement predicate][kelp] · [Water amount/source definitions][water] · [Full-strength falling water][falling-water]

Growth uses a different check: the next space must be the **Water block**, including its flowing states. Every Kelp tip or stem supplies a **Water source fluid state** at its own position. Thus growing or placing Kelp can turn its occupied water cells into sources; it does not allow growth into air or arbitrary waterlogged blocks. Kelp itself has no bucket-pickup or fill interaction. [Growth target and fluid state][kelp] · [Stem fluid state][body]

## Growth, Bone Meal, and trimming

Only the tip grows. On each of its random ticks, an unfinished tip has a **14% growth chance**, provided the next block above is Water. A new tip starts at a random **age from 0 through 24**. Each successful natural growth step increases age by one; **age 25 stops natural growth**. [Kelp probability and target][kelp] · [Age and random growth][head] · [Server random ticks][random]

Age is not the same as total plant height. A new single-block planting with uninterrupted water above can add **1–25 blocks** before its initial age allowance runs out. Cutting, manual extension, and Bone Meal can change the resulting height; there is no fixed 26-block height check in this growth routine. [Placement age and growth][head] · [Stem-to-tip reset][stem]

**Bone Meal adds one block** when Water is available above the tip, even if the tip is already age 25. The new tip's age is capped at 25, so repeated Bone Meal can keep extending a mature plant through available Water. Using Bone Meal on a connected stem finds its tip and applies the same growth rule there. [Kelp Bone Meal count][kelp] · [Tip Bone Meal][head] · [Stem forwarding][stem] · [Bone Meal use][bone]

Use **unbroken Shears on an unfinished tip**, rather than mining it, to set its age to 25 and stop automatic growth. This costs one durability and leaves the plant in place. It does not prevent a later valid Bone Meal application from extending it. [Shears interaction][shears] · [Broken-item use guard][broken] · [Mature-tip Bone Meal][head]

## Dried Kelp blocks

The [Dried Kelp item](../items/DriedKelp.md#drying-kelp) owns the Furnace, Smoker, and Campfire recipes and food values. The [Dried Kelp Block item](../items/DriedKelpBlock.md) owns packing, unpacking, and its fuel value.

Placed **Dried Kelp Blocks** (`minecraft:dried_kelp_block`) are ordinary full building blocks with **0.5 hardness** and **2.5 blast resistance**. They need no continuing support and do not fall. Any ordinary mining tool or bare hand can collect one block; hoes get their tagged mining speed. Silk Touch is unnecessary and Fortune does not change the self-drop. Explosion recovery is conditional. [Properties][pack-block] · [Full-block shape and support][shape] · [Hoe tag][hoe] · [Tool rules][tools] · [Loot][pack-loot]

Related: [Ocean biomes](../biomes/Oceans.md) · [Kelp item](../items/Kelp.md) · [Dried Kelp](../items/DriedKelp.md) · [Dried Kelp Block](../items/DriedKelpBlock.md) · [Seagrass](Seagrass.md) · [Sea Pickle](SeaPickle.md) · [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Checked plant/tip/stem registrations, the Ocean generation chain, exact water/support predicates, natural growth, Bone Meal, Shears use, support updates, loot, and the packed block's properties. No in-game generation, planting, source-water conversion, harvesting, growth, trimming, or mining test was run. Data packs can change world generation, tags, recipes, and loot.

[kelp-blocks]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L4733-L4751
[kelp-item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L370
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/kelp.json
[stem-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/kelp_plant.json
[ocean]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/ocean.json#L82-L83
[placed]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/placed_feature/kelp_cold.json
[configured]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/configured_feature/kelp.json
[feature-reg]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L114-L115
[feature]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/levelgen/feature/KelpFeature.java
[gate]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[break]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[stem]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/GrowingPlantBodyBlock.java
[support]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/GrowingPlantBlock.java
[head]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/GrowingPlantHeadBlock.java
[kelp]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/KelpBlock.java
[water]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/material/WaterFluid.java
[body]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/KelpPlantBlock.java
[random]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerLevel.java#L487-L507
[bone]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L77
[shears]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ShearsItem.java#L61-L85
[broken]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L357
[pack-block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L4749-L4751
[shape]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L326
[hoe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/mineable/hoe.json
[tools]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ToolMaterial.java#L20-L48
[pack-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/dried_kelp_block.json

[falling-water]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L192-L201
