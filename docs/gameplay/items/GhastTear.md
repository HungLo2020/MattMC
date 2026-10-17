# Ghast Tear

A **Ghast Tear** (`minecraft:ghast_tear`) is an ingredient for Regeneration brewing, End Crystals and Dried Ghasts. It is obtained from the hostile [Ghast](../mobs/Ghast.md), not by raising a Happy Ghast. [Item identity][ghast-tear-item] · [Ghast drop][ghast-loot] · [Brewing][regeneration]

## Obtaining

With mob loot enabled, a Ghast's bundled loot rolls **0–1 Tear**. Looting increases the possible maximum by one per level, up to **4 with Looting III**; the Tear pool does not require a player-attributed kill. Follow [Ghast drops](../mobs/Ghast.md#drops) for combat and loot conditions, including the separate Tears music disc. [Loot table][ghast-loot] · [Looting calculation][looting-count] · [Loot gate][animal-loot]

Ghast Tears are also ordinary category-listed ingredients in MattMC's [inventory item browser](../mechanics/InventoryBrowser.md), available through its **Creative** insertion route. Browser insertion is separate from mob drops. [Category entry][ghast-tear-category]

## Usage

- **Regeneration:** add a Tear to an **Awkward Potion** through the [brewing system](../brewing/Brewing.md). Adding it to a Water Bottle produces a Mundane Potion instead; start with the correct base. [Regeneration mix][regeneration] · [Starting-potion distinction][start-mix]
- **Dried Ghast:** use **8 Tears around 1 Soul Sand** to craft one. Follow [Dried Ghast](../blocks/DriedGhast.md#obtaining) for the pattern and the separate hydration/hatching process. [Recipe][dried-recipe]
- **End Crystal:** use **1 Tear, 1 Eye of Ender and 7 Glass**. Follow [End Crystal](EndCrystal.md#crafting) for the pattern, placement and explosion hazards. [Recipe][end-crystal-recipe]

## Behavior

Using Tears in a recipe or brew consumes the ingredient; possessing a Tear does not tame or transform a hostile Ghast. The Dried Ghast route creates a separate block whose hatching produces a Happy Ghast. Use the linked owners for those interactions.

## Notes

The ingredient is `minecraft:ghast_tear`; the separate music item is `minecraft:music_disc_tears`. See [Music Disc (Tears)](MusicDiscTears.md) and [Jukebox](../blocks/Jukebox.md) for that reward.

## Sources and verification

Source-reviewed on **2026-10-02** at `780a7733d1804088e86c248a1c8ec6957def4915`. Checked the item/category entry, Ghast loot and Looting conditions, and bundled brewing/crafting ingredients. No in-game drop, brew or crafting test was run. Data packs and server rules can differ; the linked Ghast, brewing, Dried Ghast and End Crystal guides own the full operation and loading checks.

[ghast-tear-item]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/item/Items.java#L1763
[ghast-loot]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/loot_table/entities/ghast.json#L1-L63
[regeneration]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L181-L183
[looting-count]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java#L64-L79
[animal-loot]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L567
[ghast-tear-category]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1893
[start-mix]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L230-L235
[dried-recipe]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/recipe/crafting/dried_ghast.json#L1-L18
[end-crystal-recipe]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/recipe/crafting/end_crystal.json#L1-L18
