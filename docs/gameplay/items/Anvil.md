# Anvil

The **Anvil item** places the workstation used to repair equipment, rename items, and combine enchantments. Its item and block ID is `minecraft:anvil`. [Block-item registration][items]

## Obtaining and placement

Craft one from **three Iron Blocks and four Iron Ingots**; the exact arrangement is on the [Anvil block page](../blocks/Anvil.md#crafting-and-collecting). Collect a placed anvil with a pickaxe and put it on firm support before using it. Unsupported anvils can fall and cause damage. [Crafting recipe][recipe] · [Placed behavior](../blocks/Anvil.md)

The item variants preserve wear:

- **Anvil:** `minecraft:anvil`, the new stage
- [Chipped Anvil](ChippedAnvil.md): `minecraft:chipped_anvil`, one wear stage advanced
- [Damaged Anvil](DamagedAnvil.md): `minecraft:damaged_anvil`, the last usable stage

They are separate block items, not durability values on one item. All three open the same workstation menu. Collecting and placing a worn variant does not repair it. [Registrations][items] · [Wear transitions and menu][block]

## Using the workstation

Place the item, then interact with the block. Consult [anvil mechanics](../mechanics/AnvilMechanics.md) for repair ingredients, the **40-level cost cap**, name changes, enchantment compatibility, and item preservation. The [block page](../blocks/Anvil.md#using-and-wearing-out-an-anvil) covers use wear and falling hazards.

## Related pages

- [Anvil block](../blocks/Anvil.md)
- [Anvil mechanics](../mechanics/AnvilMechanics.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-01** at commit `b81c01943c9f3254e713c365a1dd633392929cb2`; no in-game tests were run. The linked block and mechanics pages give the detailed source scope.

[items]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L627-L629
[recipe]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/anvil.json
[block]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/AnvilBlock.java#L58-L108
