# Moose

**Moose** (`minecraft:moose`) are large animals with active retaliation, breeding and a renewable [Antler](../items/MooseAntler.md) shedding cycle. Their [Raw](../items/RawMooseRibs.md) and [Cooked Ribs](../items/CookedMooseRibs.md) are registered foods, but a bundled Moose death-loot or rib-cooking route is **not established**. [Goals][moose-goals] · [Antler cycle][antler-cycle] · [Rib registration][items] · [Bundled loot][loot-tables] · [Bundled recipes][recipes]

## Obtaining

The [Moose Spawn Egg](../items/MooseSpawnEgg.md) is listed in the ordinary Spawn Eggs category. MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) supplies ordinary listed items in **Survival and Creative**. Use the shared [Spawn eggs guide](../items/SpawnEggs.md) for placement and consumption. [Egg registration][egg] · [Category listing][egg-tab] · [Browser assembly][browser-list] · [Client request][browser-client] · [Server check][browser-server]

**A natural biome-spawn route is not established in this build.** The class contains a terrain/light predicate named `canMooseSpawn`, but the checked spawn-placement registry does not register it, and the bundled biome definitions do not list Moose. Its instance spawn check returning true is not a biome-spawn entry. The presence of snow-related code therefore does not establish a snowy habitat where Moose will generate. [Predicates][spawn-check] · [Spawn-placement registry][placement-registry] · [Bundled biomes][biomes]

## Behavior

The registered attributes give **55 health points** (27.5 hearts), **7.5 attack-damage points**, movement-speed attribute **0.25**, and knockback-resistance attribute **0.5**. These are base attributes, not measured movement rates or final damage against a particular target. [Values][moose-attributes] · [Attribute binding][attributes]

Adults retaliate when hurt. Calves do not accept a combat target through the Moose target setter, and the shared baby hurt-response can alert nearby adults. Unlike Bison, the reviewed Moose goal list has no nearby-player aggression goal. Keep [Wolves](Wolf.md) away when caring for Moose: the active incoming-damage handler amplifies a Wolf-sourced hit before passing it to the shared damage code. [Goals][moose-goals] · [Baby target guard][target-guard] · [Adult alerts][retaliation] · [Wolf-sourced damage][wolf-damage]

Antlered adults can **jostle with another eligible Moose**. The goal excludes babies, existing combat targets and animals still on cooldown; partner checks also require antlers. It handles approaches, sideways movement and mutual pushes. Jostling is separate from both breeding and timed Antler shedding, so the timer does not require a second Moose. [Partner eligibility][jostle-ready] · [Jostling goal][jostle] · [Independent shedding][antler-cycle]

The ordinary melee goal uses the shared two-argument attack method. Moose's one-argument animation starter does not override that active call, so this guide does not present its animation-only **3-point antlerless** or **2-point Wolf-target** branches as ordinary melee damage. Losing antlers is not verified protection from its registered melee attack. [Active caller][melee] · [Shared hit][melee-hit] · [Separate animation hook][old-attack] · [Animation-only adjustments][animated-hit]

## Feeding and breeding

Hold **Wheat or Dandelion** to tempt Moose. Their temptation goal accepts those two items directly; breeding uses the separate `moose_breedables` tag, which currently contains the same pair. [Temptation][moose-goals] · [Breeding tag][food-tag]

To breed, use an accepted food on an adult that is **not already in love and has age 0**, meaning it is out of its breeding cooldown. Each eligible server-side food check accepts on a **one-in-five random result**; a failed check produces smoke and returns false before the shared food-consumption branch. It is not a guaranteed five-click process. Successful Survival acceptance spends one item and puts that adult in love. Repeat for another ready adult and let them approach. [Food predicate and smoke event][moose-food] · [Feeding and consumption][animal-feed] · [Item helper][feed-use] · [Mate approach][breed-goal]

The offspring factory creates a Moose calf. Shared successful breeding sets both parents to a **6,000-game-tick cooldown**. **Feeding does not accelerate Moose calves through this path**: the Moose food predicate requires age 0 and therefore rejects babies before the generic baby-growth branch can run. Following held food is not evidence that a calf can consume it for growth. [Offspring][offspring] · [Breeding result][breed-result] · [Food gate][moose-food] · [Shared baby branch][animal-feed]

## Antler shedding

