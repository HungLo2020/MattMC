# Wool and Carpet

**Wool** provides full blocks for colored builds; **wool carpet** provides a thin floor covering. Both come in **16 colors**. Use a renewable [Sheep flock](../mobs/Sheep.md#shearing-and-regrowth) for large projects, and choose full wool blocks when you need a vibration barrier. Carpet has different support and vibration-blocking rules. [Wool registrations][wool-blocks] · [Carpet registrations][carpet-blocks] · [Vibration tags][occludes]

## Colors and item IDs

Each entry links to that color's acquisition and recipe details. The block and its item share the listed ID. All colors within each family share the placement, tool, fire, and fuel rules below. [Wool items][wool-items] · [Carpet items][carpet-items] · [Wool tag][wool-tag] · [Carpet tag][carpet-tag]

| Color | Wool block and item | Carpet block and item |
| --- | --- | --- |
| White | [`minecraft:white_wool`](../items/WhiteWool.md) | [`minecraft:white_carpet`](../items/WhiteCarpet.md) |
| Orange | [`minecraft:orange_wool`](../items/OrangeWool.md) | [`minecraft:orange_carpet`](../items/OrangeCarpet.md) |
| Magenta | [`minecraft:magenta_wool`](../items/MagentaWool.md) | [`minecraft:magenta_carpet`](../items/MagentaCarpet.md) |
| Light Blue | [`minecraft:light_blue_wool`](../items/LightBlueWool.md) | [`minecraft:light_blue_carpet`](../items/LightBlueCarpet.md) |
| Yellow | [`minecraft:yellow_wool`](../items/YellowWool.md) | [`minecraft:yellow_carpet`](../items/YellowCarpet.md) |
| Lime | [`minecraft:lime_wool`](../items/LimeWool.md) | [`minecraft:lime_carpet`](../items/LimeCarpet.md) |
| Pink | [`minecraft:pink_wool`](../items/PinkWool.md) | [`minecraft:pink_carpet`](../items/PinkCarpet.md) |
| Gray | [`minecraft:gray_wool`](../items/GrayWool.md) | [`minecraft:gray_carpet`](../items/GrayCarpet.md) |
| Light Gray | [`minecraft:light_gray_wool`](../items/LightGrayWool.md) | [`minecraft:light_gray_carpet`](../items/LightGrayCarpet.md) |
| Cyan | [`minecraft:cyan_wool`](../items/CyanWool.md) | [`minecraft:cyan_carpet`](../items/CyanCarpet.md) |
| Purple | [`minecraft:purple_wool`](../items/PurpleWool.md) | [`minecraft:purple_carpet`](../items/PurpleCarpet.md) |
| Blue | [`minecraft:blue_wool`](../items/BlueWool.md) | [`minecraft:blue_carpet`](../items/BlueCarpet.md) |
| Brown | [`minecraft:brown_wool`](../items/BrownWool.md) | [`minecraft:brown_carpet`](../items/BrownCarpet.md) |
| Green | [`minecraft:green_wool`](../items/GreenWool.md) | [`minecraft:green_carpet`](../items/GreenCarpet.md) |
| Red | [`minecraft:red_wool`](../items/RedWool.md) | [`minecraft:red_carpet`](../items/RedCarpet.md) |
| Black | [`minecraft:black_wool`](../items/BlackWool.md) | [`minecraft:black_carpet`](../items/BlackCarpet.md) |

[Moss Carpet](../items/MossCarpet.md) and [Pale Moss Carpet](../items/PaleMossCarpet.md) are outside these wool families and their dye recipes. [Carpet family tag][carpet-tag]

## Obtaining and color changes

Shearing an eligible adult sheep gives wool of its current color. Dyeing a living sheep with fleece can establish a repeated supply of that color; harvesting wool and recoloring it in a crafting grid are separate choices. See [Sheep](../mobs/Sheep.md#shearing-and-regrowth) for eligibility, lead interactions, quantities, and regrowth. [Shearing callback][sheep] · [Dye interaction][dye-item] · [Shearing color routing][shearing]

The bundled recipes follow these rules across all 16 colors:

- **Wool recoloring:** one target-color dye plus one wool from any of the other 15 colors gives one wool of the target color, shapeless. White dye can recolor wool too. The starting color does not blend with the dye
- **Carpet crafting:** two wool of the same color, side by side, give three matching carpets. Use the color's item page above for its exact recipe; the white-carpet recipe is in the [White Wool recipe table](../items/WhiteWool.md#selected-crafting-uses)
- **Carpet recoloring:** one target-color dye plus one carpet from any of the other 15 wool-carpet colors gives one target-color carpet, shapeless. Each dye recolors one carpet in the bundled recipes

Every color page cites its own checked recipe files. Example source recipes: [red wool][red-wool-recipe] · [white wool][white-wool-recipe] · [red carpet][red-carpet-recipe] · [recolor red carpet][red-carpet-dye] · [recolor white carpet][white-carpet-dye]

For making new white wool from string, use the [String recipe guide](../items/String.md#selected-crafting-recipes). [White Wool](../items/WhiteWool.md#selected-crafting-uses) also links its banner and bed recipes; the [Bed guide](Bed.md) covers sleeping and placement rules. [String-to-wool recipe][string-recipe]

## Placement and support

Use the item on a block face with room for the new block. Normal placement checks both whether the block can survive at that position and whether entities obstruct its shape. [Block-item placement][placement]

| Form | Shape | Support after placement | Registered hardness |
| --- | --- | --- | ---: |
| Wool | Full block | No continuing support requirement | 0.8 |
| Wool carpet | Full-width layer, **1/16 block high** | The block immediately below must not be air | 0.1 |

Wool uses the ordinary full-block behavior. Carpet's support check does **not** require a full, solid upper face below it; it checks for a non-air block. If that position becomes air, the carpet breaks on the resulting neighbor update. Keep its floor in place when rearranging a build. [Wool registration][wool-blocks] · [Carpet registration][carpet-blocks] · [Wool-carpet inheritance][wool-carpet] · [Carpet shape and support][carpet] · [Default shape and survival][base-block] · [Air check][air-check] · [Support-loss destruction][update-destroy]

## Mining and drops

In ordinary Survival breaking, **no special tool is required** to collect either family. Every color's block loot returns **one matching wool or carpet item**. Carpets do not turn back into wool when broken. There is no Silk Touch gate or Fortune multiplier in these tables, but an explosion-survival condition means explosion recovery is not guaranteed. Individual item pages link their exact block loot. [Mining drop gate][player-drops] · [Wool registration][wool-blocks] · [Carpet registration][carpet-blocks] · [Example wool loot][white-wool-loot] · [Example carpet loot][white-carpet-loot]

Unbroken [Shears](../items/Shears.md) have a faster mining rule for the wool-block tag: speed **5**, compared with their default **1**. Wool carpets are not in that tag, so this wool speed bonus does not apply to carpet. Mining blocks with shears can spend their durability; using your hand on carpet avoids that wear. Broken shears fall back to mining speed 1. [Shears registration][shears-registration] · [Tool speed and wear][shears] · [Wool tag][wool-tag] · [Broken-tool speed][broken-tool]

## Fire and furnace fuel

Keep both families away from fire and lava. Every wool and wool-carpet color is registered as flammable and able to ignite from lava. [Fire registration][fire] · [Wool properties][wool-blocks] · [Carpet properties][carpet-blocks]

| Fuel item, any bundled color | Default furnace burn time |
| --- | ---: |
| 1 wool | **100 ticks** |
| 1 wool carpet | **67 ticks** |

The default fuel scale is 200 ticks: wool receives half that value; carpet receives one plus the integer third. Neither single item lasts for a typical 200-tick furnace recipe. Use the [Furnace guide](Furnace.md) for operation and alternatives. These values describe the default furnace fuel table, not a promise about every cooking device or recipe. [Fuel values][fuel] · [Wool fuel tag][wool-item-tag] · [Carpet fuel tag][carpet-item-tag] · [Furnace lookup][furnace]

## Vibrations

Wool flooring and a wool barrier solve different problems:

- **Wool and wool carpet dampen events involving themselves.** The active vibration listener rejects an event when its affected block belongs to either family. Ordinary placement and breaking report that block; footsteps and landings report the block involved in the movement. This supports quiet movement on wool or carpet and quiet placement/breaking of those materials
- **Only full wool blocks occlude vibration signals.** A wool barrier can stop a vibration traveling from another source to a listener when it blocks the listener's line checks. Wool carpet is absent from this occlusion tag, so it does not substitute for a full wool barrier

These checks apply to the active vibration system used by sculk sensors and the Warden. A carpet floor does not suppress every action nearby: the dampening check depends on the event's affected block, while signal blocking depends on the wool barrier and its geometry. [Dampening tag][dampens] · [Occlusion tag][occludes] · [Active listener and line checks][listener] · [Event filtering][vibration-filter] · [Placement event][placement] · [Breaking event][breaking] · [Step event][steps] · [Landing event][landing] · [Sculk listener][sensor] · [Warden listener][warden]

Related: [White Wool](../items/WhiteWool.md) · [Sheep](../mobs/Sheep.md) · [Shears](../items/Shears.md) · [Blocks](Blocks.md) · [Items](../items/Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. All 16 colors were checked against their block/item registrations, 48 dye/carpet recipes, 32 block loot tables, 32 sheep color loot tables, and the shared tags and callbacks. This is a family guide with selected acquisition routes, not a complete survey of structure loot, trading, or world generation. No in-game crafting, placement, mining, fire, furnace, sheep, or vibration test was run. Data packs can change recipes, loot, and tags.

[wool-blocks]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L811-L889
[carpet-blocks]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L3194-L3273
[wool-items]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L326-L341
[carpet-items]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L658-L708
[wool-tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/wool.json
[carpet-tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/wool_carpets.json
[sheep]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/sheep/Sheep.java#L137-L183
[dye-item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/DyeItem.java#L25-L38
[shearing]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/shearing/sheep.json
[red-wool-recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/dye_red_wool.json
[white-wool-recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/dye_white_wool.json
[red-carpet-recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/red_carpet.json
[red-carpet-dye]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/dye_red_carpet.json
[white-carpet-dye]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/dye_white_carpet.json
[string-recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/white_wool_from_string.json
[placement]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/BlockItem.java#L47-L142
[carpet]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/CarpetBlock.java
[base-block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L326
[air-check]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/LevelReader.java#L89-L91
[update-destroy]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Block.java#L213-L227
[player-drops]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[white-wool-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/white_wool.json
[white-carpet-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/white_carpet.json
[shears-registration]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L1742-L1744
[shears]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ShearsItem.java#L31-L65
[broken-tool]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ItemStack.java#L374-L379
[fire]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/FireBlock.java#L445-L480
[fuel]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L89
[wool-item-tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/item/wool.json
[carpet-item-tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/item/wool_carpets.json
[furnace]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L264-L266
[dampens]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/dampens_vibrations.json
[occludes]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/occludes_vibration_signals.json
[listener]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/gameevent/vibrations/VibrationSystem.java#L184-L255
[vibration-filter]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/gameevent/vibrations/VibrationSystem.java#L380-L402
[breaking]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Block.java#L480-L488
[steps]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Entity.java#L775-L900
[landing]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Entity.java#L1399-L1413
[sensor]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/SculkSensorBlockEntity.java#L18-L30
[warden]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/monster/warden/Warden.java#L108-L117
[wool-carpet]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/WoolCarpetBlock.java
