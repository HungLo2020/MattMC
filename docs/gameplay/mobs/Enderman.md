# Enderman

The **Enderman** is a tall, teleporting mob that becomes dangerous when provoked. Avoid staring at its eyes, and prepare an escape before fighting one for [Ender Pearls](../items/EnderPearl.md). It has **40 health points (20 hearts)**, a **base attack attribute of 7 health points**, and a body **0.6 blocks wide × 2.9 blocks tall**. Actual melee damage depends on difficulty and defenses. [Behavior and attributes][enderman] · [Registered attributes][defaults] · [Size][registration] · [Player difficulty scaling][player-damage]

## Where to find it

Confirmed bundled biome entries span all three main dimensions:

- **Overworld:** plains lists endermen in groups of **1–4**
- **Nether:** Warped Forest, Nether Wastes, and Soul Sand Valley each list groups of **4**. In Warped Forest, the normal monster spawn list contains only endermen; that does not exclude visiting mobs or other hazards
- **End:** the main End biome and End Highlands list groups of **4**

These are examples, not every biome entry or a guarantee that a full group appears. [Plains][plains] · [Warped Forest][warped] · [Nether Wastes][wastes] · [Soul Sand Valley][valley] · [The End][end-biome] · [End Highlands][highlands]

Endermen use the normal ground-spawn monster check: non-Peaceful difficulty, suitable ground, and dimension-specific light conditions. The bundled **Overworld and End require block light 0**; the Nether uses different light settings. Lighting an area affects spawning, but it is not a barrier against an existing enderman teleporting into an otherwise valid space. [Spawn registration][placement] · [Monster checks][monster] · [Ground and obstruction][mob-spawn] · [Overworld][overworld] · [End][end-dimension] · [Nether][nether]

For Creative testing, use the [Enderman Spawn Egg](../items/EndermanSpawnEgg.md). With command permission, use `/summon minecraft:enderman`.

## Provocation and gaze protection

The stare check uses your view direction toward the enderman's eyes and requires line of sight. **Look away from its eyes before it becomes angry**. Hitting it can provoke retaliation independently, and it also targets [Endermites](Endermite.md). [Goals and stare check][enderman] · [View-direction and visibility test][gaze]

Wearing a **[Carved Pumpkin](../items/CarvedPumpkin.md) in the head slot** prevents stare-based provocation. The bundled gaze-disguise equipment tag contains only the carved pumpkin. Holding one in your hand does not satisfy the equipment check, and wearing it does not cancel an existing anger target or prevent retaliation after an attack. [Head-slot check][disguise-check] · [Disguise tag][disguise] · [Anger and retaliation][enderman]

An angry enderman can stop moving while its targeted player stares at it within **16 blocks**, but this is not a dependable escape plan: at less than **4 blocks**, that same stare can prompt a teleport. When the player is farther than 16 blocks and not staring, its pursuit goal can try teleporting toward them. [Stare-freeze and pursuit goals][enderman]

Looking away does not immediately clear anger. Its starting persistent-anger timer is **20–39 seconds**, but the countdown pauses while a player remains its active target. Do not treat that duration as a guaranteed safe waiting period. [Timer configuration][enderman] · [Countdown conditions][anger]

## Teleportation and defenses

- **Use clearance to your advantage.** A shelter with two blocks of clear interior height cannot fit the normal 2.9-block-tall body. Stay back from the opening so an enderman outside cannot reach you. This is a hitbox-based precaution, not a tested invulnerable shelter design.
- **Ordinary arrows are not a reliable attack against an unmounted enderman.** It skips normal projectile damage and tries teleporting instead. While riding another entity, such as a boat or minecart, it takes normal damage from the bundled projectile types, including arrows, tridents, and bullets. Dismounting restores projectile avoidance. Clean-water thrown potions keep their separate damaging exception. [Current projectile handling][current-enderman] · [Projectile tag][projectiles]
- **Water and rain hurt it.** Endermen avoid water in pathfinding, take water/rain damage attempts, and often teleport after non-living-source damage. Water can create breathing room, but does not itself erase an anger target.
- **A teleport needs a valid destination.** The destination search looks for supporting ground and rejects bedrock. The final body position must still be collision-free and contain no liquid. Teleport attempts can fail. [Current destination checks][current-enderman] · [Final teleport clearance][teleport]

