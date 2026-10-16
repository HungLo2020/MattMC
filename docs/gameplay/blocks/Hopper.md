# Hopper

A Hopper (`minecraft:hopper`) is a five-slot container that pushes items toward its output and pulls items from above. Redstone power disables its own automatic transfer attempt. It is useful for moving resources between storage and workstations, but its output direction and the destination's slot rules matter.

## Crafting and collecting

Craft one from **five Iron Ingots and one Chest**:

| Left | Center | Right |
| --- | --- | --- |
| Iron Ingot | Empty | Iron Ingot |
| Iron Ingot | Chest | Iron Ingot |
| Empty | Iron Ingot | Empty |

The recipe names the ordinary Chest item; other chest-like items are not accepted merely by appearance. Use a **pickaxe** to collect a placed Hopper. The block requires the correct tool for drops, belongs to the pickaxe tag, and its loot returns the Hopper while preserving its custom name.

Its inventory is saved as separate block-entity contents. On ordinary removal, the container-removal path drops contents into the world; the dropped Hopper item is not a packed portable inventory. Clear or secure valuable items before dismantling a line.

## Point the output correctly

The outlet can point **down or horizontally**, never upward. Placement uses the face clicked: a horizontal face points the outlet back toward the clicked block, while either vertical face produces a downward outlet. Check the outlet before filling the system.

