# Dead Tube Coral

**Dead Tube Coral** (`minecraft:dead_tube_coral`) is the dead upright coral plant. It is a small supported decoration, separate from a Dead Tube Coral Block or Dead Tube Coral Fan. [Registration][blocks] [items]

## Obtaining

Living Tube Coral becomes this form after a qualifying dry death check. To recover the placed dead plant, use an **unbroken pickaxe with Silk Touch**. The correct-tool gate and Silk Touch loot condition are both required; an unenchanted pickaxe or Shears alone does not collect it. [Death][living-plant] · [Loot][loot-dead-tube-coral] · [Tool gate][blocks] [pickaxe] [player-tool] [broken-tool]

## Usage

It needs a sturdy upper face beneath it, but no water to remain dead. It can be waterlogged without reviving. See [Coral support](../blocks/Coral.md#placement-and-structural-support) and [dead-form rules](../blocks/Coral.md#drying-and-dead-forms); adding Bone Meal does not turn it back into living coral. [Dead plant/base behavior][dead-plant] [plant-base]

## Related pages

- [Coral](../blocks/Coral.md), [Tube Coral](TubeCoral.md), [Dead Tube Coral Block](DeadTubeCoralBlock.md), and [Items](Items.md)

## Sources and verification

Source-reviewed at `7af3a1594956f41ed57c3bf67d11ce006e61530f` on 2026-10-02. No gameplay test was run.

[blocks]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/Blocks.java#L4830-L5164
[items]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/Items.java#L882-L953
[living-plant]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/CoralPlantBlock.java
[loot-dead-tube-coral]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/dead_tube_coral.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[player-tool]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[broken-tool]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[dead-plant]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/BaseCoralPlantBlock.java
[plant-base]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/BaseCoralPlantTypeBlock.java
