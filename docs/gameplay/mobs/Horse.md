# Horse

A **Horse** is a one-rider land mount. Tame it by riding, equip a [Saddle](../items/Saddle.md) for steering and jumping, and use Horse Armor for protection. For cargo, choose a [Donkey](Donkey.md) or [Mule](Mule.md): a Horse has equipment slots but no Chest inventory. [Riding and inventory][shared] · [Equipment menu][menu] · [Passenger capacity][entity]

## Finding a Horse

Horses are registered as `minecraft:horse`. The bundled Plains, Sunflower Plains, Savanna, Savanna Plateau, and Windswept Savanna spawn lists include Horses in groups of **2–6**. Natural ground spawning uses the animal predicate: a block in `animals_spawnable_on` below, which is Grass Block in the bundled tag, and raw brightness above 8, alongside the normal placement and spawn checks. A suitable biome does not guarantee an animal at every location. [Registration][entities] · [Biome setup][biomes] · [Spawn groups][biome-spawns] · [Placement registration][spawn-rules] · [Animal predicate][animal] · [Ground tag][spawn-block]

These entries are connected through the biome registry bootstrap and the active creature-spawning path. [Biome routing][biome-registry] · [Bootstrap][biome-bootstrap] · [Spawn-list lookup][chunk-generator] · [Spawn dispatch][natural-spawner]

A normally initialized Horse has **15–30 maximum health points**, or **7.5–15 hearts**. Health, movement speed, and jump strength vary between Horses. Coat color and markings are separate from those attributes; appearance does not identify a faster mount. The [Horse Spawn Egg](../items/HorseSpawnEgg.md) is a Creative alternative. [Attribute initialization and appearance][horse] · [Generated ranges][shared] · [Active attribute registration][attributes]


[Primordial Plains](../biomes/PrimordialPlains.md) in [Primordial Caves](../dimensions/PrimordialCaves.md) also lists Horse groups of **2–6** in the bundled Normal preset. Its ceiling and lack of skylight make the ordinary brightness and surface restrictions important; a table entry is not a confirmed cave-floor population. [Biome data](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/primordial_plains.json) · [Normal preset](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/world_preset/normal.json)

## Taming and feeding

1. Find an adult Horse with no rider and interact with an **empty hand** to mount it
2. If it bucks you off, mount again
3. Hearts indicate successful taming; a Saddle is not needed for these taming attempts
4. Once tame, use the equipment steps below

Taming is random. The internal temper starts at 0, is capped at 100, and each actual taming roll succeeds when a random value from 0–99 is below the current temper. A failed player-rider roll adds 5 temper before ejecting the rider. Food can raise temper first, but there is no fixed number of rides or guaranteed taming time. The current AI goal is registered and dispatched by the server goal selector. [Mount interaction and temper][shared] · [Taming goal][taming] · [Goal dispatch][mob]

The table also applies to Donkeys and Mules. **Dismount before feeding and use the animal without Sneak/Crouch.** Healing is in health points; **2 points = 1 heart**. Growth values are the amount removed from a baby's remaining growth time at the normal 20 game ticks per second, capped at adulthood. [Feeding implementation][shared] · [Food tag][food] · [Growth conversion][age] · [Default tick rate][tick-rate]

| Food | Health restored, up to maximum | Baby growth advanced | Temper increase | Can start breeding love mode |
| --- | ---: | ---: | ---: | --- |
| Wheat | 2 | 20 seconds | 3 | No |
| Sugar | 1 | 30 seconds | 3 | No |
| Hay Bale | 20 | 180 seconds | 0 | No |
| Apple | 3 | 60 seconds | 3 | No |
| Carrot | 3 | 60 seconds | 3 | No |
| Golden Carrot | 4 | 60 seconds | 5 | Yes |
| Golden Apple | 10 | 240 seconds | 10 | Yes |
| Enchanted Golden Apple | 10 | 240 seconds | 10 | Yes |

