# Crafter

A **Crafter** (`minecraft:crafter`) turns a loaded **3×3 crafting grid** into items when redstone activates it. It can feed the result into a container in front or eject it into the world. Its nine input slots can be individually disabled to keep a recipe's empty spaces clear. [Crafting and output behavior][crafter] · [Active block and block entity][blocks] [block-entities]

## Crafting and collecting

Craft **one Crafter** from **five Iron Ingots, one Crafting Table, one Dropper, and two Redstone Dust**:

| Left | Center | Right |
| --- | --- | --- |
| Iron Ingot | Iron Ingot | Iron Ingot |
| Iron Ingot | Crafting Table | Iron Ingot |
| Redstone Dust | Dropper | Redstone Dust |

The center-bottom ingredient is a **Dropper**. See its [device guide](DispenserAndDropper.md#crafting-and-collecting) for that component's recipe. [Crafter recipe][recipe]

A placed Crafter can be collected even by hand in this checked build. Although it appears in the pickaxe and needs-stone-tool tags, its block registration **does not require the correct tool for drops**, and the player mining gate therefore permits the ordinary block drop without a pickaxe. The loot table returns one Crafter, subject to its explosion-survival condition. [Registration][blocks] · [Tool tags][pickaxe] [stone-tag] · [Actual tool gate][player] [mining] · [Block loot][loot]

On ordinary removal, its stored ingredients drop separately. The ordinary dropped Crafter item does **not** package its inventory or disabled-slot layout for transport: its loot has no component-copy function. Loaded contents, disabled slots, triggered state, and the crafting-animation counter are saved while it remains a block entity. [Removal dispatch][chunk] [entity-base] · [Saved state][container] · [Loot][loot]

## Placement and the crafting screen

The **front/output face** can point horizontally, upward, or downward. Placement sets it opposite your nearest looking direction; in ordinary horizontal placement it faces toward you. Check the output face before placing the destination container. Vertical placement also records a top orientation, but the recipe layout is still the 3×3 grid shown in the screen. [Placement and output direction][crafter] · [Grid construction][menu]

Open the Crafter to load its inputs. The result on the right is a **preview**, not a slot from which you can take crafted items. The actual crafting action needs redstone. [Menu and preview calculation][menu] · [Noninteractive result slot][preview] · [Active screen registration][screen-wiring]

To disable a slot, remove its items, then click the empty slot with an empty cursor. Click a disabled slot to enable it again. **Occupied slots cannot be disabled** through the server's slot-state handler. Disabling marks a space that must stay empty; it does not reserve that slot for an ingredient. [Screen controls][screen] · [Server packet dispatch][packet] · [Empty-slot validation][container]

Arrange a valid crafting recipe and check the preview before powering the block. The lookup uses the current **crafting recipe** set, so this is not a replacement for a Furnace, Smoker, Stonecutter, Brewing Stand, or Smithing Table. It does not select a stored recipe independently of the items currently loaded. [Recipe lookup][cache] · [Crafting input][craft-input] [crafting-input]

## Redstone timing

An **unpowered-to-powered transition** schedules one craft attempt **4 game ticks later**, equivalent to **2 redstone ticks** or **0.2 seconds** at normal tick speed. Keeping the input powered does not repeatedly craft. Remove power, then apply it again for another attempt. Placing a Crafter at an already powered position also schedules an initial attempt. [Trigger and placement callbacks][crafter] · [Game-time scheduling][tick-access] [level-access] · [Active tick dispatch][server]

Removing power before the four ticks expire does **not** cancel the queued attempt. The craft checks the ingredients present when the scheduled tick runs, not a snapshot taken when the pulse arrived. Very rapid repeated edges can share one pending block tick, so do not assume every pulse arriving inside that delay creates a separate queued craft. [Tick action][crafter] · [Queue deduplication][chunk-ticks] [tick-key] · [Scheduler][level-ticks]

A successful attempt crafts **one recipe batch** and consumes **one item from each occupied input slot**. A recipe can produce several output items in that batch. Its six-tick crafting-animation counter is separate from the four-tick trigger delay; it is not an extra recipe-processing wait. [Craft, consumption, and animation][crafter] [container]

## Feeding and distributing ingredients

[Hoppers](Hopper.md), Droppers, and other supported container transfers can load enabled slots. For ordinary one-item automatic insertion, the scan runs **left to right, then top to bottom**. Empty enabled slots are filled before more items are stacked into an earlier occupied slot; among matching stacks, a later smaller stack can take priority. Items only merge when their item and components match. [Insertion eligibility and balancing][container] · [Transfer scan and merging][hopper]

This distribution **does not know which ingredient belongs in which slot**. Mixed unsorted supplies can fill the wrong spaces and prevent or change the recipe. Disable the spaces the recipe leaves empty and control each ingredient supply. Manual placement and shift-clicking use menu slot rules rather than the same automatic balancing check, so do not assume they distribute a full stack the same way as a Hopper. [Automatic rules][container] · [Manual slot and quick-move rules][slot] [menu]

There is no separate finished-output inventory inside the Crafter. A Hopper below it can extract **ingredients** from the crafting grid. Redstone triggering does not lock those inputs against other containers, so account for transfers that can occur before the scheduled craft. [Nine-slot container][container] · [Extraction and default container rules][hopper] [container-rules]

## Outputs, remainders, and failures

The Crafter sends the assembled result toward its front first, followed by nonempty recipe remainders, such as containers returned by a recipe. These remainders leave through the output path; they are not automatically refilled into their old input slots. [Output ordering and remainders][crafter] · [Default crafting remainders][craft-recipe]

If a container is found directly in front, insertion respects that destination's available slots, item compatibility, and side rules. Output into another Crafter is attempted one item at a time, allowing the receiving Crafter's slot distribution to operate. [Output insertion][crafter] · [Container lookup and sided transfer][hopper]

**A full or incompatible destination does not cancel the craft.** Any output that could not be inserted is ejected as loose items from the front. The same applies when no receiving container exists. Leave room to collect overflow; this device does not wait for storage space. [Insertion fallback and ejection][crafter]

If no recipe matches, or its assembled result is empty, the attempt fails without consuming ingredients. A partly loaded grid can instead match a different valid recipe, so a powered “not ready” Crafter is not guaranteed to fail harmlessly. Use the preview and a deliberate trigger. [Failure and success branches][crafter] · [Current-grid recipe lookup][cache]

## Comparator signal

A [Comparator](RedstoneComparator.md) reading the Crafter receives **one signal level for each occupied or disabled input slot**, giving a total from **0 to 9**. The amount inside an occupied stack does not change that slot's contribution. [Signal calculation][container] · [Analog output callback][crafter]

For example, with eight slots disabled, an empty remaining slot gives **8**, and adding one or more items makes **9**. All nine slots disabled also gives 9 despite there being no craftable input. This signal measures slot state, not recipe validity or stack fullness. Slot changes notify comparator neighbors through the normal block-entity change path. [Slot count and toggle changes][container] · [Comparator update dispatch][entity-base]

## First example: Oak Logs into Planks

This is an **untested, source-based setup**:

1. Place a Crafter with its output face pointing directly into a Chest
2. Leave the top-left slot enabled and disable the other eight empty slots
3. Put Oak Logs in the enabled slot; the preview should show **four Oak Planks**
4. Use a Lever attached to a side of the Crafter to turn its power on, wait for the craft, then turn the Lever off
5. Check the Chest before repeating; a full Chest causes the Planks to be ejected

Each successful activation consumes **one Oak Log** and produces **four Oak Planks**. A larger input stack supplies later activations. A Hopper can later feed that single enabled slot, but it does not create the repeated redstone activations itself. [Oak Planks recipe][planks] · [Accepted Oak logs][oak-logs] · [Craft and trigger logic][crafter]

## Advancement troubleshooting

Crafting another Crafter in this block can produce the item without completing
**Crafters Crafting Crafters**. The bundled advancement has a source-predicted
recipe-ID mismatch at `bffd0eef`: it asks for `minecraft:crafter`, while the
bundled recipe loads as `minecraft:crafting/crafter`. The trigger requires the
same recipe key, so the inspected resources do not satisfy that condition.
The loader keeps the `crafting/` part of the recipe path; it does not flatten it
to the output item's ID. [Advancement condition][crafter-advancement-current] ·
[Recipe][crafter-recipe-current] · [Recipe loading][recipe-load-current] ·
[Path-to-ID conversion][recipe-path-current] · [Exact-key condition][recipe-trigger-current]

There is also a separate output-route condition: the nearby-player trigger runs
only when an output stack is **ejected into the world**. An output fully accepted
by the container in front does not reach that call. Eligible players are searched
in a **17×17×17-block box centered on the Crafter**, not within a 17-block radius.
Even ejecting the result while nearby does not resolve the bundled recipe-ID
mismatch, so this is not a verified workaround. [Output and trigger path][crafter-trigger-current]

This limitation concerns advancement completion, not whether the valid recipe
produces a Crafter. It is also separate from MattMC's intentionally inactive
recipe-unlocking rewards. See [Advancements and statistics](../mechanics/AdvancementsAndStatistics.md#rewards-and-notifications)
for that distinction. This focused source review has **no in-game reproduction**;
data-pack overrides or later fixes can change the criterion or recipe ID.
[Issue #825](https://github.com/HungLo2020/MattMC/issues/825) tracks the shared
recipe-key mismatch, including the bundled decorated-pot and armor-trim goals.

## Related pages

- [Crafter item](../items/Crafter.md), [Crafting Table](CraftingTable.md)
- [Hopper](Hopper.md), [Dispenser and Dropper](DispenserAndDropper.md), [Comparator](RedstoneComparator.md)
- [Redstone](../redstone/Redstone.md), [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `fb7d6979fb8d9773cfe05f084c6085f35feb885c` on 2026-10-02 against active `src/main` code and bundled data. No in-game crafting, transfer, redstone, comparator, or mining test was run. Examples are source-based; data packs and later builds can change recipes and behavior.

[crafter]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/CrafterBlock.java
[blocks]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/Blocks.java
[block-entities]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/entity/BlockEntityType.java
[recipe]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/crafter.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[stone-tag]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json
[player]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/entity/player/Player.java
[mining]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java
[loot]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/loot_table/blocks/crafter.json
[chunk]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java
[entity-base]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java
[container]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/entity/CrafterBlockEntity.java
[menu]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/inventory/CrafterMenu.java
[preview]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/inventory/NonInteractiveResultSlot.java
[screen-wiring]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/client/gui/screens/MenuScreens.java
[screen]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/client/gui/screens/inventory/CrafterScreen.java
[packet]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java
[cache]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/item/crafting/RecipeCache.java
[craft-input]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/inventory/CraftingContainer.java
[crafting-input]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/item/crafting/CraftingInput.java
[tick-access]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/ScheduledTickAccess.java
[level-access]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/LevelAccessor.java
[server]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/server/level/ServerLevel.java
[chunk-ticks]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/ticks/LevelChunkTicks.java
[tick-key]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/ticks/ScheduledTick.java
[level-ticks]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/ticks/LevelTicks.java
[hopper]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java
[slot]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/inventory/CrafterSlot.java
[container-rules]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/Container.java
[craft-recipe]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/item/crafting/CraftingRecipe.java
[planks]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/recipe/crafting/oak_planks.json
[oak-logs]: https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/resources/data/minecraft/tags/item/oak_logs.json

[crafter-advancement-current]: https://github.com/HungLo2020/MattMC/blob/bffd0eef886a446a480cf62166da2eba448eb574/src/main/resources/data/minecraft/advancement/adventure/crafters_crafting_crafters.json#L1-L10
[crafter-recipe-current]: https://github.com/HungLo2020/MattMC/blob/bffd0eef886a446a480cf62166da2eba448eb574/src/main/resources/data/minecraft/recipe/crafting/crafter.json
[recipe-load-current]: https://github.com/HungLo2020/MattMC/blob/bffd0eef886a446a480cf62166da2eba448eb574/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L83
[recipe-path-current]: https://github.com/HungLo2020/MattMC/blob/bffd0eef886a446a480cf62166da2eba448eb574/src/main/java/net/minecraft/resources/FileToIdConverter.java#L23-L33
[recipe-trigger-current]: https://github.com/HungLo2020/MattMC/blob/bffd0eef886a446a480cf62166da2eba448eb574/src/main/java/net/minecraft/advancements/critereon/RecipeCraftedTrigger.java#L45-L52
[crafter-trigger-current]: https://github.com/HungLo2020/MattMC/blob/bffd0eef886a446a480cf62166da2eba448eb574/src/main/java/net/minecraft/world/level/block/CrafterBlock.java#L187-L223
