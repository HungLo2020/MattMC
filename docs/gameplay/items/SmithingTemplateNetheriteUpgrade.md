# Smithing Template: Netherite Upgrade

The Netherite Upgrade Smithing Template is registered as `minecraft:netherite_upgrade_smithing_template`. It is the template input for registered Netherite equipment upgrades, not a general-purpose armor-trim pattern.

## Bastion loot

The checked bundled chest tables contain these dedicated template pools:

| Chest loot table | Template pool |
| --- | --- |
| Bastion treasure | One roll selecting one template |
| Bastion bridge | One template entry versus an empty entry weighted 9 |
| Bastion hoglin stable | One template entry versus an empty entry weighted 9 |
| Other bastion chest | One template entry versus an empty entry weighted 9 |

The latter pools correspond to a **10% chance per execution of that pool** under the listed weights. The treasure entry is one template in this source snapshot; do not substitute a count remembered from another version. These are loot-table rules, not a guarantee about how many of each chest type a particular structure contains.

## Duplicating

Use one existing template, **seven Diamonds**, and **one Netherrack** in this 3 × 3 arrangement:

- Top row: Diamond, template, Diamond
- Middle row: Diamond, Netherrack, Diamond
- Bottom row: Diamond, Diamond, Diamond

The output is **two templates**, so one existing template is required to make a net gain of one. Duplicate before spending the last copy if you want to retain a reusable source for future crafting.

## Using it

At a [Smithing Table](../blocks/SmithingTable.md), use the template with a supported Diamond item and the registered Netherite addition. For example, Diamond Pickaxe plus a Netherite Ingot produces Netherite Pickaxe. Inputs must match an actual smithing recipe; a template does not upgrade arbitrary items.

See the [smithing guide](../smithing/Smithing.md) for input consumption and component handling. This page does not describe a template as a permanent reusable tool.

## Related pages

- [Netherite Ingot](NetheriteIngot.md)
- [Smithing Table](../blocks/SmithingTable.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game loot, smithing, or smelting test was run.

- [Treasure chest pool](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/chests/bastion_treasure.json)
- [Bridge chest pool](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/chests/bastion_bridge.json)
- [Hoglin stable pool](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/chests/bastion_hoglin_stable.json)
- [Other chest pool](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/chests/bastion_other.json)
- [Duplication recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/netherite_upgrade_smithing_template.json)
- [Pickaxe upgrade](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/smithing/netherite_pickaxe_smithing.json)
