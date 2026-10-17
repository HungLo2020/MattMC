# Strider Spawn Egg

The **Strider Spawn Egg** (`minecraft:strider_spawn_egg`) places a [Strider](../mobs/Strider.md). Its stored entity type is `minecraft:strider`. [Registration][strider-egg-item]

## Obtaining

This egg is listed in the [inventory item browser](../mechanics/InventoryBrowser.md), which provides an insertion route in **Creative** in MattMC. That listing is separate from [natural Nether spawning and breeding](../mobs/Strider.md#obtaining); it is not evidence of a natural egg drop. [Category entry][strider-egg-category]

## Usage

Use it on a suitable block face to request a Strider spawn. Successful ordinary placement consumes one egg unless the user's game mode supplies infinite materials. Using a matching egg on an existing Strider instead follows the mob-interaction path to create a **baby**. This does not require the adult to enter love mode. [Block placement and consumption][egg-use] · [Mob interaction caller][egg-interaction] · [Matching egg and baby creation][egg-baby]

## Behavior

Spawn-egg placement is distinct from the natural spawn-list checks. Using an egg on a Spawner is a separate action governed by MattMC's spawner-enabled server check; see [Spawners](../blocks/MonsterSpawner.md) before changing one. For care, equipment and passenger restrictions, use the [Strider guide](../mobs/Strider.md). [Egg dispatch][egg-use]

## Notes

Source-reviewed at `bb9a8a060da02b64f23508b77794fb0f79307de4` on 2026-10-02. No in-game egg placement, Spawner or offspring test was run. Return to [Items](Items.md).

[strider-egg-item]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/Items.java#L1969
[strider-egg-category]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2098
[egg-use]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L49-L128
[egg-interaction]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/Mob.java#L1088-L1103
[egg-baby]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L184
