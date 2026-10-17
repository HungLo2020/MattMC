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

- Raccoons accept dropped items with a food component **or** membership in `minecraft:raccoon_foodstuffs`; the latter needs an added data pack in this snapshot. They beg when a nearby player holds suitable food. They collect **one dropped item at a time**, and only while their hand is empty. Eating restores up to **10 health points**, but raccoons also collect and eat food at full health. Offer only food you intend them to consume. [Food and eating][food] · [Item pickup][pickup] · [Pickup limits][pickup-goal] · [Begging][begging]
- A raccoon carrying food looks for water adjoining dry space. It washes and eats the item if it can reach a suitable edge; it can also eat without washing when its water-search timer expires. **Water is not required for ordinary feeding**, and keeping food away from water will not preserve it in the raccoon's hand. Washing consumes the food rather than turning it into a new item. [Washing][washing] · [Eating fallback][eating-fallback]
- **Keep raccoon enclosures separate from trading booths**, with enough space to prevent an approach within **1.7 blocks** of a trader. Theft does not require a clear line of sight, so a thin divider alone can leave the trader in reach. Taming does not remove the theft goal. [Target checks][theft-targets] · [Villager theft][theft]
- A successful theft supplies the raccoon with **one item** from a trade and spends one use of that offer, potentially putting it out of stock. It takes the trade result, or the first payment item when the result is an Emerald. Nearby Iron Golems can deter theft, but the checks during an approach are intermittent; do not rely on a golem alone to protect a trading hall. Chest looting and turtle-egg breaking are removed from its goal list. [Theft and golem checks][theft] · [Trade uses][trade-uses] · [Goals][goals]
- Adults have retaliation goals, and owned raccoons have owner-defense goals. Treat them as capable of fighting back rather than harmless decorations. [Goals][goals]

## Taming, breeding, and owner controls

The item tags `minecraft:raccoon_tameables`, `minecraft:raccoon_breedables`, `minecraft:raccoon_foodstuffs`, `minecraft:raccoon_teaming_foods`, and `minecraft:raccoon_dissolves` have **no definitions in the bundled item-tag data**. Consequently, this snapshot provides no supported food-based taming, breeding, blue-jay bonding, or dissolving-item recipe. The general edible-food check still works independently of these tags. [Tags][tags] · [Bundled item tags][item-tags] · [Food check][food]

If an added data pack supplies suitable foods, the taming branch requires the raccoon to pick up a taming item dropped by a player and **finish washing it**. It then rolls a **30% chance** to become tame. The item must also pass the general food check so the raccoon can collect and wash it. Stay online while it finishes, because ownership is assigned to the remembered player only if that player can be found. Breeding uses its separate breeding tag and creates a raccoon offspring. These are conditional code paths, not a verified bundled Survival route. [Pickup and thrower][pickup] · [Post-wash taming][taming] · [Breeding][breeding]

For an **already-tamed raccoon**, retrieve anything it holds before feeding or changing its controls: interact with an empty hand, then collect the dropped item. **Any player can retrieve it**, not just its owner. The raccoon pauses food pickup for **60 game ticks** afterward, so collect it promptly. This retrieval takes priority over carpet, shears, ordinary hand-feeding, and command controls. [Held-item retrieval][retrieval] · [Pickup cooldown][pickup-cooldown] If it is leashed to you, interact **without sneaking** to release the lead first, then interact again to retrieve its item. The shared lead interaction happens before the Raccoon-specific controls. [Shared interaction order][shared-interaction] · [Lead release][lead-release]

For one-item care, drop **one edible item** once its hand is empty and let it collect and eat it; wait for the pickup pause to end if you just retrieved something. Directly hand-feeding a wounded tame raccoon with ordinary food consumes **two items for one heal when the Survival stack contains at least two** in this source snapshot; a single remaining item is consumed once. That hand-feeding behavior was source-reviewed, not tested in-game; the dropped-food route takes one item. [Dropped-food transfer][pickup-goal] · [Healing interaction][hand-feeding] · [Shared consumption][use-item] · [Stack consumption][stack-consumption]

