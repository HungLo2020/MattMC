# Pewen Pressure Plate

The item places `minecraft:pewen_pressure_plate`. It is a floor sensor that emits signal **15** while any eligible entity overlaps its detection box, including dropped items. Powered occupancy is rechecked every **20 game ticks**.

## Obtaining

Craft one from two Pewen Planks side by side; see the [canonical recipe table](../blocks/PressurePlates.md#pewen-pressure-plate). Ordinary mining returns one matching item even by hand. Pewen Plate is absent from the standard axe and wooden-plate tags. [Recipe definition][recipe] · [Loot table][loot]

<span id="usage"></span><span id="behavior"></span>

## Placement and use

Place it on a valid upper support face. Spectators and entities that ignore block triggers are excluded from detection. It stays powered while eligible occupants remain and releases after an empty scheduled check. Unlike the vanilla plates, Pewen Plate can be washed away by incoming water, uses the normal piston reaction, and has no checked furnace-fuel entry. The [complete pressure-plate guide](../blocks/PressurePlates.md) explains the detection, timing, support, mining, water, and material exceptions. [Registration][registration]

<span id="notes"></span>

## Related pages

- [All pressure plates](../blocks/PressurePlates.md)
- [Redstone basics](../redstone/Redstone.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `be4ac3081f175ffb862938ab118b20e6457110d8` on 2026-10-02. The linked canonical block guide contains the complete behavior, tool, tag, and timing evidence. No gameplay or circuit timing test was run.

[recipe]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/pewen_pressure_plate.json
[loot]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/pewen_pressure_plate.json
[registration]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L7057-L7061
