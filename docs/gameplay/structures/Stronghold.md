# Stronghold

A Stronghold (`minecraft:stronghold`) is an underground network of stone-brick rooms and corridors with an End portal room. Find it to prepare a route to the [End](../dimensions/End.md), or explore its possible libraries and loot rooms before committing to that trip.

## Finding a stronghold

1. Craft Eyes of Ender from one Ender Pearl and one Blaze Powder per eye. One Blaze Rod crafts into two powder; [Nether Fortresses](NetherFortress.md) provide a route to Blazes
2. In the Overworld, use an eye while not targeting an End Portal Frame. It searches the current dimension for the bundled eye-location tag, which contains strongholds
3. Follow the eye's direction and repeat as you travel. When the target is far away, an eye moves toward a point only 12 blocks ahead and 8 blocks above its launch position; one throw does not fly the whole journey
4. Near the located area, make a controlled descent and look for the structure. The search supplies a structure location, not a cleared entrance or a safe digging route

A launched eye has a **four-in-five** chance to drop back as an item and a **one-in-five** chance to disappear instead. Recover drops where possible, but bring spare eyes for locating as well as filling the portal. If the structure search finds nothing, this implementation returns before launching or consuming the held eye.

With permission level 2, `/locate structure minecraft:stronghold` is an alternative. Run it in the Overworld for the normal-world route and read the [coordinate cautions](Structures.md#finding-a-structure). Its result does not give a safe underground Y.

## Where generation is eligible

The bundled stronghold definition permits the Overworld biome tag. Its placement set requests 128 candidate positions in concentric rings, with a separate preferred-biome tag influencing their positions. Preferred biomes are a placement bias, not the complete allowed-biome list.

Generation still depends on structure settings and valid biome checks; the candidate count is not a promise of 128 accessible, untouched portal rooms in every customized or existing world. The normal-world route is the Overworld. The bundled Primordial Caves biomes are not in this allowed tag.

The stronghold generator retries its piece layout until it contains a portal-room piece, then places the assembled structure below sea level. This verifies an intended portal room in newly generated layouts; it does not guarantee an easy path through every corridor or that an existing room has survived other changes to the world.

## Exploring the rooms

Mark turns and the way back as you explore. The room pool includes stairs, crossings, prison halls, chest corridors, libraries, and a portal room. Optional rooms vary; do not assume every stronghold contains a library.

### Loot worth looking for

Different chests use different bundled loot tables:

| Chest type | Main weighted pool | Additional trim roll |
| --- | --- | --- |
| Corridor | 2–3 rolls; possibilities include Ender Pearls, Diamonds, ingots, food, iron equipment, horse armor, an enchanted book, and the Otherside music disc | One Eye Armor Trim Smithing Template with a 1-in-10 chance |
| Crossing | 1–4 rolls; possibilities include ingots, Redstone, Coal, food, an Iron Pickaxe, and an enchanted book | None in this table |
| Library | 2–10 rolls; possibilities include Books, Paper, an Empty Map, a Compass, and an enchanted book | One Eye Armor Trim Smithing Template guaranteed by this unchanged table |

These are loot-table rolls, not guaranteed numbers of distinct item types. A guaranteed library-template roll is conditional on finding a generated library chest using that table; it does not guarantee a library in the structure. Libraries also generate Bookshelves and patches of Cobwebs.

## Preparing the portal room

The room contains lava, a Silverfish spawner, and a ring of **12 End Portal Frames**. Each frame gets its own random chance to start with an eye, so count the empty sockets when you arrive. Bring up to 12 eyes for filling an entirely empty ring, in addition to what you spend searching.

Clear hazards and establish your retreat before using eyes on the empty frames. A complete, correctly facing ring with eyes activates a 3×3 portal interior. If the generated ring already has eyes in all 12 frames, the room generator places an active portal immediately.

Do not enter just to test it. End arrival and return rules, including the arrival platform and valid respawn points, are covered in the [End guide](../dimensions/End.md). This page does not establish a Survival recipe or collection method for End Portal Frames.

## Hazards while searching

- **Silverfish in the portal room:** the generator configures its spawner for Silverfish. Deal with the room before standing beside the frame to count or place eyes
- **Infested masonry:** the stronghold's stone-brick selection includes Infested Stone Bricks. Breaking an infested block can release a Silverfish when block drops are enabled. The checked prevention tag contains Silk Touch
- **Lava:** the portal room explicitly generates lava, including beneath the portal area. Check the floor before dropping down or breaking nearby blocks
- **Dead ends and elevation changes:** explore methodically and retain a marked exit rather than assuming a locating eye traces a route through the rooms

## Related pages

- [Structures](Structures.md)
- [Nether Fortress](NetherFortress.md)
- [End](../dimensions/End.md)
- [Enchanting](../enchanting/Enchanting.md)
- [Commands](../commands/Commands.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game eye flight, stronghold generation, chest-opening, Silverfish, or portal-activation test was performed. Claims describe active code and bundled data, not every seed, customized world, or older generated structure.

- [Registered structure type](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/levelgen/structure/StructureType.java), [stronghold definition](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/structure/stronghold.json), [allowed biome tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/stronghold.json), and [its Overworld biomes](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/worldgen/biome/is_overworld.json)
- [Ring placement settings](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/structure_set/strongholds.json), [preferred biomes](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/worldgen/biome/stronghold_biased_to.json), and [ring calculation and biome bias](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/chunk/ChunkGeneratorStructureState.java#L98-L161)
- [Eye recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/ender_eye.json), [structure-location tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/worldgen/structure/eye_of_ender_located.json), [eye use and activation](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/EnderEyeItem.java), and [flight and item survival](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/projectile/EyeOfEnder.java#L73-L113)
- [Located structure-start position](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L277-L296) and [placement-coordinate calculation](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/levelgen/structure/placement/StructurePlacement.java#L95-L97)
- [Portal-room requirement and underground placement](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/StrongholdStructure.java), [room pool, chest assignment, masonry, frames, lava, and spawner](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/StrongholdPieces.java), and [complete portal-ring pattern](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/EndPortalFrameBlock.java#L83-L115)
- [Corridor loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/chests/stronghold_corridor.json), [crossing loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/chests/stronghold_crossing.json), and [library loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/chests/stronghold_library.json)
- [Infested-block break behavior](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/InfestedBlock.java#L54-L68) and [Silk Touch prevention tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/enchantment/prevents_infested_spawns.json)
