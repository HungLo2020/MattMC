# Spectre

The **Spectre** is a passive flying creature that can pass through blocks and is attracted by a held [Soul Heart](../items/SoulHeart.md). It has **50 health points (25 hearts)**. Its active goals handle attraction and wandering; no attack goal is installed. [Flight and collision][flight] · [Goals][goals] · [Health][stats] · [Attribute wiring][attributes]

## Obtaining

Use the [Spectre Spawn Egg](../items/SpectreSpawnEgg.md), an ordinary category-listed item available through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in **Survival as well as Creative**. [Egg registration][egg] · [Category entry][category]

No natural encounter route was found in the checked biome spawn lists, structure spawn overrides, or active spawning references. The bundled End biome lists contain Endermen, and the End biome builder does not add Spectres. A Spectre's permissive spawn method and spawn-roll setting do not themselves add it to those lists. Do not plan an End trip around finding one naturally in this snapshot. [Spawn-list selection][spawn-selection] · [Bundled End list][end-spawns] · [End builder][end-builder] · [Entity spawn checks][spawn-checks]

## Behavior

### Attracting a Spectre

Hold a **Soul Heart in either hand**. The lure goal checks the nearest player within **64 blocks**, then checks that player's held items. A nearer player without a Heart can therefore prevent attraction to a farther holder. It does not begin when the selected player already holds the Spectre's leash. The Heart is held, not consumed; the [Soul Heart guide](../items/SoulHeart.md#usage) owns the detailed lure instructions. [Lure checks][lure]

While attracted, the Spectre requests movement toward the player's eye height. Below **2.5 blocks** it stops navigation. Those are movement requests, not a measured guarantee that it stays at a particular distance. Spectres have no breeding food, and their offspring method returns no child; this lure does not tame or breed them. [Lure movement][lure-movement] · [Food check][goals] · [Offspring][offspring]

### Flight and damage

Spectres ignore gravity, set their collision-bypass flag while ticking, and alternate between directional flight and circling above terrain. Walls therefore do not make a reliable enclosure. [Flight and collision][flight] · [Wandering goal][wander]

Ordinary damage is rejected by the active invulnerability callback. Magic, falling out of the world, Creative-player damage, and damage tagged to bypass invulnerability are exceptions to this additional protection; the base invulnerability rules still apply. The monster-category registration is not an attack instruction. [Damage filter][damage] · [Registered entity][entity] · [Goals][goals]

### Leads and transport limits

The inherited mob rules allow a lead because the Spectre is an Animal, not an Enemy. While a non-fence entity holds its lead, the Spectre's tick clears that holder's fall distance, slows downward movement, and adds a pull toward itself when the holder is more than 10 blocks away. A fence knot does not receive this transport behavior. [Leash eligibility][leashable] · [Holder movement][holder]

**Do not rely on this as safe void transport.** The current base leash tick still breaks the lead beyond its **12-block** distance threshold. Also, sneaking drops the leash before the Spectre's same tick reads the holder again without a new null check. The cleared-holder failure after crouch release is tracked in [#799](https://github.com/HungLo2020/MattMC/issues/799). The separate distance limit remains unchanged; a safe release or rescue was not verified in game. [Base leash call][leash-tick] · [Break distance][leash-break] · [Spectre release path][holder]

## Drops

No bundled `entities/spectre` loot table or species-specific drop implementation was found. The normal entity loot key falls back to an empty table if unavailable. In particular, this review does not establish Spectres as a source of Soul Hearts. [Default loot key][loot-key] · [Missing-table fallback][loot-fallback]

## Notes

- Registered as `minecraft:spectre`, using the actual Spectre factory; size **1.0 × 1.5 blocks**, category `MONSTER`. [Entity registration][entity]
- Source-reviewed at `79f20bccc697135bd56a472c64f59981dce47fe0` on 2026-10-02. Spawn/drop absence statements cover the inspected bundle, not arbitrary data packs. No game, damage, flight, attraction, leash or acquisition test was run.

Related: [Soul Heart](../items/SoulHeart.md) · [Spectre Spawn Egg](../items/SpectreSpawnEgg.md) · [Cosmaw](Cosmaw.md) · [Cosmic Cod](CosmicCod.md) · [Mobs](Mobs.md)

[spawn-selection]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450
[end-spawns]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/biome/end_highlands.json#L35-L51
[end-builder]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/data/worldgen/biome/EndBiomes.java#L15-L35
[loot-key]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-fallback]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[flight]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntitySpectre.java#L131-L150
[goals]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntitySpectre.java#L104-L112
[stats]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntitySpectre.java#L78-L80
[attributes]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L232
[egg]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/Items.java#L1950
[category]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2081
[spawn-checks]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntitySpectre.java#L57-L64
[lure]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntitySpectre.java#L362-L378
[lure-movement]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntitySpectre.java#L380-L403
[offspring]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntitySpectre.java#L182-L186
[wander]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntitySpectre.java#L255-L339
[damage]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntitySpectre.java#L114-L117
[entity]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/EntityType.java#L1185-L1187
[leashable]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/Mob.java#L1217-L1220
[holder]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntitySpectre.java#L151-L178
[leash-tick]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/Entity.java#L535-L537
[leash-break]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/Leashable.java#L157-L203
