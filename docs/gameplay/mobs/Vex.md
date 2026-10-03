# Vex

A Vex (`minecraft:vex`) is a small flying hostile mob with **14 health points (7 hearts)**. It carries an Iron Sword and can move through solid blocks, making it a threat even when you have closed off a room. Its registered type is also fire-immune. [Movement and health][vex] · [Registered attributes][vex-attributes] · [Sword][vex-gear] · [Registration][vex-type]

## Obtaining

[Evokers](Evoker.md#vex-summons) summon Vexes in groups of three attempts, including when fighting Evokers found in a [Woodland Mansion](../structures/WoodlandMansion.md) or [raid](../mechanics/Raid.md). The summon code directly creates them, assigns an owner and adds them to the world; Vexes are not a separate entry in the raid's wave roster. [Summon caller][summon] · [Raid roster][raid-types]

For deliberate placement, use the ordinary listed [Vex Spawn Egg](../items/VexSpawnEgg.md), available through the [inventory item browser](../mechanics/InventoryBrowser.md) in Creative. Egg-created Vexes do not automatically receive the Evoker summon code's limited-life timer. Vexes are not allowed in Peaceful. [Egg listing][vex-entry] · [Egg spawn path][egg-use] · [Spawn initialization][vex-gear] · [Summon-only timer assignment][summon] · [Registration][vex-type] · [Peaceful removal][peaceful]

## Behavior

A Vex can copy its owner's target, independently select a player, or retaliate when hurt. Its charge aims toward the target's eye position; intersection with the target triggers its melee attack. The owner-target check ignores line of sight, and its movement tick enables passage through blocks and disables gravity. **Walls and ceilings do not reliably separate you from an attacking Vex.** [Target goals and movement][vex] · [Charge and owner-target logic][vex-charge]

Killing the Evoker prevents that caster from summoning more, but does not directly remove its existing Vexes. Keep watching for them while collecting the caster's drops. This follows the independent target goals and lifetime behavior; it is not an in-game-tested escape route. [Independent player targeting][vex] · [Owner lookup][vex-charge]

### Summoned lifetime

An Evoker gives each summon **600–2,380 ticks** of initial life, nominally **30–119 seconds at 20 TPS**. Expiry does **not** instantly delete the Vex: it begins taking **1 point of starvation damage every 20 ticks**. The timer and ownership are saved with the entity. This is an entity-tick limit, not a guaranteed wall-clock disappearance time while chunks are unloaded. [Timer assignment][summon] · [Expiry damage][vex] · [Saved data][vex-save]

## Drops

The bundled Vex entity loot table has **no item pools**. Its generated sword is assigned a **zero drop chance**, and the equipment-drop routine skips zero-chance slots before applying enchantment modifiers. Do not hunt ordinary Vexes for swords or assume Looting unlocks this sword drop. [Loot table][vex-loot] · [Sword and zero chance][vex-gear] · [Equipment dispatch][gear-drop]

## Notes

The mob registration, default attributes, Evoker creation call and server goal scheduler establish the active behavior reviewed here. [Registration][vex-type] · [Attributes][vex-attributes] · [Summoning][summon] · [AI caller][ai-call]

Related: [Evoker](Evoker.md) · [Vindicator](Vindicator.md) · [Combat](../mechanics/Combat.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `cf8cd5371bd1de61411ae5e7e144aabe3edb1e54` on 2026-10-02. Checked creation, owner/target behavior, movement, saved lifetime, equipment and loot. No summoned-life timing, block-passage, combat, egg-use or drop test was performed.

[vex]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/Vex.java#L73-L100
[vex-attributes]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L263
[vex-gear]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/Vex.java#L202-L217
[vex-type]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/EntityType.java#L1457-L1467
[summon]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/Evoker.java#L211-L257
[raid-types]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/raid/Raid.java#L802-L807
[vex-entry]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2111
[egg-use]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L50-L102
[peaceful]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L607-L612
[vex-charge]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/Vex.java#L219-L295
[vex-save]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/Vex.java#L108-L133
[vex-loot]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/entities/vex.json#L1-L4
[gear-drop]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L813-L838
[ai-call]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L634-L661
