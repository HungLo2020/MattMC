# TNT

TNT (`minecraft:tnt`) is a recoverable block until primed, then becomes a moving explosive with a ticking fuse. Prepare the escape route before igniting it: an explosion can set off nearby TNT with a much shorter fuse. The active block, inventory item and primed entity are separately registered. [Block registration][block-registration]; [item registration][item-registration]; [entity registration][entity-registration].

## Obtaining

### Crafting

Craft **one TNT** from **five Gunpowder** and **four Sand or Red Sand** in a crafting table. Put Gunpowder in the four corners and center, and sand in the other four slots. Each sand slot accepts either named sand item, so the two kinds can be mixed; the recipe does not accept arbitrary sand-tag members. [Recipe][crafting].

### Recovery and exploration

- Ordinary TNT starts with `unstable=false`. Breaking it in Survival does not prime it, and its loot table returns one TNT without a tool, Silk Touch or Fortune requirement. Its registered hardness and blast resistance are both **0**. Normal block-item spawning still requires `doTileDrops`. [Default state and breaking][power-unstable]; [loot][block-loot]; [registration][block-registration]; [property meaning][block-properties]; [drop gate][block-drops].
- A Desert Pyramid's trap contains a **3×3 layer of TNT** below the central Stone Pressure Plate. Approach without activating the plate, remove the trigger, then recover the unprimed blocks. The pyramid structure is wired to this piece and included in its structure set. [Trap layout][pyramid-trap]; [piece wiring][pyramid-wiring]; [structure placement][pyramid-set].
- Buried Treasure and Shipwreck supply chests have TNT loot entries producing **1–2 per selected entry**. These are weighted loot pools, so that is neither a guaranteed chest reward nor a cap on a whole chest. [Buried Treasure loot][buried-loot] and [chest wiring][buried-wiring]; [Shipwreck supply loot][shipwreck-loot], [marker mapping][shipwreck-wiring] and [marker handling][shipwreck-marker].
- Creative's Redstone Blocks selection includes TNT. [Creative entry][creative].

## Priming routes

Successful ordinary priming removes the placed block and creates a primed entity. Its initial fuse is **80 game ticks**, or **4 seconds at 20 ticks per second**. The exception below is TNT ignited by another explosion. [Priming helper][chain]; [fuse and countdown][entity-fuse].

| Trigger | What happens |
| --- | --- |
| Use Flint and Steel or a Fire Charge directly on TNT | Primes it; successful Survival use costs one durability or one Fire Charge. Use the normal interaction: secondary use while holding an item skips the block's direct interaction and can instead use the ignition item on the adjacent face. [Direct use][hand-projectile]; [interaction dispatch][interaction-dispatch] |
| Redstone power | A neighbor signal can prime TNT immediately when placed, or on a neighbor update. Disconnect the circuit before placing or recovering TNT. TNT itself is registered as a non-conductor. [Power checks][power-unstable]; [registration][block-registration] |
| Spreading fire, including fire started by lava | Ordinary fire's burn check can consume the TNT and call its priming helper. Fire spread is probabilistic, not an exact fuse trigger; fire-tick rules also apply. [Burning][fire]; [flammability][fire-flammability]; [fire tick][fire-rules]; [lava fire creation][lava-fire] |
| A burning projectile hits the block | The projectile must be on fire and pass its interaction-permission check. Merely striking TNT with an unlit projectile is insufficient. [Projectile ignition][hand-projectile] |
| Another block-destroying explosion | Removed TNT becomes primed with a random **10–29 game ticks** remaining: **0.5–1.45 seconds at 20 ticks per second**. TNT does not drop itself from an explosion. Trigger-only explosions do not use this destruction route. [Shortened fuse and no explosion drop][chain]; [drop override][hand-projectile]; [explosion dispatch][blast-blocks] |
| A Dispenser containing TNT | Consumes one TNT and creates an already-primed entity in the block space in front, with the ordinary 80-tick fuse. It does not place an unprimed TNT block. [TNT dispensing][dispenser-tnt]; [fuse][entity-fuse] |
| A Dispenser containing Flint and Steel faces TNT | Primes and removes that placed TNT, using one durability on success. [Flint and Steel dispensing][dispenser-flint] |
| A player breaks `unstable=true` TNT | Non-Creative breaking primes it; Creative breaking does not. This is a special block state, not the normal placed state, and its loot entry requires `unstable=false`. [State and breaking][power-unstable]; [loot][block-loot] |

