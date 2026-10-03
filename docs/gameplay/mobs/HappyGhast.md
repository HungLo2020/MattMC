# Happy Ghast

A **Happy Ghast** (`minecraft:happy_ghast`) is a passive flying mount. Raise a **ghastling** hatched from a [Dried Ghast](../blocks/DriedGhast.md), then equip the adult with a [Harness](../items/Harnesses.md). Up to **four passengers** can ride; the player in the first seat steers while the mount is allowed to move. This is a separate entity from the hostile [Ghast](Ghast.md). [Entity identity and seats][happy-id] · [Hatch caller][hatch] · [Adult equipment and mounting][happy-equip] · [Passenger/control checks][happy-seats]

## Obtaining

Use the [Dried Ghast guide](../blocks/DriedGhast.md) for crafting, Piglin bartering, Nether Fossil placement, Water setup and hydration. Its hatch callback creates a **baby Happy Ghast**, not a hostile Ghast. Hydration and growing the hatched animal are separate stages. [Hatching][hatch] · [Baby age][aging]

No Happy Ghast entry was found in the bundled biome spawn lists or structure spawn overrides inspected for this snapshot. Its registered ground-spawn predicate alone does **not** establish a natural grassland or Nether population. Use hatching or the listed egg instead of searching a biome on that assumption. The active biome registry is loaded from resource data, and the natural-spawn caller selects those loaded biome/structure lists. [Placement registration][spawn-registration] · [Loaded biome registry][registry-list] · [Resource loader][registry-load] · [Spawn-list selection][spawn-selection] · [Biome/structure lookup][spawn-tables]

The [Happy Ghast Spawn Egg](../items/HappyGhastSpawnEgg.md) is listed in MattMC's [inventory item browser](../mechanics/InventoryBrowser.md), where Creative players can request it. Egg placement is separate from hatching and does not prove natural spawning. [Egg registration][happy-egg-item] · [Category entry][happy-egg-category]

## Behavior

### Growing and feeding a ghastling

A newly hatched ghastling starts at **−24,000 age ticks** and reaches adulthood after about **20 minutes of ticking at 20 ticks per second**. Interact with **Snowballs** to accelerate a baby's growth by about **10% of the remaining time per feeding**, rounded down to whole seconds. Hold Snowballs to attract babies or adults; an unharnessed adult also follows held Harnesses. A harnessed adult's temptation predicate accepts the food tag instead. [Age progression and feeding amount][aging] · [Feeding dispatch][animal-feeding] · [Age advancement][age-up] · [Default tick rate][tick-rate] · [Snowball tag][happy-food] · [Tempting items][happy-tempt] · [Adult temptation][happy-goals]

Ghastlings use flying follow/temptation behavior and can follow a nearby visible player or adult friendly mob. They can breathe underwater, but **adults lose that baby-only exemption**. A ghastling cannot wear the normal Harness, so let it mature before planning a ride. [Baby behavior][happy-baby-ai] · [Adult sensor][adult-sensor] · [Sensor registration][sensor-registration] · [Followable mob tag][followable-tag] · [Water behavior][happy-water] · [Adult equipment gate][happy-equip]

**Happy Ghasts do not breed through ordinary feeding.** The species explicitly rejects love mode. Snowballs grow babies and attract the mob; the shared feeding path does not directly heal an adult. The offspring factory also supports the matching spawn egg's baby interaction, which is distinct from mating. [Love-mode rejection][happy-breed] · [Shared feeding][animal-feeding] · [Egg dispatch][egg-interaction] · [Baby-from-egg path][egg-baby]

### Health and keeping it nearby

The default maximum is **20 health points (10 hearts)**. An injured living Happy Ghast automatically heals **1 health point** on each eligible **600-tick** interval, roughly every **30 seconds at 20 ticks per second**. That changes to a **20-tick** interval, roughly **1 second**, in a natural dimension while its body intersects the cloud-height band or precipitation reaches its location. The rain/snow check requires current weather and exposure, so a roof can prevent that route; merely placing Water beside it is not the faster-healing condition. [Health attributes][happy-attributes] · [Healing caller][happy-healing] · [Cloud-band test][cloud-range] · [Precipitation exposure][weather-range]

