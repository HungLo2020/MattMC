# Acacia Button

**Acacia Button** (`minecraft:acacia_button`) is a wooden redstone button with a **30-game-tick** ordinary press. Its exact material entry is in [Buttons](../blocks/Buttons.md#acacia-button). [Item registration][item]

## Obtaining

Put **1 [Acacia Planks](AcaciaPlanks.md) in any crafting slot → 1 Acacia Button**. This shapeless recipe fits the inventory grid. Other plank materials do not substitute. [Recipe][recipe]

Mining the placed button normally returns **1 Acacia Button**, including by hand. An unbroken axe is efficient; see [button mining rules](../blocks/Buttons.md#mining-water-and-pistons). [Loot][loot]

## Usage

Attach it to a **sturdy floor, wall, or ceiling face**, then use the unpressed button. Losing valid support breaks it. It has no movement-blocking collision; see [placement and operation](../blocks/Buttons.md#placement-and-operation). [Support checks][button-support]

## Behavior

A press supplies **signal 15**, including direct power toward the supporting block. The ordinary pulse lasts **30 game ticks**, or **1.5 seconds at 20 game ticks per second**. Pressing an already powered button does not restart the timer. [Press, output, and release checks][button-use]

An eligible arrow overlapping the button's detection box can press it or keep it powered. While an arrow remains, the button checks again every 30 ticks; after it leaves, release happens at the next scheduled empty check. This does not apply to every projectile. See [Buttons: arrow detail](../blocks/Buttons.md#arrow-detail) for ordinary/spectral arrows, thrown tridents, and the separate Wind Charge route. [Arrow-enabled material][sets] · [Detection and rescheduling][button-use]

## Notes

Related: [Buttons](../blocks/Buttons.md) · [Pressure plates](../blocks/PressurePlates.md) · [Items](Items.md)

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. No in-game crafting, mining, placement, or circuit test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1044
[recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/acacia_button.json
[loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/acacia_button.json
[button-support]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/FaceAttachedHorizontalDirectionalBlock.java#L27-L82
[button-use]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/ButtonBlock.java#L86-L181
[sets]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/state/properties/BlockSetType.java#L119-L219
