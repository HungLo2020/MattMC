# Paper

**Paper** is a stackable crafting material for books, maps, fireworks and banner patterns. A renewable [Sugar Cane farm](../blocks/SugarCane.md) supplies its main recipe. [Registration][item] · [Paper recipe][paper]

## Obtaining

Craft **3 Sugar Cane in one horizontal row → 3 Paper**. Use a 3 × 3 Crafting Table grid: the inventory's 2 × 2 grid is too narrow. Put Cane in three separate slots, leaving the other slots empty; stacking three Cane in a single slot does not satisfy the recipe. Any of the three rows works. The conversion and Sugar recipe are also listed in the canonical [Sugar Cane crafting guide](SugarCane.md#crafting). [Recipe][paper] · [Pattern matching][pattern] · [Table grid][table]

Paper also occurs in these bundled chest-loot entries. Each range is the quantity **when that Paper entry is selected**, not a guaranteed total for a chest; random rolls can select it more than once or skip it.

| Chest | Paper per selected entry |
| --- | ---: |
| Shipwreck map chest | 1–10 |
| Shipwreck supply chest | 1–12 |
| Stronghold library chest | 2–7 |
| Village cartographer chest, such as the Plains cartographer house | 1–5 |

Shipwreck chest markers and Stronghold library placement assign the named loot tables. The Plains cartographer template carries its village chest table and is included in the Plains house pool. This does not guarantee that a particular generated structure includes that room or chest. [Map chest][ship-map] · [Supply chest][ship-supply] · [Shipwreck wiring][ship-wiring] · [Library loot][library-loot] · [Library wiring][library-wiring] · [Village loot][village-loot] · [Plains template][village-template] · [House pool][village-pool]

An adult Cartographer can also give **one Paper** as one possible gift to a nearby player with [Hero of the Village](../effects/OmenEffects.md). It is a chance-based gift when the Villager's gift behavior is ready, not a trade or a guaranteed reward each time you interact. [Gift pool][gift] · [Gift behavior][gift-behavior] · [Active goal package][goals]

Paper appears in the Creative category catalog. The [inventory browser's mode limit](../mechanics/InventoryBrowser.md#mode-and-permission-limits) still applies: ordinary Survival catalog clicks do not supply it. [Category entry][tabs] · [Category-built browser][browser]

## Usage

- **Books:** three Paper and one Leather make one plain [Book](Book.md#crafting-and-collecting), shapeless. A Book can then become a [Book and Quill](BookAndQuill.md). [Book recipe][book]
- **New maps:** eight Paper around one Compass make one [Empty Map](EmptyMap.md#crafting-and-first-use). [Map recipe][map]
- **Map enlargement:** use one Paper with an eligible unlocked filled map at a [Cartography Table](../blocks/CartographyTable.md#enlarging). The separate crafting-grid recipe uses eight Paper around a qualifying filled map. Follow that guide for eligibility, scale limits and the different locked-map checks. [Table operation][cartography] · [Crafting extension][extend]
- **Cartography Tables:** two Paper above two rows of two planks make one table. See its [crafting guide](../blocks/CartographyTable.md#crafting-and-placement). [Table recipe][cartography-recipe]
- **Firework Rockets:** one Paper and one to three Gunpowder in separate ingredient slots make three rockets; optional Firework Stars add their explosions. Gunpowder count determines the flight-duration value. The simple one-Paper, one-Gunpowder recipe also makes three rockets. [Special recipe data][firework-json] · [Special recipe checks][fireworks] · [Simple recipe][firework-simple]
- **Banner patterns:** one Paper plus Oxeye Daisy, Creeper Head, Wither Skeleton Skull, Enchanted Golden Apple, Bricks or Vine makes the corresponding Flower Charge, Creeper Charge, Skull Charge, Thing, Field Masoned or Bordure Indented pattern, shapeless. Each recipe consumes its other ingredient too. [Flower][flower] · [Creeper][creeper] · [Skull][skull] · [Thing][thing] · [Field Masoned][field] · [Bordure Indented][bordure]

Novice Librarians and Cartographers have Paper-buying offers with a **base price of 24 Paper for 1 Emerald**. The Librarian offer has 16 uses before restocking; the Cartographer offer has 12. Prices and selected offers follow the [Trading guide](../trading/Trading.md), so check the actual screen. The optional Librarian rebalance table retains this Paper listing. These offers **buy your Paper**, rather than sell Paper to you. [Trade pools and offer construction][trades] · [Current price calculation][prices]

## Behavior

Ordinary Paper stacks to **64**. It is a material, not a writing surface with its own page editor: turn it into a Book and Quill to write notes. Consuming Paper in a recipe does not preserve it as a separate reusable ingredient. [Plain item registration][item] · [Default stack size][components] · [Crafting input consumption][result]

## Notes

- This item is registered as `minecraft:paper`
- Keep some Sugar Cane for replanting before processing a whole harvest
- For farming, use [Sugar Cane](../blocks/SugarCane.md); for map state and copying, use [Map](Map.md) and [Cartography Table](../blocks/CartographyTable.md)

Related: [Sugar Cane item](SugarCane.md) · [Book](Book.md) · [Book and Quill](BookAndQuill.md) · [Empty Map](EmptyMap.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked the current shaped, shapeless and special recipes through [recipe loading][recipes] and [serializer registration][serializers], chest assignments and a cartographer template, gift behavior, trade pools and stack defaults. No in-game crafting, chest generation, gifting, trading or browser-insertion test was run. The chest examples describe bundled tables and placement wiring, not a measured chance of finding Paper in a world. Data packs and server changes can alter recipes, loot and offers.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1597-L1598
[paper]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/paper.json
[pattern]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/ShapedRecipePattern.java
[table]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/CraftingMenu.java#L34-L47
[ship-map]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/shipwreck_map.json
[ship-supply]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/shipwreck_supply.json
[ship-wiring]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/ShipwreckPieces.java
[library-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/stronghold_library.json
[library-wiring]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/StrongholdPieces.java#L703-L707
[village-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/village/village_cartographer.json
[village-template]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/village/plains/houses/plains_cartographer_1.nbt
[village-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/village/plains/houses.json
[gift]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/gameplay/hero_of_the_village/cartographer_gift.json
[gift-behavior]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/ai/behavior/GiveGiftToHero.java
[goals]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/ai/behavior/VillagerGoalPackages.java#L74-L103
[book]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/book.json
[map]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/map.json
[cartography]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/CartographyTableMenu.java#L90-L134
[extend]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/MapExtendingRecipe.java
[cartography-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/cartography_table.json
[firework-json]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/firework_rocket.json
[fireworks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/FireworkRocketRecipe.java
[firework-simple]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/firework_rocket_simple.json
[flower]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/flower_banner_pattern.json
[creeper]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/creeper_banner_pattern.json
[skull]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/skull_banner_pattern.json
[thing]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/mojang_banner_pattern.json
[field]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/field_masoned_banner_pattern.json
[bordure]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/bordure_indented_banner_pattern.json
[components]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L388
[trades]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java
[prices]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/trading/MerchantOffer.java#L92-L104
[tabs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[browser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[result]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L72-L113
[recipes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L71-L90
[serializers]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/RecipeSerializer.java#L10-L17
