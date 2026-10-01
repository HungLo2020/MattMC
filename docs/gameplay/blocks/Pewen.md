# Pewen family

Pewen is an integrated prehistoric tree and wood family from Alex's Caves. It includes logs, all-bark wood, stripped forms, planks, building shapes, signs, boats, saplings, branches, and pines. The registered blocks are present, but several recipes and normal wood-tool integrations differ from expectations.

## Growing and obtaining wood

A Pewen Sapling is connected to the `minecraft:pewen_tree` configured feature through its tree grower. The tree feature requires dirt-tag ground, dry replaceable space along its trunk, and space around its crown. It chooses a trunk-height parameter from **11–20**, so leave a generous open area rather than treating it as a small decorative sapling.

The sapling follows the standard two-stage growth process: random growth checks require brightness at least 9 above it, and bone meal can advance the stage when its success check passes. An unsuccessful tree-placement check means more room or appropriate ground may be needed.

Pines have a loot entry for saplings, with a base **5%** chance when not harvested with shears or Silk Touch; the listed Fortune chances rise to 6.25%, about 8.33%, and 10%. Shears or Silk Touch select the pines item instead. See [Pewen Sapling](../items/PewenSapling.md) for the acquisition distinction.

A configured and placed tree feature exists, but a biome entry actually placing it was not found in this review. Natural forests are **not verified**. Creative provides the family for building and testing.

## Construction and recipes

The following bundled shaped recipes use directly named Pewen ingredients. They require obtaining the input materials first; they are source-reviewed definitions, not an end-to-end Survival progression guarantee.

| Recipe arrangement | Output |
| --- | --- |
| Four Pewen Logs in a 2 × 2 square | Three Pewen Wood |
| Three Pewen Planks in a row | Six Pewen Slabs |
| Six Pewen Planks in a stair pattern | Four Pewen Stairs |
| Two rows of plank–stick–plank | Three Pewen Fences |
| Two rows of stick–plank–stick | One Pewen Fence Gate |
| Six Pewen Planks in two columns of three | Three Pewen Doors |
| Six Pewen Planks in two rows of three | Two Pewen Trapdoors |
| Two Pewen Planks in a row | One Pewen Pressure Plate |
| Two full rows of Pewen Planks above a centered Stick | Three Pewen Signs |
| Five Pewen Planks in a U shape | One Pewen Boat |

### Recipes and tools that need caution

- **Log to planks, button, and chest-boat recipes:** their bundled JSON still uses legacy ingredient objects. The active ingredient codec accepts holder/tag forms instead, so these definitions have a source-format incompatibility. They are not presented here as working crafting instructions.
- **Hanging sign:** its recipe refers to `minecraft:chain`; the current registry uses `minecraft:iron_chain`. That input mismatch is unresolved.
- **Stripping:** the standard axe stripping map has no Pewen entries. Registered stripped logs and wood do not by themselves establish a right-click axe conversion.
- **Generic wood recipes:** Pewen Planks were not found in the generic `minecraft:planks` item tag. Do not assume they substitute for oak in every plank-tag recipe.
- **Tool speed:** Pewen entries were not found in the standard axe-mineable block tag. The ordinary axe speed bonus is therefore not established just from their wood appearance.

These caveats describe the checked source and data. Runtime recipe-loading errors and actual behavior have not been reproduced here.

## Block behavior

Pewen logs and wood are axis-oriented with hardness 2. Planks have hardness 2 and blast resistance 3. Most building forms use cherry-wood sounds; doors and trapdoors use the cherry block-set behavior. The button is configured for **30 ticks** per activation. Signs and fence gates use the oak wood-type implementation, so visual or sound assumptions from upstream are not guaranteed.

## Related items

- [Pewen Log](../items/PewenLog.md), [Pewen Wood](../items/PewenWood.md)
- [Stripped Pewen Log](../items/StrippedPewenLog.md), [Stripped Pewen Wood](../items/StrippedPewenWood.md)
- [Pewen Planks](../items/PewenPlanks.md), [Pewen Sapling](../items/PewenSapling.md), [Pewen Pines](../items/PewenPines.md)
- [Blocks](Blocks.md)

## Sources and verification

Reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. Source/data review only; no in-game placement, growth, harvesting, or crafting test.

- [Family registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/Blocks.java#L6974-L7120)
- [Sapling registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/Blocks.java#L218-L222)
- [Tree grower](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexscaves/server/block/grower/PewenGrower.java)
- [Feature registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/levelgen/feature/ACFeatures.java)
- [Tree placement](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexscaves/server/level/feature/PewenTreeFeature.java)
- [Sapling growth](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/SaplingBlock.java)
- [Pines loot](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/blocks/pewen_pines.json)
- [Planks recipe incompatibility](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/pewen_planks.json)
- [Button recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/pewen_button.json)
- [Chest-boat recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/pewen_chest_boat.json)
- [Hanging-sign recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/pewen_hanging_sign.json)
- [Ingredient codec](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/crafting/Ingredient.java)
- [Holder-set codec](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/resources/HolderSetCodec.java)
- [Axe conversions](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/AxeItem.java)
- [Planks tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/planks.json)
- [Axe mining tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/mineable/axe.json)
- [Pewen slab recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/pewen_slab.json)
- [Pewen wood recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/pewen_wood.json)
- [Pewen stair recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/pewen_stairs.json)
- [Pewen fence recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/pewen_fence.json)
- [Pewen fence gate recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/pewen_fence_gate.json)
- [Pewen door recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/pewen_door.json)
- [Pewen trapdoor recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/pewen_trapdoor.json)
- [Pewen pressure plate recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/pewen_pressure_plate.json)
- [Pewen sign recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/pewen_sign.json)
- [Pewen boat recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/pewen_boat.json)
