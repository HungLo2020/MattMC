# Redstone Lamp

**Redstone Lamp** (`minecraft:redstone_lamp`) is the item form of the redstone device described in the [placed-block guide](../blocks/RedstoneLamp.md). Use that page for the exact recipe, placement, power behavior, and timing. [Registration](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java)

## Obtaining

Craft it using the [canonical recipe](../blocks/RedstoneLamp.md#crafting-and-collecting). Ordinary collection returns the matching item without requiring Silk Touch or a tool tier; explosion recovery remains conditional. [Recipe](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/redstone_lamp.json) · [Loot](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/redstone_lamp.json)

## Using it

The Lamp is an on/off light receiver. Any positive input lights it at full brightness, and loss of input uses a four-game-tick off check. It does not visually report exact signal strength.

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. The shared block guide cites the active callbacks. No gameplay test was run; recipes and loot can change with data packs.

Related: [Redstone Lamp block](../blocks/RedstoneLamp.md) · [Redstone](../redstone/Redstone.md) · [Items](Items.md)