When unoccupied and not leashed, it sets a home area with a nominal radius of **64 blocks for an unharnessed adult**, or **32 blocks for a baby or harnessed adult**. This is a wandering restriction, not a fence: the home can reset after displacement, and losing the last passenger clears it. Use a suitable enclosure or [Lead](../items/Lead.md) when retaining a mount matters. Ordinary distance-based animal despawning is disabled. [Home update][happy-home] · [Last-passenger behavior][happy-seats] · [Animal retention][animal-retention]

## Riding and steering

Equip a living adult's empty body slot by interacting with any of the [sixteen Harness colors](../items/Harnesses.md#colors-and-exact-recipes), or use a dispenser. No taming step or steering stick is required by this interaction. Then interact without Sneak/Crouch to mount. The Harness does not add a cargo inventory. [Mob implementation][happy-implementation] · [Equipment and interaction][happy-equip] · [Harness component][harness-component] · [Allowed species][harness-target] · [Passenger controller][happy-seats]

The mount accepts **at most four passengers**. Its **first passenger must be a player**, it must still wear body equipment, and its temporary stay-still state must be clear for that player to control it. Extra passengers do not each get independent steering. The first boarding and final dismount also trigger the Harness goggles' lowering/raising sounds. [Seat and controller rules][happy-seats]

- Use Forward/Back and Left/Right to move; forward/backward flight follows the controlling player's look angle
- Looking up while moving forward climbs, and looking down while moving forward descends
- Backward input uses half the forward directional amount; Jump adds upward input
- Steering turns gradually toward the controlling player's facing
- Use Sneak/Crouch to dismount. Defaults are **WASD**, **Space** for Jump and **Left Ctrl** for Sneak/Crouch; Shift is Sprint

[Ridden input and turning][happy-controls] · [Active ridden dispatch][ridden-dispatch] · [Ridden callback calls][ridden-callbacks] · [Default controls][keys] · [Dismount action][dismount]

### Standing on top and stopped flight

Dismounting starts a short **10-tick stay-still timeout**. A non-spectator player detected above the adult refreshes that timeout; the scan excludes a player whose root vehicle is itself a Happy Ghast. While stopped, the movement control halts it, the controller is unavailable and the adult's collision behavior supports standing on top. Its look control also aligns it to a cardinal direction. To resume piloting, board and clear any player still standing in the area above it. [Dismount timeout][happy-seats] · [Refresh and load grace period][happy-still] · [Above-player scan and collision][happy-platform] · [Movement halt][happy-stop] · [Cardinal alignment][happy-align]

That support is conditional on the living adult and its collision state; it is not a permanent solid building block. Allow room for the adult's **4 × 4-block** body, and plan a landing before stepping off. Its own fall handling does not establish fall protection for a player who leaves it. [Size][happy-id] · [Collision conditions][happy-platform] · [Mob fall handling][happy-water]

## Drops and equipment recovery

The bundled Happy Ghast death table has **no item pools**; it is not a source of Ghast Tears or Dried Ghasts. Normal adult/player-credit and mob-loot conditions can still award the shared base animal reward of **1–3 experience points**, before applicable experience modifiers. A baby is excluded by the shared experience gate. [Empty table][happy-loot] · [Default loot ID][loot-id] · [Registered loot data][loot-registry] · [Resource loading][loot-loading] · [Table caller][loot-call] · [Animal experience][animal-retention] · [Death and experience conditions][death-drops] · [Experience modifier dispatch][xp-modifiers]

