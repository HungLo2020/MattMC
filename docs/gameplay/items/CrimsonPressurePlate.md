# Crimson Pressure Plate

**Crimson Pressure Plate** (`minecraft:crimson_pressure_plate`) is the item form of a wooden floor-mounted redstone control. Its exact material entry is in [Pressure plates](../blocks/PressurePlates.md#crimson-pressure-plate). [Registration][item] · [English name][name]

## Obtaining

Put **2 [Crimson Planks](CrimsonPlanks.md) side by side in one row → 1 Crimson Pressure Plate**. This shaped recipe fits the inventory grid and requires both planks to be Crimson. [Recipe][recipe]

Ordinary Survival mining returns **1 Crimson Pressure Plate, even by hand**; no special tool or Silk Touch is required. An unbroken axe is efficient. See [collecting rules](../blocks/PressurePlates.md#crafting-and-collecting) for mining and explosion distinctions. [Block properties][block] · [Loot][loot]

It is also listed in Creative's **Building Blocks** tab. [Creative entry][creative]

## Usage

Stacks hold **64** by default. [Default block items][defaults] · [Stack limit][stack]

Place it on a block whose upper face supplies **rigid or center support**; a normal full solid floor is a simple choice. It cannot mount on a wall or ceiling. Losing support breaks it. See [placement and output](../blocks/PressurePlates.md#placement-and-output). [Support checks][plate]

## Behavior

It supplies **signal 15 while an eligible entity occupies its detection region**, otherwise 0. Players, mobs, dropped items, and arrows can qualify; spectators and entities that ignore block triggers do not. [Material sensitivity][sets] · [Binary detection][binary] · [Shared exclusions][plate]

While powered, it rechecks occupancy every **20 game ticks**. This is a recheck interval, not a fixed pulse; see [activation and release timing](../blocks/PressurePlates.md#activation-and-release-timing). [Scheduled checks][plate]

## Notes

**Crimson Pressure Plate is not furnace fuel**: the default fuel list removes it through the non-flammable-wood tag. [Wooden-control tag][fuel-tag] · [Exclusion][fuel-exclusion] · [Fuel rules][fuel]

Related: [Crimson Button](CrimsonButton.md) · [Water and piston behavior](../blocks/PressurePlates.md#water-pistons-and-fuel) · [Items](Items.md)

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. No in-game crafting, mining, placement, or circuit test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1066
[name]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/assets/minecraft/lang/en_us.json#L1517
[recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/crimson_pressure_plate.json
[loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/crimson_pressure_plate.json
[block]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/Blocks.java#L5608-L5618
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L221
[defaults]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L2750-L2781
[stack]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L390
[fuel-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/wooden_pressure_plates.json
[fuel]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L108
[fuel-exclusion]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/non_flammable_wood.json
[plate]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/BasePressurePlateBlock.java#L25-L155
[binary]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/PressurePlateBlock.java#L33-L50
[sets]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/state/properties/BlockSetType.java#L119-L219
