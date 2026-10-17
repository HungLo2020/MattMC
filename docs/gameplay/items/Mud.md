# Mud

**Mud** (`minecraft:mud`) is the inventory form of the matching block. See [Mud, Packed Mud and Mud Bricks](../blocks/MudAndMudBricks.md#mud) for placed behavior and the shared production chain.

## Obtaining

Find Mud in Mangrove Swamp terrain or make it with a Water Bottle and eligible soil. Ordinary mining returns one Mud even by hand; an unbroken shovel speeds collection. Silk Touch is unnecessary. Use the [family guide](../blocks/MudAndMudBricks.md#natural-supplies) for checked natural sources and [repeatable production](../blocks/MudAndMudBricks.md#making-a-continuing-supply) for the bottle interaction.

## Usage

Place Mud as terrain, use it in the [Packed Mud recipe](../blocks/MudAndMudBricks.md#crafting-and-stonecutting), or follow the separate [Muddy Mangrove Roots recipe](../blocks/TreeLogsAndRoots.md#muddy-mangrove-roots).

## Behavior

Placed Mud has a lower collision surface but full-block support faces. It is not waterloggable. For shape and plant support, see [placed Mud](../blocks/MudAndMudBricks.md#mud); for the required dripstone layout, see [Mud-to-Clay conversion](../blocks/ClayAndBricks.md#turning-mud-into-clay).

## Notes

- This is a registered placeable block item
- Recipes and loot can be changed by server data packs
- Source-reviewed on **2026-10-02** at `a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd`; no in-game test was run

[Item registration][mud-item] · [Block properties][mud-reg] · [Mud loot][loot-mud] · [Shovel tag][shovel-tag] · [Water Bottle use][potion]

[mud-item]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/item/Items.java#L116
[mud-reg]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/level/block/Blocks.java#L6652-L6662
[loot-mud]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/loot_table/blocks/mud.json
[shovel-tag]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/resources/data/minecraft/tags/block/mineable/shovel.json
[potion]: https://github.com/HungLo2020/MattMC/blob/a3c8229f0d3eff7b98dcb5af3aaa0803ea303dcd/src/main/java/net/minecraft/world/item/PotionItem.java#L34-L69
