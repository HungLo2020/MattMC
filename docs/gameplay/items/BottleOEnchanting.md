# Bottle o' Enchanting

**Throw a Bottle o' Enchanting to release collectable experience orbs.** Each bottle that breaks awards **3–11 experience points**, averaging **7**; those are points, not levels. Save bottles for when you can collect their orbs or use them for [Mending](../mechanics/Experience.md#why-mending-can-leave-the-bar-unchanged). [Impact reward][impact] · [Orb collection][pickup]

## Obtaining

A **level-5 (Master) Cleric** sells **1 Bottle o' Enchanting for a base price of 3 Emeralds**. The bundled offer has **12 uses before restocking**; demand and discounts can change the displayed price. The level-5 Cleric pool has two offers and the active selector takes both. Follow [Trading](../trading/Trading.md) for levelling the villager, payment and restocking. [Cleric offers][cleric] · [Offer defaults][offer] · [Active trade selection][trade-select] · [Selection count][trade-count]

These generated chests can also provide ready-made bottles:

| Chest source | Bottles when the entry is selected | Relevant pool |
| --- | ---: | --- |
| [Ancient City ordinary chest](../structures/AncientCity.md#ordinary-city-chests) | 1–3 | 5–10 weighted selections; bottle weight 3 of 86 |
| [Pillager Outpost tower](../structures/PillagerOutpost.md#chest-rewards) | 1 | 2–3 weighted selections; bottle weight 7 of 22 |
| [Shipwreck treasure chest](../structures/Shipwreck.md) | 1 | 3–6 weighted selections; bottle weight 5 of 150 |

[City loot][city-loot] · [Outpost loot][outpost-loot] · [Shipwreck loot][ship-loot] · [Weighted selection][loot-roll]

These weights apply **per selection**, not per chest or structure. Selections can repeat or all miss the bottle entry. The source routes use actual chest assignments; a shipwreck variant without its treasure chest cannot supply that table. Ancient City ice boxes have no bottle entry. The bundled optional [Trade Rebalance pack](../trading/Trading.md#optional-trade-rebalance) keeps the listed bottle entries and relevant pool weights in its city/outpost replacements. [City assignment][barracks] · [Outpost assignment][outpost-tower] · [Shipwreck marker mapping][ship-map] · [Marker assignment][ship-marker] · [Ice-box loot][ice-loot] · [City replacement][city-rebalance] · [Outpost replacement][outpost-rebalance]

## Usage

Use the held bottle to throw it, preferably toward a safe nearby surface where you can collect the orbs. Ordinary Survival throwing consumes **one bottle**, with **no empty Glass Bottle returned**. Creative's infinite-materials flag prevents the held stack from shrinking. The projectile and XP award are created on the server. [Throwing][throw] · [Consumption][consume] · [Creative flag][creative] · [Impact and removal][impact]

A powered **[Dispenser](../blocks/DispenserAndDropper.md)** also throws it, consuming one stored bottle each time that stack is selected. Its impact awards the same XP; it does not require a player to be the thrower. Use a Dispenser for an automated supply, then stand where you can safely collect the released orbs. [Registered behavior][dispenser-registration] · [Active dispense selection][dispenser] · [Projectile creation and consumption][dispense-projectile]

## Behavior

The exact reward is **3 + two independent random integers from 0 to 4**. Thus 7 points is the most likely result, and the distribution is not uniform: the outcomes 3 through 11 have relative frequencies **1, 2, 3, 4, 5, 4, 3, 2, 1 out of 25**. The total is divided into orbs, which can merge with existing orbs. [Reward calculation][impact] · [Orb creation and merging][orbs]

The points are released at the impact, not deposited straight into the thrower's bar or reserved for that player. A nearby player still has to collect them. Eligible Mending repairs happen before leftover points enter the bar, so a bottle can repair equipment without visibly increasing your level. See [Experience](../mechanics/Experience.md) for collection, point-to-level costs and Mending. [Pickup and repair][pickup]

Bottles stack to **64**, have **Uncommon** rarity and show an enchantment glint. They are thrown consumables rather than drinkable potions. [Registration][registration] · [Stack default][stack] · [Use implementation][throw]

## Notes

* Registered item: `minecraft:experience_bottle`
* Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Trades, chest assignments, active player/dispenser dispatch and projectile collision/XP collection were checked. No in-game trading, loot, throwing, dispenser or XP-distribution test was run; the distribution above is calculated from the source. Data packs, item components and server changes can alter results. [Server admission][use-admission] · [Use dispatch][item-use] · [Projectile collision][collision] · [Hit dispatch][hit-dispatch]

Related: [Experience and Mending](../mechanics/Experience.md) · [Trading](../trading/Trading.md) · [Dispenser](../blocks/DispenserAndDropper.md) · [Items](Items.md)

[impact]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/ThrownExperienceBottle.java#L38-L54
[pickup]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/ExperienceOrb.java#L274-L312
[cleric]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L457-L489
[offer]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1434-L1480
[trade-select]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/Villager.java#L819-L843
[trade-count]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L237
[city-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/ancient_city.json
[outpost-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/pillager_outpost.json
[ship-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/shipwreck_treasure.json
[loot-roll]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/LootPool.java#L60-L101
[barracks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/ancient_city/structures/barracks.nbt
[outpost-tower]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/pillager_outpost/watchtower.nbt
[ship-map]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/ShipwreckPieces.java#L69-L71
[ship-marker]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/ShipwreckPieces.java#L119-L125
[ice-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/ancient_city_ice_box.json
[city-rebalance]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/datapacks/trade_rebalance/data/minecraft/loot_table/chests/ancient_city.json
[outpost-rebalance]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/datapacks/trade_rebalance/data/minecraft/loot_table/chests/pillager_outpost.json
[throw]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ExperienceBottleItem.java#L23-L43
[consume]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1080
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L1212-L1215
[dispenser-registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L67-L79
[dispenser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/DispenserBlock.java#L79-L110
[dispense-projectile]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/dispenser/ProjectileDispenseBehavior.java#L25-L42
[orbs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/ExperienceOrb.java#L187-L211
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2022-L2024
[stack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L390
[use-admission]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1303-L1325
[item-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L300-L338
[collision]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/ThrowableProjectile.java#L49-L70
[hit-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L222-L244
