# Nether Wart

**Nether Wart** (`minecraft:nether_wart`) is a plantable brewing ingredient. Keep a replanting supply before spending your crop on potions or building materials. [Item registration][item]

## Obtaining and growing

The [Nether Wart crop guide](../blocks/NetherWart.md#finding-and-planting) owns fortress beds, fortress chest loot, Bastion housing gardens and planting on **Soul Sand**. Its [harvest table](../blocks/NetherWart.md#harvest) covers maturity, Fortune and replanting yield. Soul Soil is not an interchangeable support, and Wheat's Bone Meal and custom hoe-harvest controls do not apply. [Crop support and behavior][crop]

## Brewing

Adding Nether Wart to a Water Bottle in a fueled [Brewing Stand](../blocks/BrewingStand.md) produces an **Awkward Potion**. This is the starting stage for many effect-potion transformations; the Awkward Potion itself has no potion effect. Use the [brewing guide](../brewing/Brewing.md) for stand operation and ingredient chains. [Water-to-Awkward mix][awkward] · [Awkward registration][potions]

Not every potion starts with Nether Wart: **Water + Fermented Spider Eye → Weakness** is a direct registered route. Check the intended potion chain before adding ingredients. [Weakness exception][weakness]

## Crafting and surplus

- Use loose wart with small Nether Brick items for [Red Nether Bricks](../blocks/NetherBricks.md#crafting-and-cracking). The recipe does not accept a Nether Wart Block in its place. [Exact recipe][red-bricks]
- [Nether Wart Blocks](../blocks/NetherGroundAndVegetation.md#nether-and-warped-wart-blocks) consume loose wart, but have **no bundled reverse recipe**. Their guide owns the crafting quantity and the distinction from huge-fungus caps; do not treat them as recoverable storage for your planting or brewing supply. [Block recipe][wart-block]
- Spare wart is an accepted [Composter ingredient](../blocks/Composter.md#complete-accepted-item-list). Composting consumes it; keep the plants you need for the next harvest first. [Accepted item][compost]

## Related pages

- [Nether Wart crop](../blocks/NetherWart.md)
- [Brewing guide](../brewing/Brewing.md)
- [Nether Wart Block item](NetherWartBlock.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-09** at `4246f4e7bfc1f3ab7862272ebba5f1f38aa16953`. Item registration, brewing mixes, crop behavior, crafting recipes and the compost map were inspected. No in-game brewing, growth, crafting or collection test was run. Recipe statements describe bundled data; data packs can change the available conversions.

[item]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/item/Items.java#L1765
[crop]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/NetherWartBlock.java#L19-L70
[awkward]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L139-L142
[potions]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/item/alchemy/Potions.java
[weakness]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L187-L188
[red-bricks]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/resources/data/minecraft/recipe/crafting/red_nether_bricks.json
[wart-block]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/resources/data/minecraft/recipe/crafting/nether_wart_block.json
[compost]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L142
