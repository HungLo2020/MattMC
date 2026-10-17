# Splash Potion

A **Splash Potion** (`minecraft:splash_potion`) is a throwable bottle that delivers its contents on impact. It stacks to **one**. Its potion type is stored on the item, so differently named splash potions share the same item ID. [Item properties][item] · [Contents and naming][contents]

## Obtaining

Brew a drinkable [Potion](Potion.md) with **Gunpowder** to make a Splash Potion of the same registered type. A splash bottle also accepts registered potion-content recipes: **Splash Water + Nether Wart → Splash Awkward**, for example. Follow [Brewing](../brewing/Brewing.md) for ingredients, fuel, and batch handling. There is no registered reverse conversion to a drinkable bottle. [Container and content recipes][brew] · [Output construction][conversion]

Enabled splash variants appear in the [Inventory Browser](../mechanics/InventoryBrowser.md) for **Creative insertion**. Catalog visibility does not supply them in ordinary Survival or create a missing brewing recipe. [Listed forms][catalog] · [Enabled types][catalog-types]

## Usage

Use the bottle while aiming to **throw it immediately**. A normal Survival throw consumes one bottle and returns **no Glass Bottle**; Creative throwing keeps the item. A [Dispenser](../blocks/DispenserAndDropper.md) launches one selected splash bottle and consumes that bottle from its inventory. [Throwing][throw] · [Consumption rule][consume] · [Dispenser registration][dispense-register] · [Dispenser launch][dispense]

An effect splash can reach several nearby susceptible entities, including unintended recipients. Distance reduces timed-effect duration or instant-effect strength, and timed results of **20 ticks or less** are omitted. A nearby target is not guaranteed the full drinkable duration. See [shared delivery rules](../effects/MovementEffects.md#delivery-changes-duration) and [Healing/Harming delivery](../effects/InstantEffects.md#drinking-splashing-clouds-and-arrows) for the geometry and recipient differences. [Splash application][splash]

Brew a Splash Potion with **Dragon's Breath** for a [Lingering Potion](LingeringPotion.md), whose effect-bearing impact creates a cloud. A Splash Potion applies its effect at impact without creating that lingering cloud. [Form conversion][forms] · [Splash callback][splash]

## Behavior

**Water with no custom effects** takes a separate impact path: it can extinguish nearby burning living entities, hurt water-sensitive ones, and rehydrate nearby Axolotls. On block impact it also checks nearby fire, lit candles, and lit campfires for extinguishing. These checks have their own bounds; this is not a promise to extinguish every fire in an arbitrary four-block sphere. Other contents with no effects, such as ordinary Awkward, Mundane, or Thick, do not apply status effects. A Water base with custom effects instead follows effect-splash handling. [Impact and Water branches][impact] · [Water match][water-match] · [Effect presence][has-effects]

### Direct Water use on soil

The checked source predicts a separate interaction for a **Splash Water Bottle with no custom effects**: use it directly on the **top or side of Dirt, Coarse Dirt, or Rooted Dirt**, and the successful block interaction makes Mud before throwing can run. In Survival this consumes the bottle and returns a Glass Bottle. Creative retains the splash bottle and may add a missing Glass Bottle to the inventory. **This inherited interaction has not been tested in game.** Throwing a Water projectile at soil, including from a Dispenser, does not perform this conversion. [Inherited block use][soil] [inheritance] · [Soil tag][soil-tag] · [Block-before-air dispatch][dispatch] · [Server item callback][server-use] · [Bottle exchange][exchange] · [Separate projectile actions][impact] [dispense-register]

## Notes

Source-reviewed on **2026-10-03** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`, including the active throw and block-use dispatch. No in-game brewing, throwing, dispensing, soil conversion, inventory, or effect-duration test was run. Custom contents, recipient rules, data packs, and server timing can change the result.

Related: [Potion](Potion.md) · [Lingering Potion](LingeringPotion.md) · [Effects](../effects/Effects.md) · [Mud production](../blocks/MudAndMudBricks.md#making-a-continuing-supply) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L2242-L2244
[contents]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/PotionItem.java#L72-L76
[brew]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L133-L190
[conversion]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L101-L123
[catalog]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1756-L1770
[catalog-types]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2207-L2213
[forms]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L133-L138
[inheritance]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/SplashPotionItem.java#L15-L42
[throw]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ThrowablePotionItem.java#L15-L40
[consume]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1079
[dispense-register]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L66-L76
[dispense]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/core/dispenser/ProjectileDispenseBehavior.java#L25-L42
[splash]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/ThrownSplashPotion.java#L38-L73
[impact]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/AbstractThrownPotion.java#L49-L122
[water-match]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionContents.java#L79-L80
[has-effects]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionContents.java#L146-L148
[soil]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/PotionItem.java#L34-L69
[soil-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/block/convertable_to_mud.json
[dispatch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/Minecraft.java#L1806-L1838
[server-use]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L340-L399
[exchange]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ItemUtils.java#L15-L38
