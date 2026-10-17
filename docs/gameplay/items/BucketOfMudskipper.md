# Bucket of Mudskipper

A **Bucket of Mudskipper** (`minecraft:mudskipper_bucket`) carries water and creates a Mudskipper when successfully emptied. It stacks to one. **The current capture/release path does not retain pet ownership or age**, so read the transfer limits before using it to move a tamed animal. [Registration][bucket-item] · [Captured data][mud-bucket-save] · [Active release][bucket-spawn]

## Obtaining

Hold a **[Water Bucket](WaterBucket.md)** and use it on a living [Mudskipper](../mobs/Mudskipper.md). An empty Bucket is not the required item. Capture does not require that you own or have tamed the animal. The shared helper saves supported data, gives the filled bucket and discards the original entity. [Mudskipper interaction][mud-commands] · [Capture helper][capture] · [Registered bucket alias][bucket-alias]

In Survival, the one Water Bucket is replaced by the filled bucket. Creative capture keeps the Water Bucket and puts the filled result into inventory, or drops that result if inventory cannot accept it. These exchanges come from the shared filled-result helper. [Capture's helper call][capture] · [Result handling][filled-result]

The filled item is registered but **absent from the current category list used by the ordinary [Inventory Browser](../mechanics/InventoryBrowser.md)**. No bundled crafting or loot route for it was found in the reviewed exact resource scopes. Obtain a starting animal with its listed [Spawn Egg](MudskipperSpawnEgg.md), then capture it. [Registration][bucket-item] · [Category list][all-categories] · [Browser construction][browser] · [Egg entry][egg-list] · [Recipe scope][recipes] · [Loot scope][loot]

## Usage

Use the held filled bucket against a suitable block face. The bucket checks permissions and attempts to place water in the selected or adjacent available position. On success, the server creates a new Mudskipper, loads the supported bucket data and marks it as released from a bucket. Survival receives an empty [Bucket](Bucket.md); Creative retains the filled bucket. [Placement and result][bucket-use] · [Server creation][bucket-spawn]

Successful emptying does not always mean there is a pool. In an **ultrawarm dimension**, the water branch returns success with evaporation effects and no water placement; the creature-release callback still follows. Choose a safe release spot with access to land and air. Mudskipper's current [breathing limit](../mobs/Mudskipper.md#owner-commands-and-movement) also makes a fully enclosed underwater tank unsafe. [Water/evaporation branches][bucket-water] · [Release callback][bucket-spawn]

A **Dispenser ejects this filled bucket as an item** under the current registration. Mudskipper Bucket is not among the explicitly registered fluid/mob-bucket dispenser behaviors, so the normal item fallback applies. It does not provide automated creature release. [Bucket behavior registrations][dispenser-buckets] · [Fallback selection][dispenser-fallback] · [Item dispensing][dispenser-item]

## Behavior

### What survives release

| State in an ordinarily captured animal | Result on ordinary release |
| --- | --- |
| Custom name | Restored from the item name component |
| Health; supported NoAI, Silent, NoGravity, Glowing and Invulnerable flags | Restored through the shared bucket-data component |
| Tame owner | Not written by capture; the new animal is untamed |
| Baby age, growth progress or breeding cooldown | Not written by capture; the new animal starts at adult age 0 |
| Command and sitting state | Custom keys are written, but the active species loader does not read that component; command resets to Wander and sitting to false |
| Display cooldown | The same component mismatch leaves a fresh default cooldown |
| From-bucket flag | Set to true by the release code after loading |

Mudskipper stores its extra keys in `CUSTOM_DATA`, while the default active release hook reads `BUCKET_ENTITY_DATA`. The generic entity component application retains custom data as opaque data; it does not turn those keys into the Mudskipper's command or sitting fields. This is why their presence in a captured item is not proof that the animal state is restored. [Species save/load][mud-bucket-save] · [Shared supported fields][bucket-default-save] · [Release component choice][bucket-load-component] · [Item configuration][item-components] · [Name/custom-data application][entity-components] · [Opaque custom data][inert-custom-data]

The release path creates a fresh entity before loading those fields. Its ordinary age starts at zero, and this single-entity bucket creation does not use an existing spawning group to make a baby. [Creation sequence][entity-create] · [Default age and spawn group][age-default] · [Bucket loader and final flag][bucket-spawn]

After release, tame the animal again if you want owner commands. Its normal world saving is separate from this bucket conversion; see the [Mudskipper care guide](../mobs/Mudskipper.md#buckets-saving-and-loot).

## Notes

- Exact item ID: `minecraft:mudskipper_bucket`.
- This page covers ordinary captured and registered buckets. Commands or external tools can supply different components.
- Shared fluid behavior belongs to [Water Bucket](WaterBucket.md); empty-container handling belongs to [Bucket](Bucket.md).

Source-reviewed on **2026-10-02** at `eaeeffdeb9220de7d839af7c84a047693249c2f7`. Traced the actual registered item and alias, live capture call, component writer/reader, entity creation, hand-use results, ultrawarm branch and dispenser fallback. No game, browser, capture/release, Creative, water-placement or data-preservation test was run.

[bucket-item]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/item/Items.java#L1584-L1588
[mud-bucket-save]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/EntityMudskipper.java#L405-L437
[bucket-spawn]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/item/MobBucketItem.java#L28-L51
[mud-commands]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/entity/EntityMudskipper.java#L482-L499
[capture]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L76-L94
[bucket-alias]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/alexsmobs/item/AMItemRegistry.java#L39-L40
[filled-result]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/item/ItemUtils.java#L15-L38
[all-categories]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[browser]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[egg-list]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2045-L2049
[recipes]: https://github.com/HungLo2020/MattMC/tree/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/resources/data/minecraft/recipe
[loot]: https://github.com/HungLo2020/MattMC/tree/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/resources/data/minecraft/loot_table
[bucket-use]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/item/BucketItem.java#L41-L93
[bucket-water]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/item/BucketItem.java#L100-L143
[dispenser-buckets]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L166-L189
[dispenser-fallback]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/level/block/DispenserBlock.java#L102-L113
[dispenser-item]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/core/dispenser/DefaultDispenseItemBehavior.java#L21-L46
[bucket-default-save]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L38-L74
[bucket-load-component]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L29-L32
[item-components]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/EntityType.java#L1710-L1728
[entity-components]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/Entity.java#L3926-L3933
[inert-custom-data]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/Entity.java#L3955-L3964
[entity-create]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/EntityType.java#L1750-L1775
[age-default]: https://github.com/HungLo2020/MattMC/blob/eaeeffdeb9220de7d839af7c84a047693249c2f7/src/main/java/net/minecraft/world/entity/AgeableMob.java#L21-L48
