# Beacon

A **Beacon** (`minecraft:beacon`) supplies a selected status effect to nearby players when it has a complete mineral base and a clear beam path. Build the base, clear the column above, then open the Beacon and pay once to confirm your powers. The [Nether Star page](../items/NetherStar.md#crafting-a-beacon) owns its crafting layout; [Glass and Panes](GlassAndPanes.md#beacon-beam-colors) owns stained beam colors. [Block registration][s1] · [Active ticker and menu][s2]

## Build the base

Use full blocks of **Iron, Gold, Diamond, Emerald, or Netherite**. They are the five entries in the bundled Beacon-base block tag. You may mix these materials freely within a layer; the scanner checks tag membership, not a single matching material. More expensive blocks do not add range or power beyond the layer count. Copper Blocks, Raw Iron Blocks, and decorative variants are not entries in this tag. [Exact valid-base tag][s3] · [Layer scan][s4]

Center the Beacon over a **solid 3 × 3 layer immediately beneath it**. Each deeper layer must be two blocks wider in both horizontal directions. The layer includes its center, so a hollow border is insufficient. The scanner stops at the first incomplete layer and checks at most four layers.

| Complete levels | Layers beneath Beacon, top to bottom | Total base blocks | Effect-box extension | Fresh effect duration | New normal-menu choices |
| --- | --- | ---: | ---: | ---: | --- |
| 1 | 3 × 3 | 9 | 20 blocks | 220 ticks / 11 seconds | Speed I or Haste I |
| 2 | 3 × 3; 5 × 5 | 34 | 30 blocks | 260 ticks / 13 seconds | Resistance I or Jump Boost I, plus earlier choices |
| 3 | 3 × 3; 5 × 5; 7 × 7 | 83 | 40 blocks | 300 ticks / 15 seconds | Strength I, plus earlier choices |
| 4 | 3 × 3; 5 × 5; 7 × 7; 9 × 9 | 164 | 50 blocks | 340 ticks / 17 seconds | Add Regeneration I **or** upgrade the chosen primary to level II |

The counts are derived from the scanned squares and do not include the Beacon itself. Durations assume 20 ticks per second for the seconds conversion. One Beacon provides **one primary effect**, with the fourth level adding the secondary choice; it does not grant every unlocked primary simultaneously. [Base geometry][s5] · [Effect tiers][s6] · [Menu button tiers][s7] · [Power and duration application][s8] · [Tier-based button enablement][s9]

## Beam clearance and activation

Keep the Beacon's vertical column passable all the way to the top of the column. The scan stops at a non-beam-color block whose **light-blocking value is at least 15**, with an explicit exception for **Bedrock**. This is not a check that every block above is air. Ordinary Glass lets the beam pass, stained Glass and Panes supply color, and Tinted Glass blocks it; use the [glass guide](GlassAndPanes.md#beacon-beam-colors) for color mixing. [Beam scan and obstruction][s10]

The base and effect checks run every **80 game ticks**, while the upward beam scan examines at most **ten positions per tick**. Allow the active ticker to catch up after changing the base or overhead column. A visible menu or the block's own light does not establish that powers are being applied: effects require a nonempty beam, at least one complete base level, and a selected primary. [Scan and effect scheduling][s11] · [Primary requirement][s12]

## Select and pay for effects

Open the Beacon, choose the primary and any available secondary, place **one accepted payment item** in its slot, then confirm. The payment tag accepts **Iron Ingot, Gold Ingot, Emerald, Diamond, or Netherite Ingot**. All pay for the same operation; Copper Ingots, nuggets, mineral blocks, and Nether Stars are not accepted payments. Confirming consumes exactly one item and saves the selected effects. Maintaining the effect does not consume more fuel or payments. [Payment tag][s13] · [One-item slot][s14] · [Payment consumption and selection][s15] · [Confirm action][s16] · [Server menu dispatch][s17]

At four levels, choose **Regeneration I alongside the primary**, or choose the matching **primary II** upgrade. The upgrade works by selecting the same effect for both slots; it does not also grant Regeneration. The usual buttons unlock by base tier. [Secondary and upgrade buttons][s18] · [Upgrade follows primary selection][s19] · [Amplifier versus second effect][s20]

If you close the menu without confirming, its unused payment is **dropped from the player**. It is not stored in the Beacon for later. The payment slot belongs to the open menu, not a persistent block inventory. [Menu-local container][s21] · [Closing the menu][s22]

### What the powers do

| Power | Verified effect contribution |
| --- | --- |
| Speed I / II | +20% / +40% movement-speed attribute modifier |
| Haste I / II | +20% / +40% mining-speed factor and +10% / +20% attack-speed attribute modifier |
| Resistance I / II | Reduces damage reaching its resistance calculation by 20% / 40%, except damage that bypasses effects or resistance |
| Jump Boost I / II | Adds 0.1 / 0.2 to upward jump velocity and 1 / 2 to the safe-fall-distance attribute; this is not a fixed final jump height |
| Strength I / II | Adds 3 / 6 to the attack-damage attribute; charge, weapon behavior, and target defenses still affect the hit |
| Regeneration I | Restores one health point on each scheduled regeneration pulse while below maximum health |

These are individual contributions, not guarantees of final speed, block-break time, damage, or healing throughput. [Mining](../mechanics/Mining.md), [Armor](../mechanics/Armor.md), and [Durability](../mechanics/Durability.md) retain the broader tool and combat rules. Haste does not cancel Mining Fatigue. [Effect definitions][s23] · [Level-scaled modifiers][s24] · [Active modifier application][s25] · [Mining effects and remaining penalties][s26] · [Resistance calculation][s27] · [Jump application][s28] · [Regeneration][s29] · [Effect pulse timing][s30]

Repeated applications of the same effect do not add their levels together. The effect update path compares amplifier and remaining duration; use separate Beacons for different primaries rather than expecting two Speed I Beacons to make Speed II. [Effect replacement and duration update][s31]

## Coverage, interruption, and moving the block

Beacon coverage is an **axis-aligned box**, not a sphere. It starts with the Beacon block's bounds, expands by the table's 20–50 blocks in all directions, then extends upward by the world's full height. Thus horizontal and downward coverage are bounded, while upward coverage reaches through the playable column. The query uses player bounding boxes, excludes spectators, and does not check intervening walls or line of sight. Ordinary mobs do not receive Beacon effects from this callback. [Effect query][s32] · [Default entity-query filter][s33] · [Spectator exclusion][s34]

Players receive a fresh duration on each valid 80-tick pulse, so leaving range or blocking the beam stops later refreshes rather than instantly removing an already applied effect. Reducing the pyramid changes range and duration, and removes level-II/secondary application below four levels. **The stored primary is not automatically cleared or rechecked against its original unlock tier**: an already chosen Strength can still be applied by the checked callback while a smaller nonzero base remains active. The tier table describes normal menu selection, not an additional test in that application method. [Refresh requirements][s35] · [Stored primary application][s36] · [Saved selections][s37]

Mine the Beacon to recover **one Beacon**, without Silk Touch or a correct-tool tier. It has **hardness 3, blast resistance 3**, and no ordinary pickaxe/axe/shovel/hoe mining-tag entry. Its loot copies a custom name, but does **not** preserve the selected powers or base level in the item. Rebuild and configure it after moving it. The block emits **light level 15 even while inactive**, has a full-cube collision shape, and has no facing or waterlogged state. [Beacon loot][s38] · [Properties][s39] · [Pickaxe tag][s40] · [Axe tag][s41] · [Shovel tag][s42] · [Hoe tag][s43] · [Harvest eligibility][s44] · [Default placement and collision][s45]

## Sources and verification

Source-reviewed on **2026-10-02** at `b6b5f733b316cef6852866924e2f11f12b0c4f5c`. The registered block/entity ticker, normal menu and server payment path, exact base/payment tags, recipe, loot, geometry, and active effect consumers were checked. The [Nether Star recipe](../items/NetherStar.md#crafting-a-beacon) remains the crafting owner. No in-game base, menu, beam, effect, mining, or renderer test was run. Data packs can alter tags and recipes; server timing and existing status effects affect observations. [Checked crafting recipe][s46] · [Block entity registration][s47]

Related: [Beacon item](../items/Beacon.md) · [Nether Star](../items/NetherStar.md) · [Glass and Panes](GlassAndPanes.md) · [Conduit](Conduit.md) · [Blocks](Blocks.md)

[s1]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/Blocks.java#L2640-L2650
[s2]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/BeaconBlock.java#L36-L55
[s3]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/resources/data/minecraft/tags/block/beacon_base_blocks.json
[s4]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/entity/BeaconBlockEntity.java#L203-L229
[s5]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/entity/BeaconBlockEntity.java#L203-L229
[s6]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/entity/BeaconBlockEntity.java#L52-L61
[s7]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/client/gui/screens/inventory/BeaconScreen.java#L73-L109
[s8]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/entity/BeaconBlockEntity.java#L237-L259
[s9]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/client/gui/screens/inventory/BeaconScreen.java#L229-L233
[s10]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/entity/BeaconBlockEntity.java#L125-L170
[s11]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/entity/BeaconBlockEntity.java#L143-L187
[s12]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/entity/BeaconBlockEntity.java#L237-L250
[s13]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/resources/data/minecraft/tags/item/beacon_payment_items.json
[s14]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/inventory/BeaconMenu.java#L25-L36
[s15]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/inventory/BeaconMenu.java#L144-L155
[s16]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/client/gui/screens/inventory/BeaconScreen.java#L168-L184
[s17]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L746-L755
[s18]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/client/gui/screens/inventory/BeaconScreen.java#L91-L111
[s19]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/client/gui/screens/inventory/BeaconScreen.java#L303-L323
[s20]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/entity/BeaconBlockEntity.java#L237-L259
[s21]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/inventory/BeaconMenu.java#L25-L36
[s22]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/inventory/BeaconMenu.java#L57-L66
[s23]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/effect/MobEffects.java#L19-L59
[s24]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/effect/MobEffect.java#L201-L205
[s25]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1046-L1069
[s26]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/entity/player/Player.java#L623-L650
[s27]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1789-L1807
[s28]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2204-L2225
[s29]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/effect/RegenerationMobEffect.java#L12-L25
[s30]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L214-L228
[s31]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L128-L148
[s32]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/entity/BeaconBlockEntity.java#L237-L259
[s33]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/EntityGetter.java#L50-L52
[s34]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/entity/EntitySelector.java#L15-L16
[s35]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/entity/BeaconBlockEntity.java#L172-L181
[s36]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/entity/BeaconBlockEntity.java#L237-L259
[s37]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/entity/BeaconBlockEntity.java#L291-L309
[s38]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/resources/data/minecraft/loot_table/blocks/beacon.json
[s39]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/Blocks.java#L2640-L2650
[s40]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[s41]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[s42]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/resources/data/minecraft/tags/block/mineable/shovel.json
[s43]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/resources/data/minecraft/tags/block/mineable/hoe.json
[s44]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[s45]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L326
[s46]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/resources/data/minecraft/recipe/crafting/beacon.json
[s47]: https://github.com/HungLo2020/MattMC/blob/b6b5f733b316cef6852866924e2f11f12b0c4f5c/src/main/java/net/minecraft/world/level/block/entity/BlockEntityType.java#L109-L109
