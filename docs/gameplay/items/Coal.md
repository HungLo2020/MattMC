# Coal

Coal is a fuel and crafting ingredient registered as `minecraft:coal`. It can power a furnace, make torches, or be packed into a Coal Block.

## Mining

Coal Ore's loot table selects the ore block when mined with Silk Touch; otherwise it selects Coal, applies the Fortune ore-drop bonus, and accounts for explosions. The ore registration requires a correct tool, and it belongs to the pickaxe mining tag.

This page verifies the drop and tool rules, not an exhaustive ore-generation height chart or every chest/trade source. Do not assume a tool-free break recovers coal just because the loot table names it.

## Fuel and recipes

- One Coal supplies **1,600 default furnace burn ticks**, enough for eight uninterrupted 200-tick recipes.
- One Coal above one Stick crafts **four Torches**.
- Nine Coal filling a 3 × 3 grid craft **one Coal Block**.
- One Coal Block in a shapeless recipe returns **nine Coal**.

A Coal Block burns for **16,000 ticks** under the default fuel values, equivalent to 80 uninterrupted 200-tick recipes. That is more total burn time than nine loose Coal (72 such recipes), but the longer continuous burn can be wasted if the furnace lacks input or output room.

[Charcoal](Charcoal.md) shares Coal's ordinary fuel and torch roles, but is not an ingredient in the checked Coal Block recipe.

## Related pages

- [Charcoal](Charcoal.md)
- [Torch](Torch.md)
- [Furnace](../blocks/Furnace.md)
- [Items](Items.md)

## Sources and verification

Reviewed at source `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game placement, sleeping, respawn, or mining test was performed.

- [Coal ore drops](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/blocks/coal_ore.json)
- [Ore registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/Blocks.java#L368-L377)
- [Pickaxe mining tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json)
- [Fuel values](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java)
- [Torch recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/torch.json)
- [Coal Block crafting](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/coal_block.json)
- [Coal Block unpacking](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/coal.json)
