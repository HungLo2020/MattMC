# Gilded Blackstone

**Gilded Blackstone** (`minecraft:gilded_blackstone`) is a placeable decorative block with special mining drops. It is separate from ordinary Blackstone. [Registration][items]

## Obtaining and use

The [Gilded Blackstone section](../blocks/BlackstoneAndBasalt.md#gilded-blackstone) traces the checked Bastion route and its exact mining table. Use an **unbroken Silk Touch pickaxe** to preserve the block reliably. Without Silk Touch, mining can give Gold Nuggets instead; Fortune III makes that nugget outcome certain. Fortune does not increase the nugget branch's 2–5 count. [Loot][loot-gilded-blackstone] · [Chance lookup][fortune-table] · [Mining tag][pickaxe]

No bundled crafting, smelting or stonecutting recipe for this block was found. Place the recovered block for decoration; its [placed guide](../blocks/BlackstoneAndBasalt.md#placement-and-properties) gives the properties. Mining it can anger nearby Piglins even with Silk Touch. [Guarded tag][guarded] · [Break callback][break-anger]

Related: [Blackstone](Blackstone.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `1879e5f54378351fd773b9a2c10839eb9259c504`; registration, relevant acquisition paths, recipes and loot checked. No in-game acquisition, crafting or placement test was run.

[items]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/item/Items.java
[loot-gilded-blackstone]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/gilded_blackstone.json
[fortune-table]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/storage/loot/predicates/BonusLevelTableCondition.java
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[guarded]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/guarded_by_piglins.json
[break-anger]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/Block.java#L480-L489
