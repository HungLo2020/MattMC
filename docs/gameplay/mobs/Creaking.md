# Creaking

A **Creaking** is a hostile protector summoned by a [Creaking Heart](../blocks/CreakingHeart.md). Keep it in view while locating its Heart: an active Creaking stops moving and attacking when an eligible player is looking at it, while ordinary hits on a **Heart-bound** one provoke a response without reducing its health. Breaking its linked Heart ends that encounter. [Movement and gaze][creaking-gaze] · [Attack gate][creaking-fight] · [Bound damage handler][creaking-hurt] · [Heart removal][heart-remove]

## Obtaining

In ordinary exploration, investigate **Pale Oak trees in [Pale Garden](../biomes/TemperateForests.md#pale-garden)** for natural Hearts. The checked tree route can place a Heart inside its trunk; the mob is created later by that Heart's active ticking logic. The Creaking has no ordinary spawn-list entry in the 68 bundled biome files reviewed here. A monster-category registration is not itself a biome spawn route. [Selected tree branch][pale-selector] · [Tree configuration][pale-tree] · [Active decorator call][tree-call] · [Heart placement][heart-decorator] · [Entity registration][creaking-reg] · [Heart ticker][heart-ticker] · [Protector creation][heart-spawn]

You can also craft or relocate a Heart and make a working setup outside Pale Garden. It needs the aligned Pale Oak timber pair, a qualifying night in a natural dimension, allowed monster spawning, a nearby player and valid spawning space. It supports **one stored protector link at a time**. The [Heart guide](../blocks/CreakingHeart.md#spawning-space-and-player-distance) owns the exact clock, player-range, floor and activation rules; local darkness alone does not activate it. [Alignment][heart-logs] · [Night test][night] · [Spawn/update checks][heart-tick]

A normal [Creaking Spawn Egg](../items/CreakingSpawnEgg.md) creates an **unbound** Creaking. It does not acquire a Heart merely because you place it nearby. This distinction changes damage, Resin production and removal behavior. Ordinary listed eggs use the [inventory item browser](../mechanics/InventoryBrowser.md) in Survival or Creative; spawning and custom-data rules are covered by the egg guide. [Initially empty home position][creaking-health] · [Ordinary egg creation][egg-item] · [Heart-only binding call][heart-spawn] · [Bound-state test][creaking-link]

## Behavior

### Waking it and holding its gaze

A newly inactive Creaking activates when an eligible, non-allied player **less than 12 blocks away** looks at it with the required line of sight. It records that player as an attack target and freezes on that check. Once active, **any eligible watcher** in its nearby-player sensor list can freeze it; the watcher need not be its current target. The default sensor range follows its **32-block follow-range attribute**. [Activation and gaze loop][creaking-gaze] · [Player sensing][player-sensor] · [Attributes][creaking-health]

The look test checks your viewing direction and a clear visual ray toward several heights on its body. You do not have to aim at a single eye pixel, but seeing only a name label or knowing its location behind a wall is not enough. Keep the body visible while retreating and watch your footing. When no qualifying gaze remains, it can move again. [Look and line-of-sight test][gaze-ray] · [Selected body points][creaking-gaze] · [Freeze/update dispatch][creaking-brain]

Use Survival/Adventure teammates as watchers. Ordinary Creative invulnerability and Spectator state exclude a player from the enemy eligibility used by this gaze loop. Do not assume a Creative observer will hold it still for someone else. [Attackable-player check][attackable] · [Creative exclusion][creative] · [Gaze eligibility][creaking-gaze]

### Carved Pumpkin warning

**Wearing a Carved Pumpkin does not make this encounter safe.** Its disguise is ignored for initial activation, so looking at an inactive Creaking from close enough can still wake it. Once the mob is active, a disguised player's gaze is skipped for freezing. The Pumpkin therefore prevents that wearer from holding an already-active Creaking still, while another eligible undisguised player can still do so. [Activation versus active-gaze condition][creaking-gaze] · [Head-slot disguise predicate][disguise] · [Bundled disguise item][disguise-tag]

### Attacks and damage

When able to move, the active brain can pursue a visible attackable player and perform melee attacks. Its base attack attribute is **3 damage points**, and the installed melee behavior uses a **40-tick cooldown** after an eligible attack. Actual damage still depends on difficulty, defenses and other combat rules. Frozen Creakings are prevented from using that melee behavior as well as their movement, jump and navigation controls. [Attributes][creaking-health] · [Fight activity][creaking-fight] · [Melee dispatch][melee] · [Frozen controls][creaking-frozen]

Both forms have a nominal **1 health point**, but their damage routes differ:

| Form | Ordinary damage and practical response |
| --- | --- |
| Heart-bound | Ordinary qualifying hits trigger its hurt response without the normal health-damage call; locate and remove its linked Heart |
| Unbound, such as an ordinary egg spawn | Uses the usual mob damage path; the Heart-protection and Resin callback do not apply |

The bound protection has an explicit exception for damage tagged to bypass invulnerability. It is therefore not absolute immunity to every command or custom damage source. Bound Creakings are also fire-immune through their own callback. [Health][creaking-health] · [Damage branches][creaking-hurt] · [Bound fire immunity][creaking-fire-portal]

### Finding the Heart and producing Resin

A qualifying player-attributed hit on the Heart's own protector can cause trail particles and a Heart response. Use the trail as a clue while keeping a retreat path; the trail is not a guarantee that new Resin appeared. Resin needs an awake Heart, its production cooldown and suitable nearby attachment spaces. [Damage attribution and matching Heart][creaking-hurt] · [Heart response][heart-hurt] · [Continuing trail effects][heart-particles]

Follow [Producing Resin](../blocks/CreakingHeart.md#producing-resin) for the complete setup. Resin is placed around suitable timber by the Heart; it is not an item dropped from the mob each time you hit it. An unbound spawn-egg Creaking cannot substitute for that protector.

To finish the encounter, **break the Heart itself**. Removing one surrounding log is not a dependable immediate shutdown after a protector already exists. The Heart's removal path dismantles its protector, and the bound mob also checks that the Heart still recognizes it. Plan safe footing and space while mining the trunk. [Existing-link state rule][heart-tick] · [Player-break callback][heart-remove] · [Unlink/removal][heart-unlink] · [Mob's link check][creaking-home-check]

## Notes

- The entity ID is `minecraft:creaking`, with a default **0.9×2.7-block** size. It is disallowed in Peaceful; the removal check precedes persistence [Registration][creaking-reg] · [Peaceful ordering][despawn]
- A bound Creaking cannot use portals. Its pathfinding discourages travel beyond its Heart's 32-block home area, and the Heart can remove it beyond 34 blocks [Portal check][creaking-fire-portal] · [Home navigation][creaking-home] · [Distance removal][heart-tick]
- The Heart normally removes its protector outside the night window. A Name Tag's persistence flag can prevent that particular daytime removal, but does not make the Heart awake or enable daytime Resin. Distance and stuck-player safeguards still apply [Heart checks][heart-tick] · [Name Tag persistence][name] · [Awake-only Resin][heart-hurt]
- The Heart link is saved; it is not taming or player ownership. There is no feeding/breeding activity in the checked Creaking brain [Saved home][creaking-save] · [Installed activities][creaking-fight]
- Its ordinary entity loot table has **no item pools**, and its constructor sets **0 XP**. Resin and natural-Heart XP belong to the [Heart block's separate rules](../blocks/CreakingHeart.md#collecting-a-heart-and-its-drops) [Mob loot][creaking-loot] · [XP value][creaking-link]

## Related pages

- [Creaking Heart](../blocks/CreakingHeart.md)
- [Resin](../blocks/Resin.md)
- [Carved Pumpkin](../items/CarvedPumpkin.md)
- [Creaking Spawn Egg](../items/CreakingSpawnEgg.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `cf8cd5371bd1de61411ae5e7e144aabe3edb1e54`. This guide follows active creation, AI and interaction callers, loaded tags and loot, and shared entity behavior. No in-game spawning, combat, sorting, aging, revival, breeding, digging or item-recovery test was performed. Timings describe source rules on a ticking server, not measured output rates; data packs, server settings and custom entity/item data can change the result.

[creaking-gaze]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/creaking/Creaking.java#L463-L511
[creaking-fight]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/creaking/CreakingAi.java#L59-L112
[creaking-hurt]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/creaking/Creaking.java#L153-L184
[heart-remove]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/CreakingHeartBlock.java#L177-L184
[pale-selector]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/configured_feature/pale_garden_vegetation.json#L1-L16
[pale-tree]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/worldgen/configured_feature/pale_oak_creaking.json#L1-L64
[tree-call]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/feature/TreeFeature.java#L155-L159
[heart-decorator]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/feature/treedecorators/CreakingHeartDecorator.java#L32-L57
[creaking-reg]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/EntityType.java#L458-L460
[heart-ticker]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/CreakingHeartBlock.java#L47-L70
[heart-spawn]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/entity/CreakingHeartBlockEntity.java#L198-L211
[heart-logs]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/CreakingHeartBlock.java#L113-L123
[night]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/Level.java#L365-L371
[heart-tick]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/entity/CreakingHeartBlockEntity.java#L99-L144
[creaking-health]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/creaking/Creaking.java#L121-L149
[egg-item]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L52-L88
[creaking-link]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/creaking/Creaking.java#L82-L103
[player-sensor]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/sensing/PlayerSensor.java#L27-L46
[gaze-ray]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1684-L1698
[creaking-brain]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/creaking/Creaking.java#L204-L239
[attackable]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L917-L926
[creative]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/player/Player.java#L763-L766
[disguise]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L192-L199
[disguise-tag]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/item/gaze_disguise_equipment.json#L1-L5
[melee]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/behavior/MeleeAttack.java#L20-L41
[creaking-frozen]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/creaking/Creaking.java#L527-L590
[creaking-fire-portal]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/creaking/Creaking.java#L332-L356
[heart-hurt]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/entity/CreakingHeartBlockEntity.java#L224-L245
[heart-particles]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/entity/CreakingHeartBlockEntity.java#L80-L96
[heart-unlink]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/entity/CreakingHeartBlockEntity.java#L305-L321
[creaking-home-check]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/creaking/Creaking.java#L242-L268
[despawn]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L606-L630
[creaking-home]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/creaking/Creaking.java#L601-L612
[name]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/NameTagItem.java#L17-L29
[creaking-save]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/creaking/Creaking.java#L384-L402
[creaking-loot]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/entities/creaking.json#L1-L4
