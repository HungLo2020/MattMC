# Vines and Glow Berries

Ordinary **Vines**, **Weeping Vines**, **Twisting Vines**, and **Cave Vines** all provide climbable vegetation, but their support, growth, and recovery rules differ. Carry Shears for ordinary Vines, preserve the supported end of a Nether-vine column when harvesting, and pick Glow Berries without breaking the Cave Vine whenever you want to keep producing fruit. [Climbable block tag] · [Ordinary Vine support] · [Column placement and support] · [Berry collection and light]

[Glow Lichen](GlowLichen.md) has its own guide because its surface spreading and waterlogging differ. Imported [Archaic Vine](PrimordialPlants.md#archaic-vine) remains under Primordial Plants; its appearance is not evidence that it shares these climbing or growth rules.

## Registered blocks and inventory forms

The `_plant` IDs below are body blocks in an existing column. There is no separate body item to collect or craft. Cave Vines use **Glow Berries** as their inventory and planting form. [Nether-vine items] · [Vine and Lichen items] · [Glow Berries block item] · [Body-to-tip conversion and support loss]

| Exact block ID | Role and direction | Inventory form | Registry and loot |
| --- | --- | --- | --- |
| <span id="vine"></span>`minecraft:vine` | Surface-attached Vine, no separate tip | [Vines](../items/Vines.md) (`minecraft:vine`) | [Registry vine] · [Loot vine] |
| <span id="weeping-vines"></span>`minecraft:weeping_vines` | Lowest tip; grows downward | [Weeping Vines](../items/WeepingVines.md) (`minecraft:weeping_vines`) | [Registry weeping_vines] · [Loot weeping_vines] |
| <span id="weeping-vines-plant"></span>`minecraft:weeping_vines_plant` | Body above the lowest tip | [Weeping Vines](../items/WeepingVines.md) (`minecraft:weeping_vines`) | [Registry weeping_vines_plant] · [Loot weeping_vines_plant] |
| <span id="twisting-vines"></span>`minecraft:twisting_vines` | Highest tip; grows upward | [Twisting Vines](../items/TwistingVines.md) (`minecraft:twisting_vines`) | [Registry twisting_vines] · [Loot twisting_vines] |
| <span id="twisting-vines-plant"></span>`minecraft:twisting_vines_plant` | Body below the highest tip | [Twisting Vines](../items/TwistingVines.md) (`minecraft:twisting_vines`) | [Registry twisting_vines_plant] · [Loot twisting_vines_plant] |
| <span id="cave-vines"></span>`minecraft:cave_vines` | Lowest tip; grows downward | [Glow Berries](../items/GlowBerries.md) (`minecraft:glow_berries`) | [Registry cave_vines] · [Loot cave_vines] |
| <span id="cave-vines-plant"></span>`minecraft:cave_vines_plant` | Body above the lowest tip | [Glow Berries](../items/GlowBerries.md) (`minecraft:glow_berries`) | [Registry cave_vines_plant] · [Loot cave_vines_plant] |

## Ordinary Vines

Ordinary Vines can occupy **four wall faces and the ceiling face** of one cell; they have no floor-facing attachment. Use full solid wall or ceiling blocks for straightforward placement. The callbacks test supporting faces rather than a list of soil names. A hanging wall-facing Vine can also retain that face through a matching Vine face immediately above it, allowing it to hang below the original wall. Adding Vine items can add other supported faces to the same cell. When support updates remove every valid face, the block breaks. [Ordinary Vine faces] · [Ordinary Vine support] · [Ordinary Vine placement] · [Full-face attachment checks]

**Mine with Shears to obtain one Vine item per block**, regardless of its number of faces. Bare hands and other tools, including a Silk Touch tool without the Shears item identity, do not satisfy its loot condition. It has hardness **0.2**, no correct-tool requirement, and is in both the axe-efficient and sword-efficient tags; mining speed does not override the Shears-only loot. [Ordinary Vine registration] · [Loot vine] · [Axe-efficient blocks] · [Sword-efficient blocks] · [Shears speed and wear] · [Correct-tool drop gate]

Ordinary Vines spread through random ticks when **`doVinesSpread` is enabled**, which is its bundled default. Each random tick has a **1-in-4** chance to enter the spread routine, which then chooses a direction and checks space, faces, and support. It can add faces or spread sideways, upward, or downward; it has no age counter or direct Bone Meal target. Its growth callback has no light-level test. [Ordinary Vine spread] · [Vine-spread rule default] · [Ordinary Vine faces] · [Bone Meal use and consumption]

The density check blocks certain sideways and upward attempts when it finds **at least five Vine blocks** in the **9 × 3 × 9** area centered on the current Vine. The downward branch does not use that check. This is not a five-block maximum length or a guaranteed growth interval. [Ordinary Vine spread] · [Vine density check]

## Weeping and Twisting Vines

**Weeping Vines hang from above and extend downward; Twisting Vines stand on support below and extend upward.** At the supported end, they accept a matching tip/body segment or a sturdy face pointing into the vine. Their item-placement rules do not restrict them to Nether soils or the Nether dimension. Losing the necessary support schedules a check for the next tick, which breaks the unsupported segment and can detach the rest of that side of the column. [Weeping Vine direction and growth] · [Twisting Vine direction and growth] · [Column placement and support] · [Body-to-tip conversion and support loss] · [Tip-to-body conversion]

### Tips, age, and extending a column

A newly placed standalone tip receives an age from **0 through 24**. While its age is below **25**, each random tick has a **10% growth chance**. Growth requires **air** in the next cell; a successful step places a new tip with age increased by one and changes the previous tip into a body. These checks do not require a particular light level. Age 25 ends natural tip growth, but age is not a fixed total-column-length limit. [Tip age and natural growth] · [Tip-to-body conversion] · [Weeping Vine direction and growth] · [Twisting Vine direction and growth] · [Nether-vine growth length]

Bone Meal on either a tip or a connected body can extend the **end of the column**. It requires air beyond the tip, consumes one Bone Meal in ordinary Survival use, and can extend an age-25 tip. The number of attempted new segments varies: at least the first is attempted, then the continuation probability decreases by a factor of **0.826** after each success. An obstruction stops placement. There is no fixed “one Bone Meal equals N blocks” yield. [Tip Bone Meal extension] · [Body Bone Meal forwarding] · [Nether-vine growth length] · [Bone Meal use and consumption]

Breaking off the tip makes the newly exposed body become a tip with a fresh **0–24 age**. Thus cutting a column can restart natural growth at its new end. If you want it to stay at that length, use the trimming method below. [Body-to-tip conversion and support loss] · [Tip age and natural growth]

### Recovering Nether vines

Both tip and body use the same recovery rules:

| Tool used on the mined segment | Chance to drop one matching vine |
| --- | --- |
| Shears or Silk Touch | **100%** |
| No Fortune and neither condition above | **33%** |
| Fortune I | **55%** |
| Fortune II | **77%** |
| Fortune III | **100%** |

Fortune changes the chance of receiving **one item**, not the number in a successful drop. Both species break instantly and have no correct-tool gate. [Loot weeping_vines] · [Loot weeping_vines_plant] · [Loot twisting_vines] · [Loot twisting_vines_plant] · [Fortune chance selection] · [Nether-vine registrations] · [Survival mining dispatch]

**Harvest individual segments with the chosen tool for dependable recovery.** Removing the support or breaking an earlier segment can detach other blocks, but their automatic destruction uses an **empty tool**. They do not inherit Shears, Silk Touch, or Fortune from the tool that caused the collapse; the Nether-vine fallback chance is then 33% per detached segment. [Column placement and support] · [Body-to-tip conversion and support loss] · [Detached-block loot uses an empty tool] · [Fortune chance selection]

## Cave Vines and Glow Berries

Use a **Glow Berry item** against a suitable underside to plant a downward-growing Cave Vine. It shares the column support, age, and 10% random-tick extension mechanism above, but a newly placed tip starts **without berries**. Each successful natural extension independently gives the **new tip an 11% chance** of berries. Existing berry states survive tip/body conversions. These are growth-event chances, not a timed promise that every old segment will eventually bear fruit. [Glow Berries block item] · [Column placement and support] · [Tip age and natural growth] · [Cave tip growth and berries] · [Cave body berries and conversion]

### Collecting and renewing berries

Use a berry-bearing tip or body, preferably with an **empty main hand**, to drop **one Glow Berry item** and leave that segment in place with `berries=false`. Breaking a berry-bearing segment also drops one berry; breaking a bare segment drops nothing. Shears, Silk Touch, and Fortune do not increase these yields or produce a separate Cave Vine item. [Berry collection and light] · [Berry harvest loot] · [Loot cave_vines] · [Loot cave_vines_plant]

Apply **one Bone Meal to a bare Cave Vine segment** to make that exact segment bear berries. The success check is unconditional once it is a bare segment. **This Bone Meal route does not extend the column**, even when used on the lowest tip; it overrides the generic tip/body extension handlers. [Cave tip growth and berries] · [Cave body berries and conversion] · [Bone Meal use and consumption]

A normal main-hand use on a fruiting segment performs the berry collection before the held item's use callback. Consequently, a click while holding Bone Meal or Shears can pick the berries first. Use Bone Meal again on the now-bare segment to renew them, or use Shears on the bare tip to trim it. [Block and held-item interaction order] · [Berry collection and light] · [Cave tip growth and berries] · [Cave body berries and conversion]

After picking, that old bare segment has no separate passive berry-regrowth timer. Natural extension rolls berries on new tips, while **nectar-carrying Bees can sometimes trigger the same berry-producing operation** on a qualifying Cave Vine below them. See [Bee](../mobs/Bee.md#flowers-and-pollination) for that conditional crop-growth behavior. [Foxes](../mobs/Fox.md#berry-gathering) can also harvest fruiting Cave Vines under their own foraging rules. [Cave tip growth and berries] · [Cave body berries and conversion] · [Bee-assisted berry growth]

### Light and food

Each berry-bearing tip or body emits **light level 14**; a bare segment emits **0**. Picking the fruit switches that segment's light off. Bone Meal can restore its fruit and light without increasing the vine's length. [Cave-vine registrations] · [Berry collection and light] · [Cave tip growth and berries] · [Cave body berries and conversion]

Eating a Glow Berry supplies **2 hunger points and 0.4 saturation points before caps**. Its registration uses the normal food component without an added status-effect list. See [Glow Berries](../items/GlowBerries.md) and [Hunger, saturation, and healing](../mechanics/Hunger.md) for the inventory and shared food rules. [Glow Berries block item] · [Glow Berries food value] · [Saturation calculation] · [Eating food] · [Hunger and saturation caps] · [Default food components] · [Default food definition]

## Trimming, climbing, and water

Use **unbroken Shears on a growing tip** to set its age to **25**, requesting one durability point and stopping its natural extension. This acts on the tip, not a body block or ordinary Vine. It does not make Nether vines immune to later Bone Meal, and it does not prevent Bone Meal from producing berries on Cave Vines. A new tip exposed by breaking the old one gets a fresh age. See [Durability](../mechanics/Durability.md) for MattMC's retained broken tools and wear rules. [Shears trimming] · [Broken-item use guard] · [Tip age and natural growth] · [Tip Bone Meal extension] · [Cave tip growth and berries] · [Body-to-tip conversion and support loss]

All **seven vine block IDs** in this guide are in the climbable tag. For a player inside one, ordinary climbing movement limits descent and resets fall distance; jumping or pushing into a horizontal obstruction enables ascent, and crouching suppresses downward sliding. A wall behind every hanging segment is not part of the active tag check. Keep a supported, continuous route and a safe landing; the source check applies while actually in a climbable block. **Glow Lichen and Archaic Vine are absent from this tag.** [Climbable block tag] · [Active climbing check] · [Climbing ascent] · [Climbing descent and fall distance] · [Crouching and sliding]

These seven vines have **no waterlogged state** and grow only through their land-plant handlers. Incoming water can replace them and run their ordinary no-tool drop path: ordinary Vines yield no item, detached Nether vines use the 33% fallback, and Cave Vines yield a berry only when fruiting. **Glow Lichen's source-water behavior is different**; use [its water section](GlowLichen.md#water-and-light). [Ordinary Vine registration] · [Nether-vine registrations] · [Cave-vine registrations] · [Ordinary Vine faces] · [Tip age and natural growth] · [Body-to-tip conversion and support loss] · [Fluid admission and replacement] · [Incoming fluid replacement] · [Water-driven block drops] · [Loot vine] · [Loot weeping_vines] · [Loot twisting_vines] · [Loot cave_vines]

## Source-backed places to collect starters

These are selected active biome-feature chains. Their placement attempts can fail for lack of space or support, so a biome name is not a promise of a plant at every location. The active biome-decoration loop dispatches the registered placed features. [Active biome decoration]

| Plant | Checked example | Evidence |
| --- | --- | --- |
| Ordinary Vines | **Jungle** selects the `vines` feature, which places in air beside an acceptable supporting face | [Vine source biome] · [Vine placed feature] · [Vine configured feature] · [Vine feature placement] |
| Weeping Vines | **Crimson Forest** selects a hanging-vine feature; that natural feature starts below Netherrack or Nether Wart Block | [Weeping source biome] · [Weeping placed feature] · [Weeping configured feature] · [Weeping feature placement] |
| Twisting Vines | **Warped Forest** selects an upward-vine feature; its natural ground filter accepts Netherrack, Warped Nylium, or Warped Wart Block | [Twisting source biome] · [Twisting placed feature] · [Twisting configured feature] · [Twisting feature support] |
| Cave Vines | **Lush Caves** selects a downward column feature placed beneath a scanned sturdy ceiling; its configured states include berry-bearing tips and bodies | [Cave source biome] · [Cave placed feature] · [Cave configured feature] · [Cave-vine column placement] |

The natural Nether-feature ground filters are narrower than the item-placement support rule. Structures, chest loot, trades, and other biome routes are outside this bounded acquisition list.

## Crafting uses

Ordinary **Vines** are a named ingredient in the [mossy Cobblestone and Stone Bricks recipes](Stone.md#mossy-variants). Those recipes require `minecraft:vine`; Weeping Vines, Twisting Vines, Glow Berries, and Glow Lichen do not substitute for it. The Stone guide owns their full ingredient quantities and shaped-building routes. [Vine-to-mossy-cobblestone recipe] · [Vine-to-mossy-bricks recipe]

**One Paper plus one ordinary Vine**, shapeless, crafts **one [Bordure Indented Banner Pattern](../items/BordureIndentedBannerPattern.md)**. None of the five inventory forms in this guide and the Lichen guide has a producing crafting or smelting recipe in the checked bundled data. Collect or propagate the plants through their verified routes. [Indented-border pattern recipe]

Related: [Glow Lichen](GlowLichen.md) · [Bone Meal](../items/BoneMeal.md) · [Primordial Plants](PrimordialPlants.md) · [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `88d85ee594bc770fa362129690eadb127272dc20`. Checked all seven vine registrations and full block-loot tables, shared item mappings, Cave Vine interaction loot, three ordinary-Vine ingredient recipes, climbing/mining tags, active support and interaction dispatch, age/Bone Meal/trimming behavior, berry food/light, and the four listed biome-feature chains. Archaic Vine and general stone recipes retain their existing owners. No in-game placement, climbing, growth, harvesting, crafting, eating, or generation test was run. Data packs can change tags, recipes, loot, and biome features.

[Registry vine]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/Blocks.java#L2378-L2390
[Loot vine]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/loot_table/blocks/vine.json
[Registry weeping_vines]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/Blocks.java#L5538-L5548
[Loot weeping_vines]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/loot_table/blocks/weeping_vines.json
[Registry weeping_vines_plant]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/Blocks.java#L5549-L5553
[Loot weeping_vines_plant]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/loot_table/blocks/weeping_vines_plant.json
[Registry twisting_vines]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/Blocks.java#L5554-L5564
[Loot twisting_vines]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/loot_table/blocks/twisting_vines.json
[Registry twisting_vines_plant]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/Blocks.java#L5565-L5569
[Loot twisting_vines_plant]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/loot_table/blocks/twisting_vines_plant.json
[Registry cave_vines]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/Blocks.java#L6546-L6557
[Loot cave_vines]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/loot_table/blocks/cave_vines.json
[Registry cave_vines_plant]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/Blocks.java#L6558-L6568
[Loot cave_vines_plant]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/loot_table/blocks/cave_vines_plant.json
[Ordinary Vine registration]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/Blocks.java#L2378-L2390
[Nether-vine registrations]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/Blocks.java#L5538-L5569
[Cave-vine registrations]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/Blocks.java#L6546-L6568
[Vine and Lichen items]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/item/Items.java#L559-L560
[Nether-vine items]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/item/Items.java#L367-L368
[Glow Berries block item]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/item/Items.java#L2452-L2454
[Ordinary Vine faces]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/VineBlock.java#L27-L50
[Ordinary Vine support]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/VineBlock.java#L79-L165
[Ordinary Vine placement]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/VineBlock.java#L281-L309
[Ordinary Vine spread]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/VineBlock.java#L167-L244
[Vine density check]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/VineBlock.java#L246-L279
[Vine-spread rule default]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/GameRules.java#L203-L205
[Column placement and support]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/GrowingPlantBlock.java#L32-L62
[Tip age and natural growth]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/GrowingPlantHeadBlock.java#L19-L62
[Tip-to-body conversion]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/GrowingPlantHeadBlock.java#L65-L104
[Body-to-tip conversion and support loss]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/GrowingPlantBodyBlock.java#L29-L57
[Tip Bone Meal extension]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/GrowingPlantHeadBlock.java#L107-L128
[Body Bone Meal forwarding]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/GrowingPlantBodyBlock.java#L65-L87
[Nether-vine growth length]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/NetherVines.java#L6-L24
[Weeping Vine direction and growth]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/WeepingVinesBlock.java#L19-L36
[Twisting Vine direction and growth]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/TwistingVinesBlock.java#L19-L36
[Cave tip growth and berries]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/CaveVinesBlock.java#L28-L86
[Cave body berries and conversion]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/CaveVinesPlantBlock.java#L27-L69
[Berry collection and light]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/CaveVines.java#L23-L53
[Berry harvest loot]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/loot_table/harvest/cave_vine.json
[Bee-assisted berry growth]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/entity/animal/Bee.java#L950-L1002
[Bone Meal use and consumption]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/item/BoneMealItem.java#L35-L77
[Shears trimming]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/item/ShearsItem.java#L61-L84
[Shears speed and wear]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/item/ShearsItem.java#L32-L58
[Broken-item use guard]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L365
[Block and held-item interaction order]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L358-L388
[Climbable block tag]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/tags/block/climbable.json
[Active climbing check]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1652-L1667
[Climbing ascent]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2458-L2467
[Climbing descent and fall distance]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2485-L2499
[Crouching and sliding]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3335-L3337
[Fluid admission and replacement]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L401-L427
[Incoming fluid replacement]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L268-L276
[Water-driven block drops]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/material/WaterFluid.java#L87-L91
[Detached-block loot uses an empty tool]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/Level.java#L263-L278
[Correct-tool drop gate]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[Survival mining dispatch]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[Fortune chance selection]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/storage/loot/predicates/BonusLevelTableCondition.java#L37-L41
[Axe-efficient blocks]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[Sword-efficient blocks]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/tags/block/sword_efficient.json
[Full-face attachment checks]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/MultifaceBlock.java#L251-L259
[Glow Berries food value]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/food/Foods.java#L43
[Saturation calculation]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/food/FoodProperties.java#L60-L84
[Eating food]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/food/FoodProperties.java#L39-L56
[Hunger and saturation caps]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/food/FoodData.java#L14-L29
[Default food components]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/item/Item.java#L366-L375
[Default food definition]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/item/component/Consumables.java#L9-L16
[Vine source biome]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/biome/jungle.json
[Vine placed feature]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/placed_feature/vines.json
[Vine configured feature]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/configured_feature/vines.json
[Weeping source biome]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/biome/crimson_forest.json
[Weeping placed feature]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/placed_feature/weeping_vines.json
[Weeping configured feature]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/configured_feature/weeping_vines.json
[Twisting source biome]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/biome/warped_forest.json
[Twisting placed feature]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/placed_feature/twisting_vines.json
[Twisting configured feature]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/configured_feature/twisting_vines.json
[Cave source biome]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/biome/lush_caves.json
[Cave placed feature]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/placed_feature/cave_vines.json
[Cave configured feature]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/configured_feature/cave_vine.json
[Vine feature placement]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/levelgen/feature/VinesFeature.java#L16-L32
[Weeping feature placement]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/levelgen/feature/WeepingVinesFeature.java#L22-L39
[Twisting feature support]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/levelgen/feature/TwistingVinesFeature.java#L85-L91
[Cave-vine column placement]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/levelgen/feature/BlockColumnFeature.java#L16-L55
[Active biome decoration]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L355-L380
[Vine-to-mossy-cobblestone recipe]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/recipe/crafting/mossy_cobblestone_from_vine.json
[Vine-to-mossy-bricks recipe]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/recipe/crafting/mossy_stone_bricks_from_vine.json
[Indented-border pattern recipe]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/recipe/crafting/bordure_indented_banner_pattern.json
