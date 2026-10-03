# Elevator

An **Elevator** (`minecraft:elevator`) moves a player between Elevator blocks in the same vertical column. Use **Jump to request an upward trip** or **Sneak to request a downward trip**. No redstone input, fuel or menu is involved. The current implementation has two upward input paths with different search limits, so the details below matter when planning floors. [Registered block][registration] · [Player controls][controls] · [Grounded jump hook][jump-hook]

## Obtaining and breaking

The block has a normal [item form](../items/Elevator.md) and is listed in the **Functional Blocks** category. MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) can request this ordinary listed item in **Creative**. The inspected bundled recipes contain **no Elevator recipe**, and the bundled loot tables contain **no `minecraft:blocks/elevator` table**. A bundled crafting or natural/loot acquisition route is therefore not established by this source snapshot; browser insertion is a separate route. Server data packs can supply additional recipes or drops. [Item registration][item] · [Creative tab][creative-tab] · [Creative entry][creative-entry] · [Recipe inventory][recipes] · [Loot inventory][loot-inventory]

**Do not break an Elevator expecting to recover it.** Its registration requires a correct tool for drops, but the bundled block tags do not assign it to the standard mineable tool groups. Independently of that tool gate, its default loot-table name resolves to an empty table when no pack supplies the missing table. A stronger pickaxe or Silk Touch does not supply that absent loot. The registered hardness and blast resistance are both **1.8**; those numbers do not establish an obtainable drop. [Block properties][registration] · [Harvest check][harvest] · [Tool rules][tool-rules] · [Unmatched tools][tool-default] · [Block tags][tags] · [Default loot naming][loot-name] · [Missing-table fallback][loot-fallback] · [Strength values][strength]

## Setting up floors

Place the Elevators at matching **X and Z coordinates**, with the destinations above or below the starting block. Their placement uses a plain full block with no facing, color, channel or powered state. The search checks positions in that column rather than following a shaft, and solid blocks between floors do not stop it. At each landing, leave comfortable standing space and remove hazards yourself. [Placement default][placement] · [Block implementation][implementation] · [Full-block shape][shape] · [Vertical search][search]

For a simple source-derived setup, put two Elevators eight blocks apart vertically, leave at least two air blocks above each, stand on the lower one and tap Jump. Tap Sneak from the upper one to return. This layout keeps the target within both upward search limits and provides more headroom than the code checks; it is **not an in-game-tested design**. [Upward search][jump-search] · [Client controls and search][controls-search]

## Controls and destination search

The client checks Jump and Sneak at the **end of its tick**. It reacts when each key changes from released to pressed, so release and press again for another client request. This handler skips spectators and dead players. It handles a new Sneak press before a new Jump press if both arrive together. Merely walking onto a block does not trigger a trip. [Tick wiring][tick-hook] · [Input transitions][controls] · [Step behavior][implementation]

For this key-press path:

1. The client checks the player's current block position, then the **five positions directly below it**. That is six inspected cells, not six blocks below the feet. It uses the first Elevator found. If that Elevator fails the clearance test, it stops looking for an origin; it does not try a deeper one. Being on the ground is not a condition of this origin search. [Origin selection][origin]
2. From that origin, it checks one block at a time upward or downward in the **same X/Z column**. It stops at the dimension's build-height limit or beyond **384 blocks of vertical separation**. [Search bounds][search]
3. It selects the first Elevator whose immediately-above block is not marked suffocating. An unsuitable destination is skipped, so a farther suitable Elevator can be chosen. Other kinds of blocks between floors do not obstruct this search. [Destination selection][search] · [Clearance predicate][clearance]
4. It asks the server to perform the trip. The checked server path requires loaded Elevator endpoints and the player to remain near the origin before it calls the teleport routine. Thus **384 is the client search limit**, not a measured or unconditional travel guarantee. [Packet registration][packet-registration] · [Packet dispatch][packet] · [Server checks][server]

### The separate grounded-jump path

The ordinary player jump method also checks the block beneath the player that affects movement. When that is an Elevator, it searches **1–64 blocks upward** in the same column and chooses the first Elevator passing the same one-block suffocation test. If found, the ordinary jump is suppressed; this path performs the teleport only when running on the server. If no target is found, it falls through to the normal jump. [Jump hook][jump-hook] · [Grounded path and limit][jump-search]

