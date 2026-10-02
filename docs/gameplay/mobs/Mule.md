# Mule

A **Mule** is a land mount obtained in ordinary Survival by breeding a [Horse](Horse.md) with a [Donkey](Donkey.md). It can carry one rider and, with a Chest attached, **15 cargo slots**. Mules cannot breed or wear Horse Armor. [Crossbreeding][horse] · [Reverse-parent cross][donkey] · [Cargo capacity][chested] · [Mating default][shared] · [Armor restriction][armor-tag]

## Obtaining a Mule

Mules are actively registered as `minecraft:mule`, but the bundled biome spawn lists do not provide a natural Mule population. The presence of a ground-spawn predicate alone does not make Mules spawn in the wild. Use a Horse–Donkey cross for the normal Survival route; the [Mule Spawn Egg](../items/MuleSpawnEgg.md) provides a Creative alternative. [Registration][entities] · [Biome entries][biomes] · [Spawn helpers][biome-spawns] · [Placement registration][spawn-rules]

For the cross, both parents must be **tame adults at full health**, carrying no rider and riding nothing. Dismount, heal them, and feed each a Golden Carrot, Golden Apple, or Enchanted Golden Apple while they are outside their breeding cooldown. Keep them near each other with space to approach. The result is a Mule regardless of which parent initiates breeding. [Parent requirements and golden food][shared] · [Breeding approach][breed-goal] · [Horse offspring][horse] · [Donkey offspring][donkey]

The newborn is **untamed** and begins with **24,000 game ticks** of growth, about **20 minutes** at the normal tick rate. The parents each receive a **6,000-game-tick** cooldown, about **5 minutes**. Feed the baby to shorten its growth, then tame it after adulthood. [Birth and cooldown][animal] · [Age progression][age] · [Default tame state and feeding][shared]

A bred Mule receives health, speed, and jump attributes from both parents with random variation. Better parents can help, but the result is not guaranteed to beat either parent. Maximum health uses the shared **15–30-point** range, or **7.5–15 hearts**. A Mule spawned on a block with its egg follows its spawn initialization instead of the Horse–Donkey inheritance calculation. [Inheritance and ranges][shared] · [Spawn initialization][chested] · [Active attributes][attributes]

## Taming, riding, and carrying supplies

Mules share the [Horse taming method and feeding table](Horse.md#taming-and-feeding). Mount an adult with an empty hand, and remount after bucking until hearts appear. Ordinary Carrots are valid food; the golden foods also lure the animal. Feed while dismounted and without Sneak/Crouch. [Shared behavior][shared] · [Taming goal][taming] · [Food tag][food] · [Lure tag][lure]

Equip a tame adult with a [Saddle](../items/Saddle.md) for steering and charged jumping. Add one ordinary [Chest](../blocks/Chest.md) by interacting with it while the Mule is unoccupied and you are not using Sneak/Crouch. Open the inventory with **Sneak/Crouch + interact**, or the Inventory key while mounted. Cargo occupies **three rows of five slots**, separate from the Saddle. [Current saddle interaction][equip] · [Saddle gate and inventory access][shared] · [Chest interaction][chested] · [Menu][menu] · [Mounted inventory command][server-riding]

The storage and Chest restrictions are identical to the [Donkey cargo guide](Donkey.md#adding-and-using-cargo-storage): only one ordinary Chest, no Horse Armor, and no ordinary interaction that removes the Chest. Unload valuable items through the inventory. The Saddle can be recovered through the inventory or eligible Shears interaction. [Chest handling][chested] · [Armor restriction][armor-tag] · [Equipment shearing][entity]

Use the [Horse riding controls](Horse.md#riding-and-equipment) for steering, jump charge, and dismounting. Leave room for both rider and mount, and avoid falls and prolonged submersion. A Mule provides the same cargo capacity as a Donkey; choose between individual animals by their useful health and travel performance. No comparative travel or jump trial was run. [Ridden movement and falls][shared] · [Drowning][living] · [Underwater dismount tag][underwater]

## Why Mules do not produce more Mules

Mules inherit a mating check that always returns false. Golden food can still heal them, help taming, accelerate baby growth, or show love particles on an eligible tame adult; those particles do not enable breeding. Produce another Mule by crossing another eligible Horse–Donkey pair. A matching spawn egg can create a baby through a separate interaction, which is not natural breeding. [Mating and feeding][shared] · [Mule offspring factory][mule] · [Spawn-egg offspring interaction][egg]

## Persistence and drops

Mules do not use ordinary distance-based animal despawning. The game saves their tame state, owner, temper, health, attributes, equipment, Chest flag, and cargo. Enclose or leash them to keep them nearby. [Animal despawn rule][animal] · [Shared save data][shared] · [Chest and cargo save data][chested] · [Health and equipment persistence][living]

With normal mob loot enabled, adults have **0–2 Leather** base death loot, with a Looting count bonus; babies do not produce this base loot. The separate container-drop path releases the attached Chest and ordinary cargo as loose drops. Equipped Saddles follow the normal equipment-loot gate and drop-prevention rules; drop-prevention effects can also suppress affected stored items. The [Donkey recovery guidance](Donkey.md#persistence-and-drops) applies equally to Mules. [Leather loot][mule-loot] · [Death dispatch and loot gate][living] · [Cargo drops][shared] · [Chest drops][chested] · [Equipment drops][mob]

## Related pages

- [Horse](Horse.md)
- [Donkey](Donkey.md)
- [Mule Spawn Egg](../items/MuleSpawnEgg.md)
- [Saddle](../items/Saddle.md)
- [Transport](../mechanics/Transport.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. Registration, absence from bundled natural biome entries, active crossbreeding and mating checks, normal interaction paths, equipment, cargo, loot, and persistence were inspected. No in-game breeding, taming, cargo, or travel test was run. Data packs, modified items, and game rules can change the described defaults.

[horse]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/horse/Horse.java
[donkey]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/horse/Donkey.java
[chested]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/horse/AbstractChestedHorse.java
[shared]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java
[armor-tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/entity_type/can_wear_horse_armor.json
[entities]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java
[biomes]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/data/worldgen/biome/OverworldBiomes.java
[biome-spawns]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/data/worldgen/BiomeDefaultFeatures.java
[spawn-rules]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[breed-goal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/BreedGoal.java
[animal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/Animal.java
[age]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/AgeableMob.java
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
[mule]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/horse/Mule.java
[egg]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/SpawnEggItem.java
[mule-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/entities/mule.json
[mob]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Mob.java
