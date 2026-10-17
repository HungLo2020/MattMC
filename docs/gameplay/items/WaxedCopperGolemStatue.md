# Waxed Copper Golem Statue

**Waxed Copper Golem Statue** is the item form of the waxed Unaffected [Copper Golem Statue](../blocks/CopperGolemStatues.md). Its placed form is a poseable decoration. [Item registration][item] · [Block registration][block]

## Obtaining

Combine **1 [Copper Golem Statue](CopperGolemStatue.md) + 1 Honeycomb** in any arrangement in a crafting grid to make **1 Waxed Copper Golem Statue**. The input must be the matching Unaffected unwaxed statue. [Exact shapeless recipe][recipe]

Alternatively, use Honeycomb on that matching placed unwaxed statue, then mine the waxed result. The living golem's conversion creates only an **unwaxed Oxidized** statue; obtain the required finish before waxing. [Wax mapping][wax] · [Living conversion][formation]

It is also listed in the [inventory item browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item); use Creative for item insertion. Search for **Waxed Copper Golem Statue**. [Category entry][creative] · [English name][locale]

## Usage

Place it as a waxed Unaffected statue whose finish does not advance through normal block oxidation. For decoration and redstone, use its [four poses and Comparator output](../blocks/CopperGolemStatues.md#poses-and-revival). [Waxed registration][block] · [Pose handler][pose]

**Wax a placed statue to keep a custom name or pose.** The crafting recipe creates a fresh unnamed result in the default Standing pose; it does not copy those values from the ingredient. See [waxing and saved state](../blocks/CopperGolemStatues.md#wax-lightning-and-placement). [Fresh result][result] · [Default pose][item]

Placement restores a mined item's saved pose and name; an ordinary fresh stack starts Standing. The statue faces opposite your horizontal facing. The shared guide covers [poses](../blocks/CopperGolemStatues.md#poses-and-revival), [waterlogging and lightning](../blocks/CopperGolemStatues.md#wax-lightning-and-placement). [Placement][placement] · [Item data application][item-placement]

## Behavior

An unbroken axe first removes the wax, leaving an unwaxed [Copper Golem Statue](CopperGolemStatue.md) at the same stage; it does not revive the golem during that use. A later normal axe interaction can revive the Unaffected result. See the [revival sequence](../blocks/CopperGolemStatues.md#bring-the-statue-back-to-life). [Wax removal][axe] · [Revival condition][revival]

Mining normally returns **1 matching Waxed Copper Golem Statue**, including its custom name and pose. A pickaxe is efficient, but hand mining can collect it because the block has no correct-tool drop requirement. The bundled loot has no Silk Touch or Fortune variation and includes an explosion-survival check. See [mining and keeping the statue](../blocks/CopperGolemStatues.md#mining-and-keeping-the-statue). [Exact loot][loot] · [Block properties][block] · [Harvest rule][harvest] · [Pickaxe tag][pickaxe] · [Statue tag][statue-tag]

## Notes

- Item and placed-block ID: `minecraft:waxed_copper_golem_statue` [Item][item] · [Block][block]
- Source-reviewed at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716` on **2026-10-03**, including the bundled recipes, loot and optional packs. No in-game test was run; data packs and server settings can change the result

[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L2654-L2658
[block]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/Blocks.java#L6465-L6469
[recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/waxed_copper_golem_statue_from_honeycomb.json#L1-L13
[wax]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/HoneycombItem.java#L68-L113
[formation]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/coppergolem/CopperGolem.java#L261-L309
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1264-L1271
[locale]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/assets/minecraft/lang/en_us.json#L2368
[pose]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/CopperGolemStatueBlock.java#L109-L150
[result]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L58-L70
[placement]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/CopperGolemStatueBlock.java#L63-L83
[item-placement]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/BlockItem.java#L64-L126
[axe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/AxeItem.java#L61-L117
[revival]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/WeatheringCopperGolemStatueBlock.java#L50-L75
[loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/waxed_copper_golem_statue.json#L1-L37
[harvest]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json#L389
[statue-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/block/copper_golem_statues.json#L1-L12
