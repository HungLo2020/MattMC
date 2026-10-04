# Bowl

A Bowl (`minecraft:bowl`) is a reusable container for soups and stews. It is an ordinary item with no player-food component; an empty Bowl does not restore hunger. [Registration][bowl-item] · [Plain-item properties][plain-registration] · [Shared defaults][common-components]

## Obtaining

At a **Crafting Table**, arrange **three Planks in a shallow V**: one in the left and right slots of a row, then one centered immediately below. This makes **4 Bowls**. Each ingredient must belong to the Planks tag. The recipe is three slots wide, so it does not fit the inventory grid. [Recipe][bowl-recipe] · [Inventory grid][inventory-grid] · [Table grid][table-grid]

A Bowl is also a possible **fishing junk** catch. The active fishing hook rolls the fishing table, whose junk branch includes a Bowl; this is a random alternative to crafting. See [Fishing](../mechanics/Fishing.md) for the fishing workflow. [Fishing callback][fishing-use] · [Junk branch][fishing-root] · [Bowl entry][fishing-junk]

Bowls appear in **Ingredients** for the [inventory item browser](../mechanics/InventoryBrowser.md). Creative insertion is a separate route; a visible Survival catalog entry does not provide a Bowl. [Category entry][bowl-category]

## Usage

Use Bowls in the recipes for [Mushroom Stew](MushroomStew.md), [Beetroot Soup](BeetrootSoup.md), [Rabbit Stew](RabbitStew.md) and [Suspicious Stew](SuspiciousStew.md). Each finished serving is unstackable and has its own ingredients, food values and any effects; follow the relevant meal guide before filling a supply of Bowls. [Mushroom registration][stew-item] · [Rabbit registration][rabbit-item] · [Beetroot registration][beetroot-soup-item] · [Suspicious registration][suspicious-item]

**Use a Bowl on an adult Mooshroom** to obtain a serving without crafting. Ordinarily it produces Mushroom Stew. If a brown Mooshroom has stored flower effects, the next Bowl instead becomes Suspicious Stew with those effects, and that stored dose is cleared. Baby Mooshrooms do not take this Bowl branch. See [Mushroom Stew](MushroomStew.md) and [Suspicious Stew](SuspiciousStew.md) for preparing the animal and handling flowers. [Bowl interaction][mooshroom] · [Flower storage][mooshroom-flower]

In Survival, filling spends one empty Bowl. With several Bowls in the held stack, the serving goes into available inventory space or is dropped when it cannot fit; with the last Bowl, it replaces the held item. [Filled-result handling][filled-bowl]

## Behavior

**Finishing one of these meals in ordinary Survival returns one Bowl.** The serving is consumed first and its use remainder then replaces it, so you can reuse the container for the next meal. This is a completed eating action, not simply holding the stew. Creative eating keeps the serving and does not generate an extra Bowl. [Completed-use callback][finish-use] · [Remainder dispatch][remainder-dispatch] · [Conversion rules][use-remainder] · [Consumption exemption][consume-count] · [Creative ability][creative]

Keep inventory space for the filled meals: empty Bowls use the ordinary **64-item stack limit**, while each of the four meals above occupies a slot. [Shared stack default][common-components] · [Plain registration][plain-registration]

## Notes

An empty Bowl is also furnace fuel, with **100 base burn ticks**. That supplies only half the time of a 200-tick smelt; it is not one full smelting operation per Bowl. Burning it spends the reusable container. See [Smelting](../smelting/Smelting.md) for fuels and cook times. [Server setup][server-setup] · [Base duration][fuel-base] · [Bowl fuel entry][bowl-fuel] · [Fuel consumption][furnace-use] · [Duration lookup][furnace-duration]

Related: [Items](Items.md)

## Sources and verification

Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on 2026-10-04. Checked crafting, the active fishing-to-junk path, adult Mooshroom filling, food remainders, stack defaults and furnace fuel consumption. No in-game crafting, fishing, filling, eating, smelting or insertion test was run. Data packs can replace recipes and loot. [Active recipe loading][recipe-loader]

[bowl-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1287
[plain-registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2792-L2798
[common-components]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[bowl-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/bowl.json
[inventory-grid]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/InventoryMenu.java#L48-L50
[table-grid]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/CraftingMenu.java#L38-L40
[fishing-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/FishingHook.java#L443-L454
[fishing-root]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/gameplay/fishing.json#L1-L15
[fishing-junk]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/gameplay/fishing/junk.json#L70-L73
[bowl-category]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1868-L1872
[stew-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1350
[rabbit-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2124
[beetroot-soup-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2240
[suspicious-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2379-L2386
[mooshroom]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/animal/MushroomCow.java#L94-L118
[mooshroom-flower]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/animal/MushroomCow.java#L127-L166
[filled-bowl]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemUtils.java#L15-L33
[finish-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3255-L3267
[remainder-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L399-L412
[use-remainder]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/UseRemainder.java#L14-L27
[consume-count]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1079
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/GameType.java#L62-L79
[server-setup]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/MinecraftServer.java#L338-L340
[fuel-base]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L34-L43
[bowl-fuel]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L84-L89
[furnace-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L163-L177
[furnace-duration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L264-L273
[recipe-loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L88
