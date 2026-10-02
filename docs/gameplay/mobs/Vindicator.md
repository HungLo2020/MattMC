# Vindicator

A Vindicator (`minecraft:vindicator`) is a hostile illager with **24 health points (12 hearts)** and an Iron Axe. It pursues targets for melee attacks; keep room to retreat rather than opening an unexplored room at arm's length. [Goals and health][vindicator] · [Registered attributes][vindicator-attributes] · [Equipment][vindicator-gear]

## Obtaining

The checked encounter routes are **[Woodland Mansion](../structures/WoodlandMansion.md) Warrior markers** and **[raid waves](../mechanics/Raid.md#waves-and-difficulty)**. Mansion residents are made persistent when placed. Raids create their own Vindicators and can add mounted riders; the raid guide owns wave counts and difficulty rules. [Mansion creation][markers] · [Raid creation][raid-call] · [Raid types][raid-types]

Its ordinary listed [spawn egg](../items/VindicatorSpawnEgg.md) is also available through the [inventory item browser](../mechanics/InventoryBrowser.md) in Survival and Creative, independently of structure/event encounters. Vindicators are not allowed in Peaceful. [Egg listing][vindicator-entry] · [Registration][vindicator-type] · [Peaceful removal][peaceful]

## Behavior

Vindicators target players, villagers and Iron Golems, retaliate when attacked and can alert other raiders. Their shared illager rules exclude baby villagers as attack targets and provide alliances with unteamed illager-friend entities. They also have an avoidance goal for nearby Creakings. [Goals][vindicator] · [Shared target/alliance rules][illager]

### Doors during raids

A Vindicator's door-opening goal requires an active raid, and its navigation's door setting follows whether its position is raided. Its door-breaking goal also requires an active raid, **Normal or Hard difficulty**, and the shared `mobGriefing` check when it begins. Do not treat a closed wooden door as guaranteed protection for villagers during a raid. This is raid-specific behavior, not a claim that every mansion Vindicator constantly breaks doors. [Navigation and goals][vindicator] · [Open-door gate][illager] · [Break-door gate][door] · [Difficulty/rule check][break-door]

### The Johnny name

Setting the exact custom name **`Johnny`** enables an extra goal that searches for attackable living entities. The goal still goes through normal target eligibility and alliance checks; it is not a promise to attack literally every entity. **Renaming it afterward does not clear the Johnny flag** in the checked name setter, and the flag is saved. Keep such a Vindicator away from animals you want to preserve. [Name setter][johnny] · [Extra target goal][johnny-target] · [Saved flag][johnny-save] · [Illager constraints][illager]

## Drops and equipment

The ordinary equipment route gives an Iron Axe. Raids equip their own axe and may enchant it according to the raid's enchantment chance and wave. This changes the fight; it is not a guaranteed enchanted-axe reward. [Default axe][vindicator-gear] · [Raid axe][raid-axe]

The entity loot table has a player-kill pool for **0–1 Emerald before Looting**, with a Looting increase. The equipped axe is a separate equipment drop: its ordinary base chance is **8.5%** for an eligible player-credited death, before enchantment modifiers and other equipment rules. Mob loot must be enabled. Vindicators do not have the Evoker's Totem pool. [Emerald table][vindicator-loot] · [Default chance][drop-chance] · [Equipment drop checks][gear-drop] · [Mob-loot gate][loot-rule] · [Death dispatch][loot-call]

## Notes

The entity registration, default attributes and server goal scheduler are active sources for this guide. [Registration][vindicator-type] · [Attributes][vindicator-attributes] · [AI caller][ai-call]

Related: [Evoker](Evoker.md) · [Vex](Vex.md) · [Raids](../mechanics/Raid.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `cf8cd5371bd1de61411ae5e7e144aabe3edb1e54` on 2026-10-02. Checked mansion/event creation, active AI, raid door gates, Johnny persistence, axe equipment and death loot. No combat, door, naming, spawning or loot test was performed; the tactical advice is source-based.

[vindicator]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/Vindicator.java#L49-L94
[vindicator-attributes]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L265
[vindicator-gear]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/Vindicator.java#L124-L142
[markers]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/level/levelgen/structure/structures/WoodlandMansionPieces.java#L1200-L1260
[raid-call]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/raid/Raid.java#L499-L565
[raid-types]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/raid/Raid.java#L802-L807
[vindicator-entry]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2113
[vindicator-type]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/EntityType.java#L1471-L1479
[peaceful]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L607-L612
[illager]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/AbstractIllager.java#L26-L60
[door]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/Vindicator.java#L184-L207
[break-door]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/ai/goal/BreakDoorGoal.java#L26-L38
[johnny]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/Vindicator.java#L144-L150
[johnny-target]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/Vindicator.java#L209-L224
[johnny-save]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/Vindicator.java#L96-L117
[raid-axe]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/monster/Vindicator.java#L167-L182
[vindicator-loot]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/resources/data/minecraft/loot_table/entities/vindicator.json#L1-L41
[drop-chance]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/DropChances.java#L24-L44
[gear-drop]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L813-L838
[loot-rule]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L565-L567
[loot-call]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1475
[ai-call]: https://github.com/HungLo2020/MattMC/blob/cf8cd5371bd1de61411ae5e7e144aabe3edb1e54/src/main/java/net/minecraft/world/entity/Mob.java#L634-L661
