# Echo Shard

**Collect eight Echo Shards to craft a [Recovery Compass](RecoveryCompass.md).** They are possible Ancient City chest loot; finding a city does not guarantee enough for one compass. [City loot][city-loot] · [Recovery recipe][recovery]

## Obtaining

Search ordinary loot chests in an **[Ancient City](../structures/AncientCity.md#chest-rewards)**. The bundled ordinary table makes **5–10 weighted selections** in its main pool. Echo Shards have weight **4 out of 86**, and each selection of that entry supplies **1–3 shards**. The 4/86 chance is **per selection**, not per chest or per city. Selections can repeat, so three shards is not a chest maximum, and a chest can contain none. [Table counts and weights][city-loot] · [Selection execution][loot-roll]

The table is assigned to actual structure chests: for example, the optional Barracks template has two ordinary-table chests. Which pieces are placed varies; that example is not a promised total per city. [Barracks template][barracks] · [Piece choices][city-pool] · [Chest-data placement][template-place]

**Ice-box chests use a different table with no Echo Shard entry.** The bundled optional [Trade Rebalance pack](../trading/Trading.md#optional-trade-rebalance), when selected and taking precedence, replaces the ordinary city table but keeps this same shard entry, weight and main-pool roll range. Reopening a previously unpacked chest does not reroll its contents. [Ice-box assignment][ice-box] · [Ice-box loot][ice-loot] · [Optional replacement][city-rebalance] · [Loot unpacking][chest-unpack]

## Usage

Budget **8 Echo Shards + 1 ordinary [Compass](Compass.md) → 1 Recovery Compass**, using the [Recovery Compass crafting pattern](RecoveryCompass.md#obtaining). Taking the output consumes all eight shards and the Compass. There is no smaller shard-by-shard charging step. [Recipe][recovery] · [Ingredient consumption][craft-take]

Store the finished compass near your respawn supplies. Its job is to point toward **your latest recorded death in the same dimension**, not to find an Ancient City or check whether dropped items remain. Follow [Recovery Compass use and limits](RecoveryCompass.md#usage) before relying on it for a retrieval trip. [Recovery target][recovery-target]

## Behavior

Echo Shards stack to **64** and have **Uncommon** rarity. The bundled shard is a plain ingredient with no ordinary right-click action or location binding. [Registration][registration] · [Default stack limit][stack] · [Ordinary item use][plain-use]

## Notes

* Registered item: `minecraft:echo_shard`
* Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Structure chest assignments, ordinary/ice-box/optional tables and active recipe/loot loading were checked. No in-game city search, chest-opening, crafting or navigation test was run. Data packs and previously opened chests can change what is available. [Recipe loading][recipe-load] · [Loot loading][loot-load]

Related: [Ancient City](../structures/AncientCity.md) · [Recovery Compass](RecoveryCompass.md) · [Death and respawn](../mechanics/DeathAndRespawn.md) · [Items](Items.md)

[city-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/ancient_city.json
[recovery]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/recovery_compass.json
[loot-roll]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/LootPool.java#L60-L101
[barracks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/ancient_city/structures/barracks.nbt
[city-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/template_pool/ancient_city/structures.json
[template-place]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java#L254-L310
[ice-box]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/ancient_city/structures/ice_box_1.nbt
[ice-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/ancient_city_ice_box.json
[city-rebalance]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/datapacks/trade_rebalance/data/minecraft/loot_table/chests/ancient_city.json
[chest-unpack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/RandomizableContainer.java#L50-L89
[craft-take]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L78-L111
[recovery-target]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/item/properties/numeric/CompassAngleState.java#L43-L131
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2525-L2525
[stack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L390
[plain-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L164-L191
[recipe-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L80
[loot-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L40-L70
