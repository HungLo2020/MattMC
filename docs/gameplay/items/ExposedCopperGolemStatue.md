# Exposed Copper Golem Statue

**Exposed Copper Golem Statue** is the item form of the unwaxed Exposed [Copper Golem Statue](../blocks/CopperGolemStatues.md). Its placed form is a poseable decoration. [Item registration][item] · [Block registration][block]

## Obtaining

Use an unbroken axe once on a placed [Weathered Copper Golem Statue](WeatheredCopperGolemStatue.md), or twice on an [Oxidized statue](OxidizedCopperGolemStatue.md), then mine the Exposed result. A placed, unwaxed [Copper Golem Statue](CopperGolemStatue.md) can also weather into this stage. [Stage map][weather] · [Axe conversion][axe]

Removing wax from a placed [Waxed Exposed Copper Golem Statue](WaxedExposedCopperGolemStatue.md) with an unbroken axe also yields this stage. No bundled production recipe creates this unwaxed item; the verified starting route is [golem-to-statue conversion](../blocks/CopperGolemStatues.md#obtaining-a-statue), followed by any needed finish changes. [Wax removal][axe] · [Conversion][formation]

It is also listed in the [inventory item browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item); use Creative for item insertion. Search for **Exposed Copper Golem Statue**. [Category entry][creative] · [English name][locale]

## Usage

Combine **1 Exposed Copper Golem Statue + 1 Honeycomb** shapelessly to make **1 [Waxed Exposed Copper Golem Statue](WaxedExposedCopperGolemStatue.md)**. [Waxing recipe][recipe]

**Wax a placed statue to keep a custom name or pose.** The crafting recipe creates a fresh unnamed result in the default Standing pose; it does not copy those values from the ingredient. See [waxing and saved state](../blocks/CopperGolemStatues.md#wax-lightning-and-placement). [Fresh result][result] · [Default pose][item]

Placement restores a mined item's saved pose and name; an ordinary fresh stack starts Standing. The statue faces opposite your horizontal facing. The shared guide covers [poses](../blocks/CopperGolemStatues.md#poses-and-revival), [waterlogging and lightning](../blocks/CopperGolemStatues.md#wax-lightning-and-placement). [Placement][placement] · [Item data application][item-placement]

## Behavior

This is the unwaxed **Exposed** finish. It can weather into the [Weathered Copper Golem Statue](WeatheredCopperGolemStatue.md) while placed. Revival requires 1 scraping step back to Unaffected, then a separate normal axe interaction. Use the [revival guide](../blocks/CopperGolemStatues.md#bring-the-statue-back-to-life) for the exact controls. [Stage map][weather] · [Revival handler][revival]

Mining normally returns **1 matching Exposed Copper Golem Statue**, including its custom name and pose. A pickaxe is efficient, but hand mining can collect it because the block has no correct-tool drop requirement. The bundled loot has no Silk Touch or Fortune variation and includes an explosion-survival check. See [mining and keeping the statue](../blocks/CopperGolemStatues.md#mining-and-keeping-the-statue). [Exact loot][loot] · [Block properties][block] · [Harvest rule][harvest] · [Pickaxe tag][pickaxe] · [Statue tag][statue-tag]

## Notes

- Item and placed-block ID: `minecraft:exposed_copper_golem_statue` [Item][item] · [Block][block]
- Source-reviewed at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716` on **2026-10-03**, including the bundled recipes, loot and optional packs. No in-game test was run; data packs and server settings can change the result

[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L2639-L2643
[block]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/Blocks.java#L6450-L6454
[weather]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/WeatheringCopper.java#L52-L91
[axe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/AxeItem.java#L61-L117
[formation]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/coppergolem/CopperGolem.java#L261-L309
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1264-L1271
[locale]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/assets/minecraft/lang/en_us.json#L1651
[recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/waxed_exposed_copper_golem_statue_from_honeycomb.json#L1-L13
[result]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L58-L70
[placement]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/CopperGolemStatueBlock.java#L63-L83
[item-placement]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/BlockItem.java#L64-L126
[revival]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/WeatheringCopperGolemStatueBlock.java#L50-L75
[loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/exposed_copper_golem_statue.json#L1-L37
[harvest]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json#L389
[statue-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/block/copper_golem_statues.json#L1-L12
