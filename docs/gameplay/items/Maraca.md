# Maraca

Use a **Maraca** (`minecraft:maraca`) on a living [Cockroach](../mobs/Cockroach.md#maracas-dancing-and-shearing) to equip it and make it dance. Retrieve it by interacting empty-handed. The item has an active mob interaction even though its own registration is a plain Item. [Item][item] · [Interaction][interaction] · [Dancing][dance]

## Obtaining

- **Already-equipped Cockroach:** interact empty-handed to make it drop one Maraca. This works in Survival if such a Cockroach is present; it does not establish how the first equipped animal or item entered that world. [Recovery][interaction]
- **Authorized command:** permission level **2** can use `/give @s minecraft:maraca`. No Maraca entry was found in the checked category lists, so the ordinary [inventory browser](../mechanics/InventoryBrowser.md#which-items-appear) is not a demonstrated starting route. [Give][give] · [Command registration][give-registration] · [Registry lookup][item-parser] · [Category lists][creative-tabs] · [Browser list][browser-list]

No bundled crafting output or structure inventory supplying a Maraca was found. Cockroach death-loot selection names `minecraft:entities/cockroach`, `minecraft:entities/cockroach_maracas` and `minecraft:entities/cockroach_maracas_headless`; all three exact `loot_table/entities/*.json` files are absent from ordinary and optional-pack data. Recover it by interaction rather than relying on a death drop. [Loot selection][loot] · [Bundled data][data] · [Optional packs][packs]

## Usage

1. Use the Maraca on a living Cockroach that does not already have one. This spends **one** in Survival; the normal player interaction restores the count for infinite-materials/Creative players. [Equip][interaction] · [Creative restoration][player]
2. To recover it, use an **empty hand**. The server drops **one ordinary Maraca** beside the mob and clears its equipped and dancing flags. The mob saves a boolean, not the supplied stack, so a custom name or other supplied item components are not preserved. [Recovery][interaction] · [Saved state][save]
3. If the Cockroach is leashed to you, the first interaction releases the Lead. Name Tags and spawn eggs can also take precedence over the Maraca callback; empty-handed interaction avoids those item actions. [Mob dispatch][dispatch] · [Lead priority][lead]

Holding another Maraca does not retrieve the equipped one. Throwing one on the ground is also not the normal equip route: the dropped-item goal requires food or membership in `minecraft:cockroach_breedables`; ordinary Maraca has neither, and that exact item-tag file is absent. [Interaction][interaction] · [Pickup filter][pickup-filter] · [Mob item filter][item-filter] · [Item registration][item] · [Bundled tags][data]

## Behavior

An equipped Cockroach dances, suppresses travel and encourages nearby Cockroaches to dance. The emitted custom Maraca music event has an empty playback handler here; see the [Cockroach guide](../mobs/Cockroach.md#maracas-dancing-and-shearing) for follower and Jukebox limits. The item stacks to **64**, has common rarity, and adds no food or durability component. [Dance][dance] · [Travel][travel] · [Music handler][music] · [Item][item] · [Defaults][defaults]

## Notes

New Cockroaches start with `Maracas=false`. No Cockroach-specific spawn initializer equips it: inherited spawn setup handles age, follow range and handedness. Breeding only adds the breaded state; goals do not create a Maraca. Saved `Maracas=true` data can restore an equipped animal, but no such state was found in the bundled JSON or decoded structures. This is a starting-supply limitation, not a denial of the working recovery interaction. [Default state][state] · [Ageable spawn][ageable-spawn] · [Mob spawn][mob-spawn] · [Offspring][offspring] · [Goals][goals] · [Saved state][save]

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`; no in-game acquisition or use test was run. The bundled-resource review enumerated `minecraft` recipe and loot filenames, parsed their contents and nested references, resolved item tags, and decoded structure inventories. It covered the ordinary data and bundled optional packs; external server data packs can change the result. [Ordinary data][data] · [Optional packs][packs]


Related: [Cockroach](../mobs/Cockroach.md) · [Cockroach Spawn Egg](CockroachSpawnEgg.md) · [Commands](../commands/Commands.md) · [Items](Items.md)

[ageable-spawn]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/AgeableMob.java#L31-L49
[browser-list]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[creative-tabs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[dance]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityCockroach.java#L298-L347
[defaults]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Mob.java#L1051-L1103
[give]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/commands/GiveCommand.java#L22-L67
[give-registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/commands/Commands.java#L205-L209
[goals]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityCockroach.java#L119-L139
[interaction]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityCockroach.java#L198-L214
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2061
[item-filter]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityCockroach.java#L402-L405
[item-parser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/commands/arguments/item/ItemParser.java#L149-L155
[lead]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Entity.java#L2146-L2159
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityCockroach.java#L177-L183
[mob-spawn]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Mob.java#L1010-L1024
[music]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityCockroach.java#L350-L356
[offspring]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityCockroach.java#L375-L383
[pickup-filter]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/ai/CreatureAITargetItems.java#L54-L68
[player]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L854-L889
[save]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityCockroach.java#L161-L175
[state]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityCockroach.java#L217-L247
[travel]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityCockroach.java#L408-L416
[data]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft
[packs]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/datapacks
