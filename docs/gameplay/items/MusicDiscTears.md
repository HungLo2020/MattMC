# Music Disc (Tears)

**Music Disc (Tears)** (`minecraft:music_disc_tears`) is a reusable [Jukebox](../blocks/Jukebox.md) disc. Its special mob-loot route comes from a [Ghast](../mobs/Ghast.md); it is a different item from a [Ghast Tear](GhastTear.md). [Item registration][disc-item] · [Ghast disc pool][ghast-disc]

## Obtaining

With mob loot enabled, the Ghast table gives **one disc** when the killing damage is tagged as a projectile, its direct entity is `minecraft:fireball`, and the death has player attribution. **Return an incoming fireball for a direct hit** to use this route. An ordinary weapon kill, or merely seeing a Ghast die in an explosion, does not establish those conditions. Follow [returning a fireball](../mobs/Ghast.md#returning-a-fireball) for the actual deflection controls and limits. [Exact loot conditions][ghast-disc] · [Projectile damage tag][projectile-tag] · [Player-attribution condition][player-kill] · [Mob-loot gate][animal-loot]

The disc is category-listed, so MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) provides a separate **Survival and Creative** insertion route. [Category entry][disc-category]

## Usage

Insert it into a Jukebox to play **Tears**. The bundled song lasts **175 seconds (2:55)** and supplies **Comparator strength 10**. Follow [Jukebox insertion and ejection](../blocks/Jukebox.md#inserting-and-ejecting-discs) and [redstone output](../blocks/Jukebox.md#redstone-output) for operation. [Song data][disc-song]

## Behavior

The disc stacks to **one** and carries the Tears jukebox-playable component. It can be removed and reused; playback is owned by the Jukebox rather than a handheld-use action. [Item properties][disc-item]

## Notes

This disc is not consumed as a Ghast Tear ingredient. Brewing, End Crystals and Dried Ghasts use [Ghast Tears](GhastTear.md).

## Sources and verification

Source-reviewed on **2026-10-02** at `780a7733d1804088e86c248a1c8ec6957def4915`. Checked the registered playable item, category entry, song data and Ghast loot conditions. No in-game reflection, disc-drop, audio or redstone test was run. The [Ghast](../mobs/Ghast.md#sources-and-verification) and [Jukebox](../blocks/Jukebox.md#verification-scope) owners cover the active callers; data packs and game rules can change the result.

[disc-item]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/item/Items.java#L2356-L2358
[ghast-disc]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/loot_table/entities/ghast.json#L64-L99
[projectile-tag]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/tags/damage_type/is_projectile.json#L1-L13
[player-kill]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/level/storage/loot/predicates/LootItemKilledByPlayerCondition.java#L21-L28
[animal-loot]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L567
[disc-category]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1570
[disc-song]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/jukebox_song/tears.json#L1-L8
