# Luck and Unluck

**Luck changes the player's luck attribute; Unluck lowers it. The checked default loot uses that value to change fishing categories, not to improve every drop or random event.** Luck is useful when reeling in fishing loot, especially eligible treasure. It does not replace [Luck of the Sea](../mechanics/Fishing.md#waiting-weather-and-enchantments), [Fortune](../enchanting/MiningEnchantments.md), or Looting. [Effect modifiers][effects] · [Fishing caller][fishing-call] · [Loot calculations][pool]

## Luck

Effect ID: `minecraft:luck`.

Luck adds **+1 luck per effect level**: I adds +1, II adds +2, and so on. The ordinary registered **Potion of Luck** gives **Luck I for 6,000 ticks / five minutes** when drunk. There is no registered extended or strong Luck potion. [Registration][effects] · [Scaling][scaling] · [Potion types][potions]

MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) lists the enabled Luck potion in **drinkable, splash, lingering, and tipped-arrow forms**. These are ordinary category entries, so the documented browser insertion route works in Creative. This route does not require `/effect` command permission. [Bottle listings][browser-bottles] · [Arrow listing][browser-arrows] · [Enabled-type enumeration][browser-types]

**There is no ingredient recipe that brews Luck's potion contents** in the checked brewing registry. Redstone, Glowstone, and Fermented Spider Eye do not turn it into longer Luck, stronger Luck, or Unluck. Once you have a Luck bottle, however, the ordinary **Gunpowder drinkable-to-splash** and **Dragon's Breath splash-to-lingering** conversions preserve its potion type. Surround a Lingering Potion of Luck with eight ordinary Arrows in a Crafting Table to make **eight Arrows of Luck**. [Full brewing list][brewing] · [Container conversion][brew-conversion] · [Tipped-arrow recipe][arrow-recipe] · [Active recipe][arrow-recipe-data]

The checked random tipped-arrow trade selects only brewable potion types, so it does not supply Luck arrows. The source/resource review found no additional independent source of Luck or Unluck in the checked food, mob, loot or recipe data; this does not exclude mobs copying effects already supplied to them, command-created items, or added data packs. [Copying an existing effect][effect-copy] [Trade filter][arrow-trade] · [Brewable test][brewable]

## Unluck

Effect ID: `minecraft:unluck`.

Unluck adds **−1 luck per level**: I subtracts 1, II subtracts 2, and so on. Negative player luck can reduce treasure's fishing weight and increase fish/junk weights; it does not make every action fail. There is **no ordinary Unluck potion type**, so registering the status effect does not add an Unluck bottle or arrow to the browser. Permitted `/effect` commands can apply it, and custom potion contents can carry it through the ordinary delivery handlers. [Modifier][effects] · [Potion registry][potions] · [Custom effect contents][custom-effects] · [Commands](#commands-and-clearing)

## Levels and combining effects

The stored amplifier plus one is the effect level: amplifier `0` means I. Amplifiers are clamped to **0–255**, allowing levels I–256. A player's default luck is **0**, and the final luck attribute is clamped to **−1,024 through +1,024** after other attribute modifiers. [Level storage][levels] · [Player attribute][player-attribute] · [Luck range][attribute-range] · [Attribute arithmetic][attribute-math] · [Clamp][attribute-clamp]

Luck and Unluck have distinct modifiers and can coexist. With no other luck changes, **Luck II plus Unluck I gives +1 luck**; equal levels have a net contribution of zero while both statuses remain active. Clearing or outlasting one can expose the other's remaining contribution. Reapplying the same effect does not add levels: stronger effects take precedence, equal-level longer applications extend the remaining time, and a weaker hidden continuation keeps counting down. [Distinct modifiers][effects] · [Effect replacement][replacement] · [Timers][timers]

These are player-loot consequences. The base living-entity `getLuck()` returns zero; players override it to read their attribute. Merely putting a Luck icon on a mob does not establish equivalent player-style loot behavior. [Base getter][base-luck] · [Player getter][player-luck]

## What the loot calculation changes

A loot operation must supply a luck value, and its table must use that value meaningfully. The default context starts at **0**; merely supplying a player as an entity parameter does not copy their luck. Nested tables receive the same context. [Context default and setter][params] · [Context getter][context] · [Nested tables][nested]

For each eligible weighted entry:

