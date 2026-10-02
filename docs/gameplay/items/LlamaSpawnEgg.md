# Llama Spawn Egg

The **Llama Spawn Egg** (`minecraft:llama_spawn_egg`) is a Creative tool for placing a [Llama](../mobs/Llama.md). It is actively registered with that entity type and appears in the Creative spawn-egg selection. [Item registration][items] · [Creative entry][creative]

## Use

Use the egg on an ordinary block to place its animal at the clicked position or adjacent face, depending on collision shape. Leave suitable room. Successful ordinary placement consumes one egg in Survival; Creative use preserves the held supply. [Block placement][egg] · [Consumption helper][stack]

Using the matching egg directly on that mob can create a **baby** through a separate offspring interaction. This does not require the normal two-parent breeding procedure. [Egg offspring path][egg] · [Active mob interaction dispatch][mob]

An ordinary spawned Llama is not automatically tamed, equipped with cargo storage, or controllable by a rider. Follow the [Llama guide](../mobs/Llama.md) for taming, strength-based cargo, Carpets, and caravans. [Llama attributes and behavior][llama] · [Default tame state and controls][shared]

Using the egg on a compatible Spawner configures its entity instead of placing an animal. See [Monster Spawner](../blocks/MonsterSpawner.md) for the active settings and restrictions. Modified egg components can alter the entity data. [Spawner and entity-component handling][egg]

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. Item/Creative registration, block use, matching-mob offspring dispatch, and relevant species restrictions were inspected. No in-game egg, baby-spawn, or Spawner test was run.

[items]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java
[creative]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[egg]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/SpawnEggItem.java
[stack]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ItemStack.java
[mob]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Mob.java
[llama]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/horse/Llama.java
[shared]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java
