# Redstone Randomizer

A **Redstone Randomizer** (`minecraft:redstone_randomizer`) routes each recognized power-on transition to one of **two sideways outputs**. The selected side supplies strength **15** while powered; the other side supplies 0. It chooses a side, not a random signal strength, and a steady input does not keep rerolling. [Registration][registration] · [Power transition][transition] · [Signal output][signal]

## Obtaining, support and drops

The Randomizer has a normal [item form](../items/RedstoneRandomizer.md) listed in **Redstone Blocks**. The [inventory item browser](../mechanics/InventoryBrowser.md) can request this ordinary category entry in **Survival as well as Creative**. No recipe for it was found in the inspected bundled recipe data. Its block loot table returns **one Randomizer** on ordinary Survival breaking, with no correct-tool, Silk Touch or Fortune requirement. This recovery rule is separate from requesting the first item through the browser. Explosions apply the loot table's survival chance. [Item registration][item] · [Creative entry][creative] · [Recipe inventory][recipes] · [Block properties][registration] · [Harvest gate][harvest] · [Loot][loot]

It breaks instantly and a piston destroys it rather than carrying it. Its collision shape is **2/16 of a block high**, and placement needs a support block with a rigid upward face. Losing that support removes the Randomizer; the normal neighbor-removal path drops its resources. [Block properties][registration] · [Shape and support][support] · [Support loss][support-loss] · [Neighbor removal][neighbor]

## Orientation and wiring

The input is on the block's `facing` side. Placement sets that direction opposite the direction the player is looking, so the input initially points back toward the placer. It accepts a positive signal from that neighboring position, including the power value of adjacent Redstone Dust. Power entering some other side does not directly satisfy this input test. [Placement direction][placement] · [Input lookup][input]

The two possible output blocks are perpendicular to that input direction. For exact wiring, use this mapping; `left` and `right` are state labels relative to the stored input direction, not labels relative to whichever side you are viewing:

| Input side / `facing` | `output=left` reaches | `output=right` reaches |
| --- | --- | --- |
| North | West | East |
| East | North | South |
| South | East | West |
| West | South | North |

[Physical output mapping][directions]

The selected lateral side supplies **both ordinary and direct/strong strength 15**. The other side, the input side, the straight-through side, top and bottom receive no output from this block's signal methods. Dust can visibly connect toward any horizontal side because the Randomizer is a signal source; a connected dust arm does not by itself prove that side is an output. [Signal methods][signal] · [Signal-source flag][source-flag] · [Dust connections][wire]

Use separated branches so external wiring does not connect the outputs back together or feed them around to the input. The Randomizer counts as a diode, so a selected output aimed directly into the side of a [Repeater](RedstoneRepeater.md) can lock that Repeater. The Randomizer itself has no side-lock behavior. [Diode identity][diode-identity] · [Repeater locking][repeater-lock] · [Accepted locking signal][control-signal] · [Unlocked default][unlocked]

## Random choices and update timing

An input/state mismatch schedules a check after **2 game ticks**, nominally **0.1 seconds at 20 ticks per second**. At the scheduled check, the block reads the input again:

- If unpowered and the input is now positive, it becomes powered and makes one random Boolean choice between `left` and `right`
- If powered and the input is now 0, it turns off while retaining the stored output-side label
- If input and powered state already agree, it does nothing

The two Boolean outcomes choose the two sides with equal intended probability; repeated choices of the same side are allowed. Neither alternating outputs nor equal totals over a small number of activations are guaranteed. Any positive accepted input strength becomes 15 on the chosen output; changing input strength while it remains positive does not reroll. [Delay][delay] · [Scheduling and input condition][scheduling] · [Sampled state change][transition] · [Boolean generator][random-boolean]

**This block has no built-in clock.** Supply separate on/off cycles to obtain repeated choices, and allow each off period to be recognized before the next on period. A short pulse that has disappeared by its scheduled check can be missed; a brief interruption that is gone before an off-check does not create a new choice. Do not assume the pulse-extension behavior of an ordinary Repeater: the Randomizer replaces the inherited tick behavior with the sampled comparison above. [Randomizer tick][transition] · [Inherited Repeater-style tick][diode-tick]