A successful feed consumes one item in ordinary Survival. Food is accepted when it can heal, advance a baby's age, start eligible love mode, or increase an untamed animal's temper. A healthy, fully grown tame animal does not consume ordinary food just because it is on this list. Temper increases on a tame animal need another feeding benefit; Hay Bales never add temper. Golden food starts love only when tame, at breeding age, and not already in love. Mule love particles do **not** make Mules fertile. [Food handling][shared] · [Creative consumption exemption][stack] · [Mule mating restriction][mule]

For leading an animal, hold a **Golden Carrot, Golden Apple, or Enchanted Golden Apple**. Only these three are in the bundled temptation tag; Wheat, ordinary Carrots, Apples, Sugar, and Hay Bales are feeding items without that lure behavior. [Registered temptation goal][shared] · [Temptation items][lure]

## Riding and equipment

On a tame, unoccupied adult, interact while holding a Saddle to fill its empty saddle slot, or use **Sneak/Crouch + interact** to open its inventory. While mounted, the Inventory key opens the same screen. The current Saddle works through its equippable component; follow [Saddle](../items/Saddle.md) for the recipe and shared removal rules. [Horse interaction][horse] · [Shared inventory interaction][shared] · [Saddle registration][items] · [Equip action][equip] · [Item dispatch][stack] · [Mounted inventory command][server-riding]

Use movement controls to steer a saddled Horse. Hold Jump to charge, watch the meter, and release to jump; the charge peaks and then settles lower if held too long. Backward movement and sideways movement are slower than forward movement. Use Sneak/Crouch to dismount. Defaults are **WASD**, **Space** for Jump, **E** for Inventory, and **Left Ctrl** for Sneak/Crouch; Shift is Sprint in MattMC. [Ridden motion and jump execution][shared] · [Charge and release][client-riding] · [Dismount][player] · [Default keys][keys]

Horse Armor goes in the body-armor slot, separately from the Saddle. Interact with a tame adult holding valid armor to fill an empty body slot, or use the inventory to manage it. The bundled allowed-entity tag includes the ordinary Horse, **not Donkeys or Mules**. [Equip interaction][shared] · [Armor menu][menu] · [Allowed animal][armor-tag]

| Horse Armor | Armor points | Armor toughness |
| --- | ---: | ---: |
| [Leather](../items/LeatherHorseArmor.md) | 3 | 0 |
| [Copper](../items/CopperHorseArmor.md) | 4 | 0 |
| [Iron](../items/IronHorseArmor.md) | 5 | 0 |
| [Golden](../items/GoldenHorseArmor.md) | 7 | 0 |
| [Diamond](../items/DiamondHorseArmor.md) | 11 | 2 |
| [Netherite](../items/NetheriteHorseArmor.md) | 11 | 3 |

Netherite also adds 0.1 knockback resistance. These are equipped attributes, not extra hearts or a fixed damage-reduction percentage; see [Armor and damage reduction](../mechanics/Armor.md). Default Horse Armor has no durability component and disables damage-on-hurt. It and the Saddle can be recovered through the inventory or the eligible Shears interaction described on the Saddle page. [Registered armor][items] · [Horse-armor components][armor] · [Material values][armor-materials] · [Attribute construction][armor-modifiers] · [Equipment shearing][entity]

Plan routes with room for the mount and rider. Falls can hurt both, and sustained submersion can drown the animal; the underwater passenger rule can also dismount the rider. A land mount is not a substitute for the water vehicles in [Transport](../mechanics/Transport.md). [Fall propagation][shared] · [Drowning and dismount dispatch][living] · [Underwater dismount tag][underwater]

## Breeding Horses and Mules

Use two **tame adults at full health**, with no riders and neither animal riding another entity. Bring them close enough to approach each other, then feed each a Golden Carrot, Golden Apple, or Enchanted Golden Apple. Both must be in love and outside their breeding cooldown. Love lasts 600 game ticks, normally 30 seconds, and damage clears it. If a golden food does not restore an injured parent all the way to full health, finish healing it before expecting a foal. [Parent requirements and feeding][shared] · [Partner search and breeding goal][breed-goal] · [Love duration and damage reset][animal]

- Horse + Horse produces a Horse
- Horse + Donkey produces a Mule
- Donkey + Donkey produces a Donkey
- Mules cannot breed, including with another Mule

