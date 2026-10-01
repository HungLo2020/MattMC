# Pig

Pigs are passive animals that supply Porkchops and can be ridden using a Saddle and Carrot on a Stick. They have **10 health points (5 hearts)** and flee from danger rather than having an ordinary attack goal.

## Finding pigs

The active spawn registration uses the ground-animal rule: an eligible spawn block below and raw brightness above 8 for ordinary spawning. The bundled ground tag contains Grass Block. Pigs are explicitly present in Plains spawn data with weight 10 and group size 4; this is a spawn-table entry, not a guarantee of a herd in each Plains patch. Other biome entries are outside this selected location example.

For Creative testing, use the [Pig Spawn Egg](../items/PigSpawnEgg.md). The entity ID is `minecraft:pig`.

## Feeding and breeding

The bundled pig-food tag contains **Carrot, Potato, and Beetroot**. Hold one to attract pigs and feed ready adults to put them into love mode. After a successful pairing, the parents receive a 6,000-tick breeding cooldown, about five minutes while ticking at normal speed. Feeding a baby accelerates growth.

A Carrot on a Stick also attracts pigs, but it is not one of the breeding foods. Use harvested root crops instead. The offspring receives one parent's appearance variant rather than a guaranteed fixed variant.

## Saddling and riding

1. Equip a living **adult** with a [Saddle](../items/Saddle.md)
2. Interact without sneaking and without holding a pig breeding food to mount an unoccupied saddled pig
3. Hold a [Carrot on a Stick](../items/CarrotOnAStick.md) to become its controlling rider
4. Turn to steer; the implemented ridden input moves forward automatically while that control applies

A Saddle alone does not supply directional control. Using the Carrot on a Stick starts a temporary boost when no boost is already active; it consumes tool durability, not a Carrot from your inventory. Do not assume this behaves like a horse's ordinary movement controls.

To recover the Saddle, dismount and interact with usable Shears without sneaking. The general equipment-removal path requires the pig to have no passenger. If leash connections are present, shearing those has priority. Recover the dropped Saddle rather than assuming it is placed directly in your inventory.

## Drops and food

An adult Pig's bundled loot table gives **1–3 Raw Porkchops** before the Looting bonus. Burning at death or the applicable Fire Aspect loot condition turns that meat into Cooked Porkchops through the smelting loot function. The ordinary mob-loot rule must allow drops; babies do not pass that loot gate.

[Raw Porkchop](../items/RawPorkchop.md) provides 3 hunger points and 1.8 saturation; [Cooked Porkchop](../items/CookedPorkchop.md) provides 8 hunger points and 12.8 saturation. Cooking is a predictable way to obtain the better food without setting your enclosure on fire.

## Lightning hazard

Outside Peaceful difficulty, the Pig's lightning callback attempts to convert it into a Zombified Piglin. Keep an important breeding or riding setup protected; a pig should not be treated as unchanged after a lightning strike. This page does not establish a lightning-farm design.

## Related pages

- [Root crops](../blocks/RootCrops.md)
- [Saddle](../items/Saddle.md)
- [Carrot on a Stick](../items/CarrotOnAStick.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game breeding, riding, equipment, loot, or food test was run. Data packs, components, and game rules can change the described behavior.

- [Pig behavior, feeding, riding, and lightning conversion](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Pig.java)
- [Animal spawning and breeding](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Animal.java)
- [Spawn-placement registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/SpawnPlacements.java)
- [Plains spawn data](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome/plains.json)
- [Spawn ground tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/animals_spawnable_on.json)
- [Pig foods](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/pig_food.json)
- [Pig loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities/pig.json)
- [Baby and game-rule loot gate](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java)
- [Saddle interaction and removal](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Entity.java)
- [Interaction order and unoccupied shearing check](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Mob.java)