Player-equipped or dispenser-equipped Harnesses are marked for guaranteed equipment drops, subject to the adult/mob-loot gate and equipment-drop prevention effects. For deliberate recovery, first empty the passenger seats and use usable **Shears without Sneak/Crouch**. Leash removal can take priority. See [Harness removal](../items/Harnesses.md#removing-and-recoloring-a-harness) for the full sequence and recoloring recipes. [Equipment marking][equip-action] · [Dispenser marking][dispenser-action] · [Loot gate][loot-gate] · [Equipment drops][equipment-drops] · [Unoccupied gate][shear-passengers] · [Shears priority][equipment-shear]

## Notes

The entity is registered in the creature category. Adult size and health are different properties: **4 × 4 blocks**, **20 health points**, with **four passenger slots**. The adult flight and ghastling AI paths are distinct. [Registration][happy-id] · [Attributes][happy-attributes] · [Active attribute registration][attribute-registration] · [Age-dependent setup][happy-goals] · [Capacity][happy-seats]

## Related pages

- [Dried Ghast block](../blocks/DriedGhast.md) and [Dried Ghast item](../items/DriedGhast.md)
- [Harnesses](../items/Harnesses.md), [Snowball](../items/Snowball.md) and [Happy Ghast Spawn Egg](../items/HappyGhastSpawnEgg.md)
- [Strider](Strider.md), [Transport](../mechanics/Transport.md), [Ghast](Ghast.md) and [Mobs](Mobs.md)

## Verification scope

Source-reviewed at MattMC commit `bb9a8a060da02b64f23508b77794fb0f79307de4` on 2026-10-02. Hatch identity, the bundled spawn-list omission, food/age/health, adult and baby behavior, Harness dispatch, passenger controls, conditional standing support and loot were inspected. No in-game hatch, growth, flight, passenger, platform, healing or equipment test was run. Dried Ghast hydration remains owned by its block guide; leash cargo behavior is outside this page's reviewed scope.

[happy-id]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/EntityType.java#L672-L680
[hatch]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/level/block/DriedGhastBlock.java
[happy-equip]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/animal/HappyGhast.java#L254-L290
[happy-seats]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/animal/HappyGhast.java#L292-L332
[aging]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/AgeableMob.java#L129-L168
[spawn-registration]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L122-L149
[registry-list]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L125
[registry-load]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L310-L335
[spawn-selection]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L315-L326
[spawn-tables]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L451
[happy-egg-item]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/Items.java#L1882-L1884
[happy-egg-category]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2032
[animal-feeding]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/animal/Animal.java#L133-L158
[age-up]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/AgeableMob.java#L72-L90
[tick-rate]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/TickRateManager.java#L7-L21
[happy-food]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/item/happy_ghast_food.json
[happy-tempt]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/item/happy_ghast_tempt_items.json
[happy-goals]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/animal/HappyGhast.java#L91-L137
[happy-baby-ai]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/animal/HappyGhastAi.java#L67-L99
[adult-sensor]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/ai/sensing/AdultSensorAnyType.java
[sensor-registration]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/ai/sensing/SensorType.java#L40
[followable-tag]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/entity_type/followable_friendly_mobs.json
[happy-water]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/animal/HappyGhast.java#L154-L181
[happy-breed]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/animal/HappyGhast.java#L233-L252
[egg-interaction]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/Mob.java#L1088-L1103
[egg-baby]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L184
[happy-attributes]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/animal/HappyGhast.java#L139-L147
[happy-healing]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/animal/HappyGhast.java#L423-L452
[cloud-range]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/Entity.java#L1464-L1476
[weather-range]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/level/Level.java#L863-L873
[happy-home]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/animal/HappyGhast.java#L433-L444
[animal-retention]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L129
[happy-implementation]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/animal/HappyGhast.java
[harness-component]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/equipment/Equippable.java#L111-L120
[harness-target]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/entity_type/can_equip_harness.json
[happy-controls]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/animal/HappyGhast.java#L334-L372
[ridden-dispatch]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2823-L2837
[ridden-callbacks]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2422-L2441
[keys]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/client/Options.java#L551-L565
[dismount]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/player/Player.java#L451-L459
[happy-still]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/animal/HappyGhast.java#L400-L420
[happy-platform]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/animal/HappyGhast.java#L528-L568
[happy-stop]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/monster/Ghast.java#L245-L263
[happy-align]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/animal/HappyGhast.java#L617-L645
[happy-loot]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/loot_table/entities/happy_ghast.json
[loot-id]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-registry]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/level/storage/loot/LootDataType.java#L20-L28
[loot-loading]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L40-L67
[loot-call]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1501-L1527
[death-drops]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1487
[xp-modifiers]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/LivingEntity.java#L585-L587
[equip-action]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/equipment/Equippable.java#L159-L175
[dispenser-action]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/core/dispenser/EquipmentDispenseItemBehavior.java#L15-L37
[loot-gate]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L567
[equipment-drops]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/Mob.java#L813-L838
[shear-passengers]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/Mob.java#L512-L514
[equipment-shear]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/Entity.java#L2134-L2144
[attribute-registration]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java
