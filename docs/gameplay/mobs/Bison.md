# Bison

**Bison** (`minecraft:bison`) can be bred with Wheat and supply renewable [Bison Fur](../items/BisonFur.md) through dispenser shearing. Give adults room: their active target selection can pick a nearby player even without being attacked first. [Food and goals][bison-goals] · [Shearing][bison-shear] · [Player targeting][near-players]

## Obtaining

The [Bison Spawn Egg](../items/BisonSpawnEgg.md) is an ordinary category-listed item, available through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in **Creative**. Use the [Spawn eggs guide](../items/SpawnEggs.md) for placement, consumption and supported offspring interactions. [Egg registration][egg] · [Category listing][egg-tab] · [Browser assembly][browser-list] · [Client request][browser-client] · [Server checks][browser-server]

**A natural biome-spawn route is not established in the checked build.** Bison is registered, and its instance spawn check reads whether the configured Bison weight is positive, but that condition does not add it to a biome's creature list. The checked spawn-placement registry and bundled biome data do not list Bison. Do not treat an imported habitat description as a verified place to find one. [Entity registration][entity] · [Instance check][spawn-check] · [Spawn placements][placement-registry] · [Bundled biomes][biomes]

## Behavior

The registered attributes give **40 health points** (20 hearts), **8 attack-damage points**, and a base movement-speed attribute of **0.25**. These are source attributes, not a promise of final damage against armor or of movement measured in blocks per second. [Attribute values][bison-attributes] · [Attribute binding][attributes]

An adult that is not in love can select an eligible player through its **3-block target-selection range**. Normal target validity and sight checks still apply; the goal does not test whether the player struck first. Adult retaliation and baby-panic goals are also active, and the shared hurt-target goal can alert nearby adults when a calf is hurt. Holding Wheat is not a safety or taming guarantee. [Target selection][near-players] · [Goals][bison-goals] · [Baby/adult hurt response][retaliation]

Bison can also pair up for a charging contest. Eligible adults must be alive, out of water and off their charge cooldown; the goal searches nearby Bison and drives their approach and knockback. This interaction is separate from breeding. [Eligibility][charge-valid] · [Partner selection and contest][contest]

The ordinary melee goal calls the shared two-argument attack method. This Bison class still has a separate one-argument animation hook, so its animation-conditioned charge bonus and Wolf-specific damage adjustment are **not established as the ordinary melee hit values**. Treat a charging animal as dangerous, but do not use those imported-looking branches as a damage calculator. [Active attack caller][melee] · [Shared attack implementation][melee-hit] · [Separate animation hook][old-attack] · [Animation-conditioned hit][animated-hit]

## Feeding and breeding

Hold **Wheat** to tempt Bison. Use one Wheat on each ready adult to put both in love, then give them room to meet. A successful ordinary Survival feeding consumes one Wheat; Creative's infinite-materials handling preserves it. Breeding produces a Bison calf and sets both parents' breeding cooldown to **6,000 game ticks**. [Food and temptation][bison-goals] · [Feeding][animal-feed] · [Offspring][offspring] · [Breeding result][breed-result] · [Item consumption][feed-use] · [Infinite-materials handling][consume]

Wheat can also advance a calf's growth through the shared animal-feeding path. That is different from regrowing a sheared coat: **hand-fed Wheat does not increment the Fur regrowth counter**. [Food predicate][bison-food] · [Baby feeding][animal-feed] · [Age adjustment][baby-age] · [Actual grazing counter][grazing]

## Shearing and fur regrowth

1. Put **Shears in a [Dispenser](../blocks/DispenserAndDropper.md)** facing the space occupied by an adult, unsheared Bison. Keep the target area clear of other shearable entities
2. Activate the dispenser. Its entity check calls the Bison's shearing handler, dropping **2–3 Bison Fur** and marking its coat sheared
3. A leashed entity can have its leash connections cut **before** the dispenser reaches the coat-shearing branch. A successful hive-shearing action also takes priority over entity shearing; arrange the target area accordingly

[Registered Shears dispenser behavior][dispenser-register] · [Target box, priority and action][dispenser-shear] · [Bison readiness and Fur output][bison-shear]

The successful dispenser action **requests one point of Shears wear**. Damageability, an already-broken stack and enchantment processing determine the actual change; because this call has no player, a player's Creative exemption does not apply. [Wear request][dispenser-shear] · [Damage processing][shear-wear]

**Ordinary hand shearing is not connected in the checked Bison interaction path.** The Bison handler delegates food behavior and handles Snow/Shovels, but does not handle Shears; Shears also supplies no generic living-entity shear action. The dispenser is the verified caller of this animal's `Shearable` method. [Bison interaction][snow-interaction] · [Animal fallback][animal-feed] · [Mob dispatch][mob-interact] · [Player item fallback][player-interact] · [Shears implementation][shears-item] · [Default item interaction][item-interact]

