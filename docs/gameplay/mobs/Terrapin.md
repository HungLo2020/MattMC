# Terrapin

A **Terrapin** is a small animal that alternates between land and water and can be carried in a Water Bucket. Give it access to air and avoid jumping on it near other creatures: a spinning adult can hurt nearby living entities. Its breeding-to-egg lifecycle is incomplete in this snapshot. Its ID is `minecraft:terrapin`. [Entity][entities] · [Water and goals][terra-goals] · [Spin contact][terra-spin] · [Capture][terra-data]

## Obtaining

**No Terrapin entry was found in the bundled biome spawn tables**, so an ordinary natural-spawn route is not established. Entity registration alone does not add a wild population; the active natural chooser reads the loaded biome list or a structure override. [Registration][entities] · [World loading][world-load] · [JSON loading][registry-load] · [Active chooser][spawn-choice]

The [Terrapin Spawn Egg](../items/TerrapinSpawnEgg.md) and [Bucket of Terrapin](../items/BucketOfTerrapin.md) are ordinary category-listed items. The [inventory item browser](../mechanics/InventoryBrowser.md) can supply them in Survival as well as Creative. Egg placement or bucket release is separate from natural spawning. [Egg listing][egg-list] · [Bucket listing][terra-bucket-list] · [Items][terra-items] · [Egg use][egg-use] · [Release][bucket-release] · [Client request][browser-client] · [Server handling][browser-server]

Using a matching Terrapin Spawn Egg on an existing Terrapin can create a baby through the egg-specific offspring path. That is separate from the incomplete mating-and-laying lifecycle. [Interaction caller][egg-offspring-call] · [Offspring creation][egg-offspring] · [Terrapin child type][terrapin]

## Behavior

### Habitat, food and keeping one nearby

Provide both reachable dry ground and water with open air above it. Unlike Turtle, Terrapin is absent from the underwater-breathing tag. It has a **4,800-tick air reserve**, about four minutes, and a registered air-seeking goal; trapping it underwater can still cause drowning. Reaching air refills its reserve. [Air capacity][terra-air] · [Breathing membership][breathing-tag] · [Breathing check][breathing-check] · [Active drowning path][air-tick] · [Air goal registration][terra-goals] · [Air search][air-goal]

Its water preference changes with time spent swimming or ashore. The registered enter/leave-water goals act on those preferences only when they can find a route, so a fixed schedule of crossings is not guaranteed. [Preferences][terra-water] · [Enter caller][find-water] · [Leave caller][leave-water]

Accepted fish are **Raw Cod, Cooked Cod, Raw Salmon, Cooked Salmon, Tropical Fish and Pufferfish**. Holding one can attract a Terrapin; using one can feed a baby or start the adult mating interaction. Fish buckets are not in this food tag. Interacting with a tagged fish also sets persistence, helping prevent ordinary distance despawning; it does not tame the Terrapin or assign an owner. Bucket-origin Terrapins and named Terrapins also have persistence protection. [Food tag][fishes] · [Tempt goal][terra-goals] · [Food interaction][animal] · [Persistence interaction][terra-data] · [Distance rules][terra-persistence]

### Shell retreat and spinning

Airborne contact from a player above a Terrapin on land can make it retreat into its shell. Further qualifying contact while it is hidden can launch a spin in that player's facing direction. A spinning **adult**, after its first five spin ticks, attempts damage against nearby living entities within its bounding box expanded by **0.3 blocks**, excluding other Terrapins and allies. Each attempt has base damage from **4 up to, but not including, 8 points**, before target damage handling. Do not infer a per-second damage rate from these repeated attempts. [Contact and damage checks][terra-spin] · [Spin movement][terra-motion] · [Server damage dispatch][hurt-wrapper]

### Appearance and bucket limits

Spawn initialization selects among **Green, Black, Brown, Koopa, Painted, Red-Eared and Overlay** base types. A name containing “koopa”, ignoring case, selects the Koopa texture. Extra shell, skin and color fields are stored, but the checked renderer does not apply those stored overlays; do not assume that every saved color combination appears on screen. [Initialization][terra-init] · [Types][terra-types] · [Name check][terra-name] · [Renderer registration][terra-render-wire] · [Active renderer][terra-render]

Use a **Water Bucket** on a living Terrapin to capture it. Capture preserves health, custom name, type, shell/skin/color fields and the egg-carrying flag. Ordinary successful release returns an empty Bucket and marks the released animal as bucket-origin. **Age is not saved by this capture path:** ordinary release initializes a captured baby as an adult. The bucket also does not restore the missing laying goal or establish parent-trait inheritance. Avoid ultra-warm release, where water evaporates even though the animal can be released. [Saved data][terra-data] · [Shared capture fields][bucket-save] · [Creation order][spawn-order] · [Age initialization][baby-spawn] · [Release][bucket-release] · [Water and container use][bucket-water]

## Breeding limitation

