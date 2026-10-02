# Green Dye

**Green Dye** (`minecraft:green_dye`) supplies the green color used by dye recipes and dyeable animals. Smelting [Cactus](Cactus.md) provides a renewable source. [Registration][item] · [Smelting recipe][smelting]

## Smelting

Smelt **one cactus** in a fueled [Furnace](../blocks/Furnace.md) to obtain **one Green Dye**. The bundled recipe takes **200 ticks**, or **10 seconds at 20 ticks per second**, and has a value of **1.0 recipe XP per cactus**. [Recipe][smelting] · [Default result count][result-count] · [Furnace recipe type][furnace]

That XP value is recorded by the furnace's recipe tracking; it is not an immediate reward for inserting cactus or an automatic payout into a Hopper. Use [Furnace experience handling](../blocks/Furnace.md#experience-and-troubleshooting) for collection details and [fuel planning](../blocks/Furnace.md#fuel-planning) for fuel amounts. [Recipe tracking and payout][xp]

A [Wandering Trader](../mobs/WanderingTrader.md) can also offer **three Green Dye for one Emerald**, with **12 uses** of that selected offer. This is one possibility in a randomized offer pool. [Offer][trade] · [Offer construction][offer] · [Trader selection][selection] · [Randomized choices][random-offers]

## Dyeing and crafting

Use Green Dye on a **living, unsheared Sheep of another color** to turn its fleece green, consuming one dye in Survival. The [Sheep guide](../mobs/Sheep.md#dyeing-and-offspring-color) explains the resulting wool supply and breeding rules. A sheared sheep must regrow its fleece before this dye interaction works. [Dye interaction][sheep]

Green Dye can also color the **text on the sign face you are using**. That face must contain text, and the sign must be unwaxed and not being edited by another player; the player must be allowed to build, and the application must change the text color. A successful Survival application consumes one dye. [Dye application][sign-color] · [Nonempty text check][sign-text] · [Sign checks and consumption][sign]

Two shapeless color-mixing recipes are checked here:

| Ingredients | Result |
| --- | --- |
| **1 Green Dye + 1 [White Dye](WhiteDye.md)** | **2 [Lime Dye](LimeDye.md)** |
| **1 Green Dye + 1 [Blue Dye](BlueDye.md)** | **2 [Cyan Dye](CyanDye.md)** |

These recipes accept the named dye items. [Lime recipe][lime] · [Cyan recipe][cyan]

For building materials, use each output's exact recipe page:

- [Green Wool](GreenWool.md#obtaining) and [Green Carpet](GreenCarpet.md#crafting) for recoloring existing wool or carpet
- [Green Stained Glass](GreenStainedGlass.md) and [Green Stained Glass Pane](GreenStainedGlassPane.md) for colored windows
- [Green Terracotta](GreenTerracotta.md) and [Green Concrete Powder](GreenConcretePowder.md) for other green building materials

These linked pages own their ingredient counts, permitted inputs, and output counts. The recipes above are a selected set of uses, not an inventory of every dyeable item or animal.

Related: [Cactus farming](../blocks/Cactus.md) · [Wool and Carpet](../blocks/WoolAndCarpet.md) · [Furnace](../blocks/Furnace.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Checked item registration, cactus smelting, the two color mixes, the linked building-material recipes, trader selection, and current Sheep/sign interaction dispatch. Recipe loading is data-driven. No in-game smelting, XP collection, trading, crafting, Sheep-dyeing, or sign-dyeing test was run. Data packs can change recipes.

[item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L1708
[smelting]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/smelting/green_dye.json
[result-count]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ItemStack.java#L106-L117
[furnace]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/FurnaceBlockEntity.java#L11-L16
[xp]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L353-L391
[trade]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L790-L805
[offer]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1438-L1479
[selection]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java#L133-L141
[sheep]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/DyeItem.java#L25-L38
[sign-color]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/DyeItem.java#L48-L55
[sign]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/SignBlock.java#L89-L115
[lime]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/lime_dye.json
[cyan]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/cyan_dye.json
[sign-text]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/SignApplicator.java#L8-L13
[random-offers]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L233
