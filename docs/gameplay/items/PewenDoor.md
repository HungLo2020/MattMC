# Pewen Door

`minecraft:pewen_door` places a two-block-tall door using the Pewen family's Cherry block-set controls. Door items stack to **16**. [Item binding and stack limit][item] · [Door settings][door-block] · [Copied plank settings][block]

## Obtaining

At a Crafting Table, arrange **six [Pewen Planks](PewenPlanks.md) in two columns of three** to make **three Pewen Doors**. [Recipe][recipe]

This recipe uses Pewen Planks specifically. Obtaining those planks is a separate step: the bundled log-to-plank recipe has an incompatible ingredient format, as explained in the [Pewen recipe cautions](../blocks/Pewen.md#recipes-and-tools-that-need-caution).

It is listed with Building Blocks. The [inventory browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item) can supply it in Creative, subject to its insertion checks; a visible catalog entry in Survival does not supply the item. [Listing][listing]

## Usage

**One item places both halves.** Leave two block spaces for the doorway and a sturdy upper face beneath the bottom half. Placement requires a replaceable space above the bottom half and room below the build-height limit; your horizontal direction sets the facing. [Item placement][placement] · [Placement checks][placement-checks] · [Door placement][door-placement] · [Support][support]

Open or close the door by hand or with redstone. The Cherry settings allow hand operation; see the [Pewen family behavior](../blocks/Pewen.md#block-behavior). [Cherry controls][type] · [Hand use][hand] · [Redstone response][redstone]

## Behavior

Normal Survival harvesting of an intact door, including by hand, returns **one Pewen Door item**, through the lower-half loot entry. The upper half does not add a second item. No special tool or Silk Touch is required, and Fortune does not increase the count. Explosion recovery is conditional. [Door settings][door-block] · [Copied plank settings][block] · [Player drop gate][drop-gate] · [Harvest path][harvest] · [Loot][loot]

Removing the required support or paired half causes the remaining door section to disappear. The door has no waterlogged state. [Support and paired-half updates][updates] · [Door states][states]

The bundled axe-mineable tag omits this block, so an ordinary axe does not receive its tag-based mining-speed bonus here. [Axe tag][axe-tag] · [Axe tool][axe-tool] · [Speed rule][tool-speed]

## Notes

The bundled wooden-doors item tag omits Pewen Door, so the default wooden-door furnace-fuel entry does not cover it. [Item tag][fuel-tag] · [Fuel list][fuels]

Related: [Pewen Trapdoor](PewenTrapdoor.md) · [Pewen Fence Gate](PewenFenceGate.md) · [Items](Items.md)

Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on 2026-10-04. These are checked bundled recipes, tags, loot, and placement rules; data packs can change them. No in-game crafting, placement, or harvesting test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L301-L310
[door-block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L7034-L7038
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L7010-L7017
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/pewen_door.json
[listing]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L237-L246
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BlockItem.java#L40-L93
[placement-checks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BlockItem.java#L119-L154
[door-placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/DoorBlock.java#L141-L162
[support]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/DoorBlock.java#L240-L245
[type]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/state/properties/BlockSetType.java#L123-L140
[hand]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/DoorBlock.java#L201-L211
[redstone]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/DoorBlock.java#L226-L237
[drop-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L277-L293
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/pewen_door.json
[updates]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/DoorBlock.java#L87-L107
[states]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/DoorBlock.java#L266-L269
[axe-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[axe-tool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L439-L441
[tool-speed]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ToolMaterial.java#L35-L48
[fuel-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/wooden_doors.json
[fuels]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L42-L108
