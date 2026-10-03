# Dyes

Dyes supply **16 named colors** for materials, animals, text and equipment. First choose a dye recipe, then choose the operation that accepts it: recoloring wool, adding a Banner pattern and mixing equipment colors are different actions. All sixteen are registered as Dye items and normally stack to **64**. [Dye registrations] · [Item defaults] · [Stack default]

## Choosing a recipe

The color pages below give **every ordinary bundled recipe that produces that dye**, with exact input quantities and outputs. Green Dye keeps its established smelting guide. The starting choices here help locate an ingredient; they are not a promise that a plant grows at your current position.

| Color and exact item ID | Recipe starting points |
| --- | --- |
| [White Dye](WhiteDye.md) (`minecraft:white_dye`) | Bone Meal or Lily of the Valley |
| [Light Gray Dye](LightGrayDye.md) (`minecraft:light_gray_dye`) | Azure Bluet, Oxeye Daisy, White Tulip, or White Dye mixtures |
| [Gray Dye](GrayDye.md) (`minecraft:gray_dye`) | Closed Eyeblossom, or Black + White Dye |
| [Black Dye](BlackDye.md) (`minecraft:black_dye`) | Ink Sac or Wither Rose |
| [Brown Dye](BrownDye.md) (`minecraft:brown_dye`) | Cocoa Beans |
| [Red Dye](RedDye.md) (`minecraft:red_dye`) | Poppy, Red Tulip, Beetroot or Rose Bush |
| [Orange Dye](OrangeDye.md) (`minecraft:orange_dye`) | Orange Tulip, Torchflower, Open Eyeblossom, or Red + Yellow Dye |
| [Yellow Dye](YellowDye.md) (`minecraft:yellow_dye`) | Dandelion, Wildflowers or Sunflower |
| [Lime Dye](LimeDye.md) (`minecraft:lime_dye`) | Smelt Sea Pickle, or mix Green + White Dye |
| [Green Dye](GreenDye.md) (`minecraft:green_dye`) | Smelt Cactus |
| [Cyan Dye](CyanDye.md) (`minecraft:cyan_dye`) | Pitcher Plant, or Blue + Green Dye |
| [Light Blue Dye](LightBlueDye.md) (`minecraft:light_blue_dye`) | Blue Orchid, or Blue + White Dye |
| [Blue Dye](BlueDye.md) (`minecraft:blue_dye`) | Lapis Lazuli or Cornflower |
| [Purple Dye](PurpleDye.md) (`minecraft:purple_dye`) | Blue + Red Dye |
| [Magenta Dye](MagentaDye.md) (`minecraft:magenta_dye`) | Allium, Lilac, or one of three dye mixtures |
| [Pink Dye](PinkDye.md) (`minecraft:pink_dye`) | Pink Tulip, Peony, Pink Petals, Cactus Flower, or Red + White Dye |

**Convert ingredients before substituting dyes.** Bone Meal, Lapis Lazuli, Ink Sacs and flowers are separate item IDs; a recipe naming White, Blue or Black Dye does not automatically accept the raw material used to make it. The checked dye-producing recipes name exact items rather than ingredient tags. [Ingredient matching]

All the dye-making crafting recipes are shapeless and use at most four occupied slots, so they fit the inventory's **2×2 crafting grid**. Put repeated ingredients in separate slots: the four-dye Magenta recipe needs one Blue, two Red and one White, with the two Red Dye occupying two slots. Taking a crafting result consumes one item from each occupied ingredient slot. [Inventory grid] · [Inventory crafting dispatch] · [Shapeless matching] · [Input counting] · [Ingredient consumption] · [Four-item Magenta recipe]

