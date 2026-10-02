# Trading

Trade with an adult, employed [Villager](../mobs/Villager.md) to exchange goods and [Emeralds](../items/Emerald.md). The villager's profession, trading level, selected offers, and the world's enabled features determine what is available. Check the actual trading screen before gathering a large payment.

## Make a trade

1. Give an unemployed adult access to an available job-site block, or find a villager that already has a profession. The [Villager page](../mobs/Villager.md#professions-and-job-sites) lists the matching blocks
2. Interact with the villager and select an offer
3. Supply the displayed input item or items, including a second input when the offer requires one
4. Take the result to complete the trade and consume the payment

Babies, unemployed villagers, and nitwits do not provide ordinary villager trades. A sleeping villager or one already trading with another player will not start a new session.

## Offers and trading levels

Villagers have **five trading levels**, starting at level 1. Trading awards villager experience, which is separate from the player's experience levels. The cumulative villager-experience thresholds are:

| Reach level | Total villager experience needed |
| --- | --- |
| 2 | 10 |
| 3 | 70 |
| 4 | 150 |
| 5 | 250 |

Different offers award different amounts of villager experience. Once the progress bar fills, close the trading screen and give the villager a moment to advance. A promotion adds offers from the new level's pool; existing offers remain.

The ordinary selection process picks **up to two valid offers from each newly unlocked level's pool**, at random. A profession's complete list is therefore not a promise that one villager will have every possible trade.

Before its first experience gain, a level-1 villager can lose its profession after losing its claimed job site. Once it has villager experience, removing the workstation does not reset its profession and offers. See [employment and changing jobs](../mobs/Villager.md#employment-and-changing-jobs).

## Stock and restocking

Each offer has its own use limit. Completing that offer uses one unit of its allowance; when the limit is reached, that offer is out of stock even if other offers still work. There is no single universal stock limit for all trades.

Villagers replenish used offers **while working at their own claimed job-site block**. Keep that block accessible and allow work time. Merely placing a spare workstation beside a villager does not ensure that it is the block the villager has claimed.

Restocking is timed and limited, normally allowing two restocks in a day rather than unlimited instant refills. A partially used offer can also be replenished; it need not be completely sold out. Restocking clears the offers' use counts and updates demand, so the refreshed price may differ.

## Prices can change

An offer has a base price, but the displayed first input can change with:

- **Demand:** heavy use can increase the required amount; lower use and later restocks can reduce accumulated demand
- **Player reputation:** the villager's reputation information about the trading player can change the price
- **Hero of the Village:** this effect supplies an additional discount

The first input is constrained to at least one item and no more than that item's stack limit. A required second input still has to be supplied. Use the current displayed amounts rather than assuming a permanently fixed exchange rate or discount.

### Verified example: selling Wheat

The regular level-1 Farmer pool includes an offer with a **base payment of 20 Wheat for 1 Emerald**, a **16-use stock allowance**, and **2 villager experience per completed trade**.

This is one of five possible level-1 Farmer listings. It is not guaranteed on every Farmer, and 20 Wheat is the base price before demand and player-specific adjustments. A Farmer uses a **Composter** as its job site.

## Optional trade rebalance

The `minecraft:trade_rebalance` feature is **not part of the default feature set** in the reviewed source. When enabled, villagers use the experimental profession table where one exists, falling back to the regular table for other professions.

The checked experimental replacements cover **Librarians and Armorers** and include villager-type-dependent offers. Do not apply a trade-rebalance book or armor table to every world, or assume two villagers of the same profession must have identical stock. The Farmer example above comes from the regular table, which is also the fallback for Farmers under this feature.

## Related pages

- [Raids and Hero of the Village](../mechanics/Raid.md#victory-defeat-and-stopping)

- [Villager: professions and job sites](../mobs/Villager.md)
- [Emerald](../items/Emerald.md)
- [Gameplay](../Gameplay.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01, using active `src/main` code and data. No in-game trading or restocking test was run. This is a guide to villager trading basics and one selected offer, not a complete offer catalog or a guide to Wandering Trader behavior.

- [Starting trades, restocking, prices, experience, and offer-table selection](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/npc/Villager.java)
- [Payment consumption when taking the result](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/inventory/MerchantResultSlot.java#L51-L66)
- [Level thresholds](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/npc/VillagerData.java)
- [Use counting and random offer selection](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java)
- [Per-offer stock, demand, and price calculation](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/trading/MerchantOffer.java)
- [Working at the claimed job site](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/ai/behavior/WorkAtPoi.java)
- [Regular and experimental offer pools, including the Farmer example](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java)
- [Default and optional feature flags](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/flag/FeatureFlags.java#L32-L42)
- [Trade-rebalance pack's feature declaration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/datapacks/trade_rebalance/pack.mcmeta)
