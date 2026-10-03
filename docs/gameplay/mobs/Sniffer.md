# Sniffer

A **Sniffer** is a large passive animal that can uncover **Torchflower Seeds or Pitcher Pods**. Hatch one from a Sniffer Egg, raise it, then provide accessible digging ground and let it search undisturbed. It has **14 health points (7 hearts)**; seeds are food for breeding and baby growth, not a healing interaction. [Attributes][sniffer-attributes] · [Active attribute wiring][sniffer-attributes-wiring] · [Digging rewards][sniffer-dig-loot] · [Food handling][animal-food]

## Obtaining

Start with a **[Sniffer Egg](../items/SnifferEgg.md)**. Warm ocean-ruin Suspicious Sand can provide one through archaeology; ready adult Sniffers can breed for more. Place the egg and let its block tick through hatching. It creates a baby Sniffer when the hatch completes. The [placed-egg guide](../blocks/AnimalEggs.md#sniffer-eggs) owns brushing, hatch timing, Moss Block acceleration, recovery and protection. [Warm-ruin reward table][warm-egg-loot] · [Active hatch callback][sniffer-hatch] · [Breeding egg drop][sniffer-breed]

No ordinary Sniffer biome spawn-list entry was found in the bundled data reviewed here. The `CREATURE` registration is a classification; ordinary natural and generation-time spawning still select from biome/structure lists. Hatching an egg is the verified route for starting an ordinary herd. The [Sniffer Spawn Egg](../items/SnifferSpawnEgg.md) is a separate creation option, and ordinary listed items can be requested through the [inventory item browser](../mechanics/InventoryBrowser.md) in Creative. [Registration][sniffer-reg] · [Natural selection][biome-spawn] · [Generation-time selection][gen-spawn]

## Behavior

### Food, following and growth

**Torchflower Seeds** are the only item in the bundled Sniffer-food tag. Mature Torchflowers and Pitcher Pods do not qualify. Holding seeds in either hand can tempt a Sniffer to follow; put them away after moving it into its pen if you want it to resume searching for digging sites. Feeding does not tame it or assign an owner. [Food tag][sniffer-food] · [Food check][sniffer-food-check] · [Temptation sensor registration][sniffer-sensor] · [Both-hand checks][tempt] · [Following activity][sniffer-brain] · [Mob interaction][sniffer-interaction]

A hatchling starts with **48,000 game ticks of baby growth**, about **40 minutes at 20 TPS while the entity ticks**. Feeding a baby one Torchflower Seed removes approximately **10% of its remaining growth time**, with the shared rounding rule. Baby Sniffers do not dig up seeds or pods. [Hatch creates a baby][sniffer-hatch] · [Sniffer-specific baby age][sniffer-baby] · [Age countdown][age-tick] · [Baby feeding][animal-food] · [Growth calculation][age-food] · [Adult-only digging][sniffer-search]

### Breeding for more eggs

Bring two ready adults together and feed each a Torchflower Seed. They need room to approach one another. Their mating check additionally requires both to be **idling, scenting or feeling happy**, so an occupied or interrupted animal may need time to settle. [Food/love interaction][animal-food] · [Allowed mating states][sniffer-food-check] · [Installed mating behavior][sniffer-brain] · [Approach and breeding caller][breed-call]

Successful ordinary breeding **drops one Sniffer Egg item** into the world. It does not place an egg block or immediately create a baby. Collect the egg and place it in a protected hatching area. Both parents receive a **6,000-tick breeding cooldown**, about **5 minutes while ticking**. [Actual breeding override][sniffer-breed] · [Parent cooldown][animal-breed] · [Age countdown][age-tick]

Keep some seeds for breeding before planting the rest. The [Torchflower crop guide](../blocks/Torchflower.md#harvesting-and-displaying-the-flower) explains why growing the crop does not multiply your seed supply.

### Helping it find digging ground

Give an adult room to walk and turn on reachable ground such as **Dirt, Grass Block, Moss Block or Mud**. The full nine-block list is in [Sniffer seeds and pods](../blocks/Torchflower.md#sniffer-seeds-and-pods). **Farmland and Mycelium are absent** from the digging tag. Planting crops throughout the pen can therefore remove useful searching ground. [Exact ground tag][sniffer-ground]

A normal search-and-dig cycle requires an adult on the ground, out of water, not riding, not panicking and not distracted by a tempting player or breeding. A **Lead prevents the normal sniff/search/start chain**; detach it after transport. Valid terrain alone does not force a dig. [Sniffing restrictions][sniffer-ready] · [Adult and terrain checks][sniffer-search] · [Dig start/continuation][sniffer-cooldown]

The Sniffer checks ground near its forward head position and needs a path that reaches the candidate. It tries several random positions, so a tiny patch beside its body is not a guaranteed working site. It also remembers explored positions, and those memories are saved. Give it fresh accessible ground when it struggles to find a target; do not rely on unloading/reloading to clear its history. No fixed minimum pen size or timed ground-reset rule is established here. [Head position][sniffer-ready] · [Candidate search and path check][sniffer-search] · [Remembered positions][sniffer-memory] · [Memory codecs][memory-codecs] · [Brain save][brain-save] · [Brain load][brain-load]

### Collecting seeds and pods

Each execution of the bundled digging table produces **one Torchflower Seed or one Pitcher Pod**, with equal weights. The reward appears as a loose item near the head; collect it from the ground. It is not delivered into a chest or player inventory. The Sniffer's active digging route does not consume or break its supporting terrain. [Digging table][sniffer-dig-loot] · [Default entry weights][loot-weight-default] · [Item creation][sniffer-gift] · [Gift-table dispatch][gift-call] · [Loaded table execution][gift-load]

Entering the digging state schedules its drop at **tick +120**, about six seconds at 20 TPS. A dig interrupted before that moment can produce no item. Completing the timed dig installs a **9,600-tick sniff cooldown**, about eight minutes, followed by further searching and activity choices before another successful dig. This is **not a guaranteed item every eight minutes**. The later completed rising action records an explored body-support position. [Dig start][sniffer-start] · [Active drop tick][sniffer-tick] · [Registered reward key][sniffer-key] · [Completion/cooldown][sniffer-cooldown] · [Rising completion][sniffer-rise] · [Position recording][sniffer-start] · [Ticking memory expiry][brain-tick]

The checked gift path has no `mobGriefing` or `doMobLoot` requirement; those rules should not be used as a substitute for checking its age, AI state, ground and path. The [Torchflower](../blocks/Torchflower.md) and [Pitcher Plant](../blocks/PitcherPlant.md) owners cover planting and using the recovered items. [Direct digging gift call][sniffer-gift] · [Gift execution][gift-load]

### Keeping a Sniffer safe

Use seeds or a [Lead](../items/Lead.md) for transport, then leave a safe, dry walking area with headroom for this **1.9-block-wide, 1.75-block-tall adult**. Its behavior includes swimming and panic, but neither makes water a suitable digging site. Damage can interrupt work and reset love mode. Seeds do not restore lost health through the feeding path. [Size][sniffer-reg] · [Leash support][sniffer-ready] · [Lead eligibility][leash-eligible] · [Panic/swim activities][sniffer-brain] · [Damage resets love][animal-hurt] · [Feeding][animal-food]

The ordinary interaction offers no owner sit command, cargo inventory or saddle-riding control. Bundled Saddles and Horse/Wolf Armor do not permit Sniffers as their equipment targets. In MattMC, wild [Vallumraptors](Vallumraptor.md) can also target Sniffers: keep those predators out of an animal enclosure. [Interaction][sniffer-interaction] · [Saddle component][saddle] · [Saddle targets][saddle-tag] · [Armor components][equip-armor] · [Horse-armor targets][horse-armor] · [Equipment eligibility][equip-gate] · [Predator goal][raptor-goal] · [Untamed condition][raptor-untamed] · [Prey tag][raptor-targets]

## Notes

- The entity ID remains `minecraft:sniffer` and its classification is `CREATURE` [Registration][sniffer-reg]
- A Sniffer does not normally despawn because you walk away; a Name Tag is not needed merely for distance retention. Its area still has to tick for growth and active work to progress [Animal retention][sniffer-persist] · [Despawn caller][despawn] · [Age ticking][age-tick]
- Its ordinary death table has **no item pools**. Killing one is not the normal source of seeds, pods or eggs. Eligible adult player-credited kills use the shared base **1–3 XP** animal reward under the ordinary loot/experience gates [Death table][sniffer-death-loot] · [Animal XP][sniffer-persist] · [Adult/loot gate][loot-gate] · [Experience gate][animal-xp-gate]

## Related pages

- [Placed Sniffer Eggs: acquisition, hatching and protection](../blocks/AnimalEggs.md#sniffer-eggs)
- [Torchflower and digging rewards](../blocks/Torchflower.md)
- [Pitcher Plant](../blocks/PitcherPlant.md)
- [Sniffer Egg](../items/SnifferEgg.md) and [Sniffer Spawn Egg](../items/SnifferSpawnEgg.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `cf8cd5371bd1de61411ae5e7e144aabe3edb1e54`. This guide follows active creation, AI and interaction callers, loaded tags and loot, and shared entity behavior. No in-game spawning, combat, sorting, aging, revival, breeding, digging or item-recovery test was performed. Timings describe source rules on a ticking server, not measured output rates; data packs, server settings and custom entity/item data can change the result.

[sniffer-attributes]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/sniffer/Sniffer.java#L79-L88
[sniffer-attributes-wiring]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L241
[sniffer-dig-loot]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/gameplay/sniffer_digging.json#L1-L20
[animal-food]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Animal.java#L133-L158
[warm-egg-loot]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/archaeology/ocean_ruin_warm.json#L1-L57
[sniffer-hatch]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/SnifferEggBlock.java#L64-L78
[sniffer-breed]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/sniffer/Sniffer.java#L333-L341
[sniffer-reg]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/EntityType.java#L1236-L1244
[biome-spawn]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L326
[gen-spawn]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L360-L370
[sniffer-food]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/item/sniffer_food.json#L1-L5
[sniffer-food-check]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/sniffer/Sniffer.java#L420-L433
[sniffer-sensor]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/sensing/SensorType.java#L53
[tempt]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/sensing/TemptingSensor.java#L27-L52
[sniffer-brain]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/sniffer/SnifferAi.java#L75-L164
[sniffer-interaction]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/sniffer/Sniffer.java#L362-L371
[sniffer-baby]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/sniffer/Sniffer.java#L410-L426
[age-tick]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/AgeableMob.java#L129-L147
[age-food]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/AgeableMob.java#L166-L168
[sniffer-search]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/sniffer/Sniffer.java#L247-L271
[breed-call]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/behavior/AnimalMakeLove.java#L49-L84
[animal-breed]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Animal.java#L197-L227
[sniffer-ground]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/block/sniffer_diggable_block.json#L1-L13
[sniffer-ready]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/sniffer/Sniffer.java#L124-L149
[sniffer-cooldown]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/sniffer/SnifferAi.java#L185-L204
[sniffer-memory]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/sniffer/Sniffer.java#L310-L319
[memory-codecs]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/memory/MemoryModuleType.java#L124-L134
[brain-save]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L741-L744
[brain-load]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L808-L817
[loot-weight-default]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/storage/loot/entries/LootPoolSingletonContainer.java#L45-L55
[sniffer-gift]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/sniffer/Sniffer.java#L274-L284
[gift-call]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1544-L1553
[gift-load]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1569-L1581
[sniffer-start]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/sniffer/Sniffer.java#L217-L241
[sniffer-tick]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/sniffer/Sniffer.java#L349-L360
[sniffer-key]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/storage/loot/BuiltInLootTables.java#L100
[sniffer-rise]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/sniffer/SnifferAi.java#L226-L260
[brain-tick]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/Brain.java#L401-L425
[leash-eligible]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L1217-L1220
[animal-hurt]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Animal.java#L80-L84
[saddle]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/equipment/Equippable.java#L99-L108
[saddle-tag]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/entity_type/can_equip_saddle.json#L1-L14
[equip-armor]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/Item.java#L469-L501
[horse-armor]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/entity_type/can_wear_horse_armor.json#L1-L5
[equip-gate]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3580-L3584
[raptor-goal]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/alexscaves/server/entity/living/VallumraptorEntity.java#L153-L158
[raptor-untamed]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/alexscaves/server/entity/ai/MobTargetUntamedGoal.java#L18-L23
[raptor-targets]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/entity_type/vallumraptor_targets.json#L1-L15
[sniffer-persist]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L129
[despawn]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L606-L630
[sniffer-death-loot]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/entities/sniffer.json#L1-L4
[loot-gate]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L563-L568
[animal-xp-gate]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1480-L1487
