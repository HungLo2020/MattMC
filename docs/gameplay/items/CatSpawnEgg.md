# Cat Spawn Egg

The **Cat Spawn Egg** (`minecraft:cat_spawn_egg`) is a Creative tool for placing a [Cat](../mobs/Cat.md). Its item is registered with the Cat entity type and appears in the Creative spawn-egg selection. [Item registration][items] · [Creative entry][creative]

## Use

Use the egg on an ordinary block with room for the animal. The normal block-use path places the Cat at the clicked position or adjacent face, then initializes its variant from the current spawn conditions. A successful ordinary placement consumes one egg in Survival; Creative use preserves the held supply. [Egg placement][egg] · [Positioned entity finalization][entities] · [Variant initialization][cat] · [Consumption helper][stack]

A Cat placed this way is not automatically tamed. Follow the [Cat guide](../mobs/Cat.md) for coat rules, raw-fish taming, owner controls, breeding, and gifts. Modified egg components can supply different entity data. [Default owner state][tamable] · [Egg components][egg]

Using the matching egg directly on an existing Cat creates a **kitten through that Cat's offspring factory**. With an ordinary egg, the kitten copies that parent's coat; if the parent is tame, it also inherits the parent's owner and tame state. This route is separate from ordinary two-parent food breeding. [Live egg interaction][mob] · [Matching-mob baby helper][egg] · [Cat offspring factory][cat]

Using the egg on a compatible Spawner configures its entity instead of placing a Cat. See [Monster Spawner](../blocks/MonsterSpawner.md) for the active settings and restrictions. [Spawner interaction][egg]

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. Item/Creative registration, block-use finalization, matching-mob baby creation, and variant/owner behavior were inspected. No in-game egg, kitten, Spawner, or variant test was run.

Related: [Cat](../mobs/Cat.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java
[creative]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[egg]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/SpawnEggItem.java
[entities]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java
[cat]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/Cat.java
[stack]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ItemStack.java
[tamable]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/TamableAnimal.java
[mob]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Mob.java
