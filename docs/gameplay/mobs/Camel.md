# Camel

A **Camel** carries up to **two players through normal mounting** and uses a charged forward dash. Equip a [Saddle](../items/Saddle.md) so the first rider can steer. Camels need **no taming step** and have **32 health points**, or **16 hearts**. [Camel interaction and tame state][camel] · [Steering passenger][shared] · [Active attributes][attributes]

## Finding a Camel

Look for Camels in **Desert villages**, or in natural spawn areas in **Desert** and **[Dry Midlands](../biomes/DryMidlands.md)**. MattMC includes a creature spawn-list entry of **one Camel** in each of those biomes. Dry Midlands is selected in the bundled normal preset's [Primordial Caves dimension](../dimensions/PrimordialCaves.md). These entries do not guarantee a Camel at any chosen location. [Desert entry][desert] · [Dry Midlands entry][dry-midlands] · [Dimension biome source][normal-preset]

Natural Camel spawning requires a block from the current `camels_spawnable_on` tag below and raw brightness above 8, plus the normal ground-placement and spawn checks. That tag expands to **Sand, Red Sand, and Suspicious Sand** in this snapshot. Registration of `Camel::checkCamelSpawnRules`, worldgen resource loading, and the active creature-spawning path connect those conditions to gameplay. [Placement registration][spawn-rules] · [Camel predicate][camel] · [Brightness check][animal] · [Camel ground tag][camel-ground] · [Sand tag][sand] · [Registry loading][registry-loader] · [Spawn dispatch][natural-spawner]

The ordinary Desert village meeting-point templates connect to a Camel pool whose template stores one Camel entity. The checked structure-placement path includes template entities and finalizes them. This is a village-generation route, not a promise that every possible Desert village or zombie-village variant always supplies one. The [Camel Spawn Egg](../items/CamelSpawnEgg.md) is a Creative alternative. [Village pool registration][desert-pools] · [Camel pool][camel-pool] · [Meeting-point template][meeting-point] · [Stored Camel][camel-template] · [Entity placement settings][pool-element] · [Entity loading and finalization][structure-template]

## Mounting and equipment

Interact with an adult using an empty hand to mount. A second player can interact to take the rear seat. Camels are treated as tame by their implementation; repeated bucking-and-taming attempts are unnecessary. Babies cannot be mounted through the normal interaction. [Current interaction][camel]

Interact while holding a Saddle to equip an empty saddle slot, or open the adult's inventory with **Sneak/Crouch + interact**. While riding, use Inventory. The actual equip action uses the Saddle's equippable component and Camel's allowed saddle slot; see [Saddle](../items/Saddle.md) for crafting and shared removal rules. A Camel has no Chest cargo slots, and the bundled Horse Armor and Llama Carpet components do not fit it. [Inventory and saddle-slot rules][shared] · [Camel inventory access][camel] · [Saddle item component][items] · [Equip action and Carpet restriction][equip] · [Allowed saddle types][saddle-tag] · [Horse Armor restriction][armor-tag] · [Mounted inventory command][server-riding]

The **first player passenger controls** a saddled Camel; the rear passenger does not steer. Without a Saddle, mounting alone does not give player control. Use movement controls for travel, Sprint for its ridden speed bonus when the dash cooldown is clear, and Sneak/Crouch to dismount. Default keys are **WASD**, **Shift** for Sprint, **Space** for Jump, **E** for Inventory, and **Left Ctrl** for Sneak/Crouch. [Control selection][shared] · [Camel ridden movement][camel] · [Dismount][player] · [Default controls][keys]

## Dashing and sitting

While the saddled Camel is standing on the ground, **hold Jump to charge and release to dash forward**. The normal jump-charge input feeds Camel's dash implementation. The dash sets a **55-game-tick cooldown**, about **2.75 seconds** at the normal 20 ticks per second; a ready sound plays when the countdown reaches zero. Charge, attributes, and the ground's speed factor affect the impulse, so this guide does not promise one fixed dash distance. [Charge and release][client-riding] · [Shared charge execution][shared] · [Camel dash and cooldown][camel]

Camels can sit while idle. A fully seated Camel responds to its controlling rider's forward input by standing, but movement is blocked during sitting and pose transitions. The ordinary stand-up transition is **52 game ticks**, about **2.6 seconds**. A suitable Lead pull can also prompt it to stand; entering water, being hurt, or beginning panic can make it stand immediately. [Rider input, movement restriction, and pose timing][camel] · [Idle sitting and panic behavior][camel-ai]

The idle sitting behavior is an active brain behavior, not a player command to sit on a fixed schedule. The Camel's current server AI ticks that brain and updates its activities. [Brain behavior registration][camel-ai] · [Server brain tick][camel]

Camels have a **1.5-block step-height attribute**, but still need collision clearance. Leave room above both riders and avoid assuming a fence is a secure pen wall. Falls can damage the Camel and propagate to passengers, and underwater travel can cause drowning or dismounting. Tall seating is not a blanket guarantee against enemy attacks or terrain damage. [Camel step height and navigation][camel] · [Fall propagation][shared] · [Drowning and underwater dismount dispatch][living] · [Dismount tag][underwater]

