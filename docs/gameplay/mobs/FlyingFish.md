# Flying Fish

**Flying Fish** are small passive fish that leap out of water and glide above the surface. They have **6 health points (3 hearts)** and a **0.4 × 0.3-block body**. Keep them in a contained pool: gliding does not let them survive indefinitely out of water, and their bucket currently does not preserve their appearance reliably. [Registration][fish-id] · [Attributes][fish-stats] · [Attribute wiring][fish-attributes]

## Obtaining

Use a [Flying Fish Spawn Egg][egg] or release a [Bucket of Flying Fish][bucket]. Both items are category-listed and obtainable from MattMC's [inventory item browser][browser] in **Creative**. Their ordinary placement paths create a fish in the world. [Item registration][fish-items] · [Egg listing][fish-egg-category] · [Bucket listing][fish-bucket-category] · [Egg placement][egg-place] · [Bucket release][bucket-release]

**No natural biome or structure spawn route was found** in the checked active source, 68 bundled biome definitions, 34 structure definitions and 1,202 structure templates. The mob has no Flying Fish entry in SpawnPlacements; its permissive spawn-rule method does not establish a population. Do not assume an ocean habitat or Survival source from the name or an upstream mod guide. [Biome-spawn code][worldgen-code] · [Bundled data][data] · [Placement registry][placements] · [Species rule][fish-stats]

## Behavior

### Swimming and gliding

Flying Fish swim, seek nearby water when stranded and have a panic goal. Their installed goals do not include attacking players or hunting other mobs. When a suitable visible stretch of water surface is found, they can swim toward it, launch upward and glide. During a descending glide, their downward motion is halved each tick. They can land outside a pool, so leave swimming space and contain the surrounding area. [Goals][fish-goals] · [Glide search and movement][fish-glide] · [Tick behavior][fish-tick]

On land they occasionally flop upward. Ordinary movement's fall-damage check is overridden with an empty method, but this does not protect them from running out of air. [Flopping][fish-tick] · [Fall check][fish-travel] · [Active movement caller][fall-dispatch]

### Care, food and persistence

Keep Flying Fish **in water**. The active WaterAnimal air routine restores **300 air** in water; after about **320 dry ticks (16 seconds at 20 TPS)** it deals the first **2-point hit**, followed by continued damage. The Flying Fish class also contains an older one-argument air method that mentions 1,000 air, but the current base tick calls a different signature, so that longer allowance is not active. [Active air routine][water-air] · [Damage threshold][air-threshold] · [Unused overload][fish-old-air]

They cannot be leashed and have no feeding, taming or food-breeding interaction. They are WaterAnimal mobs rather than ageable animals. Using a matching spawn egg directly on one does not produce a baby, because the shared helper requires baby state that this mob does not implement. [Leash rule][water-leash] · [Class and goals][fish-goals] · [Interaction][fish-interact] · [Top-level interaction][interact-dispatch] · [Egg helper][egg-baby] · [Baby setter][baby-setter] · [Baby state][baby-state]

A fish released from a bucket, or one with a custom name, avoids its ordinary distance-removal rule. An unnamed fish without the bucket flag can despawn. Normal world saving preserves its variant and bucket-origin flag. [Persistence][fish-persistence] · [World save][fish-save]

### Variants and buckets

There are **three visual variants**, selected with equal probability during ordinary individual initialization. Group initialization can reuse a shared variant, but no natural school-spawn route is established here. The active renderer uses a separate texture for each variant. [Initialization][fish-variants] · [Renderer][fish-render] · [Renderer registration][fish-render-registry]

Use a **Water Bucket on a living fish** to capture it. Normal hand release puts a fish back into the world and restores its saved health and custom name, but **rerolls the visual variant**: capture writes the variant to a component that the active bucket reader does not use to restore it. Do not capture a particular appearance expecting to keep it. The [Bucket of Flying Fish guide][bucket] explains placement, saved data and the Dispenser limitation. [Capture][bucket-capture] · [Species data][fish-bucket] · [Common data reader][bucket-common] · [Release][bucket-release] · [Initialization order][entity-create]

## Drops

The default `minecraft:entities/flying_fish` loot table is absent from the bundled entity loot directory. The missing-table fallback is empty, so no species-specific death drops are configured in this snapshot. A qualifying player-credited kill can still award **1–3 base XP** with `doMobLoot` enabled. [Default loot name][default-loot] · [Bundled loot][loot-data] · [Fallback][loot-fallback] · [XP amount][water-xp] · [XP conditions][xp-gate]

## Notes

* Entity ID: `minecraft:flying_fish`; spawn egg ID: `minecraft:flying_fish_spawn_egg`
* The active class is `net.alexsmobs.entity.EntityFlyingFish`, registered in `MobCategory.WATER_AMBIENT` from integrated Alex's Mobs content [Entity registration][fish-id] · [Item registration][fish-items]

Related: [Flying Fish Spawn Egg][egg] · [Bucket of Flying Fish][bucket] · [Mobs][mobs]

Source-reviewed at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716` on 2026-10-03. Registration, spawn routes, AI/air dispatch, variants, bucket components and loot fallback were traced. No in-game spawn, glide, air or capture/release test was run.

[fish-id]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L628-L634
[fish-stats]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityFlyingFish.java#L108-L114
[fish-attributes]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L166
[egg]: ../items/FlyingFishSpawnEgg.md
[bucket]: ../items/BucketOfFlyingFish.md
[browser]: ../mechanics/InventoryBrowser.md
[fish-items]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1871-L1876
[fish-egg-category]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2018
[fish-bucket-category]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1443
[egg-place]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L76-L103
[bucket-release]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/MobBucketItem.java#L28-L52
[worldgen-code]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/data/worldgen/BiomeDefaultFeatures.java
[data]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data
[placements]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L78-L178
[fish-goals]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityFlyingFish.java#L48-L70
[fish-glide]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityFlyingFish.java#L321-L427
[fish-tick]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityFlyingFish.java#L116-L157
[fish-travel]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityFlyingFish.java#L181-L204
[fall-dispatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L741-L745
[water-air]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/WaterAnimal.java#L39-L58
[air-threshold]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L491-L493
[fish-old-air]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityFlyingFish.java#L168-L179
[water-leash]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/WaterAnimal.java#L65-L68
[fish-interact]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityFlyingFish.java#L304-L308
[interact-dispatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1053-L1111
[egg-baby]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L184
[baby-setter]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1264-L1265
[baby-state]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L532-L534
[fish-persistence]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityFlyingFish.java#L80-L86
[fish-save]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityFlyingFish.java#L250-L260
[fish-variants]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityFlyingFish.java#L290-L317
[fish-render]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/client/render/RenderFlyingFish.java#L12-L49
[fish-render-registry]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/entity/EntityRenderers.java#L161
[bucket-capture]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L76-L94
[fish-bucket]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityFlyingFish.java#L262-L288
[bucket-common]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L29-L77
[entity-create]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L1750-L1775
[default-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L2063-L2066
[loot-data]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/entities
[loot-fallback]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[water-xp]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/WaterAnimal.java#L34-L37
[xp-gate]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1480-L1486
[mobs]: Mobs.md
