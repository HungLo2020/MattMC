# Tube Coral Fan

**Tube Coral Fan** (`minecraft:tube_coral_fan`) places either a blue floor fan or the separate `minecraft:tube_coral_wall_fan` block state, depending on available support and placement direction. Both use this same item. [Registration][items] · [Shared placement][fan-item]

## Obtaining

Use **Silk Touch** to recover one fan from either living placed form. Shears alone give no fan, and the living forms have no correct-tool tier requirement. Reefs and [Warm Ocean Bone Meal](../blocks/Coral.md#bone-meal-for-plants-and-fans) provide source-backed acquisition routes. [Fan loot][loot-tube-coral-fan] · [Wall loot mapping][wall-loot] · [Properties][blocks]

## Usage

A floor fan needs support below; a wall fan needs a sturdy face behind it. Both can be waterlogged and need their stored or adjacent water to stay alive. Drying preserves the species and floor/wall form while producing dead coral, which does not revive when rewetted. Read the [shared Coral rules](../blocks/Coral.md#placement-and-structural-support). [Support][plant-base] [wall-base] · [Death callbacks][living-fan] [living-wall]

## Related pages

- [Coral](../blocks/Coral.md), [Tube Coral](TubeCoral.md), [Dead Tube Coral Fan](DeadTubeCoralFan.md), and [Items](Items.md)

## Sources and verification

Source-reviewed at `7af3a1594956f41ed57c3bf67d11ce006e61530f` on 2026-10-02. No gameplay test was run.

[items]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/Items.java#L882-L953
[fan-item]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/StandingAndWallBlockItem.java
[loot-tube-coral-fan]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/tube_coral_fan.json
[wall-loot]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/Blocks.java#L7264-L7272
[blocks]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/Blocks.java#L4830-L5164
[plant-base]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/BaseCoralPlantTypeBlock.java
[wall-base]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/BaseCoralWallFanBlock.java
[living-fan]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/CoralFanBlock.java
[living-wall]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/CoralWallFanBlock.java
