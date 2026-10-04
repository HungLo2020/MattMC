# Monster Spawner

**Monster Spawner** (`minecraft:spawner`) is the item form of the ordinary mob-spawning cage. Its [block guide](../blocks/MonsterSpawner.md) explains activation, attempts, limits, and configuration. It is distinct from [Trial Spawner](TrialSpawner.md). [Item registration](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L474)

## Obtaining

Request the registered item through the [combined inventory item browser](../mechanics/InventoryBrowser.md) in Creative; its source entry belongs to **Spawn Eggs**. No bundled recipe creates it, and mining a placed spawner does not recover the item, including with Silk Touch. Preserve a found spawner if you want to keep using that location. [Creative listing](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1962-L1969) · [Empty block loot](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/spawner.json) · [Current catalog entry][catalog-entry] · [Combined browser][catalog-browser]

## Placement and use

Place the item as a block, then configure its mob with a suitable spawn egg. A plain new spawner has empty spawn data rather than an automatic Pig assignment. The server's spawner rule must be enabled, and the resulting mob still needs valid spawning conditions. [Spawn-egg interaction](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L50-L78) · [Default data](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/SpawnData.java#L27-L44)

The item is not a portable version of an existing populated spawner. See [collection limits](../blocks/MonsterSpawner.md#finding-and-preserving-one) before attempting to move one.

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Registration, Creative listing, bundled recipe absence, loot, spawn data, and configuration were checked. No gameplay test was run; commands and custom item/block-entity data can change the setup.

Related: [Monster Spawner block](../blocks/MonsterSpawner.md) · [Items](Items.md)

[catalog-entry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1967
[catalog-browser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