For a raccoon that is already tamed and owned by you, once its hand is empty:

- Interact with an empty hand, without sneaking, to cycle **wander → follow → sit**
- Use a wool carpet to give it a colored bandana; a different carpet replaces and returns the old one
- Use shears to remove and recover the carpet

[Owner controls][controls] · [Follow condition][breeding]

## Drops and verification

No dedicated raccoon death-loot table was found in the bundled entity loot data. A worn carpet is explicitly returned by its equipment-drop code, but [Raccoon Tail](../items/RaccoonTail.md) has no verified raccoon-drop route here. [Loot data][loot] · [Carpet drop][carpet-drop]

Source-reviewed on **2026-10-10** against MattMC commit `1b9b103398fd70d5b5152b93a1d0abc581fffc19`. Feeding, held-item retrieval, taming, washing, combat, theft, and spawning were not tested in-game. Added data packs can change the missing-tag and loot limitations described above.

## Related pages

- [Mobs](Mobs.md)
- [Blue Jay](BlueJay.md)
- [Capuchin Monkey](CapuchinMonkey.md)
- [Raccoon Tail](../items/RaccoonTail.md)

[attributes]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/alexsmobs/entity/EntityRaccoon.java#L102-L107
[attribute-wiring]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L218
[registration]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/entity/EntityType.java#L1094-L1099
[egg]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/item/Items.java#L1936
[tags]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/alexsmobs/misc/AMTagRegistry.java#L188-L193
[item-tags]: https://github.com/HungLo2020/MattMC/tree/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/resources/data/minecraft/tags/item
[biomes]: https://github.com/HungLo2020/MattMC/tree/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/resources/data/minecraft/worldgen/biome
[biome-code]: https://github.com/HungLo2020/MattMC/tree/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/level/biome
[placements]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[food]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/alexsmobs/entity/EntityRaccoon.java#L311-L447
[pickup]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/alexsmobs/entity/EntityRaccoon.java#L583-L607
[begging]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/alexsmobs/entity/ai/RaccoonAIBeg.java#L25-L70
[washing]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/alexsmobs/entity/ai/RaccoonAIWash.java#L30-L143
[theft]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/alexsmobs/entity/EntityRaccoon.java#L696-L814
[goals]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/alexsmobs/entity/EntityRaccoon.java#L126-L148
[taming]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/alexsmobs/entity/EntityRaccoon.java#L449-L465
[breeding]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/alexsmobs/entity/EntityRaccoon.java#L562-L580
[controls]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/alexsmobs/entity/EntityRaccoon.java#L188-L271
[loot]: https://github.com/HungLo2020/MattMC/tree/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/resources/data/minecraft/loot_table/entities
[carpet-drop]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/alexsmobs/entity/EntityRaccoon.java#L160-L186
[pickup-goal]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/alexsmobs/entity/ai/CreatureAITargetItems.java#L70-L145
[eating-fallback]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/alexsmobs/entity/EntityRaccoon.java#L399-L409
[theft-targets]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/alexsmobs/entity/EntityRaccoon.java#L90-L91
[trade-uses]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/item/trading/MerchantOffer.java#L123-L168
[retrieval]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/alexsmobs/entity/EntityRaccoon.java#L192-L207
[pickup-cooldown]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/alexsmobs/entity/EntityRaccoon.java#L433-L438
[hand-feeding]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/alexsmobs/entity/EntityRaccoon.java#L228-L248
[use-item]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/entity/Mob.java#L1113-L1120
[stack-consumption]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/item/ItemStack.java#L1072-L1079

[shared-interaction]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/entity/Mob.java#L1054-L1073
[lead-release]: https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/world/entity/Entity.java#L2146-L2159