Feeding ready adults can run the registered mating goal, set one parent's egg-carrying flag and apply a **6,000-tick cooldown** to both. However, the separate egg-laying goal is **not installed**, so this is not a completed route to eggs or offspring. No registered Terrapin Egg inventory item exists, and the placed egg's parent-data lookup happens after block removal. These source-reviewed lifecycle limitations remain tracked in [#798](https://github.com/HungLo2020/MattMC/issues/798); no fix is claimed here. [Registered goals][terra-goals] · [Mating result][terra-mate] · [Unused laying goal][terra-lay] · [Hatch order][terra-egg] · [Unmapped egg drop][terra-drop]

Use the existing [Terrapin Egg section](../blocks/AnimalEggs.md#terrapin-eggs) for command-placed egg behavior and its restrictions. An already existing baby can mature through the normal **24,000-tick** age process, and accepted fish shorten its remaining growth time; that does not repair the breeding lifecycle. [Age tick][growth] · [Feeding][animal]

## Drops

No bundled Terrapin death-item table or Turtle Scute growth reward was found. The missing default death table resolves to empty loot. Eligible adult player-credit kills still have the inherited **1–3 experience** reward with mob loot enabled; babies do not give death experience. Raise [Turtles](Turtle.md#raising-babies-for-scutes) for Scutes instead. [Active implementation][terrapin] · [Missing-table fallback][missing-loot] · [Experience amount][animal] · [Death gates][death] · [Baby gate][loot-gates]

## Notes

- **Health:** 10 points, or 5 hearts; **base armor:** 10 points. Armor is not a fixed damage-reduction percentage. [Attribute registration][attributes] · [Values][terra-goals] · [Armor mechanics](../mechanics/Armor.md)
- Source review did not establish working overlay rendering or full bucket age preservation; neither is presented as a runtime-tested result

## Related pages

- [Placed Terrapin Eggs: acquisition, hatching and protection](../blocks/AnimalEggs.md#terrapin-eggs)
- [Bucket of Terrapin](../items/BucketOfTerrapin.md)
- [Turtle](Turtle.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `384aa3dfa1473af7753759569de95012d5bdc46f`. Checked the bundled spawn-data inventory, active attributes/goals, breathing, food, spin damage dispatch, renderer, bucket save/load and age initialization, missing loot and the existing #798 lifecycle limitations. Egg-block details remain with Animal Eggs. No in-game test was run. Timing assumes 20 ticks per second; data packs can change the listed tags, recipes, biome entries and loot.

[entities]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/EntityType.java#L1449-L1456
[terra-goals]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/alexsmobs/entity/EntityTerrapin.java#L97-L120
[terra-spin]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/alexsmobs/entity/EntityTerrapin.java#L156-L215
[terra-data]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/alexsmobs/entity/EntityTerrapin.java#L519-L565
[world-load]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/server/WorldLoader.java#L38-L51
[registry-load]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L277-L334
[spawn-choice]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450
[egg-list]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2108-L2110
[terra-bucket-list]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1446-L1446
[terra-items]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/item/Items.java#L1991-L1993
[egg-use]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L49-L127
[bucket-release]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/item/MobBucketItem.java#L28-L50
[browser-client]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-server]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1928
[terra-air]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/alexsmobs/entity/EntityTerrapin.java#L398-L404
[breathing-tag]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/resources/data/minecraft/tags/entity_type/can_breathe_under_water.json
[breathing-check]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L384-L385
[air-tick]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L424-L442
[air-goal]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/ai/goal/BreathAirGoal.java#L22-L76
[terra-water]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/alexsmobs/entity/EntityTerrapin.java#L472-L515
[find-water]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/alexsmobs/entity/ai/AnimalAIFindWater.java#L22-L49
[leave-water]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/alexsmobs/entity/ai/AnimalAILeaveWater.java#L25-L57
[fishes]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/resources/data/minecraft/tags/item/fishes.json
[animal]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L158
[terra-persistence]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/alexsmobs/entity/EntityTerrapin.java#L290-L315
[terra-motion]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/alexsmobs/entity/EntityTerrapin.java#L418-L452
[hurt-wrapper]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/Entity.java#L1772-L1776
[terra-init]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/alexsmobs/entity/EntityTerrapin.java#L455-L464
[terra-types]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/alexsmobs/entity/util/TerrapinTypes.java
[terra-name]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/alexsmobs/entity/EntityTerrapin.java#L575-L578
[terra-render-wire]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/client/renderer/entity/EntityRenderers.java#L305-L305
[terra-render]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/alexsmobs/client/render/RenderTerrapin.java
[bucket-save]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L29-L93
[spawn-order]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/EntityType.java#L1750-L1774
[baby-spawn]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/AgeableMob.java#L31-L48
[bucket-water]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/item/BucketItem.java#L72-L140
[terra-mate]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/alexsmobs/entity/EntityTerrapin.java#L584-L620
[terra-lay]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/alexsmobs/entity/EntityTerrapin.java#L622-L669
[terra-egg]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/alexsmobs/block/BlockTerrapinEgg.java#L111-L138
[terra-drop]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/alexsmobs/block/BlockTerrapinEgg.java#L191-L205
[growth]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/AgeableMob.java#L129-L167
[terrapin]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/alexsmobs/entity/EntityTerrapin.java
[missing-loot]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[death]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1527
[loot-gates]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L567
[attributes]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L261-L262

[egg-offspring-call]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/entity/Mob.java#L1088-L1098
[egg-offspring]: https://github.com/HungLo2020/MattMC/blob/384aa3dfa1473af7753759569de95012d5bdc46f/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L181
