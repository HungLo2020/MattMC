# Dispenser and Dropper

A **Dispenser** (`minecraft:dispenser`) uses certain items when triggered, such as launching an Arrow or applying Bone Meal. A **Dropper** (`minecraft:dropper`) transfers one item into a container in front, or ejects it as a loose item when no destination container is found. Both hold **nine inventory slots**.

Choose a Dropper for controlled item transfer. Choose a Dispenser when you want the selected item's registered action; pointing a Dispenser at a Chest does not turn it into the Dropper's direct inventory-transfer mechanism.

## Crafting and collecting

Both recipes use seven **Cobblestone** and one **Redstone Dust** in a Crafting Table. A Dispenser additionally uses one **Bow** in the center; leave that center empty for a Dropper:

| Left | Center | Right |
| --- | --- | --- |
| Cobblestone | Cobblestone | Cobblestone |
| Cobblestone | Bow for Dispenser; empty for Dropper | Cobblestone |
| Cobblestone | Redstone Dust | Cobblestone |

Each recipe makes one block. The ingredients are specific items, not broad stone or weapon tags. Use a **pickaxe** to collect either placed block: both require the correct tool for their block-item drops.

Ordinary block loot preserves a custom name, while the separate container-removal path spills inventory contents into the world. The dropped block item is not a packed nine-slot inventory. Empty or secure the contents before dismantling it.

## Facing, loading, and activation

The output can point horizontally, up, or down. Placement points the opening opposite your nearest look direction, usually toward you for a horizontal placement. Interact to open the nine-slot inventory.

On a detected change from unpowered to powered, either device schedules **one action four game ticks later**: two conventional redstone ticks, or 0.2 seconds at 20 game ticks per second. Continuous power does not make it repeat. Remove power and apply it again for the next action.

The power check considers neighboring power at the device and at the position above it. A simple Button on the device is easier to diagnose than an indirect-power arrangement that depends on a separate neighbor update.

The scheduled action does not cancel merely because power disappears before it runs. Very fast on/off changes can share a pending scheduled tick rather than producing one action per change. Do not treat four game ticks as a tested maximum operating rate or promise of an action for every arbitrary pulse.

## How the item is selected

Each action chooses **one occupied slot at random**, with equal chances among occupied slots. Empty slots are ignored. A slot containing one Arrow has the same selection chance as a slot containing 64 Cobblestone. Putting an item type in several occupied slots increases its chance of selection.

The device does not prefer an item that can successfully act on the target. If the chosen action fails, it does not try every other slot for a better choice. Start with only the intended item type loaded.

## Dropper transfer and ejection

A Dropper checks the adjacent output position for a container. If it finds one, it tries to insert **one item** from the selected slot through the facing side of that container. Destination slot rules still apply; for example, [Furnace](Furnace.md#hopper-automation) input and fuel belong on different faces.

If the target container is full or rejects the item, the item stays in the Dropper. It does not spill that item into the world as a fallback. If no container is found, it ejects one loose item from the output instead. It does not shoot an Arrow, empty a Water Bucket, or place a block merely because that item has a special Dispenser action.

A Dropper can point up for upward item transfer. It needs a new activation for each attempt; a [Hopper](Hopper.md) follows its own enabled-state and cooldown rules instead. Hoppers can feed or drain these inventories, subject to their ordinary transfer conditions.

## Some verified Dispenser actions

| Loaded item | Dispenser action |
| --- | --- |
| Arrow, Snowball, or Egg | Launches one projectile using that item's projectile settings |
| Bone Meal | Attempts growth on the block directly in front; an unsuitable target does not guarantee consumption or growth |
| Water or Lava Bucket | Attempts the bucket's contents-placement action; success returns an empty Bucket, while a failed placement can eject the filled bucket |
| Empty Bucket | Attempts pickup from a compatible block in front, such as a collectible fluid source; otherwise falls back to item ejection |
| Ordinary item without a special action, such as Cobblestone | Ejects one loose item; it does not place that block |

Water still follows its [dimension and placement restrictions](../items/WaterBucket.md). A successful bucket action can mean evaporation rather than placed water in an ultrawarm dimension. Some other items use equipment, vehicle, tool, or special block behaviors; this table is deliberately not a complete catalog. Do not assume every integrated item has registered automation support.

## Small example: one item per button press

This layout is source-derived and has **not been tested in game**.

1. Place a Dropper horizontally and put a Chest directly against its output opening. Leave space above the Chest to open it.
2. Mount a Stone Button on the Dropper's rear or side face.
3. Load one slot with several Cobblestone and press the Button.
4. After the four-game-tick action delay, the Chest should gain one Cobblestone. Wait for the Button to release before pressing again.

For a separate Dispenser check, give a Dispenser clear space in front, attach the same type of Button, and load only Snowballs. A press should launch a Snowball instead of transferring it into storage. Check the opening direction if nothing appears where expected.

## Fullness and persistence

Both devices provide a [Comparator](RedstoneComparator.md) fullness reading from their nine-slot contents. The normal empty-to-full range is 0–15 and uses item stack limits, as described in that guide.

Inventory contents are saved with the placed block entity, and inventory changes mark the containing chunk for saving. This supports persistent placed storage; it does not make ordinary mined block items retain the contents or prove crash recovery has been tested.

## Related pages

- [Dispenser item](../items/Dispenser.md) and [Dropper item](../items/Dropper.md)
- [Pistons](Pistons.md), [Hopper](Hopper.md), and [Chest](Chest.md)
- [Redstone basics](../redstone/Redstone.md) and [Buttons](Buttons.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. Registrations, recipes, drop conditions, behavior bootstrap, current callbacks, scheduled-tick dispatch, slot selection, container transfer, and inventory save/removal paths were checked. No in-game dispensing, transfer, timing, or save/reload test was run. Item behavior, game rules, data packs, and update order can change a machine's outcome.

- [Block registration and tool requirements](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java)
- [Dispenser recipe](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/dispenser.json)
- [Dropper recipe](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/dropper.json)
- [Pickaxe tag](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json)
- [Dispenser block loot](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/dispenser.json)
- [Dropper block loot](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/dropper.json)
- [Facing, menu, trigger scheduling, dispensing, and analog output](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/DispenserBlock.java)
- [Dropper destination transfer and failure behavior](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/DropperBlock.java)
- [Nine slots, random selection, and inventory saving](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/DispenserBlockEntity.java)
- [Dropper inheritance](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/DropperBlockEntity.java)
- [Container finding and sided insertion rules](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java)
- [Behavior bootstrap](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/Bootstrap.java)
- [Registered item actions](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java)
- [Projectile execution](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/core/dispenser/ProjectileDispenseBehavior.java)
- [Default single-item ejection and returned containers](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/core/dispenser/DefaultDispenseItemBehavior.java)
- [Bucket placement and evaporation rules](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/BucketItem.java)
- [Inventory updates](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/BaseContainerBlockEntity.java)
- [Container removal and save notification](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java)
- [Chunk dirty marking](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/Level.java)
- [Game-time scheduling](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/LevelAccessor.java)
- [Pending-tick deduplication](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/ticks/LevelChunkTicks.java)
- [Scheduled-tick execution](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerLevel.java)
