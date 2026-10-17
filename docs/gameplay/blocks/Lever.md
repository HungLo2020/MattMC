# Lever

A **Lever** is a redstone switch that stays on or off until toggled. Use it to hold a circuit in one state while you work, keep a [Redstone Lamp](RedstoneLamp.md) powered, or provide a steady input to other components. Its block and item ID is `minecraft:lever`. [Block registration][blocks] · [Item registration][items]

## Crafting and placement

Place **one Stick directly above one Cobblestone** to craft **one Lever**. This vertical recipe fits the inventory crafting grid. It requires Cobblestone specifically, rather than the broader stone-crafting-materials tag. [Crafting recipe][recipe]

Levers attach to the **top, side, or underside of a block with a sturdy attachment face**. A full Cobblestone block is a simple support. The lever starts off when newly placed; changing its floor, wall, or ceiling orientation does not change its strength. If the attachment face stops being valid, the lever breaks away. Placement support and redstone conduction are separate checks: a face that holds a lever does not by itself prove the support will carry its power. [Placement and survival][attachment] · [Default state][states] · [Support removal][block] · [Conduction checks][signals]

Ordinary Survival breaking returns **one Lever**, without Silk Touch or a required tool tier. Its hardness is **0.5**, and the placed switch has no collision, so it does not act as a barrier. Explosion drops depend on the applicable explosion loot rules. [Loot][loot] · [Current block profile][catalog] · [Physical properties][physics] · [Property application][native]

## Switching

Aim at the placed lever and use your normal **Use** control to toggle it. Release Sneak for a straightforward interaction; secondary use while **either hand holds an item** bypasses the ordinary block interaction. The server changes the lever's state, plays its click, and updates neighbors around both the lever and its support. [Interaction handling][use] · [Toggle and updates][lever]

An on lever supplies signal strength **15** to adjacent positions in all six directions; an off lever supplies **0**. It additionally supplies **direct power into the block it is attached to**: below a floor lever, behind a wall lever, or above a ceiling lever. When that support conducts redstone, components beside it can receive power through the support. This does not turn a row of ordinary blocks into a wire. [Lever output][lever] · [Attachment direction][attachment] · [Receiving-block queries][signals]

Ordinary use has **no automatic release timer**. The output holds until another toggle or until the lever is removed. Breaking a powered lever also updates its neighbors so its old output is removed. A receiver may add its own delay: for example, a Redstone Lamp schedules an off check four game ticks after losing power. [State changes and removal][lever] · [Lamp response][lamp]

### Water, pistons, and wind charges

- **Keep the switch out of flowing water.** Levers cannot be waterlogged. Water that spreads into the lever's position replaces it and follows the normal block-drop path. [State and fluid definitions][states] · [Empty fluid state][intrinsics] · [Fluid replacement][flow] · [Water drops][water]
- **An extending piston breaks a lever in its path** instead of moving the switch intact. The piston destruction path drops its resources; moving away its support can also make the lever break away. [Piston reaction][physics] · [Destruction selection][piston-resolver] · [Piston drops][piston]
- **A Wind Charge burst can toggle a lever it reaches**, including switching an already-on lever off. Breeze wind-charge bursts can also trigger it when `mobGriefing` allows this. The trigger path does not run ordinary explosion block destruction, but destructive explosions may still remove the lever or its support. [Lever explosion action][lever] · [Player Wind Charge][wind] · [Breeze charge][breeze] · [Trigger permission][explosion] · [Explosion dispatch][server] · [Destruction handling][behavior]

## Choosing a control

- Choose a Lever for sustained power, such as keeping a test circuit enabled.
- Choose a [Button](Buttons.md) for a timed pulse.
- Use [Redstone Dust](RedstoneDust.md) to carry a signal over a supported path, checking connections and distance.
- Start with the [small source–wire–lamp example](../redstone/Redstone.md#a-small-lamp-circuit) before adding branches or timing-sensitive devices.

## Related pages

- [Lever item](../items/Lever.md)
- [Redstone basics](../redstone/Redstone.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-09** at `4246f4e7bfc1f3ab7862272ebba5f1f38aa16953`. Recipes, loot, current native block/state/physical definitions, their Java application, and active interaction, signal, fluid, piston, and explosion consumers were checked. No crafting, placement, circuit, water, piston, or wind-charge gameplay test was run. Wire feature flags can affect connected circuits.

[blocks]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/Blocks.java#L1182-L1184
[items]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/item/Items.java#L1019
[recipe]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/resources/data/minecraft/recipe/crafting/lever.json
[attachment]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/FaceAttachedHorizontalDirectionalBlock.java
[states]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/rust/content/block/definitions/templates.rs#L57
[intrinsics]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/rust/content/block/definitions/intrinsic/declarations.rs#L13
[block]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/Block.java#L216-L228
[signals]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/SignalGetter.java
[loot]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/resources/data/minecraft/loot_table/blocks/lever.json
[catalog]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/rust/content/block/definitions/catalog.rs#L284
[physics]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/rust/content/block/definitions/physics.rs#L337-L343
[native]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/state/NativeBlockDefinitions.java#L99-L122
[use]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L340-L396
[lever]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/LeverBlock.java
[lamp]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/RedstoneLampBlock.java
[flow]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/material/FlowingFluid.java
[water]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/material/WaterFluid.java#L90-L94
[piston-resolver]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/piston/PistonStructureResolver.java
[piston]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java#L284-L301
[wind]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/entity/projectile/windcharge/WindCharge.java
[breeze]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/entity/projectile/windcharge/BreezeWindCharge.java
[explosion]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/ServerExplosion.java#L291-L299
[server]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/server/level/ServerLevel.java#L1156-L1167
[behavior]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L179-L202
