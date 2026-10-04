# Waxed Weathered Copper Chest

Place this **weathered, waxed Copper Chest** for storage or as a [Copper Golem sorting source](../blocks/CopperChests.md#copper-golem-sorting).

## Obtaining

Combine **1 [Weathered Copper Chest](WeatheredCopperChest.md) + 1 [Honeycomb](Honeycomb.md)** in any crafting-grid positions to make **1 Waxed Weathered Copper Chest**. Alternatively, use one Honeycomb on the matching placed unwaxed chest, then mine it with the correct pickaxe. Hold secondary use while applying the Honeycomb to avoid opening the chest. [Waxing recipe][recipe] · [Placed waxing][wax]

Using a full **Waxed Weathered Copper** as the body when [creating a Copper Golem](../blocks/CopperChests.md#creating-a-chest-with-a-copper-golem) selects an **unwaxed Weathered Copper Chest**. An adjoining chest can change that finish; wax the matching-stage chest afterward to obtain this item. [Body-to-chest mapping][conversion]

### Recovering the item

Mine the matching placed chest with an **unbroken Stone, Copper, Iron, Diamond, or Netherite Pickaxe** to recover **1 Waxed Weathered Copper Chest**. Empty it first: stored contents spill separately, while the chest item keeps its custom name. See [mining and relocation](../blocks/CopperChests.md#mining-and-relocation) for the tool gate and explosion caveat. [This variant's loot][loot] · [Container removal][removal-dispatch] · [Content drops][removal]

## Usage

Place it as a **27-slot chest**, normally facing toward you. A compatible neighbor can form a double chest and change its finish, so check [storage and joining](../blocks/CopperChests.md#storage-and-joining) before mixing variants. That guide also covers lid access and [Hopper/Comparator connections](../blocks/CopperChests.md#hoppers-and-comparator). [Placement facing][placement]

## Behavior

Wax prevents normal oxidation and lightning cleaning. One use of an unbroken axe removes the wax and produces [Weathered Copper Chest](WeatheredCopperChest.md), **keeping the same oxidation stage**. A further axe use on that unwaxed chest removes one oxidation stage. [Waxed block registration][registry] · [Lightning conversion][lightning] · [Axe conversions][axe]

For placed waxing or scraping, hold secondary use. [Variant changes retain stored contents and also update a joined partner](../blocks/CopperChests.md#aging-waxing-and-inventory-retention); [joining mixed finishes](../blocks/CopperChests.md#storage-and-joining) can change the stage or remove wax.

## Notes

- This item is the item form of the `minecraft:waxed_weathered_copper_chest` block. [Item registration][item]
- Available in the Creative Menu. [Creative entry][creative]

Source-reviewed at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. This page describes bundled behavior; no in-game test was run.

[recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_copper_chest_from_honeycomb.json
[wax]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/HoneycombItem.java#L64-L113
[conversion]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/CopperChestBlock.java#L35-L52
[loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/waxed_weathered_copper_chest.json
[removal-dispatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java#L311-L323
[removal]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java#L250-L254
[placement]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/ChestBlock.java#L199-L220
[registry]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/Blocks.java#L6391-L6439
[lightning]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LightningBolt.java#L173-L211
[axe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/AxeItem.java#L61-L123
[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L2632
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1174-L1181
