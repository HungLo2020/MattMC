# Anaconda Spawn Egg

The **Anaconda Spawn Egg** creates an Anaconda directly, or a baby beside a living Anaconda. Prepare a contained area before placing this aggressive, semi-aquatic animal.

## Obtaining

Available from the **Spawn Eggs Creative category**, including the category-derived [inventory item browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item) in Creative. Its registered item is `minecraft:anaconda_spawn_egg`, bound to `minecraft:anaconda`. [Item registration][registration] · [Category entry][creative]

Seeing this entry in Survival does not supply an egg: ordinary Survival browser insertion is blocked by the server's [mode and permission limits](../mechanics/InventoryBrowser.md#mode-and-permission-limits). Creative catalog availability is not a Survival recipe or drop. An egg already supplied to a Survival player can still be used, with the consumption rules below.

## Usage

- **On an ordinary solid block:** use the egg on the face beside which you want the Anaconda. A block with an empty collision shape instead uses its own position. Interactive blocks may handle the click first; use the secondary-use control, normally Sneak, to reach egg use. [Position selection][egg-use] · [Block interaction order][block-dispatch]
- **At a source liquid:** the separate fluid-targeting action places at a source Water or Lava block when interaction/build permission allows it. Flowing liquid alone is not the target for that route. This does not grant environmental protection. [Source-liquid use][egg-use]

A fresh, unmodified egg used on a block or source liquid initializes an **adult**. Its yellow/non-yellow appearance is chosen randomly; this placement action does not select a particular color. [Initialization][species-spawn] · [Creation order][creation] · [Initial age][age]

A successful ordinary placement consumes **one egg in Survival**; Creative preserves the stack. **Anaconda is allowed in Peaceful** by its current entity registration, so the hand-use Peaceful rejection does not block this species. Custom eggs can hold a different entity type. [Use and difficulty gate][egg-use] · [Consumption][consume] · [Creative count restoration][block-dispatch] · [Entity registration][entity] · [Peaceful default][peaceful]

## Behavior

### Creating a baby beside an existing Anaconda

Use this matching egg on a **living Anaconda** to create a baby at that animal's position. This active interaction does not require an adult target, a second animal, food, love mode or an expired breeding cooldown. The common mob dispatcher reaches the species offspring factory before its normal feeding interaction. The result must exist and accept baby state; a mismatched egg or failed child creation does not produce a baby. [Live interaction][mob-dispatch] · [Matching and baby checks][egg-baby]

The baby copies the clicked Anaconda's **yellow/non-yellow flag**. This comes from the live species offspring factory, rather than the random color selection used for ordinary egg placement. It does not copy the whole parent or tame either animal. [Species offspring][species-baby] · [Egg helper][egg-baby]

Successful baby creation consumes **one egg in Survival**, while Creative keeps the egg. The new baby starts at **−24,000 age ticks**; this interaction does not run ordinary mating rewards or set the clicked parent's breeding cooldown. [Egg helper][egg-baby] · [Baby age][baby-age] · [Consumption][consume]

### Spawners and use restrictions

On a **[Monster Spawner](../blocks/MonsterSpawner.md#spawn-eggs-and-custom-setups)**, this egg changes the configured mob instead of placing a loose Anaconda. MattMC's `spawnerBlocksEnabled` gate must allow it. The egg branch adds no separate operator-permission requirement, but normal interaction restrictions still apply. Use the [shared spawner instructions](SpawnEggs.md#changing-a-spawner) for permissions, consumption, Trial Spawners and configuration limits. [Spawner branch][egg-use] · [Enabled setting][spawner-setting]

Spectators cannot perform the ordinary item/mob use, and server reach, world-border, enabled-item and applicable block/build restrictions still matter. See [when an egg does not work](SpawnEggs.md#when-an-egg-does-not-work) for the shared checks and [Dispensers](SpawnEggs.md#dispensing-eggs) for that separate creation route. [Server item checks][server-use] · [Block dispatch][block-dispatch] · [Adventure gate][adventure] · [Server mob checks][server-entity] · [Player dispatch][player-dispatch]

## Notes

* This item is registered as `minecraft:anaconda_spawn_egg`. [Registration][registration]
* This mob entry comes from the bundled Alex's Mobs content in MattMC.

For food breeding, temporary pacification, and the disconnected swallowing/Shed Snake Skin route, use the [Anaconda guide](../mobs/Anaconda.md). Its conditional rendering limitation also still applies; this egg review does not establish a working render or fix that limitation.

Related: [Anaconda](../mobs/Anaconda.md) · [Spawn eggs](SpawnEggs.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Traced the active client block/fluid/entity actions, registered packets, server restrictions, common mob dispatcher and this species' offspring implementation. This is source review, not an in-game spawning, baby-growth, spawner or rendering test. The workflows describe ordinary unmodified eggs; commands, custom components and changed data can alter results. [Client action order][client-click] · [Item sender][client-send] · [Entity sender][client-entity] · [Item packet registration][packet-use] · [Entity packet registration][packet-entity]

[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1798
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1972
[entity]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/EntityType.java#L235-L241
[egg-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L49-L127
[egg-baby]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L184
[mob-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Mob.java#L1053-L1107
[creation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/EntityType.java#L1736-L1774
[age]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/AgeableMob.java#L18-L48
[baby-age]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/AgeableMob.java#L156-L164
[peaceful]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/EntityType.java#L2050-L2074
[consume]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1080
[block-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L340-L397
[client-click]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/Minecraft.java#L1770-L1835
[client-send]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L317-L407
[client-entity]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L428-L438
[packet-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L114-L125
[packet-entity]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L80-L86
[server-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1254-L1323
[server-entity]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1703-L1741
[player-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L854-L890
[adventure]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L370
[spawner-setting]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/MinecraftServer.java#L1525-L1527
[species-spawn]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityAnaconda.java#L575-L579
[species-baby]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityAnaconda.java#L527-L535
