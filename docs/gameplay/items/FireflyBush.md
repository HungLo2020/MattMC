# Firefly Bush

**Firefly Bush** (`minecraft:firefly_bush`) places a decorative plant with **constant light level 2** and client firefly particles under suitable brightness conditions. [Firefly registry] · [Bush item registry] · [Firefly effects]

## Obtaining

Break it with any ordinary tool or your hand to collect **one Firefly Bush**. Shears and Silk Touch are unnecessary. The [family guide](../blocks/ShrubsAndDryGrass.md#checked-acquisition-examples) covers checked Plains/Swamp feature routes and the possible trader listing of **1 for 3 Emeralds**, up to 12 uses. No bundled recipe makes it. [firefly_bush loot] · [Trader plant offers] · [Offer construction]

## Usage

Place it on **Farmland or a block in the dirt support tag**. Nearby water is not a player-placement requirement. Bone Meal can create **one matching bush in an empty horizontal neighbor cell with valid ground**, leaving the first plant. [Plant ground and survival] · [dirt tag] · [Firefly growth] · [Adjacent spread helper]

It has a **30%** ordinary composting level-increase chance. It is absent from the checked standard Furnace fuel table. The [resource comparison](../blocks/ShrubsAndDryGrass.md#recipes-compost-and-fuel) explains both systems. [Compost values] · [Fuel table]

## Behavior

The plant has no collision, waterlogged form, or automatic spreading. Its registered block light remains level 2; **visible fireflies are a separate particle effect**, eligible at local brightness **13 or lower**. The [light, particles, and sound section](../blocks/ShrubsAndDryGrass.md#firefly-light-particles-and-sound) explains why particle and sound visibility can differ. [Firefly registry] · [Firefly effects] · [Firefly growth] · [Local brightness]

## Notes

The block and inventory item share `minecraft:firefly_bush`. Its ordinary hand drop differs from [Bush](Bush.md), which needs Shears or Silk Touch. [firefly_bush loot] · [bush loot]

## Sources and verification

Source-reviewed on **2026-10-02** at `053cd852a8609f4002234ce0d445d3a345b551ae`. Checked the registration, full loot, support, Bone Meal spread, constant light, client effects, selected trader listing, recipe absence, compost and fuel. No gameplay test was run.

[Firefly registry]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/Blocks.java#L6880-L6891
[Bush item registry]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/item/Items.java#L290-L294
[Firefly effects]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/FireflyBushBlock.java#L16-L45
[firefly_bush loot]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/loot_table/blocks/firefly_bush.json
[Trader plant offers]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L824-L829
[Offer construction]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1438-L1479
[Plant ground and survival]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L23-L47
[dirt tag]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/tags/block/dirt.json
[Firefly growth]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/FireflyBushBlock.java#L47-L61
[Adjacent spread helper]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/BonemealableBlock.java#L20-L37
[Compost values]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L110-L114
[Fuel table]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L34-L109
[Local brightness]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/LevelReader.java#L169-L176
[bush loot]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/loot_table/blocks/bush.json
