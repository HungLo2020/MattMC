# Acacia Pressure Plate

Acacia Pressure Plate (`minecraft:acacia_pressure_plate`) is a floor-mounted redstone input. It detects a broad range of eligible entities and supplies a full-strength signal while occupied. [Registration][plate-reg] · [Detection][plate]

## Obtaining

Place **two Acacia Planks side by side → one Acacia Pressure Plate**. The recipe fits the inventory's 2 × 2 grid; other plank types do not substitute. [Recipe][plate-recipe]

Ordinary Survival mining recovers **one plate even by hand**. An axe speeds breaking but is not required for drops. Silk Touch is unnecessary and Fortune does not add plates. [Registration][plate-reg] · [Axe tag][axe] · [Player tool gate][gate] · [Mining dispatch][mining] · [Loot][plate-loot]

It appears with other acacia building blocks in the Building Blocks category, which feeds the [Creative inventory browser](../mechanics/InventoryBrowser.md). Catalog visibility is separate from permission to insert items; ordinary Survival requests are skipped. [Category entry][plate-category]

## Usage

Place it on a block that offers **rigid or center support on its upper face**. It sits horizontally, has no wall or ceiling mounting, and breaks if its required support is removed. Connect a nearby receiver or Redstone Dust; a pressed plate also directly powers its supporting block. See [placement and output](../blocks/PressurePlates.md#placement-and-output). [Support][plate-support] · [Placement checks][placement] · [Output][plate-output]

One plate supplies **300 default Furnace burn ticks**. Use [Furnace fuel planning](../blocks/Furnace.md#fuel-planning) when choosing fuel for a batch. [Wooden-plate fuel tag][plate-fuel] · [Fuel values][fuel] · [Fuel exclusions][fuel-exclusion] · [Tag application][fuel-tags] · [Furnace consumption][furnace-use]

## Behavior

It detects players, mobs, dropped items, arrows, and other eligible entities whose bounds overlap its detection area. **Spectators and entities that ignore block triggers are excluded.** At least one qualifying occupant gives output **15**; no qualifying occupant gives **0**. [Acacia registration][plate-reg] · [Wood sensitivity][plate-set] · [Binary output][plate] · [Entity filter][plate-output]

While powered, it rechecks occupancy every **20 game ticks**, about one second at the normal tick rate. This is a recheck interval, not a fixed pulse: it remains powered while occupied and notices departure at a scheduled check. It has no waterlogged state; the [pressure-plate guide](../blocks/PressurePlates.md#water-pistons-and-fuel) explains its incoming-water and piston rules. [Recheck interval][plate-time] · [Contact and scheduled checks][plate-contact] · [Plate state][plate]

## Notes

This item is the item form of `minecraft:acacia_pressure_plate`. See [Pressure plates](../blocks/PressurePlates.md) to compare Stone, other woods, and weighted plates. Normal self-drops assume block drops are enabled; the loot table's explosion condition still applies. [Item registration][items] · [Drop dispatch][drops] · [Loot][plate-loot]

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`, using the bundled recipes, loot and tags and the active behavior paths linked here. No gameplay test was run; custom data-pack changes are outside this review.

[plate-reg]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L1817-L1828
[plate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/PressurePlateBlock.java#L28-L55
[plate-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/acacia_pressure_plate.json
[axe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[mining]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L297
[plate-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/acacia_pressure_plate.json
[plate-category]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L133-L143
[plate-support]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/BasePressurePlateBlock.java#L53-L73
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BlockItem.java#L110-L141
[plate-output]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/BasePressurePlateBlock.java#L126-L148
[plate-fuel]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/wooden_pressure_plates.json
[fuel]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L34-L108
[fuel-exclusion]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/non_flammable_wood.json
[fuel-tags]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L125-L145
[furnace-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L145-L178
[plate-set]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/state/properties/BlockSetType.java#L200-L217
[plate-time]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/BasePressurePlateBlock.java#L44-L46
[plate-contact]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/BasePressurePlateBlock.java#L75-L117
[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java
[drops]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L364-L420
