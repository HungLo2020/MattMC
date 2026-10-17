# Trial Key

A **Trial Key** (`minecraft:trial_key`) opens a correctly configured [normal Vault](../blocks/Vault.md#normal-vaults) for a player who is eligible there. Each successful Survival opening consumes **one key**. [Item registration][items] · [Natural Vault configuration][normal-template] · [Use flow][use-key]

## Obtaining

A completed **normal Trial Spawner** can eject this key as its selected reward; it is not guaranteed after every encounter. [Trial Spawner](../blocks/TrialSpawner.md) owns encounter completion and reward-selection details. There is no bundled crafting recipe, and the item is available in Creative. [Key loot][key-loot] · [Reward configuration][spawner-defaults] · [Reward dispatch][spawner-reward] [spawner-eject] · [Creative entry][creative-keys]

Trial Chamber entrance chests and corridor Decorated Pots also have a chance to contain a Trial Key. See the [verified key sources](../blocks/Vault.md#getting-keys), including their actual template wiring. [Chest loot][entrance-loot] · [Pot loot][pot-loot]

## Usage

Approach an eligible active Vault, then use the block with the key without Sneak/Crouch. Normal and ominous keys do not substitute for each other in generated Vaults. The key's item components must match the configured key too, so a renamed or otherwise modified key can be rejected. [Interaction][vault] · [Component/count check][key-test] [stack-match] · [Input routing][use-routing]

A Vault that has already rewarded you rejects another key while your UUID remains in its history. Waiting through the opening animation does not reset that record. Incorrect or rejected keys are not consumed by the Vault handler; Creative use keeps a valid key but still records the opening. [Acceptance and consumption][use-key] [consume] · [Player history](../blocks/Vault.md#player-history-and-persistence)

## Behavior

Stay nearby to collect the loose items ejected above the Vault. The cycling display is a preview, not a guaranteed next reward. See [Vault rewards and timing](../blocks/Vault.md#unlocking-and-ejection) for the full sequence and normal/ominous comparison. [Ejection][state] · [Display selection][display] · [Reward roll][roll]

## Notes

Source-reviewed at `053cd852a8609f4002234ce0d445d3a345b551ae` on 2026-10-02. No gameplay test was run. This item does not place a Vault and has no separate crafting conversion into the other key.

[items]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/item/Items.java#L2674-L2677
[normal-template]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/structure/trial_chambers/reward/vault.nbt
[use-key]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/vault/VaultBlockEntity.java#L246-L273
[key-loot]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/loot_table/spawners/trial_chamber/key.json
[spawner-defaults]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawnerConfig.java#L82-L95
[spawner-reward]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawnerState.java#L115-L130
[spawner-eject]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawner.java#L236-L249
[creative-keys]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1951-L1952
[entrance-loot]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/entrance.json
[pot-loot]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/resources/data/minecraft/loot_table/pots/trial_chambers/corridor.json
[vault]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/VaultBlock.java
[key-test]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/vault/VaultBlockEntity.java#L332-L341
[stack-match]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/item/ItemStack.java#L666-L677
[use-routing]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L340-L400
[consume]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1086
[state]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/vault/VaultState.java
[display]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/vault/VaultBlockEntity.java#L284-L304
[roll]: https://github.com/HungLo2020/MattMC/blob/053cd852a8609f4002234ce0d445d3a345b551ae/src/main/java/net/minecraft/world/level/block/entity/vault/VaultBlockEntity.java#L306-L330
