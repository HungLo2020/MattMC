# Alligator Snapping Turtle

The **Alligator Snapping Turtle** (`minecraft:alligator_snapping_turtle`) is a low, armored animal that can bite nearby creatures without being attacked first. It can breed when fed fish and grow harvestable moss, but its current water behavior needs care: **full submersion can drown it**. It has **18 health points (9 hearts)**, **8 armor points**, and a registered adult body **1.5 × 0.7 blocks**. [Registration][a-id] · [Attributes and goals][a-stats] · [Attribute wiring][a-attr]

## Obtaining

The [Alligator Snapping Turtle Spawn Egg](../items/AlligatorSnappingTurtleSpawnEgg.md) is an ordinary category-listed item. Request it through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in **Survival as well as Creative**, then place the creature with room around it. [Egg registration][a-egg] · [Category entry][a-category] · [Egg placement][egg-placement]

**No natural population or structure-supplied turtle was found in the checked bundled data.** The review covered all 68 biome definitions, 34 structure definitions and 1,202 structure templates. Its sand-and-height helper is not registered in the active spawn-placement table; that standalone method does not establish a beach or swamp habitat. [Bundled data][bundled-data] · [Placement registrations][spawn-rules] · [Standalone predicate][a-stats]

## Behavior

Keep players and other animals out of its immediate reach. Its nearby-target search includes living creatures other than its own species and Armor Stands, excluding Creative and Spectator players. It also retaliates when hurt. The installed melee goal remains active alongside the animated bite logic; the base attack attribute is **4 damage points**, and babies should not be treated as harmless. Final damage depends on the target's defenses and damage handling. [Target filter][a-targets] · [Goals and attack attribute][a-stats] · [Melee caller][melee-call] · [Damage handler][mob-attack] · [Animated bite][a-ai]

In water it can wait with its mouth open, then lunge at a visible target within **2.3 blocks**. It abandons a chase after more than 40 chase ticks when the target is over five blocks away for a player, or ten for another creature, then has a 50-tick targeting pause. These are source conditions, not a guarantee that approaching during an animation is safe. [Waiting, bite and chase handling][a-ai]

### Water and enclosure care

The turtle seeks water and has no ordinary leave-water preference. It can climb when colliding horizontally while in water, so a low open pool wall is not a reliable enclosure. Provide shallow water where its eyes can remain above the surface and a way to keep it from becoming trapped underwater. [Water goals and preferences][a-stats] · [Preferences][a-state] · [Climbing flag][a-ai] · [Movement][a-move]

**The checked turtle does not have working underwater breathing.** Its class contains an empty `updateAir` helper, but the active breathing check uses the entity-type tag, which omits this species. The inherited living-entity path therefore drains air while its eyes are submerged. With ordinary unmodified air supply, the first 2-point drowning hit occurs after about **320 ticks (16 seconds at 20 TPS)**, followed by further hits while it stays submerged. It has no installed breath-seeking goal. [Unused helper and preferences][a-state] · [Breathing tag][breathe-tag] · [Active tag check][air-tag-check] · [Air handling][living-air] · [Default air][air-max] · [Damage threshold][air-damage]

It can be moved with a **Lead** and does not normally disappear solely because a player is far away. It is not a bucketable or tame-owner pet: feeding it does not give follow/stay commands. [Leash rule][lead] · [Animal persistence][animal-persist] · [Implemented interactions][animal-food] · [Species class][a-class] · [Goals][a-stats]

## Feeding and breeding

Feed two ready adults **Raw or Cooked Cod, Raw or Cooked Salmon, Tropical Fish, or Pufferfish**. Those are the six entries in the actual fish tag; Seagrass, Raw Catfish and Lobster Tail are not substitutes. The installed breeding goal creates a **live baby Alligator Snapping Turtle**, not a placed Turtle Egg. Keep other targets away so combat does not interrupt the pair. [Food predicate and breeding goal][a-stats] · [Food tag][fishes] · [Adult interaction][animal-food] · [Active breeding caller][breed-goal] · [Correct offspring type][a-child]

Parents receive the ordinary **6,000-tick breeding cooldown**. Babies start at **−24,000 age ticks**, nominally twenty minutes of ticking to adulthood; feeding one of the accepted fish speeds growth by roughly 10% of the remaining time. This food interaction does not directly heal or tame the animal. [Breeding completion][animal-breed] · [Age and growth feeding][age] · [Food interaction][animal-food]

## Moss and Seagrass

A newly initialized turtle receives **0–5 moss levels** and a visual scale of roughly **0.8–1.0**. The renderer uses both values. Moss can later grow to level ten: the saved timer advances each server tick, and after it exceeds **12,000 ticks**, a tick in water adds one level and resets the timer. Time on land contributes to that timer, so this is not a requirement to stay submerged for ten minutes. [Initialization][a-scale] · [Timer and cap][a-ai] · [Saved state][a-state] · [Active renderer][a-render] · [Renderer registration][render-register]

