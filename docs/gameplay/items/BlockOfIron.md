# Block of Iron

A **Block of Iron** (`minecraft:iron_block`) stores refined Iron Ingots and supplies the body material for an Iron Golem. It is not interchangeable with a Block of Raw Iron.

## Obtaining

Nine Iron Ingots pack into one block, which unpacks into nine ingots. The existing [Iron Ingot guide](IronIngot.md#useful-crafting-choices) owns these material conversions.

Collect a placed block with a **Stone-tier or better pickaxe**, including a Copper Pickaxe in MattMC. The block requires the correct tool, is in the pickaxe tag, and has the Stone-tier requirement. Wooden and Golden Pickaxes do not qualify. Its ordinary block loot returns one Block of Iron.

## Use

Use **four blocks** in the [Iron Golem construction pattern](../mobs/IronGolem.md#build-an-iron-golem), then add its head last. The successful pattern consumes the blocks; the golem's death drops do not repay the 36-ingot construction cost.

Iron Blocks also appear in the [Anvil recipe](../blocks/Anvil.md). Keep stored blocks separate from Raw Iron and other metal blocks when following exact recipes or construction patterns.

## Related pages

- [Iron Ingot](IronIngot.md)
- [Iron Golem](../mobs/IronGolem.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. Registration, conversion recipes, mining-tier tags, loot, and the golem pattern were checked. No gameplay crafting, mining, or construction test was run.

- [Block registration](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java)
- [Block-item registration](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java)
- [Packing recipe](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/iron_block.json)
- [Unpacking recipe](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/iron_ingot_from_iron_block.json)
- [Pickaxe tag](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json)
- [Required mining tier](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json)
- [Copper tool exclusions](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/incorrect_for_copper_tool.json)
- [Wood tool exclusions](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json)
- [Gold tool exclusions](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/incorrect_for_gold_tool.json)
- [Block loot](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/iron_block.json)
- [Golem pattern](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/CarvedPumpkinBlock.java)
