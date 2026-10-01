# Anvil

An **Anvil** repairs equipment, changes item names, and combines compatible enchantments. MattMC caps the payment at **40 experience levels** instead of rejecting expensive work. See [anvil mechanics](../mechanics/AnvilMechanics.md) for inputs, costs, and what the result preserves.

The three block stages are `minecraft:anvil`, `minecraft:chipped_anvil`, and `minecraft:damaged_anvil`. All three use the same menu; wear changes how close the block is to breaking, not the available operations. [Block registrations][registration] · [Menu and wear stages][block]

## Crafting and collecting

At a [Crafting Table](CraftingTable.md), arrange **three Iron Blocks and four Iron Ingots**:

| Left | Center | Right |
| --- | --- | --- |
| Iron Block | Iron Block | Iron Block |
| Empty | Iron Ingot | Empty |
| Iron Ingot | Iron Ingot | Iron Ingot |

The recipe produces **one new Anvil**. Making the three blocks from ingots brings the total to **31 Iron Ingots**. [Anvil recipe][recipe] · [Iron Block recipe][iron-block]

Use a **pickaxe** to collect a placed anvil. Its registration requires the correct tool for drops, and all three stages belong to the pickaxe mining tag. Their block loot tables return one item matching the current wear stage, subject to explosion survival. Mining and replacing a Chipped or Damaged Anvil does not turn it into a new Anvil. [Tool requirement][registration] · [Pickaxe tag][pickaxe] · [Stage tag][anvil-tag] · [Anvil loot][loot] · [Chipped loot][chipped-loot] · [Damaged loot][damaged-loot]

## Using and wearing out an anvil

Place the anvil on firm support and interact with it. The left slot holds the item to keep; the middle slot takes a repair material, sacrifice item, or enchanted book. Preview the output before taking it. Renaming alone needs only the left slot. [Menu interaction][block] · [Repairing, naming, and combining](../mechanics/AnvilMechanics.md)

Each completed use by a player without Creative's infinite-material ability has a **12% chance** to advance one stage:

**Anvil → Chipped Anvil → Damaged Anvil → destroyed**

The roll happens when the result is taken, including a rename-only result. Merely opening the menu does not trigger it. Creative use skips this wear roll. The input slots are not storage: closing the menu clears and returns or drops their contents. [Completed-use handling][use] · [Menu closing][closing] · [Returning inputs][returning]

## Falling and impact damage

Anvils fall when the block below is air, fire-tagged, liquid, or replaceable. Do not remove their support while standing underneath. [Falling trigger][falling]

A falling anvil damages eligible living entities in its impact area; Creative and Spectator players are excluded. For recorded fall distance `d`, the base damage is **2 × ceil(d − 1)** points, with a minimum of 0 and a cap of **40 points (20 hearts)**, before the victim's damage handling. Distances that do not produce a positive amount do not cause the anvil's impact-wear roll. [Damage settings][block] · [Impact calculation][impact]

A damaging fall also has a **5% + 5% × ceil(d − 1)** chance to advance the anvil's wear stage, reaching certainty for sufficiently long falls. This roll does not require a victim to be present. If it destroys an already Damaged Anvil, that falling anvil does not drop an item. Creative's protection from menu-use wear does not disable falling wear. [Impact wear and destruction][impact]

## Related pages

- [Anvil mechanics](../mechanics/AnvilMechanics.md)
- [Anvil item](../items/Anvil.md)
- [Chipped Anvil item](../items/ChippedAnvil.md)
- [Damaged Anvil item](../items/DamagedAnvil.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-01** at commit `b81c01943c9f3254e713c365a1dd633392929cb2`. No in-game tests were run. Recipes, mining tags, and loot can change with data packs. Natural structure placement was not reviewed.

[registration]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2804-L2833
[block]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/AnvilBlock.java#L58-L108
[recipe]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/anvil.json
[iron-block]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/iron_block.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[anvil-tag]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/anvil.json
[loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/anvil.json
[chipped-loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/chipped_anvil.json
[damaged-loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/damaged_anvil.json
[use]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L71-L116
[closing]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/inventory/ItemCombinerMenu.java#L108-L112
[falling]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/FallingBlock.java#L48-L65
[impact]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/item/FallingBlockEntity.java#L247-L274

[returning]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java#L594-L612
