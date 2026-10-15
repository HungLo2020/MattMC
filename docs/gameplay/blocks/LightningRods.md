# Lightning Rods

Lightning Rods provide a target for natural thunder strikes and emit a short redstone pulse when struck. There are **eight variants**: four oxidation stages and four matching waxed forms. Oxidation and wax do not remove their rod signal or point-of-interest registration. [Rod registrations][rod-reg] · [All variant target states][rod-poi] · [POI registration][rod-poi-register] · [Strike response][rod]

## Crafting and collecting

The [Copper Ingot guide](../items/CopperIngot.md#selected-uses) owns the basic Lightning Rod crafting recipe: three ingots vertically make one unaffected rod. There are no direct crafting recipes for the three later unwaxed stages in this checked set. Let a placed rod oxidize or scrape it to the desired stage using the shared [copper finish controls](CopperConstruction.md#waxing-and-scraping). Each stage can also be waxed in the crafting grid with **1 matching unwaxed Rod + 1 Honeycomb → 1 waxed Rod**. [Base recipe][r-lightning_rod] · [Variant recipes](#exact-variants-recipes-and-loot) · [Rod weathering callback][rod-aging]

Use an **unbroken Stone, Copper, Iron, Diamond, or Netherite Pickaxe** for the block drop. Wooden/Golden Pickaxes, hand mining, and broken pickaxes do not satisfy these blocks' bundled tool gate. They have **hardness 3 and blast resistance 6**, and ordinary loot returns one matching oxidation/wax variant; Fortune and Silk Touch add no extra quantity or alternate block result. Explosions add a survival condition. [Pickaxe targets][mineable-pickaxe] · [Tier tag][needs_stone_tool] · [Wood exclusions][incorrect_for_wooden_tool] · [Gold exclusions][incorrect_for_gold_tool] · [Tool materials][tool] · [Broken drop guard][broken-drop] · [Harvest gate][harvest] · [Active harvest][harvest-call] [Rod properties][rod-reg]

## Attracting natural lightning

During the server's natural thunder-target selection, it searches for the **closest registered Lightning Rod within 128 blocks** of the candidate strike position. The range filter uses squared 3D distance. The rod must be at **the top occupied block of its X/Z column**, as checked against the World Surface heightmap. The chosen strike position is one block above the rod. All eight variants' possible block states are registered for this search. [Target search and height test][rod-target] · [3D range filter][poi-range] · [Closest-target selection][poi-closest] · [Registered states][rod-poi]

The weather path still requires rain/thunder and rain at the selected location. A rod redirects an eligible natural strike; it does not generate a thunderstorm or guarantee a strike on a timer. This review does not extend that natural-target rule to commands or every enchanted-projectile route. [Natural strike conditions and creation][storm]

Leave the rod exposed at the top of its column. The selection code does not require an upward-facing rod, so a wall-facing rod can qualify when that height condition is met. Lightning can still start nearby fire through its ordinary impact behavior; position valuable flammable material accordingly. [Height-only rod predicate][rod-target] · [Facing-independent registered states][rod-poi] · [Impact and fire dispatch][lightning] · [Nearby fire attempts][lightning-clean]

## Deliberate strikes with Channeling

To activate a rod without waiting for a naturally selected strike, throw a **Channeling Trident directly at the rod** during a **thunderstorm**, with the rod's position open to the sky. All eight bundled rod variants qualify, including waxed and fully Oxidized forms. This is a separate block-hit effect; it does not use the natural strike's nearest-rod search or 128-block attraction radius. [Channeling block-hit requirements][channeling-hit] · [Eligible rod tag][channeling-rods] · [Projectile impact dispatch][channeling-impact] · [Lightning creation][channeling-summon]

If the throw produces no lightning, check these conditions:

- **Hit the rod itself.** A shot into its support or a neighboring block does not satisfy the rod-location check. The Trident evaluates the impact inside the struck block; being close to a rod is insufficient. [Impact position][channeling-impact] · [Location requirement][channeling-hit]
- **Check thunder and sky access.** Ordinary rain alone does not qualify, and a roof can fail the sky check. This branch checks thunder plus sky visibility, rather than the natural strike's local-rain test. [Weather predicate][channeling-weather] · [Sky visibility][channeling-sky]
- **Check the projectile and its carried item.** An ordinary arrow or an unenchanted Trident does not provide this effect. A Trident already broken by its final release also skips the block-hit enchantments; the [last-throw explanation](../items/Trident.md#the-last-throw-and-broken-tridents) owns that durability edge case. [Projectile requirement][channeling-hit] · [Broken-item guard][channeling-broken]

A successful summon creates ordinary lightning, with its entity effects and conditional fire attempts. Keep living targets and flammable surroundings clear; a rod is not a harmless redstone-only target. When the bolt resolves its strike block to the rod, it uses the normal [short pulse](#placement-water-and-redstone) and [wax-dependent copper-cleaning rules](#oxidation-and-lightning-cleaning). [Summoned entity][channeling-summon] · [Rod activation][channeling-strike] · [Entity and fire effects][channeling-hazards]

For a deliberate pulse, aim at the rod's **top or an exposed side**. An exact underside impact is not guaranteed to activate the hit rod at every height: the impact clamp's lower edge can round to an integer, while lightning selects its strike block slightly below the summon position. This caution is source-derived, not an in-game reproduction. [Impact clamping][channeling-clamp] · [Strike position][channeling-hazards]

## Placement, water, and redstone

A rod points along the **clicked face**, so floor, wall, and underside placements are supported. Its collision shape runs along that axis, and it has no continuing attachment-support rule. Source Water at placement sets its waterlogged state; bucket filling and collection use the shared waterlogged-block interface. [Placement and water state][rod] · [Axial shape][rod-shape] · [Default survival][default] · [Water handling][water]

When struck, it sets **powered = true**, supplies signal strength **15**, and schedules its reset after **8 game ticks**. It sends the direct signal toward its attachment-side neighbor and explicitly updates that neighbor. The ordinary signal is 15 while powered and 0 otherwise. At the scheduled reset it becomes unpowered and updates neighbors again. Use this as a short pulse source, with additional circuitry if you want a remembered on/off state such as a [Copper Bulb](CopperLighting.md#toggle-control-and-comparator-output). [Signal, strike, neighbor update, and reset callbacks][rod] · [Direct-signal query directions][direct-signal]

Waxed and fully Oxidized rods still use these same signal callbacks. Breaking and replacing a rod resets its normal placed powered state; the loot saves its block variant, not a live redstone pulse. [Variant registrations][rod-reg] · [Default placement state][rod] · [Loot tables](#exact-variants-recipes-and-loot)

## Oxidation and lightning cleaning

Unwaxed rods use the [shared random-tick oxidation rules](CopperConstruction.md#oxidation-and-spacing); waxed rods use the plain non-weathering rod class. Waxing and scraping preserve their facing, power, and water states through shared-property copying. [Weathering callback][rod-aging] · [Waxed registrations][rod-reg] · [Wax map][wax] · [Previous-stage/state conversion][weather]

The [shared lightning-cleaning guide](CopperConstruction.md#lightning-cleaning) owns the exact random-walk rules. At a rod-directed strike, the **unwaxed rod itself** is the initial weathering block and resets to Unaffected; nearby copper may then be cleaned by the random walks. The guaranteed reset is not redirected to the supporting block. A **waxed rod still receives the redstone pulse but does not start this copper-cleaning path**, because it is not a weathering block. [Strike order][lightning] · [Actual strike position and cleaning][lightning-clean] · [Waxed/unwaxed classes][rod-reg]

## Exact variants, recipes, and loot

| Registry ID | Recipes | Block loot |
| --- | --- | --- |
| `minecraft:exposed_lightning_rod` | No direct production recipe | [Loot][loot-exposed_lightning_rod] |
| `minecraft:lightning_rod` | [Craft][r-lightning_rod] | [Loot][loot-lightning_rod] |
| `minecraft:oxidized_lightning_rod` | No direct production recipe | [Loot][loot-oxidized_lightning_rod] |
| `minecraft:waxed_exposed_lightning_rod` | [Wax][r-waxed_exposed_lightning_rod_from_honeycomb] | [Loot][loot-waxed_exposed_lightning_rod] |
| `minecraft:waxed_lightning_rod` | [Wax][r-waxed_lightning_rod_from_honeycomb] | [Loot][loot-waxed_lightning_rod] |
| `minecraft:waxed_oxidized_lightning_rod` | [Wax][r-waxed_oxidized_lightning_rod_from_honeycomb] | [Loot][loot-waxed_oxidized_lightning_rod] |
| `minecraft:waxed_weathered_lightning_rod` | [Wax][r-waxed_weathered_lightning_rod_from_honeycomb] | [Loot][loot-waxed_weathered_lightning_rod] |
| `minecraft:weathered_lightning_rod` | No direct production recipe | [Loot][loot-weathered_lightning_rod] |

## Sources and verification

Source-reviewed on **2026-10-02** at `1879e5f54378351fd773b9a2c10839eb9259c504`. All eight rod variants, five recipes, eight loot tables, tool tags, exposed-column POI targeting, redstone, waterlogging, and the relevant oxidation/lightning dispatch were checked. No in-game test was run. Recipes, tags, loot, server rules, and later code changes can alter these results.

Channeling rod activation was additionally source-reviewed on **2026-10-15** at `522b1f8b84345d79cd51fb1c19b4d51ab37628cb`, including projectile dispatch, bundled requirements, broken-item suppression and lightning effects. No in-game throw, weather, fire, cleaning or redstone test was run.

Related: [Blocks](Blocks.md) · [Copper construction](CopperConstruction.md) · [Copper catalog](catalog/copper.md) · [Items](../items/Items.md) · [Rain and thunder conditions](../mechanics/TimeWeatherAndSleep.md#rain-thunder-and-the-place-you-stand)

[rod-reg]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/Blocks.java#L6485-L6519
[default]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L326
[harvest]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[harvest-call]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[broken-drop]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/item/ItemStack.java#L586-L589
[tool]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L48
[wax]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/item/HoneycombItem.java#L24-L113
[weather]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/WeatheringCopper.java#L17-L103
[water]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java#L18-L49
[direct-signal]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/SignalGetter.java#L13-L43
[rod]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/LightningRodBlock.java
[rod-shape]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/RodBlock.java#L15-L43
[rod-aging]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/WeatheringLightningRodBlock.java#L11-L40
[rod-poi]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiTypes.java#L73-L85
[rod-poi-register]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiTypes.java#L140-L143
[poi-range]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiManager.java#L220-L223
[poi-closest]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiManager.java#L269-L276
[storm]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/server/level/ServerLevel.java#L519-L548
[rod-target]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/server/level/ServerLevel.java#L588-L620
[lightning]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/LightningBolt.java#L69-L99
[lightning-clean]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/LightningBolt.java#L148-L210
[mineable-pickaxe]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[needs_stone_tool]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json
[incorrect_for_wooden_tool]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[incorrect_for_gold_tool]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/incorrect_for_gold_tool.json
[loot-exposed_lightning_rod]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/exposed_lightning_rod.json
[loot-lightning_rod]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/lightning_rod.json
[r-lightning_rod]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/lightning_rod.json
[loot-oxidized_lightning_rod]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/oxidized_lightning_rod.json
[loot-waxed_exposed_lightning_rod]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/waxed_exposed_lightning_rod.json
[r-waxed_exposed_lightning_rod_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/waxed_exposed_lightning_rod_from_honeycomb.json
[loot-waxed_lightning_rod]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/waxed_lightning_rod.json
[r-waxed_lightning_rod_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/waxed_lightning_rod_from_honeycomb.json
[loot-waxed_oxidized_lightning_rod]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/waxed_oxidized_lightning_rod.json
[r-waxed_oxidized_lightning_rod_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/waxed_oxidized_lightning_rod_from_honeycomb.json
[loot-waxed_weathered_lightning_rod]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/waxed_weathered_lightning_rod.json
[r-waxed_weathered_lightning_rod_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_lightning_rod_from_honeycomb.json
[loot-weathered_lightning_rod]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/weathered_lightning_rod.json

[channeling-hit]: https://github.com/HungLo2020/MattMC/blob/522b1f8b84345d79cd51fb1c19b4d51ab37628cb/src/main/resources/data/minecraft/enchantment/channeling.json#L7-L50
[channeling-rods]: https://github.com/HungLo2020/MattMC/blob/522b1f8b84345d79cd51fb1c19b4d51ab37628cb/src/main/resources/data/minecraft/tags/block/lightning_rods.json#L1-L12
[channeling-impact]: https://github.com/HungLo2020/MattMC/blob/522b1f8b84345d79cd51fb1c19b4d51ab37628cb/src/main/java/net/minecraft/world/entity/projectile/ThrownTrident.java#L141-L159
[channeling-summon]: https://github.com/HungLo2020/MattMC/blob/522b1f8b84345d79cd51fb1c19b4d51ab37628cb/src/main/java/net/minecraft/world/item/enchantment/effects/SummonEntityEffect.java#L27-L47
[channeling-weather]: https://github.com/HungLo2020/MattMC/blob/522b1f8b84345d79cd51fb1c19b4d51ab37628cb/src/main/java/net/minecraft/world/level/storage/loot/predicates/WeatherCheck.java#L23-L28
[channeling-sky]: https://github.com/HungLo2020/MattMC/blob/522b1f8b84345d79cd51fb1c19b4d51ab37628cb/src/main/java/net/minecraft/advancements/critereon/LocationPredicate.java#L44-L75
[channeling-broken]: https://github.com/HungLo2020/MattMC/blob/522b1f8b84345d79cd51fb1c19b4d51ab37628cb/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L318-L334
[channeling-strike]: https://github.com/HungLo2020/MattMC/blob/522b1f8b84345d79cd51fb1c19b4d51ab37628cb/src/main/java/net/minecraft/world/entity/LightningBolt.java#L69-L99
[channeling-hazards]: https://github.com/HungLo2020/MattMC/blob/522b1f8b84345d79cd51fb1c19b4d51ab37628cb/src/main/java/net/minecraft/world/entity/LightningBolt.java#L127-L184
[channeling-clamp]: https://github.com/HungLo2020/MattMC/blob/522b1f8b84345d79cd51fb1c19b4d51ab37628cb/src/main/java/net/minecraft/core/BlockPos.java#L255-L261
