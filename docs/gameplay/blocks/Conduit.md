# Conduit

A **Conduit** (`minecraft:conduit`) gives nearby wet players **Conduit Power** when surrounded by water and enough valid frame blocks. A complete frame also enables attacks against selected nearby hostile mobs. It activates automatically from its water and frame checks; there is no Beacon-style payment or effect-selection menu. [Conduit registration][s1] · [Active server ticker][s2] · [Activation, effect and attack dispatch][s3]

## Crafting and collecting

At a crafting table, surround **one [Heart of the Sea](../items/HeartOfTheSea.md)** with **eight [Nautilus Shells](../items/NautilusShell.md)** to make **one Conduit**:

```text
Shell  Shell  Shell
Shell  Heart  Shell
Shell  Shell  Shell
```

This is the exact checked shaped recipe. It establishes what the ingredients make; it does not establish every acquisition route for the first Heart or Shells. [Conduit recipe][s4]

The placed block has **hardness 3 and blast resistance 3**. A pickaxe is its efficient mining tool, but no correct-tool tier or Silk Touch is required to recover **one Conduit**. Fortune does not add more. The loot returns the block item without saved waterlogged, frame, or attack-target state. [Block properties][s5] · [Pickaxe tag][s6] · [Conduit loot][s7] · [Harvest gate][s8]

## Fill the inner water volume

The Conduit must sit at the center of a **3 × 3 × 3 volume whose 27 cells all contain water**, including the Conduit's own cell. The activation check asks whether each cell's fluid state belongs to the water tag. Flowing water and waterlogged blocks can qualify; it is not a requirement for 27 plain source-water blocks. An air pocket or a dry solid block anywhere in that inner cube prevents activation. [All 27 water checks][s9] · [Water-at-position definition][s10] · [Water fluid tag][s11]

Item placement makes the Conduit waterlogged when the existing fluid is water-tagged with **amount 8**, including a qualifying falling-water state. Otherwise it places dry. A Water Bucket can fill its dry waterlogged state, and an empty bucket can take the water back; removing the central water makes the activation check fail. The Conduit has no facing and does not need permanent floor support. Its collision shape is a centered **6/16-block cube**, while its block emits **light level 15 even when inactive**. [Placement fluid check][s12] · [Falling amount-eight fluid][s13] · [Flowing water amount][s14] · [Shape and state][s15] · [Centered cube dimensions][s16] · [Stored water][s17] · [Bucket fill and pickup][s18] · [Default survival][s19] · [Constant light emission][s20]

## Build a valid frame

Only these four exact full blocks count, and you may mix them:

- **Prismarine** (`minecraft:prismarine`)
- **Prismarine Bricks** (`minecraft:prismarine_bricks`)
- **Dark Prismarine** (`minecraft:dark_prismarine`)
- **Sea Lantern** (`minecraft:sea_lantern`)

The active implementation uses this fixed list, not a frame-material tag. Stairs, slabs, walls, and other underwater-looking blocks do not count. [Exact accepted block list][s21]

Valid positions are the perimeters of **three perpendicular 5 × 5 squares**, all centered on the Conduit: one horizontal ring and two vertical rings. Their outer edges are two blocks from the center. A single complete ring has **16 blocks** and is enough to activate; two complete perpendicular rings share two positions and use **30 blocks**; all three share their intersections and use **42 blocks**. The inner water cube stays inside these rings. [Exact frame-position predicate and threshold][s22]

One vertical ring, viewed straight on:

```text
FFFFF
FWWWF
FWCWF
FWWWF
FFFFF
```

`F` is a valid frame block, `C` the waterlogged Conduit, and `W` a water-containing cell. Also fill the corresponding inner 3 × 3 planes **one block in front of and behind** this view, completing the 27-cell water volume. Add the other two perpendicular rings for the full frame.

