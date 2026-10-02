# Root crops

Carrots, Potatoes, and Beetroots grow on [Farmland](Farmland.md). All three support MattMC's harvest-and-regrow controls, so a mature field can be gathered without breaking and replanting every crop.

## Starting a plot

Use the correct planting item on Farmland:

| Crop | Plant with | Fully grown age |
| --- | --- | --- |
| Carrots | [Carrot](../items/Carrot.md) | 7, from ages 0–7 |
| Potatoes | [Potato](../items/Potato.md) | 7, from ages 0–7 |
| Beetroots | [Beetroot Seeds](../items/BeetrootSeeds.md) | 3, from ages 0–3 |

A harvested Beetroot is food and a crafting ingredient, not a seed. Baked and Poisonous Potatoes do not plant the ordinary potato crop. Keep some planting items if you want to expand the field or replace broken plants.

## Growth and bone meal

The crops need raw brightness **8 or higher** to survive and **9 or higher** for natural growth. Nearby hydrated Farmland improves the growth calculation. For watering range and keeping the soil intact, see [Farmland](Farmland.md).

Natural growth uses random ticks, so it has no guaranteed harvest time. Same-crop neighbors along both horizontal axes, or on a diagonal, halve the growth-speed value. Alternating rows of different crops can avoid that same-crop crowding penalty.

- **Carrots and Potatoes:** bone meal advances the crop by **2–5 age stages**, capped at age 7.
- **Beetroots:** each random tick has an extra **one-in-three chance to skip** the ordinary growth check. Bone meal advances **0 or 1 stage**, capped at age 3, so a use can leave the visible growth unchanged.

Fewer age stages do not mean Beetroots follow the same growth schedule as Carrots or Potatoes.

## MattMC harvesting controls

- **Use an empty hand on a mature crop:** drops that crop's mature loot and resets it to age 0.
- **Use a hoe on a mature crop:** harvests the clicked crop and mature crops of the **same type** in the surrounding **3 × 3 horizontal area**, resetting each to age 0.
- The hoe's area stays at the clicked crop's height. Immature plants and neighboring crop types are left alone. Clicking an immature crop does not start an area harvest.
- The hoe requests durability damage equal to the number of crops harvested, before normal durability-modifying effects.

Retained broken hoes also reach this block-side harvest handler; their wear processing skips further damage. See [the current hoe exception](../mechanics/AxesAndHoes.md#mattmc-crop-area-harvesting), which is separate from guarded tilling.

Both harvest controls leave the planted crop in place and do not explicitly spend a replacement planting item. Empty-hand harvesting supplies an empty tool for loot; hoe harvesting supplies the actual hoe, allowing its Fortune level to affect the relevant crop drops.

## What a harvest yields

For an ordinary harvest without Fortune or explosion losses:

| Crop | Mature crop | Breaking an immature crop |
| --- | --- | --- |
| Carrots | **2–5 Carrots** | **1 Carrot** |
| Potatoes | **2–5 Potatoes**, plus a separate **2% chance of 1 Poisonous Potato** | **1 Potato** |
| Beetroots | **1 Beetroot and 1–4 Beetroot Seeds** | **1 Beetroot Seed** |

Fortune adds chances for extra Carrots, Potatoes, or Beetroot Seeds. It does not increase the mature Beetroot item count or the separate Poisonous Potato chance in these loot tables. Explosion decay can reduce drops.

Breaking a plant removes it; the two use controls above reset a mature plant instead. Harvest at maturity for the food yield and use the item pages for cooking and crafting.

## Related pages

- [Carrot](../items/Carrot.md)
- [Potato](../items/Potato.md)
- [Beetroot](../items/Beetroot.md)
- [Potoo](../mobs/Potoo.md#luring-and-care): Beetroot Seed luring and current breeding limits
- [Wheat crop](Wheat.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No growth timing, harvesting, or tool behavior was tested in-game.

- [Shared growth and MattMC harvest controls](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/CropBlock.java)
- [Beetroot growth and bone meal](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/BeetrootBlock.java) and [bone-meal consumption](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/BoneMealItem.java)
- [Planting-item registrations](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java)
- [Carrot loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/carrots.json), [Potato loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/potatoes.json), and [Beetroot loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/beetroots.json)
- [Fortune bonus calculation](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/storage/loot/functions/ApplyBonusCount.java) and [harvest tool context](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/Block.java)
