# Soul Campfire

The **Soul Campfire** item places `minecraft:soul_campfire`. It cooks up to four individual foods at once and provides light while lit. See [Campfires](../blocks/Campfires.md) for placement, the full cooking list, Water, smoke, Bees, and safe use. [Item registration][items] · [Shared block behavior][campfire]

## Obtaining

Craft **one Soul Campfire** in a Crafting Table with three Sticks, three tagged logs, and **one Soul Sand or Soul Soil**. Use the [canonical recipe layout and log choices](../blocks/Campfires.md#variants-and-crafting); the recipe accepts mixed tagged logs, including the Nether stem families. [Recipe][recipe-soul_campfire] · [Log choices][logs] [burnlogs][] [crimson][] [warped]

Mine the placed block with **Silk Touch** to recover this item. Ordinary harvesting instead gives **one Soul Soil**; an axe is faster but does not substitute for Silk Touch. Fortune does not increase that drop. [Loot][loot-soul_campfire] · [Registration][blocks] · [Axe tag][axe]

The crafting recipe accepts either soul material, but the ordinary mining byproduct is always Soul Soil. The item also has a Creative entry. [Soul ingredient tag][soul-base] · [Loot][loot-soul_campfire] · [Creative entry][creative]

## Usage

Normal placement starts lit; source-Water placement starts waterlogged and unlit. Lit Soul Campfires emit light level **10** and apply base contact damage of **two health points** through the living-entity fire-damage path. Follow the [damage and immunity rules](../blocks/Campfires.md#contact-damage-and-immunity) when building around them. [Registration][blocks] · [Placement and contact][campfire]

Use the block with eligible food to insert one item per use. Both Campfire variants take **600 game ticks per item**, about 30 seconds at normal tick speed, and need no ongoing fuel. Extinguishing keeps food in place while its progress cools; finished food drops into the world. [Cooking details](../blocks/Campfires.md#cooking-four-items) · [Insertion][insert] · [Cook/cooldown loops][cooking]

## Behavior

Use Flint and Steel or a Fire Charge to relight a dry block, and a Shovel or Water to extinguish it. A Hay Bale directly underneath changes the smoke particles. Silk Touch recovers the block but **spills unfinished ingredients separately**, rather than storing them in its item. [Lighting][flint] [charge] · [Extinguishing][shovel] [campfire] · [Spill][spill] · [Loot][loot-soul_campfire]

## Notes

Source-reviewed at `96e5604a6abaec697de2004b1ba9775e303bfba7` on 2026-10-02. No gameplay test was run. The canonical [Campfires guide](../blocks/Campfires.md) owns the shared placed-block mechanics.

[items]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/Items.java#L2455-L2460
[campfire]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/CampfireBlock.java
[recipe-soul_campfire]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/recipe/crafting/soul_campfire.json
[logs]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/item/logs.json
[burnlogs]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/item/logs_that_burn.json
[crimson]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/item/crimson_stems.json
[warped]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/item/warped_stems.json
[loot-soul_campfire]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/loot_table/blocks/soul_campfire.json
[blocks]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/Blocks.java#L5423-L5447
[axe]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[soul-base]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/item/soul_fire_base_blocks.json
[creative]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1080-L1081
[insert]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/entity/CampfireBlockEntity.java#L168-L188
[cooking]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/entity/CampfireBlockEntity.java#L41-L100
[flint]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/FlintAndSteelItem.java
[charge]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/FireChargeItem.java
[shovel]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/item/ShovelItem.java
[spill]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/entity/CampfireBlockEntity.java#L200-L217
