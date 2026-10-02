# Ocelot Spawn Egg

The **Ocelot Spawn Egg** (`minecraft:ocelot_spawn_egg`) is a Creative tool for placing an [Ocelot](../mobs/Ocelot.md). It is actively registered with that entity type and appears in the Creative spawn-egg selection. [Item registration][items] · [Creative entry][creative]

## Use

Use the egg on an ordinary block with room for the animal. Placement uses the clicked position or adjacent face according to the block's collision shape. Successful ordinary placement consumes one egg in Survival; Creative use preserves the held supply. [Placement][egg] · [Consumption helper][stack]

The ordinary spawned animal starts **untrusting**. Use the [Ocelot guide](../mobs/Ocelot.md#building-trust) for the raw-fish approach and trust interaction. Trust never turns it into a Cat or grants Cat ownership controls. [Default trust and interaction][ocelot]

Using the matching egg directly on an Ocelot invokes its offspring factory and creates a **baby Ocelot**, also untrusting by default. It does not require two breeding parents and does not copy a trusting parent's relationship state. Modified egg components can alter entity data. [Live mob interaction][mob] · [Egg baby helper][egg] · [Offspring factory and default trust][ocelot]

Using the egg on a compatible Spawner configures its entity instead of placing an Ocelot. See [Monster Spawner](../blocks/MonsterSpawner.md) for the active restrictions; the egg does not prove that every location supports natural Ocelot spawning. [Spawner interaction][egg]

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. Item/Creative registration, ordinary placement, trust default, and matching-mob offspring dispatch were inspected. No in-game egg, trust, baby-spawn, or Spawner test was run.

Related: [Ocelot](../mobs/Ocelot.md) · [Cat](../mobs/Cat.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java
[creative]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[egg]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/SpawnEggItem.java
[stack]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ItemStack.java
[ocelot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/Ocelot.java
[mob]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Mob.java