For repeated supplies, use the existing [Flowers](../blocks/Flowers.md#harvesting-and-propagation), [Cocoa](../blocks/Cocoa.md), [Cactus](../blocks/Cactus.md), [Sea Pickle](../blocks/SeaPickle.md), [flowerbed](../blocks/FlowerbedsAndLeafLitter.md), [Eyeblossom](../blocks/Eyeblossoms.md), [Torchflower](../blocks/Torchflower.md) and [Pitcher Plant](../blocks/PitcherPlant.md) guides. Their planting, harvesting and propagation rules differ.

## Browser and trading

All sixteen dyes appear in an ordinary category used by the [inventory item browser](../mechanics/InventoryBrowser.md). The checked MattMC browser can insert listed items in **Creative**, subject to its cursor, inventory-space and enabled-item checks. This is a separate route from processing ingredients. [Dye category entries] · [Browser list construction] · [Client request] · [Server insertion]

A [Wandering Trader](../mobs/WanderingTrader.md)'s offer pool includes **three of any one dye color for one Emerald**, with **12 uses** when that offer is selected. Each color is a separate possible offer. Traders randomly choose from their pools, so a particular trader need not sell your target color. See [Trading](../trading/Trading.md) for using offers. [All dye offers] · [Offer construction] · [Trader offer dispatch] · [Random offer selection]

## Choosing how to apply a dye

### Sheep and pet collars

Use a dye on a **living, unsheared Sheep of another color** to replace its fleece color. The interaction has no adult-only check. A successful ordinary Survival application spends one dye; the player's Creative interaction path preserves the held supply. A sheared sheep must regrow fleece before this dye action works. Use [Sheep](../mobs/Sheep.md#dyeing-and-offspring-color) for offspring-color rules and [shearing/regrowth](../mobs/Sheep.md#shearing-and-regrowth) for repeated wool harvests. [Sheep dye action] · [Player interaction and Creative handling]

Use a **different-color dye on your own tamed Cat or Wolf** to recolor its collar. This colors the collar, not the animal's coat or its equipped armor. A successful change consumes one dye in Survival; Creative's infinite-material handling skips that cost. See [Cat controls](../mobs/Cat.md#sitting-following-and-collars) and [Wolf controls](../mobs/Wolf.md#taming-and-owner-controls). [Cat collar] · [Wolf collar] · [Consumption handling]

### Signs and Banner patterns

Apply dye to the **face of a Sign or Hanging Sign that you are using**. That face needs text, the sign must be unwaxed, another player must not be editing it, and you must be allowed to build. The new dye color must differ from the existing text color. A successful Survival application consumes one dye. [Signs](../blocks/Signs.md#dye-glow-ink-and-wax) explains glow, ink, waxing and the separate front/back faces. [Sign dispatch and cost] · [Nonempty text requirement] · [Dye text change] · [Different-color check]

For a Banner, use a [Loom](../blocks/Loom.md#adding-a-pattern): provide a Banner and a dye, choose an available pattern, then take the output. This adds a pattern layer in the dye's color; it does not simply repaint the whole Banner. Taking a result spends **one dye**, and the normal menu stops at six pattern layers. Use [Banners](../blocks/Banners.md) for base colors and pattern handling. [Loom inputs and consumption] · [Loom layer limit] · [Pattern color]

### Wool, carpet and portable storage

Use the exact output recipe when recoloring building materials. [Wool and Carpet](../blocks/WoolAndCarpet.md#obtaining-and-color-changes) explains the sixteen-color families, permitted starting colors and quantities. The checked recipes replace the old material color with the named target color; they do not blend the two dyes. [Red Wool example] · [White Wool example] · [Red Carpet example]

A dye can recolor **one Bundle or one Shulker Box** in a crafting grid, including an already colored one. The recipe preserves the original container's components and contents; a container already of the target color is rejected. The ingredient tags contain the uncolored item and all sixteen colored variants. See [Bundle colors](Bundle.md#colors-and-recoloring) and [Shulker Box colors](../blocks/ShulkerBox.md#obtaining-and-colors) for the full storage rules. [Bundle recipe example] · [Bundle tag] · [Shulker recipe example] · [Shulker tag] · [Recolor matching] · [Result components] · [Copied components]

### Equipment colors and washing

Craft **one dyeable equipment item with one or more dyes** to color it. The bundled dyeable tag contains exactly six items: Leather Cap, Leather Tunic, Leather Pants, Leather Boots, Leather Horse Armor and Wolf Armor. The recipe accepts only one equipment item; the remaining occupied slots must contain dyes. [Active equipment recipe] · [Recipe serializer] · [Dyeable item tag] · [Equipment matching]

Equipment dyeing **blends RGB color**, including any color already saved on the item. Adding White Dye therefore participates in a blend; it is not a command to erase earlier color. The result keeps the item's other components and has a count of one. To start again, use the dyed equipment item on a **water-filled Cauldron**: this removes its saved dye color and lowers the water by one level. See [Cauldrons](../blocks/Cauldrons.md) and [Wolf Armor](WolfArmor.md). [Color blend and copy] · [Washing registrations] · [Washing action]

### Firework colors

A basic **Firework Star** uses one Gunpowder and one or more dyes in separate crafting slots. Its saved explosion lists those dye colors. Combining one existing Firework Star with one or more dyes sets its fade-color list instead, returning one star and preserving its other components. These are active special crafting recipes; follow [Firework Star](FireworkStar.md) and [Firework Rocket](FireworkRocket.md) for the item entries. [Star recipe resource] · [Star matching and colors] · [Fade recipe resource] · [Fade matching and result]

## Sources and verification

Source-reviewed on **2026-10-02** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`. The acquisition review checked all **43 dye-producing ordinary recipe resources**: 41 shapeless recipes and the Cactus/Sea Pickle smelting recipes. Their inputs name exact items; outputs and default result counts were checked against the recipe and ItemStack codecs. Shared review followed Sheep/collar and sign interaction callers, Loom inputs/output consumption, dyeable-equipment and firework recipe dispatch, storage recoloring, and browser/trader routes. [Recipe loader] · [Singular recipe registry] · [Resource path conversion] · [Shapeless codec] · [Default result count]

This is a source review of recipes and selected practical uses, not an exhaustive list of every mob interaction, trade, structure reward or data-pack possibility involving a dye. No in-game crafting, smelting, trading, browser request, recoloring or washing test was run. Active data packs can change recipes, tags and loot.

Related: [Items](Items.md) · [Crafting](../crafting/Crafting.md) · [Flowers](../blocks/Flowers.md) · [Wool and Carpet](../blocks/WoolAndCarpet.md)

[Dye registrations]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1695-L1710
[Item defaults]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Item.java#L355-L358
[Stack default]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[Ingredient matching]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/crafting/Ingredient.java#L24-L62
[Inventory grid]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/InventoryMenu.java#L48-L53
[Inventory crafting dispatch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/InventoryMenu.java#L80-L85
[Shapeless matching]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L58-L69
[Input counting]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/crafting/CraftingInput.java#L16-L29
[Ingredient consumption]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L82-L110
[Four-item Magenta recipe]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/magenta_dye_from_blue_red_white_dye.json
[Dye category entries]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1855-L1870
[Browser list construction]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[Client request]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[Server insertion]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1919
[All dye offers]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L790-L805
[Offer construction]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1438-L1479
[Trader offer dispatch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java#L133-L141
[Random offer selection]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L233
[Sheep dye action]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/DyeItem.java#L25-L38
[Player interaction and Creative handling]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L854-L885
[Cat collar]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/animal/Cat.java#L373-L407
[Wolf collar]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java#L455-L504
[Consumption handling]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1080
[Sign dispatch and cost]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/SignBlock.java#L89-L115
[Nonempty text requirement]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/SignApplicator.java#L8-L13
[Dye text change]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/DyeItem.java#L48-L55
[Different-color check]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/SignText.java#L73-L79
[Loom inputs and consumption]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/LoomMenu.java#L64-L104
[Loom layer limit]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/LoomMenu.java#L166-L177
[Pattern color]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/LoomMenu.java#L260-L279
[Red Wool example]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/dye_red_wool.json
[White Wool example]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/dye_white_wool.json
[Red Carpet example]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/dye_red_carpet.json
[Bundle recipe example]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/red_bundle.json
[Bundle tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/bundles.json
[Shulker recipe example]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/red_shulker_box.json
[Shulker tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/shulker_boxes.json
[Recolor matching]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/crafting/TransmuteRecipe.java#L38-L79
[Result components]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/crafting/TransmuteResult.java#L16-L56
[Copied components]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ItemStack.java#L631-L638
[Active equipment recipe]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/armor_dye.json
[Recipe serializer]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/crafting/RecipeSerializer.java#L10-L12
[Dyeable item tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/dyeable.json
[Equipment matching]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/crafting/ArmorDyeRecipe.java#L17-L70
[Color blend and copy]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/DyedItemColor.java#L32-L76
[Washing registrations]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/core/cauldron/CauldronInteraction.java#L124-L129
[Washing action]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/core/cauldron/CauldronInteraction.java#L313-L328
[Star recipe resource]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/firework_star.json
[Star matching and colors]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/crafting/FireworkStarRecipe.java#L46-L121
[Fade recipe resource]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/firework_star_fade.json
[Fade matching and result]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/crafting/FireworkStarFadeRecipe.java#L20-L68
[Recipe loader]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L84
[Singular recipe registry]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/core/registries/Registries.java#L283
[Resource path conversion]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/resources/FileToIdConverter.java#L23-L37
[Shapeless codec]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L83-L92
[Default result count]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ItemStack.java#L106-L117
