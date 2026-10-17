# Infested Cobblestone

**Infested Cobblestone** is the placeable infested lookalike of ordinary [Cobblestone](Cobblestone.md). Collecting its host material and obtaining this infested item are different tasks.

## Obtaining

It is listed in the **Functional Blocks** category used by the [inventory browser](../mechanics/InventoryBrowser.md#mode-and-permission-limits); this listing has no operator-category permission requirement. Use Creative mode to request it there; ordinary Survival insertion requests are skipped. [Category][creative-category] · [Entry][creative-entry] · [Packet gate][packet-gate]

No checked bundled crafting, cooking or stonecutting JSON declares this item as its output. **Mining never returns Infested Cobblestone**: with `doTileDrops` enabled, **Silk Touch gives 1 ordinary [Cobblestone](Cobblestone.md) block**; without Silk Touch, the block drops **no item**. [Exact loot][loot] · [Item-drop rule][item-drops]

This infested block has **no correct-tool requirement**. Mining it by hand still reaches the Silverfish-release check; using a weak or unsuitable tool is not a way to make it safe. See the [Stone family's collection and infestation rules](../blocks/Stone.md#infested-stone-variants). [Registration][blocks] · [Tool check][tool-check] · [Mining path][mining]

## Usage

Place it when you want a Silverfish-bearing imitation of Cobblestone. For ordinary building materials and their recipes, use the [Stone family guide](../blocks/Stone.md#stone-and-cobblestone).

## Behavior

Ordinary Survival mining without a spawn-suppressing enchantment can release a **[Silverfish](../mobs/Silverfish.md)** when `doTileDrops` is enabled. The bundled suppression tag contains **Silk Touch**, so that enchantment both prevents this release and selects the ordinary host-item drop above. [Release check][release] · [Suppression tag][suppression] · [Exact loot][loot]

Ordinary Creative player breaking skips the drop-and-release path. See [infested stone variants](../blocks/Stone.md#infested-stone-variants) for destructive explosions and the separate Silverfish hiding and wake-up rules; different removal paths do not all behave like mining. [Creative/mining path][mining]

## Notes

* This item places the `minecraft:infested_cobblestone` block; its ordinary host is `minecraft:cobblestone`. [Item registration][items] · [Block registration][blocks]

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Bundled recipe files, loot, tool eligibility, Creative permissions, packet admission and active break callbacks were checked. No in-game inventory, mining, placement or Silverfish test was run.

[creative-category]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1031-L1038
[creative-entry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1272-L1278
[packet-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L40-L58
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/infested_cobblestone.json
[item-drops]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L410-L416
[blocks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2277-L2279
[tool-check]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[mining]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L297
[release]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/InfestedBlock.java#L51-L66
[suppression]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/enchantment/prevents_infested_spawns.json
[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L532
