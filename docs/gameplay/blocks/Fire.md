# Fire and Soul Fire

**Fire** and **Soul Fire** are placed hazards and light sources. Create them with an ignition item; neither has an ordinary inventory block item to collect. Breaking either gives no item, including with Silk Touch. For a controllable cooking block, see [Campfires](Campfires.md). [Block registrations][blocks] · [Checked item registry][items] · [Fire loot][fire-loot] · [Soul Fire loot][soul-loot]

## Comparing the fires

| Placed block | Light level | Contact damage request | Continuing behavior |
| --- | ---: | ---: | --- |
| <span id="fire">Fire, `minecraft:fire`</span> | 15 | 1 health point | Can spread, consume flammable blocks and burn out |
| <span id="soul-fire">Soul Fire, `minecraft:soul_fire`</span> | 10 | 2 health points | Requires Soul Sand or Soul Soil below; does not spread or age out |

Both break instantly, are replaceable, and have no collision that stops movement. Their visible flames do not make a solid barrier. The contact numbers are requests to the damage system, **not damage-per-second promises**; immunity and normal damage handling still apply. [Properties][blocks] · [Ordinary damage value][fire-value] · [Soul damage and support][soul] · [Contact callback][contact]

## Lighting and finding fire

Use **Flint and Steel** or a **Fire Charge** on a block face with air beside it. That adjacent position must support the selected fire or complete an eligible Nether portal. The block **directly below the new fire** selects the type: Soul Sand or Soul Soil gives Soul Fire; otherwise the placement helper selects ordinary Fire. Successful ordinary Survival use costs one Flint and Steel durability or one Fire Charge. [Flint and Steel use][flint] · [Fire Charge use][charge] · [Type selection][selection] · [Placement check][placement] · [Soul support tag][soul-tag]

The ignition tools have ordinary recipes: **1 Flint + 1 Iron Ingot → 1 Flint and Steel**, or **1 Blaze Powder + 1 Gunpowder + 1 Coal or Charcoal → 3 Fire Charges**, both shapeless. These craft the tools, not a Fire block item. [Flint and Steel recipe][flint-recipe] · [Fire Charge recipe][charge-recipe]

The normal Nether generator includes Nether Wastes and Soul Sand Valley; their feature lists include Fire and Soul Fire patches. The placed features feed active patch/simple-block placement: their targets are **air over Netherrack** for ordinary Fire and **air over Soul Soil** for generated Soul Fire. These are terrain-dependent attempts, not a guaranteed fire count or a restriction against manually lighting Soul Sand. [Normal preset][preset] · [Nether biome selection][nether-biomes] · [Wastes features][wastes] · [Valley features][valley] · [Fire placement][fire-placement] · [Soul placement][soul-placement] · [Fire patch][fire-patch] · [Soul patch][soul-patch] · [Active feature caller][generation] · [Placement dispatch][placed] · [Configured dispatch][configured] · [Registered patch][patch-registry] · [Registered block feature][simple-registry] · [Patch caller][patch] · [Block placement][simple]

Lava also has a chance to start fire around suitable flammable surroundings, subject to the fire-tick rules below. See [Water and Lava](WaterAndLava.md) for fluid handling. [Lava ignition][lava]

## Support, persistence and spread

Ordinary Fire can survive on a sturdy upper face or beside a flammable neighbor. Its shape changes with the neighboring blocks. A support update removes fire that can no longer survive. On an ordinary nonflammable floor it can still burn out; support alone is not a permanent-fire guarantee. [Support and placement][fire-support] · [Neighbor update][fire-update] · [Burnout][fire-tick]

