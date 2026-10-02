# End Crystal

An **End Crystal** (`minecraft:end_crystal`) is a placeable explosive entity that can heal the [Ender Dragon](../mobs/EnderDragon.md) and participate in its respawn ritual. It is registered with an enchantment-style glint. Treat a placed crystal as an explosive hazard, not a storage block you can casually pick up again. [Item registration][item] · [Entity behavior][entity]

## Crafting

Craft one crystal from **7 Glass**, **1 Eye of Ender**, and **1 Ghast Tear**:

```text
Glass Glass Glass
Glass Eye of Ender Glass
Glass Ghast Tear Glass
```

This is a shaped Crafting Table recipe. Making the four crystals for a normal respawn therefore requires 28 Glass, four Eyes of Ender, and four Ghast Tears. [Recipe][recipe]

## Placement

Use the item on **Obsidian or Bedrock**. The block directly above the clicked base must be air. The placement also requires a **1×2×1 entity-clearance box** above that base to contain no entities. The current implementation does not separately require two air blocks; an older version's placement checklist should not replace these actual checks. [Placement handler][placement]

Successful placement creates a crystal centered above the base and consumes one item in ordinary Survival. Creative item-use restoration is handled by the game-mode path. Placement permissions and obstructions can still prevent an attempt. [Placement][placement] · [Server item-use handling][game-mode]

A crystal placed in the active End fight context asks that fight manager to check for a respawn arrangement. It does not summon a dragon by itself in an arbitrary dimension. See [Respawning the dragon](../mobs/EnderDragon.md#respawning-the-dragon) for the exact four-crystal layout and destructive rebuilding behavior.

## Destruction and healing

An ordinary damaging hit removes the crystal and, unless the damage was already explosion-tagged, creates a **power-6 explosion**. Power is an explosion-strength parameter, not a guaranteed damage value or destruction radius. The crystal refuses dragon-attributed damage and respects its base invulnerability checks. [Damage handling][entity]

Attacking a placed crystal does not return an End Crystal item through this path. Keep distance, cover, and nearby builds in mind. An existing explosion can destroy a crystal without triggering another independent crystal explosion in the checked damage handler. [Destruction][entity]

The dragon periodically selects a nearby crystal and heals while that crystal remains present. Breaking the currently linked crystal also requests damage against the dragon's head. Full healing and phase details belong in the [Ender Dragon guide](../mobs/EnderDragon.md#crystals-and-healing). [Dragon crystal handling][dragon]

## Sources and verification

Source-reviewed on 2026-10-02 at `3e85592c4c78ebb420302360667a6c230dc0318d`. No gameplay test of crafting, clearance, explosions, healing, or respawn placement was run.

Related: [Eye of Ender](EyeOfEnder.md) · [Ghast Tear](GhastTear.md) · [Ender Dragon](../mobs/EnderDragon.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L2229-L2231
[recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/end_crystal.json
[placement]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/EndCrystalItem.java
[entity]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/boss/enderdragon/EndCrystal.java
[game-mode]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java
[dragon]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/boss/enderdragon/EnderDragon.java