The cross can run from either parent species. A Mule's offspring factory also supports its spawn-egg interaction, but ordinary mating is rejected. [Horse offspring][horse] · [Donkey offspring][donkey] · [Shared mating default][shared] · [Mule implementation][mule]

A foal starts with **24,000 game ticks** of growth, about **20 minutes** while ticking at the normal rate. Each parent receives a **6,000-game-tick** breeding cooldown, about **5 minutes**. Food speeds baby growth; the shared feeding handler does not reduce an adult's cooldown. Offspring are not automatically tamed. [Birth and cooldown][animal] · [Age ticking][age] · [Default tame state and feeding][shared]

Health, speed, and jump strength are inherited with variation around the parents' values. The implementation uses both parents' base attributes, a random adjustment, and bounded ranges; it does not simply copy the better parent. This also allows bred Donkeys to vary from the speed and jump values used for newly spawned Donkeys. Choose parents by their actual performance, and assess offspring after they grow; no speed or jump trial was run for this guide. [Inheritance][shared] · [New chested-horse attributes][chested]

## Keeping and recovering your mount

Horses do not use ordinary distance-based animal despawning. Their tame state, owner, temper, coat, health, attributes, and equipped items are saved. Taming does not make a Horse invulnerable or prevent wandering; keep it in a suitable enclosure or use a [Lead](../items/Lead.md). [Animal despawn rule][animal] · [Tame-state persistence][shared] · [Coat persistence][horse] · [Health, attributes, and equipment persistence][living]

With normal mob loot enabled, an adult's base death loot is **0–2 Leather**, with a Looting count bonus; babies do not produce this base loot. Normally player-equipped Saddles and Horse Armor are marked for guaranteed equipment drops, subject to the ordinary loot gate and equipment-drop prevention effects. Removing useful gear through the inventory before exposing the mount to danger is safer than relying on death recovery. [Leather loot][horse-loot] · [Adult and game-rule gate][living] · [Equipment drop marking and processing][mob] · [Saddle equip marking][equip]

## Related pages

- [Donkey](Donkey.md): naturally spawning cargo mount
- [Mule](Mule.md): bred cargo mount
- [Horse Spawn Egg](../items/HorseSpawnEgg.md)
- [Saddle](../items/Saddle.md)
- [Transport](../mechanics/Transport.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. Registrations, spawn wiring, active goal dispatch, normal feeding and riding interactions, equipment components, inheritance, loot, and save paths were inspected. The instructions describe normal unoccupied-adult preparation; other interaction states and modified components can take different paths. No in-game taming, feeding, breeding, armor, jump, or travel test was run.

[shared]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java
[menu]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/inventory/HorseInventoryMenu.java
[entity]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Entity.java
[entities]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java
[biomes]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/data/worldgen/biome/OverworldBiomes.java
[biome-spawns]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/data/worldgen/BiomeDefaultFeatures.java
[spawn-rules]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[animal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/Animal.java
[spawn-block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/animals_spawnable_on.json
[biome-registry]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/data/worldgen/biome/BiomeData.java
[biome-bootstrap]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/data/registries/VanillaRegistries.java
[chunk-generator]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java
[natural-spawner]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/NaturalSpawner.java
[horse]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/horse/Horse.java
[attributes]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java
[taming]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/RunAroundLikeCrazyGoal.java
[mob]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Mob.java
[food]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/item/horse_food.json
[age]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/AgeableMob.java
[tick-rate]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/TickRateManager.java
[stack]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ItemStack.java
[mule]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/horse/Mule.java
[lure]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/item/horse_tempt_items.json
[items]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java
[equip]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/equipment/Equippable.java
[server-riding]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java
[client-riding]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/client/player/LocalPlayer.java
[player]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/player/Player.java
[keys]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/client/Options.java
[armor-tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/entity_type/can_wear_horse_armor.json
[armor]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Item.java
[armor-materials]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/equipment/ArmorMaterials.java
[armor-modifiers]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/equipment/ArmorMaterial.java
[living]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java
[underwater]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/entity_type/dismounts_underwater.json
[breed-goal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/BreedGoal.java
[donkey]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/horse/Donkey.java
[chested]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/horse/AbstractChestedHorse.java
[horse-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/entities/horse.json
