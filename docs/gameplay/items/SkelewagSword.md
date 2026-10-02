# Skelewag Sword

**Skelewag Sword** (`minecraft:skelewag_sword`) is a single-stack item whose current implementation has **no configured sword damage, attack-speed bonus or special mining ability**. Its sword name and appearance do not give it the properties of the standard swords. [Registration][item] · [English name][name] · [Current implementation][class] · [Default components][common]

## Obtaining

Search for **Skelewag Sword** in the [inventory item browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item). It is listed in the Combat category, and MattMC's browser can insert ordinary listed items in Survival and Creative when inventory space is available. [Category entry][creative]

The checked bundled recipes, loot tables and structure data contain no Skelewag Sword acquisition entry, and no named mob-drop caller was found. A crafting recipe or Skelewag drop should therefore not be assumed from the item's name.

## Usage

You can hold it and perform ordinary player attacks or mine blocks. Those actions use the ordinary held-item rules: the default stack supplies no sword attack modifiers, no extra attack-damage callback, and no special mining speed or correct-tool qualification. It does not replace a pickaxe or a configured sword. [Player attack][attack] · [Inherited bonus][bonus] · [Mining speed][mining-speed] · [Tool checks][mining]

Right-clicking has no dedicated Skelewag Sword action in the inspected implementation. [Implementation][class] · [Inherited use][item-use]

## Behavior

The item is limited to **one per stack** and has a configured maximum durability of **430**. Ordinary successful attacks and block mining do **not** spend that durability through the current item's inherited callbacks: attack wear requires a weapon component, and mining wear requires a tool component. This describes a normal, unmodified stack; the durability value alone does not promise 430 sword attacks. [Registration][item] · [Durability properties][properties] · [Attack and mining dispatch][wear] · [Inherited mining][mining]

The standard sword setup separately supplies its attack modifiers, tool rules and attack wear. Skelewag Sword does not call that setup. [Standard sword properties][sword-properties] · [Current registration][item]

## Notes

- Item ID: `minecraft:skelewag_sword`
- Source-reviewed at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6` on 2026-10-02. No combat, mining, browser-insertion or gameplay test was run

Related: [Skelewag](../mobs/Skelewag.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L2748
[name]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/assets/minecraft/lang/en_us.json#L5285
[class]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/item/ItemSkelewagSword.java#L7-L13
[common]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[properties]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Item.java#L354-L390
[creative]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1581-L1591
[attack]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L971-L982
[bonus]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Item.java#L221-L234
[item-use]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Item.java#L164-L192
[mining]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Item.java#L236-L252
[mining-speed]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Item.java#L168-L171
[wear]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ItemStack.java#L546-L584
[sword-properties]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ToolMaterial.java#L62-L90
[break-caller]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L291