**Netherrack and Magma Blocks support persistent ordinary Fire** in the bundled Overworld, Nether and End dimension types. The End also accepts Bedrock. These dimension-specific infinite-burn tags skip ordinary rain/burnout removal; they do not ignite the floor automatically or stop nearby flammable blocks from catching fire. The [Netherrack guide](Netherrack.md#persistent-fire) covers obtaining that support. [Support tags][infiniburn-overworld] · [Nether tag][infiniburn-nether] · [End tag][infiniburn-end] · [Overworld assignment][overworld] · [Nether assignment][nether] · [End assignment][end] · [Active checks][fire-tick]

Ordinary Fire checks adjacent blocks for burning and searches nearby air for spreading fire, including up to four blocks above its own position. The chance varies with the fuel, age, difficulty, rain and biome conditions; leave clearance around flammable construction rather than treating a one-block gap as containment. A waterlogged block has zero burn/ignition odds in these checks. Burning [TNT](TNT.md#priming-routes) can prime it. [Spread search][spread] · [Wet blocks and burning][burn] · [Registered flammable examples][flammability] · [Active flammability setup][bootstrap]

**Soul Fire has no spreading, ageing or rain-extinguish tick.** It stays while valid ground remains, and a support update replaces it with air when the block below stops being an accepted Soul Fire base. This does not make its contact safe. [Soul lifecycle][soul] · [Shared tick defaults][tick-defaults]

## Extinguishing and fire rules

- **Break the flame itself:** both fires break instantly and drop nothing. This leaves their floor in place. [Properties][blocks] · [Player destruction][player-break] · [Empty Fire loot][fire-loot] · [Empty Soul Fire loot][soul-loot]
- **Water reaching the position replaces the fire:** neither fire has a waterlogged form. Water flow admits these non-solid blocks and replaces them. A thrown water bottle also extinguishes Fire-tag blocks in its checked impact neighborhood; that tag includes both fires. [Fluid admission][fluid-admission] · [Non-solid calculation][solid] · [Fluid replacement][fluid-replacement] · [Water-bottle impact][bottle] · [Dousing][dowse] · [Fire tag][fire-tag]
- **Rain can put ordinary Fire out** when the flame or a horizontally adjacent position is exposed to rain, unless the floor is in the dimension's infinite-burn tag. This removal is probabilistic and part of the ordinary fire tick. Rain is not Soul Fire's removal mechanism. [Rain and burnout][fire-tick] · [Rain neighborhood][rain] · [Soul lifecycle][soul]
- **`doFireTick` defaults to true; `allowFireTicksAwayFromPlayer` defaults to false.** Ordinary fire ageing, rain removal, burning and spread run only when the first is enabled and either the second is enabled or the server's nearby-player check passes. These rules also gate lava's fire creation. Disabling fire ticking does not disable direct tool ignition, contact damage, player extinguishing or support updates. [Rule defaults][rules] · [Tick gate][fire-tick] · [Lava gate][lava] · [Direct ignition][flint] · [Contact][contact] · [Support update][fire-update]

Keep [the Nether's water-bucket restriction](../dimensions/Nether.md#hazards-to-plan-around) in mind before relying on poured water there. Water's entity-contact effect and rain can extinguish a burning entity; removing the flame block is a separate action. [Water contact][water-contact] · [Rain on entities][entity-rain]

## Contact and portal use

Both fires clear freezing and invoke the shared ignition effect. For a non-fire-immune entity, that effect can start an eight-second burning duration. Soul Fire's direct contact request is twice the ordinary Fire value, but subsequent burning uses the shared entity fire handling. **Fire Resistance** blocks the tagged fire-damage path for living entities. The player rule **`fireDamage`**, default **true**, also makes players immune to tagged fire damage when disabled; it does not control block spread. [Contact and ignition][contact] · [Damage values][fire-value] · [Soul damage][soul] · [Fire damage tag][fire-damage] · [Resistance check][resistance] · [Player damage-rule check][player-fire] · [Rule default][fire-damage-rule] · [Entity burning][entity-burning]

Fire placement can activate a valid Obsidian portal in the Overworld or Nether. Use [the Nether portal guide](../dimensions/Nether.md#building-and-using-a-portal) for the frame, destination and travel rules. Lighting Campfires or Candles and directly priming TNT are separate item/block interactions; see [Campfires](Campfires.md), [Candles](Candles.md) and [TNT](TNT.md#priming-routes). [Portal activation][portal] · [Ignition dispatch][flint]

Related: [Soul Sand, Soul Soil and Magma Blocks](SoulSandSoilAndMagma.md#farming-fire-and-construction-uses) · [Light sources and fire](catalog/lighting.md) · [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `b823010659d7b5095ed021b1c99cf85627e2082a`. Checked registrations, empty fire loot, ignition recipes and callers, active Nether patch placement, support and spread, fluid replacement, contact and fire rules. The item registry scan found no ordinary Fire or Soul Fire inventory block item. No in-game ignition, generation, spread, damage, water or portal test was run. Data packs, server permissions and game rules can change relevant behavior.

[blocks]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Blocks.java#L1203-L1226
[items]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L1-L2827
[fire-loot]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/loot_table/blocks/fire.json#L1-L4
[soul-loot]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/loot_table/blocks/soul_fire.json#L1-L4
[fire-value]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/FireBlock.java#L63-L68
[soul]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/SoulFireBlock.java#L13-L52
[contact]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/BaseFireBlock.java#L132-L154
[flint]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/FlintAndSteelItem.java#L25-L56
[charge]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/FireChargeItem.java#L29-L55
[selection]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/BaseFireBlock.java#L44-L50
[placement]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/BaseFireBlock.java#L190-L218
[soul-tag]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/tags/block/soul_fire_base_blocks.json#L1-L6
[flint-recipe]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/recipe/crafting/flint_and_steel.json#L1-L12
[charge-recipe]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/recipe/crafting/fire_charge.json#L1-L16
[preset]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/worldgen/world_preset/normal.json#L24-L33
[nether-biomes]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L55-L72
[wastes]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/worldgen/biome/nether_wastes.json#L35-L51
[valley]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/worldgen/biome/soul_sand_valley.json#L43-L59
[fire-placement]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/worldgen/placed_feature/patch_fire.json#L1-L31
[soul-placement]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/worldgen/placed_feature/patch_soul_fire.json#L1-L31
[fire-patch]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/worldgen/configured_feature/patch_fire.json#L1-L52
[soul-patch]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/worldgen/configured_feature/patch_soul_fire.json#L1-L44
[generation]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L355-L380
[placed]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/placement/PlacedFeature.java#L35-L60
[configured]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/feature/ConfiguredFeature.java#L17-L26
[patch-registry]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L67-L67
[simple-registry]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java#L120-L120
[patch]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/feature/RandomPatchFeature.java#L15-L37
[simple]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/feature/SimpleBlockFeature.java#L18-L42
[lava]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/material/LavaFluid.java#L78-L116
[fire-support]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/FireBlock.java#L112-L135
[fire-update]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/FireBlock.java#L86-L100
[fire-tick]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/FireBlock.java#L137-L181
[infiniburn-overworld]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/tags/block/infiniburn_overworld.json#L1-L6
[infiniburn-nether]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/tags/block/infiniburn_nether.json#L1-L5
[infiniburn-end]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/tags/block/infiniburn_end.json#L1-L6
[overworld]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/dimension_type/overworld.json#L10-L13
[nether]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/dimension_type/the_nether.json#L10-L13
[end]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/dimension_type/the_end.json#L10-L13
[spread]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/FireBlock.java#L174-L213
[burn]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/FireBlock.java#L223-L251
[flammability]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/FireBlock.java#L308-L321
[bootstrap]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/Bootstrap.java#L42-L58
[tick-defaults]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L341-L345
[player-break]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/BaseFireBlock.java#L181-L188
[fluid-admission]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L401-L427
[solid]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L482-L508
[fluid-replacement]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L268-L277
[bottle]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/projectile/AbstractThrownPotion.java#L49-L67
[dowse]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/projectile/AbstractThrownPotion.java#L110-L121
[fire-tag]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/tags/block/fire.json#L1-L6
[rain]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/FireBlock.java#L215-L221
[rules]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/GameRules.java#L41-L46
[water-contact]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/material/WaterFluid.java#L93-L96
[entity-rain]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/Entity.java#L847-L865
[fire-damage]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/tags/damage_type/is_fire.json#L1-L11
[player-fire]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/player/Player.java#L706-L719
[fire-damage-rule]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/GameRules.java#L158-L160
[resistance]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1146-L1154
[entity-burning]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/Entity.java#L508-L523
[portal]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/BaseFireBlock.java#L156-L175
