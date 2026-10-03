# Tiger

Tigers are powerful predators that can attack nearby players without being struck first. Giving one dropped food can earn **Tiger's Blessing**, a temporary effect that suppresses tiger targeting. Feeding does not create an owned pet, and the bundled data supplies no Tiger breeding food.

## At a glance

- **Entity ID:** `minecraft:tiger`
- **Health:** 50 points (25 hearts)
- **Base attack attribute:** 12 points (6 hearts); its custom attacks use separate damage values below
- **Registered adult size:** 1.5 blocks wide × 1.3 blocks tall

## Obtaining

The [Tiger Spawn Egg](../items/TigerSpawnEgg.md) is registered and explicitly listed in Creative. With command permission, use `/summon minecraft:tiger`.

**Natural spawning is not established in this snapshot.** No Tiger entry was found in the reviewed biome spawn tables, biome-building code, or spawn-placement registrations. Its brightness predicate, spawn-roll check, and liquid-obstruction check do not provide a confirmed natural habitat.

## Feeding and Tiger's Blessing

Drop food near a Tiger and let it collect the item. It accepts items with a food component **except Rotten Flesh**, including foods other than meat. Each pickup consumes one item and heals the Tiger by up to **5 health points**. It considers dropped items more than 10 game ticks old.

When you are the recorded thrower and still present in the world, each accepted item can give you Tiger's Blessing:

| Dropped food | Chance per item |
| --- | --- |
| Raw or Cooked Porkchop | 40% |
| Raw or Cooked Chicken | 30% |
| Other accepted food | 10% |

[Pigs](Pig.md) and [Porkchop cooking](../items/CookedPorkchop.md) provide a useful food supply. Holding food or directly using ordinary food on a Tiger is not the blessing interaction; it must collect your dropped item.

The effect lasts **12,000 game ticks**, about **10 minutes** at 20 ticks per second. Tiger player-targeting excludes a blessed player, and every Tiger's tick clears an existing target carrying the effect. This is not general damage immunity, and feeding is not guaranteed to stop a fight before its next hit.

There is no saved trust list, permanent taming, owner command cycle, or player-riding interaction. The protection resides on the blessed player and expires with the effect.

The effect ID is `minecraft:tigers_blessing`, and dropped food grants **level I**. Another successful pickup refreshes a shorter level-I timer to 12,000 ticks instead of adding another ten minutes. Higher stored levels do not strengthen the Tiger's presence-based targeting checks. [Food grant][custom-tiger-grant] · [Default level][custom-tiger-level] · [Refresh rules][custom-tiger-refresh] · [Target clearing][custom-tiger-clear] · [Player target gate][custom-tiger-gate]

[Milk](../items/MilkBucket.md) removes this blessing along with other active effects, so drinking it can end your protection near Tigers. The blessing does not grant general damage resistance or protect other unblessed animals. [Milk][custom-tiger-milk] · [All-effects removal][custom-tiger-removal] · [Tiger checks][custom-tiger-clear]

## Hunting and combat

An adult's nearby-player targeting goal has a **4-block range** and excludes Tiger's Blessing. Other targeting and retaliation paths also exist, so do not treat that distance as a universal safe boundary.

The prey tag includes ordinary land animals and villagers, plus Kangaroos, Moose, Gelada Monkeys, and Caimans. The shared land-animal tag also includes **Tasmanian Devils** and several owned-pet species. Keep a Tiger away from livestock and villagers even when you have the blessing.

Its active combat goal stalks distant targets, runs as it closes in, and uses several attacks:

- **Paw strikes:** request 7–11 damage points
- **Leap contact:** requests 2 points and can start a hold
- **Holding:** pulls the target toward the Tiger and repeatedly requests 5–6 points of damage

These are damage requests before armor, immunity windows, and other damage handling; they are not a fixed total for each encounter. A close-range rush can also apply **Weakness I for 100 ticks**, about five seconds. The source's `FEAR` name is an alias for ordinary Weakness in this integration.

The Tiger heals **5 health points when credited with a kill**. Its resting and sleeping are ordinary idle behavior, not player-issued commands.

## Breeding and white Tigers

`minecraft:tiger_breedables` is declared and checked, but **has no bundled item-tag definition**. No food can therefore be recommended for normal Tiger breeding or baby-growth feeding in this snapshot. Dropped food and the blessing work independently of that missing tag.

If a data pack supplies breeding foods, the existing breeding goal creates a live cub. The offspring's white-color chance is 10% with two non-white parents, 40% with one white parent, and 80% with two white parents. White state is saved, but the class does not randomize newly spawned Tigers to establish a natural white-variant chance.

## Integration limits and drops

Two older hooks should not be treated as complete behavior:

- The Tiger's anger-update method has an outdated signature, so its configured 40–80 second anger timer is not a reliable forgiveness rule
- Its special movement collision method is bypassed by the current entity movement method. Passing through solid Leaves or Bamboo is therefore not established, even though navigation contains special handling for them

No dedicated Tiger death-loot table or registered Tiger-specific resource item was found. This guide establishes no special hide, fang, or other death reward.

Source-reviewed on **2026-10-01** at `b81c01943c9f3254e713c365a1dd633392929cb2`. No in-game spawning, feeding, blessing, breeding, combat, movement, or persistence test was run.

## Related pages

- [Tasmanian Devil](TasmanianDevil.md)
- [Status effects](../effects/Effects.md)
- [Mobs](Mobs.md)

## Sources

- [Registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/EntityType.java#L1370-L1376)
- [Attribute wiring](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L248)
- [Spawn egg](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L1973)
- [Creative listing](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2102)
- [Stats, spawning checks, goals, and kill healing](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityTiger.java#L109-L215)
- [Anger hook, running, holding, and blessing protection](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityTiger.java#L280-L435)
- [Conditional breeding and white inheritance](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityTiger.java#L453-L466)
- [Dropped food and blessing chances](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityTiger.java#L512-L553)
- [Active custom combat and player targeting](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityTiger.java#L583-L704)
- [Prey tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/entity_type/tiger_targets.json)
- [Shared animal prey](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/entity_type/passive_land_animals.json)
- [Villager prey](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/entity_type/villagers.json)
- [Food-tag declaration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/misc/AMTagRegistry.java#L184-L186)
- [Blessing and Fear aliases](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/effect/AMEffectRegistry.java#L36-L45)
- [Registered Weakness modifier](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/effect/MobEffects.java#L75-L79)
- [Dropped-item dispatch and consumption](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/ai/CreatureAITargetItems.java#L53-L145)
- [Current server AI dispatch](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Mob.java#L634-L675)
- [Active kill-credit callback](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1413-L1435)
- [Current private collision method](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Entity.java#L1033-L1108)
- [Movement collision call](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Entity.java#L704-L706)
- [Spawn placements](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/SpawnPlacements.java)
- [Bundled item tags](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item)
- [Biome spawn data](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome)
- [Biome-building code](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/biome)
- [Entity loot tables](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities)

Tiger's Blessing details additionally source-reviewed on **2026-10-02** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`. No in-game blessing or removal test was run.

[custom-tiger-grant]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityTiger.java#L512-L553
[custom-tiger-level]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L49-L58
[custom-tiger-refresh]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L123-L147
[custom-tiger-clear]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityTiger.java#L430-L433
[custom-tiger-gate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityTiger.java#L663-L678
[custom-tiger-milk]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L64
[custom-tiger-removal]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L943-L952
