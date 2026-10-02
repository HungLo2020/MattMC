# Crafter

The **Crafter** item places `minecraft:crafter`, a **redstone-activated 3×3 crafting block**. Load a recipe, disable any slots that must remain empty, and trigger it to send crafted items out of its front face. [Block-item registration][items] · [Placed behavior][crafter]

## Obtaining and use

Craft one from **five Iron Ingots, one Crafting Table, one Dropper, and two Redstone Dust** using the [exact layout on the block guide](../blocks/Crafter.md#crafting-and-collecting). It also has a Redstone Creative entry. [Recipe][recipe] · [Creative placement][creative]

The checked block registration allows ordinary collection even by hand; its pickaxe/tool tags do not introduce a required-tool drop gate by themselves. Stored ingredients drop separately on removal, and the ordinary dropped item does not preserve the loaded crafting setup. [Registration and mining gate][blocks] [player] · [Block loot][loot] · [Container removal][entity-base]

Place its output face toward the receiving container. The on-screen result is only a preview, and a full destination causes crafted output to be ejected rather than stopping the craft. The [placed Crafter guide](../blocks/Crafter.md) covers slot controls, ingredient distribution, four-game-tick triggering, recipe remainders, comparator signals, and a simple Plank-making example. [Crafting and overflow][crafter] · [Preview slot][preview]

## Related pages

- [Placed Crafter](../blocks/Crafter.md)
- [Dropper](Dropper.md), [Hopper](../blocks/Hopper.md), [Items](Items.md)

## Sources and verification

Source-reviewed at `fb7d6979fb8d9773cfe05f084c6085f35feb885c` on 2026-10-02 against active `src/main` code and bundled data. No in-game crafting, transfer, redstone, comparator, or mining test was run. Examples are source-based; data packs and later builds can change recipes and behavior.

[items]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/item/Items.java
[crafter]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/CrafterBlock.java
[recipe]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/crafter.json
[creative]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[blocks]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/Blocks.java
[player]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/entity/player/Player.java
[loot]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/crafter.json
[entity-base]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java
[preview]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/inventory/NonInteractiveResultSlot.java
