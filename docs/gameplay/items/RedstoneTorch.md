# Redstone Torch

**Redstone Torch** (`minecraft:redstone_torch`) is the item form of the redstone device described in the [placed-block guide](../blocks/RedstoneTorch.md). Use that page for the exact recipe, placement, power behavior, and timing. [Registration](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java)

## Obtaining

Craft it using the [canonical recipe](../blocks/RedstoneTorch.md#crafting-and-collection). Ordinary collection returns the matching item without requiring Silk Touch or a tool tier; explosion recovery remains conditional. [Recipe](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/redstone_torch.json) · [Loot](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/redstone_torch.json)

## Using it

The Torch supplies power while its support-side input is off. Rapid switching can cause burnout; this is separate from ordinary light-emitting Torch behavior. The same item handles standing and wall placement.

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. The shared block guide cites the active callbacks. No gameplay test was run; recipes and loot can change with data packs.

Related: [Redstone Torch block](../blocks/RedstoneTorch.md) · [Redstone](../redstone/Redstone.md) · [Items](Items.md)
