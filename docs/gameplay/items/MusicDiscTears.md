# Music Disc (Tears)

**Music Disc (Tears)** (`minecraft:music_disc_tears`) is a reusable [Jukebox](../blocks/Jukebox.md) disc. Its special mob-loot route comes from a [Ghast](../mobs/Ghast.md); it is a different item from a [Ghast Tear](GhastTear.md). [Item registration][disc-item] · [Ghast disc pool][ghast-disc]

## Obtaining

With mob loot enabled, the Ghast table gives **one disc** when the killing damage is tagged as a projectile, its direct entity is `minecraft:fireball`, and the death has player attribution. The separate disc pool rolls once with a fixed count of one: **there is no additional random-chance test, and Looting does not increase that count**. An ordinary weapon kill or explosion-only kill does not satisfy the direct-fireball projectile condition. [Exact loot conditions][ghast-disc] · [Projectile damage tag][projectile-tag] · [Player-attribution condition][player-kill] · [Mob-loot gate][animal-loot]

**Return an incoming fireball for a direct hit** to use this route. The player attack path redirects a tagged fireball along the player's look direction and assigns the player as its owner. A direct Large Fireball hit supplies that owner to the damage source, allowing the damage and death callers to record player attribution. The Ghast also has a special damage response to a player-owned Large Fireball. Follow [returning a fireball](../mobs/Ghast.md#returning-a-fireball) for aiming and combat limits. [Attack dispatch][attack] · [Deflection and ownership][deflect] · [Direct-hit damage][fireball] · [Player attribution and death context][attribution] [loot-context] · [Ghast response][ghast-response]

Player attribution is checked through the death's loot context; the disc pool does not test a particular held weapon. Returning a fireball is the source-verified practical route through those conditions, not a guarantee that any nearby fireball explosion awards a disc. [Damage-source predicate][damage-condition] · [Death-loot context][loot-context]

The disc is category-listed, so MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) provides a separate **Creative** insertion route. [Category entry][disc-category]

## Usage

Insert it into an empty Jukebox to select **Tears** (`minecraft:tears`). The bundled song has a **configured length of 175 seconds (2:55)** and **Comparator strength 10**. These are song-data values, not an audio measurement. The Jukebox reads the inserted song's value for its Comparator output. Follow [Jukebox insertion and ejection](../blocks/Jukebox.md#inserting-and-ejecting-discs) and [redstone output](../blocks/Jukebox.md#redstone-output) for operation. [Song data][disc-song] · [Playable component][component] · [Comparator consumer][comparator]

## Behavior

The disc **stacks to one** and carries the Tears jukebox-playable component. It can be removed and reused; the Jukebox keeps the inserted item after playback finishes. Playback is owned by the Jukebox rather than a handheld-use action. [Item properties][disc-item] · [Item storage][comparator] · [Song-player finish][player]

## Notes

This disc is not consumed as a Ghast Tear ingredient. Brewing, End Crystals and Dried Ghasts use [Ghast Tears](GhastTear.md).

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked the registered playable item, category entry, loaded song record and Comparator consumer, Ghast loot predicates and mob-loot gate, player deflection/ownership, direct-fireball damage and death-loot attribution. No in-game reflection, combat, disc-drop, listening, audio-duration or redstone test was run. The [Ghast](../mobs/Ghast.md#sources-and-verification) and [Jukebox](../blocks/Jukebox.md#verification-scope) owners cover shared operation; data packs and game rules can change the result.

[disc-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2356-L2358
[ghast-disc]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/ghast.json#L64-L99
[projectile-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/damage_type/is_projectile.json#L1-L13
[player-kill]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/predicates/LootItemKilledByPlayerCondition.java#L21-L28
[animal-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L567
[attack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L974-L987
[deflect]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L250-L258
[fireball]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/LargeFireball.java#L25-L49
[attribution]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1324-L1339
[loot-context]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1528
[ghast-response]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/Ghast.java#L78-L109
[damage-condition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/advancements/critereon/DamageSourcePredicate.java#L30-L47
[disc-category]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1570
[disc-song]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/jukebox_song/tears.json#L1-L8
[component]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L406-L408
[comparator]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/JukeboxBlockEntity.java#L67-L119
[player]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/JukeboxSongPlayer.java