The rings are a convenient building layout. The scanner actually counts valid occupied positions independently: **any 16 of the 42 valid positions** can activate it. A large solid shell or blocks placed outside those positions do not add range. The maximum count is 42. [Counted positions][s23]

## Player range and refresh

| Counted frame blocks | Conduit Power range | Hostile-target attack enabled? |
| --- | ---: | --- |
| Fewer than 16 | Inactive | No |
| 16–20 | 32 blocks | No |
| 21–27 | 48 blocks | No |
| 28–34 | 64 blocks | No |
| 35–41 | 80 blocks | No |
| 42 | 96 blocks | Yes |

The formula is the whole-number result of **frame count ÷ 7, multiplied by 16**, after the 16-block activation threshold. The server refreshes the structure and effects every **40 ticks** and grants **Conduit Power I for 260 ticks** per valid refresh: two-second refreshes and a thirteen-second duration at 20 TPS. These are game-tick values, not measured wall-clock promises. [Refresh scheduling][s24] · [Range and effect application][s25]

A player must be **in water or exposed to rain**, and their integer block position must be at a **strictly smaller three-dimensional distance** than the listed range from the Conduit's block position. Exactly 32 blocks away fails a 32-block test. The preliminary search box does not turn this into the Beacon's upward-reaching coverage: Conduit applies the distance check afterward. Spectators are excluded, walls are not checked, and ordinary mobs do not receive the player aura. [Player conditions][s26] · [Strict distance comparison][s27] · [Water or rain check][s28] · [Default spectator filter][s29]

Leaving the water/rain condition, leaving range, or breaking the structure stops subsequent refreshes. The effect already received can remain until its current duration expires. The structure itself still needs its full inner water volume even when rain qualifies the player. [Structure and effect conditions][s30] · [Effect duration ticking][s31]

## What Conduit Power actually supplies

