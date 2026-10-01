# Raccoon

The **Raccoon** picks up food, washes it beside water, and can steal from villagers. It has taming and owner-control code, but **the bundled data does not define any raccoon taming or breeding foods**. Eating an ordinary food item is not enough to tame one. [Food and taming checks][food] · [Tag definitions][tags] · [Bundled item tags][item-tags]

## At a glance

- **Health:** 10 points (5 hearts)
- **Base attack damage:** 2 points (1 heart), before combat modifiers
- **Adult size:** 0.75 blocks wide and 0.5 blocks tall
- **Entity ID:** `minecraft:raccoon`

[Attributes][attributes] · [Attribute wiring][attribute-wiring] · [Entity registration][registration]

## Obtaining

Use the [Raccoon Spawn Egg](../items/RaccoonSpawnEgg.md) in Creative or `/summon minecraft:raccoon` with command permission. **Natural spawning is not established in this snapshot:** no raccoon entry was found in the bundled biome spawn tables, biome-building code, or spawn-placement registrations. Its creature-category registration and permissive spawn check do not establish a place to find wild raccoons. [Spawn egg][egg] · [Biome data][biomes] · [Biome-building code][biome-code] · [Spawn placements][placements]

## Food, washing, and village behavior

- Raccoons accept dropped items with a food component **or** membership in `minecraft:raccoon_foodstuffs`; the latter needs an added data pack in this snapshot. They beg when a nearby player holds suitable food and take one dropped item at a time. Eating restores up to **10 health points**. [Food and eating][food] · [Item pickup][pickup] · [Begging][begging]
- A raccoon carrying food looks for water adjoining dry space. It washes and eats the item if it can reach a suitable edge; it can also eat without washing when its water-search timer expires. Washing does not turn food into a new item. [Washing][washing] · [Eating fallback][food]
- Keep them away from an important trading area. The stealing goal can take **one item** associated with a villager's trade and increase that offer's use count. It avoids starting a theft when a nearby iron golem is detected. Chest looting and turtle-egg breaking are explicitly removed from its goal list. [Villager theft][theft] · [Goals][goals]
- Adults have retaliation goals, and owned raccoons have owner-defense goals. Treat them as capable of fighting back rather than harmless decorations. [Goals][goals]

## Taming, breeding, and owner controls

The item tags `minecraft:raccoon_tameables`, `minecraft:raccoon_breedables`, `minecraft:raccoon_foodstuffs`, `minecraft:raccoon_teaming_foods`, and `minecraft:raccoon_dissolves` have **no definitions in the bundled item-tag data**. Consequently, this snapshot provides no supported food-based taming, breeding, blue-jay bonding, or dissolving-item recipe. The general edible-food check still works independently of these tags. [Tags][tags] · [Bundled item tags][item-tags] · [Food check][food]

If an added data pack supplies suitable foods, the taming branch requires the raccoon to pick up a taming item dropped by a player and **finish washing it**. It then rolls a **30% chance** to become tame. The item must also pass the general food check so the raccoon can collect and wash it. Stay online while it finishes, because ownership is assigned to the remembered player only if that player can be found. Breeding uses its separate breeding tag and creates a raccoon offspring. These are conditional code paths, not a verified bundled Survival route. [Pickup and thrower][pickup] · [Post-wash taming][taming] · [Breeding][breeding]

For a raccoon that is already tamed and owned by you:

- Interact with an empty hand, without sneaking, to cycle **wander → follow → sit**
- If it is holding an item, interacting first makes it drop that item and briefly pause item pickup
- Use a wool carpet to give it a colored bandana; a different carpet replaces and returns the old one
- Use shears to remove and recover the carpet

The held-item interaction takes priority over carpet and command controls. [Interactions][controls] · [Follow condition][breeding]

## Drops and verification

No dedicated raccoon death-loot table was found in the bundled entity loot data. A worn carpet is explicitly returned by its equipment-drop code, but [Raccoon Tail](../items/RaccoonTail.md) has no verified raccoon-drop route here. [Loot data][loot] · [Carpet drop][carpet-drop]

Source-reviewed on **2026-10-01** against MattMC commit `b81c01943c9f3254e713c365a1dd633392929cb2`. Taming, washing, combat, theft, and spawning were not tested in-game. Added data packs can change the missing-tag and loot limitations described above.

## Related pages

- [Mobs](Mobs.md)
- [Blue Jay](BlueJay.md)
- [Capuchin Monkey](CapuchinMonkey.md)
- [Raccoon Tail](../items/RaccoonTail.md)

[attributes]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityRaccoon.java#L102-L107
[attribute-wiring]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L218
[registration]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/EntityType.java#L1094-L1099
[egg]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L1936
[tags]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/misc/AMTagRegistry.java#L188-L193
[item-tags]: https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item
[biomes]: https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome
[biome-code]: https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/biome
[placements]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[food]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityRaccoon.java#L311-L447
[pickup]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityRaccoon.java#L583-L607
[begging]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/ai/RaccoonAIBeg.java#L25-L70
[washing]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/ai/RaccoonAIWash.java#L30-L143
[theft]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityRaccoon.java#L696-L814
[goals]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityRaccoon.java#L126-L148
[taming]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityRaccoon.java#L449-L465
[breeding]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityRaccoon.java#L562-L580
[controls]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityRaccoon.java#L188-L271
[loot]: https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities
[carpet-drop]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityRaccoon.java#L160-L186
