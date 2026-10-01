# Emerald

Emeralds are a currency for [Trading](../trading/Trading.md), a crafting ingredient, and an armor-trim material. The item is registered as `minecraft:emerald`.

## Obtaining

- **Trade goods to villagers:** offers that buy items pay Emeralds. For example, the regular level-1 Farmer pool can include a Wheat-buying offer. Its selection, base price, stock limit, and price adjustments are explained in the [trading example](../trading/Trading.md#verified-example-selling-wheat)
- **Mine Emerald Ore or Deepslate Emerald Ore:** their ordinary loot branch drops Emeralds and applies the Fortune ore-drop bonus; Silk Touch selects the ore block instead. Correct-tool rules apply, and both ores are in the bundled Iron-required mining group
- **Unpack a Block of Emerald:** one block returns nine Emeralds through shapeless crafting

These are selected verified acquisition routes, not a survey of ore distribution or every loot source. See [Mining](../mechanics/Mining.md) for tool-tier rules.

## Trading

Emeralds can be either the payment or the result of a trade. Some purchases also require a second item, so having enough Emeralds alone does not guarantee that an offer can be completed.

Offers are specific to the villager and its current stock. Demand, reputation, and Hero of the Village can change the first input price. Check the displayed payment each time rather than assuming a fixed exchange rate.

Use [Villagers](../mobs/Villager.md) with the appropriate job-site blocks and let them work to replenish used trades.

## Crafting and other uses

- Nine Emeralds in a 3 × 3 square make **one Block of Emerald**; unpacking it returns all nine
- Emerald is an accepted addition material for **armor trimming** with a compatible armor item and trim template; see [Smithing](../smithing/Smithing.md)
- Emerald belongs to the bundled **beacon payment items** tag

Packing Emeralds into blocks is useful for storage, but ordinary Emerald trade inputs require the individual item rather than its block.

## Related pages

- [Trading](../trading/Trading.md)
- [Villager](../mobs/Villager.md)
- [Block of Emerald](BlockOfEmerald.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01, using active `src/main` code and data. No in-game trading, mining, crafting, beacon, or smithing test was run. Enabled features and data packs can affect availability and recipes.

- [Emerald registration and trim material](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java#L1294)
- [Villager trade inputs and results](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java) and [payment matching and price changes](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/trading/MerchantOffer.java)
- [Emerald Ore loot](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/blocks/emerald_ore.json) and [Deepslate Emerald Ore loot](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/blocks/deepslate_emerald_ore.json)
- [Iron-required mining group](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json)
- [Block packing recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/emerald_block.json) and [unpacking recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/emerald.json)
- [Trim-material ingredients](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/trim_materials.json)
- [Beacon payment items](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/beacon_payment_items.json)
