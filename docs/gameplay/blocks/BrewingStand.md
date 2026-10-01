# Brewing Stand

A Brewing Stand turns bottled potions into other potions using an ingredient and brewing fuel. It can process **up to three bottles in one operation**. Its block and item ID is `minecraft:brewing_stand`.

## Crafting and collecting

At a [Crafting Table](CraftingTable.md), place **one Blaze Rod above the center of a row of three stone-crafting materials**. The recipe produces one stand. The bundled material tag accepts **Cobblestone, Blackstone, and Cobbled Deepslate**, including mixtures of those materials; ordinary Stone is not accepted.

The stand's block loot returns one stand and preserves its custom name, subject to explosion survival. Pickaxes are its tagged mining tools, but **the checked MattMC registration does not require a correct tool for drops**. Do not assume a pickaxe is mandatory to collect it in this source snapshot.

## Slots and use

Place the stand and interact with it to open its menu:

- Three lower slots each hold one potion or bottle
- The upper ingredient slot holds the item used for the next transformation
- The separate fuel slot accepts the brewing-fuel tag, which contains only **Blaze Powder** in the bundled data

Fill empty Glass Bottles with water before brewing. Although the stand accepts empty bottles in its lower slots, those bottles do not have a brewing transformation. See the [brewing guide](../brewing/Brewing.md) for working potion chains.

## Fuel, batches, and time

**One Blaze Powder loads 20 brewing uses.** The stand consumes a use when an operation starts, then counts down **400 ticks**, or **20 seconds at 20 ticks per second**. A completed operation consumes one ingredient and applies it to every compatible bottle in the three slots. Incompatible bottles remain unchanged.

A full three-bottle batch costs the same one ingredient and one fuel use as a single compatible bottle. Each additional step in a potion chain is another operation. Blaze Powder placed in the ingredient slot for Strength is separate from the powder needed as fuel.

The stand can consume a powder item to refill its internal fuel reserve even when no recipe is ready. Stored uses do not run down while idle. Removing the required ingredient or leaving no compatible bottle cancels an active brew; its already-spent fuel use is not refunded. Leave the batch in place until it finishes.

## Troubleshooting

If brewing does not start, check fuel and the exact **current potion + ingredient** combination. An item fitting the ingredient slot does not mean it works on every potion. An active server tick is required for progress; the listed seconds assume normal tick speed.

## Related pages

- [Brewing guide](../brewing/Brewing.md)
- [Brewing Stand item](../items/BrewingStand.md)
- [Blaze Powder](../items/BlazePowder.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. This is not an in-game test. Data packs can change crafting recipes, fuel tags, and loot; later builds may change the behavior described here. Naturally generated stands were not reviewed for this page.

- [Stand recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/brewing_stand.json)
- [Stone-crafting materials](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/stone_crafting_materials.json)
- [Block registration and tool requirement](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/Blocks.java#L2525-L2527)
- [Player tool-for-drops check](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657)
- [Pickaxe tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json)
- [Block loot](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/blocks/brewing_stand.json)
- [Interaction and server ticking](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/BrewingStandBlock.java#L49-L73)
- [Menu slots](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/inventory/BrewingStandMenu.java)
- [Brewing-fuel tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/brewing_fuel.json)
- [Fuel loading, timing, cancellation, and batch processing](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/entity/BrewingStandBlockEntity.java#L96-L190)
- [Matching and unchanged incompatible bottles](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L73-L125)
