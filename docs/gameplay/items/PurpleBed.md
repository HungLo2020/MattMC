# Purple Bed

Purple Bed is the placeable item for a purple [Bed](../blocks/Bed.md). It stacks to **1**, so each spare bed needs its own inventory slot.

## Obtaining

- **Craft one:** put three [Purple Wool](PurpleWool.md) across one row above three items from the planks tag in a crafting table. All three wool must be purple; the plank slots accept any items in `minecraft:planks`.
- **Recolor one:** combine one bed of any of the other 15 colors with one [Purple Dye](PurpleDye.md) in a shapeless recipe. The result is one Purple Bed. A bed that is already purple is excluded from this recipe.
- **Pick it back up:** breaking either half of an intact purple bed in ordinary Survival, with block drops enabled, returns one Purple Bed. The paired half is removed too. Only the head half supplies bed loot, so the two placed halves do not give two items.

## Usage

Place the item to build both halves of the bed; ordinary Survival placement consumes one item. It needs two horizontal block positions, with the head extending in the direction you face. The second position must be replaceable and inside the world border.

Use the placed bed for sleeping and respawn-setting where those interactions are supported. Leave space for access and a usable exit. See [Beds: sleeping and respawn](../blocks/Bed.md#sleeping-and-respawn) for obstruction, distance, daylight, and nearby-monster checks.

## Behavior

**Do not use this bed to sleep in the Nether or End.** Their bundled dimension types set `bed_works` to false. Interacting with a bed where that setting is false removes it and causes a **power-5 explosion with fire enabled**. Color does not change this danger, and other dimensions must be checked rather than assumed safe.

Successful sleep and night skipping still depend on the shared [bed rules](../blocks/Bed.md#night-skipping-and-weather). Recoloring changes which bed item and block you have; it does not bypass those checks.

## Notes

* This item is the item form of the `minecraft:purple_bed` block.
* The normal one-bed recovery described above is not a promise for explosions: the loot table includes an explosion-survival condition.
* See [Dyes](Dyes.md) for color ingredients, [Crafting](../crafting/Crafting.md) for recipe use, and [Items](Items.md) for other inventory entries.

## Sources and verification

Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on 2026-10-04. No in-game crafting, recoloring, placement, sleeping, respawn, breaking, or explosion test was performed.

- [Crafting recipe](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/purple_bed.json) and [recoloring recipe](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/dye_purple_bed.json)
- [Head-only block loot](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/purple_bed.json)
- [Bed item registration and stack limit](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1717-L1732), [BedItem placement](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BedItem.java), and [placement item consumption](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BlockItem.java#L48-L93)
- [Colored block registrations](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L666-L681) and [shared bed registration](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L7148-L7160)
- [Bed placement, paired removal, and interaction](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/BedBlock.java)
- [Paired-block drop handling](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L217-L227) and [block destruction and loot](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/Level.java#L262-L285)
- [Nether bed setting](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/dimension_type/the_nether.json) and [End bed setting](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/dimension_type/the_end.json)