To harvest, point a **Dispenser containing Shears** at a living turtle with moss. The registered dispenser action reaches its shearing method, which drops **one Seagrass** and clears all moss, regardless of the previous moss level. A successful dispenser action requests one point of Shears wear, before damageability and enchantment handling. [Durability handling][shear-durability] The dispenser checks leash removal first, so a leashed turtle may need another activation after its lead is removed. [Dispenser registration][shear-register] · [Dispenser checks][shear-dispense] · [Species harvest][a-shear]

Ordinary handheld Shears interaction is not wired to this turtle's shearing method in the checked class. There is also no Spiked Scute output in its active harvest method. Use the [Seagrass guide](../items/Seagrass.md) for planting and ordinary plant harvesting, and [Dispenser and Dropper](../blocks/DispenserAndDropper.md) for the device controls. [Handheld Shears methods][shears] · [Mob interaction dispatch][mob-interact] · [Actual harvest output][a-shear]

## Drops

The default `entities/alligator_snapping_turtle` loot table is absent from the bundled files, and missing tables resolve to empty loot. No meat or scute death drop is configured here. Moss shearing is the verified Seagrass route. An adult can still give the ordinary animal **1–3 base XP** after qualifying player kill credit with `doMobLoot` enabled; babies do not give that ordinary death XP. [Default loot mapping][default-loot] · [Bundled entity loot][loot-data] · [Missing-table fallback][loot-fallback] · [Animal XP][animal-persist] · [XP and baby gates][death-call] [Baby loot gates][loot-baby]

## Notes

This registered `MobCategory.CREATURE` uses `EntityAlligatorSnappingTurtle` from bundled Alex's Mobs content. Its active implementation differs from both an ordinary [Turtle](Turtle.md) and upstream expectations. Preserve its breathing and handheld-shearing limitations when planning an enclosure or farm.

Related: [Spawn egg](../items/AlligatorSnappingTurtleSpawnEgg.md) · [Seagrass](../items/Seagrass.md) · [Mobs](Mobs.md)

Source-reviewed at `a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1` on 2026-10-02. Registration, attributes, spawn/data inventory, active AI, breathing, breeding, shearing caller and loot fallback were traced. No game, breeding, combat, air-supply or farm test was run.

[a-id]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/EntityType.java#L228-L234
[a-stats]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/entity/EntityAlligatorSnappingTurtle.java#L86-L124
[a-attr]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L112
[a-egg]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/Items.java#L1797
[a-category]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1971
[egg-placement]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L76-L103
[bundled-data]: https://github.com/HungLo2020/MattMC/tree/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/resources/data
[spawn-rules]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L87-L178
[a-targets]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/entity/EntityAlligatorSnappingTurtle.java#L49-L51
[melee-call]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/ai/goal/MeleeAttackGoal.java#L127-L137
[mob-attack]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/Mob.java#L1292-L1320
[a-ai]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/entity/EntityAlligatorSnappingTurtle.java#L142-L224
[a-state]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/entity/EntityAlligatorSnappingTurtle.java#L289-L332
[a-move]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/entity/EntityAlligatorSnappingTurtle.java#L350-L369
[breathe-tag]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/resources/data/minecraft/tags/entity_type/can_breathe_under_water.json
[air-tag-check]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/LivingEntity.java#L384-L386
[living-air]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/LivingEntity.java#L424-L442
[air-max]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/Entity.java#L2660-L2662
[air-damage]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/LivingEntity.java#L491-L493
[lead]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/Mob.java#L1217-L1220
[animal-persist]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L128
[animal-food]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/animal/Animal.java#L133-L157
[a-class]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/entity/EntityAlligatorSnappingTurtle.java#L47-L71
[fishes]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/resources/data/minecraft/tags/item/fishes.json
[breed-goal]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/ai/goal/BreedGoal.java#L33-L82
[a-child]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/entity/EntityAlligatorSnappingTurtle.java#L385-L390
[animal-breed]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/animal/Animal.java#L197-L227
[age]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/AgeableMob.java#L129-L170
[a-scale]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/entity/EntityAlligatorSnappingTurtle.java#L254-L261
[a-render]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/client/render/RenderAlligatorSnappingTurtle.java#L20-L60
[render-register]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/client/renderer/entity/EntityRenderers.java#L78-L113
[shear-register]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L378
[shear-dispense]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/core/dispenser/ShearsDispenseItemBehavior.java#L19-L68
[a-shear]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/entity/EntityAlligatorSnappingTurtle.java#L371-L383
[shears]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/ShearsItem.java#L26-L86
[mob-interact]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/Mob.java#L1054-L1107
[default-loot]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/EntityType.java#L2063-L2066
[loot-data]: https://github.com/HungLo2020/MattMC/tree/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/resources/data/minecraft/loot_table/entities
[loot-fallback]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[death-call]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1486
[loot-baby]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L567

[shear-durability]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/ItemStack.java#L457-L473