After shearing, the Bison must complete **five grazings on Grass Blocks** to restore its coat. Each completed graze increments the counter and changes the Grass Block below it into Dirt. Starting a graze involves a random check, so five grazings is not a fixed waiting time. The sheared state and grazing count are saved. This direct grass replacement has no `mobGriefing` check in the reviewed handler. [Grass eating][grazing] · [Regrowth threshold][regrowth] · [Saved state][save]

## Snow and terrain interactions

Use the **Snow layer item** (`minecraft:snow`) on a Bison without a snowy coat to add a persistent snowy appearance. A Shovel clears it. The Snow item is consumed through the ordinary feeding-style item helper; outside Creative, a damageable Shovel's damage value is directly increased by one, capped at its maximum. This is a different path from the dispenser's enchantment-aware wear request. [Snow item registration][snow-item] · [Bison interaction][snow-interaction] · [Consumption][feed-use] · [Damage-value setter][raw-damage]

The server AI also removes **single-layer Snow blocks** in its nearby scan at **Y 127 or below**. It does not remove every snow depth, and this handler has no `mobGriefing` check. [Terrain scan][snow-break]

## Notes

The registered entity uses `MobCategory.CREATURE`, class `EntityBison`, and dimensions **1.7 × 2.0 blocks**. These registrations do not establish natural spawning. No bundled Bison death-loot table or Bison Fur crafting recipe was found; Fur's verified produced supply is the live dispenser-shearing route above. Missing default loot tables resolve to the empty table, though equipment or custom loot overrides are separate. [Entity][entity] · [Default loot key][loot-key] · [Loot lookup][loot-read] · [Missing-table fallback][loot-missing] · [Bundled loot][loot-tables] · [Bundled recipes][recipes]

Source-reviewed on **2026-10-02** at `25319cecd6bee492767c5b15ee53b9213d1ec1ee`. Checked active registrations, attributes, goals/callers, food and offspring handling, shearing dispatch, regrowth, manual snow interactions, terrain changes, and bounded spawn/loot/recipe gaps. No in-game spawning, combat, breeding, dispenser, fur-production or weather test was run. Natural snowy-coat timing is not certified by this guide.

Related: [Bison Fur](../items/BisonFur.md) · [Wheat](../items/Wheat.md) · [Moose](Moose.md) · [Mobs](Mobs.md)

[animal-feed]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/entity/animal/Animal.java#L134-L158
[animated-hit]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityBison.java#L223-L253
[attributes]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L123
[baby-age]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/entity/AgeableMob.java#L156-L167
[biomes]: https://github.com/HungLo2020/MattMC/tree/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/resources/data/minecraft/worldgen/biome
[bison-attributes]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityBison.java#L76-L78
[bison-food]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityBison.java#L132-L134
[bison-goals]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityBison.java#L115-L134
[bison-shear]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityBison.java#L403-L417
[breed-result]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/entity/animal/Animal.java#L197-L227
[browser-client]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-list]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[browser-server]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1906
[charge-valid]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityBison.java#L427-L429
[consume]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1080
[contest]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityBison.java#L447-L524
[dispenser-register]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L378
[dispenser-shear]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/core/dispenser/ShearsDispenseItemBehavior.java#L19-L67
[egg]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/Items.java#L1803
[egg-tab]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1979
[entity]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/entity/EntityType.java#L1158-L1160
[feed-use]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/entity/Mob.java#L1113-L1120
[grazing]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityBison.java#L198-L209
[item-interact]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/Item.java#L254-L256
[loot-key]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-missing]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[loot-read]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1527
[loot-tables]: https://github.com/HungLo2020/MattMC/tree/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/resources/data/minecraft/loot_table
[melee]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/entity/ai/goal/MeleeAttackGoal.java#L127-L132
[melee-hit]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/entity/Mob.java#L1292-L1320
[mob-interact]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/entity/Mob.java#L1053-L1110
[near-players]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityBison.java#L527-L542
[offspring]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityBison.java#L145-L149
[old-attack]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityBison.java#L281-L286
[placement-registry]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[player-interact]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/entity/player/Player.java#L854-L888
[raw-damage]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/ItemStack.java#L437-L450
[recipes]: https://github.com/HungLo2020/MattMC/tree/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/resources/data/minecraft/recipe
[regrowth]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityBison.java#L259-L266
[retaliation]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/ai/AnimalAIHurtByTargetNotBaby.java#L8-L31
[save]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityBison.java#L151-L167
[shear-wear]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/ItemStack.java#L457-L473
[shears-item]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/ShearsItem.java#L27-L84
[snow-break]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityBison.java#L346-L375
[snow-interaction]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityBison.java#L318-L344
[snow-item]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/Items.java#L486
[spawn-check]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/alexsmobs/entity/EntityBison.java#L80-L88
