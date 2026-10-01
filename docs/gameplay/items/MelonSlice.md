# Melon Slice

**Melon Slice** (`minecraft:melon_slice`) is food and a crafting ingredient. It is neither a placeable Melon block nor a seed. [Item registration][item]

## Obtaining and eating

Break a Melon without Silk Touch to collect slices. See [Melon harvesting](../blocks/PumpkinAndMelon.md#harvesting-and-keeping-the-stem) for the ordinary yield and Fortune cap.

Eating one slice restores **2 hunger points** (one food icon) and supplies **1.2 saturation**, subject to the usual food and saturation caps. See [Hunger and healing](../mechanics/Hunger.md) for those limits. [Food value][food] · [Saturation conversion][saturation]

## Crafting uses

- Make [Melon Seeds](MelonSeeds.md#crafting) for planting
- Combine slices into a [whole Melon](Melon.md#crafting)
- For **1 Glistering Melon Slice**, put **1 Melon Slice in the center** of a 3 × 3 grid and surround it with **8 Gold Nuggets** [Recipe][glistering]

The Glistering item is a brewing ingredient and is not registered as food. See [Brewing](../brewing/Brewing.md) for the Healing potion chain and the effect of adding ingredients to the wrong starting potion. [Item registrations][item]

Related: [Pumpkin and Melon farming](../blocks/PumpkinAndMelon.md) · [Melon](Melon.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-01** at `4285adff2e35307c277a3a5bf54ebd064aa5e64b`. No in-game eating, crafting, or brewing test was run. The crafting data uses the current [shaped recipe codec][codec].

[item]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/Items.java#L1746-L1793
[food]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/food/Foods.java#L28
[saturation]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32
[glistering]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/glistering_melon_slice.json
[codec]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/crafting/ShapedRecipe.java#L104-L112
