# Cauldron

Cauldron (`minecraft:cauldron`) is the single inventory item shared by the empty, Water, Lava, and Powder Snow Cauldron blocks. Placing it starts an empty cauldron. Use the [Cauldrons guide](../blocks/Cauldrons.md) for filling, cleaning, weather collection, dripstone, and comparator behavior. [Registration][items] · [Shared item mapping][item-map] · [Empty block][empty]

## Obtaining

Craft **seven Iron Ingots in a U shape** at a crafting table to make **one Cauldron**. Collect a placed cauldron with an **unbroken pickaxe**; Wood or a higher standard tier qualifies. Hand mining does not drop it. All four content forms have hardness and blast resistance 2 and use the pickaxe tool gate. [Recipe][recipe] · [Block properties][blocks] · [Pickaxe tags][pickaxe-tag] [cauldron-tag] · [Tool rules][tool] · [Harvest gate][harvest]

## Usage

A filled Water, Lava, or Powder Snow Bucket can set the cauldron's contents; these interactions can overwrite existing contents. An empty Bucket collects full Water/Powder Snow or Lava. Water also supports one-layer bottle transfers and washing specific dyed equipment, banner patterns, and colored Shulker Boxes. See the exact [bucket/bottle transactions](../blocks/Cauldrons.md#bucket-and-bottle-transactions) and [cleaning rules](../blocks/Cauldrons.md#washing-equipment-banners-and-shulker-boxes). [Active interactions][interactions]

Cauldrons have no item-storage inventory. Their comparator reading is **0–3**, according to contents and level. Rain, snow, and a valid Pointed Dripstone setup can fill them through their separate checked conditions. [Shared behavior][base] · [Precipitation][empty] · [Layered levels][layered] · [Lava output][lava] · [Dripstone transfer][drip-random]

## Behavior

Breaking any content form with the correct tool returns **one empty Cauldron item**. Silk Touch does not preserve its contents or fill level, and Fortune does not increase the drop. Collect the liquid or Powder Snow before moving it. [Empty loot][loot-empty] · [Water loot][loot-water] · [Lava loot][loot-lava] · [Powder Snow loot][loot-snow]

Filled Water/Powder Snow can extinguish burning entities at the cost of a layer; remaining Powder Snow becomes Water. Lava burns and damages susceptible entities. See [entity-contact behavior](../blocks/Cauldrons.md#entity-contact-fire-and-freezing). [Layered contact][layered] · [Lava contact][lava]

## Related pages

- [Bucket](Bucket.md), [Water Bucket](WaterBucket.md), [Lava Bucket](LavaBucket.md)
- [Powder Snow Bucket](PowderSnowBucket.md)
- [Cauldrons](../blocks/Cauldrons.md)
- [Redstone Comparator](../blocks/RedstoneComparator.md)

## Sources and verification

Reviewed at `cf8cd5371bd1de61411ae5e7e144aabe3edb1e54` on 2026-10-02. Source and bundled-data review only; no in-game filling, cleaning, weather, dripstone, entity-contact, or redstone test.

[items]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/Items.java#L1791
[item-map]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/Items.java#L2764-L2771
[empty]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/CauldronBlock.java
[recipe]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/recipe/crafting/cauldron.json
[blocks]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/Blocks.java#L2528-L2543
[pickaxe-tag]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[cauldron-tag]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/block/cauldrons.json
[tool]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/ToolMaterial.java
[harvest]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[interactions]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/core/cauldron/CauldronInteraction.java
[base]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/AbstractCauldronBlock.java
[layered]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/LayeredCauldronBlock.java
[lava]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/LavaCauldronBlock.java
[drip-random]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/PointedDripstoneBlock.java#L182-L236
[loot-empty]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/blocks/cauldron.json
[loot-water]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/blocks/water_cauldron.json
[loot-lava]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/blocks/lava_cauldron.json
[loot-snow]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/blocks/powder_snow_cauldron.json
