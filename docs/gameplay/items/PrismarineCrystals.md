# Prismarine Crystals

**Prismarine Crystals** (`minecraft:prismarine_crystals`) are a crafting material used in Sea Lanterns. They are registered as an ordinary item, so the crystals themselves do not place a block. Returning a stranded [Cachalot Whale](../mobs/CachalotWhale.md#rescue-reward) to water is one implemented way to obtain them. [Registration][item] · [Recipe][recipe] · [Whale reward][whale]

## Obtaining

| Source | Verified crystal result |
| --- | --- |
| An eligible whale rescue reward | **2–3**, once per whale after its nearby-player reward conditions are met |
| Breaking a Sea Lantern without Silk Touch | **2–3** before Fortune; the result is capped at **5** |
| Guardian death loot | A possible crystal entry, with **one** crystal before a Looting count bonus |
| Elder Guardian death loot | A possible crystal entry, with **one** crystal before a Looting count bonus |

The whale reward is not a death drop and does not use Looting. Its guide explains which nearby player receives credit and how the saved one-time flag works. [Rescue reward](../mobs/CachalotWhale.md#rescue-reward)

A Sea Lantern's Silk Touch alternative returns the **Sea Lantern block**, not crystals. Its ordinary crystal branch adds a random Fortune bonus and limits the resulting count to five; explosions can reduce the surviving count. The block does not require a specific mining-tool tier for its drops. [Lantern loot][lantern-loot] · [Fortune formula][fortune] · [Block properties][lantern]

Guardian and Elder Guardian crystals are selected from weighted pools that also contain fish and an empty result, so neither mob guarantees a crystal. Looting can increase the number after the crystal entry is selected; it does not change that entry's selection weight in these tables. The crystal pools have no separate player-kill condition. [Guardian loot][guardian] · [Elder Guardian loot][elder] · [Looting count function][looting]

## Crafting a Sea Lantern

At a [Crafting Table](../blocks/CraftingTable.md), combine **four Prismarine Shards and five Prismarine Crystals** to make **one Sea Lantern**:

| | | |
| --- | --- | --- |
| Prismarine Shard | Prismarine Crystals | Prismarine Shard |
| Prismarine Crystals | Prismarine Crystals | Prismarine Crystals |
| Prismarine Shard | Prismarine Crystals | Prismarine Shard |

The shards occupy the four corners; crystals fill the center and the middle of each edge. [Crafting recipe][recipe]

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Checked plain-item registration, the active whale reward callback, Sea Lantern block properties and loot, Guardian/Elder Guardian loot selection and count functions, and the shaped crafting recipe. No in-game rescue, mining, loot, or crafting test was run.

Related: [Cachalot Whale](../mobs/CachalotWhale.md) · [Prismarine Shard](PrismarineShard.md) · [Sea Lantern](SeaLantern.md) · [Guardian](../mobs/Guardian.md) · [Elder Guardian](../mobs/ElderGuardian.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L2121
[recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/sea_lantern.json
[whale]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityCachalotWhale.java#L433-L472
[lantern-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/sea_lantern.json
[fortune]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/storage/loot/functions/ApplyBonusCount.java#L156-L166
[lantern]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L3179-L3188
[guardian]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/entities/guardian.json
[elder]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/entities/elder_guardian.json
[looting]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java#L64-L81
