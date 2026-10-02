# Dinosaur Chop

Dinosaur Chop and Cooked Dinosaur Chop are **placeable, four-serving food blocks**. Their inventory forms are non-stackable block items without ordinary food components: place a chop, then interact with the block to eat it. The last serving leaves a Thin Bone block.

## Obtaining and keeping a chop

Both [Dinosaur Chop](../items/DinosaurChop.md), `minecraft:dinosaur_chop`, and [Cooked Dinosaur Chop](../items/CookedDinosaurChop.md), `minecraft:cooked_dinosaur_chop`, are ordinary listed food-category entries. MattMC's [inventory browser](../mechanics/InventoryBrowser.md) can supply them in Survival as well as Creative, separately from recipes and natural loot. They can also be given by ID with command permission. [Category entries](https://github.com/HungLo2020/MattMC/blob/b153e7232bbb43920a8694afbdb0053c2e219d77/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1739-L1740)

**A bundled crafting or natural-loot source for the initial raw chop is not established.** No raw-chop crafting recipe, natural generation source, or entity-loot entry was found in the reviewed source and data. The registered dinosaur mobs should not be assumed to drop it. Cooking an existing raw chop is a confirmed recipe path, not a source for the first one.

**Breaking either placed chop loses the food.** Both bundled block loot tables have no entries, including no Silk Touch exception. Place it where you intend to use it rather than assuming it can be picked up again.

## Eating servings

In Survival, wait until you are hungry and interact with the placed chop using an **empty hand**. Each successful use consumes one serving.

| Variant | Hunger restored per serving | Saturation added per serving |
| --- | --- | --- |
| Raw | 3 points (1.5 drumstick icons) | 1.2 |
| Cooked | 7 points (3.5 drumstick icons) | 4.9 |

Hunger and saturation still obey their normal caps; unused nutrition is not stored for later. See [Hunger and healing](../mechanics/Hunger.md).

The chop starts at `bites=0`, progresses through 1, 2, and 3, and is replaced after the fourth serving by `minecraft:thin_bone`. The remaining bone's axis follows the chop's facing axis. Finishing it does not restore a raw or cooked chop item.

## The leftover Thin Bone

After the fourth serving, the chop becomes **Thin Bone** (`minecraft:thin_bone`) with the same facing axis. That is a placed remnant, not an awarded inventory item. Thin Bone is registered as an ordinary rotated pillar: its item placement follows the clicked face's axis, and its inherited collision is a full block despite the name. Hardness is **0.4**, not a mining-time guarantee. [Chop remainder](https://github.com/HungLo2020/MattMC/blob/b153e7232bbb43920a8694afbdb0053c2e219d77/src/main/java/net/alexscaves/server/block/DinosaurChopBlock.java#L160-L175) · [Registration](https://github.com/HungLo2020/MattMC/blob/b153e7232bbb43920a8694afbdb0053c2e219d77/src/main/java/net/minecraft/world/level/block/Blocks.java#L4354-L4363) · [Axis placement](https://github.com/HungLo2020/MattMC/blob/b153e7232bbb43920a8694afbdb0053c2e219d77/src/main/java/net/minecraft/world/level/block/RotatedPillarBlock.java#L47-L55) · [Default shape](https://github.com/HungLo2020/MattMC/blob/b153e7232bbb43920a8694afbdb0053c2e219d77/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L321-L327)

**Do not mine the remnant expecting an ordinary tool to recover it.** The block requires a correct tool for drops, but no bundled block tag assigns Thin Bone to a standard mining-tool rule. Its one-item loot entry does not bypass the player's tool check. A stronger pickaxe, Silk Touch or Fortune does not supply the missing correct-tool assignment. This is a source-reviewed standard-tool limitation, not a tested harvesting result; custom item rules or data packs may change it. [Loot](https://github.com/HungLo2020/MattMC/blob/b153e7232bbb43920a8694afbdb0053c2e219d77/src/main/resources/data/minecraft/loot_table/blocks/thin_bone.json) · [Bundled block tags](https://github.com/HungLo2020/MattMC/tree/b153e7232bbb43920a8694afbdb0053c2e219d77/src/main/resources/data/minecraft/tags/block) · [Tool-rule result](https://github.com/HungLo2020/MattMC/blob/b153e7232bbb43920a8694afbdb0053c2e219d77/src/main/java/net/minecraft/world/item/component/Tool.java#L49-L56) · [Player harvest gate](https://github.com/HungLo2020/MattMC/blob/b153e7232bbb43920a8694afbdb0053c2e219d77/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292)

