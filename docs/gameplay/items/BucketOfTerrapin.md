# Bucket of Terrapin

A **Bucket of Terrapin** carries one [Terrapin](../mobs/Terrapin.md) in water. It preserves several individual traits, but **does not preserve age**: a captured baby is normally released as an adult. Prepare a safe destination before emptying it. [Capture data][terrapin-data] · [Release][release] · [Age initialization][age]

## Obtaining

Use a **Water Bucket on a living Terrapin**. The animal is removed from the world and its data is written into the resulting Bucket of Terrapin. In ordinary Survival, the filled creature bucket replaces the Water Bucket. An empty Bucket is not accepted for this capture. [Active mob interaction][mob-dispatch] · [Terrapin capture caller][terrapin-data] · [Capture][bucketable] · [Container replacement][containers]

The item is also listed in the **Tools & Utilities Creative category** and available through the [inventory item browser in Creative](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item). A fresh catalog bucket creates a new Terrapin; it is not a recovered individual. Catalog visibility in Survival does not bypass the browser's [mode gate](../mechanics/InventoryBrowser.md#mode-and-permission-limits), and the [Terrapin guide](../mobs/Terrapin.md#obtaining) retains the unestablished natural-spawn route. [Registration][registration] · [Category][creative] · [Bucket listing][creative-bucket] · [Creation and initialization][creation]

## Usage

1. Prepare an enclosure with reachable water, air and dry ground; see [Terrapin care](../mobs/Terrapin.md#habitat-food-and-keeping-one-nearby).
2. Use the filled bucket against a block face where Water can be placed, or on a suitable water-holding block. Existing water is not required: the bucket normally supplies it. The normal world-interaction and item-placement checks must allow the action. [Bucket target and permission checks][bucket-use]
3. On successful emptying, the server creates a Terrapin, loads the stored bucket fields and marks it as **bucket-origin**. Ordinary Survival use returns an empty [Bucket](Bucket.md); Creative retains the filled bucket. Bucket-origin protects the Terrapin from its ordinary distance-despawn rule. [Release][release] · [Container result][bucket-use] · [Persistence][persistence]

**Do not empty it in an ultra-warm dimension such as the Nether expecting water protection.** The Water-evaporation branch reports success, so the release step can still create the Terrapin and ordinary Survival still returns an empty Bucket, even though no Water remains. Choose a suitable destination before using it. [Nether dimension][nether] · [Evaporation and success][water] · [Release caller][bucket-use] · [Animal creation][release]

## Behavior

### What capture preserves

The capture/release pair preserves:

- **Health and custom name**
- **Terrapin type**, shell type and skin type
- **Body, shell and skin color fields**
- The **egg-carrying flag**, `HasEgg`
- Existing enabled special flags for disabled AI, silence, no gravity, glowing and invulnerability

The name is carried as an item component; the remaining fields are read from the bucket-entity-data component. The released animal is initialized first, then the saved bucket fields are restored. Saved appearance values are not proof that every overlay renders: the [Terrapin appearance section](../mobs/Terrapin.md#appearance-and-bucket-limits) owns that limitation. [Species save/load][terrapin-data] · [Shared bucket fields][bucketable] · [Name application][name] · [Creation order][creation] · [Final bucket load][release]

### What capture does not preserve

**Age, forced-growth progress, and the age-based breeding cooldown are not saved.** Ordinary release creates a new animal with adult age and no retained cooldown: taking a baby into a bucket is not a way to pause and resume its growth. The species also does not store its partner-appearance data in the bucket. This is a selected trait transfer, not a complete copy of the original entity. [Saved fields][terrapin-data] · [Ordinary age fields][age-save] · [Initial age and group check][age] · [Release order][creation] · [Partner data][mating]

Keeping `HasEgg` does **not** complete breeding or preserve a working route to inherited offspring. The registered goals omit the Terrapin laying goal, and the existing egg-item/hatching/parent-data limitations remain tracked in [issue #798](https://github.com/HungLo2020/MattMC/issues/798). Moving an egg-carrying Terrapin in a bucket does not repair them. Use the canonical [Terrapin breeding limitation](../mobs/Terrapin.md#breeding-limitation) and [placed Terrapin Eggs](../blocks/AnimalEggs.md#terrapin-eggs) for that lifecycle. [Registered goals][goals] · [Saved flag][terrapin-data]

## Notes

* This item is registered as `minecraft:terrapin_bucket` and stacks to **one**. [Registration][registration]
* Capture requires a living target and normal nonspectator entity interaction; being close enough to click the animal does not imply permission to place the bucket's water at a protected destination. [Server entity checks][server-entity] · [Player dispatch][player-dispatch] · [Bucket placement checks][bucket-use]

Related: [Terrapin](../mobs/Terrapin.md) · [Water Bucket](WaterBucket.md) · [Bucket](Bucket.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Followed the client entity/item actions, registered packets, server dispatch, Terrapin capture callback, item components, release initialization, age and ultra-warm branch. No in-game capture, release, growth, breeding, rendering or data-loss test was run. [Client actions][client-click] · [Entity sender][client-entity] · [Item sender][client-use] · [Entity packet registration][packet-entity] · [Item packet registration][packet-use] · [Server item dispatch][server-use]

[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1992-L1993
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1396-L1403
[creative-bucket]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1438-L1449
[terrapin-data]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityTerrapin.java#L519-L565
[bucketable]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L29-L93
[release]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/MobBucketItem.java#L28-L50
[bucket-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BucketItem.java#L40-L93
[water]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BucketItem.java#L99-L141
[age]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/AgeableMob.java#L18-L48
[age-save]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/AgeableMob.java#L106-L117
[creation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/EntityType.java#L1750-L1774
[name]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Entity.java#L3926-L3958
[persistence]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityTerrapin.java#L290-L315
[mating]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityTerrapin.java#L596-L619
[goals]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityTerrapin.java#L109-L120
[mob-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Mob.java#L1053-L1107
[client-click]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/Minecraft.java#L1770-L1835
[client-entity]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L428-L438
[client-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L378-L407
[packet-entity]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L80-L86
[packet-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L114-L125
[server-entity]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1703-L1741
[player-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L854-L890
[server-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L300-L336

[nether]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/dimension_type/the_nether.json#L19
[containers]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemUtils.java#L15-L38
