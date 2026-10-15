# Cactus

**Cactus** (`minecraft:cactus`) is a renewable plant that supplies [Green Dye](../items/GreenDye.md#smelting). Give it clear space to grow, and keep yourself and loose items away from its damaging sides and top. [Registration][block] · [Growth][growth] · [Contact damage][damage]

## Finding and collecting cactus

The bundled **Desert, Badlands, Eroded Badlands, and Wooded Badlands** biome definitions include cactus patches. Their shared patch requests columns of **1–3 cactus blocks**, sometimes topped by a [Cactus Flower](../items/CactusFlower.md). These are generation rules, not a guarantee that every part of those biomes contains a cactus. [Desert][desert] · [Badlands][badlands] · [Eroded Badlands][eroded] · [Wooded Badlands][wooded] · [Desert placement][desert-placement] · [Badlands placement][badlands-placement] · [Shared patch][patch]

MattMC also wires cactus patches into **Dry Midlands** in the bundled normal [Primordial Caves](../dimensions/PrimordialCaves.md) dimension. Its dense patch requests **2–6 cactus blocks**, so a generated plant can exceed the three-block random-growth limit below. Space and placement checks can shorten or reject a generated column. [Dimension wiring][preset] · [Dry Midlands features][dry] · [Dense placement][dense-placement] · [Dense column][dense] · [Column placement][column]

Break cactus **by hand or with a tool**. Ordinary Survival mining returns **one cactus item per block**; neither Silk Touch nor Fortune changes that count. Its hardness is **0.4**, with no correct-tool requirement. The loot table has an explosion-survival condition, so blasts need not return the item. Leave the bottom block in place if you want the plant to grow again. [Properties][block] · [Loot][loot] · [Tool gate][tool-gate] · [Mining dispatch][mining]

Other acquisition and inventory uses are on the [Cactus item page](../items/Cactus.md).

## Placement and support

A placed cactus requires:

- **Another cactus or a block in the sand tag directly below**. The bundled tag contains **Sand, Red Sand, and Suspicious Sand**
- **No solid block or lava in any of its four side-adjacent positions**
- **No liquid block directly above**

The side restriction uses the game's solid-block test; it does not simply require every adjacent position to be air. Ordinary block-item placement also checks survival and obstruction. [Survival rules][support] · [Sand tag][sand] · [Item placement][placement]

Removing support or adding an invalid neighbor schedules a check **one game tick later**. If the cactus still cannot survive, that check breaks it with drops; a stacked section above can then lose its own support. Cactus has the **destroy** piston reaction. See [Pistons](Pistons.md#what-can-move) for the shared movement rules. [Neighbor scheduling][neighbor] · [Scheduled survival check][scheduled] · [Piston property][block]

## Growth and flowers

Growth uses **random ticks**, requires **air directly above**, and has no light-level or nearby-water check. Bone Meal does **not** accelerate cactus or make its flower appear: cactus does not implement the target interface used by Bone Meal. [Growth callback][growth] · [Random-tick dispatch][random-dispatch] · [Bone Meal dispatch][bone-meal]

A new cactus starts at **age 0**. While the space above remains air, random ticks advance its age toward **15**. At age 15, a column shorter than **three cactus blocks** can add a block above and reset the former top's age. Three blocks is the limit for this growth routine, not a limit on manual stacking or the taller generated columns described above. Random ticking does not provide a fixed real-time harvest interval. [Default age and growth][growth]

At **age 8**, the top can instead grow a **Cactus Flower** if a cactus would be able to survive in the prospective position above it. The attempt has a **10% chance on a one- or two-block cactus**, or **25% on a column at least three blocks tall**. These are chances on that age-8 attempt, not on every random tick. A flower occupies the needed air space and stops further cactus aging and upward growth until removed. A top on a column at least three blocks tall that reaches age 15 does not reset its age or repeat the age-8 attempt; replacing that top provides a fresh age-0 block. [Flower attempt and air check][growth]

For a display plant, use a [Flower Pot](FlowerPot.md#supported-plants). Its separate potting and growth rules apply; a potted cactus is not a growing cactus column.

### Collecting flowers repeatedly

**Removing only the flower does not reset the cactus's age.** The age-8 tick advances the top to age 9 even when it produces a flower. After you pick that flower, the same top resumes aging rather than making another age-8 attempt. On a column at least three blocks tall, it eventually stops at age 15 without growing a replacement top. [Current growth routine][repeat-growth]

For another attempt on a **three-block column**, collect any flower, then **break and replant just the top cactus block**, leaving the lower two and their support in place. An ordinarily harvested cactus item does not retain the old age; replanting uses the current **age-0 default**. Leave air above the replanted top and wait for random ticks. This restarts one 25% flower attempt, not a guaranteed flower or a fixed harvest timer. A missed attempt needs another reset; stacking higher does not improve the chance. Keep loose drops away from the remaining plant's [damaging sides](#contact-and-loose-item-hazards). [Native cactus definition][repeat-definition] · [Default age][repeat-default] · [Cactus loot][repeat-loot] · [Item placement][repeat-placement] · [Default placement state][repeat-state] · [Attempt and height limit][repeat-growth]

If the cactus stays intact but never flowers, check clearance at the **prospective flower's height**, one block above the top cactus. The attempt applies cactus survival rules at that position: none of its four horizontal neighbors may be solid or contain lava, and the block directly above that position must not be liquid. A wall beside the future flower can therefore prevent an attempt even if it does not break the existing cactus. With air above the cactus, that blocked age-8 opportunity still advances its age; clearing the wall later does not rewind it. [Attempt position and aging][repeat-growth] · [Survival checks][repeat-support]

## Contact and loose-item hazards

The active contact callback applies **1 damage point**, equivalent to **half a heart before damage handling**, using the cactus damage type. Entity-specific immunity and protection can still affect the result. Avoid brushing against the plant or standing on its top. [Cactus callback][damage] · [Collision dispatch][collision] · [Server damage dispatch][hurt]

**Dropped items can be destroyed by cactus**, including harvested cactus that lands against another part of the plant. An ordinary item entity starts with **5 health** and loses health to accepted contact hits; reaching zero discards it. Item damage resistance is checked, so this is not a promise that every possible item vanishes immediately. Keep collection paths clear of the plant. [Item health][item-health] · [Item damage and destruction][item-damage] · [Item resistance][item-resistance]

Related: [Cactus item](../items/Cactus.md) · [Green Dye](../items/GreenDye.md) · [Cactus Flower](../items/CactusFlower.md) · [Flower Pot](FlowerPot.md) · [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Checked registrations, bundled biome/feature wiring, placement and neighbor callbacks, current random-tick and entity-contact dispatch, Bone Meal targeting, and ordinary loot. No in-game generation, farming, placement, support-removal, mining, explosion, or damage test was run. Data packs can change world generation, tags, and loot.

Repeat-flower collection and clearance were source-reviewed on **2026-10-15** at `6c069f5851cb3d6e1549ee478909559c2783cfe6`, including the native default/physical definitions and active random-tick callback path. The reset sequence is inferred from placement and growth code; no flower farm, collection-rate or gameplay test was run. Earlier citations retain their original review scope. [Native ticking property][repeat-physical] · [Current callback dispatch][repeat-dispatch]

[block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L1946-L1961
[growth]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/CactusBlock.java#L25-L82
[damage]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/CactusBlock.java#L125-L130
[desert]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/desert.json
[badlands]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/badlands.json
[eroded]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/eroded_badlands.json
[wooded]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/wooded_badlands.json
[desert-placement]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/placed_feature/patch_cactus_desert.json
[badlands-placement]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/placed_feature/patch_cactus_decorated.json
[patch]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/configured_feature/patch_cactus.json
[preset]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[dry]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/biome/dry_midlands.json
[dense-placement]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/placed_feature/patch/cactus_dense.json
[dense]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/configured_feature/patch/cactus_dense.json
[column]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/levelgen/feature/BlockColumnFeature.java
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/cactus.json
[tool-gate]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[mining]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[support]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/CactusBlock.java#L112-L123
[sand]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/sand.json
[placement]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/BlockItem.java#L111-L138
[neighbor]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/CactusBlock.java#L94-L110
[random-dispatch]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerLevel.java#L487-L507
[bone-meal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L77
[collision]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Entity.java#L1175-L1205
[hurt]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Entity.java#L1772-L1784
[item-health]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/item/ItemEntity.java#L44-L54
[item-damage]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/item/ItemEntity.java#L264-L286
[item-resistance]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ItemStack.java#L1101-L1104
[scheduled]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/CactusBlock.java#L46-L51
[repeat-growth]: https://github.com/HungLo2020/MattMC/blob/6c069f5851cb3d6e1549ee478909559c2783cfe6/src/main/java/net/minecraft/world/level/block/CactusBlock.java#L56-L84
[repeat-support]: https://github.com/HungLo2020/MattMC/blob/6c069f5851cb3d6e1549ee478909559c2783cfe6/src/main/java/net/minecraft/world/level/block/CactusBlock.java#L114-L125
[repeat-definition]: https://github.com/HungLo2020/MattMC/blob/6c069f5851cb3d6e1549ee478909559c2783cfe6/src/main/rust/content/block/definitions/catalog.rs#L305-L306
[repeat-default]: https://github.com/HungLo2020/MattMC/blob/6c069f5851cb3d6e1549ee478909559c2783cfe6/src/main/rust/content/block/definitions/templates.rs#L63
[repeat-loot]: https://github.com/HungLo2020/MattMC/blob/6c069f5851cb3d6e1549ee478909559c2783cfe6/src/main/resources/data/minecraft/loot_table/blocks/cactus.json#L1-L21
[repeat-placement]: https://github.com/HungLo2020/MattMC/blob/6c069f5851cb3d6e1549ee478909559c2783cfe6/src/main/java/net/minecraft/world/item/BlockItem.java#L110-L125
[repeat-state]: https://github.com/HungLo2020/MattMC/blob/6c069f5851cb3d6e1549ee478909559c2783cfe6/src/main/java/net/minecraft/world/level/block/Block.java#L444-L447
[repeat-physical]: https://github.com/HungLo2020/MattMC/blob/6c069f5851cb3d6e1549ee478909559c2783cfe6/src/main/rust/content/block/definitions/physics.rs#L397-L403
[repeat-dispatch]: https://github.com/HungLo2020/MattMC/blob/6c069f5851cb3d6e1549ee478909559c2783cfe6/src/main/java/net/minecraft/world/level/chunk/NativeRandomTicks.java#L170-L176