There is a real [Thin Bone item](../items/ThinBone.md), listed in ordinary Natural Blocks; the [inventory browser](../mechanics/InventoryBrowser.md) can supply it in Survival and Creative. No bundled recipe produces or consumes Thin Bone. That browser route is separate from collecting an eaten chop's remnant. [Item registration](https://github.com/HungLo2020/MattMC/blob/b153e7232bbb43920a8694afbdb0053c2e219d77/src/main/java/net/minecraft/world/item/Items.java#L770) · [Category entry](https://github.com/HungLo2020/MattMC/blob/b153e7232bbb43920a8694afbdb0053c2e219d77/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L805)

## Cooking

All three bundled item recipes turn **one raw chop into one cooked chop**:

| Method | Recipe time | At 20 ticks per second |
| --- | --- | --- |
| Furnace | 200 ticks | 10 seconds |
| Smoker | 100 ticks | 5 seconds |
| Campfire | 600 ticks | 30 seconds |

The recipes specify an experience value of 0.15. Furnace/smoker experience is handled by their normal accumulated-recipe payout; the campfire completion code drops the cooked item without awarding recipe XP.

### Cooking an already placed chop

A placed raw chop can also become cooked on a successful random-tick check, with a **one-in-four chance per check**, when the heat test below it succeeds. This is separate from the fixed-time item recipes.

The check looks downward through air until it reaches another block. Accepted heat is a **lit Campfire**, a block in the **fire tag**, or **Lava**. An intervening non-air block stops the search. [Primal Magma and Fissure Primal Magma](PrimalMagma.md) are not included as heat sources in this method.

Conversion preserves the chop's facing, bites already taken, and waterlogged state. A partly eaten raw chop therefore becomes a partly eaten cooked chop, not a fresh four-serving block. The check is random-tick based; no fixed cooking time is established for this placed-block route.

## Placement, water, and integration limits

Placement chooses one of six facing directions from the opposite of the player's nearest looking direction. Its visible shape changes as servings are eaten. The placement method initializes the waterlogged state to false.

Current bucket behavior comes from the shared waterlogging interface: it permits adding or collecting water without requiring a bite first. The class contains older bite-gated bucket methods, but their signatures do not match the callbacks a bucket currently invokes.

Two other old helper methods should not be treated as working features:

- **Comparator output:** the class's old output method does not match the current direction-aware callback, and the block does not enable analog output. No serving-count comparator signal is established
- **Pistons:** the current engine reads the block's registered push reaction, which remains **NORMAL**. The older helper returning `DESTROY` does not establish automatic breakage when pushed

These are source-level distinctions, not results from a redstone or waterlogging test.

## Related pages

- [Dinosaur Chop item](../items/DinosaurChop.md)
- [Cooked Dinosaur Chop item](../items/CookedDinosaurChop.md)
- [Thin Bone item](../items/ThinBone.md)
- [Furnace](Furnace.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No gameplay test of eating, cooking, collection, waterlogging, comparator output, or piston behavior was run. Data packs can change the bundled recipes, tags, and loot tables.

- [Block registration and per-serving values](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2109-L2118), [block-item registration and stack limits](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L1715-L1716), and [Creative listing](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1739-L1740)
- [Active eating, placement, shapes, bone remainder, cooking, and old helpers](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/block/DinosaurChopBlock.java)
- [Food application and caps](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/food/FoodData.java#L19-L30) and [saturation calculation](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32)
- [Furnace recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/furnace/cooked_dinosaur_chop_smelting.json), [smoker recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/furnace/cooked_dinosaur_chop_smoking.json), and [campfire recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/furnace/cooked_dinosaur_chop_campfire.json)
- [Campfire completion](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/CampfireBlockEntity.java#L54-L85) and [furnace-family XP handling](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java)
- [Empty raw-chop loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/dinosaur_chop.json) and [empty cooked-chop loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/cooked_dinosaur_chop.json)
- [Current waterlogging callbacks](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java) and [bucket dispatch](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/BucketItem.java)
- [Current analog-output and push-reaction handling](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java) and [comparator checks](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/ComparatorBlock.java)