There is one placement exception to the normal delay: placing it with a positive input already present schedules an initial check after **1 game tick**. On a real power-state change the Randomizer notifies both lateral output neighborhoods, allowing a formerly powered branch to update as well as the selected branch. Placement and removal also use those lateral notifications. All timing is game-tick based and depends on the world processing the scheduled updates. [Powered placement][placement] · [Branch updates][updates] · [Placement/removal callbacks][lifecycle]

## Reading the block and trying a circuit

The state contains `facing`, `powered` and `output`; ordinary placement starts unpowered with `output=left`, then any scheduled powered-placement check can select either side. There is no use-action mode switch, adjustable delay, menu or reroll button. The bundled model changes between powered and unpowered appearances, but **left and right selections use the same model and rotation** for a given facing/power state. Observe separate output branches to see the selected result. [Default state][state] · [State properties][state-properties] · [Default interaction][use-default] · [Blockstate models][models]

For a source-derived trial, orient the input north, drive it with an independently toggled north-side source and place a [Redstone Lamp](RedstoneLamp.md) directly west and east. Leave each input state long enough for the Randomizer and lamps to settle before toggling again. One lateral branch is selected during each recognized powered period. The lamps have their own update behavior, so their visible changes are not a precise timing measurement of the Randomizer. This arrangement has **not been tested in game**. [Input][input] · [Output mapping][directions] · [Signal][signal]

## Sources and verification

Source-reviewed on **2026-10-02** at `099b1184d1a7115915d880b436c8f58c1ed5a422`. Active block/item/Creative registration, codec, state/model mappings, bundled recipe absence, loot, support rules, diode input/scheduling, overridden randomization and output notifications were inspected. No gameplay circuit, pulse-width, randomness-distribution, support-break or piston test was run. Data packs can change recipe and loot availability; upstream component behavior is not assumed.

Related: [Redstone Randomizer item](../items/RedstoneRandomizer.md) · [Redstone Dust](RedstoneDust.md) · [Redstone basics](../redstone/Redstone.md) · [Redstone and transport catalog](catalog/redstone.md) · [Blocks](Blocks.md)

[registration]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/Blocks.java#L2852-L2854
[item]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/Items.java#L999-L1003
[creative]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1283-L1296
[recipes]: https://github.com/HungLo2020/MattMC/tree/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/recipe
[harvest]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[loot]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/loot_table/blocks/redstone_randomizer.json#L1-L21
[support]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/DiodeBlock.java#L27-L51
[support-loss]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/RedstoneRandomizerBlock.java#L38-L52
[neighbor]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/DiodeBlock.java#L83-L96
[placement]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/DiodeBlock.java#L151-L161
[input]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/DiodeBlock.java#L119-L133
[directions]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/RedstoneRandomizerBlock.java#L102-L113
[signal]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/RedstoneRandomizerBlock.java#L70-L82
[source-flag]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/DiodeBlock.java#L146-L149
[wire]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/RedStoneWireBlock.java#L381-L389
[diode-identity]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/DiodeBlock.java#L191-L193
[repeater-lock]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/RepeaterBlock.java#L78-L86
[control-signal]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/SignalGetter.java#L48-L58
[unlocked]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/DiodeBlock.java#L115-L117
[delay]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/RedstoneRandomizerBlock.java#L33-L36
[scheduling]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/DiodeBlock.java#L98-L121
[transition]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/RedstoneRandomizerBlock.java#L54-L68
[random-boolean]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/levelgen/BitRandomSource.java#L42-L45
[diode-tick]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/DiodeBlock.java#L53-L67
[updates]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/RedstoneRandomizerBlock.java#L84-L100
[lifecycle]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/DiodeBlock.java#L163-L173
[state]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/RedstoneRandomizerBlock.java#L19-L26
[use-default]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L202-L210
[models]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/assets/minecraft/blockstates/redstone_randomizer.json#L1-L64

[state-properties]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/RedstoneRandomizerBlock.java#L115-L133
