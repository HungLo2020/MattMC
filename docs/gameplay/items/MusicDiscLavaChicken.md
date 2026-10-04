# Music Disc (Lava Chicken)

**Music Disc (Lava Chicken)** (`minecraft:music_disc_lava_chicken`) is a reusable [Jukebox](../blocks/Jukebox.md) disc obtained from a qualifying **baby Zombie riding a Chicken**. The Zombie is the disc-dropping mob; the Chicken is its separate mount. [Item registration][disc-item] · [Zombie disc pool][disc-loot]

## Obtaining

With mob loot enabled, defeat an ordinary **baby [Zombie](../mobs/Zombie.md) while it is still riding a [Chicken](../mobs/Chicken.md#movement-and-chicken-jockeys)** and give the death player attribution. The disc's separate loot pool checks all three facts: player attribution, the Zombie's baby state, and a current vehicle whose type is `minecraft:chicken`. It then makes **one roll yielding one disc**, with **no additional random-chance test**. Looting does not add discs, and this pool does not require a particular weapon. [Exact pool][disc-loot] · [Monster mob-loot gate][loot-gate] · [Player-attribution predicate][player-kill]

**Keep the mount alive and the Zombie aboard until the rider dies.** The predicate reads the Zombie's current vehicle during death-loot evaluation; it does not remember that the Zombie rode a Chicken earlier. The checked death callback evaluates loot before the later removal that clears riding. A Chicken's saved jockey flag alone is not the disc condition. [Current vehicle test][vehicle] · [Death-loot ordering][death] · [Later removal][removal] · [Jockey creation][jockey]

A direct player kill is the straightforward attribution route. Internally, player damage records a **100-tick attribution window**, and a tame Wolf can credit its owner; the loot caller still needs a resolvable player and positive remaining attribution time. This is a player-credit rule, not a requirement for the final hit to use a sword. [Damage attribution][attribution] · [Attribution countdown][attribution-tick] · [Death-loot context][loot-context]

The disc is also category-listed for the **Creative [inventory item browser](../mechanics/InventoryBrowser.md)**. [Category entry][creative]

## Usage

Insert it into an empty Jukebox to select **Lava Chicken** (`minecraft:lava_chicken`). Its bundled song record has a **configured length of 134 seconds (2:14)** and **Comparator strength 9**. These are configured values, not a measured audio duration; the Jukebox reads the inserted song for its Comparator output. [Song data][song] · [Playable component][component] · [Comparator consumer][comparator]

Follow [Jukebox insertion and ejection](../blocks/Jukebox.md#inserting-and-ejecting-discs) to retrieve the disc, and [redstone output](../blocks/Jukebox.md#redstone-output) for circuits and the separate playing signal.

## Behavior

The item **stacks to one** and carries the Lava Chicken jukebox-playable component. The disc is reusable, and the Jukebox keeps it inserted after the song finishes. [Item properties][disc-item] · [Item storage][comparator] · [Song-player finish][player]

## Notes

The [Zombie drops guide](../mobs/Zombie.md#drops) owns the rider's other rewards. The Chicken's [death drops](../mobs/Chicken.md#death-drops-and-experience) and its jockey flag are separate; killing only the mount does not evaluate this Zombie disc pool.

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked the registered Zombie loot route, baby/current-vehicle predicate, death/removal ordering, player attribution and monster loot gate, category entry, playable component, loaded song record and Comparator consumer. No jockey encounter, combat, loot-frequency, gameplay, listening, audio-duration or redstone test was run. Data packs, entity overrides and game rules can change the result.

[disc-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2317-L2319
[disc-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/zombie.json#L106-L134
[loot-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/Monster.java#L130-L133
[player-kill]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/predicates/LootItemKilledByPlayerCondition.java#L21-L28
[vehicle]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/advancements/critereon/EntityPredicate.java#L135-L145
[death]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1413-L1475
[removal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Entity.java#L3836-L3848
[jockey]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/Zombie.java#L480-L506
[attribution]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1324-L1339
[attribution-tick]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L464-L468
[loot-context]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1528
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1571
[song]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/jukebox_song/lava_chicken.json
[component]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L406-L408
[comparator]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/JukeboxBlockEntity.java#L67-L119
[player]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/JukeboxSongPlayer.java
