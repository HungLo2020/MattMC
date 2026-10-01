# Netherite Scrap

Netherite Scrap is a material registered as `minecraft:netherite_scrap`. Combine it with Gold Ingots to make Netherite Ingots; scrap is not the checked smithing addition by itself.

## Obtaining

Process one [Ancient Debris](AncientDebris.md) to obtain one scrap:

| Device | Recipe time | At 20 ticks per second |
| --- | --- | --- |
| Furnace | 200 ticks | 10 seconds |
| Blast Furnace | 100 ticks | 5 seconds |

Both recipes declare **2 experience**. Device fuel and experience collection still follow their own rules.

Ancient Debris is in the Diamond-required mining tag, so use an appropriate Diamond or Netherite pickaxe rather than assuming an Iron tool is sufficient. Its block loot table drops Ancient Debris itself; the scrap comes from processing it afterward.

## Crafting

Combine **four Netherite Scrap and four Gold Ingots**, shapeless, to make **one Netherite Ingot**. This recipe uses eight ingredients, so use a Crafting Table.

The scrap registration is fire-resistant. That is not a claim of protection against every possible way a dropped item can be destroyed or lost.

## Related pages

- [Netherite Ingot](NetheriteIngot.md)
- [Mining tiers](../mechanics/Mining.md)
- [Smelting](../smelting/Smelting.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game loot, smithing, or smelting test was run.

- [Smelting recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/smelting/netherite_scrap.json)
- [Blasting recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/blasting/netherite_scrap_from_blasting.json)
- [Ingot recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/netherite_ingot.json)
- [Mining tier](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/needs_diamond_tool.json)
- [Ancient Debris loot](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/blocks/ancient_debris.json)
- [Fire-resistant registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java#L1304-L1305)
