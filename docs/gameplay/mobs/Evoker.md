# Evoker

An Evoker (`minecraft:evoker`) is a hostile spellcaster with **24 health points (12 hearts)**. It attacks with ground fangs and summoned [Vexes](Vex.md), and is a source of [Totems of Undying](../items/TotemOfUndying.md). [Health and goals][evoker] · [Registered attributes][evoker-attributes]

## Obtaining

The checked encounter routes are **[Woodland Mansion](../structures/WoodlandMansion.md) room markers** and **[raid waves](../mechanics/Raid.md#waves-and-difficulty)**. Mansion Evokers are created as persistent structure residents; raid Evokers are created by the event, including eligible Ravager riders. Use the mansion guide for generation and the raid guide for wave availability. [Mansion creation][markers] · [Raid creation][raid-call] · [Raid types][raid-types]

For deliberate placement, its ordinary listed [spawn egg](../items/EvokerSpawnEgg.md) can be requested through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in Survival or Creative. This is separate from finding a generated resident. Evokers are not allowed in Peaceful. [Egg listing][evoker-entry] · [Entity registration][evoker-type] · [Peaceful removal][peaceful]

## Behavior

Evokers target players, villagers and Iron Golems, retaliate when hurt, and can alert other raiders. They try to move away from nearby players and Creakings when their higher-priority casting behavior is not taking control. Spell preparation has a sound and a warm-up; while casting, the movement goal stops navigation. Do not mistake a pause for surrender. [Targeting and avoidance][evoker] · [Casting controls][spell]

### Fangs

Against a target less than three blocks away, the spell places two rings of fangs around the Evoker; farther away it attempts a line of 16 fangs toward the target. Placement searches for a supporting surface, so terrain affects which fangs actually appear. Each fang's hit attempts **6 magic damage points** and excludes the caster and its allies. This is the attack's input value, not a promise of health lost after defenses. Move away from the forming pattern rather than relying on a doorway to stop it. [Pattern and terrain][fangs] · [Hit and ally checks][fang-hit]

### Vex summons

A successful summon spell attempts **three Vexes**, assigning the Evoker as their owner and giving each a limited-life timer. The summon decision counts nearby Vexes and compares the count with a random value from 1–8. It is neither a fixed three-Vex lifetime allowance nor a hard world-wide cap. [Summon gate and creation][summon]

Deal with the caster promptly if you can do so safely: [Vexes](Vex.md#behavior) can pass through solid blocks, and killing the Evoker does not immediately erase existing summons. That advice is inferred from the checked spell and Vex behavior, not a tested combat strategy.

### Sheep recoloring

When it has no combat target, an Evoker can change a nearby **blue Sheep to red**. The spell requires `mobGriefing` enabled and an eligible Sheep in its search area. This is a color change, not taming or breeding. [Sheep spell][sheep]

## Drops

The bundled loot table supplies **one Totem of Undying** in its own unconditional pool. It does not require a player-kill condition, and Looting does not multiply that Totem. A separate player-kill pool gives **0–1 Emerald before Looting**, with a Looting count increase. Both still depend on the ordinary mob-loot gate. [Loot table][evoker-loot] · [Mob-loot rule][loot-rule] · [Death dispatch][loot-call]

## Notes

The registered mob class, its default attributes and server goal scheduler connect the described behavior to the active Evoker entity. [Registration][evoker-type] · [Attributes][evoker-attributes] · [AI caller][ai-call]

Related: [Vindicator](Vindicator.md) · [Vex](Vex.md) · [Woodland Mansion](../structures/WoodlandMansion.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `cf8cd5371bd1de61411ae5e7e144aabe3edb1e54` on 2026-10-02. Checked mansion/event callers, registered AI and attributes, fang placement/hits, summons, Sheep interaction and death loot. No spawning, spell, combat, recoloring or drop test was performed. Modified data and rules can change outcomes.

[evoker]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/Evoker.java#L46-L72
[evoker-attributes]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L164
[markers]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/structures/WoodlandMansionPieces.java#L1200-L1260
[raid-call]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/raid/Raid.java#L499-L565
[raid-types]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/raid/Raid.java#L802-L807
[evoker-entry]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2017
[evoker-type]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/EntityType.java#L578-L586
[peaceful]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L607-L612
[spell]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/SpellcasterIllager.java#L130-L207
[fangs]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/Evoker.java#L123-L187
[fang-hit]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/projectile/EvokerFangs.java#L86-L119
[summon]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/Evoker.java#L211-L257
[sheep]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/Evoker.java#L271-L316
[evoker-loot]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/entities/evoker.json#L1-L51
[loot-rule]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L565-L567
[loot-call]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1475
[ai-call]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L634-L661
