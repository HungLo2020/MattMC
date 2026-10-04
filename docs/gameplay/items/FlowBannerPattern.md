# Banner Pattern (Flow)

Flow Banner Pattern (`minecraft:flow_banner_pattern`) unlocks the **Flow** design in a Loom. It stacks to **one**. [Item registration][templates] · [Pattern tag][pattern-tag]

## Obtaining

Opening an **Ominous Vault** in a Trial Chamber with an **[Ominous Trial Key](OminousTrialKey.md)** can reward **one Flow Banner Pattern**. The bundled Vault structure specifies that key and its reward table; the server rolls that table when a valid, previously unrewarded player unlocks it. [Vault configuration][vault-template] · [Unlock and reward lookup][vault-reward]

The reward chain is `chests/trial_chambers/reward_ominous` → `chests/trial_chambers/reward_ominous_unique` → `minecraft:flow_banner_pattern`, all in the `minecraft:` namespace. The unique pool is chance-gated and also contains other rewards, so the template is **not guaranteed**. The [Guster Banner Pattern](GusterBannerPattern.md) comes from the normal Vault's unique-reward pool. [Reward pool][reward-pool] · [Unique rewards][unique-rewards]

## Usage

Put a Banner, a Dye, and this template into a [Loom](../blocks/Loom.md#adding-a-pattern). Its pattern tag supplies only **Flow**, so the Loom selects that design automatically when the Banner and Dye are present. The Dye sets the new layer's color. Check the preview, then take the decorated Banner to apply it. [Template tag][pattern-tag] · [Selection and result][loom-menu]

## Behavior

Taking one result consumes **one Banner and one Dye**, while **the template remains in its slot for reuse**. The ordinary Loom menu removes those two inputs even in Creative mode. Closing the menu returns remaining inputs through the normal inventory/drop handling. [Result-slot consumption][loom-menu] · [Returning inputs][clear-menu]

A template still obeys the [Loom's pattern limits](../blocks/Loom.md#pattern-limits). For copying a finished design or removing its latest layer, follow [Banners](../blocks/Banners.md#patterns-and-duplication).

## Notes

- This item is registered as `minecraft:flow_banner_pattern`.
- The default English item name is **Banner Pattern**. This wiki uses **Flow** to distinguish the item; its design is **Flow** (`minecraft:flow`). The specific wiki label is not a separate item ID. [English names][names] · [Registered item name][item-name] · [Pattern resource][pattern]
- Compare sources and designs in [All Loom templates](../blocks/Loom.md#available-designs-and-templates).

## Sources and verification

Reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on 2026-10-04. Source and bundled-data review only; no in-game crafting, trading, loot, or Loom test.

[templates]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2387-L2424
[pattern-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/banner_pattern/pattern_item/flow.json
[pattern]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/banner_pattern/flow.json
[loom-menu]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/LoomMenu.java
[clear-menu]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java#L598-L611
[names]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/assets/minecraft/lang/en_us.json
[item-name]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L122-L125
[vault-template]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/trial_chambers/reward/ominous_vault.nbt
[vault-reward]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/vault/VaultBlockEntity.java#L245-L329
[reward-pool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward_ominous.json
[unique-rewards]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward_ominous_unique.json
