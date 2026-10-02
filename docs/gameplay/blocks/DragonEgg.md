# Dragon Egg

The **Dragon Egg** (`minecraft:dragon_egg`) is a placeable trophy block with teleporting interaction and falling-block behavior. It emits **light level 1** and has hardness 3 and blast resistance 9. It is not the item used to respawn the Ender Dragon. [Registration][block] · [Behavior][egg] · [Fight respawn check][fight]

## Obtaining the trophy

The normal End fight manager places a Dragon Egg near the exit podium after its **first tracked dragon kill**. Later tracked kills do not repeat that reward. The egg is placed as a block rather than emitted by an ordinary dragon item-loot table. [Fight reward][fight]

This is not a one-egg-per-world guarantee in MattMC: the active [Vallumraptor breeding placeholder](../mobs/Vallumraptor.md#owner-commands-and-breeding) also returns the ordinary Dragon Egg state. It does not hatch a Vallumraptor or establish a separate dragon-breeding system. Commands and custom data can create other eggs too. [Placeholder method][vallum]

## Interaction and teleporting

**Attacking the block or using it without an item attempts to teleport it.** Rather than expecting normal left-click mining to leave the egg in place, plan a collection method that does not invoke that interaction.

The teleport method tries up to **1,000 candidate positions**. Each candidate's offset is the difference of two random integers, producing horizontal offsets from −15 to +15 and vertical offsets from −7 to +7. The target must be air, inside the world border, and within build height. The search can fail if no candidate passes; these ranges are not a guaranteed destination distance. [Teleport callbacks and selection][egg]

The destination is not required to have support underneath. A teleported egg can therefore begin falling. Keep track of nearby ledges, water, and the void instead of assuming it stays safely on the podium.

## Collecting with a piston

A source-supported collection route is to arrange a **Piston facing the placed egg**, then power it. The egg's registered piston reaction is **DESTROY**; the piston destruction path calls block resource drops before removing it, and the egg's loot table supplies **one Dragon Egg item** under normal block-drop conditions. Collect that item from a safe platform. This is not a movable-piston-block behavior and does not require Silk Touch. [Piston reaction][block] · [Piston destruction/drop path][piston] · [Egg loot][loot]

Avoid attacking the egg while setting up the piston, and check where the item could land before activating it. This guide does not rely on an untested torch-placement trick or claim an automatic collection farm.

## Falling and display

The Dragon Egg inherits the current Falling Block scheduled-tick path, with a **five-tick delay** after placement/update scheduling. It becomes a falling entity when the block below is free under that implementation's air/fire/liquid/replaceable checks. A solid display pedestal is therefore more predictable than a suspended egg. [Delay override][egg] · [Falling implementation][falling]

It has no hatch or pet-creation method in the checked block behavior. Respawning the dragon uses [four End Crystals](../items/EndCrystal.md) around the exit portal instead. Store the trophy somewhere safe before deliberately rebuilding the fight arena.

## Sources and verification

Source-reviewed on 2026-10-02 at `3e85592c4c78ebb420302360667a6c230dc0318d`. No gameplay test of trophy generation, teleport selection, falling, piston collection, or display was run. Game rules, world changes, and custom data can alter drops and surroundings.

Related: [Dragon Egg item](../items/DragonEgg.md) · [Ender Dragon](../mobs/EnderDragon.md) · [Dinosaur eggs](DinosaurEggs.md) · [Blocks](Blocks.md)

[block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L2570-L2580
[egg]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/DragonEggBlock.java
[fight]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/dimension/end/EndDragonFight.java
[vallum]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexscaves/server/entity/living/VallumraptorEntity.java#L600-L602
[piston]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/dragon_egg.json
[falling]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/FallingBlock.java