This is separate from the end-of-tick key handler and has no downward counterpart. The code does not reduce the two paths to one shared range setting. In particular, the grounded jump hook can run again under normal held-jump processing, whereas the client request is tied to a fresh key press. Do not assume every upward activation uses exactly the same callback sequence or range; their combined behavior has not been checked in game. [Jump hook][jump-hook] · [Client input transitions][controls] · [Living-entity jump processing][living-jump]

## Landing position and safety limits

The teleport routine places the player at the destination block's **X/Z center**. The landing Y is the block's Y plus the top of its support shape, with a minimum of the block's own Y; for this full-block Elevator, that is the top surface, **Y + 1**. It clears accumulated fall distance and vertical velocity, but preserves horizontal velocity. A player already moving sideways can therefore continue moving after arrival. [Landing routine][landing] · [Support shape][support-shape] · [Full-block shape][shape]

**The destination test does not guarantee a safe landing.** It checks only whether the block immediately above the Elevator is suffocating. It does not require two empty blocks, inspect the player's full collision box or reject liquid, fire and other hazards. Build your own clear landing area rather than treating the destination check as a complete collision or hazard inspection. [Actual clearance check][clearance] · [Grounded clearance check][jump-search] · [Server acceptance checks][server] · [Teleport routine][landing]

The source-reviewed travel-validation and arrival checks are tracked in [#797](https://github.com/HungLo2020/MattMC/issues/797). This is an open implementation issue, not a verified fix or a live teleport test.

## MattMC scope and verification

These are MattMC's active registrations and callbacks. Comments referring to an imported Elevator mod do not establish extra features here: the inspected search uses hard-coded ranges, and this block supplies no color pairing, configurable range screen, precision-target toggle or right-click configuration. The guide does not infer upstream crafting or safety rules. [Client implementation][controls-search] · [Block implementation][implementation] · [Default interaction][use-default]

Source-reviewed on **2026-10-02** at `099b1184d1a7115915d880b436c8f58c1ed5a422`. Registration, item/Creative wiring, bundled recipes/loot/tags, both input chains, packet registration and server acceptance, clearance and landing were inspected. No in-game teleport, held-key, multiplayer, collision or mining test was run.

Related: [Elevator item](../items/Elevator.md) · [Bubble Columns](BubbleColumns.md) · [Redstone and transport catalog](catalog/redstone.md) · [Blocks](Blocks.md)

[registration]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/Blocks.java#L4728-L4732
[item]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/Items.java#L871
[creative-tab]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1031-L1036
[creative-entry]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1258-L1262
[recipes]: https://github.com/HungLo2020/MattMC/tree/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/recipe
[loot-inventory]: https://github.com/HungLo2020/MattMC/tree/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/loot_table/blocks
[tags]: https://github.com/HungLo2020/MattMC/tree/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/tags/block
[harvest]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[tool-rules]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/ToolMaterial.java#L35-L49
[tool-default]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/component/Tool.java#L49-L57
[loot-name]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1007-L1009
[loot-fallback]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[strength]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1143-L1153
[placement]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/Block.java#L434-L437
[implementation]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/ElevatorBlock.java#L12-L85
[shape]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L321-L327
[support-shape]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L288-L290
[tick-hook]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/client/Minecraft.java#L2001-L2006
[controls]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/client/player/LocalPlayer.java#L1057-L1079
[controls-search]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/client/player/LocalPlayer.java#L1057-L1129
[origin]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/client/player/LocalPlayer.java#L1109-L1124
[search]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/client/player/LocalPlayer.java#L1082-L1106
[clearance]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/client/player/LocalPlayer.java#L1126-L1129
[packet-registration]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L103-L107
[packet]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/network/protocol/game/ServerboundElevatorTeleportPacket.java#L9-L45
[server]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2279-L2309
[jump-hook]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/player/Player.java#L510-L523
[jump-search]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/ElevatorBlock.java#L28-L65
[living-jump]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2789-L2813
[landing]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/ElevatorBlock.java#L67-L85
[use-default]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L202-L210
