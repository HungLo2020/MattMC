# Rhinoceros

Rhinoceros is a heavily armored animal that can learn to trust up to two players. Trust changes its targeting and enables defensive behavior; it does not make the animal an owned, rideable pet. An untrusted adult can attack a player who approaches closely, even without being struck first.

## At a glance

| Property | Source value |
| --- | --- |
| Entity ID | `minecraft:rhinoceros` |
| Health | 60 points (30 hearts) |
| Armor | 12 points |
| Armor toughness | 4 |
| Base attack attribute | 8 points (4 hearts), before modifiers |
| Registered size | 1.8 blocks wide × 2 blocks tall |

Its active grass-eating animation converts the Grass Block under it to Dirt and heals **10 health points**. Keep that behavior in mind when designing an enclosure.

## Obtaining

Use a [Rhinoceros Spawn Egg](../items/RhinocerosSpawnEgg.md), explicitly listed in Creative, or summon `minecraft:rhinoceros` with command permission.

**Natural spawning is not established.** No Rhinoceros entry was found in the reviewed biome spawn data or spawn-placement registrations. The animal's spawn-roll check is active code, but a random acceptance test does not place it in a biome. This guide therefore does not assign it a natural habitat from another version or mod.

## Trust and feeding

The bundled trust-food tag contains **Wheat, Hay Bale, Apple, Carrot, Sugar Cane, Bamboo, and Sweet Berries**. Holding one of these foods can attract the animal.

For a straightforward trust interaction, use **[Bamboo](../items/Bamboo.md) or [Sweet Berries](../items/SweetBerries.md)**. These are trust foods without being in the bundled breeding-food tag. An untrusted player's feeding interaction can add that player to the animal's saved trust list, which has **two slots**.

Important limits:

- Once the animal trusts anyone, its simple nearby-player targeting goal stops acquiring new players. Other combat and retaliation goals still exist
- It can respond to attacks involving a trusted player, including targets that player attacks
- A third player's food can still be consumed when both trust slots are occupied, without adding that player
- Wheat, Hay Bale, Apple, Carrot, and Sugar Cane overlap with breeding food. Ordinary animal feeding runs first, so a single-item stack can be consumed before trust is checked, while a larger stack can lose an additional item to the trust branch

There are no pet sit/follow commands or player-mounting interaction in this class. Trust is saved, but a calf's creation path does not copy its parents' trust list.

## Breeding

The bundled breeding foods are **Wheat, Hay Bale, Apple, Carrot, and Sugar Cane**. Feed eligible adults to enter love mode; the registered breeding goal creates a live Rhinoceros calf. Normal animal feeding also accelerates baby growth.

These are distinct tag checks: Bamboo and Sweet Berries are useful for trust and attraction, but are not breeding foods in the bundled data. Data packs can change either list.

## Combat and potion coating

An adult that is neither in love nor trusting anyone has a nearby-player acquisition distance of **3 blocks**. This is not a safe escape boundary after a fight starts. Adults also target Raiders and retaliate when hurt.

Animated attacks fling or slash the main target and can hit nearby **untrusted, non-animal living entities** as collateral damage. The animated main-target damage uses 8 points normally and 10 against Raiders. Do not treat these numbers as a complete damage-per-attack total: the active ordinary melee goal and the animation-tick damage path both exist.

An adult accepts a regular, splash, or lingering potion through its interaction method when the base potion has an effect. It stores **only that base potion's first effect**, including its amplifier and duration, for later animated hits. It consumes the potion and returns a Glass Bottle. This is not a promise that every custom potion effect behaves correctly.

The stored coating counts newly applied effects and eventually wears off; its saved duration is the duration inflicted on a target, not a countdown for the coating itself. **Do not rely on an ordinary Water Bottle to clear it:** the current water comparison uses incompatible object forms and does not match the registered water potion as intended. This specific clearing failure is tracked in [issue #783](https://github.com/HungLo2020/MattMC/issues/783).

## Drops and integration notes

No dedicated Rhinoceros death-loot table or registered Rhino Horn item was found in the checked source. This guide establishes no horn or other species-specific death drop.

The class's older one-argument melee method is not the method invoked by the current melee goal. Its animation-driven attacks are still triggered separately in the entity tick, so the mismatch does not establish harmlessness. Use a safe test world before experimenting with potion coating or combat behavior.

## Related pages

- [Komodo Dragon](KomodoDragon.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game spawning, trust, breeding, potion, combat, or drop test was run.

- [Stats, trust, food, potion interactions, and active animation damage](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityRhinoceros.java)
- [Trust-food tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/rhinoceros_foodstuffs.json)
- [Breeding-food tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/rhinoceros_breedables.json)
- [Ordinary animal feeding and breeding](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Animal.java)
- [Current melee dispatch](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/goal/MeleeAttackGoal.java#L127-L133)
- [Registered Water potion holder](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/alchemy/Potions.java#L11)
- [Item registry](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java)
- [Entity registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/EntityType.java)
- [Attribute registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java)
- [Creative spawn eggs](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)
- [Spawn-placement registrations](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/SpawnPlacements.java)
- [Biome data](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome)
- [Entity loot tables](https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities)