`effective weight = max(floor(base weight + quality × context luck), 0)`

An omitted quality is **0**. Failed conditions and entries with zero weight do not enter the weighted choice; the remaining positive weights are normalized against their sum. A weight of 7 is not automatically a 7% chance. [Defaults and arithmetic][weights] · [Selection][pool]

For a pool that passes its conditions:

`roll count = rolls.getInt(context) + floor(bonus_rolls.getFloat(context) × context luck)`

An omitted `bonus_rolls` is **0**. The loop makes no rolls if the calculated count is zero or negative. These extra rolls are a table-controlled feature, not an automatic extra item per Luck level. For example, hypothetical `rolls=1`, `bonus_rolls=0.5`, and luck −1 gives `1 + floor(−0.5) = 0`; that example is **not a bundled fishing setting**. [Pool defaults and roll loop][pool]

The inspected base loot tables have nonzero quality **only in the three top-level fishing entries**, and all their bonus-roll values are zero or omitted. The complete loot consumer scan found no separate direct luck use in functions, conditions, or number providers. Thus this bundle does not give Luck a general chest, mining, mob-drop, archaeology, or Vault reward bonus. Looting and Fortune functions read their enchantments separately; a fixed random-chance condition does not gain a Luck bonus. Added data packs can change these conclusions. [Base loot directory][base-tables] · [Loot implementation][loot-package] · [Looting count function][looting] · [Fortune count function][fortune] · [Chance condition][random-chance]

### Where player luck is supplied

| Checked action | Luck passed into loot |
| --- | --- |
| Reel in a bite's fishing loot | Rod bonus saved at casting **plus current player luck at retrieval** |
| First player opening of an unresolved loot container, including container vehicles | Opening player's current luck |
| Player-credited entity death loot | Last credited player's luck, when the player-credit flag and player are present |
| First accepted brushing of an unresolved suspicious block | Brushing entity's getter; a player supplies their current luck |
| Accept a Vault key and resolve its rewards | Key user's current luck; the separate display preview uses the default zero |

[Fishing][fishing-call] · [Containers][containers] · [Vehicles][vehicles] · [Death-loot credit][death-credit] · [Death-loot context][death-loot] · [Brushing][brush] · [Vault reward and preview][vault]

**Opening timing only matters if the table responds to luck.** For a [Chest](../blocks/Chest.md), ordinary menu creation supplies the player, but an earlier inventory read or removal, including Hopper extraction, can resolve its pending loot with **no player and zero context luck**. The stored loot-table assignment is cleared on resolution, so drinking Luck afterward does not reroll existing contents. The default chest tables reviewed here still have no Luck benefit in either case. [Menu and inventory access][container-access] · [Hopper extraction][hopper] · [One-time resolution][containers]

Ordinary block drops, Piglin barter, gift/shearing handlers, and Trial Spawner completion rewards do not copy player luck into their checked contexts. `/loot`'s mine, kill, loot, and fish branches also omit it: `/loot fish` is not a test of a live rod's saved enchantment bonus plus the player's Luck. Shoebill fishing has a separate fed-bird `luckLevel × 0.5` value; it does not read the player's Luck status. [Block drops][blocks] · [Barter][barter] · [Gifts and shearing][gifts] · [Spawner rewards][spawner] · [Loot command][loot-command] · [Shoebill caller][shoebill]

## Fishing with Luck

The normal cast records the rod's Luck of the Sea bonus, **+1/+2/+3 at I/II/III**. Reeling a bite adds the player's luck **at that later moment**. Drinking Luck while a bobber is already cast can therefore affect its eventual loot roll; switching rods does not rewrite the bobber's saved bonus. Lure controls waiting separately. [Cast][cast] · [Enchantment][sea] · [Retrieval][fishing-call] · [Fishing timing](../mechanics/Fishing.md#waiting-weather-and-enchantments)

With treasure eligible, the bundled category weights are **fish `85 − L`**, **junk `10 − 2L`**, and **treasure `5 + 2L`**, each floored and clamped at zero as above. Here `L` is the **combined context luck**, not just the effect level. [Complete top-level table][fishing-table]

