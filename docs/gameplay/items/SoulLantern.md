# Soul Lantern

Soul Lantern (`minecraft:soul_lantern`) gives steady **light level 10** and can stand on a support or hang below one. It needs no fuel or redstone power. [Registration][lantern-reg] · [Lantern behavior][lantern]

## Obtaining

In a 3 × 3 Crafting Table grid, surround **one Soul Torch with eight Iron Nuggets → one Soul Lantern**. An ordinary Torch makes the separate [Lantern](Lantern.md), not this recipe's Soul Lantern. [Soul Lantern recipe][lantern-recipe]

**Hand mining recovers one Soul Lantern in current MattMC.** Its registration has no correct-tool drop requirement. A pickaxe speeds breaking through the lantern mining tag, but is not required to recover it. Silk Touch is unnecessary and Fortune adds no extra lanterns. [Registration][lantern-reg] · [Pickaxe tag][pickaxe] · [Lantern tag][lantern-tag] · [Tool gate][gate] · [Mining dispatch][mining] · [Loot][lantern-loot]

The Functional Blocks listing also supplies a [Creative inventory-browser route](../mechanics/InventoryBrowser.md). Visibility in Survival's catalog does not grant ordinary Survival insertion. The [Librarian trade described in the family guide](../blocks/Lanterns.md#crafting-and-obtaining) supplies an ordinary Lantern, not a Soul Lantern. [Category entry][lantern-category]

## Usage

Place it **on a support's upper center** or **hanging from a support's lower center**. The matching face must pass the center-support check; there is no wall-mounted form. Removing that floor or ceiling support breaks it and normally returns its item. [Placement and survival][lantern] · [Center support][center-support] · [Support removal][support-loss] · [Loot][lantern-loot]

A placed Soul Lantern is a **Piglin repellent**. This affects ordinary Piglin behavior separately from its light output; it is not protection against every hostile mob. See [Soul Lantern and Piglins](../blocks/Lanterns.md#soul-lantern-and-piglins). [Repellent tag][piglin-tag] · [Piglin sensor][piglin-sensor] · [Avoidance behavior][piglin-ai]

## Behavior

Place it in **source Water** to waterlog it, or fill a supported lantern with a Water Bucket. An empty Bucket removes its stored source Water while leaving a supported lantern in place. It still emits **light level 10 when waterlogged**, and redstone does not switch it off. [Placement and states][lantern] · [Bucket callbacks][waterlogged] · [Constant light][lantern-reg]

Standing, hanging and waterlogged are placed-block states. Ordinary recovery does not save the hanging position or Water in the item; a normal item's next placement chooses those states again. See [Lanterns and Soul Lanterns](../blocks/Lanterns.md) for detailed support and water rules. [Placement][lantern] · [Loot][lantern-loot]

## Notes

This is the item form of `minecraft:soul_lantern`, distinct from the light-level-15 [Lantern](Lantern.md). Normal recovery assumes block drops are enabled; its explosion-survival loot condition means blasts are not guaranteed recovery. [Item registration][items] · [Drop dispatch][drops] · [Loot][lantern-loot]

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`, using the bundled recipes, loot and tags and the active behavior paths linked here. No gameplay test was run; custom data-pack changes are outside this review.

[lantern-reg]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5397-L5408
[lantern]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/LanternBlock.java#L36-L101
[lantern-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/soul_lantern.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[lantern-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/lanterns.json
[gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[mining]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L297
[lantern-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/soul_lantern.json
[lantern-category]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1031-L1045
[center-support]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L323-L328
[support-loss]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L213-L233
[piglin-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/piglin_repellents.json
[piglin-sensor]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/ai/sensing/PiglinSpecificSensor.java#L118-L126
[piglin-ai]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L283-L285
[waterlogged]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java#L18-L50
[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java
[drops]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L364-L420