A Dispenser loaded with Fire Charges launches Small Fireballs. These ignite themselves before processing a hit and call the block projectile callback; their block-hit handling can also place fire nearby. [Dispenser registration][dispenser-firecharge]; [projectile creation][firecharge-projectile]; [burning before impact][fireball-burning]; [block impact][fireball-hit].

## Fuse, movement and water

Once primed, TNT has a small random sideways motion and an upward kick. Gravity pulls it down, movement loses speed, and landing makes it bounce and slow. Fluids can push it; nearby explosions can also push primed TNT. Its final blast position can therefore differ from the original block position. [Movement][entity-fuse]; [explosion knockback][blast-entities].

**Water does not stop the fuse.** The countdown continues while TNT updates its water state. Water has explosion resistance **100**, which feeds into the blast's block-resistance calculation, so immersion can strongly suppress terrain damage. It is not personal blast protection: entity damage and knockback are calculated separately, and the exposure ray checks ignore fluids. Do not approach primed TNT just because it is underwater. [Countdown][entity-fuse]; [water resistance][water-resistance]; [resistance calculation][blast-resistance]; [entity effects][blast-entities]; [exposure checks][blast-exposure].

The primed entity does not accept ordinary damage, so hitting it is not a way to turn it back into a collectible block. [Damage handling][entity-damage].

## Explosion and drops

Ordinary TNT has explosion power **4** and does not request fire creation. Power is not a fixed block-clearing radius: rays start with randomized strength and lose strength to blocks and fluids. Damage to entities also depends on distance and exposure. Plan clearance around the possible moving blast location, not a promised crater size. [Explosion settings][entity-fuse]; [terrain calculation][blast-rays]; [entity calculation][blast-entities].

`tntExplosionDropDecay` defaults to **false**. Setting it to true supplies the explosion radius to destroyed blocks' loot evaluation, enabling their explosion-sensitive decay conditions. False does **not** promise that every broken block yields a recoverable item: block-specific loot still applies, TNT itself never drops from explosions, and `doTileDrops` still gates spawned block drops. Repeated blasts can affect items left in the work area. [Default][rule-decay]; [explosion category][explosion-rules]; [loot evaluation][blast-blocks]; [TNT override][hand-projectile]; [drop spawning][block-drops]; [dropped-item damage][item-damage].

## Rules and permissions

- **`tntExplodes`**, default **true**, gates normal priming, explosion chaining, TNT dispensing, and the final blast. If it is false when a primed entity's fuse expires, the entity disappears without exploding. Direct ignition reports that TNT is disabled. [Default][rule-tnt]; [priming][chain]; [direct use][hand-projectile]; [dispensing][dispenser-tnt]; [expiry][entity-fuse].
- Disabling that rule is **not a preservation guarantee**: spreading fire removes or replaces the block before asking it to prime, and a destructive explosion removes it before the chain-priming callback. Both can therefore destroy TNT without creating a new primed entity. [Fire ordering][fire]; [explosion ordering][blast-blocks].
- **`mobGriefing`** affects permission for a projectile owned by a non-player entity. Player-owned projectiles consult their owner's interaction permission, including server spawn protection and world borders; ownerless projectiles pass this particular ownership check. Once primed, TNT uses the TNT explosion category, whose terrain destruction is not disabled by `mobGriefing`. [Projectile permission][projectile-permission]; [player permission][player-permission]; [protected positions][level-permission]; [TNT category][explosion-rules].
- **`doFireTick`** defaults to true; **`allowFireTicksAwayFromPlayer`** defaults to false. They govern the spreading-fire route, not direct ignition, redstone or the running TNT fuse. [Defaults][rule-fire]; [fire tick gate][fire-rules].

## Practical handling

