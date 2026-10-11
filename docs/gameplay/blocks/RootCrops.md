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
- **Beetroots:** each random tick has an extra **one-in-three chance to skip** the ordinary growth check. On an immature crop, bone meal advances **0 stages with a 25% chance** or **1 stage with a 75% chance**, capped at age 3. A Survival use still spends the Bone Meal when the result is zero.

Fewer age stages do not mean Beetroots follow the same growth schedule as Carrots or Potatoes.

## MattMC harvesting controls

- **Use an empty main hand on a mature crop:** drops that crop's mature loot and resets it to age 0.
- **Use a hoe on a mature crop:** harvests the clicked crop and mature crops of the **same type** in the surrounding **3 × 3 horizontal area**, resetting each to age 0.
- The hoe's area stays at the clicked crop's height. Immature plants and neighboring crop types are left alone. Clicking an immature crop does not start an area harvest.
- The hoe requests durability damage equal to the number of crops harvested, before normal durability-modifying effects.

Do not hold secondary use while harvesting with a hoe. When either hand holds an item, that modifier skips the crop's block interaction; it can also prevent empty-main-hand harvesting when the offhand is occupied.

Retained broken hoes also reach this block-side harvest handler; their wear processing skips further damage. See [the current hoe exception](../mechanics/AxesAndHoes.md#mattmc-crop-area-harvesting), which is separate from guarded tilling.

Once normal block interaction is reached, main-hand use with a non-hoe item can also fall through to the single-crop reset. That fallback still supplies an empty loot tool: merely holding a Fortune-enchanted non-hoe does not apply Fortune to this reset harvest. An empty main hand makes the intended action clearest.

Both harvest controls leave the planted crop in place and do not explicitly spend a replacement planting item. Empty-hand harvesting supplies an empty tool for loot; hoe harvesting supplies the actual hoe, allowing its Fortune level to affect the relevant crop drops.

## What a harvest yields

Without Fortune, with the bundled loot tables, block drops enabled and no explosion losses:

| Crop | Mature crop | Breaking an immature crop |
| --- | --- | --- |
| Carrots | **2–5 Carrots** | **1 Carrot** |
| Potatoes | **2–5 Potatoes**, plus a separate **2% chance of 1 Poisonous Potato** | **1 Potato** |
| Beetroots | **1 Beetroot and 1–4 Beetroot Seeds** | **1 Beetroot Seed** |

Fortune adds chances for extra Carrots, Potatoes, or Beetroot Seeds. It does not increase the mature Beetroot item count or the separate Poisonous Potato chance in these loot tables. Explosion decay can reduce drops.

### Fortune and replanting

The following ranges are per mature crop. For Potatoes, the separate 2% Poisonous Potato chance remains in addition to the ordinary Potato count.

| Fortune on the harvest tool | Carrots or ordinary Potatoes | Beetroot Seeds, alongside 1 Beetroot |
| --- | --- | --- |
| None, including empty-hand reset harvest | **2–5** | **1–4** |
| Fortune I | **2–6** | **1–5** |
| Fortune II | **2–7** | **1–6** |
| Fortune III | **2–8** | **1–7** |

Carrots and Potatoes start with two guaranteed items at maturity; Beetroot Seeds start with one. Each then gets **3 plus the Fortune level** bonus rolls, each with about a **57.14%** chance of adding one item. Counts within each range are **not equally likely**. Fortune does not improve the immature crop's one planting item. The hoe's area harvest applies the tool's loot context separately to each selected mature crop; it does not guarantee the maximum yield for the whole area.

Breaking removes the plant. In Survival, reserve **one Carrot, one Potato or one Beetroot Seed** to replant that space. After an unenchanted mature harvest and replanting, this leaves **1–4 spare Carrots or Potatoes**, or **0–3 spare Beetroot Seeds plus the Beetroot**. A single Beetroot harvest can therefore maintain its planting space without providing a spare seed to expand the field. A Poisonous Potato cannot pay the replanting cost.

MattMC's mature reset controls spend no replacement planting item, so all their dropped food and seeds remain available. If block drops are disabled, the handler can still reset the crop without spawning loot. Data packs can replace the yields above. Harvest at maturity for the food yield and use the item pages for cooking and crafting.

## Related pages

- [Carrot](../items/Carrot.md)
- [Potato](../items/Potato.md)
- [Beetroot](../items/Beetroot.md)
- [Potoo](../mobs/Potoo.md#luring-and-care): Beetroot Seed luring and current breeding limits
- [Wheat crop](Wheat.md)
- [Blocks](Blocks.md)

## Sources and verification

Harvest routing, exact yields, replanting costs and Beetroot bone-meal probabilities rechecked at `9e012a49b2535c67df6e8b3af3a3be7c778076c4` on **2026-10-11**. These are source-derived rules, not in-game timing, interaction or yield measurements. Current native age defaults and mature random-tick eligibility were also checked; the original review below remains historical.

- [Current Carrot loot](https://github.com/HungLo2020/MattMC/blob/9e012a49b2535c67df6e8b3af3a3be7c778076c4/src/main/resources/data/minecraft/loot_table/blocks/carrots.json), [Potato loot](https://github.com/HungLo2020/MattMC/blob/9e012a49b2535c67df6e8b3af3a3be7c778076c4/src/main/resources/data/minecraft/loot_table/blocks/potatoes.json), [Beetroot loot](https://github.com/HungLo2020/MattMC/blob/9e012a49b2535c67df6e8b3af3a3be7c778076c4/src/main/resources/data/minecraft/loot_table/blocks/beetroots.json) and [binomial Fortune calculation](https://github.com/HungLo2020/MattMC/blob/9e012a49b2535c67df6e8b3af3a3be7c778076c4/src/main/java/net/minecraft/world/level/storage/loot/functions/ApplyBonusCount.java#L66-L115)
- [Mature reset/hoe handlers](https://github.com/HungLo2020/MattMC/blob/9e012a49b2535c67df6e8b3af3a3be7c778076c4/src/main/java/net/minecraft/world/level/block/CropBlock.java#L195-L252), [default non-hoe fallback](https://github.com/HungLo2020/MattMC/blob/9e012a49b2535c67df6e8b3af3a3be7c778076c4/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L205-L213), [server hand/secondary-use dispatch](https://github.com/HungLo2020/MattMC/blob/9e012a49b2535c67df6e8b3af3a3be7c778076c4/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L340-L388), [tool and drop-rule context](https://github.com/HungLo2020/MattMC/blob/9e012a49b2535c67df6e8b3af3a3be7c778076c4/src/main/java/net/minecraft/world/level/block/Block.java#L355-L425) and [one-item planting cost](https://github.com/HungLo2020/MattMC/blob/9e012a49b2535c67df6e8b3af3a3be7c778076c4/src/main/java/net/minecraft/world/item/BlockItem.java#L65-L83)
- [Beetroot age increment](https://github.com/HungLo2020/MattMC/blob/9e012a49b2535c67df6e8b3af3a3be7c778076c4/src/main/java/net/minecraft/world/level/block/BeetrootBlock.java#L52-L61), [inclusive integer draw](https://github.com/HungLo2020/MattMC/blob/9e012a49b2535c67df6e8b3af3a3be7c778076c4/src/main/java/net/minecraft/util/Mth.java#L143-L145) and [Bone Meal consumption](https://github.com/HungLo2020/MattMC/blob/9e012a49b2535c67df6e8b3af3a3be7c778076c4/src/main/java/net/minecraft/world/item/BoneMealItem.java#L64-L79)
- [Native crop definitions](https://github.com/HungLo2020/MattMC/blob/9e012a49b2535c67df6e8b3af3a3be7c778076c4/src/main/rust/content/block/definitions/catalog.rs), [age-0 templates](https://github.com/HungLo2020/MattMC/blob/9e012a49b2535c67df6e8b3af3a3be7c778076c4/src/main/rust/content/block/definitions/templates.rs) and [age-based random-tick policy](https://github.com/HungLo2020/MattMC/blob/9e012a49b2535c67df6e8b3af3a3be7c778076c4/src/main/rust/content/block/definitions/policy.rs#L101-L105)

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No growth timing, harvesting, or tool behavior was tested in-game.

- [Shared growth and MattMC harvest controls](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/CropBlock.java)
- [Beetroot growth and bone meal](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/BeetrootBlock.java) and [bone-meal consumption](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/BoneMealItem.java)
- [Planting-item registrations](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java)
- [Carrot loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/carrots.json), [Potato loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/potatoes.json), and [Beetroot loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/beetroots.json)
- [Fortune bonus calculation](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/storage/loot/functions/ApplyBonusCount.java) and [harvest tool context](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/Block.java)
