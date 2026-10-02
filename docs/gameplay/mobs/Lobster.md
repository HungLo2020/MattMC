# Lobster

**Lobsters** (`minecraft:lobster`) are small bottom-walking aquatic mobs with six color variants. They can retaliate at close range and fit in a Water Bucket, but **bucket release currently rerolls their color**, and they take air-supply damage on land. A Lobster has **5 health points (2½ hearts)**, **2 armor points**, and a registered body **0.9 × 0.5 blocks**. [Registration][l-id] · [Attributes and goals][l-stats] · [Attribute wiring][l-attr]

## Obtaining

Both the [Lobster Spawn Egg](../items/LobsterSpawnEgg.md) and [Bucket of Lobster](../items/BucketOfLobster.md) are ordinary category-listed items. MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) can supply them in **Survival as well as Creative**. Egg and bucket release both reach active entity-creation paths. [Egg registration][l-egg] · [Egg category][l-category] · [Bucket registration][l-bucket] · [Bucket category][l-bucket-list] · [Egg creation][egg-placement] · [Bucket creation][standard-release]

**No natural biome or structure spawn route was found** in the checked 68 biome definitions, 34 structure definitions and 1,202 templates. The class's sand-or-water predicate is not installed in SpawnPlacements; its permissive spawn-rule method and group-size limits do not create a natural population. [Bundled data][bundled-data] · [Placement table][spawn-rules] · [Standalone predicate][l-water] · [Rule and group methods][l-stats]

## Behavior

Lobsters wander near the bottom and seek water. Their active water movement tends downward when they are not jumping, and their water preference does not request an ordinary trip onto land. This does not make a dry tank safe. [Goals][l-stats] · [Water movement][l-travel] · [Water preference][l-water]

A hurt Lobster can acquire its attacker as a target. Within about one block, its tick loop invokes the normal attack handler with a **2-point base attack**, then waits 20 ticks before another such attack. A later animation check can attempt another 2-point hit at close range; normal damage immunity and defenses still decide what is accepted, so these checks are not a guaranteed four-damage combo. [Target goal and attributes][l-stats] · [Attack loop][l-attack] · [Active damage handler][mob-attack]

### Keeping it alive

Keep the Lobster in water. Its inherited WaterAnimal tick has an active dry-air damage path. The empty one-argument `handleAirSupply` in the Lobster class does **not** override the current two-argument method called by WaterAnimal. With ordinary full air, a dry Lobster reaches its first **2-point hit after about 320 ticks**, or 16 seconds at 20 TPS, then keeps taking damage. [Unused overload][l-air] · [Active base tick][water-base] · [Default air and threshold][air-max] [Drowning threshold][air-damage]

Lobsters cannot be leashed and have no food-breeding or tame-owner interaction. They are WaterAnimal mobs, not ageable animals; their matching egg does not create a baby through the shared egg helper. Capture and release, naming and confinement are separate from taming. [WaterAnimal leash rule][water-lead] · [Class and goals][l-stats] · [Actual interaction][l-bucket-data] · [Baby helper][egg-baby] · [Default baby setter][baby-setter] · [Default baby state][baby-state]

A released bucket Lobster has the bucket-origin persistence flag, and a named Lobster also avoids the species' ordinary distance-removal rule. An unnamed Lobster without that flag may despawn. Its normal world save preserves the variant and bucket flag. [Persistence checks][l-bucket-data] · [World save][l-save]

## Colors

The active renderer selects red, blue, yellow, red-and-blue, black or white textures. Ordinary initialization uses one random value and these approximate shares:

| Variant | Approximate share of initialization rolls |
| --- | --- |
| Red | 75% |
| Blue | 15% |
| Yellow | 5% |
| Red-and-blue | 4.998% |
| Black | 0.001% |
| White | 0.001% |

The very rare values are rounded descriptions of the source's floating-point thresholds, not measured encounter frequencies. There is no verified natural population to attach these percentages to. [Initialization thresholds][l-variants] · [Renderer selection][l-render] · [Renderer registration][l-render-register]

### Bucket color limitation

