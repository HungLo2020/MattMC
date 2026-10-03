# Warped Fungus on a Stick

**Warped Fungus on a Stick** (`minecraft:warped_fungus_on_a_stick`) attracts [Striders](../mobs/Strider.md) and controls a saddled one while held. It has **100 durability** and costs **1 durability per successful boost** before applicable durability modifiers. It is a separate steering tool from [Carrot on a Stick](CarrotOnAStick.md). [Item registration][tool-items] · [Tempting tag][strider-tempt] · [Controller requirement][strider-control] · [Boost action][boost-use]

## Obtaining

Place **1 [Fishing Rod](FishingRod.md) diagonally above-left of 1 [Warped Fungus](WarpedFungus.md)** to craft **1 Warped Fungus on a Stick**. The two-by-two shaped recipe fits the inventory crafting grid. Its bundled recipe ID is `minecraft:crafting/warped_fungus_on_a_stick`. This ordinary shaped recipe copies its defined output; it does not transfer a rod's enchantments or damage into the new tool. [Exact recipe][tool-recipe] · [Recipe loading][recipe-load] · [Result assembly][shaped-result]

The item is also listed in MattMC's [inventory item browser](../mechanics/InventoryBrowser.md), which provides an independent insertion route in Creative. A Strider's conditional Zombified Piglin jockey also receives this item as held equipment; this is not a guaranteed drop from an ordinary Strider. [Category entry][equipment-category] · [Jockey equipment][strider-jockey] · [Equipment-drop conditions][equipment-drops]

## Usage

Equip an adult Strider with a [Saddle](Saddle.md), mount it and hold this tool in either hand. The first player passenger then controls its facing, with a constant forward riding input. Merely holding the tool nearby attracts a Strider; it does not mount or saddle it. Feed ordinary Warped Fungus for breeding and baby growth. [Control requirement][strider-control] · [Ridden input][strider-riding] · [Tempting items][strider-tempt] · [Breeding food][strider-food] · [Mounting][strider-breeding]

## Behavior

Use the tool while controlling a Strider to request a boost. The boost only starts if the controlled vehicle matches the registered Strider type and is not already boosting. An accepted boost costs **1 durability**; holding the item for steering alone does not call this wear action. [Target and cost][tool-items] · [Use checks][boost-use] · [Already-boosting guard][boost-curve]

The active random calculation selects a duration parameter of **140–980 ticks** and uses a changing sine-wave speed factor, rising from its base toward **2.15 times** the unboosted ridden factor before falling again. Progress advances in the controlled riding handler. The parameter is not a guaranteed real-time boost length after the rider stops controlling the animal. [Actual random range and curve][boost-curve] · [Progress caller][strider-riding]

**Do not expect a Fishing Rod back when this tool wears out in the reviewed MattMC snapshot.** The helper requests that conversion only when the old stack becomes empty; the current damage path retains a nonempty broken stack instead. A fresh, unmodified Survival tool therefore becomes broken on its **100th successful boost**, and further use is rejected until repaired. The Strider's held-item steering check itself does not test the broken state, so this is specifically a boosting/use limit rather than proof that holding it stops steering. [Conversion call][boost-use] · [Retained broken state and conversion condition][break-state] · [Use guard][broken-use] · [Held-item control test][strider-control] · [Both-hand lookup][held-hands]

See [Durability](../mechanics/Durability.md) for MattMC's retained broken items and repair behavior. The wear-out and continued-steering statements above are source deductions, not a tested riding cycle.

## Notes

The bundled item registration targets **Striders only**. It has no Horse or Happy Ghast steering role. Its recipe output, 100 durability and 1-point boost cost are distinct from the Pig's Carrot on a Stick. [Both registrations][tool-items] · [Type check][boost-use]

## Related pages

- [Strider](../mobs/Strider.md), [Strider Spawn Egg](StriderSpawnEgg.md) and [Saddle](Saddle.md)
- [Warped Fungus](WarpedFungus.md), [Fishing Rod](FishingRod.md) and [Carrot on a Stick](CarrotOnAStick.md)
- [Items](Items.md)

## Verification scope

Source-reviewed at MattMC commit `bb9a8a060da02b64f23508b77794fb0f79307de4` on 2026-10-02. Recipe, category listing, item components, active boosting/steering and broken-stack dispatch were inspected. No in-game crafting, boost-duration, wear-out, steering or repair test was run.

[tool-items]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/Items.java#L1190-L1195
[strider-tempt]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/item/strider_tempt_items.json
[strider-control]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/monster/Strider.java#L190-L196
[boost-use]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/FoodOnAStickItem.java#L23-L38
[tool-recipe]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/crafting/warped_fungus_on_a_stick.json
[recipe-load]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L85
[shaped-result]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/crafting/ShapedRecipe.java#L61-L82
[equipment-category]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1494-L1512
[strider-jockey]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/monster/Strider.java#L424-L461
[equipment-drops]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/Mob.java#L813-L838
[strider-riding]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/monster/Strider.java#L241-L257
[strider-food]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/item/strider_food.json
[strider-breeding]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/monster/Strider.java#L373-L417
[boost-curve]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/ItemBasedSteering.java#L26-L49
[break-state]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/ItemStack.java#L449-L524
[broken-use]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/ItemStack.java#L382-L396
[held-hands]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2082-L2088
