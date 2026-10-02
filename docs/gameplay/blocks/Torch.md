# Torch

A Torch is an inexpensive placed light source. Ordinary Torches and their wall form emit **light level 14**; the blue-flamed [Soul Torch](#soul-torch) forms emit **10** and affect Piglin avoidance. Choose the kind for its light and behavior, rather than assuming every torch is interchangeable. These light values do not establish a guaranteed spawn-proof radius. [Ordinary registrations][ordinary-reg] · [Soul registrations][soul-reg]

## Crafting

Place one [Coal](../items/Coal.md) or [Charcoal](../items/Charcoal.md) directly above one Stick to craft **four Torches**. This vertical recipe fits a 2 × 2 inventory crafting grid or a [Crafting Table](CraftingTable.md). Coal and Charcoal are explicit alternatives here; that does not make them interchangeable in every recipe. [Ordinary recipe][ordinary-recipe]

## Placement

The ordinary item places `minecraft:torch` on a floor or `minecraft:wall_torch` against a wall; the Soul Torch item similarly chooses its [two forms](#soul-torch). There is no separate wall-form inventory item. A standing torch needs center support on the top face beneath it. A wall torch needs a sturdy side face behind it and faces away from that support. These items do not hang from a ceiling. Removing the required support breaks the torch. [Item registrations][items] · [Placement routing][placement] · [Standing support][base] · [Wall support and facing][wall]

Both ordinary and Soul forms are instant-break, non-colliding blocks. They emit light without redstone power, a burn timer, or refueling. They have **no waterlogged state**: water that advances into the position replaces the torch and runs its ordinary block-drop path. Keep them out of flowing-water channels. [Properties][ordinary-reg] · [Soul properties][soul-reg] · [Torch handler][torch] · [Wall handler][wall] · [Fluid admission][fluid-entry] · [Fluid replacement][flow] · [Water drops][water-drops]

With normal block drops enabled, hand mining or support loss returns **one matching Torch item**. The wall forms inherit their standing forms' loot. No tool tier or Silk Touch is required, and Fortune adds nothing to these self-drops; explosions apply a survival condition. See [Mining](../mechanics/Mining.md) for tool requirements versus loot. [Ordinary loot][ordinary-loot] · [Soul loot][soul-loot] · [Wall loot inheritance][wall-loot] · [Tool gate][harvest] · [Harvest dispatch][harvest-call] · [Support removal][support-removal] · [Removal drops][removed-drops] · [Block-drop rule][drops]

## Soul Torch

| Placed form | Registered block ID | Inventory item and drop | Light |
| --- | --- | --- | ---: |
| Standing Soul Torch | `minecraft:soul_torch` | Soul Torch | 10 |
| Wall Soul Torch | `minecraft:soul_wall_torch` | Soul Torch | 10 |

Craft a vertical column of **1 Coal or Charcoal, 1 Stick, then 1 Soul Sand or Soul Soil**, from top to bottom, for **4 Soul Torches**. The three-row recipe needs a Crafting Table. The bottom ingredient uses the `soul_fire_base_blocks` item tag, whose bundled choices are Soul Sand and Soul Soil. The finished torch can stand on ordinary suitable support; Soul Sand/Soil is a crafting ingredient, not a special placement requirement. [Recipe][soul-recipe] · [Ingredient tag][soul-ingredient] · [Support][base]

A Soul Torch is also a possible **Ancient City chest** selection: its loot entry supplies **1–15** when selected. This is not a guaranteed chest reward. The bundled Barracks template assigns that chest table and is a choice in the city's structure pool. [Chest entry][city-loot] · [Barracks template][barracks] · [Structure pool][city-pool]

### Piglin avoidance and melting

Both placed Soul forms are in the Piglin repellent block tag. Ordinary [Piglins](../mobs/Piglin.md) search up to **8 blocks horizontally and 4 vertically** for such blocks; their idle and celebration behaviors can choose to move away. This is an AI influence, not a solid barrier or a guarantee against an attacking Piglin. The checked fight behavior does not run that same avoidance task. [Repellent tag][repellents] · [Sensor][sensor] · [Activity behavior][piglin-ai] · [Active Piglin brain][piglin]

The Soul Torch **item** is separately tagged to prevent Piglin pickup. Holding it does not turn the placed-block search into a portable repellent. Ordinary and Copper Torches are absent from those bundled repellent tags. [Item tag][repellent-items] · [Pickup refusal][pickup] · [Placed-block sensor][sensor]

Soul Torch light alone is below the melting thresholds for ordinary Ice and Snow layers. Ordinary Torches are bright enough to trigger those checks nearby when sufficient block light reaches the target. Other nearby lights still matter; Soul Torches do not protect Ice or Snow against them. See [Ice melting](Ice.md#ordinary-ice-melting-and-breaking) and [Snow](Snow.md) for the canonical rules. The flame particles are not a separate heat rating. [Light registrations][soul-reg] · [Ice check][ice] · [Snow-layer check][snow] · [Particle handler][torch]

## Other torch types

These are separate registrations and should not be treated as the same item:

| Type | Registered light | Further guide |
| --- | ---: | --- |
| Ordinary Torch | 14 | [Placement](#placement) |
| Copper Torch | 14 | [Copper Torches](CopperLighting.md#copper-torches) |
| Soul Torch | 10 | [Soul Torch](#soul-torch) |
| Redstone Torch, when lit | 7 | [Redstone Torch](RedstoneTorch.md) |

Redstone torches have circuit behavior beyond this lighting guide. Their lower light value does not describe their redstone output strength. [Copper/Soul registrations][soul-reg] · [Redstone registration][redstone-reg]

## Related pages

- [Torch item](../items/Torch.md) and [Soul Torch item](../items/SoulTorch.md)
- [Coal](../items/Coal.md) and [Charcoal](../items/Charcoal.md)
- [Lanterns](Lanterns.md), including the Soul Lantern recipe that consumes a Soul Torch
- [End Rod](EndRod.md), [Jack o'Lantern](JackOLantern.md), and [Ambersol](Ambersol.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `cf1c134b3f9ff634490e448fe26c335b90f82227`. Checked the ordinary and Soul forms' registrations, item routing, support, fluid replacement, recipes, loot, Piglin tags and active brain paths, and light-melting callbacks. Ancient City acquisition is a checked template/table route. No gameplay placement, fluid, avoidance, lighting-radius, or harvesting test was run. Data packs and game rules can change the bundled results.

[ordinary-reg]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L1193-L1202
[soul-reg]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L2033-L2052
[redstone-reg]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L1905-L1914
[ordinary-recipe]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/crafting/torch.json
[soul-recipe]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/crafting/soul_torch.json
[soul-ingredient]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/tags/item/soul_fire_base_blocks.json
[ordinary-loot]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/loot_table/blocks/torch.json
[soul-loot]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/loot_table/blocks/soul_torch.json
[items]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/item/Items.java
[placement]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/item/StandingAndWallBlockItem.java
[base]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/BaseTorchBlock.java
[wall]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/WallTorchBlock.java
[torch]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/TorchBlock.java
[wall-loot]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L7264-L7272
[support-removal]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Block.java#L213-L226
[removed-drops]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/Level.java#L263-L283
[city-loot]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/loot_table/chests/ancient_city.json
[barracks]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/structure/ancient_city/structures/barracks.nbt
[city-pool]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/worldgen/template_pool/ancient_city/structures.json
[repellents]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/tags/block/piglin_repellents.json
[repellent-items]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/tags/item/piglin_repellents.json
[sensor]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/ai/sensing/PiglinSpecificSensor.java#L117-L127
[piglin-ai]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L153-L191
[pickup]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L452-L458
[piglin]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/monster/piglin/Piglin.java
[flow]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L268-L278
[fluid-entry]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L400-L427
[water-drops]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/material/WaterFluid.java#L88-L91
[harvest]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[harvest-call]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L278-L294
[drops]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Block.java#L364-L420
[ice]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/IceBlock.java#L50-L63
[snow]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/SnowLayerBlock.java#L105-L111
