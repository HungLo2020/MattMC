# Primal Magma and Fissure Primal Magma

Primal Magma is a dimly glowing block with an active hot-floor hazard. Its Fissure variant is also registered, but has no separate inventory item and can replace itself after a scheduled update. **The bundled implementation does not establish a boss ritual or natural source for either block.**

## Obtaining

| Block | Registry ID | Confirmed access |
| --- | --- | --- |
| Primal Magma | `minecraft:primal_magma` | [Primal Magma item](../items/PrimalMagma.md) explicitly listed in Creative; commands |
| Fissure Primal Magma | `minecraft:fissure_primal_magma` | Block-placement commands; no separate registered item |

Both use their named active block classes and emit **light level 5**. No crafting recipe, natural generation placement, or active boss event creating them was found in the reviewed source and data.

Both block loot tables name **one Primal Magma item**, including the Fissure table. However, both blocks require a correct tool for normal mining drops, and neither appears in the checked bundled mineable tags. **Do not assume an ordinary pickaxe will collect them.** A loot-table entry is not proof that the player-mining tool check succeeds.

Fissure also contains an old pick-block helper returning Primal Magma, but its signature does not match the current callback. It is not evidence that normal pick-block supplies that item.

## Collision and conditional step damage

When the current step-on callback runs for a living entity that is not stepping carefully and has no Frost Walker enchantment, it attempts **1 point of hot-floor damage** and applies an ignition duration:

- **Primal Magma:** 3 seconds
- **Fissure Primal Magma:** 6 seconds

Damage and fire protections can affect the result. The Primal Magma check does not require its `active` state to be true: **inactive does not mean safe to walk on**. Stepping carefully or Frost Walker skips this callback's damage-and-ignition branch; that is not a guarantee against unrelated nearby fire or lava.

Primal Magma normally has full collision. When its `active` state is true, entity collision uses a thin, **2/16-block-high** surface, with no collision for dropped-item entities. Fissure always uses that reduced entity-collision behavior. Keep dropped valuables away while testing the blocks.

The thin surface also affects the step check: the engine selects the stepped-on block from **0.2 blocks below the feet**. A player settled on a 0.125-block-high surface can therefore have the block underneath selected instead. The ignition durations above describe the callbacks; they do not establish continuous contact fire while standing on either thin variant.

**The old inside-block effects are not connected.** Both classes contain four-argument inside-block methods, while the current engine dispatches a six-argument callback. This review therefore does not claim their additional slowing, upward push, or inside-block fire/damage bodies run. The verified step-on and collision-shape paths are separate.

## State changes and Fissure replacement

Primal Magma defaults to `active=false` and `permanent=false`. A neighbor-shape update schedules its block tick two ticks later. That tick consults a boss-active check which **always returns false** in this snapshot:

- A non-permanent active block is switched inactive when that scheduled tick runs
- Setting `permanent=true` prevents this scheduled active-state toggle
- No automatic boss-driven activation is established

Fissure's `regen_height` state ranges from **0 to 4**, defaulting to 0. On its scheduled tick, it replaces its own position and may fill that many positions above it, skipping already occupied positions above the base.

This is **not restoration of saved terrain**. The simplified selection favors neighboring Grass Blocks, then Moss Blocks, then Dirt; otherwise it can copy another neighboring state. Air is not filtered out, so a Fissure can disappear. The fallback also examines material below it. Do not use this state-changing block as a stable building material without testing its surroundings.

Although both registrations request random ticks, their classes do not override the current random-tick callback. The changes described above come from the scheduled block-tick path, not a verified regular regeneration interval.

## Related pages

- [Primal Magma item](../items/PrimalMagma.md)
- [Dinosaur Chop](DinosaurChop.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No gameplay test of damage, mining, collision, command placement, or scheduled replacement was run. Commands, custom states, and data packs can change the conditions.

- [Block registration, light, and mining requirements](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/Blocks.java#L6894-L6923), [item registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L296), and [Creative entry](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L796)
- [Primal Magma state, step hazard, collision, and false boss check](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/block/PrimalMagmaBlock.java)
- [Fissure state, step hazard, collision, replacement, and old pick-block helper](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/block/FissurePrimalMagmaBlock.java)
- [Current step-on dispatch](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Entity.java#L847-L853), [current inside-block callback](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L367-L370), and [inside-block dispatch](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L775-L777)
- [Stepped-on position selection](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Entity.java#L941-L970)
- [Empty base random-tick callback](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L341-L345) and [current pick-block callback](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L399-L401)
- [Primal Magma loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/primal_magma.json), [Fissure loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/fissure_primal_magma.json), and [bundled mineable tags](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/mineable)
- [Player correct-tool check](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657) and [mining drop gate](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L291)
