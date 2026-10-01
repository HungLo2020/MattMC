# Nether Wart

Nether Wart is a plantable brewing ingredient registered as `minecraft:nether_wart`.

## Obtaining and growing

Collect it from [Nether Wart crops](../blocks/NetherWart.md). The fortress stalk-room generator provides a source; mature crops have a base 2–4 item drop before Fortune and explosion rules.

Plant on Soul Sand to grow more. It uses a separate crop class from Wheat, so consult its block guide before assuming bone meal, Farmland, or hoe area harvesting will work.

## Brewing

Adding Nether Wart to a Water Bottle in a fueled [Brewing Stand](../blocks/BrewingStand.md) produces an **Awkward Potion**. Awkward Potion is a starting stage for many effect-potion transformations, not an effect by itself in this guide.

Not every potion starts with Nether Wart: the current registry includes a direct Water + Fermented Spider Eye route to Weakness. Follow a verified potion chain rather than adding ingredients blindly.

## Related pages

- [Nether Wart crop](../blocks/NetherWart.md)
- [Brewing guide](../brewing/Brewing.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game brewing, growth, or collection test was run.

- [Water-to-Awkward mix](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L139-L142)
- [Weakness exception](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L187-L188)
- [Crop loot](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/blocks/nether_wart.json)
- [Fortress planting](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/levelgen/structure/structures/NetherFortressPieces.java#L989-L992)
