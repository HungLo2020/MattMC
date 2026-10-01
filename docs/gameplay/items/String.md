# String

**String** (`minecraft:string`) is a crafting material and the item placed as tripwire. Keep some for bows, fishing rods, leads, and small storage bundles; extra string also has villager trading uses. [Registration][item] · [Recipes below](#selected-crafting-recipes) · [Trading][trades]

## Obtaining

These are confirmed examples rather than every possible source:

- [Spiders](../mobs/Spider.md) and [Cave Spiders](../mobs/CaveSpider.md) each have a **0–2 string** loot roll. Looting increases the possible maximum to **5 at Looting III**. Their string pools do not require a player-attributed kill, unlike their spider-eye pools. Mob loot must be enabled.
- Mine a [Cobweb](Cobweb.md) with a **sword without Silk Touch** to obtain **one string**. **Shears** preserve the cobweb instead. The cobweb requires a correct harvesting tool, so breaking it by hand is not an equivalent collection method.
- Breaking placed tripwire normally drops **one string**, with an explosion-survival condition on the drop.

[Spider loot][spider-loot] · [Cave-spider loot][cave-loot] · [Mob-loot rule][monster] · [Cobweb loot choices][cobweb-loot] · [Cobweb tool requirement][cobweb] · [Sword harvesting][sword] · [Shears harvesting][shears] · [Harvest gate][harvesting] · [Tripwire loot][tripwire-loot]

## Selected crafting recipes

The quantities below are the bundled recipes, not an exhaustive list. These are shaped recipes: ingredient placement matters, so use the recipe book or the linked recipe when arranging a grid.

| Result | Ingredients | Recipe |
| --- | --- | --- |
| 1 [Bow](Bow.md) | 3 string + 3 sticks | [Bow recipe][bow] |
| 1 [Fishing Rod](FishingRod.md) | 2 string + 3 sticks | [Fishing-rod recipe][rod] |
| 2 [Leads](Lead.md) | 5 string | [Lead recipe][lead] |
| 1 [Bundle](Bundle.md) | 1 string above 1 leather | [Bundle recipe][bundle] |
| 1 [White Wool](WhiteWool.md) | 4 string in a 2 × 2 square | [Wool recipe][wool] |
| 6 [Scaffolding](Scaffolding.md) | 1 string + 6 bamboo | [Scaffolding recipe][scaffolding] |

**The current lead recipe uses no slimeball.** Its 3 × 3 layout is two string in the top-left pair, two directly beneath them, and one in the bottom-right corner; the other four cells are empty. This produces two leads. [Lead recipe][lead]

## Tripwire use

Using string for placement creates **tripwire**, which works with [Tripwire Hooks](TripwireHook.md). Breaking an armed line can activate its connected hooks; using shears to break a segment marks it disarmed. Take care when collecting string from an existing trap. The [Redstone guide](../redstone/Redstone.md) covers basic circuit principles. [String registration][item] · [Placement and disarming][tripwire] · [Hook state handling][hook]

## Trading

The standard trade lists include these **base-price** offers:

- **Level-1 fisherman:** 20 string for 1 [Emerald](Emerald.md), if that offer is selected
- **Level-3 fletcher:** 14 string for 1 emerald

Professions unlock trades by level, and merchants select from their available listings. Displayed prices can change with price adjustments. [Trade lists][trades] · [Selection and levels][villager] · [Price calculation][prices]

Related: [Spider Eye](SpiderEye.md) · [Spider](../mobs/Spider.md) · [Trading](../trading/Trading.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-01** at `b81c01943c9f3254e713c365a1dd633392929cb2`, using active MattMC code and bundled data. No in-game harvesting, crafting, tripwire, or trading test was run. Data packs can change recipes and loot; this page does not claim compatibility for a complete trap or farm design.

[item]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L1351
[trades]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java
[spider-loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities/spider.json
[cave-loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities/cave_spider.json
[monster]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/monster/Monster.java
[cobweb-loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/cobweb.json
[cobweb]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/Blocks.java#L689-L700
[sword]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/ToolMaterial.java#L62-L79
[shears]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/ShearsItem.java#L32-L44
[harvesting]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L285-L292
[tripwire-loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/tripwire.json
[bow]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/bow.json
[rod]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/fishing_rod.json
[lead]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/lead.json
[bundle]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/bundle.json
[wool]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/white_wool_from_string.json
[scaffolding]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/scaffolding.json
[tripwire]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/TripWireBlock.java
[hook]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/TripWireHookBlock.java
[villager]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/npc/Villager.java#L818-L843
[prices]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/trading/MerchantOffer.java#L85-L97
