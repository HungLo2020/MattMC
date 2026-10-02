# Llama

A **Llama** is a pack animal that can carry a Chest and follow a leashed Llama in a caravan. You can mount one to tame it, but ordinary Saddles do not fit and a rider cannot steer it like a [Horse](Horse.md). Use a [Lead](../items/Lead.md) or a Hay Bale lure to guide it. [Registered behavior][llama] · [Saddle eligibility][saddle-tag] · [Rider control][shared] · [Caravan following][caravan] · [Lure item][llama-lure]

## Finding Llamas

Llamas are registered as `minecraft:llama`. The bundled Windswept Hills, Windswept Gravelly Hills, and Windswept Forest biome entries have groups of **4–6**; Savanna Plateau has groups of **4**. These are spawn-list group sizes, not a guaranteed herd at every location. [Registration][entities] · [Hills][wind-hills] · [Gravelly hills][wind-gravel] · [Forest][wind-forest] · [Plateau][savanna-plateau]

Natural ground spawning uses the animal predicate: Grass Block below in the bundled spawnable-ground tag, raw brightness above 8, and the normal placement and spawn checks. Biome resources are loaded into the worldgen registry and consumed by the creature-spawning code. [Placement registration][spawn-rules] · [Animal predicate][animal] · [Ground tag][spawn-block] · [World loading][world-loader] · [Biome registry loader][registry-loader] · [Spawn dispatch][natural-spawner]

A normally initialized Llama has **15–30 maximum health points**, or **7.5–15 hearts**. Its strength determines cargo capacity and is separate from coat color. [Attribute registration][attributes] · [Health initialization][chested] · [Health range][shared] · [Strength and appearance][llama]

[Trader Llamas](TraderLlama.md) are a separate merchant-associated type with additional behavior and a timed despawn rule. The [Llama Spawn Egg](../items/LlamaSpawnEgg.md) places the ordinary type in Creative.

## Taming and feeding

Mount an unoccupied adult with an **empty hand**. If it bucks you off, mount again until hearts indicate taming. Llamas use the shared riding-taming goal, but their maximum temper is **30**, not the Horse's 100. The actual taming roll succeeds when a random value from 0–29 is below its current temper; a failed player-rider roll adds 5, capped at 30. Feeding can improve temper, but does not replace the successful taming roll. [Mount interaction][shared] · [Llama temper limit][llama] · [Taming goal][taming]

Feed while the animal is **unoccupied and you are not using Sneak/Crouch**. Health values below are points, with **2 points = 1 heart**. [Llama feeding][llama] · [Food tag][llama-food]

| Food | Health restored, up to maximum | Baby growth advanced | Temper increase | Starts eligible breeding love mode |
| --- | ---: | ---: | ---: | --- |
| Wheat | 2 | 10 seconds | 3 | No |
| Hay Bale | 10 | 90 seconds | 6 | Yes |

Growth advances use fixed amounts of game time at the normal tick rate and stop at adulthood. A successful feed consumes one item in ordinary Survival. A tame, healthy adult does not consume Wheat without another benefit. Hay Bales can put tame adults into love when their breeding age is zero. Only **Hay Bales** are in the bundled Llama temptation tag; holding Wheat does not provide that lure. [Feeding rules][llama] · [Consumption][shared] · [Growth conversion][age] · [Temptation tag][llama-lure]

## Chest capacity and carpets

After taming an adult, dismount and interact with an ordinary [Chest](../blocks/Chest.md), without Sneak/Crouch, to attach one. Open the animal's inventory with **Sneak/Crouch + interact**, or use Inventory while mounted. Cargo occupies three rows, with the number of columns set by the animal's strength. [Chest interaction][chested] · [Inventory access and row count][shared] · [Column count][llama] · [Equipment and cargo menu][menu] · [Mounted inventory command][server-riding]

| Strength | Cargo slots |
| ---: | ---: |
| 1 | 3 |
| 2 | 6 |
| 3 | 9 |
| 4 | 12 |
| 5 | 15 |

Inspect the inventory after attaching the Chest to see the actual capacity. New Llamas usually draw strength from 1–3; a separate 4% branch draws from 1–5, so strength 4 and 5 are possible but uncommon. A second Chest does not add storage, and this interaction does not accept a Trapped Chest. There is no ordinary interact-to-remove-Chest action. [Strength initialization][llama] · [Chest handling][chested]

A colored **wool Carpet** fits the body equipment slot. Equip one through the inventory or by interacting with a tame adult whose body slot is empty. It changes decoration without adding cargo capacity, armor attributes, or steering. Moss and Pale Moss Carpets do not have this Llama equipment component. Use [White Carpet](../items/WhiteCarpet.md) and [Wool and Carpet](../blocks/WoolAndCarpet.md) for recipes, colors, and placed-block behavior. [Carpet item components][items] · [Decoration component][equip] · [Body-slot interaction][shared]

You can retrieve the Carpet through the inventory or the eligible Shears equipment interaction. Shearing requires usable Shears on an unoccupied animal without Sneak/Crouch; removing leash connections takes priority. It removes equipped decoration, not the Chest. Horse Armor and ordinary Saddles are not accepted by their bundled entity tags. [Shearing dispatch][entity] · [Unoccupied gate][mob] · [Armor tag][armor-tag] · [Saddle tag][saddle-tag]

## Leading a caravan

