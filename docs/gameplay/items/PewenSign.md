# Pewen Sign

Pewen Sign (`minecraft:pewen_sign`) is registered as a Sign item for the standing and wall Pewen forms, stacks to **16**, and appears in Creative. Its current placement integration is incomplete. [Item registration][pewen-items] · [Creative listing][creative]

## Obtaining

Its bundled shaped recipe uses **six Pewen Planks in two rows** and **one centered Stick below**, defining **three Pewen Signs**. See the [Pewen family guide](../blocks/Pewen.md#construction-and-recipes) for acquisition and plank-recipe limitations before treating this as a complete Survival route. [Recipe][recipe-pewen-sign]

## Usage and current limitation

The registered blocks use standard standing/wall sign classes with Oak's wood type. However, Pewen is absent from the Sign block entity's valid-block list. Normal placement reaches entity construction, whose validation throws for that state. This is an identified **source-level placement failure path**, not an in-game crash reproduction. Functioning text editing is not established for these Pewen forms. [Blocks][pewen-blocks] · [Entity type][entity-type] · [Placement dispatch][chunk-placement] · [Sign factory][sign] · [Constructor check][entity-validation]

The standing and wall forms each have a plain one-Pewen-Sign loot definition. Neither copies text, color, glow, or wax. Pewen is also absent from the standard sign tags used by axe mining; its name alone does not establish the usual speed bonus. These data rules do not resolve the placement problem. [Standing loot][loot-pewen-sign] · [Wall loot][loot-pewen-wall-sign] · [Standing tag][standing-tag] · [Wall tag][wall-tag] · [Axe tag][axe-tag]

## Related pages

- [Pewen sign integration details](../blocks/Signs.md#pewen-signs-incomplete-integration)
- [Pewen family](../blocks/Pewen.md)
- [Pewen Hanging Sign](PewenHangingSign.md)
- [Standard Signs and Hanging Signs](../blocks/Signs.md)

## Sources and verification

Reviewed at `7af3a1594956f41ed57c3bf67d11ce006e61530f` on 2026-10-02. Source and bundled-data review only; no in-game placement, editing, crafting, support-removal, or harvesting test.

[pewen-items]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/Items.java#L314-L319
[creative]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[recipe-pewen-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/recipe/pewen_sign.json
[pewen-blocks]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/Blocks.java#L7062-L7100
[entity-type]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/entity/BlockEntityType.java#L45-L100
[chunk-placement]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java#L336-L355
[sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/SignBlock.java
[entity-validation]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java#L51-L65
[loot-pewen-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/pewen_sign.json
[loot-pewen-wall-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/pewen_wall_sign.json
[standing-tag]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/tags/block/standing_signs.json
[wall-tag]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/tags/block/wall_signs.json
[axe-tag]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/tags/block/mineable/axe.json
