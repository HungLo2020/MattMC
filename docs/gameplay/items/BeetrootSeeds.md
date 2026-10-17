# Beetroot Seeds

Beetroot Seeds (`minecraft:beetroot_seeds`) plant Beetroots on [Farmland](../blocks/Farmland.md). They are a separate item from harvested [Beetroot](Beetroot.md); use seeds to start or expand the field and roots for food and soup. [Seed registration][seed-item] · [Valid ground][crop-ground]

## Obtaining

An ordinary mature Beetroot harvest gives **1 Beetroot and 1–4 Beetroot Seeds**, before Fortune or explosion losses. Breaking an immature plant returns **1 Beetroot Seed** and no Beetroot. Fortune adds chances for extra seeds, while the root count stays one. [Crop loot][crop-loot] · [Seed bonus calculation][crop-bonus]

For a first seed supply, a **Wandering Trader** can select an offer of **1 Beetroot Seed for a base price of 1 Emerald**; the seed offer is not guaranteed on every trader. The checked **monster-room chest** table can also select a stack of **2–4 seeds**. These are selected Survival sources, not a complete loot catalog. [Trade entry][seed-trade] · [Offer construction][trade-output] · [Trader selection][trader-selection] · [Chest entry][dungeon-seeds] · [Chest assignment][dungeon-assignment] · [Loot key][dungeon-key]

Seeds are listed in **Natural Blocks** for the [inventory item browser](../mechanics/InventoryBrowser.md). Creative insertion can supply them independently of farming or exploration; a visible Survival entry does not. [Category entry][seed-category]

## Usage

Use the seeds on Farmland to plant a Beetroot crop. It matures at **age 3**, with ages 0–2 still immature. Natural growth is random, and Bone Meal can advance **0 or 1 stage**, so a use need not visibly change the plant. The [Root crops guide](../blocks/RootCrops.md#growth-and-bone-meal) owns light, water, spacing and growth requirements. [Crop stages and Bone Meal adjustment][crop-age]

MattMC provides two ways to keep a mature crop planted:

- **Use an empty hand:** receive its mature loot and reset the crop to age 0
- **Use a hoe:** harvest mature Beetroots of the same type in the surrounding **3 × 3 horizontal area**, resetting each to age 0

Those actions do not spend replacement seeds. Breaking the crop removes it and requires planting again. Read [the harvesting controls](../blocks/RootCrops.md#mattmc-harvesting-controls) for tool wear, Fortune, immature neighbors and the [retained broken-hoe exception](../mechanics/AxesAndHoes.md#mattmc-crop-area-harvesting). Keep spare seeds when expanding or repairing a field. [Active harvest handlers][crop-harvest]

## Behavior

Seeds have several animal uses, with different outcomes:

- Feed ready adult **[Chickens](../mobs/Chicken.md), [Emus](../mobs/Emu.md) or [Roadrunners](../mobs/Roadrunner.md)** for breeding; feeding their young advances growth. Beetroot Seeds are accepted through the current food tags and their active food checks. [Chicken/Emu tag][chicken-food-tag] · [Chicken check][chicken-food] · [Emu check][emu-food] · [Roadrunner tag][roadrunner-tag] · [Roadrunner check][roadrunner-food] · [Shared feeding][animal-feed]
- Use them on a wild **[Parrot](../mobs/Parrot.md)** for a **one-in-ten taming chance per feeding**. This is its taming action, not breeding; do not substitute [Cookies](Cookie.md). [Parrot food tag][parrot-food-tag] · [Taming handler][parrot-tame]
- Hold them to lure a **[Potoo](../mobs/Potoo.md#luring-and-care)**. Its feeding path checks a separate breeding-food tag with no definition in the reviewed bundle, so the lure does not establish a seed-feeding, healing or breeding route. [Installed lure][potoo-lure] · [Food predicate][potoo-food] · [Interaction][potoo-interact]
- **[Crows](../mobs/Crow.md#taming-and-food)** can eat carried Beetroot Seeds and heal; these seeds do not replace the Pumpkin Seeds used for taming. [Crow tag][crow-tag] · [Food check][crow-food] · [Eating and taming conditions][crow-eat]

## Notes

Beetroot Seeds have a **30% chance to add one compost level at levels 1–6**. The first accepted item in an empty [Composter](../blocks/Composter.md#from-ingredients-to-bone-meal) always succeeds. Accepted Survival insertions spend a seed even when the later chance roll fails, so reserve planting stock first. [Seed chance][compost-seeds] · [Player insertion][compost-use] · [Roll][compost-roll]

Related: [Beetroot Soup](BeetrootSoup.md) · [Wheat Seeds](WheatSeeds.md) · [Items](Items.md)

## Sources and verification

Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on 2026-10-04. Checked seed/crop registration, crop loot, selected trader/chest acquisition, harvest callbacks, active animal consumers and composting. No in-game planting, harvesting, trading, feeding, growth-timing or insertion test was run. Data packs can change tags and loot.

[seed-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2238-L2240
[crop-ground]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/CropBlock.java#L53-L56
[crop-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/beetroots.json
[crop-bonus]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/functions/ApplyBonusCount.java#L94-L115
[seed-trade]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L777-L780
[trade-output]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1434-L1478
[trader-selection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java#L134-L140
[dungeon-seeds]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/simple_dungeon.json#L216-L230
[dungeon-assignment]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/MonsterRoomFeature.java#L91-L111
[dungeon-key]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/BuiltInLootTables.java#L18
[seed-category]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L959-L966
[crop-age]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/BeetrootBlock.java#L19-L58
[crop-harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/CropBlock.java#L193-L246
[chicken-food-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/chicken_food.json
[chicken-food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/animal/Chicken.java#L173-L175
[emu-food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityEmu.java#L121-L134
[roadrunner-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/roadrunner_breedables.json
[roadrunner-food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityRoadrunner.java#L194-L196
[animal-feed]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/animal/Animal.java#L133-L158
[parrot-food-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/parrot_food.json
[parrot-tame]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/animal/Parrot.java#L248-L275
[potoo-lure]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityPotoo.java#L80-L87
[potoo-food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityPotoo.java#L476-L478
[potoo-interact]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityPotoo.java#L426-L429
[crow-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/crow_foodstuffs.json
[crow-food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityCrow.java#L578-L604
[crow-eat]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityCrow.java#L305-L332
[compost-seeds]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L86-L91
[compost-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L244-L259
[compost-roll]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L304-L318
