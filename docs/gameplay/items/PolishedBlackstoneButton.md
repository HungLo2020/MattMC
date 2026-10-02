# Polished Blackstone Button

The item places `minecraft:polished_blackstone_button`. It emits signal **15** when pressed, with a normal **20-game-tick** click pulse. It uses Stone button behavior and does not activate from the arrow-contact check.

## Obtaining

Craft one from one Polished Blackstone in a shapeless recipe; see the [canonical recipe table](../blocks/Buttons.md#polished-blackstone-button). Ordinary mining returns one matching item even by hand. A pickaxe is the tagged mining tool. [Recipe definition][recipe] · [Loot table][loot]

<span id="usage"></span><span id="behavior"></span>

## Placement and use

Place it on a supported floor, wall, or ceiling face and interact to press it. Reusing a powered button does not restart the pulse. Incoming water can wash it away; it does not waterlog. A Wind Charge can still press it through the separate explosion callback. The [complete button guide](../blocks/Buttons.md) explains the detection, timing, support, mining, water, and material exceptions. [Registration][registration]

<span id="notes"></span>

## Related pages

- [All buttons](../blocks/Buttons.md)
- [Redstone basics](../redstone/Redstone.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `be4ac3081f175ffb862938ab118b20e6457110d8` on 2026-10-02. The linked canonical block guide contains the complete behavior, tool, tag, and timing evidence. No gameplay or circuit timing test was run.

[recipe]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/recipe/crafting/polished_blackstone_button.json
[loot]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/resources/data/minecraft/loot_table/blocks/polished_blackstone_button.json
[registration]: https://github.com/HungLo2020/MattMC/blob/be4ac3081f175ffb862938ab118b20e6457110d8/src/main/java/net/minecraft/world/level/block/Blocks.java#L5859-L5861
