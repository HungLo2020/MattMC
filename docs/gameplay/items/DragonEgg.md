# Dragon Egg

The **Dragon Egg item** (`minecraft:dragon_egg`) places the teleporting, gravity-affected [Dragon Egg block](../blocks/DragonEgg.md). Its item registration gives it **Epic rarity**. It is a trophy/display item, not a hatchable dragon pet or an ingredient in the dragon-respawn check. [Item registration][item] · [Block behavior][block] · [Respawn check][fight]

## Obtaining and placing

The normal first tracked Ender Dragon kill places the trophy block near the exit podium. Follow the [block guide's piston collection route](../blocks/DragonEgg.md#collecting-with-a-piston) to obtain its item without treating direct attacking as ordinary mining. Simply clicking the placed egg can teleport it instead.

MattMC's Vallumraptor breeding placeholder also uses this block, so the first boss trophy is not proof that no other Dragon Eggs can exist. That integration limitation is explained in the [Vallumraptor guide](../mobs/Vallumraptor.md#owner-commands-and-breeding).

Use the item to place a display egg on solid support. Once placed, its interaction and falling rules apply again; storing the item in a chest avoids accidentally teleporting the display block. The dragon's actual respawn materials are [End Crystals](EndCrystal.md), with a specific four-crystal portal arrangement.

## Sources and verification

Source-reviewed on 2026-10-02 at `3e85592c4c78ebb420302360667a6c230dc0318d`; no acquisition, placement, or interaction gameplay test. The canonical block page contains the complete supporting collection and teleport sources.

Related: [Dragon Egg block](../blocks/DragonEgg.md) · [Ender Dragon](../mobs/EnderDragon.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L586
[block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/DragonEggBlock.java
[fight]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/dimension/end/EndDragonFight.java
