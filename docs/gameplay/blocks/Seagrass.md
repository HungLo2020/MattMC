# Seagrass

**Seagrass** is underwater vegetation with a short form (`minecraft:seagrass`) and a two-block-tall form (`minecraft:tall_seagrass`). **Use Shears to collect it**. Both forms return the ordinary [Seagrass item](../items/Seagrass.md), which plants the short form. [Block registrations][seagrass-reg] · [Item registration][sea-items] · [Short loot][grass-loot] · [Tall loot][tall-loot]

## Finding and harvesting

The bundled **Ocean** biome includes the Seagrass placement that resolves to a generator capable of placing short or tall plants over suitable underwater ground. This is one checked natural source, not a complete biome list. [Ocean features][ocean] · [Placement][grass-placed] · [Configuration][grass-config] · [Feature registration][grass-feature-reg] · [Generator][grass-feature]

Both forms break instantly and have no collision. Their ordinary loot depends on the tool:

| Plant | With Shears | Without Shears |
| --- | --- | --- |
| Short Seagrass | **1 Seagrass** | **Nothing** |
| Tall Seagrass | **2 Seagrass for the plant** | **Nothing** |

Silk Touch is not an alternative to the Shears condition, and Fortune does not increase either count. Cutting either half of tall Seagrass follows the double-plant destruction path, which handles the harvest once and removes the paired half. Breaking the support does not substitute for cutting with Shears. [Short loot][grass-loot] · [Tall loot][tall-loot] · [Double-plant harvesting][double] · [Mining dispatch][mining] · [Properties][seagrass-reg]

## Underwater planting

The block directly underneath needs a **sturdy upper face** and must **not be a Magma Block**. Item placement requires water-tagged fluid with **amount 8** at the destination, in addition to normal replaceable-space and obstruction checks. Ordinary Water sources qualify; the check also accepts full-strength falling water, rather than demanding the source flag. [Support and water predicate][grass] · [Item placement checks][placement] · [Water types and amounts][water] · [Full-strength falling water][falling-water] · [Water tag][water-tag]

The planted short form and both tall halves supply **Water source fluid states** in their own spaces. They do not use the fill/drain interaction of a waterlogged block. Removing support breaks the plant through its survival updates; tall Seagrass also requires its paired half. [Short fluid and support handling][grass] · [Tall fluid and paired support][tall] · [Vegetation updates][vegetation]

## Bone Meal and propagation

Short Seagrass has **no automatic random-tick growth** into the tall form. Use **Bone Meal** while there is a **Water block immediately above** it to turn one short plant into a two-block-tall plant. This upper-block check accepts a flowing Water block too; the resulting upper half supplies its own Water source. Tall Seagrass has no further Bone Meal growth target. [Registrations][seagrass-reg] · [Short Bone Meal callback][grass] · [Tall class][tall] · [Bone Meal dispatch][bone-use]

You can also use Bone Meal on a **sturdy block face next to full-strength Water** to attempt to scatter new underwater plants. The routine starts from a Water block with amount 8, searches nearby positions, and only places plants where their support and water checks pass. Seagrass is the normal candidate; **Warm Ocean** can also select coral-related plants. The item can be consumed without a guaranteed number of new plants. [Water-plant use and scattering][bone-water] · [Sturdy-face entry point][bone-use] · [Coral-selection biome tag][coral-biome]

A source-derived multiplication loop is to plant short Seagrass with Water above, apply Bone Meal, then harvest the tall plant with Shears for two items. Replant one to repeat. This uses the direct tall-growth and loot rules; no automated farm was tested. [Tall conversion][grass] · [Tall harvest][tall-loot]

Related: [Ocean biomes](../biomes/Oceans.md) · [Seagrass item](../items/Seagrass.md) · [Shears](../items/Shears.md) · [Kelp](Kelp.md) · [Sea Pickle](SeaPickle.md) · [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Checked both block forms, the item, one Ocean generation chain, water/support predicates, paired-half harvesting, loot, direct Bone Meal growth, and water-plant scattering. No in-game generation, placement, source-water conversion, propagation, or harvesting test was run. Data packs can change world generation, tags, and loot.

[seagrass-reg]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L784-L806
[sea-items]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L324-L325
[grass-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/seagrass.json
[tall-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/tall_seagrass.json
[ocean]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/ocean.json#L82-L83
[grass-placed]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/placed_feature/seagrass_normal.json
[grass-config]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/configured_feature/seagrass_short.json
[grass-feature-reg]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L114
[grass-feature]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/levelgen/feature/SeagrassFeature.java
[double]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/DoublePlantBlock.java
[mining]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[grass]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/SeagrassBlock.java
[placement]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/BlockItem.java#L111-L138
[water]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/material/WaterFluid.java
[water-tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/fluid/water.json
[tall]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/TallSeagrassBlock.java
[vegetation]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L27-L47
[bone-use]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/BoneMealItem.java#L35-L77
[bone-water]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/BoneMealItem.java#L80-L140
[coral-biome]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/worldgen/biome/produces_corals_from_bonemeal.json

[falling-water]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L192-L201
