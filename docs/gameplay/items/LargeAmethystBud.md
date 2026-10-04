# Large Amethyst Bud

**Large Amethyst Bud** (`minecraft:large_amethyst_bud`) is an immature crystal. Collect it with Silk Touch for decoration; this stage does not drop Amethyst Shards. [Item registration][items] · [Bud loot][loot]

## Obtaining

Break the bud with a **Silk Touch** tool to receive **one Large Amethyst Bud**. Without Silk Touch it drops nothing, including no Shards; Fortune adds no yield. It is in the pickaxe mining-speed tag, but has **no correct-tool or pickaxe-tier requirement** for this drop. An unbroken Silk Touch pickaxe is a straightforward collection tool. [Loot condition][loot] · [Block properties][blocks] · [Mining tag][pickaxe] · [Player drop gate][player] · [Harvest dispatch][harvest]

The item also appears in the Creative menu. No bundled recipe producing it was found in the checked recipe inventory. For the natural source, see [finding an Amethyst geode](../blocks/Amethyst.md#finding-a-geode). [Creative listing][creative]

## Usage

Place it as a crystal decoration with **light level 4**. It is the final immature stage after [Small Amethyst Bud](SmallAmethystBud.md) and [Medium Amethyst Bud](MediumAmethystBud.md), immediately before a mature [Amethyst Cluster](AmethystCluster.md). See the [Amethyst family guide](../blocks/Amethyst.md#large-bud) for the full stage comparison. [Stage properties][blocks] · [Growth sequence][growth]

It can project from a floor, wall or ceiling in any of six directions. The supporting face must be sturdy; placement faces the bud outward from the clicked face. It can be waterlogged, and placing it into source Water sets that state. See [crystal placement and waterlogging](../blocks/Amethyst.md#placing-crystals-and-making-sound). [Placement and support][crystal]

## Behavior

A Large Bud becomes a mature **Amethyst Cluster** when the adjoining Budding Amethyst selects its face for a successful growth attempt. Wait for that change before harvesting Shards. The bud must face outward from that Budding Amethyst block. A collected bud can resume growth if replanted on a suitable face of an intact Budding Amethyst block; on ordinary Amethyst or another decorative support, it does not grow by itself. Growth depends on the budding block's random ticks, not a fixed timer. [Growth callback][growth] · [Random-tick registration][blocks] · [Server dispatch][tick]

Preserve the original **Budding Amethyst**: normal Survival harvesting cannot collect it, even with Silk Touch, and neither this bud nor a decorative Block of Amethyst replaces it as a growth source. Use the shared [growth guide](../blocks/Amethyst.md#growing-more-crystals) for timing and water conditions. [Growth source][growth] · [Budding loot][budding-loot]

**Collect the bud before removing its support.** Normal support loss removes it without returning a bud or Shards. See [harvesting and protecting the growth block](../blocks/Amethyst.md#harvesting-and-protecting-the-growth-block). [Support check][crystal] · [Removal dispatch][support] · [Empty-tool drops][removal] · [Bud loot][loot]

## Notes

* This item is the item form of the `minecraft:large_amethyst_bud` block.
* Related: [Budding Amethyst](BuddingAmethyst.md) · [Amethyst Cluster](AmethystCluster.md) · [Amethyst Shard](AmethystShard.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked the item and block registrations, exact loot, bundled recipe outputs, pickaxe tag, ordinary harvest gate, growth dispatch, support, facing and waterlogging. No gameplay test was run; data packs and server ticking can change drops and growth. See the [shared Amethyst guide](../blocks/Amethyst.md) for full placed-block guidance.

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2515-L2518
[blocks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5963-L6000
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L833-L840
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json#L304-L309
[player]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L282-L292
[growth]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/BuddingAmethystBlock.java#L25-L55
[crystal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/AmethystClusterBlock.java#L61-L115
[tick]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerLevel.java#L488-L503
[budding-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/budding_amethyst.json
[support]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L217-L226
[removal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/Level.java#L263-L278
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/large_amethyst_bud.json
