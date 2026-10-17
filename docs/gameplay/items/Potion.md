# Potion

A **Potion** (`minecraft:potion`) is a drinkable bottle whose contents determine its effects. Water Bottles, Awkward Potions, and named effect potions share this item ID; their stored contents distinguish them. Each bottle occupies one inventory slot. [Item properties][item] · [Stored contents][contents]

## Obtaining

Fill a [Glass Bottle](GlassBottle.md#crafting-and-filling-with-water) at a water source to get a Water Bottle. Use a fueled [Brewing Stand](../blocks/BrewingStand.md) and a registered ingredient combination to change its contents. A common start is **Water + Nether Wart → Awkward**, followed by an effect ingredient; **Water + Fermented Spider Eye → Weakness** is a direct exception. Follow [Brewing](../brewing/Brewing.md) for the batch workflow, modifiers, and recipe examples. [Water collection][bottle] · [Registered combinations][brew] · [Active stand][stand]

The [Inventory Browser](../mechanics/InventoryBrowser.md) lists enabled potion variants for **Creative insertion**. A visible entry does not establish a Survival source or a brewing recipe: [Luck](../effects/LuckAndUnluck.md#luck), for example, has no ingredient recipe in the checked brewing list. [Potion listings][catalog] · [Type enumeration][catalog-types] · [Brewing list][brew]

## Usage

Hold use until drinking finishes: an ordinary potion takes **32 game ticks**, normally **1.6 seconds at 20 ticks per second**. You can drink with a full hunger bar. Effects are applied when consumption finishes, so releasing early does not give a partial dose. Ordinary Survival consumption uses the potion and leaves a **Glass Bottle** in the hand. Creative keeps the potion and does not create a drinking remainder. [Drink settings][drink-settings] · [Start and completion][item-use] · [Use countdown][countdown] · [Consumption][consume] · [Bottle return][remainder-call] [remainder]

For throwing, brew the potion with **Gunpowder** to make a [Splash Potion](SplashPotion.md). Add **Dragon's Breath** to that splash form for a [Lingering Potion](LingeringPotion.md). These conversions retain the registered potion type; they do not preserve every custom item component. [Container recipes][forms] · [Conversion output][conversion]

## Behavior

Drinking applies the contents to the drinker. Timed effects use their stored duration at the drinkable item's normal scale; instant effects are applied directly. Ordinary Water, Awkward, Mundane, and Thick potions have **no status effects**. Use the [effects guides](../effects/Effects.md) for each effect's strength, duration, removal, and recipient limits, especially [Healing and Harming](../effects/InstantEffects.md). [Application][apply] · [Empty-effect bases][bases]

A **Water Bottle with no custom effects** also has block uses:

- Use it on the **top or side of Dirt, Coarse Dirt, or Rooted Dirt** to make Mud and return a Glass Bottle in Survival. See [Mud production](../blocks/MudAndMudBricks.md#making-a-continuing-supply) for refilling and the separate Dispenser route
- Use it on an **empty Cauldron or partly filled Water Cauldron** to add one water level and return a Glass Bottle. See [Cauldron bottle exchanges](../blocks/Cauldrons.md#bucket-and-bottle-transactions) for filling limits and Creative handling

These interactions check Water contents with an empty custom-effect list; a Water base carrying custom effects does not pass that check. [Soil use][soil] · [Accepted soil][soil-tag] · [Water match][water-match] · [Bottle exchange][exchange] · [Cauldron entries][cauldron]

## Notes

Source-reviewed on **2026-10-03** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`. No in-game brewing, drinking, block-use, inventory, or timing test was run. These are checked acquisition routes and ordinary item defaults; custom components, data packs, and server rules can change them.

Related: [Brewing](../brewing/Brewing.md) · [Splash Potion](SplashPotion.md) · [Lingering Potion](LingeringPotion.md) · [Tipped Arrow](TippedArrow.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1770-L1778
[contents]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionContents.java#L41-L53
[bottle]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/BottleItem.java#L45-L75
[brew]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L133-L190
[stand]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/BrewingStandBlockEntity.java#L96-L190
[catalog]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1756-L1770
[catalog-types]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2207-L2213
[drink-settings]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L71-L73
[item-use]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Item.java#L173-L196
[countdown]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3168-L3172
[consume]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumable.java#L62-L102
[remainder-call]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ItemStack.java#L399-L418
[remainder]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/UseRemainder.java#L14-L27
[forms]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L133-L138
[conversion]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L101-L123
[apply]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionContents.java#L154-L166
[bases]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/Potions.java#L11-L14
[soil]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/PotionItem.java#L34-L69
[soil-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/block/convertable_to_mud.json
[water-match]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionContents.java#L79-L80
[exchange]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ItemUtils.java#L15-L38
[cauldron]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/core/cauldron/CauldronInteraction.java#L53-L123
