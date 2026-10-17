# Haste and Mining Fatigue

**Haste improves mining speed and ordinary melee attack recharge; Mining Fatigue reduces both. They can coexist and do not cancel each other.** Neither effect changes whether your tool qualifies for a block's drops. Use [Mining](../mechanics/Mining.md) for tool selection and [mining enchantments](../enchanting/MiningEnchantments.md) for Efficiency, Fortune, and Silk Touch. [Mining and harvest checks][mining] · [Attack-speed modifiers][registration]

Levels are the stored amplifier plus one: amplifier `0` means I and `2` means III. The stored amplifier is clamped to **0–255**, allowing levels I–256, although ordinary sources below use much lower levels. Times assume **20 game ticks per second**. [Level storage][level-storage]

## Haste

Effect ID: `minecraft:haste`.

Haste multiplies the mining-speed result at its stage by **`1 + 0.2 × level`**: **1.2 at I** or **1.4 at II**. The consumer keeps scaling at higher levels; II is a normal Beacon-source limit, not a cap in the mining formula. Haste also multiplies the attack-speed attribute by **`1 + 0.1 × level`**, giving **1.1 at I** or **1.2 at II** before the final attribute clamp. [Mining consumer][mining] · [Registration][registration] · [Level scaling][level-scaling] · [Attribute calculation][attribute-order]

