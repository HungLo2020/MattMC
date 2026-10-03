# Copper Golem

A **Copper Golem** moves items from [Copper Chests](../blocks/CopperChests.md) into ordinary or Trapped Chests. It has **12 health points (6 hearts)**, works without taming, and can eventually become a [Copper Golem Statue](../blocks/CopperGolemStatues.md) if left unwaxed. Its active behavior is sorting, wandering and fleeing danger; it has no fighting or breeding activity. [Attributes and persistence][copper-start] · [Attribute registration][attributes] · [Sorting targets][sort-predicates] · [Active brain][copper-brain] · [Installed activities][copper-ai]

## Obtaining

Place a **Carved Pumpkin or Jack o'Lantern last on top of one full Copper Block**. All four oxidation stages and their waxed counterparts qualify, but Cut Copper, Grates and Raw Copper Block do not. Completing the pattern creates a golem and replaces the body block with a Copper Chest. The golem starts at the body's oxidation stage; the body's wax does **not** wax the new golem. Use the [construction guide](../blocks/CopperChests.md#creating-a-chest-with-a-copper-golem) for the chest's finish and connection rules. [Head-placement callback][pumpkin] · [Creation and stage choice][copper-create] · [Pattern and chest replacement][copper-pattern] · [Accepted bodies][copper-body] · [Stage initialization][copper-spawn] · [Initial unwaxed state][copper-start]

