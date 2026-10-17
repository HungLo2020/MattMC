# Heart of the Sea

**Save a Heart of the Sea for a [Conduit](../blocks/Conduit.md).** One Heart plus eight Nautilus Shells makes one device; carrying the Heart alone does not supply Conduit Power. [Recipe][conduit] · [Item registration][registration]

## Obtaining

Find an untouched **[Buried Treasure](../structures/BuriedTreasure.md)** chest. Its bundled loot table has a dedicated pool with **one roll and one Heart of the Sea entry**, so an unchanged, freshly unpacked chest supplies **exactly one Heart**. This is separate from its random valuables and equipment pools. [Heart pool][treasure-loot] · [Chest assignment][treasure-piece]

Use the [treasure-map and digging guide](../structures/BuriedTreasure.md#getting-a-treasure-map) to reach the chest. The guarantee belongs to that chest's original loot table, not every map destination: another player may already have taken it, and reopening a chest does not regenerate its loot. Custom data packs can also replace the table. [One-time loot unpacking][chest-unpack] · [Loaded loot resources][loot-load]

## Usage

Budget **1 Heart of the Sea + 8 [Nautilus Shells](NautilusShell.md) → 1 Conduit**. Follow the [Conduit crafting pattern](../blocks/Conduit.md#crafting-and-collecting); taking the crafted output consumes those ingredients. [Recipe][conduit] · [Crafting consumption][craft-take]

Next, [place the Conduit in water](../blocks/Conduit.md#fill-the-inner-water-volume) and [build its valid frame](../blocks/Conduit.md#build-a-valid-frame). Crafting the item does not complete the water and frame requirements. The block guide owns activation, range and effects. [Active device checks][conduit-active]

## Behavior

The Heart stacks to **64** and has **Uncommon** rarity. It is a plain ingredient with no ordinary eating, drinking or placement action. [Registration][registration] · [Default stack limit][stack] · [Ordinary item interaction][plain-use]

## Notes

* Registered item: `minecraft:heart_of_the_sea`
* Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`, including the actual treasure chest assignment, loaded loot, recipe and ingredient-consumption path. No in-game treasure search, chest-opening, crafting or Conduit-activation test was run. [Recipe loading][recipe-load]

Related: [Buried Treasure](../structures/BuriedTreasure.md) · [Nautilus Shell](NautilusShell.md) · [Conduit item](Conduit.md) · [Conduit construction](../blocks/Conduit.md) · [Items](Items.md)

[conduit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/conduit.json
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2372-L2372
[treasure-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/buried_treasure.json#L1-L14
[treasure-piece]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/BuriedTreasurePieces.java#L43-L76
[chest-unpack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/RandomizableContainer.java#L50-L89
[loot-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L40-L70
[craft-take]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L78-L111
[conduit-active]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/ConduitBlockEntity.java#L93-L110
[stack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L390
[plain-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L164-L191
[recipe-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L80
