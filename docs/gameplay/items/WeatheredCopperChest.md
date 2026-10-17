# Weathered Copper Chest

Place this **weathered, unwaxed Copper Chest** for storage or as a [Copper Golem sorting source](../blocks/CopperChests.md#copper-golem-sorting).

## Obtaining

Let a placed [Exposed Copper Chest](ExposedCopperChest.md) oxidize to this stage, then mine it with the correct pickaxe. There is **no direct crafting recipe** for Weathered Copper Chest in the bundled recipe set. See the [chest aging rules](../blocks/CopperChests.md#aging-waxing-and-inventory-retention). [Stage progression][weathering]

Use an unbroken axe on a placed [Waxed Weathered Copper Chest](WaxedWeatheredCopperChest.md) to remove its wax, or scrape a placed [Oxidized Copper Chest](OxidizedCopperChest.md) back one stage; then recover the resulting chest. [Axe conversions][axe]

[Creating a Copper Golem](../blocks/CopperChests.md#creating-a-chest-with-a-copper-golem) with a full **Weathered Copper block (waxed or unwaxed)** also creates this unwaxed chest at the body position. Place the Carved Pumpkin or Jack o'Lantern last. An adjoining chest can alter the resulting finish through joining. [Active construction callback][pumpkin] · [Body-to-chest mapping][conversion]

### Recovering the item

Mine the matching placed chest with an **unbroken Stone, Copper, Iron, Diamond, or Netherite Pickaxe** to recover **1 Weathered Copper Chest**. Empty it first: stored contents spill separately, while the chest item keeps its custom name. See [mining and relocation](../blocks/CopperChests.md#mining-and-relocation) for the tool gate and explosion caveat. [This variant's loot][loot] · [Container removal][removal-dispatch] · [Content drops][removal]

## Usage

Place it as a **27-slot chest**, normally facing toward you. A compatible neighbor can form a double chest and change its finish, so check [storage and joining](../blocks/CopperChests.md#storage-and-joining) before mixing variants. That guide also covers lid access and [Hopper/Comparator connections](../blocks/CopperChests.md#hoppers-and-comparator). [Placement facing][placement]

## Behavior

While placed and unwaxed, it can advance to [Oxidized Copper Chest](OxidizedCopperChest.md). Lightning can remove its oxidation. [Lightning conversion][lightning] Wax it in place or craft it with **1 Honeycomb** to obtain [Waxed Weathered Copper Chest](WaxedWeatheredCopperChest.md). [Aging callback][aging] · [Matching waxing recipe][wax-recipe]

For placed waxing or scraping, hold secondary use. [Variant changes retain stored contents and also update a joined partner](../blocks/CopperChests.md#aging-waxing-and-inventory-retention); [joining mixed finishes](../blocks/CopperChests.md#storage-and-joining) can change the stage or remove wax.

## Notes

- This item is the item form of the `minecraft:weathered_copper_chest` block. [Item registration][item]
- Available through the [combined inventory item browser](../mechanics/InventoryBrowser.md) in Creative. [Creative entry][creative] · [Current catalog entry][catalog-entry] · [Combined browser][catalog-browser]

Source-reviewed at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. This page describes bundled behavior; no in-game test was run.

[weathering]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/WeatheringCopper.java#L49-L98
[axe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/AxeItem.java#L61-L123
[pumpkin]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/CarvedPumpkinBlock.java#L93-L102
[conversion]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/CopperChestBlock.java#L35-L52
[loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/weathered_copper_chest.json
[removal-dispatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java#L311-L323
[removal]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java#L250-L254
[placement]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/ChestBlock.java#L199-L220
[lightning]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LightningBolt.java#L173-L211
[aging]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/WeatheringCopperChestBlock.java#L35-L58
[wax-recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_copper_chest_from_honeycomb.json
[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L2628
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1174-L1181

[catalog-entry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1176
[catalog-browser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