The [water and fire effects guide](../effects/WaterAndFireEffects.md#conduit-power) compares Conduit Power with Water Breathing and Breath of the Nautilus.

- **Underwater breathing:** the active air-supply check treats Conduit Power as water breathing, preventing ordinary drowning-air depletion while the effect remains
- **Mining assistance:** Conduit Power I gives the shared digging-speed calculation a **20% multiplier increase**. If Haste is also present, the helper uses the higher amplifier rather than adding both bonuses
- **Underwater visibility input:** with the player's eyes in water, the current client feeds Conduit Power into its night-vision lightmap factor using the player's water-vision value

The first two are active gameplay consumers. The visibility claim is a source-traced rendering input, not a tested promise about every shader or display setting. [Breathing and digging helpers][s32] · [Active air-supply branch][s33] · [Active mining calculation][s34] · [Water-vision gate][s35] · [Conduit lightmap factor][s36] · [Active renderer consumes lightmap input][s37]

**Conduit Power does not remove Mining Fatigue or all underwater mining penalties.** The player mining method applies the digging bonus, then Mining Fatigue, the submerged-mining attribute when the eyes are in water, and the off-ground penalty. It also does not grant a general swimming-speed or damage-resistance effect. Use [Mining](../mechanics/Mining.md) for tool and harvest-tier choices; Conduit Power does not make an unsuitable tool qualify for a block's drops. [Mining modifiers and harvest gate][s38] · [Conduit effect registration][s39]

## Attacks from a complete frame

All **42 frame positions** must count before the server enables attacking. Each active 40-tick update can damage **one selected living target** for **4 magic damage points before the target's defenses and damage rules**. This is not area damage or a guarantee of two hearts lost. [Full-frame gate and cadence][s40] · [Damage call][s41]

A new target must satisfy the game's **Enemy classification** and be in water or rain. The Conduit picks randomly from candidates whose bounding boxes intersect the Conduit block's box expanded by eight blocks. It does not choose the nearest mob or perform a line-of-sight test. Players and passive animals are not newly selected by this predicate; appearance or aggressive behavior alone does not prove that every imported mob implements the required classification. [New target selection and search box][s42]

There is an important distinction between **selection and retention**. Once selected, the target remains eligible while alive and within a strict **eight-block block-position distance**. That retention path does **not** recheck water/rain, so a target can keep taking attacks after moving onto dry ground while remaining close enough. Initial selection uses the box instead of this distance test, so a candidate near a box corner can receive an initial hit even though it will fail the later retention check. Loss of a target can leave an update without a replacement attack. [Retention and replacement][s43] · [Attack after target update][s44]

## Sources and verification

Source-reviewed on **2026-10-02** at `b6b5f733b316cef6852866924e2f11f12b0c4f5c`. The registered block, block entity, server ticker, recipe, loot, fluid test, all 42 frame positions, effect thresholds, attack selection/retention, and active effect consumers were checked. No in-game activation, range, combat, drowning, mining, or renderer test was run. Data packs can change recipes and fluid tags; entity behavior, status effects, server ticking, and rendering settings affect observations. [Block entity registration][s45]

Related: [Conduit item](../items/Conduit.md) · [Beacon](Beacon.md) · [Heart of the Sea](../items/HeartOfTheSea.md) · [Nautilus Shell](../items/NautilusShell.md) · [Kelp](Kelp.md) · [Sea Pickles](SeaPickle.md) · [Blocks](Blocks.md)

[s1]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/Blocks.java#L5178-L5188
[s2]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/ConduitBlock.java#L53-L58
[s3]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/entity/ConduitBlockEntity.java#L93-L110
[s4]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/resources/data/minecraft/recipe/crafting/conduit.json
[s5]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/Blocks.java#L5178-L5188
[s6]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[s7]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/resources/data/minecraft/loot_table/blocks/conduit.json
[s8]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[s9]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/entity/ConduitBlockEntity.java#L128-L140
[s10]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/LevelReader.java#L142-L144
[s11]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/resources/data/minecraft/tags/fluid/water.json
[s12]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/ConduitBlock.java#L88-L93
[s13]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L192-L202
[s14]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/material/WaterFluid.java#L138-L153
[s15]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/ConduitBlock.java#L29-L47
[s16]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/Block.java#L158-L182
[s17]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/ConduitBlock.java#L60-L64
[s18]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java#L20-L51
[s19]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L311
[s20]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/Blocks.java#L5178-L5188
[s21]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/entity/ConduitBlockEntity.java#L36-L43
[s22]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/entity/ConduitBlockEntity.java#L142-L162
[s23]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/entity/ConduitBlockEntity.java#L142-L162
[s24]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/entity/ConduitBlockEntity.java#L93-L110
[s25]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/entity/ConduitBlockEntity.java#L165-L180
[s26]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/entity/ConduitBlockEntity.java#L165-L180
[s27]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/core/Vec3i.java#L189-L198
[s28]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/entity/Entity.java#L1439-L1449
[s29]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/EntityGetter.java#L50-L52
[s30]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/entity/ConduitBlockEntity.java#L128-L180
[s31]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L214-L228
[s32]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/effect/MobEffectUtil.java#L25-L46
[s33]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/entity/LivingEntity.java#L424-L442
[s34]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/entity/player/Player.java#L623-L650
[s35]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/client/player/LocalPlayer.java#L1033-L1046
[s36]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/client/renderer/LightTexture.java#L223-L242
[s37]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L2699-L2742
[s38]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/entity/player/Player.java#L623-L657
[s39]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/effect/MobEffects.java#L106-L107
[s40]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/entity/ConduitBlockEntity.java#L93-L110
[s41]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/entity/ConduitBlockEntity.java#L182-L194
[s42]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/entity/ConduitBlockEntity.java#L210-L220
[s43]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/entity/ConduitBlockEntity.java#L196-L220
[s44]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/entity/ConduitBlockEntity.java#L182-L194
[s45]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/entity/BlockEntityType.java#L215-L215
