# Dark Oak Button

**Dark Oak Button** (`minecraft:dark_oak_button`) is the item form of a wooden redstone button. Its exact material entry is in [Buttons](../blocks/Buttons.md#dark-oak-button). [Registration][item] · [English name][name]

## Obtaining

Put **1 [Dark Oak Planks](DarkOakPlanks.md) in any crafting slot → 1 Dark Oak Button**. This shapeless recipe fits the inventory grid; other plank materials do not substitute. [Recipe][recipe]

Ordinary Survival mining returns **1 Dark Oak Button, even by hand**; no special tool or Silk Touch is required. An unbroken axe is efficient. See [collecting rules](../blocks/Buttons.md#mining-water-and-pistons) for mining and explosion distinctions. [Block properties][block] · [Loot][loot]

It is also listed in Creative's **Building Blocks** tab. [Creative entry][creative]

## Usage

Stacks hold **64** by default. [Default block items][defaults] · [Stack limit][stack]

Attach it to a **sturdy floor, wall, or ceiling face**, then use the unpressed button. Losing valid support breaks it. See [placement and operation](../blocks/Buttons.md#placement-and-operation). [Support checks][support]

## Behavior

A normal press supplies **signal 15 for 30 game ticks** (1.5 seconds at 20 game ticks per second). Pressing it again while powered does not restart the timer. [Material timing][block] · [Press and output][button]

An eligible arrow overlapping the button can activate it or keep it powered. This does not apply to every projectile. The shared [arrow detail](../blocks/Buttons.md#arrow-detail) explains eligible projectiles, repeat checks, release, and the separate Wind Charge route. [Arrow-enabled material][sets] · [Detection][button]

## Notes

One Dark Oak Button supplies **100 burn ticks** in the default furnace fuel table. See [fuel planning](../blocks/Furnace.md#fuel-planning) before burning spare controls. [Fuel tag][fuel-tag] · [Fuel rules][fuel]

Related: [Dark Oak Pressure Plate](DarkOakPressurePlate.md) · [Water and piston behavior](../blocks/Buttons.md#mining-water-and-pistons) · [Items](Items.md)

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. No in-game crafting, mining, placement, or circuit test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1046
[name]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/assets/minecraft/lang/en_us.json#L1551
[recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/dark_oak_button.json
[loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/dark_oak_button.json
[block]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/Blocks.java#L2730-L2732
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L156
[defaults]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L2750-L2781
[stack]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L390
[fuel-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/wooden_buttons.json
[fuel]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L108
[support]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/FaceAttachedHorizontalDirectionalBlock.java#L27-L82
[button]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/ButtonBlock.java#L86-L181
[sets]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/state/properties/BlockSetType.java#L119-L219
