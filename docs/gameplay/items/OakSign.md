# Oak Sign

Oak Sign (`minecraft:oak_sign`) is the stackable item for both an Oak standing sign and an Oak wall sign. It stacks to **16**. Use the [Signs and Hanging Signs guide](../blocks/Signs.md) for the shared placement and text rules. [Registration][items] · [Paired placement][paired-item]

## Obtaining

At a crafting table, arrange **six Oak Planks in two full rows** with **one Stick centered below** to make **three Oak Signs**. Ordinary hand mining can recover one from either placed form; an axe is faster. Silk Touch is unnecessary. [Recipe][recipe-oak-sign] · [Loot][loot-oak-sign] · [Shared wall loot][wall-loot] · [Axe tag][axe-tag] · [Registration][blocks]

## Usage

Place it on a solid support to make a standing sign, or against a solid side support to make a wall sign. It supports four lines on each face, with separate text, dye color, and glow. Honeycomb locks both faces. See [editing and decoration](../blocks/Signs.md#writing-and-editing-both-faces) and [waterlogging](../blocks/Signs.md#waterlogging). [Standing][standing] · [Wall][wall] · [Text and save state][sign-entity]

## Behavior

Text remains saved while the sign is placed. Its ordinary drop is a **blank item** and does not retain text, dye, glow, or wax. An axe does not unwax signs; breaking and replacing resets the writing. Losing its required support also breaks this sign. [Loot][loot-oak-sign] · [Axe actions][axe] · [Support checks][standing] · [Wall support][wall]

## Related pages

- [All ordinary sign variants](../blocks/Signs.md#ordinary-sign-variants)
- [Oak Hanging Sign](OakHangingSign.md)
- [Wood construction](../blocks/WoodConstruction.md)

## Sources and verification

Reviewed at `7af3a1594956f41ed57c3bf67d11ce006e61530f` on 2026-10-02. Source and bundled-data review only; no in-game placement, editing, crafting, support-removal, or harvesting test.

[items]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/Items.java#L1419-L1511
[paired-item]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/StandingAndWallBlockItem.java
[recipe-oak-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/recipe/crafting/oak_sign.json
[loot-oak-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/oak_sign.json
[wall-loot]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/Blocks.java#L7264-L7272
[axe-tag]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[blocks]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/Blocks.java#L1302-L1750
[standing]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/StandingSignBlock.java
[wall]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/WallSignBlock.java
[sign-entity]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/entity/SignBlockEntity.java
[axe]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/AxeItem.java
