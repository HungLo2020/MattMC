# Sugar

Sugar (`minecraft:sugar`) is a **crafting and brewing ingredient**. The ordinary item has no player-food or consumable component, so holding use does not eat it or restore hunger. Its animal-feeding uses are separate. [Registration][sugar-item] · [Plain-item properties][plain-registration] · [Shared defaults][common-components] · [Use dispatch][item-use]

## Obtaining

Both crafting recipes fit the inventory grid and are shapeless:

- **1 Sugar Cane → 1 Sugar**. Use [Sugar Cane](SugarCane.md) for a repeatable crop supply. [Recipe][sugar-cane]
- **1 Honey Bottle → 3 Sugar**, leaving **1 Glass Bottle** as the crafting remainder. See [Honey Bottle](HoneyBottle.md) for collecting honey. [Recipe][sugar-honey] · [Bottle remainder registration][honey-remainder] · [Remainder lookup][craft-remainder] · [Crafting consumption and return][craft-take]

Sugar is also a possible **Witch death-loot** result; the selected entry has a base count of **0–2**, with a Looting bonus, so a kill does not guarantee Sugar. MattMC's [Anteater digging behavior](../mobs/Anteater.md#digging-dirt) can drop **1–2 Sugar** through its installed raid goal. Neither source requires treating Sugar as player food. [Witch entry][witch-sugar] · [Entity loot dispatch][entity-loot] · [Anteater goal installation][anteater-goal] · [Sugar drop][anteater-sugar]

Sugar is listed in **Ingredients** for the [inventory item browser](../mechanics/InventoryBrowser.md). Creative insertion is available separately from these Survival routes; seeing it in the Survival catalog does not supply it. [Category entry][sugar-category]

## Usage

For cooking and crafting:

- **Pumpkin Pie:** one Pumpkin, one Sugar and one item from the Eggs tag, in any arrangement, produce one pie. [Recipe][pie]
- **Fermented Spider Eye:** one Spider Eye, one Brown Mushroom and one Sugar, in any arrangement, produce one. [Recipe][fermented-eye]
- **Cake:** at a Crafting Table, put three Milk Buckets across the top, Sugar–Egg–Sugar across the middle, and three Wheat across the bottom. This uses **two Sugar** for one Cake. [Recipe][cake]

For **Swiftness**, add Sugar to **Awkward Potions** in a fueled [Brewing Stand](../blocks/BrewingStand.md). The ordinary potion grants Speed I for **3 minutes** at 20 TPS. Redstone extends it to **8 minutes**; Glowstone makes Speed II for **1 minute 30 seconds**. Sugar added directly to Water Bottles makes **Mundane Potions**, so prepare the Awkward base first. [Sugar mixes][sugar-brewing] · [Water/Awkward routing][start-mix] · [Active brewing][brew-use] · [Potion effects][speed-potions]

## Behavior

[Horses, Donkeys and Mules](../mobs/Horse.md#taming-and-feeding) can accept Sugar for **1 health point**, **30 seconds of baby growth** at normal tick speed and **3 temper**, subject to the feeding conditions in that guide. A healthy tame adult does not consume it just for being a food item. Sugar neither starts this family's love mode nor lures it. [Feeding and consumption][horse-feed] · [Lure tag][horse-lure]

[Crows](../mobs/Crow.md#taming-and-food) can eat Sugar carried in the beak and heal up to **4 health points**. Wild Crows can eat it at full health; tame ones wait until injured. Sugar is food for this path, not the Pumpkin Seeds required by the Crow's taming path. [Sugar food tag][crow-tag] · [Food/pickup check][crow-food] · [Eating and taming check][crow-eat]

## Notes

Crafting Sugar from honey spends the honey rather than granting its drinking effects. Choose whether you need food, bottled honey or an ingredient before converting it. Related: [Brewing](../brewing/Brewing.md) · [Crafting](../crafting/Crafting.md) · [Items](Items.md)

## Sources and verification

Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on 2026-10-04. Checked the non-food registration, exact recipes and bottle return, selected drops, active brewing and animal consumers. No in-game crafting, looting, brewing, feeding or insertion test was run. Data packs can replace recipes, tags and loot. [Active recipe loading][recipe-loader]

[sugar-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1711-L1714
[plain-registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2792-L2798
[common-components]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[item-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L170-L196
[sugar-cane]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/sugar_from_sugar_cane.json
[sugar-honey]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/sugar_from_honey_bottle.json
[honey-remainder]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2477-L2479
[craft-remainder]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/CraftingRecipe.java#L18-L30
[craft-take]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L72-L108
[witch-sugar]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/witch.json#L31-L54
[entity-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1527
[anteater-goal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityAnteater.java#L88-L100
[anteater-sugar]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/ai/AnteaterAIRaidNest.java#L35-L50
[sugar-category]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1882-L1889
[pie]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/pumpkin_pie.json
[fermented-eye]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/fermented_spider_eye.json
[cake]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/cake.json
[sugar-brewing]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L163-L167
[start-mix]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L230-L234
[brew-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/BrewingStandBlockEntity.java#L170-L187
[speed-potions]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/alchemy/Potions.java#L32-L34
[horse-feed]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L410-L484
[horse-lure]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/horse_tempt_items.json
[crow-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/crow_foodstuffs.json
[crow-food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityCrow.java#L578-L604
[crow-eat]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityCrow.java#L305-L332
[recipe-loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L88
