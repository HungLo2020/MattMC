# Eye of Ender

An Eye of Ender (`minecraft:ender_eye`) helps locate a [Stronghold](../structures/Stronghold.md) and activates its End portal when used in an empty frame. Throwing one does not teleport you; that is the [Ender Pearl's](EnderPearl.md) role.

## Crafting

Combine **one Ender Pearl and one Blaze Powder** in a shapeless recipe to make **one eye**. One [Blaze Rod](BlazeRod.md) makes two powder, enough for two eyes if you also have two pearls.

Reserve eyes for both searching and filling the portal. A generated portal has 12 frames with randomly prefilled eyes, so it may need up to 12 more. The eyes lost during your search are additional to that supply.

## Locating a stronghold

Use an eye while not targeting an End Portal Frame. The server searches the current dimension using the eye-location structure tag; the bundled tag contains `minecraft:stronghold`. For the normal-world route, search in the Overworld. World-generation settings and applicable structures determine whether a target can be found.

If a target is found, ordinary Survival use consumes one eye and launches a separate flying eye toward it. At a horizontal target distance greater than 12 blocks, the flight target is only 12 blocks ahead and 8 blocks above the launch position. Follow the direction and throw again as needed; one eye does not lead you all the way there.

At the end of a flight, an eye has a **four-in-five chance to drop as an item** and a **one-in-five chance to disappear**. Recover dropped eyes before continuing where it is safe to do so. If no structure is found, the checked code returns before launching an eye or consuming the held one.

The located point is a structure-placement position, not the exact portal room or a safe underground entrance. See [finding a stronghold](../structures/Stronghold.md#finding-a-stronghold) for the full expedition route. Inserting an eye in a frame and throwing one to locate a structure are separate actions.

## Filling portal frames

Use an eye directly on an End Portal Frame with an empty socket. In ordinary Survival use, that fills the frame and consumes one eye from the held stack. An already-filled frame does not accept another through this interaction.

After insertion, the game checks for a complete, correctly facing ring of filled frames. A matching ring creates a **3×3 End portal** in its center. Activation replaces existing blocks in those nine interior positions with portal blocks, so keep the interior clear of belongings and temporary construction.

This describes using existing frames, such as those in a generated stronghold. It does not establish a Survival crafting recipe or collection method for End Portal Frames.

Prepare before stepping into an active portal. The [End guide](../dimensions/End.md) explains arrival, exit, and respawn behavior; locating or activating a portal does not make it safe to enter.

## Other verified recipes

- **Ender Chest:** surround one eye with eight Obsidian to craft one chest
- **End Crystal:** place three Glass across the top, Glass–eye–Glass across the middle, and Glass–Ghast Tear–Glass across the bottom to craft one crystal

These are crafting recipes, not instructions for safely placing or using either result.

## Related pages

- [Ender Pearl](EnderPearl.md)
- [Blaze Powder](BlazePowder.md)
- [Stronghold](../structures/Stronghold.md)
- [End](../dimensions/End.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game eye flight, frame insertion, portal activation, or crafting test was performed. The guide describes the checked item implementation and bundled recipes/tags; modified world data can change the available target or recipes.

- [Item registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L1792); [Eye recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/ender_eye.json); [Powder recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/blaze_powder.json)
- [Searching, consumption, frame insertion, and interior replacement](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/EnderEyeItem.java); [Eye-location tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/worldgen/structure/eye_of_ender_located.json); [Flight and recovery chance](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/projectile/EyeOfEnder.java#L73-L113)
- [Located start-chunk position](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L277-L296); [Placement-coordinate calculation](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/levelgen/structure/placement/StructurePlacement.java#L95-L97); [Generated frames and eyes](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/StrongholdPieces.java#L814-L848); [Required frame pattern](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/EndPortalFrameBlock.java#L83-L115)
- [Stack consumption](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/ItemStack.java#L1072-L1080); [Server frame-use handling](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L379-L391); [Ender Chest recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/ender_chest.json); [End Crystal recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/end_crystal.json)
