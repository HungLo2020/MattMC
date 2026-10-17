# Copper Golem Statues

Copper Golem Statues are placed decorations with four selectable poses and a Comparator output. Their eight oxidation/wax variants can also lead back to a living [Copper Golem](../mobs/CopperGolem.md), but revival requires a specific **unwaxed, Unaffected statue** and a normal axe interaction. [Registration][statue-reg] · [Pose behavior][statue] · [Revival handler][statue-aging]

## Obtaining a statue

The four unwaxed Statue forms have **no direct crafting recipe** in this checked set. The verified creation route is a living, unwaxed Copper Golem becoming fully Oxidized and turning into an **Oxidized Copper Golem Statue**. Other finishes follow from scraping, placed oxidation, and waxing. Each unwaxed statue item can be combined shapelessly with **1 Honeycomb for 1 matching waxed statue**. [Recipe inventory](#exact-variants-recipes-and-loot) · [Living-to-statue conversion][golem-statue] · [Shared finish controls](CopperConstruction.md#waxing-and-scraping)

The [Copper Chest construction interaction](CopperChests.md#creating-a-chest-with-a-copper-golem) creates a golem and a chest from a full copper block topped with a Carved Pumpkin or Jack o'Lantern. The golem starts at the body's oxidation stage, so an Oxidized full-copper body provides an already-Oxidized golem; waxing that body does not produce a waxed golem. [Pattern, spawn, and stage selection][pumpkin] · [Accepted body/pattern][copper-pattern] · [Initial stage assignment][golem-spawn] · [Initial aging state][golem-default]

The living golem has its **own aging schedule**, rather than the placed-block neighbor formula: stage intervals are selected from **504,000–552,000 game ticks**. Once already Oxidized and not waxed, its server update can make a statue when its current block is exactly Air and a **0.0058 threshold roll** succeeds. Conversion is therefore not immediate or a fixed countdown. Waxing the living golem disables this normal aging/statue path. [Active server update][golem-weather] · [Living timer and conversion conditions][golem-statue] · [Living Honeycomb interaction][golem-mob-use]

The statue is placed at the golem's block position with its facing and one of the four poses; its custom name is saved in the statue block entity. [Conversion][golem-statue] · [Name transfer][statue-be] · [Registered entity and block-entity types][golem-create] · [Statue block entity][statue-be-reg]

## Poses and revival

Normal use with an empty hand cycles the pose. Most non-axe items also reach this pose handler; an unwaxed statue specifically lets Honeycomb proceed to waxing instead. The visual pose does not change the shared **10/16-block-wide, 14/16-block-high** collision shape. [Pose interaction, shape, and enum][statue] · [Unwaxed Honeycomb exception][statue-aging]

| Pose | Comparator output |
| --- | ---: |
| Standing | 1 |
| Sitting | 2 |
| Running | 3 |
| Star | 4 |

After Star, the next use returns to Standing. A [Comparator](RedstoneComparator.md) reads the selected pose's value, independent of oxidation stage or wax. [Pose cycle and analog output][statue] · [Comparator input dispatch][comparator]

### Bring the statue back to life

1. If waxed, use an unbroken axe once to remove its wax
2. Scrape an unwaxed statue back one stage per axe use until it is Unaffected
3. **Use the axe normally once more, without secondary use/sneaking**, on that unwaxed Unaffected statue to revive it

The final interaction creates a new Copper Golem, restores the custom name and facing, and removes the statue block. The new golem starts Unaffected with its ordinary unwaxed aging state. The revival handler requests **1 axe durability**. Merely scraping from Exposed to Unaffected does not revive it during that same click. [Wax removal and scraping][axe] · [Weathering map][weather] · [Revival branch][statue-aging] · [Revived name/position/facing][statue-be] · [Default weather state][golem-weather-default] · [Default aging state][golem-default]

**Secondary use skips the revival handler.** Once the statue is already Unaffected and unwaxed, the axe item's ordinary scraping path has no earlier stage, so holding secondary use will not complete revival. [Server block/item interaction order][server-use] · [Revival callback][statue-aging] · [Axe conversion path][axe]

### Retained broken-axe exception

The current revival branch checks the **axe item tag**, not whether the stack is broken. It runs in the block interaction before the normal item-use guard. As a result, the inspected source allows a retained broken axe to revive an **unwaxed Unaffected statue through normal use**. It still cannot perform the ordinary scraping or wax-removal steps; the generic broken-item guard blocks those. Broken stacks also skip the requested extra durability damage. This is a source-reviewed exception, not an in-game reproduction. [Revival condition][statue-aging] · [Axe tag][axes] · [Server interaction order][server-use] · [Client interaction order][client-use] · [Client click dispatch][client-click] · [Broken use guard][broken] · [Broken damage handling][broken-wear]

## Wax, lightning, and placement

The placed unwaxed statue follows the shared [copper oxidation rules](CopperConstruction.md#oxidation-and-spacing). Waxing keeps its present oxidation and pose; it does not revive the golem. In-place variant changes keep the statue block entity and custom name. [Aging callback][statue-aging] · [Wax/property preservation][wax] · [Statue block-entity retention][statue] · [Active retention dispatch][chunk-preserve]

For a named or posed statue, prefer **waxing it while placed**. The shapeless wax recipes construct a fresh result stack and do not copy the input item's custom name or saved pose. Ordinary mining does preserve those two values, and placement reapplies the saved pose. [Wax recipe examples][r-waxed_copper_golem_statue_from_honeycomb] · [Fresh recipe result][shapeless-result] · [Name/pose loot][loot-copper_golem_statue] · [Item state application][place-state]

Lightning can remove oxidation from an unwaxed **statue block** through the [shared cleaning path](CopperConstruction.md#lightning-cleaning), but that path does not call statue revival. Use the final normal axe interaction afterward. Waxed statue blocks are outside the weathering-cleaning path. A living golem has a different lightning callback: a new bolt can remove one oxidation stage and restart its aging timer, even if that aged golem was waxed. Do not transfer the block's wax protection rule to the living mob. [Block lightning cleaning][lightning-clean] · [Waxed/unwaxed statue registrations][statue-reg] · [Axe-only revival][statue-aging] · [Living golem lightning handling][golem-lightning]

Statues face opposite the player's horizontal direction on placement and can be waterlogged with source Water. They have no continuing floor-support requirement. Reviving a waterlogged statue removes the block through the ordinary fluid-restoring removal path. [Placement and fluid state][statue] · [Shared water handling][water] · [Default survival rule][default] · [Revival removal][statue-aging] · [Block removal and fluid restoration][remove-fluid]

## Mining and keeping the statue

All eight variants have **hardness 3 and blast resistance 6**. A pickaxe is the efficient tool, but **there is no correct-tool requirement for their block drop**, so hand mining can collect a statue. The bundled loot returns **1 matching variant**, preserving the custom name and pose; Silk Touch and Fortune do not add a different result or quantity. Explosions have a survival check. [Properties][statue-reg] · [Pickaxe tag][mineable-pickaxe] · [Harvest gate][harvest] · [Exact loot](#exact-variants-recipes-and-loot)

Mine the statue when you want to relocate it; normal axe use on its Unaffected unwaxed form can revive it instead. This page covers the statue lifecycle and its sorting-related connection, not the golem's full combat, equipment, or natural-spawn behavior.

## Exact variants, recipes, and loot

| Registry ID | Recipes | Block loot |
| --- | --- | --- |
| `minecraft:copper_golem_statue` | No direct production recipe | [Loot][loot-copper_golem_statue] |
| `minecraft:exposed_copper_golem_statue` | No direct production recipe | [Loot][loot-exposed_copper_golem_statue] |
| `minecraft:oxidized_copper_golem_statue` | No direct production recipe | [Loot][loot-oxidized_copper_golem_statue] |
| `minecraft:waxed_copper_golem_statue` | [Wax][r-waxed_copper_golem_statue_from_honeycomb] | [Loot][loot-waxed_copper_golem_statue] |
| `minecraft:waxed_exposed_copper_golem_statue` | [Wax][r-waxed_exposed_copper_golem_statue_from_honeycomb] | [Loot][loot-waxed_exposed_copper_golem_statue] |
| `minecraft:waxed_oxidized_copper_golem_statue` | [Wax][r-waxed_oxidized_copper_golem_statue_from_honeycomb] | [Loot][loot-waxed_oxidized_copper_golem_statue] |
| `minecraft:waxed_weathered_copper_golem_statue` | [Wax][r-waxed_weathered_copper_golem_statue_from_honeycomb] | [Loot][loot-waxed_weathered_copper_golem_statue] |
| `minecraft:weathered_copper_golem_statue` | No direct production recipe | [Loot][loot-weathered_copper_golem_statue] |

## Sources and verification

Source-reviewed on **2026-10-02** at `1879e5f54378351fd773b9a2c10839eb9259c504`. All eight statue variants, four waxing recipes, eight loot tables, pose/Comparator behavior, saved name/pose handling, the living conversion route, revival, and relevant lightning paths were checked. No in-game test was run. Recipes, tags, loot, server rules, and later code changes can alter these results.

Related: [Blocks](Blocks.md) · [Copper construction](CopperConstruction.md) · [Copper catalog](catalog/copper.md) · [Items](../items/Items.md)

[statue-reg]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/Blocks.java#L6441-L6483
[comparator]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/ComparatorBlock.java#L97-L119
[default]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L326
[place-state]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/item/BlockItem.java#L110-L142
[harvest]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[broken]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L379
[wax]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/item/HoneycombItem.java#L24-L113
[weather]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/WeatheringCopper.java#L17-L103
[axe]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/item/AxeItem.java#L61-L117
[server-use]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L358-L394
[broken-wear]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/item/ItemStack.java#L457-L473
[shapeless-result]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L58-L70
[water]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java#L18-L49
[remove-fluid]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/Level.java#L257-L260
[client-use]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L317-L375
[client-click]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/client/Minecraft.java#L1770-L1823
[chunk-preserve]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java#L311-L323
[pumpkin]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/CarvedPumpkinBlock.java#L47-L124
[copper-pattern]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/CarvedPumpkinBlock.java#L204-L230
[statue]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/CopperGolemStatueBlock.java
[statue-aging]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/WeatheringCopperGolemStatueBlock.java#L21-L75
[statue-be]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/entity/CopperGolemStatueBlockEntity.java#L17-L56
[statue-be-reg]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/entity/BlockEntityType.java#L261-L272
[golem-create]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/EntityType.java#L445-L454
[golem-default]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/animal/coppergolem/CopperGolem.java#L73-L87
[golem-weather-default]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/animal/coppergolem/CopperGolem.java#L163-L168
[golem-weather]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/animal/coppergolem/CopperGolem.java#L198-L207
[golem-mob-use]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/animal/coppergolem/CopperGolem.java#L211-L258
[golem-statue]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/animal/coppergolem/CopperGolem.java#L261-L305
[golem-spawn]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/animal/coppergolem/CopperGolem.java#L363-L366
[golem-lightning]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/animal/coppergolem/CopperGolem.java#L454-L465
[lightning-clean]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/LightningBolt.java#L148-L210
[mineable-pickaxe]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[axes]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/item/axes.json
[loot-copper_golem_statue]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/copper_golem_statue.json
[loot-exposed_copper_golem_statue]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/exposed_copper_golem_statue.json
[loot-oxidized_copper_golem_statue]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/oxidized_copper_golem_statue.json
[loot-waxed_copper_golem_statue]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/waxed_copper_golem_statue.json
[r-waxed_copper_golem_statue_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/waxed_copper_golem_statue_from_honeycomb.json
[loot-waxed_exposed_copper_golem_statue]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/waxed_exposed_copper_golem_statue.json
[r-waxed_exposed_copper_golem_statue_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/waxed_exposed_copper_golem_statue_from_honeycomb.json
[loot-waxed_oxidized_copper_golem_statue]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/waxed_oxidized_copper_golem_statue.json
[r-waxed_oxidized_copper_golem_statue_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/waxed_oxidized_copper_golem_statue_from_honeycomb.json
[loot-waxed_weathered_copper_golem_statue]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/waxed_weathered_copper_golem_statue.json
[r-waxed_weathered_copper_golem_statue_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_copper_golem_statue_from_honeycomb.json
[loot-weathered_copper_golem_statue]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/weathered_copper_golem_statue.json
