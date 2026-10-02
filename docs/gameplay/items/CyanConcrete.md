# Cyan Concrete

**Cyan Concrete** (`minecraft:cyan_concrete`) is the hardened cyan building block made from [Cyan Concrete Powder](CyanConcretePowder.md). The shared [Concrete guide](../blocks/Concrete.md) covers water conversion, support, and mining. [Block registration][block] · [Item registration][item]

## Obtaining

Craft [Cyan Concrete Powder](CyanConcretePowder.md#crafting), then expose the placed or falling powder to qualifying water contact. It hardens into **the same color**, cyan concrete. Use the [water-hardening guide](../blocks/Concrete.md#hardening-powder-with-water) for placement, flowing water, and contact limits. [Matching conversion target][powder-block] · [Hardening callbacks][hardening]

## Collection and building

Mine it with an **unbroken pickaxe** to collect **one cyan concrete**. A Wooden Pickaxe is sufficient under the bundled tool tags; a hand or shovel does not satisfy Concrete's collection requirement. See [mining and drops](../blocks/Concrete.md#mining-and-drops) for the active tool rules. [Correct-tool requirement][block] · [Pickaxe tag][pickaxe] · [Block loot][loot]

The loot has no Silk Touch requirement or Fortune count bonus, and includes an explosion-survival condition. It returns concrete, not powder. [Block loot][loot]

Once hardened, this is an ordinary full building block and does not need continuing support underneath. For the falling behavior of its powder form, use the [family guide](../blocks/Concrete.md#falling-and-support).

Related: [Cyan Concrete Powder](CyanConcretePowder.md) · [All concrete colors](../blocks/Concrete.md#colors-and-item-ids) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. These are the checked crafting, hardening, and collection routes. No gameplay test was run. Data packs can change recipes, tags, and loot.

[block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L4590-L4647
[item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L839-L854
[powder-block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L4648-L4727
[hardening]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/ConcretePowderBlock.java#L38-L94
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/cyan_concrete.json
