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

## Hopper automation

A [Hopper](Hopper.md#point-the-output-correctly) must reach the correct face of the stand. The following are the ordinary Hopper connections; insertion still checks whether the item belongs in the selected slot. [Sided slots][stand-slots] · [Accepted items and face rules][stand-transfer] · [Hopper insertion][hopper-insert]

| Hopper connection | Stand slots reached | What passes through |
| --- | --- | --- |
| Above, pointing down into the stand | Ingredient slot (3) | Registered brewing ingredients |
| Beside the stand, pointing into its side | Bottle slots (0–2) and fuel slot (4) | Potions, Splash Potions, Lingering Potions, or empty Glass Bottles into empty bottle slots; brewing fuel into the fuel slot |
| Below, pulling from the stand | Bottle slots (0–2) and ingredient slot (3) | Any stored bottle-slot item; the ingredient slot can be emptied this way only when it contains a Glass Bottle |

**Feed Blaze Powder from above for an ingredient, or from the side for fuel.** The upper route cannot reach the fuel slot, and the side route cannot reach the ingredient slot. The bottom route cannot extract spare powder from the fuel slot. [Sided slots][stand-slots] · [Slot permissions][stand-transfer]

**Lock the output Hopper before loading a batch, and keep it locked until the desired brewing step finishes.** A Hopper underneath does not check whether a potion is finished: it can remove Water Bottles, intermediate potions, or bottles from an active brew. Removing the last compatible bottle cancels the operation as described above. Applying redstone to the stand itself does not pause its brewing loop; control transfers with [Hopper locking](Hopper.md#redstone-locking). These rules do not establish a tested automatic brewing circuit. [Downward extraction][hopper-pull] · [Extraction permission][hopper-permissions] · [Active brew and cancellation checks][stand-tick] · [Hopper enabled check][hopper-enabled]

The ingredient-slot Glass Bottle permission is **not a promise of a returned bottle after every brew**. See the existing [Dragon's Breath remainder caveat](../items/DragonsBreath.md#usage), tracked in [issue #812](https://github.com/HungLo2020/MattMC/issues/812). The checked consumption code looks up the remainder after shrinking the ingredient stack; this page does not assume that issue is fixed. [Remainder handling][stand-remainder]

## Signals and visible bottles

A [Comparator](RedstoneComparator.md#reading-containers) measures **item fullness across all five inventory slots**, including ingredient and spare fuel items. It does not read brewing progress or the internal reserve of loaded fuel uses. Its output is 0 for an empty inventory; otherwise it is one plus the rounded-down value of 14 times average slot fullness. Each stack is measured against its allowed stack size. A changing signal can therefore reflect an item transfer or consumed powder without proving that a potion is ready. [Stand analog hook][stand-analog] · [Five-slot inventory][stand-slots] · [Fullness calculation][fullness] · [Rounding][rounding]

The three bottle-presence states (`has_bottle_0`, `has_bottle_1`, and `has_bottle_2`) tell you only whether the corresponding bottle slot is nonempty. They do not identify the potion or mark a completed brew. Smoke particles are produced by the stand's visual animation without checking brewing progress, so smoke is not an activity indicator either. [Bottle-state updates][stand-bottles] · [Smoke and state definition][stand-visuals]

## Saving and moving a stand

The placed stand saves its inventory, remaining brewing countdown (`BrewTime`), and remaining loaded fuel uses (`Fuel`). When a positive countdown is loaded, the loader restores ingredient tracking from the ingredient slot; progress still depends on active server ticks and the current brew checks. Saved fields alone are not a guarantee that any changed or invalid batch will finish after loading. [Load and save][stand-save] · [Active brew checks][stand-tick]

On ordinary block removal, the stand's inventory spills as separate items. Its block-item loot copies the custom name only, not the inventory, countdown, or loaded fuel reserve. Empty it before relocating it; a mined stand is not portable filled brewing storage. [Removal dispatch][removal] · [Container spill][spill] · [Per-slot drops][spill-items] · [Complete stand loot][stand-loot]

## Troubleshooting

If brewing does not start, check fuel and the exact **current potion + ingredient** combination. An item fitting the ingredient slot does not mean it works on every potion. An active server tick is required for progress; the listed seconds assume normal tick speed.

## Related pages

- [Brewing guide](../brewing/Brewing.md)
- [Brewing Stand item](../items/BrewingStand.md)
- [Blaze Powder](../items/BlazePowder.md)
- [Hopper](Hopper.md#redstone-locking)
- [Redstone Comparator](RedstoneComparator.md#reading-containers)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. This is not an in-game test. Data packs can change crafting recipes, fuel tags, and loot; later builds may change the behavior described here. Naturally generated stands were not reviewed for this page.

Automation, signals, visual states, persistence, and removal were additionally source-reviewed at `9bafc14d2e2943dcfe8a37e9e81dc001386b88bb` on **2026-10-04**, tracing the active Hopper, Comparator, server-tick, load/save, and removal consumers. No in-game brewing, circuit, transfer, save/reload, or mining test was run. The existing Dragon's Breath remainder limitation remains qualified in its linked owner.

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

[fullness]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java#L748-L767
[removal]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java#L311-L323
[spill]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java#L250-L254
[stand-slots]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/block/entity/BrewingStandBlockEntity.java#L28-L44
[stand-transfer]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/block/entity/BrewingStandBlockEntity.java#L213-L243
[hopper-insert]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L143-L174
[hopper-pull]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L218-L263
[hopper-permissions]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L280-L312
[stand-tick]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/block/entity/BrewingStandBlockEntity.java#L96-L122
[hopper-enabled]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L106-L131
[stand-remainder]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/block/entity/BrewingStandBlockEntity.java#L170-L190
[stand-analog]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/block/BrewingStandBlock.java#L88-L95
[rounding]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/util/Mth.java#L524-L527
[stand-bottles]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/block/entity/BrewingStandBlockEntity.java#L124-L149
[stand-visuals]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/block/BrewingStandBlock.java#L75-L100
[stand-save]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/level/block/entity/BrewingStandBlockEntity.java#L192-L211
[spill-items]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/Containers.java#L11-L24
[stand-loot]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/loot_table/blocks/brewing_stand.json#L1-L30