Leash one Llama and walk it past other unleased Llamas. Nearby eligible Llamas can join behind it, including [Trader Llamas](TraderLlama.md). Followers do not need to be tamed to join, although taming is needed for normal cargo access. A follower looks for a nearby chain tail or leashed leader, and the chain must remain connected to a leashed Llama. [Caravan entry and continued checks][caravan]

For one chain behind a single leashed leader, the checked recursion permits **nine followers**, or **ten Llamas including the leader**. This comes from the actual entry and continuation checks, not just the code's limit constant. It is a source-derived bound, not an in-game formation test. Do not expect every nearby animal to join immediately: a prospective head too close to the follower is rejected, and pathfinding or separation can break the chain. [Caravan checks][caravan]

For a small, **untested** cargo example:

1. Tame two adults, attach Chests, and inspect their available slots
2. Leash one and begin walking along a short, clear land route
3. Let the second follow behind; keep it close enough to maintain the chain
4. Stop and check both animals before loading valuable supplies or extending the route

The goal follows the preceding animal with space between them. Large separation can eventually end following even after its temporary catch-up behavior. Keep the route clear and watch for animals left behind; a caravan is not a persistent teleporting convoy. [Navigation and separation handling][caravan]

## Breeding and behavior

Use two **tame adults at full health**, with no riders and neither riding another entity. Feed each a Hay Bale when outside the breeding cooldown, then let them approach. The baby is untamed and normally needs **24,000 game ticks**, about **20 minutes**, to grow; each parent receives **6,000 game ticks**, about **5 minutes**, of cooldown. Food speeds baby growth. [Parent requirements][shared] · [Llama food and mating][llama] · [Breeding goal][breed-goal] · [Birth and cooldown][animal] · [Age ticking][age]

The baby's strength is chosen from **1 through the stronger parent's strength**, then has a **3% chance** to increase by one, capped at 5. A strong parent therefore does not guarantee a strong baby. Coat comes from one parent; health, speed, and jump attributes use the shared inherited-variation calculation. The ordinary AI can also pair an ordinary Llama with a Trader Llama, producing an ordinary Llama; see the Trader guide for that type's offspring and retention rules. [Offspring creation][llama] · [Attribute inheritance][shared] · [Partner-class selection][breed-goal] · [Trader factory][trader-llama]

Llamas can spit at attackers and independently target **untamed Wolves**. The spit projectile applies **1 point of base damage** before the target's defenses. Do not treat decorative Carpet as armor or assume taming prevents all retaliation. [Target goals and spit launch][llama] · [Projectile damage][spit]

## Persistence and drops

Ordinary Llamas do not use distance-based animal despawning. Their tame state, owner, strength, coat, Chest, cargo, and equipment are saved; the temporary caravan head/tail references are not part of those saved Llama fields. Check the animals and re-form a caravan when returning to them. [Animal despawn rule][animal] · [Shared save data][shared] · [Strength and coat save data][llama] · [Chest persistence][chested] · [Equipment persistence][living]

With normal mob loot enabled, an adult's base death loot is **0–2 Leather**, with a Looting count bonus; babies do not produce that base loot. The separate container-drop path releases the attached Chest and ordinary cargo as loose items. Player-equipped Carpets follow the equipment-loot gate and drop-prevention rules; affected stored items can also be suppressed by a drop-prevention effect. Retrieve supplies and decoration through the inventory where possible. [Leather loot][llama-loot] · [Death dispatch and loot gate][living] · [Cargo drops][shared] · [Chest drops][chested] · [Equipment drops][mob]

## Related pages

- [Trader Llama](TraderLlama.md)
- [Camel](Camel.md)
- [Llama Spawn Egg](../items/LlamaSpawnEgg.md)
- [Horse](Horse.md)
- [Transport](../mechanics/Transport.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. Active registration, biome resources, goal dispatch, taming/food, cargo, decoration, caravan recursion, breeding, loot, and saved state were inspected. No in-game taming, caravan, breeding, capacity, or travel test was run. Data packs and modified item components can change the described defaults.

[llama]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/horse/Llama.java
[saddle-tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/entity_type/can_equip_saddle.json
[shared]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java
[caravan]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/LlamaFollowCaravanGoal.java
[llama-lure]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/item/llama_tempt_items.json
[entities]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java
[wind-hills]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/windswept_hills.json
[wind-gravel]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/windswept_gravelly_hills.json
[wind-forest]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/windswept_forest.json
[savanna-plateau]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/savanna_plateau.json
[spawn-rules]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[animal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/Animal.java
[spawn-block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/animals_spawnable_on.json
[world-loader]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/WorldLoader.java
[registry-loader]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/resources/RegistryDataLoader.java
[natural-spawner]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/NaturalSpawner.java
[attributes]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java
[chested]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/horse/AbstractChestedHorse.java
[taming]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/RunAroundLikeCrazyGoal.java
[llama-food]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/item/llama_food.json
[age]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/AgeableMob.java
[menu]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/inventory/HorseInventoryMenu.java
[server-riding]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java
[items]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java
[equip]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/equipment/Equippable.java
[entity]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Entity.java
[mob]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Mob.java
[armor-tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/entity_type/can_wear_horse_armor.json
[breed-goal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/BreedGoal.java
[trader-llama]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/horse/TraderLlama.java
[spit]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/projectile/LlamaSpit.java
[living]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java
[llama-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/entities/llama.json