| Setup, with otherwise default player luck | Combined luck | Fish / junk / treasure weights | Treasure share |
| --- | ---: | --- | ---: |
| Plain rod, no effect | 0 | 85 / 10 / 5 | 5% |
| Plain rod, Luck I | 1 | 84 / 8 / 7 | 7/99 ≈ 7.07% |
| Plain rod, Unluck I | −1 | 86 / 12 / 3 | 3/101 ≈ 2.97% |
| Luck of the Sea III plus Luck I | 4 | 81 / 2 / 13 | 13/96 ≈ 13.54% |

These are conditional category shares per fishing-loot roll, not guarantees per cast. **Treasure still requires [open water](../mechanics/Fishing.md#open-water-for-treasure).** If its condition fails, treasure is excluded and the remaining weights are renormalized: Luck I then gives junk `8/92` and fish `84/92`, with no treasure. Extreme custom negative luck can reduce treasure weight to zero even in open water. [Condition and weights][fishing-table] · [Selection][pool]

Luck changes the category choice, not the listed fish proportions or the relative treasure choices within a category. The three child tables have zero quality and zero bonus rolls; treasure's enchanting functions still use their configured **level 30**, without adding player luck to that level. The existing [Fishing guide](../mechanics/Fishing.md#what-you-can-catch) owns the catch lists, open-water test, and rod durability. [Fish table][fish-table] · [Junk table][junk-table] · [Treasure table][treasure-table] · [Enchanting consumer][enchant-function]

## Delivery and duration

For an unchanged ordinary Luck potion type, all these deliveries apply **level I**:

| Delivery | Duration received |
| --- | --- |
| Drinkable | 6,000 ticks / 5:00 |
| Splash | Up to 6,000 ticks; decreases with impact distance, rounds to a tick, and omits results of 20 ticks or less |
| Lingering cloud contact | 1,500 ticks / 1:15 per accepted application |
| Tipped-arrow hit | 750 ticks / 37.5 seconds |

[Potion duration][potions] · [Drinking][drink] · [Splash][splash] · [Item scales][delivery-scales] · [Cloud scale/application][cloud] · [Arrow hit][arrow-hit] · [Duration scaling][duration-scale]

A lingering cloud's lifetime is separate from the effect duration it gives. Repeated eligible contact can refresh the effect under the cloud's own checks. Custom effect contents and duration-scale components can change these values; there is no standard Unluck bottle duration to copy from this table. See [shared potion delivery](MovementEffects.md#delivery-changes-duration) for the common rules. [Cloud creation][cloud-create] · [Custom contents][custom-effects]

## Commands and clearing

With **command permission level 2**, these source-checked examples give five minutes at level I or remove one selected effect:

```text
/effect give @s minecraft:luck 300 0
/effect give @s minecraft:unluck 300 0
/effect clear @s minecraft:luck
/effect clear @s minecraft:unluck
```

These non-instant duration arguments are **seconds**, while the amplifier is zero-based. The command also supports `infinite`; normal finite durations are 1–1,000,000 seconds and amplifiers 0–255. This permission gate is separate from obtaining ordinary listed Luck items through the browser. [Command syntax and permission][commands] · [Duration conversion][command-duration]

Wait for a finite effect to expire or drink **[Milk](../items/MilkBucket.md)** to remove both effects along with other active statuses. A Honey Bottle removes Poison only. Successful [Totem of Undying](../items/TotemOfUndying.md#activation) protection clears old effects before granting its protections, and ordinary [death respawn](../mechanics/DeathAndRespawn.md) does not preserve these effects. Clearing a status removes its modifier; it does not reset other changes to the luck attribute or undo loot already generated. [Milk and Honey][consumables] · [Clear callback][clear-callback] · [Status removal][clear] · [Modifier removal][remove-modifiers] · [Totem][totem] · [Respawn copy][respawn]

## Sources and verification

Source-reviewed on **2026-10-03** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`. The review traced effect, potion, browser, brewing, command, delivery, and clearing paths; every `getLuck`/`withLuck` consumer; loot functions, conditions and number providers; and all **1,410 base singular `loot_table/` JSON files**. Those are source-file counts, not a runtime registry dump. The **five optional trade-rebalance loot overrides** were checked separately and likewise have no nonzero quality or bonus rolls. TaCZ's **191 plural recipe files** were a separate acquisition check through its dedicated recipe loader, not counted as loot tables. [Loot loading][loot-loader] · [Optional-pack selection][packs] · [TaCZ loader][tacz]

No in-game effect, inventory request, brewing, fishing distribution, container, combat, brushing, Vault, command, or timing test was run. Server data packs, item components, attribute changes, and other releases can change the reviewed results. Times assume 20 game ticks per second.

Related: [Status effects](Effects.md) · [Fishing](../mechanics/Fishing.md) · [Brewing](../brewing/Brewing.md) · [Inventory item browser](../mechanics/InventoryBrowser.md)

[effects]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffects.java#L95-L104
[scaling]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffect.java#L201-L204
[potions]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/Potions.java#L11-L80
[browser-bottles]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1756-L1770
[browser-arrows]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1671-L1680
[browser-types]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2207-L2213
[brewing]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L127-L191
[brew-conversion]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L73-L125
[arrow-recipe]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/crafting/TippedArrowRecipe.java#L14-L48
[arrow-recipe-data]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/tipped_arrow.json
[arrow-trade]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1530-L1543
[brewable]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L63-L71
[custom-effects]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionContents.java#L41-L54
[levels]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L49-L77
[player-attribute]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L220-L230
[attribute-range]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L53-L53
[attribute-math]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/attributes/AttributeInstance.java#L150-L167
[attribute-clamp]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/attributes/RangedAttribute.java#L30-L33
[replacement]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L123-L148
[timers]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L214-L261
[base-luck]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L500-L502
[player-luck]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L1811-L1814
[params]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/storage/loot/LootParams.java#L46-L96
[context]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/storage/loot/LootContext.java#L71-L73
[nested]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/storage/loot/entries/NestedLootTable.java#L44-L49
[weights]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/storage/loot/entries/LootPoolSingletonContainer.java#L21-L131
[pool]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/storage/loot/LootPool.java#L30-L101
[base-tables]: https://github.com/HungLo2020/MattMC/tree/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table
[loot-package]: https://github.com/HungLo2020/MattMC/tree/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/storage/loot
[looting]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java#L65-L82
[fortune]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/storage/loot/functions/ApplyBonusCount.java#L66-L76
[random-chance]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/storage/loot/predicates/LootItemRandomChanceCondition.java#L21-L24
[fishing-call]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/FishingHook.java#L435-L452
[containers]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/RandomizableContainer.java#L73-L90
[vehicles]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/vehicle/ContainerEntity.java#L97-L111
[death-credit]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1475
[death-loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1513-L1528
[brush]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/BrushableBlockEntity.java#L65-L114
[vault]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/vault/VaultBlockEntity.java#L297-L329
[container-access]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/RandomizableContainerBlockEntity.java#L49-L93
[hopper]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L241-L259
[blocks]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/Block.java#L345-L361
[barter]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L440-L446
[gifts]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1544-L1574
[spawner]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawner.java#L236-L247
[loot-command]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/commands/LootCommand.java#L425-L489
[shoebill]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/ai/ShoebillAIFish.java#L82-L95
[cast]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/FishingRodItem.java#L52-L56
[sea]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/enchantment/luck_of_the_sea.json
[fishing-table]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/gameplay/fishing.json
[fish-table]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/gameplay/fishing/fish.json
[junk-table]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/gameplay/fishing/junk.json
[treasure-table]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/gameplay/fishing/treasure.json
[enchant-function]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantWithLevelsFunction.java#L56-L61
[drink]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionContents.java#L154-L164
[splash]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/ThrownSplashPotion.java#L39-L73
[delivery-scales]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L2246-L2255
[cloud]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/AreaEffectCloud.java#L213-L237
[arrow-hit]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/Arrow.java#L104-L111
[duration-scale]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L180-L188
[cloud-create]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/ThrownLingeringPotion.java#L31-L44
[commands]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/commands/EffectCommands.java#L33-L149
[command-duration]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/commands/EffectCommands.java#L153-L177
[consumables]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L13-L64
[clear-callback]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/consume_effects/ClearAllStatusEffectsConsumeEffect.java#L21-L24
[clear]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L943-L954
[remove-modifiers]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1076-L1082
[totem]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/DeathProtection.java#L25-L42
[respawn]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1553-L1583
[loot-loader]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L54-L67
[packs]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/packs/repository/ServerPacksSource.java#L35-L71
[tacz]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L174
[effect-copy]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySkunk.java#L290-L303