The Hopper pulls from the container directly **above it**, regardless of the output direction. Its outlet pushes into a container at the adjacent output position. Pointing at open air does not make it spit items like a [Dropper](DispenserAndDropper.md#dropper-transfer-and-ejection).

## Transfer behavior and rate

An enabled Hopper that is not on cooldown:

1. Tries to push one item from its inventory into the attached container
2. If its own inventory has room, tries to pull one item from the source container above, or collect a nearby dropped-item entity when no source container exists
3. Starts an **8-game-tick cooldown** after a successful transfer attempt

At normal tick speed, eight ticks are 0.4 seconds. A sustained one-item container-transfer direction therefore has a nominal rate of **2.5 items per second** under suitable conditions. This is not a universal item-entity pickup limit: pickup can merge several items from a dropped stack, and receiving Hopper timing is adjusted within chains.

Push and pull may both succeed in one cycle. A full destination, incompatible stack components, blocked slot permissions, or lack of input can stop progress. Items of the same name do not necessarily merge if their components differ.

## Picking up dropped items

Without a source container above, the Hopper searches its intake region for dropped items. A full-collision block above normally blocks that search, except for blocks in the `does_not_block_hoppers` tag. The bundled exception tag contains the beehives tag.

Pickup is not a promise that every nearby item, or an item beyond the intake region, will enter. If a container exists above, the normal source-container path takes precedence over searching for loose items. A complete dropped stack may be collected when there is room; a partly accepted stack can remain in the world.

### Collecting through a Mud floor

**Ordinary [Mud](MudAndMudBricks.md#mud) can cover a Hopper while letting it collect loose items resting on that block.** Mud's collision surface is only **14/16 of a block high**, despite its full visual and support shapes. The Hopper tests collision, so Mud does not activate the full-block intake obstruction. Packed Mud, full Mud Bricks and ordinary full-cube Dirt do; their similar appearance or family name does not make them substitutes. [Mud collision][floor-mud] · [Current block registrations][floor-blocks] · [Intake obstruction][floor-intake] · [Collision query][floor-collision]

For a placed Hopper, the search box has the block's **one-block-wide horizontal footprint** and extends vertically from **11/16 above its bottom to the top of the block immediately above**. An item resting on that upper Mud surface can overlap this box; the search does not vacuum an entire surrounding floor or wait for the item to fall through the Mud. [Intake bounds][floor-bounds] · [World-space item query][floor-query] · [Block-relative coordinates][floor-position]

This small collection setup is **source-derived and has not been tested in game**:

1. Point a Hopper into a Chest or other suitable output container, and leave room for the intended item in both inventories
2. Keep the Hopper unpowered and put **one ordinary Mud block directly above it**, with no gap
3. Drop an item over that Mud block and let it settle within the Hopper's footprint; inspect the inventories rather than assuming a nearby item was collected

If collection stops, check power, capacity, item position and the covering block. A source container found above, including an intersecting container entity, takes priority over loose-item searching even when it yields nothing. The ordinary [transfer cooldown](#transfer-behavior-and-rate) still applies. This is a single collection spot, not a tested farm, lossless floor or throughput guarantee. [Server ticker and power][floor-ticker] · [Capacity and cooldown][floor-cycle] · [Source-container priority][floor-intake] · [Container lookup][floor-container]

Source-reviewed on **2026-10-16** at `b85b6cbfa9d5482f8b88d73743e80c871250f3bd`, including native block definitions and their active Java shape/ticker consumers. No placement, pickup, circuit or collection-efficiency test was run. [Native definitions][floor-native]

## Redstone locking

Neighbor redstone power sets `enabled` to false and prevents this Hopper's own push/pull attempt. Removing power enables it again. A [Lever](Lever.md) provides a simple control source.

**Locking one Hopper is not an access lock on its inventory.** Other enabled Hoppers can still insert into or extract from that container through their own transfer paths. Players can also open it. Design the surrounding line, rather than assuming a powered Hopper seals every direction.

The Hopper exposes an analog fullness signal for a [Comparator](RedstoneComparator.md), ranging from 0 for empty to 15 for full. Stack limits affect fullness; a non-stackable item fills its slot differently from one item in a 64-stack.

## Item filtering

A Hopper has no item-filter setting. A simple filter instead **occupies all five slots**, leaving room only in stacks that match the intended input. Empty slots admit other items. Matching requires the same item **and components**, including a custom name; merely sharing an item ID is insufficient. [Insertion][filter-insert] · [Merge condition][filter-merging] · [Exact matching][filter-match]

For an ordinary item that stacks to **64**, put **41 target items in the leftmost slot** and **one renamed, 64-stack filler item in each of the other four slots**. For example, use 41 ordinary Cobblestone, then four Cobblestone renamed together at an [Anvil](Anvil.md), split one per remaining slot. The renamed stacks do not merge with ordinary incoming Cobblestone. Choose a filler name absent from the supply, and keep those exact filler stacks out of the input: matching filler is accepted too. [Menu slot order][filter-menu] · [Anvil name component][filter-name] · [Matching][filter-match] · [Stack limits][filter-limits]

### Reading the reserve

Read the filter directly with a Comparator's **rear toward the Hopper**, using compare mode with no side input. For these five 64-stack slots, its nonempty reading is **1 + floor(14 × total item count / 320)**. The four one-item fillers are part of that total. [Hopper analog output][filter-output] · [Container fullness][filter-fullness] · [Rounding][filter-rounding] · [Comparator input](RedstoneComparator.md#reading-containers)

| Target stack | Other four slots | Total items | Comparator reading |
| ---: | --- | ---: | ---: |
| 41 | One filler each | 45 | **2** |
| 42 | One filler each | 46 | **3** |
| 64 | One filler each | 68 | **3** |

This gives a **2-to-3 threshold** when one matching item arrives. The target slot has room for **23 more items** above its 41-item reserve. Even when that slot reaches 64, the reading stays 3, provided each filler stays at one. This is a capacity calculation, not a guarantee that neighboring circuits are safe from overflow. The 41-item reserve does not apply to 16-stack or unstackable items, or to custom stack limits. [Fullness calculation][filter-fullness] · [Per-item capacity][filter-limits]

### Intake and controlled extraction

Point the filter's outlet horizontally toward **an ordinary solid block with no inventory**, and leave the filter unpowered. It can then pull from a container directly above without pushing its reserve into an output container. A lower extraction Hopper can pull from it independently of that outlet direction. [Push destination][filter-destination] · [Pull direction][filter-pull] · [Power gate][filter-cycle]

An automatic design must keep that **lower extraction Hopper locked at reading 2**, release it when the filter reaches 3, and lock it again before the reserve is drained. Keep the filter itself unpowered so it can accept input. The [Comparator modes](RedstoneComparator.md#compare-and-subtract-modes) distinguish strengths; a Lamp connected directly to the readout lights at both 2 and 3 and cannot identify this threshold by itself. These are design requirements, not a complete gameplay-tested sorter circuit. [Hopper locking][filter-power] · [Independent extraction][filter-pull]

The target belongs in the leftmost slot because extraction checks slots **left to right, stopping at the first successful transfer**. It can skip a target stack that the receiving Hopper cannot accept, then take a later filler. Keep the lower Hopper available for the intended target, without filler or unrelated stacks. Leaving extraction on can drain the reserve and eventually the fillers; a newly empty slot then admits unrelated input. [Slot scan and failed transfers][filter-pull] · [Displayed order][filter-menu] · [Insertion rules][filter-insert]

### A small filter check

This source-derived check has **not been tested in game**:

1. Place one Hopper with its outlet against a plain solid block, and a Chest directly above it. Leave both unpowered and put no extracting Hopper below
2. Load the Hopper with the 41/1/1/1/1 arrangement above, then attach a directly adjacent Comparator with no side input
3. Put one ordinary Dirt and one ordinary Cobblestone into the upper Chest. The matching Cobblestone should enter the target slot, taking it to 42; the Dirt should stay in the Chest even if its slot is checked first
4. Stop adding input before changing the reserve. Check the stored stacks and the Comparator's 2-to-3 change before adding an extraction circuit

With more matching input and no extraction, the target fills to 64 and further matching items stay in the Chest. This finite-buffer example does not make dropped-item overflow safe: provide collection for items that cannot enter. Check filler counts, component differences, unwanted power, and output capacity when troubleshooting. [Source-container scanning][filter-pull] · [Destination capacity][filter-insert] · [Loose-stack remainder][filter-loose]

## Furnace connections

The [Furnace](Furnace.md#hopper-automation) exposes its input from above, fuel from the sides, and output from below. Point the feeding Hoppers at the appropriate faces. Container insertion and extraction still use that device's allowed slots and items; a Hopper does not bypass recipe or fuel validation.

## Related pages

- [Shulker Box](ShulkerBox.md#nesting-and-automation): portable storage with sided transfer and nesting restrictions
- [Hopper item](../items/Hopper.md)
- [Chest](Chest.md)
- [Furnace](Furnace.md)
- [Redstone](../redstone/Redstone.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game transfer, pickup, redstone timing, comparator, or furnace-automation test was run. Container rules, tags, and data packs can change results.

Filtering and reserve thresholds were additionally source-reviewed on **2026-10-13** at `0d23141bdaf918ce7314fdf3cc2f846df35d04a3`. The current [native Hopper definition][filter-native] and [state defaults][filter-states], Java ticker/power/transfer paths, menu order, naming, stack limits, Comparator formula, and bundled recipe/loot were checked. The threshold values are arithmetic derived from those sources; no filter circuit, timing, overflow, or throughput gameplay test was run. Other sections retain their earlier verification scope.

- [Recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/hopper.json)
- [Placement, redstone, and menu](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/HopperBlock.java)
- [Transfer order, cooldown, item pickup, and saved inventory](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java)
- [Correct-tool requirement](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/Blocks.java)
- [Pickaxe tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json)
- [Hopper block loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/hopper.json)
- [Container removal drops](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java)
- [Above-block pickup exceptions](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/does_not_block_hoppers.json)
- [Fullness signal calculation](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java)
- [Furnace sided slots](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java)

[filter-insert]: https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L280-L342
[filter-merging]: https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L396-L398
[filter-match]: https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/java/net/minecraft/world/item/ItemStack.java#L666-L671
[filter-menu]: https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/java/net/minecraft/world/inventory/HopperMenu.java#L17-L25
[filter-name]: https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L226-L235
[filter-limits]: https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/java/net/minecraft/world/Container.java#L31-L36
[filter-output]: https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/java/net/minecraft/world/level/block/HopperBlock.java#L136-L144
[filter-fullness]: https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java#L752-L766
[filter-rounding]: https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/java/net/minecraft/util/Mth.java#L529-L532
[filter-destination]: https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L143-L170
[filter-pull]: https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L218-L262
[filter-cycle]: https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L97-L130
[filter-power]: https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/java/net/minecraft/world/level/block/HopperBlock.java#L119-L128
[filter-loose]: https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L265-L277
[filter-native]: https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/rust/content/block/definitions/catalog.rs#L509
[filter-states]: https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/rust/content/block/definitions/templates.rs#L91

[floor-mud]: https://github.com/HungLo2020/MattMC/blob/b85b6cbfa9d5482f8b88d73743e80c871250f3bd/src/main/java/net/minecraft/world/level/block/MudBlock.java#L13-L39
[floor-blocks]: https://github.com/HungLo2020/MattMC/blob/b85b6cbfa9d5482f8b88d73743e80c871250f3bd/src/main/java/net/minecraft/world/level/block/Blocks.java
[floor-intake]: https://github.com/HungLo2020/MattMC/blob/b85b6cbfa9d5482f8b88d73743e80c871250f3bd/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L215-L243
[floor-collision]: https://github.com/HungLo2020/MattMC/blob/b85b6cbfa9d5482f8b88d73743e80c871250f3bd/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1023-L1044
[floor-bounds]: https://github.com/HungLo2020/MattMC/blob/b85b6cbfa9d5482f8b88d73743e80c871250f3bd/src/main/java/net/minecraft/world/level/block/entity/Hopper.java#L7-L12
[floor-query]: https://github.com/HungLo2020/MattMC/blob/b85b6cbfa9d5482f8b88d73743e80c871250f3bd/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L349-L357
[floor-position]: https://github.com/HungLo2020/MattMC/blob/b85b6cbfa9d5482f8b88d73743e80c871250f3bd/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L400-L417
[floor-ticker]: https://github.com/HungLo2020/MattMC/blob/b85b6cbfa9d5482f8b88d73743e80c871250f3bd/src/main/java/net/minecraft/world/level/block/HopperBlock.java#L96-L128
[floor-cycle]: https://github.com/HungLo2020/MattMC/blob/b85b6cbfa9d5482f8b88d73743e80c871250f3bd/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L97-L140
[floor-container]: https://github.com/HungLo2020/MattMC/blob/b85b6cbfa9d5482f8b88d73743e80c871250f3bd/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L365-L394
[floor-native]: https://github.com/HungLo2020/MattMC/blob/b85b6cbfa9d5482f8b88d73743e80c871250f3bd/src/main/rust/content/block/definitions/catalog.rs#L1169