Use a **Water Bucket** on a living Lobster to capture it. The capture correctly stores common health/name data, but its variant is written to `CUSTOM_DATA`. The standard MobBucketItem release reads `BUCKET_ENTITY_DATA`, so it does not restore that saved color. A released Lobster keeps the color selected by its new initialization roll. Do not bucket a rare color expecting to preserve it. [Capture interaction and saved data][l-bucket-data] · [Common capture][bucket-capture] · [Component reader][bucket-component] · [Release caller][standard-release] · [Initialization][l-variants]

The [Bucket of Lobster guide](../items/BucketOfLobster.md) owns release placement and the current round-trip limits. A plain browser bucket also gets a fresh variant; it is not an encoded guarantee of a red Lobster.

## Drops

The registered default `entities/lobster` loot table is missing, so the checked death-loot path does not generate [Lobster Tail](../items/LobsterTail.md). That food item is separately registered and category-listed, and it has working cooking recipes. A kill is not the verified way to obtain the tail in this snapshot. [Default loot mapping][default-loot] · [Bundled entity loot][loot-data] · [Missing-table fallback][loot-fallback] · [Food registration][l-food-items] · [Food category][l-food-list]

A qualifying player-credited kill can still award **1–3 base XP** with `doMobLoot` enabled. [WaterAnimal XP][water-xp] · [Experience gate][death-call]

## Notes

The entity is registered in `MobCategory.WATER_AMBIENT` and uses the integrated `net.minecraft.world.entity.animal.EntityLobster` class. The class's package does not make its behavior interchangeable with an upstream Lobster guide. Air-supply and bucket-color limitations above are source-only findings, not runtime test results.

Related: [Lobster Spawn Egg](../items/LobsterSpawnEgg.md) · [Bucket of Lobster](../items/BucketOfLobster.md) · [Lobster Tail](../items/LobsterTail.md) · [Cooked Lobster Tail](../items/CookedLobsterTail.md) · [Mobs](Mobs.md)

Source-reviewed at `a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1` on 2026-10-02. Registration, spawning inventory, AI/air dispatch, variant renderer, bucket components, persistence and missing loot fallback were traced. No in-game spawn, color, air, combat or bucket test was run.

[l-id]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/EntityType.java#L855-L861
[l-stats]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/animal/EntityLobster.java#L51-L116
[l-attr]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L193
[l-egg]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/Items.java#L1904
[l-category]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2043
[l-bucket]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/Items.java#L1905-L1909
[l-bucket-list]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1451
[egg-placement]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L76-L103
[standard-release]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/MobBucketItem.java#L28-L50
[bundled-data]: https://github.com/HungLo2020/MattMC/tree/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/resources/data
[spawn-rules]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L87-L178
[l-water]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/animal/EntityLobster.java#L284-L316
[l-travel]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/animal/EntityLobster.java#L118-L135
[l-attack]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/animal/EntityLobster.java#L191-L223
[mob-attack]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/Mob.java#L1292-L1320
[l-air]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/animal/EntityLobster.java#L225-L227
[water-base]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/animal/WaterAnimal.java#L39-L59
[air-max]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/Entity.java#L2660-L2662
[air-damage]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/LivingEntity.java#L491-L493
[water-lead]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/animal/WaterAnimal.java#L67-L70
[l-bucket-data]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/animal/EntityLobster.java#L145-L184
[egg-baby]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L181
[baby-setter]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/Mob.java#L1264-L1265
[baby-state]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/LivingEntity.java#L532-L534
[l-save]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/animal/EntityLobster.java#L237-L257
[l-variants]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/animal/EntityLobster.java#L265-L281
[l-render]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/client/renderer/entity/RenderLobster.java#L23-L38
[l-render-register]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/client/renderer/entity/EntityRenderers.java#L206
[bucket-capture]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L76-L94
[bucket-component]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L29-L32
[default-loot]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/EntityType.java#L2063-L2066
[loot-data]: https://github.com/HungLo2020/MattMC/tree/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/resources/data/minecraft/loot_table/entities
[loot-fallback]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[l-food-items]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/Items.java#L1680-L1681
[l-food-list]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1732-L1733
[water-xp]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/animal/WaterAnimal.java#L34-L37
[death-call]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1486
