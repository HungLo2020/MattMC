# Pewen Hanging Sign

Pewen Hanging Sign (`minecraft:pewen_hanging_sign`) is registered for ceiling and wall-hanging Pewen forms. It stacks to **16** and is listed in Creative, but crafting and placement both have unresolved integration gaps. [Registration][pewen-items] · [Creative listing][creative]

## Obtaining

The bundled recipe requests **six Stripped Pewen Logs** and two `minecraft:chain`, defining **three Pewen Hanging Signs**. The active chain is registered as `minecraft:iron_chain`, so the requested ingredient is unresolved. This is **not a verified working crafting recipe**, and the normal axe map has no Pewen stripping conversion. See the [Pewen recipe caveats](../blocks/Pewen.md#recipes-and-tools-that-need-caution). [Recipe][recipe-pewen-hanging-sign] · [Current chain registry][iron-chain] · [Axe map][axe]

## Usage and current limitation

Both blocks use the standard hanging-sign classes with Oak's wood type, but neither is included in the Hanging Sign block entity's valid-block list. The active placement path constructs an entity that rejects that block state with an exception. This is source-level evidence of a placement failure path, not a reproduced in-game crash. Editable labels are not established for these forms. [Blocks][pewen-blocks] · [Valid-block list][entity-type] · [Placement dispatch][chunk-placement] · [Ceiling factory][ceiling] · [Wall factory][wall-hanging] · [Constructor validation][entity-validation]

The ceiling form has a plain one-item loot table. The wall-hanging form has neither a bundled table nor a shared-loot override, so its missing lookup resolves to empty loot. Pewen is also absent from the standard hanging-sign tags, including those used by normal axe mining and aligned hanging-sign attachment. These data findings are not tested recovery instructions for blocks affected by the placement issue. [Ceiling loot][loot-pewen-hanging-sign] · [Registration][pewen-blocks] · [Missing table handling][missing-loot] · [Ceiling tag][ceiling-tag] · [Wall tag][wall-hanging-tag] · [Axe tag][axe-tag]

## Related pages

- [Pewen sign integration details](../blocks/Signs.md#pewen-signs-incomplete-integration)
- [Pewen family](../blocks/Pewen.md)
- [Pewen Sign](PewenSign.md)
- [Working standard hanging variants](../blocks/Signs.md#hanging-sign-variants)

## Sources and verification

Reviewed at `7af3a1594956f41ed57c3bf67d11ce006e61530f` on 2026-10-02. Source and bundled-data review only; no in-game placement, editing, crafting, support-removal, or harvesting test.

[pewen-items]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/Items.java#L314-L319
[creative]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[recipe-pewen-hanging-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/recipe/pewen_hanging_sign.json
[iron-chain]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/Blocks.java#L2317-L2322
[axe]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/AxeItem.java
[pewen-blocks]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/Blocks.java#L7062-L7100
[entity-type]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/entity/BlockEntityType.java#L45-L100
[chunk-placement]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java#L336-L355
[ceiling]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/CeilingHangingSignBlock.java
[wall-hanging]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/WallHangingSignBlock.java
[entity-validation]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java#L51-L65
[loot-pewen-hanging-sign]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/pewen_hanging_sign.json
[missing-loot]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[ceiling-tag]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/tags/block/ceiling_hanging_signs.json
[wall-hanging-tag]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/tags/block/wall_hanging_signs.json
[axe-tag]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/tags/block/mineable/axe.json
