# Mudskipper

The **Mudskipper** (`minecraft:mudskipper`) is a tameable animal with land/water movement and a ranged mud attack. It has **12 health points**. Give it access to land and the surface: the current source does not grant it underwater breathing. Also read the [bucket transfer limits](../items/BucketOfMudskipper.md#what-survives-release) before moving a pet. [Entity][mud-entity] · [Bound attributes][attributes] · [Health][mud-stats] · [Breathing check][breathe] · [Breathing tag][breath-tag]

## Obtaining

The [Mudskipper Spawn Egg](../items/MudskipperSpawnEgg.md) is registered and listed in the ordinary [Inventory Browser](../mechanics/InventoryBrowser.md), available in **Creative**. Its factory creates the actual Mudskipper entity. [Egg][mud-egg] · [Category entry][egg-list] · [Browser list][browser] · [Server access][browser-server] · [Egg use][egg-use]

**Natural spawning is not established by the reviewed active source.** A static Mud/Muddy Mangrove Roots predicate exists, but has no active caller or placement registration, and no bundled biome spawn list names Mudskipper. Do not infer a mangrove-swamp finding route from that unused predicate or the `CREATURE` category. [Species predicates][mud-spawn] · [Placement registrations][placements] · [Spawn-list selection][natural-list] · [Bundled biomes][biomes]

Once you have a living Mudskipper, it can breed or be collected with a [Water Bucket](../items/WaterBucket.md). The resulting [Bucket of Mudskipper](../items/BucketOfMudskipper.md) is registered, but it is **not listed in the current ordinary category/browser inventory**. Its verified supply is live capture, not the generic Creative-menu route previously described here. [Capture call][mud-commands] · [Shared capture][capture] · [Filled item][bucket-item] · [Category list][all-categories]

## Behavior

### Diet, taming and breeding

| Purpose | Accepted item in the bundled tag |
| --- | --- |
| Tempting and the custom taming attempt | [Tropical Fish](../items/TropicalFish.md) |
| Breeding and baby growth | Tropical Fish |
| Healing an injured tamed Mudskipper | [Raw Cod](../items/RawCod.md), [Raw Salmon](../items/RawSalmon.md), or Tropical Fish |

Cooked fish are not in these Mudskipper tags. Each reached custom taming attempt has a **one-in-two** success check. Each reached custom healing branch consumes food and restores up to **5 health points**. [Taming tag][mud-tame-tag] · [Breeding tag][mud-breed-tag] · [Healing tag][mud-heal-tag] · [Temptation goal][mud-goals] · [Custom feeding][mud-food]

**Tropical Fish can be used for breeding or growth before taming/healing is checked.** This applies to wild Mudskippers too: the shared breeding-food predicate does not require taming. An eligible adult can spend the first fish to enter love mode; a baby can spend it on growth. If more Tropical Fish remain in that held stack, the later branch may spend a second fish on taming or healing. If the first branch used the final fish, that later action does not run. Heart particles alone therefore do not distinguish breeding from successful taming. [Shared-first call and food predicate][mud-food] · [Shared feeding][shared-feed] · [Item consumption][consume-feed] · [Empty-stack identity][empty-stack]

If you own the Mudskipper, spending the **last Tropical Fish on eligible adult breeding can also advance its command**: the later control gate does not exclude that adult success result. Recheck its Wander/Follow/Sit state afterward. Baby feeding uses a different result and does not have this same fallthrough. [Shared results][shared-feed] · [Distinct success values][interaction-results] · [Owner gate][mud-commands]

Compatible adults in love use the shared breeding goal. Their offspring factory creates a Mudskipper baby and does not copy ownership; offspring need separate taming. [Goal registration][mud-goals] · [Breeding flow][shared-breed] · [Offspring factory][mud-child] · [Registry alias][mud-alias]

### Owner commands and movement

An owner using an empty hand cycles **Wander → Follow → Sit → Wander**. Food and Water Bucket collection have their own interaction paths. Follow uses the owner-following goal and can be interrupted by combat. Sit stops the species' travel input and navigation; do not use it to keep a Mudskipper fully submerged. [Commands][mud-commands] · [Command text][commands-text] · [Following][mud-follow] · [Sitting movement][mud-travel]

The water AI tracks time in and out of water: being hurt or spending long enough out of water makes entry eligible, while sufficient swimming or displaying makes leaving eligible. These goals still need a reachable target and their own checks, so provide an accessible shore rather than depending on a guaranteed exit. [Timer updates][mud-swim-timer] · [Entry/exit rules][mud-water] · [Find-water caller][find-water] · [Leave-water caller][leave-water]

**Mudskipper can drown in this version.** It has no species breathing override and is absent from the underwater-breathing tag, including that tag's nested groups. The inherited living-entity path consumes air while its eyes are underwater and can apply drowning damage. Water-seeking AI and successful bucket release do not provide immunity. [Breathing method][breathe] · [Bundled breathing tag][breath-tag] · [Air and drowning][drown]

### Defense and social display

Its registered target goals defend the owner, respond to the owner's attacks, and enable retaliation after taming. The attack goal follows a live target and, with line of sight inside its shooting range, launches mud balls. A mud ball requests **1–3 damage** and applies **Slowness for 60 ticks** to a living hit target; actual damage and effect acceptance depend on that target. This projectile is not a collected food or new inventory item. [Target goals][mud-goals] · [Attack goal][mud-attack] · [Projectile values][mud-ball] · [Active collision/damage path][projectile-tick] · [Impact dispatch][projectile-hit]

The circling/display goal is separate from breeding and does not create offspring. Its current start check rejects an on-ground initiator, while its partner predicate requires an on-ground partner. Do not treat a display as a dependable breeding signal or promise a routine display between two grounded animals. [Start checks][mud-display-start] · [Partner checks][mud-display-partner] · [Display behavior][mud-display]

### Buckets, saving and loot

Using a Water Bucket on a living Mudskipper reaches the shared capture helper without a taming or ownership requirement. It removes that entity and makes the filled item. **Ordinary release does not preserve its owner, age, command or sitting state**; use the [bucket guide's field-by-field limits](../items/BucketOfMudskipper.md#what-survives-release) before transporting a pet. [Capture interaction][mud-commands] · [Helper][capture] · [Captured fields][mud-bucket-save] · [Release loader][bucket-load-component]

Ordinary world saves are different from bucket transfer: Mudskipper's world save/load overrides preserve its command, sitting flag, display cooldown and bucket flag, while the inherited tame-animal path handles ownership. [World persistence][mud-save] · [Owner persistence][tame-save]

No default Mudskipper death-loot table or species food recipe was found in the reviewed active data. Missing default tables resolve to the empty table; this does not rule out generic equipment, experience or custom loot overrides. [Default loot key][loot-key] · [Per-mob override][mob-loot] · [Loot lookup][death-loot] · [Missing fallback][loot-fallback] · [Bundled loot][loot] · [Bundled recipes][recipes]

## Notes

- Entity ID: `minecraft:mudskipper`; egg ID: `minecraft:mudskipper_spawn_egg`.
- Registered category: `CREATURE`; size: 0.6 × 0.5 blocks. [Registration][mud-entity]
- Related: [Mantis Shrimp](MantisShrimp.md), [Bucket of Mudskipper](../items/BucketOfMudskipper.md), and [Water Bucket](../items/WaterBucket.md).

Source-reviewed on **2026-10-02** at `eaeeffdeb9220de7d839af7c84a047693249c2f7`. Checked active registration, diet tags, interaction ordering, breeding, commands, water/drowning paths, projectile and display callers, world saves, bucket data and exact spawn/loot/recipe scopes. No game, browser, feeding, breeding, water-care, bucket, combat or display test was run.

[mud-entity]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/EntityType.java#L939-L946
[attributes]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L196-L200
[mud-stats]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/EntityMudskipper.java#L170-L176
[breathe]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/LivingEntity.java#L384-L386
[breath-tag]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/resources/data/minecraft/tags/entity_type/can_breathe_under_water.json
[mud-egg]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/item/Items.java#L1918-L1918
[egg-list]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2045-L2049
[browser]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[browser-server]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1919
[egg-use]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L50-L101
[mud-spawn]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/EntityMudskipper.java#L108-L120
[placements]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[natural-list]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450
[biomes]: https://github.com/HungLo2020/MattMC/tree/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/resources/data/minecraft/worldgen/biome
[mud-commands]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/EntityMudskipper.java#L482-L499
[capture]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L76-L94
[bucket-item]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/item/Items.java#L1584-L1588
[all-categories]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[mud-tame-tag]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/resources/data/minecraft/tags/item/mudskipper_tameables.json
[mud-breed-tag]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/resources/data/minecraft/tags/item/mudskipper_breedables.json
[mud-heal-tag]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/resources/data/minecraft/tags/item/mudskipper_foodstuffs.json
[mud-goals]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/EntityMudskipper.java#L122-L145
[mud-food]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/EntityMudskipper.java#L450-L481
[shared-feed]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/animal/Animal.java#L134-L158
[consume-feed]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/Mob.java#L1113-L1120
[empty-stack]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/item/ItemStack.java#L322-L332
[interaction-results]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/InteractionResult.java#L11-L15
[shared-breed]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/animal/Animal.java#L197-L227
[mud-child]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/EntityMudskipper.java#L324-L329
[mud-alias]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/AMEntityRegistry.java#L29-L30
[commands-text]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/resources/assets/minecraft/lang/en_us.json#L3985-L3988
[mud-follow]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/ai/TameableAIFollowOwnerWater.java#L40-L75
[mud-travel]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/EntityMudskipper.java#L90-L106
[mud-swim-timer]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/EntityMudskipper.java#L233-L260
[mud-water]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/EntityMudskipper.java#L374-L392
[find-water]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/ai/AnimalAIFindWater.java#L22-L49
[leave-water]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/ai/AnimalAILeaveWater.java#L25-L57
[drown]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/LivingEntity.java#L424-L443
[mud-attack]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/ai/MudskipperAIAttack.java#L23-L70
[mud-ball]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/EntityMudBall.java#L50-L66
[projectile-tick]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/EntityMobProjectile.java#L55-L92
[projectile-hit]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/EntityMobProjectile.java#L172-L178
[mud-display-start]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/ai/MudskipperAIDisplay.java#L30-L51
[mud-display-partner]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/EntityMudskipper.java#L320-L322
[mud-display]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/ai/MudskipperAIDisplay.java#L81-L152
[mud-bucket-save]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/EntityMudskipper.java#L405-L437
[bucket-load-component]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L29-L32
[mud-save]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/EntityMudskipper.java#L178-L196
[tame-save]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/TamableAnimal.java#L54-L79
[loot-key]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[mob-loot]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/Mob.java#L403-L404
[death-loot]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1527
[loot-fallback]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[loot]: https://github.com/HungLo2020/MattMC/tree/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/resources/data/minecraft/loot_table
[recipes]: https://github.com/HungLo2020/MattMC/tree/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/resources/data/minecraft/recipe
