# Breeze Rod

**Collect Breeze Rods for [Wind Charges](WindCharge.md) or a [Mace](Mace.md).** Keep spare rods if you plan to repair a Mace: crafting ammunition and repairing equipment consume the same resource. [Recipes][wind-recipe] · [Mace recipe][mace-recipe] · [Repair ingredient][registration]

## Obtaining

Defeat a **[Breeze](../mobs/Breeze.md)** with player kill credit. The verified encounter route is the Breeze Trial Spawner in **[Trial Chambers](../structures/TrialChambers.md)**; the connected template stores normal and ominous configurations that both select Breezes. Actual spawns still require the spawner's rule/difficulty gates and space checks to pass. Follow the chamber and mob guides for the encounter. [Spawner template][trial-template] · [Normal configuration][trial-normal] · [Ominous configuration][trial-ominous] · [Spawn gates][trial-gates]

With ordinary mob loot enabled, the bundled death table drops **1–2 rods** before Looting, but only when its **player-credit condition** passes. Recent credited player damage can supply that condition; a death with no player credit does not yield rods from this table. The active damage path also recognizes a tamed Wolf's owner. [Rod table][rod-loot] · [Player-credit predicate][player-condition] · [Damage credit][damage-credit] · [Death loot and context][death-context] · [Mob-loot rule][mob-loot]

Looting adds **a rounded random amount from 1–2 times the effective Looting level**. With the bundled main-hand Looting definition, the resulting total ranges are:

| Effective Looting level | Rods from a qualifying death |
| --- | ---: |
| None | 1–2 |
| I | 2–4 |
| II | 3–6 |
| III | 4–8 |

These are possible ranges, not equally likely quantities. The bonus reads the living attacker associated with the final damage source; player credit from an earlier hit does not by itself guarantee a Looting bonus. The table has no fire-to-cooked-item conversion: its reward entry remains Breeze Rod. [Count function][looting-count] · [Attacker context][death-context] · [Main-hand enchantment lookup][looting-read] · [Looting definition][looting] · [Complete rod table][rod-loot]

## Usage

- **Wind Charges:** **1 rod → 4 Wind Charges**, consuming the rod. Follow the [Wind Charge recipe](WindCharge.md#obtaining) and its throwing/movement guide. [Recipe][wind-recipe] · [Crafting consumption][craft-take]
- **Mace:** **1 rod + 1 Heavy Core → 1 Mace**. Follow [Mace crafting](Mace.md#obtaining) for the shared recipe and the core's separate acquisition route. Both ingredients are consumed. [Recipe][mace-recipe] · [Crafting consumption][craft-take]
- **Copy a Flow armor trim template:** **1 rod + 7 Diamonds + 1 existing Flow template → 2 Flow templates**, a net gain of one template. All inputs are consumed. Follow the [Flow template guide](SmithingTemplateFlowArmorTrim.md#obtaining) for the shared layout and first-template route. [Duplication recipe][flow-recipe] · [Crafting consumption][craft-take]
- **Mace repair:** on an Anvil, each rod used repairs **up to 125 durability** of the Mace's 500, capped by its missing durability. Taking the result consumes the required rods and, in Survival, charges the Anvil's displayed level cost. See [Mace repair](Mace.md#durability-repair-and-a-broken-mace) for the complete repair and broken-item rules. [Repair registration][registration] · [Repair calculation][repair] · [Taking the result][repair-take]

## Behavior

Breeze Rods stack to **64**. The rod itself is a plain ingredient; use it in crafting or Anvil repair rather than expecting it to throw a wind burst. [Registration][rod-registration] · [Default stack limit][stack] · [Ordinary item use][plain-use]

## Notes

* Registered item: `minecraft:breeze_rod`
* Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`, including connected spawner configurations, player-credit and Looting paths, recipes and repair consumption. No in-game spawning, combat, drop, crafting or repair test was run. Modified loot, enchantments, components and server rules can change the results

Related: [Breeze](../mobs/Breeze.md) · [Trial Chambers](../structures/TrialChambers.md) · [Wind Charge](WindCharge.md) · [Mace](Mace.md) · [Items](Items.md)

[wind-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/wind_charge.json
[mace-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/mace.json
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2033-L2045
[trial-template]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/structure/trial_chambers/spawner/breeze/breeze.nbt
[trial-normal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/trial_spawner/trial_chamber/breeze/normal.json
[trial-ominous]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/trial_spawner/trial_chamber/breeze/ominous.json
[trial-gates]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawner.java#L147-L233
[rod-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/breeze.json
[player-condition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/predicates/LootItemKilledByPlayerCondition.java#L21-L28
[damage-credit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1325-L1339
[death-context]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1527
[mob-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/Monster.java#L130-L133
[looting-count]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java#L64-L80
[looting-read]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L280-L292
[looting]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/enchantment/looting.json
[craft-take]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L78-L111
[flow-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/flow_armor_trim_smithing_template.json
[repair]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L132-L151
[repair-take]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L72-L97
[rod-registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2033-L2033
[stack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L390
[plain-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L164-L191
