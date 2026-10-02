# Mushroom Stew

Mushroom Stew (`minecraft:mushroom_stew`) is an **unstackable food** that normally leaves a Bowl after eating. It restores **6 hunger points and 7.2 saturation points before caps**. [Item registration][stew-reg] · [Food values][stew-food] [Saturation modifier][stew-saturation] · [Saturation calculation][food-builder] [Saturation formula][saturation-math]

## Obtaining

Combine **one Brown Mushroom, one Red Mushroom and one Bowl** in any arrangement to make **one Mushroom Stew**. This shapeless recipe fits the inventory's two-by-two crafting grid. The [Mushrooms guide](../blocks/Mushrooms.md) owns mushroom collection and growing. [Exact recipe][stew-recipe] · [Shapeless execution][shapeless] · [Ingredient consumption][craft-consume]

Use a Bowl on an **adult [Mooshroom](../mobs/Mooshroom.md#bowls-milk-and-flower-servings)** for another serving. If it has a stored flower effect, that next Bowl produces Suspicious Stew instead. The animal interaction does not use up a mushroom from your inventory. [Bowl callback][moosh-bowl]

The item is also ordinarily listed in the [inventory item browser](../mechanics/InventoryBrowser.md), whose insertion route works in Survival and Creative independently of crafting and livestock. [Category entry][stew-entry]

## Usage

Hold use to eat. The default food action lasts **32 ticks**, nominally 1.6 seconds at 20 TPS, and ordinary Survival eating requires missing hunger. The food adds its values through the shared consumption listener; [Hunger](../mechanics/Hunger.md) explains caps and healing. [Food registration][food-reg] · [Duration][eat-duration] · [Consumption and hunger gate][consume] · [Food listener][food-listener]

A consumed Survival serving returns one Bowl through the use-remainder path. Keep the Bowl for the next recipe or Mooshroom serving. [Remainder dispatch][remainder] · [Bowl replacement][bowl-return]

## Behavior

Ordinary Mushroom Stew has no flower-effect component in its registration. Choose [Suspicious Stew](SuspiciousStew.md) when the stored flower effect is part of your plan. [Item components][stew-reg]

## Notes

Related: [Mooshroom](../mobs/Mooshroom.md) · [Mushrooms](../blocks/Mushrooms.md) · [Items](Items.md)

## Sources and verification

Source-reviewed at `cf8cd5371bd1de61411ae5e7e144aabe3edb1e54` on 2026-10-02. Checked exact recipe, adult Bowl callback, category listing, food/saturation, hunger gate and remainder handling. No crafting, livestock, inventory insertion or eating test was performed.

[stew-reg]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/Items.java#L1350
[stew-food]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/food/Foods.java#L29-L30
[stew-saturation]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/food/Foods.java#L59-L61
[food-builder]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/food/FoodProperties.java#L60-L83
[saturation-math]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32
[stew-recipe]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/recipe/crafting/mushroom_stew.json#L1-L13
[shapeless]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L58-L91
[craft-consume]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L81-L98
[moosh-bowl]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/MushroomCow.java#L93-L118
[stew-entry]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1746
[food-reg]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/Item.java#L366-L375
[eat-duration]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/component/Consumable.java#L132-L137
[consume]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/component/Consumable.java#L77-L101
[food-listener]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/food/FoodProperties.java#L39-L56
[remainder]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/ItemStack.java#L399-L412
[bowl-return]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/component/UseRemainder.java#L11-L27
