# Vault

The **Vault** item places `minecraft:vault`, a reward block that accepts configured keys and records which players have opened it. [Vault](../blocks/Vault.md) is the canonical guide to generated normal/ominous forms, keys, rewards, and player history. [Item registration][items] · [Block behavior][vault]

## Obtaining

The item has a **Creative entry**. No bundled crafting recipe or normal mining drop supplies it in Survival. The placed block's loot table is empty, including when mined with Silk Touch or Fortune. Find generated Vaults in Trial Chambers and use them in place. [Creative entry][creative-vault] · [Block loot][vault-loot] · [Verified structure route](../blocks/Vault.md#finding-vaults)

## Usage

A fresh ordinary item places an inactive, normal-looking Vault facing its placer. Its default configuration accepts **one [Trial Key](TrialKey.md)** and selects the normal Trial Chamber reward table. Merely changing its ominous block-state flag does not change the configured key or rewards. [Placement][vault] · [Default configuration][config] · [Fresh block entity][entity]

Naturally generated ominous Vaults instead include an explicit Ominous Trial Key and ominous reward configuration. See [normal and ominous Vaults](../blocks/Vault.md#normal-and-ominous-vaults); there is no separate registered Ominous Vault inventory item. [Items][items] · [Ominous template][ominous-template]

## Behavior

This is not an ordinary storage container. Accepted use ejects loose reward stacks above the block and immediately records the player. The default player history normally prevents repeat openings at that same Vault; it has a 128-entry retention limit rather than a timed refill. [Use and reward flow][use-key] [state] · [History][history]

## Notes

Source-reviewed at `053cd852a8609f4002234ce0d445d3a345b551ae` on 2026-10-02. No gameplay test was run. [Placed Vault mechanics](../blocks/Vault.md) owns the detailed interactions and limitations.

[items]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/item/Items.java#L2674-L2677
[vault]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/VaultBlock.java
[creative-vault]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1263
[vault-loot]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/loot_table/blocks/vault.json
[config]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/vault/VaultConfig.java
[entity]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/vault/VaultBlockEntity.java
[ominous-template]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/structure/trial_chambers/reward/ominous_vault.nbt
[use-key]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/vault/VaultBlockEntity.java#L246-L273
[state]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/vault/VaultState.java
[history]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/vault/VaultServerData.java
