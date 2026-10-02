# Shulker Box

The **uncolored Shulker Box** (`minecraft:shulker_box`) carries a placed box's 27-slot inventory in one non-stackable item. See the [Shulker Box block guide](../blocks/ShulkerBox.md) for opening clearance, contents preservation, nesting restrictions, and automation.

## Obtaining and use

Craft it using the [basic Shulker Shell recipe](ShulkerShell.md#crafting-a-shulker-box), or [wash a dyed box in a Water Cauldron](../blocks/ShulkerBox.md#washing-off-a-color). Washing preserves contents and uses one water level; Creative handles the output differently from Survival.

Use the item on a block face to place it, then open the placed box. Its [ordinary block loot][loot] preserves the contents when collected. This does not make a dropped box immune to damage or expiry.

The uncolored item is separate from [Purple Shulker Box](PurpleShulkerBox.md). Adding a dye can create any of the [16 colored variants](../blocks/ShulkerBox.md#obtaining-and-colors).

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`; no in-game crafting, washing, storage, or collection test was run. [Registration][registration] · [Crafting recipe][recipe] · [Block loot][loot]. Shared behavior and its complete sources are on the [block guide](../blocks/ShulkerBox.md).

Related: [Shulker Shell](ShulkerShell.md) · [Items](Items.md)

[registration]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L772-L774
[recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/shulker_box.json
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/shulker_box.json
