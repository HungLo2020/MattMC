# Scaffolding

**Scaffolding** (`minecraft:scaffolding`) is a climbable temporary building platform made from [Bamboo](Bamboo.md) and [String](../items/String.md). Its special item extends existing columns and walkways, while its support distance decides which pieces stay in place. [Block registration][block] · [Special item registration][item] · [Placement][placement]

## Crafting and collecting

Use the **Scaffolding row in [String's selected crafting recipes](../items/String.md#selected-crafting-recipes)** for the canonical ingredient count and recipe. It is a shaped Crafting Table recipe using raw Bamboo items, rather than Blocks of Bamboo or Bamboo Planks. [Exact recipe][recipe]

Scaffolding has **zero hardness** and no correct-tool requirement. Ordinary mining by hand or with a tool returns **one Scaffolding item**. Silk Touch is not required and Fortune does not increase this drop. The loot has an explosion-survival condition. [Registration][block] · [Default properties][defaults] · [Tool gate][gate] · [Loot][loot] · [Mining dispatch][break]

## Extending a structure

Hold Scaffolding and use an existing Scaffolding block:

| How you use it | Direction searched for the next placement |
| --- | --- |
| Ordinary use on its side or underside | **Upward**, through the existing column |
| Ordinary use on its top | **Horizontally in the direction you face** |
| Secondary use | Toward the clicked face; a hit from inside the block uses the opposite direction |

The item searches along that direction until it finds a replaceable space or an obstruction. A horizontal search examines up to **seven positions ahead**; vertical searches are bounded by obstructions and world limits rather than this horizontal count. It places one item at the selected destination, not an entire column at once. [Special placement routing][placement]

For a fresh placement away from existing Scaffolding, the item rejects a destination whose support distance is 7. Extending an existing structure can place an unsupported piece temporarily, which the next support check handles. This is why the end of a long horizontal extension can fall even though the item allowed placement. [Fresh-placement check and extension][placement] · [Scheduled support check][tick]

## Support, overhangs, and collapse

Support uses the best available connection:

- A block with a **sturdy upper face directly underneath** gives distance **0**
- Scaffolding directly underneath passes up its own distance unchanged
- Each horizontal Scaffolding connection adds **1** to that neighbor's distance
- A distance below **7** is stable; distance **7** is unsupported

A column above a sturdy base can therefore support **six horizontal extensions** at the same level. A seventh needs another supporting route. Vertical stacking does not itself spend horizontal distance, and there is no special Scaffolding height cap below the world's build limit. [Distance calculation][support] · [Survival check][tick] · [World-bound placement][placement]

Placement and neighbor changes schedule a support check **one tick later**. The outcome depends on the stored and newly calculated distance:

| Change | Outcome |
| --- | --- |
| A piece already stored as distance 7 remains unsupported | Becomes a **falling block entity** |
| A previously supported piece loses its support and reaches distance 7 | **Breaks with block drops** |
| A valid supporting route remains | Keeps or updates its supported state |

Removing a base can therefore break multiple connected pieces as updates propagate. Falling Scaffolding can settle where its landing position passes replacement and support checks; a failed landing can drop an item under the entity-drop rules. Do not assume every collapsing piece remains a falling block or that every fall produces a placed block. [Support tick][tick] · [Active tick dispatch][dispatch] · [Starting a fall][fall-start] · [Landing and failed-landing handling][fall]

## Climbing and collision

Scaffolding is in the **climbable** block tag. While inside it, the active living-entity movement path resets fall distance and limits downward motion. **Jump** can move you upward; **Sneak** requests descent and bypasses the top deck's normal standing collision. [Climbable tag][climb-tag] · [Climbable detection][climb] · [Movement][movement] · [Descent input][descend]

Its collision is conditional, not a full solid cube:

- An entity above the block that is not descending collides with the upper frame, including the **2/16-block-thick top deck**
- A bottom piece with nonzero support distance can expose a **2/16-block-thick lower plate** while the entity is above that plate's bottom level; bypassing the top deck does not remove this lower plate
- Other entity positions can have an empty collision shape, and placement checks use an empty collision shape

This lower plate matters on overhangs: do not assume every piece allows uninterrupted descent through its underside. Holding Scaffolding also changes its **selection outline** to a full cube; that outline does not turn its movement collision into a full cube. [Shape definitions and outline][shape] · [Collision conditions][collision] · [Entity context][context] · [Active collision dispatch][collision-dispatch]

## Water and fire

Scaffolding can be **waterlogged**. Placement in a Water source sets its waterlogged state, and the shared waterlogging interaction lets a Water Bucket fill it or an Empty Bucket recover that water. Waterlogged Scaffolding still uses the same support-distance calculation. Follow the [Water Bucket guide](../items/WaterBucket.md) for bucket placement and dimension restrictions. [Placement state][water] · [Stored water][water-state] · [Fill/pickup handling][water-interface] · [Bucket dispatch][bucket] · [Support calculation][support]

One Scaffolding item provides **50 default Furnace burn ticks**, one quarter of a 200-tick recipe. Use [Furnace fuel planning](Furnace.md#fuel-planning) for batches and partial progress. Dry Scaffolding is registered as flammable; avoid relying on a frame exposed to spreading fire. [Fuel values][fuel] · [Server fuel setup][fuel-load] · [Fire registration][fire]

Related: [Scaffolding item](../items/Scaffolding.md) · [Bamboo](Bamboo.md) · [String](../items/String.md) · [Blocks](Blocks.md) · [Items](../items/Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Checked the special block item, exact recipe, support-distance and scheduled-tick callbacks, falling-block path, climbable movement, conditional shapes, waterlogging, loot, fuel, and fire registration. No in-game placement, climbing, collision, collapse, waterlogging, harvesting, or fuel test was run. Data packs can change recipes, tags, and loot.

[block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L5294-L5305
[item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L991
[placement]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ScaffoldingBlockItem.java
[recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/scaffolding.json
[defaults]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L992-L1001
[gate]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/scaffolding.json
[break]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[tick]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/ScaffoldingBlock.java#L97-L136
[support]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/ScaffoldingBlock.java#L157-L181
[dispatch]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L767-L772
[fall-start]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/item/FallingBlockEntity.java#L92-L102
[fall]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/item/FallingBlockEntity.java#L147-L239
[climb-tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/climbable.json
[climb]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1652-L1667
[movement]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2458-L2499
[descend]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Entity.java#L2561-L2563
[shape]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/ScaffoldingBlock.java#L29-L65
[collision]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/ScaffoldingBlock.java#L139-L150
[context]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/phys/shapes/EntityCollisionContext.java#L32-L66
[collision-dispatch]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L673-L678
[water]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/ScaffoldingBlock.java#L77-L95
[water-state]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/ScaffoldingBlock.java#L152-L155
[water-interface]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java#L18-L49
[bucket]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/BucketItem.java#L54-L85
[fuel]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L96
[fuel-load]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/MinecraftServer.java#L340
[fire]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/FireBlock.java#L485-L486
