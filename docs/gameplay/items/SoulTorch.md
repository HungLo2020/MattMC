# Soul Torch

A **Soul Torch** (`minecraft:soul_torch`) places a blue-flamed light on suitable floor or wall support. Its two placed forms emit **light level 10**. [Item routing][items] · [Block registrations][blocks]

## Obtaining

Use the [Soul Torch recipe and Ancient City chest route](../blocks/Torch.md#soul-torch). The block guide owns the exact ingredients and quantities. Breaking either placed form normally returns one Soul Torch, without a tool-tier or Silk Touch requirement. [Loot][loot]

## Usage

Use it for placed lighting or as the center ingredient for a [Soul Lantern](../blocks/Lanterns.md#crafting-and-obtaining). The shared [Torch guide](../blocks/Torch.md#placement) explains floor/wall support and water removal.

## Behavior

Placed Soul Torches affect [Piglin avoidance](../blocks/Torch.md#piglin-avoidance-and-melting). The item is also in the Piglin-repellent item tag, which prevents pickup; holding it does not turn that placed-block behavior into a portable repellent. [Item tag][tag] · [Pickup refusal][pickup] · [Placed-block search][sensor]

## Notes

- The same inventory item places `minecraft:soul_torch` or `minecraft:soul_wall_torch`; there is no separate Wall Soul Torch item
- Recipe, placed light, loot and behavior were source-reviewed on **2026-10-02** at `cf1c134b3f9ff634490e448fe26c335b90f82227`; no gameplay test was run

[items]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/item/Items.java#L522-L525
[blocks]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L2033-L2042
[loot]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/loot_table/blocks/soul_torch.json
[tag]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/tags/item/piglin_repellents.json
[pickup]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L452-L458
[sensor]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/ai/sensing/PiglinSpecificSensor.java#L117-L127
