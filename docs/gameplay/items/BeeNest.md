# Bee Nest

A **Bee Nest** is the tree-generated form of [Bee housing](../blocks/BeeHousing.md). It holds up to **three Bees** and uses the same honey-production and harvesting behavior as a crafted [Beehive](Beehive.md). Its important difference is collection: **the ordinary block-loot table drops a Bee Nest only with Silk Touch**. [Shared blocks][blocks] · [Capacity][capacity] · [Nest loot][loot]

## Finding and collecting

Bee-bearing tree configurations can place a nest when they find room beside the trunk and in front of the nest. The active decorator stores **two or three Bees** in a newly generated nest. See [Finding Bees](../mobs/Bee.md#finding-bees) for verified Cherry Grove and Meadow routes and flower-assisted Oak, Birch, and Cherry sapling growth. [Nest generation][generation]

Mine the nest with **Silk Touch** to obtain the item with its stored Bees and honey level. Without Silk Touch, the nest's block-loot table provides no item, and the mining callback can release occupants and anger nearby Bees. Campfire smoke used for harvesting does not replace Silk Touch for collecting the block. [Nest loot][loot] · [Mining response][mining] · [Mining-protection enchantment tag][silk]

The item is registered as `minecraft:bee_nest` and available in Creative. Its ordinary item defaults have **no Bees and honey level 0**; a Creative inventory item is not the same as an occupied naturally generated nest. [Registration][registration] · [Creative entry][creative]

## Moving and using the nest

Wait until the Bees you intend to move are inside before mining with Silk Touch. The copied item carries stored occupants, not nearby flying Bees. On placement, the game reapplies the saved Bee and honey-level components. Leave the new entrance clear and provide safe flowers nearby. [Copied data][loot] · [Placement restoration][placement] · [Bee components][components]

At honey level 5, use Shears for three Honeycomb or a Glass Bottle for one Honey Bottle. This resets the honey level. Follow [Bee housing](../blocks/BeeHousing.md#harvesting) for manual smoke checks, dispenser collection, and the source-identified broken-Shears caveat. [Harvest interaction][harvest] · [Honeycomb count][harvest-loot]

## Related pages

- [Bee housing](../blocks/BeeHousing.md)
- [Beehive](Beehive.md)
- [Bee](../mobs/Bee.md)
- [Honeycomb](Honeycomb.md)
- [Honey Bottle](HoneyBottle.md)
- [Items](Items.md)

## Verification scope

Source-reviewed at MattMC commit `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. Registration, occupied-nest generation, capacity, ordinary and Silk Touch loot, placement restoration, and harvesting were inspected. No in-game generation, mining, relocation, or harvesting test was run.

[blocks]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5748-L5757
[capacity]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/BeehiveBlockEntity.java#L115-L121
[loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/bee_nest.json
[generation]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/levelgen/feature/treedecorators/BeehiveDecorator.java#L39-L67
[mining]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/BeehiveBlock.java#L89-L127
[silk]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/enchantment/prevents_bee_spawns_when_mining.json
[registration]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L2463-L2468
[creative]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1098-L1099
[placement]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/BlockItem.java#L64-L102
[components]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/BeehiveBlockEntity.java#L302-L314
[harvest]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/BeehiveBlock.java#L143-L189
[harvest-loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/harvest/beehive.json