Build and test the control circuit before adding TNT. Keep spare TNT away from the intended blast, because a chain reaction gives far less time to move. Use a remote control such as a [Lever](Lever.md#switching) and [Redstone Dust](RedstoneDust.md#signal-strength), and check the [Dispenser and Dropper guide](DispenserAndDropper.md#facing-loading-and-activation) before automating delivery. These precautions follow the priming, fuse and movement rules above; no layout or extraction yield is guaranteed by this guide.

## Related pages

- [TNT item](../items/TNT.md): inventory use and Minecart with TNT crafting
- [Trapped Chest](TrappedChest.md#crafting-and-obtaining): opening signals and the optional Mansion TNT room
- [Stone Pressure Plate](PressurePlates.md#stone-pressure-plate): trap triggers
- [Redstone and transport catalog](catalog/redstone.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `cf1c134b3f9ff634490e448fe26c335b90f82227`. The linked active registrations, callbacks, bootstrap wiring, recipes, loot and rules support this guide. Fire and dispenser behaviors are installed during [bootstrap][bootstrap]. No in-game fuse, underwater blast, structure search or item-yield test was run. Server settings and data packs can change the defaults and bundled recipes/loot described here.

[block-registration]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L1107-L1111
[item-registration]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/item/Items.java#L1031-L1037
[entity-registration]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/EntityType.java#L1394-L1403
[crafting]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/crafting/tnt.json#L1-L20
[power-unstable]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/TntBlock.java#L42-L70
[block-loot]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/loot_table/blocks/tnt.json#L1-L30
[block-properties]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1143-L1153
[block-drops]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Block.java#L410-L416
[pyramid-trap]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/levelgen/structure/structures/DesertPyramidPiece.java#L273-L283
[pyramid-wiring]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/levelgen/structure/structures/DesertPyramidStructure.java#L25-L30
[pyramid-set]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/worldgen/structure_set/desert_pyramids.json#L1-L14
[buried-loot]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/loot_table/chests/buried_treasure.json#L49-L70
[buried-wiring]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/levelgen/structure/structures/BuriedTreasurePieces.java#L71-L73
[shipwreck-loot]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/loot_table/chests/shipwreck_supply.json#L243-L257
[shipwreck-wiring]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/levelgen/structure/structures/ShipwreckPieces.java#L69-L71
[shipwreck-marker]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/levelgen/structure/structures/ShipwreckPieces.java#L120-L126
[creative]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1342-L1357
[chain]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/TntBlock.java#L72-L96
[entity-fuse]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/item/PrimedTnt.java#L63-L137
[hand-projectile]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/TntBlock.java#L98-L140
[interaction-dispatch]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L340-L377
[fire]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/FireBlock.java#L235-L251
[fire-flammability]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/FireBlock.java#L411-L412
[fire-rules]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/FireBlock.java#L138-L181
[lava-fire]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/material/LavaFluid.java#L78-L115
[blast-blocks]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L176-L200
[dispenser-tnt]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L256-L274
[dispenser-flint]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L210-L240
[dispenser-firecharge]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L66-L79
[firecharge-projectile]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/item/FireChargeItem.java#L63-L86
[fireball-burning]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/projectile/AbstractHurtingProjectile.java#L78-L87
[fireball-hit]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/projectile/SmallFireball.java#L49-L60
[blast-entities]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/ServerExplosion.java#L169-L205
[water-resistance]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/material/WaterFluid.java#L128-L131
[blast-resistance]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/ExplosionDamageCalculator.java#L10-L36
[blast-exposure]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/ServerExplosion.java#L77-L104
[entity-damage]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/item/PrimedTnt.java#L202-L205
[blast-rays]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/ServerExplosion.java#L120-L166
[rule-decay]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/GameRules.java#L182-L190
[explosion-rules]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/server/level/ServerLevel.java#L1156-L1179
[item-damage]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/item/ItemEntity.java#L268-L292
[rule-tnt]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/GameRules.java#L214-L216
[projectile-permission]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L336-L345
[player-permission]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/server/level/ServerPlayer.java#L2009-L2012
[level-permission]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/server/level/ServerLevel.java#L819-L822
[rule-fire]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/GameRules.java#L41-L48
[bootstrap]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/server/Bootstrap.java#L42-L58
