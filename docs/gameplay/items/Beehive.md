# Beehive

A **Beehive** is craftable housing for up to **three [Bees](../mobs/Bee.md)**. Its honey-production and harvesting behavior matches a Bee Nest. Use the [Bee housing guide](../blocks/BeeHousing.md) for entrance placement, smoke, harvesting, and moving a colony. [Shared blocks][blocks] · [Capacity][capacity]

## Crafting and obtaining

Craft **one Beehive** in a [Crafting Table](../blocks/CraftingTable.md) with:

- Three planks across the top row
- Three [Honeycomb](Honeycomb.md) across the middle row
- Three planks across the bottom row

The recipe accepts the planks tag, so the six planks do not have to be one wood species. The resulting item is `minecraft:beehive`, and it is also available in Creative. [Recipe][recipe] · [Registration][registration] · [Creative entry][creative]

A fresh crafted hive has **no Bees and honey level 0**. You need an existing colony to occupy it; crafting and placing the block do not spawn Bees. Provide flowers and available housing near the colony. [Item defaults][registration] · [Bee home search][search]

## Placement and recovery

Place the hive with its front face clear so Bees can exit. A normal mine drops an ordinary Beehive item but does not preserve stored Bees or honey. Mining without Silk Touch can also release occupants and anger nearby Bees. [Exit check][exit] · [Loot branches][loot] · [Mining response][mining]

Use **Silk Touch** to move an occupied hive. Its Silk Touch loot branch copies the stored Bees and honey level into the dropped item, and placement restores those components. Wait until the Bees you intend to move are inside; flying Bees are not automatically included. [Silk Touch loot][loot] · [Placement restoration][placement] · [Bee components][components]

At honey level 5, collect Honeycomb with Shears or a Honey Bottle with a Glass Bottle. Follow [the harvesting instructions](../blocks/BeeHousing.md#harvesting) for smoke and tool behavior before collecting. [Harvest handler][harvest]

## Related pages

- [Bee housing](../blocks/BeeHousing.md)
- [Bee Nest](BeeNest.md)
- [Bee](../mobs/Bee.md)
- [Honeycomb](Honeycomb.md)
- [Items](Items.md)

## Verification scope

Source-reviewed at MattMC commit `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. Registration, recipe, default item data, capacity, placement, harvesting, and mining loot were inspected. No in-game crafting, colony, or Silk Touch test was run.

[blocks]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5748-L5757
[capacity]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/BeehiveBlockEntity.java#L115-L121
[recipe]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/beehive.json
[registration]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L2469-L2474
[creative]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1098-L1099
[search]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Bee.java#L1027-L1063
[exit]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/BeehiveBlockEntity.java#L198-L206
[loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/beehive.json
[mining]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/BeehiveBlock.java#L89-L127
[placement]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/BlockItem.java#L64-L102
[components]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/BeehiveBlockEntity.java#L302-L314
[harvest]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/BeehiveBlock.java#L143-L189
