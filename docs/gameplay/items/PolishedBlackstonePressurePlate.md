# Polished Blackstone Pressure Plate

The item places `minecraft:polished_blackstone_pressure_plate`. It is a floor sensor that emits signal **15** while at least one eligible living entity overlaps its detection box. Dropped-item entities do not activate it. Powered occupancy is rechecked every **20 game ticks**.

## Obtaining

Craft one from two Polished Blackstone side by side; see the [canonical recipe table](../blocks/PressurePlates.md#polished-blackstone-pressure-plate). Ordinary mining returns one matching item even by hand. A pickaxe is the tagged mining tool. [Recipe definition][recipe] · [Loot table][loot]

<span id="usage"></span><span id="behavior"></span>

## Placement and use

Place it on a valid upper support face. Spectators and entities that ignore block triggers are excluded from detection. It stays powered while eligible occupants remain and releases after an empty scheduled check. It blocks incoming water from its cell without waterlogging and is destroyed when a piston pushes into it. The [complete pressure-plate guide](../blocks/PressurePlates.md) explains the detection, timing, support, mining, water, and material exceptions. [Registration][registration]

<span id="notes"></span>

## Related pages

- [All pressure plates](../blocks/PressurePlates.md)
- [Redstone basics](../redstone/Redstone.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `be4ac3081f175ffb862938ab118b20e6457110d8` on 2026-10-02. The linked canonical block guide contains the complete behavior, tool, tag, and timing evidence. No gameplay or circuit timing test was run.

[recipe]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/polished_blackstone_pressure_plate.json
[loot]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/polished_blackstone_pressure_plate.json
[registration]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L5848-L5858
