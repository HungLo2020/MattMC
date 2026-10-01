# Fishing Rod

The **Fishing Rod** (`minecraft:fishing_rod`) casts a bobber to catch fish, junk, or treasure from water, and can hook and pull entities. It has **64 durability** and an enchantability value of 1. The [Fishing guide](../mechanics/Fishing.md) explains bite timing, open-water treasure conditions, loot, and precise retrieval costs.

## Crafting and obtaining

Craft one rod from **three [Sticks](Stick.md)** and **two [Strings](String.md)** at a Crafting Table:

```text
. . Stick
. Stick String
Stick . String
```

The shaped recipe can also be mirrored. Fishing loot itself can supply a damaged rod through the junk table or an enchanted rod through eligible treasure loot. These are confirmed routes, not an exhaustive survey of every trade or other source. [Crafting recipe][recipe] · [Pattern mirroring][shaped] · [Junk loot][junk] · [Treasure loot][treasure]

## Using and maintaining

Use the rod to cast, then use it again during the bobber's bite to reel in loot. Keep a rod in either hand and stay within 32 blocks of the bobber. Reeling in an entity pulls that entity instead of rolling ordinary fishing loot. [Rod use][rod] · [Bobber checks and retrieval][hook]

A successful fishing-loot retrieval normally costs **1 durability**; entity hooks and ground retrieval have different costs. MattMC retains a fully damaged rod, but normal use refuses broken stacks. Two rods can be combined through the supported repair systems; review their enchantment/data retention before sacrificing a useful rod. [Durability and repair](../mechanics/Durability.md) · [Fishing wear table](../mechanics/Fishing.md#durability-and-repairs)

**Lure** reduces the initial waiting counter, while **Luck of the Sea** changes loot weights. Treasure still requires open water. Their effects are checked on the rod at casting, so equipping an enchanted rod later is not evidence that an already-cast bobber has gained its bonuses. [Casting effects][rod] · [Fishing enchantments](../mechanics/Fishing.md#waiting-weather-and-enchantments)

## Sources and verification

Source-reviewed on 2026-10-01 at `4285adff2e35307c277a3a5bf54ebd064aa5e64b`; no gameplay test. [Item registration][item] and the source paths below establish these defaults.

Related: [Fishing](../mechanics/Fishing.md) · [Carrot on a Stick](CarrotOnAStick.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/Items.java#L1663
[recipe]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/fishing_rod.json
[shaped]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/crafting/ShapedRecipePattern.java
[junk]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/gameplay/fishing/junk.json
[treasure]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/gameplay/fishing/treasure.json
[rod]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/FishingRodItem.java
[hook]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/projectile/FishingHook.java
