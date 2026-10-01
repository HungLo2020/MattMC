# Villager

Villagers can buy and sell goods once they are adults with a trading profession. Use them as a renewable source of selected supplies and [Emeralds](../items/Emerald.md), provided they can work and restock. See [Trading](../trading/Trading.md) for payments, levels, stock limits, and changing prices.

## Professions and job sites

An unemployed adult can claim an available job-site block it can reach. The block determines its profession. A nitwit cannot acquire a trading profession, and babies do not take jobs or open the trading screen.

| Profession | Job-site block |
| --- | --- |
| Armorer | Blast Furnace |
| Butcher | Smoker |
| Cartographer | Cartography Table |
| Cleric | Brewing Stand |
| Farmer | Composter |
| Fisherman | Barrel |
| Fletcher | Fletching Table |
| Leatherworker | Cauldron, including filled variants |
| Librarian | Lectern |
| Mason | Stonecutter |
| Shepherd | Loom |
| Toolsmith | Smithing Table |
| Weaponsmith | Grindstone |

Each job-site block has room for one claimant. A nearby block may already belong to another villager; proximity alone does not establish ownership. Keep a clear route to the claimed workstation so the villager can approach it and work.

## Employment and changing jobs

A level-1 villager with **zero villager experience** can return to unemployment after it loses its job site. This lets an untraded villager take a different profession when it later claims another kind of workstation. Changing profession clears the old offers.

After trading has awarded villager experience, removing the workstation does **not** make the villager forget its profession or reset its offers. It still needs an appropriate claimed workstation to replenish used trades. Do not dismantle a working trading setup expecting an experienced villager to become a new profession.

## Interacting and unlocking offers

Interact with an awake adult that has offers and is not already trading with another player. The trading menu shows that villager's selected offers, its current prices, and its progress toward the next level.

Trading unlocks further levels, up to level 5. The profession alone does not guarantee a particular offer: listings are selected from level-specific pools, and the optional trade-rebalance feature changes some pools. Consult the [trading guide](../trading/Trading.md#offers-and-trading-levels) before planning around a specific purchase.

If an offer sells out, allow the villager to work at its own job site. Work-based restocking is limited and not an immediate response to placing a block.

## Creative use

The entity ID is `minecraft:villager`. For Creative testing and mapmaking, use the [Villager Spawn Egg](../items/VillagerSpawnEgg.md).

## Related pages

- [Trading](../trading/Trading.md)
- [Emerald](../items/Emerald.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01, using active `src/main` code. No in-game employment or trading test was run. This page covers jobs and trading, not a complete guide to spawning, breeding, raids, or village population management.

- [Profession registration and job-site matching](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/npc/VillagerProfession.java)
- [Workstation blocks and one-claimant capacity](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiTypes.java)
- [Finding and reaching an available job site](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/ai/behavior/AcquirePoi.java) and [villager job-search rules](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/ai/behavior/VillagerGoalPackages.java#L30-L66)
- [Assigning a profession](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/ai/behavior/AssignProfessionFromJobSite.java) and [zero-experience profession reset](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/ai/behavior/ResetProfession.java)
- [Interaction, profession changes, and trading behavior](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/npc/Villager.java)
- [Workstation restocking](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/ai/behavior/WorkAtPoi.java)
- [Entity registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/EntityType.java#L1468-L1473) and [spawn-egg registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java#L1995)