For a short, **source-derived and untested** two-player trip, saddle an adult, mount the driver first, then the passenger. Start on level open ground, wait for the Camel to stand, and try one charged dash with room to stop before taking a route with drops or obstacles. [Normal mounting and dash][camel]

## Feeding and breeding

The bundled Camel food is **Cactus**. Holding it can lure a Camel through its active temptation behavior. Feeding one Cactus can restore **2 health points**, or **1 heart**; advance a baby's growth by **10 seconds** of normal game time; or put an eligible adult in love. This is a fixed baby-growth reduction, not 10% of its remaining growth time. [Food tag][camel-food] · [Feeding effects][camel] · [Growth conversion][age] · [Temptation behavior][camel-ai] · [Temptation sensor][sensors]

A successful feed consumes one item in ordinary Survival. A healthy adult on breeding cooldown does not consume Cactus just to shorten that cooldown. Dismount both animals before breeding, heal each to full health, then feed each Cactus when eligible. Both parents must be **adults at full health**, in love, carrying no riders, and riding nothing; panicking partners are also excluded by the active mating behavior. No prior taming is required. [Food consumption][shared] · [Camel mating and food][camel] · [Shared parent gate][shared] · [Active mating behavior][animal-love] · [Brain registration][camel-ai]

Two Camels produce a baby Camel. Without feeding, it begins with **24,000 game ticks** of growth, normally **20 minutes** while ticking. Parents receive a **6,000-game-tick** cooldown, normally **5 minutes**. Camel offspring use Camel's own attributes; their factory does not run the Horse-family inherited speed/jump variation calculation. [Camel offspring factory][camel] · [Birth and cooldown][animal] · [Age progression][age] · [Attribute registration][attributes]

## Persistence and recovery

Camels do not use ordinary distance-based animal despawning. Their health, attributes, equipment, and saved sitting/standing state persist. Keep a Camel in a suitable enclosure or on a Lead; feeding or naming is not required merely to prevent ordinary distance despawning. [Animal despawn rule][animal] · [Pose save data][camel] · [Health and equipment save data][living]

The Camel's normal death loot table has **no item pools**, so it supplies no ordinary Leather or meat drop. A normally player-equipped Saddle can drop under the usual equipment-loot rules, including the mob-loot gate and drop-prevention effects. Recover it from the inventory or eligible Shears interaction where possible. Adult player-attributed deaths normally award **1–3 experience**, subject to the experience and mob-loot checks; babies do not award that death experience. [Empty item loot][camel-loot] · [Saddle drop marking][equip] · [Equipment drop handling][mob] · [Loot and experience gates][living] · [Base animal experience][animal]

## Related pages

- [Camel Spawn Egg](../items/CamelSpawnEgg.md)
- [Saddle](../items/Saddle.md)
- [Horse](Horse.md)
- [Llama](Llama.md)
- [Trader Llama](TraderLlama.md)
- [Transport](../mechanics/Transport.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. Natural spawn resources, the normal world preset, village pool/template entities, active brain and interaction paths, equipment, ordinary player mounting, dash input/cooldown, food, breeding, loot, and saved state were inspected. Village NBT was decoded for source inspection. No in-game spawn, riding, dash-distance, passenger, feeding, or breeding test was run.

[camel]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/camel/Camel.java
[shared]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java
[attributes]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java
[desert]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/desert.json
[dry-midlands]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/dry_midlands.json
[normal-preset]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[spawn-rules]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[animal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/Animal.java
[camel-ground]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/camels_spawnable_on.json
[sand]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/sand.json
[registry-loader]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/resources/RegistryDataLoader.java
[natural-spawner]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/NaturalSpawner.java
[desert-pools]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/data/worldgen/DesertVillagePools.java
[camel-pool]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/template_pool/village/desert/camel.json
[meeting-point]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/structure/village/desert/town_centers/desert_meeting_point_1.nbt
[camel-template]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/structure/village/desert/camel_spawn.nbt
[pool-element]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/levelgen/structure/pools/SinglePoolElement.java
[structure-template]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java
[items]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java
[equip]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/equipment/Equippable.java
[saddle-tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/entity_type/can_equip_saddle.json
[armor-tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/entity_type/can_wear_horse_armor.json
[server-riding]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java
[player]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/player/Player.java
[keys]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/client/Options.java
[client-riding]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/client/player/LocalPlayer.java
[camel-ai]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/camel/CamelAi.java
[living]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java
[underwater]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/entity_type/dismounts_underwater.json
[camel-food]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/item/camel_food.json
[age]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/AgeableMob.java
[sensors]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/sensing/SensorType.java
[animal-love]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/behavior/AnimalMakeLove.java
[camel-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/entities/camel.json
[mob]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Mob.java
