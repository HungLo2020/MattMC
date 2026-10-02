# Kelp

**Kelp** (`minecraft:kelp`) is the item used to plant Kelp and the harvest from both its tip and stem. Its [block guide](../blocks/Kelp.md) covers the verified natural source, underwater placement, growth, Bone Meal, and harvesting. [Item registration][kelp-item] · [Tip loot][loot] · [Stem loot][stem-loot]

## Obtaining and uses

Ordinary mining gives **one Kelp per plant block**, with no tool or Silk Touch requirement and no Fortune bonus. Leave a rooted section for [repeat harvesting](../blocks/Kelp.md#finding-and-harvesting). [Plant properties][kelp-blocks] · [Tip loot][loot] · [Stem loot][stem-loot]

Plant it in [suitable full-strength water](../blocks/Kelp.md#planting-and-water), or process it using the [Dried Kelp cooking recipes](DriedKelp.md#drying-kelp). The raw item has no food component; **Dried Kelp** is the edible form. Raw Kelp is not a default Furnace fuel. [Planting][kelp] · [Raw item registration][kelp-item] · [Food registration][dry-item] · [Fuel list][fuel]

Related: [Dried Kelp](DriedKelp.md) · [Dried Kelp Block](DriedKelpBlock.md) · [Seagrass](Seagrass.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Registration, placement, both plant loot tables, food distinction, and default fuels checked. No in-game test was run.

[kelp-item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L370
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/kelp.json
[stem-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/kelp_plant.json
[kelp-blocks]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L4733-L4751
[kelp]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/KelpBlock.java
[dry-item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L1747
[fuel]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L108
