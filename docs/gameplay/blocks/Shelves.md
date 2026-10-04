# Wooden Shelves

A Shelf displays and stores **three item stacks**. Use its front to exchange an individual stack while unpowered, or power it to exchange a fixed part of your hotbar. Up to three compatible powered Shelves can connect. This guide covers the twelve wooden Shelf variants; [Bookshelves](Bookshelves.md) owns the separate Bookshelf and Chiseled Bookshelf blocks. [Shelf interaction][shelf] · [Storage][entity]

## Variants and crafting

Every ID below uses the `minecraft:` namespace. All twelve use `ShelfBlock`, have a matching item, and are accepted by the registered Shelf block-entity type. They share their storage and redstone behavior. There is no registered Pewen Shelf in this set. [Block registrations][registration] · [Item registrations][items] · [Valid block-entity forms][types] · [Shelf tag][shelf-tag]

Use **six of the exact stripped material**, three across the top row and three across the bottom row, leaving the entire middle row empty. Each 3 × 3 recipe makes **six matching Shelves**. These recipes require a Crafting Table; they do not use planks, ordinary unstripped logs, or a general wood tag. Stripped Wood does not substitute for a named Stripped Log, nor stripped hyphae for a named stem. [All twelve recipes below](#variants-and-crafting)

| Block ID | Six recipe inputs | Recipe and ordinary block loot |
| --- | --- | --- |
| `acacia_shelf` ([item](../items/AcaciaShelf.md)) | Stripped Acacia Log | [Recipe][recipe-acacia_shelf] · [Loot][loot-acacia_shelf] |
| `bamboo_shelf` ([item](../items/BambooShelf.md)) | Stripped Block of Bamboo | [Recipe][recipe-bamboo_shelf] · [Loot][loot-bamboo_shelf] |
| `birch_shelf` ([item](../items/BirchShelf.md)) | Stripped Birch Log | [Recipe][recipe-birch_shelf] · [Loot][loot-birch_shelf] |
| `cherry_shelf` ([item](../items/CherryShelf.md)) | Stripped Cherry Log | [Recipe][recipe-cherry_shelf] · [Loot][loot-cherry_shelf] |
| `crimson_shelf` ([item](../items/CrimsonShelf.md)) | Stripped Crimson Stem | [Recipe][recipe-crimson_shelf] · [Loot][loot-crimson_shelf] |
| `dark_oak_shelf` ([item](../items/DarkOakShelf.md)) | Stripped Dark Oak Log | [Recipe][recipe-dark_oak_shelf] · [Loot][loot-dark_oak_shelf] |
| `jungle_shelf` ([item](../items/JungleShelf.md)) | Stripped Jungle Log | [Recipe][recipe-jungle_shelf] · [Loot][loot-jungle_shelf] |
| `mangrove_shelf` ([item](../items/MangroveShelf.md)) | Stripped Mangrove Log | [Recipe][recipe-mangrove_shelf] · [Loot][loot-mangrove_shelf] |
| `oak_shelf` ([item](../items/OakShelf.md)) | Stripped Oak Log | [Recipe][recipe-oak_shelf] · [Loot][loot-oak_shelf] |
| `pale_oak_shelf` ([item](../items/PaleOakShelf.md)) | Stripped Pale Oak Log | [Recipe][recipe-pale_oak_shelf] · [Loot][loot-pale_oak_shelf] |
| `spruce_shelf` ([item](../items/SpruceShelf.md)) | Stripped Spruce Log | [Recipe][recipe-spruce_shelf] · [Loot][loot-spruce_shelf] |
| `warped_shelf` ([item](../items/WarpedShelf.md)) | Stripped Warped Stem | [Recipe][recipe-warped_shelf] · [Loot][loot-warped_shelf] |

Use [Tree Logs and Roots](TreeLogsAndRoots.md#stripping) for stripping timber and [Bamboo](Bamboo.md) for bamboo material preparation. The recipes and [Creative entries][creative] are verified acquisition routes; this guide does not infer naturally generated Shelves from their wood names.

## Placement, support, and water

A Shelf faces toward the player when placed, using the opposite of the player's horizontal facing. The accessible display face is its front. Its collision shape consists of a back panel and the top/bottom ledges, rather than a full cube or an empty space. It has **no attachment-survival requirement**: it can remain after a decorative backing wall is removed. [Placement and shape][shelf] · [Inherited survival/collision][defaults]

The block has `facing`, `powered`, `side_chain_part`, and `waterlogged` states. It records existing redstone power on placement. Putting it in **source Water** sets waterlogging; the placement callback compares the fluid type to `Fluids.WATER`, not every flowing-water state. A Water Bucket can fill a placed Shelf, and an empty Bucket can collect the stored source while leaving the Shelf. Waterlogging does not clear its inventory. [Block state and fluid callbacks][shelf] · [Bucket interaction][waterlogged] · [Inventory survives same-block state changes][chunk]

## Three slots and normal use

The Shelf has **one row of three slots**, read left to right while looking at its front. Each slot stores a stack with its normal item stack limit: for example, three ordinary 64-count stacks fit, while unstackable tools occupy one slot each. The underlying container also caps a slot at 99, so a custom higher item maximum would not bypass that cap. It does not restrict storage to books or to a particular item family. [Slot layout][shelf] [slots] · [Inventory and stack limits][entity] [list][] [container][] · [Default item stack size][components]

Use the **front face**, aiming at its left, middle, or right third, with your main hand:

| While the Shelf is unpowered | Result |
| --- | --- |
| Held stack, empty Shelf slot | Moves the entire held stack into that slot |
| Empty main hand, occupied slot | Moves the entire stored stack into the selected hotbar slot |
| Held stack, occupied slot | Swaps the two complete stacks |
| Empty main hand, empty slot | No inventory change |

This is a stack exchange, not one-item insertion or automatic merging with an existing stack. Only the selected display slot changes. Clicking a different face does not select a slot, and the offhand does not run the Shelf exchange. There is **no container menu** to open: the displayed items are rendered from the three stored stacks. [Interaction and single-slot swap][shelf] · [Front-only hit selection][slots] · [No menu provider][menu] [entity] · [Renderer registration][renderer-registration] [renderer]

Ordinary use with a held item runs the Shelf interaction first. Secondary use while either hand contains an item bypasses that block interaction, which is useful when you want to place another block near the Shelf instead of storing it. In Creative, putting a held stack into an **empty unpowered slot** preserves a copy in the selected hotbar slot; exchanging with an occupied slot follows the normal swap. [Input dispatch][use] · [Creative single-slot branch][shelf]

## Redstone and hotbar exchanges

A Shelf detects neighboring redstone power on placement and neighbor updates. **Power changes the use action; it does not move items by itself.** While powered, using any valid front slot exchanges the associated Shelf inventory with the fixed hotbar positions below. Your currently selected hotbar position does not choose the range. Empty Shelf slots participate too, so an exchange can move hotbar stacks into previously empty storage. Use the front again to swap them back. [Power detection and powered interaction][shelf] · [Neighbor signal lookup][signals]

| Connected powered Shelves | Shelf positions, seen from the front | Hotbar positions exchanged |
| --- | --- | --- |
| One | Its three slots | **7–9** |
| Two | Left Shelf, then right Shelf | **4–6**, then **7–9** |
| Three | Left, middle, then right Shelf | **1–3**, **4–6**, then **7–9** |

Within each Shelf, left/middle/right correspond to the three hotbar positions in order. These are the normal player-visible 1–9 hotbar positions. The exchange moves whole stacks and does not change which hotbar position is selected. The powered path has no Creative duplication branch. [Hotbar index calculation and swap][shelf] · [Connected order][chains]

### Connecting Shelves

Place up to **three Shelves side by side at the same height**, with the **same facing**, and give each Shelf a detectable power signal. Connection uses the wooden-shelves tag, so different registered woods can share a group. Vertical stacking and differently facing Shelves do not form this horizontal group. Merely placing an unpowered Shelf beside a powered one does not meet the connection predicate. [Connectable state and three-block limit][shelf] · [Neighbor positions and facing check][chains] · [All twelve tag members][shelf-tag]

Connection state is updated when Shelves become powered, are placed, or lose power. The active chunk update calls the Shelf placement callback even for the relevant same-block state changes. Removing power disconnects that Shelf while leaving its items in storage. Longer rows can form separate groups; the joining algorithm will not merge groups beyond three and considers the left group before the right, so avoid assuming a long row always partitions the same way. A gap between intended groups keeps the layout straightforward. [Power callbacks][shelf] · [Joining and disconnecting][chains] [chain-states] · [Active state-update dispatch][chunk]

## Comparator output

Read a Shelf with a [Comparator](RedstoneComparator.md) **behind its back**, with the Comparator input toward the Shelf and its output away. The Shelf returns its analog value only toward the side opposite its front facing; a Comparator on the display side or a side face reads zero from this callback. For example, a north-facing Shelf has its readable back to the south. [Direction restriction and encoding][shelf] · [Comparator input direction][comparator]

The output is the sum of the weights for the **occupied display slots**, not fullness or the last slot touched:

| Occupied slot, seen from the front | Adds to signal |
| --- | ---: |
| Left | 1 |
| Middle | 2 |
| Right | 4 |

An empty Shelf gives **0**; left plus right gives **5**; all three occupied gives **7**. One item and a full stack in a given slot contribute the same amount. Each Shelf reports only its own three slots, even in a connected group, and the formula does not depend on its powered state. Inventory changes mark the block entity changed and notify comparator output neighbors. [Analog callback][shelf] · [Change notification][entity] [base-entity]

## Hoppers and other container transfers

A [Hopper](Hopper.md) pointing into a Shelf can insert items, and one beneath it can extract. The generic Hopper path recognizes the Shelf block entity as a container. Shelves do not define sided slot restrictions: all three slots participate, in slot order, subject to room and normal item-merging checks. Unlike manual use, hopper insertion can merge compatible stacks. [Container discovery and transfers][hopper] · [Accepted items and capacity][list] [container]

Shelf power does **not** lock its inventory against these transfers; there is no powered-state check in its container methods. A nearby circuit may separately power and lock the Hopper itself, so use the [Hopper redstone rules](Hopper.md#redstone-locking) when combining automation with hotbar controls. Hopper changes also update the Shelf's comparator value and displayed storage. [Container methods][entity] [list] · [Hopper updates][hopper]

## Mining, saving, and moving contents

All twelve Shelves have **hardness 2 and blast resistance 3**. An axe is the efficient mining tool, but no registration requires a particular tool to receive ordinary loot. Hand mining drops **one matching empty Shelf**, with no Silk Touch requirement or Fortune bonus. Each block loot table has an explosion-survival condition. The block item's ordinary drops follow `doTileDrops`. [Registration][registration] · [Axe tag][axe] [shelf-tag] · [Harvest gate][gate] [harvest] · [Drop rule][drop-rule] · [Per-variant loot](#variants-and-crafting)

**Breaking a Shelf spills its stored items separately, including with Silk Touch.** The active block-entity removal path calls the generic container spill routine, and the Shelf's block loot does not copy its contents into the dropped item. Collect the stored stacks before moving a Shelf when loose items would be hard to retrieve. Power and connection changes keep the same block entity; actual block removal is the different path that spills it. [Removal dispatch][chunk] · [Container removal side effects][spill] [containers] · [Shelf loot](#variants-and-crafting)

While placed, the Shelf saves and reloads its three item stacks, and synchronizes them for display. Shelf items do support a `container` component, with empty contents by default; placing an item that already carries that component restores it. Creative **Pick Block with block data** can collect such component data. That data support does not change ordinary Survival mining into portable container recovery. [Save/load and components][entity] · [Default item components][items] · [Placement restoration][placement] · [Creative data-copy path][pick] · [Pick Block data modifier][pick-control]

### Fuel and fire distinctions

The ten Overworld/Bamboo Shelf items are default furnace fuel for **300 burn ticks each**. Crimson and Warped Shelves are excluded by the non-flammable-wood item tag. Their ordinary FireBlock spread/burn entries are also absent, while the other ten have entries. However, **all twelve block registrations carry the lava-ignition flag**, which the lava ignition code checks for nearby fire creation. The item fuel exclusion is not a blanket statement that every fire/lava interaction ignores the Nether Shelf variants. [Fuel construction and exclusion][fuel] [shelf-item-tag][] [nonflammable][] · [Active server fuel initialization][fuel-init] · [Fire entries][fire] · [Block flags][registration] · [Lava ignition path][lava]

## Small storage setup

A source-based example, **not gameplay-tested**: place one Oak Shelf, leave it unpowered, and use its three front slots to store a tool, building blocks, and food. Put a solid block immediately behind it with a [Lever](Lever.md) on an exposed face. Turn the Lever on to power the Shelf, then use its front to exchange those stacks with hotbar positions **7–9**. Use the Shelf again to reverse the exchange, or turn the Lever off to return to individual-slot exchanges. Powering alone leaves all stacks where they are. [Shelf behavior][shelf] · [Lever signals][lever] · [Neighbor-power lookup][signals]

Related: [Bookshelves](Bookshelves.md) · [Chest](Chest.md) · [Hopper](Hopper.md) · [Comparator](RedstoneComparator.md) · [Tree Logs and Roots](TreeLogsAndRoots.md) · [Bamboo](Bamboo.md)

## Sources and verification

Reviewed against pinned MattMC registrations, all twelve recipes and block loot tables, active interaction/connection/container paths, and tags on **2026-10-02**. The example and hotbar/comparator behavior are source-derived, not gameplay-tested. No inventory, redstone timing, connection-order, or mining test was run.

[shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/ShelfBlock.java
[entity]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/entity/ShelfBlockEntity.java
[registration]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/Blocks.java#L1125-L1184
[items]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/item/Items.java#L418-L453
[types]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/entity/BlockEntityType.java#L234-L249
[shelf-tag]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/tags/block/wooden_shelves.json
[recipe-acacia_shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/recipe/crafting/acacia_shelf.json
[loot-acacia_shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/loot_table/blocks/acacia_shelf.json
[recipe-bamboo_shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/recipe/crafting/bamboo_shelf.json
[loot-bamboo_shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/loot_table/blocks/bamboo_shelf.json
[recipe-birch_shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/recipe/crafting/birch_shelf.json
[loot-birch_shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/loot_table/blocks/birch_shelf.json
[recipe-cherry_shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/recipe/crafting/cherry_shelf.json
[loot-cherry_shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/loot_table/blocks/cherry_shelf.json
[recipe-crimson_shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/recipe/crafting/crimson_shelf.json
[loot-crimson_shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/loot_table/blocks/crimson_shelf.json
[recipe-dark_oak_shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/recipe/crafting/dark_oak_shelf.json
[loot-dark_oak_shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/loot_table/blocks/dark_oak_shelf.json
[recipe-jungle_shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/recipe/crafting/jungle_shelf.json
[loot-jungle_shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/loot_table/blocks/jungle_shelf.json
[recipe-mangrove_shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/recipe/crafting/mangrove_shelf.json
[loot-mangrove_shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/loot_table/blocks/mangrove_shelf.json
[recipe-oak_shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/recipe/crafting/oak_shelf.json
[loot-oak_shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/loot_table/blocks/oak_shelf.json
[recipe-pale_oak_shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/recipe/crafting/pale_oak_shelf.json
[loot-pale_oak_shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/loot_table/blocks/pale_oak_shelf.json
[recipe-spruce_shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/recipe/crafting/spruce_shelf.json
[loot-spruce_shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/loot_table/blocks/spruce_shelf.json
[recipe-warped_shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/recipe/crafting/warped_shelf.json
[loot-warped_shelf]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/loot_table/blocks/warped_shelf.json
[creative]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1132-L1143
[defaults]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L338
[waterlogged]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java
[chunk]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java#L311-L357
[slots]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/SelectableSlotContainer.java
[list]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/entity/ListBackedContainer.java
[container]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/Container.java
[components]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/core/component/DataComponents.java#L381-L390
[menu]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/BaseEntityBlock.java
[renderer-registration]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/client/renderer/blockentity/BlockEntityRenderers.java#L63
[renderer]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/client/renderer/blockentity/ShelfRenderer.java
[use]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L340-L400
[signals]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/SignalGetter.java
[chains]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/SideChainPartBlock.java
[chain-states]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/state/properties/SideChainPart.java
[comparator]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/ComparatorBlock.java#L97-L118
[base-entity]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java#L208-L221
[hopper]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java
[axe]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[gate]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[harvest]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L297
[drop-rule]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/Block.java#L411-L416
[spill]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java#L250-L254
[containers]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/Containers.java
[placement]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/item/BlockItem.java
[pick]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L667-L698
[pick-control]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/client/Minecraft.java#L2438-L2447
[fuel]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java
[shelf-item-tag]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/tags/item/wooden_shelves.json
[nonflammable]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/tags/item/non_flammable_wood.json
[fuel-init]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/server/MinecraftServer.java#L340
[fire]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/FireBlock.java
[lava]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/material/LavaFluid.java#L78-L137
[lever]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/LeverBlock.java
