# Cactus

**Cactus** (`minecraft:cactus`) is the inventory form of the [Cactus block](../blocks/Cactus.md). Replant it to grow more, smelt it for dye, or display it in a pot. [Item registration][item]

## Obtaining

- Collect a placed cactus under the [block's mining rules](../blocks/Cactus.md#finding-and-collecting-cactus)
- Recover the plant from a [potted cactus](../blocks/FlowerPot.md#supported-plants)
- A [Wandering Trader](../mobs/WanderingTrader.md) can offer **one cactus for three Emeralds**, with **eight uses** of that selected offer. It belongs to a randomized offer pool, so not every trader sells it. [Offer][trade] · [Offer construction][offer] · [Trader selection][selection] · [Randomized choices][random-offers]

The [block guide](../blocks/Cactus.md#finding-and-collecting-cactus) covers the checked natural-generation routes.

## Uses

**Planting:** place it on a valid support with room around it. See [placement](../blocks/Cactus.md#placement-and-support), [growth and flowers](../blocks/Cactus.md#growth-and-flowers), and [loose-item hazards](../blocks/Cactus.md#contact-and-loose-item-hazards) before building a farm.

**Smelting:** use the [Green Dye recipe](GreenDye.md#smelting). Its exact output, time, and recipe XP are documented there; the [Furnace guide](../blocks/Furnace.md) owns shared fuel and experience handling.

**Potting:** use cactus on an empty [Flower Pot](../blocks/FlowerPot.md). It has a registered potted form. See the pot guide for removal, drops, support, and growth rules. [Potted registration][pot]

**Composting:** cactus is accepted by a [Composter](Composter.md). It fills the first level of an empty composter, then has a **50% chance per consumed cactus** to add a level while the composter can accept more. [Registered compost value][compost] · [Level increase][compost-roll]

Related: [Cactus block](../blocks/Cactus.md) · [Green Dye](GreenDye.md) · [Cactus Flower](CactusFlower.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Checked the block item, trader offer and selection path, potted registration, and compost value/level handling. Placed behavior and the dye recipe are documented on their linked canonical pages. No in-game trading, potting, or composting test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L489-L490
[trade]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L750-L764
[offer]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1438-L1479
[selection]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java#L133-L141
[pot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L2713
[compost]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L115-L120
[compost-roll]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L304-L319
[random-offers]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L233