Select **Haste I** at an active [Beacon](../blocks/Beacon.md#select-and-pay-for-effects) with at least one complete base level. With four complete levels, choose the matching secondary upgrade for **Haste II**. A valid Beacon refreshes effects every **80 ticks**; fresh duration is **220, 260, 300, or 340 ticks** for one through four base levels. Leaving coverage or disabling the Beacon stops later refreshes, while the received effect can remain until its duration ends. The Beacon guide owns construction, payment, range, and interruption details. [Primary choices][beacon-tiers] · [Normal menu tiers][beacon-menu] · [Active refresh][beacon-tick] · [Level and duration][beacon-grant] · [Duration ticking][effect-tick]

**Conduit Power shares Haste's mining bonus, not its attack-speed modifier.** With both present, mining uses the higher level once, rather than adding their bonuses: Haste II plus Conduit Power I still gives **1.4** at that stage. See [Conduit Power](WaterAndFireEffects.md#conduit-power) for its separate breathing and lighting benefits. [Shared helper][dig-helper] · [Haste modifier][registration] · [Plain Conduit registration][conduit-registration]

## Mining Fatigue

Effect ID: `minecraft:mining_fatigue`.

The mining penalty has these exact steps. Level III is **0.0027**, not 0.027. Higher levels beyond IV do not further reduce this particular mining factor. [Mining switch][mining]

| Level | Mining-speed factor | Speed left at this stage | Attack-speed factor before the final clamp |
| --- | ---: | ---: | ---: |
| I | 0.3 | 30% | 0.9 |
| II | 0.09 | 9% | 0.8 |
| III | 0.0027 | 0.27% | 0.7 |
| IV | 0.00081 | 0.081% | 0.6 |
| V and above | 0.00081 | 0.081% | Continues as `1 − 0.1 × level` |

The attack-speed reduction continues past IV because it uses a separate level-scaled attribute modifier. These values are factors on intermediate calculations, not fixed block-breaking times or guaranteed damage reductions. [Fatigue registration][registration] · [Level scaling][level-scaling] · [Attribute calculation][attribute-order]

An [Elder Guardian](../mobs/ElderGuardian.md#mining-fatigue) can apply **Mining Fatigue III for 6,000 ticks / five minutes** on its **1,200-tick** pulse. Its entity-offset timer does not give a new arrival a guaranteed minute of safety. Eligible Survival/Adventure players must be non-allied and **less than 50 blocks** away; walls and being on dry land do not block the check. A pulse considers players with no Fatigue, a weaker level, or a finite effect with less than 1,200 ticks left. The linked mob guide owns the full encounter and refresh conditions. [Active Elder callback][elder] · [Player and refresh filters][elder-helper] · [Survival/Adventure definition][survival-modes] · [Finite duration test][effect-duration]

Killing an Elder stops its future pulses; it does not clear an effect already received. Use existing openings during the encounter, then wait for expiry or use the [removal options below](#refresh-and-removal). [Elder grant][elder] · [Independent effect timer][effect-tick]

## Combining mining modifiers

The ordinary player calculation applies these stages in order:

1. Start with the selected main-hand item's speed against the block
2. Add mining efficiency only if that initial item speed is **greater than 1**
3. Multiply by the **higher Haste/Conduit Power level's** digging bonus, if either is present
4. Multiply by **Mining Fatigue's** factor, if present
5. Multiply by the **block-break-speed attribute**
6. With the eyes in water, multiply by the **submerged-mining-speed attribute**; its default is **0.2**
7. While off the ground, divide by **5**

[Active stages][mining] · [Attribute defaults][mining-attributes] · [Submerged attribute][submerged-attribute]

For example, **Haste II and Mining Fatigue III together multiply the result at their two stages by `1.4 × 0.0027 = 0.00378`**, or **0.378%** of the incoming speed. A Conduit does not remove that Fatigue penalty or automatically remove the water/off-ground penalties. [Combined calculation][mining] · [Haste/Conduit selection][dig-helper]

The ordinary block-progress calculation still divides by block hardness and by **30 with a qualifying tool**, or **100 without one**; hardness `−1` returns no progress. Tick thresholds and separate instant-build handling also matter. An effect factor alone therefore cannot promise that a block takes a particular number of seconds, becomes instantly mineable, or drops its expected item. [Block progress][block-progress] · [Server break path and instant-build branch][break-dispatch]

## Attack recharge and swing timing

For ordinary player melee, attack recharge uses the final attack-speed attribute. Haste and Fatigue multiply it separately, after additive attribute changes: **Haste II plus Fatigue III contributes `1.2 × 0.7 = 0.84`**, not cancellation. Final attack speed is clamped to **0–1,024**. At command-supplied Fatigue X or above, its factor is nonpositive; with otherwise ordinary positive attack speed, the result clamps to zero and normal attack charge does not replenish. This does not itself prohibit making an attack. [Distinct effect modifiers][registration] · [Attribute order][attribute-order] · [Attribute range][attack-range] · [Clamp][attribute-clamp] · [Recharge consumer][attack-charge]

The ordinary recharge reference is **20 ÷ attack speed game ticks**. Haste does not directly add attack damage, but charging sooner can affect damage because the melee hit reads charge. [Combat](../mechanics/Combat.md#time-your-melee-attacks) owns that timing, damage scaling, and weapon-specific cautions. Do not treat the modifier as a guarantee about every mob's attack interval or every weapon's firing rate. [Melee hit and charge][attack-hit]

The inherited **arm-swing timing calculation** is separate: Haste I/II gives **5/4 ticks**, while Fatigue I/II/III/IV alone gives **8/10/12/14 ticks**. Haste or Conduit Power takes precedence in this calculation even if Fatigue is present. The swing timer therefore does not establish that Fatigue is gone or melee charge is full. The simple formula can reach zero or negative values at high command-supplied digging levels. These are checked timing/state values, not a verified description of visible animation in the current renderer. [Swing selection][swing] · [Player update call][swing-caller] · [Swing-state update][swing-update]

## Refresh and removal

Repeated applications of one effect do not add their levels together. A stronger effect takes precedence; an equal-level longer application extends the remaining duration. A weaker effect can remain hidden and resume when the stronger effect ends, but its finite timer keeps counting down. [Effect replacement][effect-update] · [Hidden-effect ticking][effect-tick]

- **Milk** clears Haste and Mining Fatigue together with other effects, including underwater breathing protection. Drink from a safe position, and remember that a living Elder or active Beacon can apply its effect again. [Milk item][milk-item] · [Milk consumption][milk] · [Clear callback][clear-callback] · [Removal][clear-all]
- **Axolotl support** removes Mining Fatigue specifically. Finish the target an [Axolotl](../mobs/Axolotl.md#hunting-and-player-support) was attacking and stay nearby: its attack-end callback checks the dead target's last damage source for player credit and the player's presence inside its bounding box expanded by 20 blocks. Merely standing beside it is insufficient. [Active brain tick][axolotl-tick] · [Installed callback][axolotl-ai] · [Callback execution][attack-end] · [Support and Fatigue removal][axolotl-support]
- **Honey Bottles remove Poison only**, so they do not clear either effect. A qualifying [Totem of Undying activation](../items/TotemOfUndying.md#activation) clears prior effects before applying its own protections. [Honey definition][honey] · [Totem activation gate][totem-gate] · [Totem clearing][totem]
- **Ordinary death followed by respawning** starts a replacement player without these active effects. The alive return from the End uses a different copy path that preserves active effects; changing dimension is not a general clearing method. [Respawn dispatch][respawn-dispatch] · [Replacement player][respawn-create] · [Effect-copy condition][respawn-copy]

For command-assisted testing, see [shared effect commands](CombatEffects.md#duration-clearing-and-commands). The ordinary bundled potion list and brewing mix list contain **no Haste or Mining Fatigue potion/recipe**. The registered **Serene Salad, Seething Stew, and Primordial Soup** also use ordinary food consumption without either effect; do not assume their upstream versions supply a mining buff here. [Potion types][potions] · [Mixes and bootstrap][brewing] · [Meal registrations][meals] · [Default food routing][food-route] · [Default consumption][default-food]

Applying an effect to a creature is also subject to its own rules. The base acceptance checks do not single out Haste or Mining Fatigue, but the **Wither and Ender Dragon reject ordinary effect application entirely**. An effect modifier only changes an attribute the recipient actually has; it does not rewrite a mob's attack goal. [Base checks][effect-acceptance] · [Wither override][wither-override] · [Dragon override][dragon-override] · [Attribute-presence gate][modifier-install]

## Sources and verification

Source-reviewed on **2026-10-02** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`; the inspected source tree is unchanged at wiki baseline `bf1aa1dd57ba225e4392c4c2eba8550c4cd997bd`. The review followed the active mining, block-progress, attack-charge, animation, source-refresh, and clearing consumers. A scan of **`src/main/java` and `src/main/resources/data`**, including imported mob code, effect aliases, item registrations, recipes, and loot, found no additional direct Haste or Mining Fatigue grant. This is a bundled-source/resource scope, not a claim about added data packs, command-created item components, or other releases.

No in-game mining, Beacon, Elder pulse, combat, animation, consumption, or removal test was run. Source multipliers and normal-TPS conversions are not measured gameplay timings.

Related: [Status effects](Effects.md) · [Beacon](../blocks/Beacon.md) · [Elder Guardian](../mobs/ElderGuardian.md) · [Conduit](../blocks/Conduit.md) · [Mining](../mechanics/Mining.md)

[mining]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L623-L657
[registration]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffects.java#L33-L44
[level-storage]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L25-L76
[level-scaling]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffect.java#L201-L205
[attribute-order]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/attributes/AttributeInstance.java#L150-L167
[beacon-tiers]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/BeaconBlockEntity.java#L52-L61
[beacon-menu]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/screens/inventory/BeaconScreen.java#L73-L111
[beacon-tick]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/BeaconBlockEntity.java#L172-L182
[beacon-grant]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/BeaconBlockEntity.java#L237-L259
[effect-tick]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L214-L261
[dig-helper]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectUtil.java#L25-L40
[conduit-registration]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffects.java#L106-L107
[elder]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/ElderGuardian.java#L63-L78
[elder-helper]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectUtil.java#L47-L63
[survival-modes]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/GameType.java#L90-L92
[effect-duration]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L172-L178
[mining-attributes]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L18-L20
[submerged-attribute]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L87-L89
[block-progress]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L347-L355
[break-dispatch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L152-L208
[attack-range]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L15-L17
[attribute-clamp]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/attributes/RangedAttribute.java#L30-L33
[attack-charge]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L1723-L1735
[attack-hit]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L971-L1030
[swing]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1887-L1903
[swing-update]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2032-L2046
[effect-update]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L122-L150
[milk-item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1531-L1533
[milk]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L64
[clear-callback]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/consume_effects/ClearAllStatusEffectsConsumeEffect.java#L21-L24
[clear-all]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L943-L954
[axolotl-tick]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/animal/axolotl/Axolotl.java#L332-L339
[axolotl-ai]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/animal/axolotl/AxolotlAi.java#L71-L82
[attack-end]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/behavior/StopAttackingIfTargetInvalid.java#L25-L47
[axolotl-support]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/animal/axolotl/Axolotl.java#L434-L459
[honey]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L16-L20
[totem-gate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1349-L1378
[totem]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/DeathProtection.java#L25-L43
[potions]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/Potions.java#L11-L80
[brewing]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L127-L191
[meals]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1685-L1688
[food-route]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Item.java#L366-L371
[default-food]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L13-L15
[swing-caller]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L462-L475
[respawn-dispatch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1766-L1785
[respawn-create]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/players/PlayerList.java#L430-L437
[respawn-copy]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1553-L1583
[effect-acceptance]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L983-L1012
[wither-override]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/boss/wither/WitherBoss.java#L488-L491
[dragon-override]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/boss/enderdragon/EnderDragon.java#L818-L821
[modifier-install]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffect.java#L168-L175