It does not have the zombie-style sunlight-burning routine. Exposed bright daytime conditions can make it clear its current target and try teleporting after the code's minimum waiting period, but the check is random rather than a fixed departure deadline. [Teleportation, projectile handling, water sensitivity, and daytime behavior][enderman] · [Projectile tag][projectiles] · [Water/rain damage][water] · [Final teleport clearance][teleport] · [Melee reach][melee-reach]

## Moving blocks

With **`mobGriefing` enabled**, an enderman can pick up and later place blocks from the **enderman-holdable** tag. Examples include dirt-family blocks, sand, gravel, small flowers, mushrooms, pumpkins, melons, and TNT. It cannot pick up every building block. Placement also requires a valid supported location with no entity occupying the target block space. [Block-moving goals][enderman] · [Holdable tag][holdable] · [Dirt-family tag][dirt]

Carrying a block prevents ordinary distance-based despawning. The carrier can therefore remain around a build longer than expected; changing to Peaceful still removes this monster type. [Carrying persistence][enderman] · [Despawn conditions][despawn]

## Drops

With mob loot enabled, the normal table rolls **0–1 ender pearl**. Looting increases the possible maximum by one per level, reaching **4 with Looting III**. The pearl pool has no player-kill requirement or fire-based replacement. A qualifying player-attributed kill has a base reward of **5 experience**. [Pearl loot][loot] · [Base reward and loot rule][monster] · [Experience conditions][experience]

If it dies carrying a block, that block's drops are evaluated as though harvested with a **Silk Touch I diamond axe**, using the block's own loot table. This is an additional carried-block loot calculation; the result depends on the block. See [Ender Pearl](../items/EnderPearl.md) for throwing and crafting uses. [Carried-block drops][enderman] · [Loot-tool enchantment provider][silk]

## Verification scope

Source-reviewed on **2026-10-01** at `b81c01943c9f3254e713c365a1dd633392929cb2`, using active MattMC code and bundled data. No in-game gaze, teleport, shelter, spawning, or loot test was run. Data packs can change biome tables, tags, and loot; game rules, effects, and entity data can alter encounters.

The riding-projectile and bedrock-destination fixes passed **49 automated cases** on **2026-10-02** in the [entity regression suite][behavior-tests]. These cover mounting transitions, potion compatibility, the synchronized-health-data path, and teleport success/failure paths with mocked world responses. No actual network or in-game session was tested.

Related: [Ender Pearl](../items/EnderPearl.md) · [Endermite](Endermite.md) · [End](../dimensions/End.md) · [Mobs](Mobs.md)

[enderman]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/monster/EnderMan.java
[current-enderman]: https://github.com/HungLo2020/MattMC/blob/fix/issue-767-enderman-behavior/src/main/java/net/minecraft/world/entity/monster/EnderMan.java
[behavior-tests]: https://github.com/HungLo2020/MattMC/blob/fix/issue-767-enderman-behavior/src/test/misc/net/minecraft/world/entity/monster/EnderManBehaviorTest.java
[defaults]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java
[registration]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/EntityType.java#L529-L537
[player-damage]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/player/Player.java#L722-L750
[plains]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome/plains.json
[warped]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome/warped_forest.json
[wastes]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome/nether_wastes.json
[valley]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome/soul_sand_valley.json
[end-biome]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome/the_end.json
[highlands]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome/end_highlands.json
[placement]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L117
[monster]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/monster/Monster.java
[mob-spawn]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Mob.java#L728-L740
[overworld]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/dimension_type/overworld.json
[end-dimension]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/dimension_type/the_end.json
[nether]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/dimension_type/the_nether.json
[gaze]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1684-L1702
[disguise-check]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L192-L199
[disguise]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/gaze_disguise_equipment.json
[anger]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/NeutralMob.java
[projectiles]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/damage_type/is_projectile.json
[water]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2863-L2877
[teleport]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3352-L3400
[melee-reach]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Mob.java#L1270-L1290
[holdable]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/enderman_holdable.json
[dirt]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/dirt.json
[despawn]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Mob.java#L607-L630
[loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities/enderman.json
[experience]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1488
[silk]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/enchantment_provider/enderman_loot_drop.json
