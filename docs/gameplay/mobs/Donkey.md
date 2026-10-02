# Donkey

A **Donkey** is a one-rider land mount that can carry **15 cargo slots** after you attach a Chest. Tame it, add a [Saddle](../items/Saddle.md) to steer, and load supplies through its inventory. Its cargo capacity is the same as a [Mule's](Mule.md), and it cannot wear Horse Armor. [Chested-horse implementation][chested] · [Riding][shared] · [Armor restriction][armor-tag]

## Finding a Donkey

Donkeys are actively registered as `minecraft:donkey`. The bundled natural spawn lists include:

| Biome | Group size |
| --- | ---: |
| Plains and Sunflower Plains | 1–3 |
| Savanna, Savanna Plateau, and Windswept Savanna | 1 |
| Meadow | 1–2 |

These are spawn-list group sizes, not guaranteed local populations. Ordinary natural placement requires Grass Block below, raw brightness above 8, and the normal ground-placement and spawn checks. Cherry Grove does not use Meadow's Donkey entry. [Entity registration][entities] · [Biome settings][biomes] · [Plains entries][biome-spawns] · [Placement registration][spawn-rules] · [Animal predicate][animal] · [Ground tag][spawn-block]

The biome registry maps these named biomes to the checked spawn helpers, and the active spawn code consumes their entries. [Biome routing][biome-registry] · [Spawn dispatch][natural-spawner]

A normally spawned Donkey has **15–30 maximum health points**, or **7.5–15 hearts**. Newly spawned Donkeys use fixed initial speed and jump attributes, but bred Donkeys can inherit different values. For Creative placement, use the [Donkey Spawn Egg](../items/DonkeySpawnEgg.md). [Initial attributes][chested] · [Health range and inheritance][shared] · [Active attribute registration][attributes]


[Primordial Plains](../biomes/PrimordialPlains.md) in [Primordial Caves](../dimensions/PrimordialCaves.md) also lists Donkey groups of **1–3** in the bundled Normal preset. Its ceiling and lack of skylight make the ordinary brightness and surface restrictions important; a table entry is not a confirmed cave-floor population. [Biome data](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/primordial_plains.json) · [Normal preset](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/world_preset/normal.json)

## Taming, feeding, and riding

Mount an unoccupied adult with an **empty hand**, and try again after it bucks you off until hearts signal taming. Food can improve temper. Donkeys use the same [taming rules and feeding table as Horses](Horse.md#taming-and-feeding), including ordinary Carrots as food and only the three golden foods as lures. Feed while dismounted and without Sneak/Crouch. [Shared taming and feeding][shared] · [Taming goal][taming] · [Food tag][food] · [Lure tag][lure]

Once tame and adult, interact with a Saddle to equip the empty saddle slot, or open the animal's inventory with **Sneak/Crouch + interact**. While mounted, use Inventory. A Saddle enables steering and charged jumping; the Chest is separate and is not needed to ride. Follow the [Horse riding controls](Horse.md#riding-and-equipment) and [Saddle guide](../items/Saddle.md) for controls, crafting, and removal. [Saddle slot and interaction][shared] · [Current equip action][equip] · [Equipment menu][menu]

## Adding and using cargo storage

1. Dismount from your tame adult Donkey
2. Hold an ordinary [Chest](../blocks/Chest.md) and interact **without Sneak/Crouch** to attach it; one Chest is consumed in ordinary Survival
3. Open the inventory with Sneak/Crouch + interact, or the Inventory key while riding
4. Put supplies in the **three rows of five cargo slots**; the Saddle slot is separate

You can add only one Chest. Donkeys do not become double chests, and Trapped Chests are not accepted by this interaction. The storage works without a Saddle; taming is still needed to open the screen. A Donkey already carrying a rider cannot accept the Chest through this interaction. [Chest interaction and dimensions][chested] · [Shared inventory size and access][shared] · [Menu slots][menu] · [Mounted inventory command][server-riding]

There is **no ordinary interact-to-remove-Chest action** in the checked implementation. Retrieve cargo through the inventory before abandoning a mount. Shears can remove eligible equipped gear, but they do not remove the attached Chest or turn its contents into a portable filled Chest. [Chest interaction and saved flag][chested] · [Equipment-shearing path][entity]

For a small supply run, tame an adult, attach a Chest, add a Saddle, and load expendable supplies first. Ride a short, open land route, dismount, and confirm you can unload at the destination before carrying valuable items. This is a **source-derived, untested example**. Avoid steep drops and submerged routes: fall damage can reach the rider, and these mounts can drown. [Riding and fall handling][shared] · [Drowning and passenger handling][living] · [Underwater dismount tag][underwater]

## Breeding

Two Donkeys produce a Donkey; a Donkey and a Horse produce a Mule. Both parents must be **tame adults at full health**, in love, carrying no rider, and riding nothing. After dismounting, feed each a Golden Carrot, Golden Apple, or Enchanted Golden Apple and let them approach. [Donkey pairing and offspring][donkey] · [Eligibility and feeding][shared] · [Breeding goal][breed-goal]

The new animal starts untamed. It takes **24,000 game ticks**, normally about **20 minutes**, to grow without feeding; parents have a **6,000-game-tick**, normally **5-minute**, cooldown. Health, speed, and jump strength receive inherited variation. Breeding is not a guaranteed improvement, and a bred Donkey is not restricted to the fixed speed/jump used for natural initialization. See [Horse breeding](Horse.md#breeding-horses-and-mules) for the shared rules. [Inheritance and tame default][shared] · [Birth and cooldown][animal] · [Age ticking][age] · [Initial attributes][chested]

## Persistence and drops

Donkeys do not use ordinary distance-based animal despawning. Tame state, owner, temper, health, attributes, equipment, the Chest flag, and cargo slots are saved. Keep the mount enclosed or use a [Lead](../items/Lead.md); saved data does not prevent wandering or damage. [Animal despawn rule][animal] · [Tame state][shared] · [Cargo persistence][chested] · [Health and equipment persistence][living]

With normal mob loot enabled, an adult's base death loot is **0–2 Leather**, with a Looting count bonus; babies do not produce this base loot. The separate cargo-drop path releases ordinary stored items and the attached Chest on death. They become loose drops, not a filled Chest item. Normally equipped Saddles use the equipment-drop rules, including the mob-loot gate and equipment-drop prevention effects; effects that prevent equipment drops can also suppress affected stored items. Recover items through the inventory when possible. [Leather loot][donkey-loot] · [Loot gates and death dispatch][living] · [Cargo drops][shared] · [Chest drop][chested] · [Equipped-item drops][mob]

## Related pages

- [Horse](Horse.md): shared taming, food table, and riding controls
- [Mule](Mule.md)
- [Donkey Spawn Egg](../items/DonkeySpawnEgg.md)
- [Saddle](../items/Saddle.md)
- [Transport](../mechanics/Transport.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. Registration, biome spawn entries, current interactions, chest menu/capacity, taming, breeding, loot, and persistence were inspected. No in-game taming, cargo, breeding, jump, or travel test was run. Modified tags, item components, and game rules can differ.

[chested]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/horse/AbstractChestedHorse.java
[shared]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java
[armor-tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/entity_type/can_wear_horse_armor.json
[entities]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java
[biomes]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/data/worldgen/biome/OverworldBiomes.java
[biome-spawns]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/data/worldgen/BiomeDefaultFeatures.java
[spawn-rules]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[animal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/Animal.java
[spawn-block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/animals_spawnable_on.json
[biome-registry]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/data/worldgen/biome/BiomeData.java
[natural-spawner]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/NaturalSpawner.java
[attributes]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java
[taming]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/RunAroundLikeCrazyGoal.java
[food]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/item/horse_food.json
[lure]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/item/horse_tempt_items.json
[equip]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/equipment/Equippable.java
[menu]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/inventory/HorseInventoryMenu.java
[server-riding]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java
[entity]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Entity.java
[living]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java
[underwater]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/entity_type/dismounts_underwater.json
[donkey]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/horse/Donkey.java
[breed-goal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/BreedGoal.java
[age]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/AgeableMob.java
[donkey-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/entities/donkey.json
[mob]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Mob.java
