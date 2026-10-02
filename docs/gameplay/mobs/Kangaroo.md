# Kangaroo

The **Kangaroo** is a tameable, neutral animal from bundled Alex's Mobs content. Carrots tame it, while Dead Bushes and Short Grass are its breeding foods. Adults have a nine-slot pouch, saved inventory, and owner commands. [Food interactions][interactions] · [Breeding tag][breedables] · [Save/load handling][saving]

## At a glance

- **Health:** 22 points (11 hearts)
- **Base attack damage:** 4 points (2 hearts), before equipment and combat modifiers
- **Adult size:** 0.8 blocks wide × 1.8 blocks tall
- **Base movement-speed attribute:** 0.5, not a blocks-per-second measurement
- **Entity ID:** `minecraft:kangaroo`

The listed attributes are wired into the active attribute registry. [Attributes][attributes] · [Active registration][active-attributes] · [Entity size][registration]

## Obtaining

Use the [Kangaroo Spawn Egg](../items/KangarooSpawnEgg.md) in Creative, or `/summon minecraft:kangaroo` with command permission. The egg is registered and included in the Creative spawn-egg tab. [Egg][egg] · [Creative entry][creative]

**Natural spawning is not established in this snapshot.** A helper checks for brightness above 8 over Grass Block, Sand, or Red Sand, but no caller wiring it into spawn placements was found, and no Kangaroo entry was found in the bundled biome spawn tables. Those blocks are not a confirmed recipe for finding Kangaroos in Survival. [Spawn helper][spawn-helper] · [Ground tag][spawn-blocks] · [Spawn placements][placements] · [Biome data][biomes]

## Taming and owner commands

Feed a wild Kangaroo **[Carrots](../items/Carrot.md)** by interacting with it. The actual bundled tameables tag contains only Carrot. Its feeding counter gives no success chance for the first ten feedings, a **50% chance on each of feedings 11–15**, and a guaranteed success on feeding 16 if it remains untamed. Keep enough carrots for the full attempt; the counter is not included in its save data, so partial progress is not preserved when the entity is saved and reloaded. [Taming food][tameables] · [Taming sequence][interactions] · [Saving][saving]

Once it is yours, an **empty-hand interaction** cycles **wander → follow → sit → wander**. It begins in wander mode, so the first command changes it to follow. The follow goal is active only for the follow command and can give way to nearby combat. Sneak-interacting with an owned adult instead opens its pouch. Using an empty hand avoids food and held-item interactions taking priority. [Controls][interactions] · [Default command][defaults] · [Follow condition][follow] · [Follow goal][follow-goal]

Kangaroos are **not player-controlled mounts**: this class returns no controlling passenger and provides no player-mount interaction. A joey's ability to ride another Kangaroo is a separate behavior. [Passenger control][passenger]

## Pouch, equipment, and healing

Sneak-interact with your adult Kangaroo to open its **nine-slot pouch**; no chest is needed. Opening it ejects any passenger, including a joey. The menu uses a three-by-three inventory layout. [Pouch interaction][interactions] · [Inventory][inventory] · [Menu][menu]