A newly initialized Moose starts antlered with its first counter set to **168,000, 192,000 or 216,000 ticking game ticks**. When an antlered Moose's counter reaches zero, the server removes its antlered state and drops **one Moose Antler**. It then waits **48,000, 72,000 or 96,000 ticking game ticks**, restores the antlers without dropping another item, and resets the longer counter. [Initial values][antler-initial] · [Default antler state][antler-default] · [Drop and regrowth][antler-cycle]

These are discrete source counter values, not wall-clock promises or a farm-production rate. The counter and antlered state are saved. The cycle has **no adult, jostling, feeding, shearing or death requirement** in its checked tick handler. [Cycle][antler-cycle] · [Saved state][save]

## Snow interaction

Use the **Snow layer item** (`minecraft:snow`) on a Moose without a snowy coat to apply a persistent snowy appearance. A Shovel removes it. Snow uses the ordinary item-consumption helper; outside Creative, a damageable Shovel's damage value is increased directly by one, capped at its maximum. This is an appearance interaction, not the Antler-shedding trigger. [Snow item][snow-item] · [Interaction][snow-interaction] · [Item helper][feed-use] · [Damage-value setter][raw-damage]

## Notes

Moose is registered as a `MobCategory.CREATURE` using `EntityMoose`, with dimensions **1.5 × 2.3 blocks**. No bundled `entities/moose` death-loot table was found; missing default tables resolve to the empty table. This does not rule out separate equipment/custom loot overrides, and it does not make the browser-listed Rib items a verified animal drop. [Entity registration][entity] · [Default loot key][loot-key] · [Loot reading][loot-read] · [Missing-table fallback][loot-missing] · [Bundled loot][loot-tables]

Source-reviewed on **2026-10-02** at `25319cecd6bee492767c5b15ee53b9213d1ec1ee`. Checked active registration/attributes, spawn predicates and missing callers/data, targeting and melee callers, food acceptance and generic breeding, antler ticking/saving, jostling, manual snow interactions, and resource-route gaps. No spawning, combat, breeding, shedding, weather, item-drop or multiplayer test was run. Natural snowy-coat timing is not certified here.

Related: [Moose Antler](../items/MooseAntler.md) · [Raw Moose Ribs](../items/RawMooseRibs.md) · [Cooked Moose Ribs](../items/CookedMooseRibs.md) · [Bison](Bison.md) · [Mobs](Mobs.md)

[animal-feed]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/entity/animal/Animal.java#L134-L158
[animated-hit]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityMoose.java#L219-L233
[antler-cycle]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityMoose.java#L206-L218
[antler-default]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityMoose.java#L158-L164
[antler-initial]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityMoose.java#L50-L64
[attributes]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L199
[biomes]: https://github.com/HungLo2020/MattMC/tree/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/resources/data/minecraft/worldgen/biome
[breed-goal]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/entity/ai/goal/BreedGoal.java#L33-L80
[breed-result]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/entity/animal/Animal.java#L197-L227
[browser-client]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-list]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[browser-server]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1906
[egg]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/Items.java#L1915
[egg-tab]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2048
[entity]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/entity/EntityType.java#L917-L919
[feed-use]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/entity/Mob.java#L1113-L1120
[food-tag]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/resources/data/minecraft/tags/item/moose_breedables.json#L1-L6
[items]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/Items.java#L1782-L1785
[jostle]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/ai/MooseAIJostle.java#L27-L166
[jostle-ready]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityMoose.java#L405-L411
[loot-key]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-missing]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[loot-read]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1527
[loot-tables]: https://github.com/HungLo2020/MattMC/tree/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/resources/data/minecraft/loot_table
[melee]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/entity/ai/goal/MeleeAttackGoal.java#L127-L132
[melee-hit]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/entity/Mob.java#L1292-L1320
[moose-attributes]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityMoose.java#L81-L83
[moose-food]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityMoose.java#L119-L143
[moose-goals]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityMoose.java#L105-L117
[offspring]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityMoose.java#L402-L407
[old-attack]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityMoose.java#L151-L156
[placement-registry]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[raw-damage]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/ItemStack.java#L437-L450
[recipes]: https://github.com/HungLo2020/MattMC/tree/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/resources/data/minecraft/recipe
[retaliation]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/ai/AnimalAIHurtByTargetNotBaby.java#L8-L31
[save]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityMoose.java#L167-L183
[snow-interaction]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityMoose.java#L322-L345
[snow-item]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/Items.java#L486
[spawn-check]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityMoose.java#L76-L87
[target-guard]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityMoose.java#L145-L149
[wolf-damage]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityMoose.java#L255-L265