An **unwaxed, Unaffected statue** can also be revived with a normal axe interaction. The statue guide owns the [scraping and final revival steps](../blocks/CopperGolemStatues.md#bring-the-statue-back-to-life), including secondary-use and broken-axe distinctions. Revival creates a new golem with the saved name and facing; the statue does not store its old sorting cargo. [Revival handler][statue-revive] · [Transferred data][statue-name] · [Cargo release at conversion][weather]

Copper Golems have no entries in the 68 bundled biome spawn tables reviewed for this guide. Their registered `MISC` category is also excluded from the ordinary natural-mob spawning route. Build or revive one instead of searching a biome for a wild herd. The [Copper Golem Spawn Egg](../items/CopperGolemSpawnEgg.md) provides another creation route; ordinary listed items are available through the [inventory item browser](../mechanics/InventoryBrowser.md) in Creative. [Entity registration][copper-reg] · [Natural-spawn exclusion][misc-spawn]

## Behavior

### Setting up a working golem

Start with one reachable Copper Chest for input and ordinary or Trapped Chests for output. Put a sample item in each output chest you want to reserve for that item type. A completely empty output chest can accept any carried type. Keep lids and walking routes clear. [Source/destination selection][sort-predicates] · [Destination matching][transport-items] · [Access validation][transport-search]

The golem carries **up to 16 items from one source slot** at a time. Matching a destination uses the item ID; merging stacks also requires matching item components. A matching but full chest can leave it holding the remainder. The search normally reaches **32 blocks horizontally and 8 vertically** around the golem, with a smaller search while it rides another entity. See [Copper Golem sorting](../blocks/CopperChests.md#copper-golem-sorting) for target selection, timing, queueing and container restrictions. [Pickup and insertion][transport-items] · [Search dimensions][sort-predicates] · [Riding search][transport-box]

A **Lead pauses sorting**, and panic also stops the transport behavior. Use the Lead to move it, then release it at the work area. Its brain can operate compatible doors along a path, so a closed door alone need not keep it in a room. This is not a tame pet with an owner-follow or sit command. [Leash/panic gate][transport-gate] · [Lead eligibility][leash-eligible] · [Door and idle activities][copper-ai]

### Taking its carried items

Interact with an **empty hand** while it is carrying a stack to make it throw that stack toward you. The item enters the world; it is not inserted directly into your inventory. No owner check limits this interaction. If you are currently holding its Lead, the shared interaction releases the Lead first, so interact again after detaching it. [Empty-hand return][copper-interaction] · [Shared interaction order][mob-interaction] · [Lead priority][leash-interaction]

Handing it a sample does not configure its sorting. Configure the chest contents instead. The checked interaction has no food, taming, breeding or **Copper Ingot repair** branch, and its base class is not a breedable Animal. Protect it from damage rather than relying on an ingot to restore health. [Complete mob interaction][copper-interaction] · [Base golem class][golem-persistence] · [Default fallback][mob-default-interaction]

### Waxing, scraping and aging

Use **one Honeycomb** on an unwaxed living golem to suspend its normal aging and statue conversion. Waxing its Copper Chest is a separate action. Use an **unbroken axe** on a waxed golem to remove the wax; later axe uses remove one oxidation stage at a time. Each successful axe action requests one durability, and scraping restarts the aging schedule. Unlike the statue's special revival callback, these living-mob axe actions explicitly reject broken axes. [Living controls][copper-interaction] · [One-item consumption][mob-default-interaction]

Once fully Oxidized, an unwaxed golem can turn into a statue when its feet block is exactly ordinary Air and its conversion roll succeeds. Green coloring alone does not mean conversion has already happened, and conversion is not immediate. It releases preserved carried equipment and saves its name in the statue. Follow the [statue lifecycle](../blocks/CopperGolemStatues.md#obtaining-a-statue) for the aging intervals, poses and revival. [Server aging call][copper-aging] · [Aging and conversion][weather] · [Name transfer][statue-name] · [Preserved-equipment release][preserved]

A new lightning bolt can remove one oxidation stage from an aged living golem and reset its aging schedule **even if it was waxed**. That reset resumes its aging path, so reapply Honeycomb if you want it protected afterward. This behavior differs from lightning cleaning a waxed copper block. [Living lightning handler][copper-lightning] · [Wax sentinel and schedule][weather]

### Poppy on the antenna

An [Iron Golem](IronGolem.md) can offer a nearby Copper Golem a **Poppy**. The offer needs the Iron Golem's bright-outside/random goal check, a suitable nearby recipient, and completion while the recipient still meets the range and empty-antenna checks. It is an occasional interaction, not a guaranteed flower every fixed interval. [Installed offering goal][iron-goal] · [Offer and transfer][flower-offer] · [Candidate tag][gift-candidates] · [Accepted recipient][gift-accepts]

Use **unbroken Shears** on the flower-wearing golem to drop the Poppy into the world, requesting one Shears durability. The bundled shearable-item tag contains only Poppy. Removing the flower does not hurt the golem or alter its oxidation stage. [Shearing interaction][copper-interaction] · [Flower removal][copper-shear] · [Allowed item][poppy]

## Notes

- The entity ID remains `minecraft:copper_golem`; its default size is **0.49×0.98 blocks** [Registration][copper-reg]
- It is marked persistent when created and does not normally despawn merely because you walk away. Persistence does not prevent damage or statue conversion [Creation][copper-start] · [Shared despawn checks][despawn]
- It is in the bundled **fall-damage-immune** tag; this is not immunity to other hazards [Tag][fall-tag] · [Fall calculation][fall-damage]
- Weather state and the next aging deadline are saved [Save/load][copper-save]
- With normal mob loot enabled, its base death table gives **1–3 Copper Ingots**, with a Looting count function. Sorting cargo and the gifted Poppy are separately marked for preserved equipment drops and can be released on death or statue conversion [Base loot][copper-loot] · [Loot gate][loot-gate] · [Death dispatch][death] · [Loaded table][death-table] · [Cargo preservation][transport-items] · [Flower preservation][flower-offer] · [Death release][copper-shear] · [Conversion release][weather] · [Preserved-slot rule][drop-chances]

## Related pages

- [Copper Chests and sorting layouts](../blocks/CopperChests.md)
- [Copper Golem Statues](../blocks/CopperGolemStatues.md)
- [Copper construction](../blocks/CopperConstruction.md)
- [Copper Golem Spawn Egg](../items/CopperGolemSpawnEgg.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `cf8cd5371bd1de61411ae5e7e144aabe3edb1e54`. This guide follows active creation, AI and interaction callers, loaded tags and loot, and shared entity behavior. No in-game spawning, combat, sorting, aging, revival, breeding, digging or item-recovery test was performed. Timings describe source rules on a ticking server, not measured output rates; data packs, server settings and custom entity/item data can change the result.

[copper-start]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/coppergolem/CopperGolem.java#L88-L102
[attributes]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L145-L150
[sort-predicates]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/coppergolem/CopperGolemAi.java#L38-L65
[copper-brain]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/coppergolem/CopperGolem.java#L148-L195
[copper-ai]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/coppergolem/CopperGolemAi.java#L84-L118
[pumpkin]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/CarvedPumpkinBlock.java#L47-L65
[copper-create]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/CarvedPumpkinBlock.java#L93-L124
[copper-pattern]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/CarvedPumpkinBlock.java#L212-L230
[copper-body]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/block/copper.json#L1-L12
[copper-spawn]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/coppergolem/CopperGolem.java#L363-L374
[statue-revive]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/WeatheringCopperGolemStatueBlock.java#L50-L75
[statue-name]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/block/entity/CopperGolemStatueBlockEntity.java#L22-L46
[weather]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/coppergolem/CopperGolem.java#L261-L309
[copper-reg]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/EntityType.java#L445-L447
[misc-spawn]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L244-L265
[transport-items]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/behavior/TransportItemsBetweenContainers.java#L493-L574
[transport-search]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/behavior/TransportItemsBetweenContainers.java#L277-L376
[transport-box]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/behavior/TransportItemsBetweenContainers.java#L396-L407
[transport-gate]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/behavior/TransportItemsBetweenContainers.java#L78-L115
[leash-eligible]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L1217-L1220
[copper-interaction]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/coppergolem/CopperGolem.java#L210-L258
[mob-interaction]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L1053-L1075
[leash-interaction]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Entity.java#L2146-L2175
[golem-persistence]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/AbstractGolem.java#L38-L41
[mob-default-interaction]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L1109-L1120
[copper-aging]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/coppergolem/CopperGolem.java#L198-L207
[preserved]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L845-L864
[copper-lightning]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/coppergolem/CopperGolem.java#L454-L465
[iron-goal]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/IronGolem.java#L66-L74
[flower-offer]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/goal/OfferFlowerGoal.java#L29-L86
[gift-candidates]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/entity_type/candidate_for_iron_golem_gift.json#L1-L6
[gift-accepts]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/entity_type/accepts_iron_golem_gift.json#L1-L5
[copper-shear]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/coppergolem/CopperGolem.java#L429-L445
[poppy]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/item/shearable_from_copper_golem.json#L1-L5
[despawn]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L606-L630
[fall-tag]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/tags/entity_type/fall_damage_immune.json#L1-L22
[fall-damage]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1724-L1730
[copper-save]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/animal/coppergolem/CopperGolem.java#L170-L183
[copper-loot]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/entities/copper_golem.json#L1-L36
[loot-gate]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L563-L568
[death]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1474
[death-table]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1527
[drop-chances]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/DropChances.java#L28-L47
