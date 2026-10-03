# Cosmaw

The **Cosmaw** is a flying predator of [Cosmic Cod](CosmicCod.md), with **20 health points (10 hearts)**. It retaliates when attacked but does not install a general player-hunting goal. Its companion behavior is present, but the bundled food definitions currently prevent the documented feeding and taming loop from being a usable ordinary route. [Targets and goals][goals] · [Health][stats] · [Attribute wiring][attributes]

## Obtaining

The [Cosmaw Spawn Egg](../items/CosmawSpawnEgg.md) is an ordinary category-listed item, available through the [inventory item browser](../mechanics/InventoryBrowser.md) in **Creative**. This route does not establish natural spawning. [Egg registration][egg] · [Category entry][category]

No Cosmaw entry was found in the checked loaded biome spawn lists, structure spawn overrides, or other active natural-spawning references. End biome resources and their builder do not add it. The separate method checking for a non-air block underneath is not registered as a spawn-placement predicate in this snapshot; it is not evidence of an End habitat. [Spawn-list selection][spawn-selection] · [Bundled End list][end-spawns] · [End builder][end-builder] · [Standalone spawn methods][spawn-checks]

## Behavior

### Flight and hunting

Cosmaws ignore gravity and use a flying movement controller. Their wandering goal picks destinations over ground or empty space. They actively target Cosmic Cod, and retaliate against attackers other than their owner. The owner is also excluded by the inherited attack-permission check. No owner-defense or owner-target-assistance goal is installed. [Flight setup][flight] · [No gravity][gravity] · [Wandering][wander] · [Targets][goals] · [Owner protection][owner-protection]

The installed attack goal calls the Cosmaw's own bite method, which starts an animation counter. The tick then applies its **1-point attack attribute** when its target is within 3.3 blocks. The damage helper dispatches to the server damage callback. This does not establish a fixed damage total per bite, because the counter can request damage on several ticks and the target's damage rules still apply. [Attack goal][attack-goal] · [Bite setup][bite] · [Damage tick][damage] · [Damage dispatch][damage-dispatch] · [Attribute value][stats]

### Feeding and taming: missing item dependency

**No usable bundled taming or breeding food was verified.** Both `cosmaw_tameables` and `cosmaw_breedables` require an item named `minecraft:cosmic_cod`. MattMC registers a Cosmic Cod entity, spawn egg and bucket, but no item with that exact ID. The food tag depends on those two required tags. The tag loader rejects a tag with unresolved required entries, so neither the live fish nor its bucket supplies this missing food item. [Taming tag][taming-tag] · [Breeding tag][breeding-tag] · [Food tag][food-tag] · [Registered bucket and egg][cod-items] · [Required-entry resolution][tag-entry] · [Tag-loader failure][tag-loader]

If valid food tags are supplied by a different build or a data pack, the active code can take one matching item directly from a player's hand or pick one up from the ground. After holding matching food for more than **30 ticks**, it heals **4 health points (2 hearts)** and consumes one item. For a wild Cosmaw with a recorded player feeder, a tameable item has a **30% chance** to set that player as owner and select follow mode. These are conditional code paths, not working food instructions for the checked bundle. [Hand interaction][interact] · [Dropped food][pickup] · [Eating and taming][eat]

Breeding similarly requires an already tamed Cosmaw and a matching breeding-tag item. A Breed goal and a child factory exist, but the missing food dependency prevents presenting an ordinary breeding recipe. [Breeding-food check][food-check] · [Goals][goals] · [Child factory][offspring]

### Owner commands and attempted rescue

For a Cosmaw that already has an owner, an ordinary empty-handed interaction with an adult cycles commands **0 → 1 → 2 → 0**: wander, follow and sit. Follow mode starts at 8 blocks or more from the owner and can attempt to teleport closer from 12 blocks away, subject to its movement and destination checks. This behavior is conditional on having an owned Cosmaw. [Command cycling][interact] · [Command meaning][commands] · [Following][follow]

The installed pickup goal watches an airborne owner with more than **4 blocks of accumulated fall distance**, provided the Cosmaw is tamed, not sitting and the owner is not already a passenger. It approaches or teleports toward the owner, then attempts to carry them; fall-flying owners are normally ignored unless below Y=-30. While carrying someone, it moves toward a remembered ground position and may put the passenger down nearby. This is an automated rescue attempt, not a steerable mount or guaranteed protection from the void. [Pickup trigger and action][rescue] · [Return movement][return] · [No controlling passenger][controller]

Sit commands also have a compatibility limit: this class writes its own sitting flag, while the inherited sit goal and follow helper read the base animal's separate ordered-to-sit flag. Wandering and rescue check the custom flag, but this does not establish that every active goal stops on command. [Custom sit setter][sit-custom] · [Base flag][sit-base] · [Sit goal][sit-goal] · [Follow checks][follow]

## Drops

No bundled `entities/cosmaw` loot table or species-specific resource drop was found. The normal default table key resolves to an empty table when absent; there is no verified Cosmaw resource-farming route here. [Default loot key][loot-key] · [Missing-table fallback][loot-fallback]

## Notes

- Registered as `minecraft:cosmaw`, category `CREATURE`, with a **1.5 × 1.5-block** registered size and the actual Cosmaw factory. [Entity registration][entity]
- Source-reviewed at `79f20bccc697135bd56a472c64f59981dce47fe0` on 2026-10-02. Absence findings concern the checked bundle. No game, feeding, taming, breeding, command, combat, rescue or tag-reload test was run.

Related: [Cosmic Cod](CosmicCod.md) · [Cosmaw Spawn Egg](../items/CosmawSpawnEgg.md) · [Spectre](Spectre.md) · [Mobs](Mobs.md)

[spawn-selection]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450
[end-spawns]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/biome/end_highlands.json#L35-L51
[end-builder]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/data/worldgen/biome/EndBiomes.java#L15-L35
[loot-key]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-fallback]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[goals]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmaw.java#L107-L138
[stats]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmaw.java#L74-L76
[attributes]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L144
[egg]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/Items.java#L1843
[category]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1999
[spawn-checks]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmaw.java#L78-L84
[flight]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmaw.java#L69-L72
[gravity]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmaw.java#L156-L158
[wander]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmaw.java#L473-L527
[owner-protection]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/TamableAnimal.java#L166-L173
[attack-goal]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmaw.java#L564-L581
[bite]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmaw.java#L100-L105
[damage]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmaw.java#L248-L261
[damage-dispatch]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/Entity.java#L1772-L1777
[taming-tag]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/tags/item/cosmaw_tameables.json#L1-L6
[breeding-tag]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/tags/item/cosmaw_breedables.json#L1-L6
[food-tag]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/tags/item/cosmaw_foodstuffs.json#L1-L8
[cod-items]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/Items.java#L1831-L1836
[tag-entry]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/tags/TagEntry.java#L62-L79
[tag-loader]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/tags/TagLoader.java#L94-L135
[interact]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmaw.java#L334-L363
[pickup]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmaw.java#L405-L424
[eat]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmaw.java#L262-L287
[food-check]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmaw.java#L152-L154
[offspring]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmaw.java#L384-L392
[commands]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmaw.java#L436-L457
[follow]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/ai/FlyingAIFollowOwner.java#L47-L112
[rescue]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmaw.java#L532-L562
[return]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmaw.java#L288-L329
[controller]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmaw.java#L164-L167
[sit-custom]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmaw.java#L190-L196
[sit-base]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/TamableAnimal.java#L224-L230
[sit-goal]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/ai/goal/SitWhenOrderedToGoal.java#L15-L43
[entity]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/EntityType.java#L408-L414
