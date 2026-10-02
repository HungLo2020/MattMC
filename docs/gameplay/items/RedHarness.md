# Red Harness

**Red Harness** (`minecraft:red_harness`) is the red variant of [Happy Ghast riding equipment](Harnesses.md). Its color changes the Harness appearance; the shared adult-only equipment and four-passenger rules are on the [Harness family guide](Harnesses.md#equipping-and-riding). [Registered colors][harness-items] · [Color asset and equipment][harness-component]

## Obtaining

Craft **1 Red Harness** with **3 Leather, 2 Glass and 1 Red Wool**. Put Leather–Leather–Leather above Glass–Red Wool–Glass in a Crafting Table. Alternatively, combine **1 Red Dye** with **1 Harness of any other color** in a shapeless recipe. A Red Harness is excluded as the input to its own recoloring recipe. [Exact crafting recipe][craft-red] · [Exact recoloring recipe][dye-red]

It is also listed in the [inventory item browser](../mechanics/InventoryBrowser.md), including MattMC's Survival insertion route. [Category entries][equipment-category]

## Usage

<span id="usage_1"></span>

Use it on a living adult [Happy Ghast](../mobs/HappyGhast.md) with an empty body slot, then interact to ride. For dispenser placement, passenger limits and control, see [equipping a Harness](Harnesses.md#equipping-and-riding). To change an equipped color, follow [removal and recoloring](Harnesses.md#removing-and-recoloring-a-harness); applying a second Harness does not replace the occupied slot. [Adult interaction][happy-equip] · [Empty-slot equip action][equip-action]

## Behavior

This variant uses the same nonstackable equipment component as every other Harness color. Shared [recipes, components and recovery](Harnesses.md) are canonical; red does not provide a different seat count or movement rule. [Registration][harness-items] · [Component][harness-component] · [Capacity][happy-seats]

## Notes

Recipe IDs are `minecraft:crafting/red_harness` and `minecraft:crafting/dye_red_harness`. See [all sixteen colors](Harnesses.md#colors-and-exact-recipes) or return to [Items](Items.md). Source-reviewed at `bb9a8a060da02b64f23508b77794fb0f79307de4` on 2026-10-02; no in-game crafting or equipment test was run.

[harness-items]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/Items.java#L1127-L1174
[harness-component]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/equipment/Equippable.java#L111-L120
[craft-red]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/red_harness.json
[dye-red]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/dye_red_harness.json
[equipment-category]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1494-L1512
[happy-equip]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/animal/HappyGhast.java#L254-L290
[equip-action]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/equipment/Equippable.java#L159-L175
[happy-seats]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/animal/HappyGhast.java#L292-L332
