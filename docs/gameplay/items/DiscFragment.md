# Disc Fragment

A **Disc Fragment** (`minecraft:disc_fragment_5`) is an Ancient City collectible used to craft [Music Disc (5)](MusicDisc5.md). It is an ingredient, not a playable disc or food. Save **nine** for one finished disc. [Item registration][item] · [Recipe][recipe]

## Obtaining

Look in **ordinary Ancient City chests** using `minecraft:chests/ancient_city`. The main pool makes **5–10 weighted selections**; a fragment entry has weight **4 out of 86**, or **2/43 (about 4.65%) per selection**, and awards **1–3 fragments** when selected. Repeated selections are possible, but neither one selection nor one chest guarantees the nine needed. These are per-selection odds, not a 4.65% chance per chest or per city. [Chest table][city] · [Default weights][weights] · [Selection loop][pool]

The connected barracks template, for example, saves the ordinary table on its chests; template placement loads that saved data and the chest resolves the loaded table when unpacked. **Ice-box chests use a different table with no fragment entry.** Follow [Ancient City](../structures/AncientCity.md#chest-rewards) for finding eligible chests and preparing for its hazards. [Example assignments][city-chest] · [Block-entity placement][place] · [Container loading][container] · [Ice-box table][ice]

When the optional [Trade Rebalance pack](../trading/Trading.md#optional-trade-rebalance) supplies the city table, it keeps the fragment's weight **4**, the pool total **86**, the **5–10 rolls**, and the **1–3** selected count. Other entries change, so use that pack's full table when comparing other rewards. Fragments are also listed in Creative. [Optional table][rebalance] · [Pack metadata][pack] · [Creative entry][fragment-creative]

## Usage

Craft one [Music Disc (5)](MusicDisc5.md#crafting-from-fragments) from nine fragments in a Crafting Table. That page owns the exact recipe and the finished disc's song data. The bundled recipe inventory contains no recipe to recover fragments by dismantling a disc, and playing Disc 5 does not generate replacements. [Recipe inventory][recipes] · [Recipe][recipe]

## Behavior

Fragments stack to **64** through the ordinary item components. Their registration supplies no food or jukebox-playable component, and their item class only adds descriptive tooltip text. Keep them until you have enough to craft; inserting a loose fragment is not a partial-song mechanic. [Registration][item] · [Component defaults][defaults] · [Default inheritance][properties] · [Fragment class][fragment-class] · [Jukebox component gate][insert]

An already-unpacked chest does not reroll its loot when reopened. Search untouched eligible chests for more fragments, following the [city expedition guide](../structures/AncientCity.md). [One-time loot handling][container]

## Notes

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked item components, the recipe resource inventory, ordinary and optional city tables, decoded chest assignments and the active table-loading path. No crafting, chest-opening or loot-distribution test was run. Custom data packs and later builds can differ. [Loot loading][reload]

Related: [Music Disc (5)](MusicDisc5.md) · [Ancient City](../structures/AncientCity.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2341-L2359
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/music_disc_5.json
[city]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/ancient_city.json
[weights]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/entries/LootPoolSingletonContainer.java#L46-L55
[pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/LootPool.java#L63-L101
[city-chest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/ancient_city/structures/barracks.nbt
[place]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java#L285-L317
[container]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/RandomizableContainer.java#L50-L89
[ice]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/ancient_city_ice_box.json
[rebalance]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/datapacks/trade_rebalance/data/minecraft/loot_table/chests/ancient_city.json
[pack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/datapacks/trade_rebalance/pack.mcmeta
[fragment-creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1852-L1856
[recipes]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe
[defaults]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L355-L359
[fragment-class]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/DiscFragmentItem.java
[insert]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/JukeboxPlayable.java#L47-L69
[reload]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L40-L70
