# Chest

A Chest is a persistent storage block with **27 inventory slots**. Two compatible adjacent chests can join into a **54-slot double chest**. Its block and item ID is `minecraft:chest`.

## Crafting and collecting

Arrange eight planks around an empty center in a [Crafting Table](CraftingTable.md) to craft one chest. Each ingredient must match the bundled planks tag. [Pewen Planks](../items/PewenPlanks.md) currently lack that tag membership, so they are not an established substitute in this recipe.

The chest block does not require a special tool for its own drop, and an axe is its tagged mining tool. Its block loot table preserves a custom name. Empty valuable contents before relocating storage; this page does not describe a chest item as portable filled storage.

## Placement and access

Ordinary placement can connect a new chest to a compatible single chest beside it when their facing and connection directions agree. The combined menu has six rows. Secondary-use placement affects automatic connection, so check the resulting shape before filling a large storage wall.

Normal placement faces the new chest opposite your horizontal facing and checks its left and right neighbors, not its front or back. **Ordinary Chests join only other ordinary Chests**, and a partner must still be a single chest: a third chest cannot extend an existing pair. [Placement and partner checks][chest-placement] · [Same-block rule][chest-pair]

For **secondary use**, hold a Chest item, activate your configured **Sneak** control, then use/place it. MattMC's bundled defaults are **Left Control** for Sneak and **right mouse** for Use Item/Place Block; use your current bindings and crouch-toggle setting if changed. This lets the held item place against an existing chest instead of opening its menu. [Default controls][controls] · [Sneak input][sneak-input] · [Secondary-use flag][secondary-use] · [Placement context][place-context] · [Server interaction dispatch][server-use]

- **Keep adjacent chests separate:** secondary-use a top or bottom block face to place the new chest without automatic joining
- **Join deliberately:** secondary-use the left or right side face of a compatible single chest. That face must be perpendicular to the existing chest's facing; the new half adopts that facing

These are the shared placement controls referenced by [Copper Chests](CopperChests.md#storage-and-joining). Their variant compatibility and sorting behavior remain in that guide; [Trapped Chests](TrappedChest.md#placement-and-double-chests) join their own kind. [Exact face and facing checks][chest-placement]

Leave the lid area clear. The opening check rejects a chest when the block above is a redstone conductor, or when a sitting cat occupies the space above it. For a double chest, check both halves if the container will not open.

The block can be waterlogged. It also exposes an analog comparator signal based on its container contents, useful for fullness indicators. This is different from the special trapped-chest opening signal.

**A blocked lid does not seal the inventory against Hoppers.** [Hopper](Hopper.md) container lookup bypasses the lid-obstruction check. The [Comparator](RedstoneComparator.md#reading-containers) lookup keeps that check: a blocked chest, including a double chest with either half blocked, evaluates to 0 even with stored items. See the [shared chest access explanation](TrappedChest.md#comparators-and-hoppers) for the distinction. These are source-derived access rules, not a circuit-timing test. [Menu and container lookup][chest-access] · [Obstruction and analog hook][chest-analog] · [Both-half check][chest-combine] · [Hopper bypass][chest-hopper] · [Empty lookup signal][fullness]

## Storage cautions

Each half stores its own items even while the double menu presents them together. Normal removal spills the removed half's contents as separate item entities; the other half stays in place with its own contents and becomes a single chest. The dropped Chest item carries its custom name, not its filled inventory. See [saving contents and breaking the block](TrappedChest.md#saving-contents-and-breaking-the-block) for the shared removal details, or [Shulker Boxes](ShulkerBox.md#breaking-and-carrying-contents) for portable filled storage. [Per-half saving][chest-save] · [Remaining-half state][chest-pair] · [Removal dispatch][removal] · [Container spill][spill] · [Complete Chest loot][chest-loot]

Opening a chest calls the nearby-piglin anger behavior, so a storage action can have consequences around piglins. The exact anger radius and exceptions are outside this page's verified scope.

This article covers ordinary chests. [Trapped Chests](TrappedChest.md), [Ender Chests](../items/EnderChest.md), and integrated copper storage have distinct behavior and should not be assumed interchangeable.

## Related pages

- [Inventory controls](../mechanics/InventoryControls.md#move-stacks-quickly)
- [Chest item](../items/Chest.md)
- [Copper Chests](CopperChests.md#storage-and-joining)
- [Placed Ender Chest](EnderChest.md)
- [Crafting Table](CraftingTable.md)
- [Blocks](Blocks.md)

For pending rewards and early inventory access, see [Generated loot and first access](../mechanics/LootAndDrops.md#generated-containers-first-access-can-matter).

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. This is not a gameplay test; data packs and later builds can change recipes and tags.

Placement controls, access, and removal were additionally source-reviewed at `9bafc14d2e2943dcfe8a37e9e81dc001386b88bb` on **2026-10-04**. This expands the ordinary Chest owner for shared behavior already described in the Trapped Chest and Copper Chest guides. No in-game placement, circuit, save/reload, or removal test was run.

- [Crafting recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/chest.json)
- [Planks tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/planks.json)
- [Slots](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/entity/ChestBlockEntity.java)
- [Connection, obstruction and comparator behavior](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/ChestBlock.java)
- [Registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/Blocks.java#L1244-L1247)
- [Loot](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/blocks/chest.json)
- [Axe tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/mineable/axe.json)

[fullness]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java#L748-L767
[removal]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java#L311-L323
[spill]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java#L250-L254
[chest-placement]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/block/ChestBlock.java#L201-L240
[chest-pair]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/block/ChestBlock.java#L164-L181
[controls]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Options.java#L560-L565
[sneak-input]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/player/KeyboardInput.java#L25-L35
[secondary-use]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/entity/player/Player.java#L308-L310
[place-context]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/context/UseOnContext.java#L74-L76
[server-use]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L358-L387
[chest-access]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/block/ChestBlock.java#L269-L299
[chest-analog]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/block/ChestBlock.java#L328-L360
[chest-combine]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/block/DoubleBlockCombiner.java#L24-L54
[chest-hopper]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L375-L387
[chest-save]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/block/entity/ChestBlockEntity.java#L82-L97
[chest-loot]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/loot_table/blocks/chest.json#L1-L30