The pouch saves each occupied slot, including stack counts, durability, names, enchantments, and other item components. Empty slots keep their positions. This addresses the missing pouch data reported in [issue #780](https://github.com/HungLo2020/MattMC/issues/780). Older saves without pouch data load an empty pouch; items already lost by an older version cannot be recovered by this change. Invalid slot records are reported and skipped rather than placed in another slot. [Save/load handling][saving] · [Automated regression coverage][persistence-tests]

Save/new-entity/load round trips are covered by automated tests. **Real chunk unload/reload and server stop/restart have not yet been checked in-game**, so back up your world before relying on valuable stored items. See [verification scope](#verification-scope).

After loading all pouch contents, the Kangaroo selects its held weapon, helmet, and chest equipment again. Weapon selection compares attack-damage modifiers; helmet and chest selection check the relevant equipment slot and armor values. However, the custom inventory-sync sender is disabled in this integration, and equipment display and full combat effects have not been tested. Avoid handing over valuable gear until storage and equipment behavior are verified in your world. [Equipment selection][equipment] · [Disabled synchronization][sync]

An injured, tamed Kangaroo accepts food directly for healing. Hand-feeding heals the food's nutrition value in health points. It can also **eat food stored in the pouch automatically**, healing twice that value per item, with a 20–59 tick cooldown between checks. For example, a Carrot heals **3 points by hand** or **6 points from the pouch**. Stored food is a consumable supply, not protected cargo. [Hand healing][interactions] · [Pouch feeding][healing] · [Carrot nutrition][carrot]

A stationary Kangaroo can also graze on a Grass Block beneath it. When injured, completing the grazing animation heals **6 points** and changes that block to Dirt. Include grass in an enclosure if you want this source-defined healing option, and expect bare patches. [Grazing][grazing]

## Breeding and joeys

Use **[Dead Bush](../items/DeadBush.md)** or **[Short Grass](../items/ShortGrass.md)** to breed ready adults. These are the two entries in the bundled breeding-food tag; Carrot belongs to the taming tag instead. The breeding check does not require the adults to be tamed. [Breeding foods][breedables] · [Food check][food] · [Breeding goal][goals]

Breeding creates another Kangaroo. The offspring method does not assign an owner, so do not assume a tame parent's baby is already yours. Babies can approach and ride an available adult Kangaroo, and dismount when they grow up. Opening the adult's pouch also makes its passenger get out. [Offspring][offspring] · [Ride-parent goal][joeys] · [Growing up][growing] · [Pouch interaction][interactions]

## Safety and drops

Adults can retaliate and defend their owner. Their combat AI can seek water when fighting the creature that hurt them; at close range it pushes an attacker in water downward and reduces that attacker's air supply. Do not follow an angry Kangaroo into a pond. Babies clear their combat target. [Targeting goals][goals] · [Water combat][water-combat] · [Baby targeting][growing]

No dedicated Kangaroo death-loot table was found in the bundled entity loot data. [Kangaroo Hide](../items/KangarooHide.md) and [Raw Kangaroo Meat](../items/RawKangarooMeat.md) are registered items, but registration does not establish them as drops. The pouch-drop path drops each stored stack once, including selected equipment, with its original durability and components. Pouch contents retain their existing guaranteed-drop behavior even with mob loot disabled or a Curse of Vanishing effect; they are not subjected to a second equipment-drop lottery. [Loot data][loot] · [Item registration][items] · [Pouch drops][drops]

## Related pages

- [Kangaroo Spawn Egg](../items/KangarooSpawnEgg.md)
- [Carrot](../items/Carrot.md)
- [Elephant](Elephant.md)
- [Mobs](Mobs.md)

## Verification scope

General mob behavior was source-reviewed against MattMC commit `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. Pouch persistence and death-drop handling were updated and automatically tested on 2026-10-02. The regression uses real entity construction, item codecs, and save/new-entity/load operations with mocked world I/O. It covers all nine slots and components, sparse/empty/legacy inventories, invalid slot records, repeated loading and initialization, menu close/reopen, post-load equipment selection, and both death-drop stages with mob loot enabled and disabled. Item spawning is captured at the world boundary. [Regression tests][persistence-tests]

These tests are not an in-game chunk-unload, server-restart, taming, breeding, equipment-rendering, combat, or natural-spawning check. The persistence regression must remain covered during migration work tracked in [#770](https://github.com/HungLo2020/MattMC/issues/770) and [#774](https://github.com/HungLo2020/MattMC/issues/774).

[attributes]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityKangaroo.java#L112-L114
[active-attributes]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L188
[registration]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/EntityType.java#L1259-L1265
[egg]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L1898
[creative]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2038
[spawn-helper]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityKangaroo.java#L107-L110
[spawn-blocks]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/kangaroo_spawns.json
[placements]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[biomes]: https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome
[tameables]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/kangaroo_tameables.json
[interactions]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityKangaroo.java#L217-L274
[saving]: https://github.com/HungLo2020/MattMC/blob/fix/issue-780-kangaroo-pouch/src/main/java/net/alexsmobs/entity/EntityKangaroo.java
[defaults]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityKangaroo.java#L348-L360
[follow]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityKangaroo.java#L913-L916
[follow-goal]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/ai/TameableAIFollowOwner.java#L20-L34
[passenger]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityKangaroo.java#L116-L119
[inventory]: https://github.com/HungLo2020/MattMC/blob/fix/issue-780-kangaroo-pouch/src/main/java/net/alexsmobs/entity/EntityKangaroo.java
[menu]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityKangaroo.java#L300-L314
[equipment]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityKangaroo.java#L767-L818
[sync]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityKangaroo.java#L820-L827
[healing]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityKangaroo.java#L480-L501
[carrot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/food/Foods.java#L11
[grazing]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityKangaroo.java#L470-L479
[breedables]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/kangaroo_breedables.json
[food]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityKangaroo.java#L762-L765
[goals]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityKangaroo.java#L367-L382
[offspring]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityKangaroo.java#L740-L744
[joeys]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/ai/AnimalAIRideParent.java
[growing]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityKangaroo.java#L536-L555
[water-combat]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/ai/KangarooAIMelee.java#L29-L71
[loot]: https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities
[items]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L1756-L1759
[drops]: https://github.com/HungLo2020/MattMC/blob/fix/issue-780-kangaroo-pouch/src/main/java/net/alexsmobs/entity/EntityKangaroo.java

[persistence-tests]: https://github.com/HungLo2020/MattMC/blob/fix/issue-780-kangaroo-pouch/src/test/misc/net/alexsmobs/entity/EntityKangarooInventoryTest.java
