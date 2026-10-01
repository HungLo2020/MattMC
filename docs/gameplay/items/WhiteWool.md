# White Wool

**White Wool** (`minecraft:white_wool`) is a placeable building material and an ingredient for beds, carpets, and banners. A living white [Sheep](../mobs/Sheep.md) can provide repeated harvests, so keep breeding stock instead of relying only on death drops. [Item registration][item] · [Sheep production](../mobs/Sheep.md#shearing-and-regrowth)

## Obtaining

These are selected, source-confirmed routes rather than a complete list of world or merchant sources:

- **Shear a white sheep:** an eligible adult gives **1–3 white wool**. Follow the [shearing and regrowth guide](../mobs/Sheep.md#shearing-and-regrowth) for eligibility, grazing, and the leash-cutting interaction that can take priority
- **Death drop:** an unsheared adult white sheep drops **one white wool**, with mob loot enabled. Looting does not increase that wool count
- **Craft from [String](String.md):** use the [wool recipe in the String guide](String.md#selected-crafting-recipes)
- **Recolor wool:** combine **one [White Dye](WhiteDye.md)** with **one wool of any other bundled color** in a shapeless recipe to obtain **one white wool**

[Shearing loot][shear] · [Death color selection][sheep-loot] · [White death loot][death] · [Adult and mob-loot gate][adult] · [String recipe][string] · [Recoloring recipe][dye-white]

A sheep must have fleece before dye can recolor it directly. For repeated white harvests from a colored sheep, use white dye after its fleece has grown back; see [Dyeing and offspring color](../mobs/Sheep.md#dyeing-and-offspring-color). [Dye interaction][dye-item]

Breaking placed white wool normally returns **one white wool**. Its loot table has no Silk Touch requirement and includes an explosion-survival condition. [Block loot][block-loot]

## Selected crafting uses

| Result | Ingredients and arrangement |
| --- | --- |
| 3 [White Carpets](WhiteCarpet.md) | 2 white wool side by side |
| 1 [White Banner](WhiteBanner.md) | 6 white wool in two full rows, with 1 stick centered below |

White wool also makes a [White Bed](WhiteBed.md#crafting-and-use); use that page for the bed recipe and the [Bed guide](../blocks/Bed.md) for safe placement and sleeping. The table above is not an exhaustive recipe list. [Carpet recipe][carpet] · [Banner recipe][banner] · [Bed recipe][bed]

Wool can be recolored again in crafting. For example, **one white wool plus one red dye** makes **one red wool**, shapeless. Dyeing a harvested block and dyeing a living sheep are separate interactions. [Red-wool recipe][dye-red]

## Placement and fuel

Use the item on a suitable block face to place white wool. Keep wool builds away from fire and lava: the block is registered as flammable. [Block-item placement][placement] · [White-wool block][block] · [Fire registration][fire]

White wool supplies **100 default furnace burn ticks**, half the fuel needed for a typical 200-tick recipe. One wool alone cannot finish such a recipe; saving it for a bed or other craft is often more useful early on. [Fuel values][fuel] · [Wool fuel tag][wool-tag] · [Furnace fuel lookup][furnace]

Related: [Sheep](../mobs/Sheep.md) · [Shears](Shears.md) · [String](String.md) · [Crafting](../crafting/Crafting.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-01** at `4285adff2e35307c277a3a5bf54ebd064aa5e64b`. No in-game shearing, crafting, placement, or furnace test was run. Data packs can change loot, recipes, and tags. Detailed wool vibration behavior is outside this short item guide.

[item]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/Items.java#L326
[shear]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/shearing/sheep/white.json
[sheep-loot]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/entities/sheep.json
[death]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/entities/sheep/white.json
[adult]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L567
[string]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/white_wool_from_string.json
[dye-white]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/dye_white_wool.json
[dye-item]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/DyeItem.java#L25-L38
[block-loot]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/blocks/white_wool.json
[carpet]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/white_carpet.json
[banner]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/white_banner.json
[bed]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/white_bed.json
[dye-red]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/dye_red_wool.json
[placement]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/BlockItem.java
[block]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/Blocks.java#L811-L814
[fire]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/FireBlock.java#L445
[fuel]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java
[wool-tag]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/item/wool.json
[furnace]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java
