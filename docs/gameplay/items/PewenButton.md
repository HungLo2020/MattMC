# Pewen Button

The item places `minecraft:pewen_button`. It emits signal **15** when pressed, with a normal **30-game-tick** click pulse. Its Cherry-type behavior also checks for qualifying arrows and thrown tridents overlapping the button.

## Obtaining

The bundled crafting definition still has the incompatible legacy ingredient format documented in [Pewen](../blocks/Pewen.md#construction-and-recipes). Do not rely on it as a working one-plank crafting route. An already placed button normally drops one matching item when mined, including by hand. [Recipe definition][recipe] · [Loot table][loot]

<span id="usage"></span><span id="behavior"></span>

## Placement and use

Place it on a supported floor, wall, or ceiling face and interact to press it. Reusing a powered button does not restart the pulse. Incoming water can wash it away; it does not waterlog. Its normal piston reaction and missing furnace-fuel tag differ from the ordinary wooden buttons. The [complete button guide](../blocks/Buttons.md) explains the detection, timing, support, mining, water, and material exceptions. [Registration][registration]

<span id="notes"></span>

## Related pages

- [All buttons](../blocks/Buttons.md)
- [Redstone basics](../redstone/Redstone.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `be4ac3081f175ffb862938ab118b20e6457110d8` on 2026-10-02. The linked canonical block guide contains the complete behavior, tool, tag, and timing evidence. No gameplay or circuit timing test was run.

[recipe]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/pewen_button.json
[loot]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/pewen_button.json
[registration]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L7048-L7056
