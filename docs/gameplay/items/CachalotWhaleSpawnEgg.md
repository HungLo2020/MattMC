# Cachalot Whale Spawn Egg

The **Cachalot Whale Spawn Egg** (`minecraft:cachalot_whale_spawn_egg`) creates a [Cachalot Whale](../mobs/CachalotWhale.md). It is available in Creative. No bundled crafting recipe or Survival loot source was found, and the egg does not establish natural whale spawning. [Registration][item] · [Creative listing][creative]

## Spawning a whale

Use the egg on an ordinary block surface or a source-water block through the egg's fluid-use path. Choose open water with access to air: the whale's registered main body is **5 × 4.5 blocks**, and its visible body extends beyond that. Consult its [care and building-damage guide](../mobs/CachalotWhale.md) before putting it in a small aquarium. [Egg use][egg] · [Registered size][registration]

Normal individual spawning calls the whale's initialization, which fills its air supply and rolls a **1% albino chance**. The albino variant has higher health and attack attributes. A fresh individual egg spawn does not use the whale's later group-member baby chance. Custom egg data can alter these defaults. [Creation caller][create] · [Whale initialization][spawn] · [Group age handling][age]

Successful ordinary use consumes one egg outside Creative. The separate spawner-block path depends on the server's spawner setting. Neither ordinary egg use nor food feeding assigns a tame owner. [Use and spawner gate][egg] · [Consumption][consume] · [Whale interaction][whale]

## Using on a whale

Use a matching egg directly on an existing Cachalot Whale to create a **baby whale**. This calls the actual offspring factory and copies that existing whale's **albino flag**. An ordinary egg used on an albino parent therefore produces an albino baby through this route, rather than making a new 1% roll. [Mob dispatch][mob] · [Offspring factory][offspring] · [Baby helper][baby]

This requires neither breeding food nor a second adult. The mob has no accepted food, so the egg route is separate from ordinary breeding, which cannot be started by feeding in the checked implementation. Baby whales are targets for [Orcas](../mobs/Orca.md); house them accordingly. [Food check][whale] · [Orca target][orca]

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Checked registration, Creative listing, ordinary and fluid egg paths, initialization, variant inheritance, matching-mob dispatch, offspring creation, and consumption. No in-game spawn, variant-frequency, or enclosure test was run.

Related: [Cachalot Whale](../mobs/CachalotWhale.md) · [Giant Squid Spawn Egg](GiantSquidSpawnEgg.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L1813
[creative]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1989
[egg]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L49-L128
[registration]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java#L332-L334
[create]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java#L1736-L1774
[spawn]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityCachalotWhale.java#L850-L858
[age]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/AgeableMob.java#L33-L48
[consume]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1080
[whale]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityCachalotWhale.java
[mob]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Mob.java#L1088-L1103
[offspring]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityCachalotWhale.java#L835-L843
[baby]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L184
[orca]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityOrca.java#L155
