# Spawn eggs

Spawn eggs let you place a chosen creature, configure a spawner, or create a supported baby beside an existing mob. MattMC's ordinary listed eggs can be obtained through the **inventory item browser in Survival as well as Creative**. An egg's availability does not establish that its creature naturally spawns, breeds with food, or can be tamed. Use the [mob guides](../mobs/Mobs.md) for those species rules. [Registered eggs][registrations] · [Browser list][browser-list] · [Client request][client-add] · [Server checks][server-add] · [Slot insertion][server-insert]

## Obtaining

Open your inventory and find the egg by its displayed name in the item panel. Keep the mouse cursor empty and leave inventory space, then click to request one egg or Shift-click for a stack. The [inventory item browser guide](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item) owns the controls, capacity checks and permissions. This supplies the egg without a crafting ingredient or a game-mode change; using it still follows the rules below. [Ordinary Spawn Eggs category][category] · [Insertion request][client-add] · [Server check][server-add]

The checked source registers **158 spawn-egg items**. **156** appear in the ordinary Spawn Eggs category, with no operator-only condition around that category. The exceptions are **[Ender Dragon Spawn Egg](EnderDragonSpawnEgg.md)** and **[Wither Spawn Egg](WitherSpawnEgg.md)**: both are registered, but neither is included in the checked category list. Their absence also keeps them out of the browser's category-derived list. [All registrations][registrations] · [Complete category][category] · [List construction][browser-list]

For those two absent entries, a player with permission level **2** can use the registered `/give` command with `minecraft:ender_dragon_spawn_egg` or `minecraft:wither_spawn_egg`. See [Commands](../commands/Commands.md) for command access. This is a separate permission-controlled route, not an ordinary browser entry. [Dragon registration][boss-eggs] · [Wither registration][wither-egg] · [Give syntax and permission][give] · [Command registration][give-register]

## Choosing the creature

A normal egg carries its registered entity type. **Cave Centipede Spawn Egg** is bound to `minecraft:cave_centipede_head`, the centipede's main entity, rather than to an ID named `minecraft:cave_centipede`. The egg item remains `minecraft:cave_centipede_spawn_egg`. [Stored type][binding] · [Egg binding][centipede] · [Entity registration][centipede-entity]

The egg reads the entity type stored on its stack. An administrator-supplied egg with altered entity data can therefore differ from the fresh browser item. Ordinary block placement initializes the creature, then applies the egg's components and supported entity data. Species variant selection and behavior belong to the corresponding mob guide. [Type lookup][egg-type] · [Creation][creation] · [Stack configuration][stack-config]

## Placing a mob by hand

Use the egg on a block. If the clicked block has an empty collision shape, the egg uses that block's position; otherwise it uses the neighboring position in the clicked-face direction. The creation path adjusts vertical placement against collision shapes and initializes the mob. Prepare suitable space and habitat: egg placement does not certify that a land, water or flying creature can live safely where you put it. [Position choice][placement] · [Initialization][creation]

A successful ordinary placement **consumes one egg in Survival**. Creative's infinite-materials handling preserves the stack. The hand-placement path rejects an entity type marked unavailable in **Peaceful** before creating it. That flag is a property of the registered type; do not infer it solely from a mob's appearance or category. [Hand placement and Peaceful guard][placement] · [Consumption][consume] · [Creative block-use handling][block-dispatch]

You can also use an egg while aiming at a **source liquid block**, including Water or Lava. The fluid-targeting route places at that source block and checks interaction/build permission. A flowing liquid alone is not the source-fluid target used by this route. Choosing a liquid does not give the creature immunity to drowning, fire or other environmental damage. [Fluid targeting and use][fluid]

Interactive blocks may handle the click before the egg. Use the secondary-use control, normally Sneak, when you need to bypass a block's usual interaction and reach item use. A spawner is a special case described next. [Block interaction order][block-dispatch]

## Changing a spawner

Use an egg on a **[Monster Spawner](../blocks/MonsterSpawner.md)** or **[Trial Spawner](../blocks/TrialSpawner.md)** to set its entity type instead of placing a mob beside it. Both block entities implement the egg's spawner interface. The `spawnerBlocksEnabled` game rule must be true; its bundled default is true. If disabled, this interaction fails and sends the player the spawner-disabled message. No additional operator check appears in this egg branch, though the normal interaction restrictions still apply. [Egg's spawner branch][placement] · [Game rule][spawner-rule] · [Active setting accessor][spawner-enabled] · [Monster Spawner setter][spawner] · [Trial Spawner setter][trial-spawner]

The egg supplies the entity **type**, not the full contents of a custom mob stack. Trial Spawner reassignment also resets its tracked state and returns it to its inactive state. A successful change consumes one egg in ordinary Survival use; Creative block-use handling restores the count. Spawner activation, space checks and encounter rules remain separate, so selecting a creature does not guarantee immediate output. Read the relevant spawner guide before changing an existing setup. [Spawner branch][placement] · [Trial reset][trial-reset] · [Creative handling][block-dispatch]

## Using an egg on an existing mob

Using the matching egg on a living mob can create a baby at that mob's position **when the creature supports this path**. For an ageable mob, the helper asks the clicked creature's offspring factory for a child, passing that same creature as both parents. For other mobs it creates the matching entity directly. In both cases, the result must accept baby state before it is added to the world. This interaction does not require a second adult, breeding food, or love mode; it does not prove that ordinary food breeding works for the species. The common helper also does not require the clicked mob to be an adult. [Active mob interaction][mob-dispatch] · [Offspring creation and checks][offspring]

A successful result consumes one egg under ordinary Survival rules. If the child factory returns nothing, or the result cannot become a baby, this path fails and the mob can fall through to its normal interaction. Some important differences are:

| Target | Checked result |
| --- | --- |
| Frog | The Frog cannot enter baby state. A Frog egg used this way does **not** produce a Tadpole. |
| Crocodile or Parrot | Their offspring factories return no child for this helper. |
| Wandering Trader | Its child factory returns no child; ordinary trading can handle the click afterward. |
| Fox | A successfully created cub additionally trusts the player who used the egg. |

[Frog implementation][frog] · [Crocodile factory][crocodile] · [Parrot factory][parrot] · [Trader factory and interaction][trader] · [Fox egg hook][fox-trust] · [Consumption and failure handling][offspring]

Babies are not universally clones, tame, or owned by the player. The species' offspring factory can copy or randomize traits; use its current mob guide for the result. Multipart creatures also need care: Cachalot Whale and Giant Squid parts forward directly to the parent's ordinary mob interaction, bypassing the common egg helper. Target the actual main creature for a supported baby interaction. Anaconda and sauropod parts instead forward through the parent's full interaction path. [Whale part][cachalot-parts] · [Squid part][squid-parts] · [Anaconda part][anaconda-parts] · [Sauropod part][sauropod-parts]

## Dispensing eggs

Load an egg into a **[Dispenser](../blocks/DispenserAndDropper.md)** and trigger it to attempt creation in front of the output face. Startup registers this action for every registered spawn egg, and the device selects it for enabled items. The action uses the egg's entity type and consumes one egg after the spawn call; it does not require clicking an existing creature and does not run the baby-on-mob helper. [Startup registration][dispenser-registration] · [Egg action][dispenser-eggs] · [Device dispatch][dispenser-dispatch]

This is a separate creation path from hand use: it does not repeat the hand-placement Peaceful guard, and its consumption is not conditional on the creation call returning a mob. Do not treat an egg disappearing from the Dispenser as proof of a lasting creature. The normal mob despawn path removes types disallowed in Peaceful. Device timing, slot selection and wiring belong to the Dispenser guide. [Dispenser action][dispenser-eggs] · [Peaceful removal][despawn]

## When an egg does not work

- **Feature availability:** category entries are filtered by enabled features, eggs inherit their default entity type's feature requirements, and entity creation checks availability again. [Category filter][feature-filter] · [Egg requirement][egg-type] · [Entity check][entity-feature]
- **World and player restrictions:** block use checks reach, the world border and spawn protection; Adventure-style item placement restrictions can also prevent use. Fluid use has its own build-permission check. Spectators do not perform the ordinary item-use action. [Block request][block-request] · [World restriction][border] · [Adventure check][adventure] · [Fluid permission][fluid-permission] · [Spectator block handling][block-dispatch]
- **Entity interactions:** the server checks reach, world border and enabled items before dispatching the click, and Spectator interaction does not enter ordinary mob use. [Entity request][entity-request] · [Player interaction][player-interaction]
- **A different action handled the click:** block interaction or a permitted WorldEdit brush bound to the held item can run before the egg. [Block order][block-dispatch] · [Bound brush check][brush]

## Related pages

- [Inventory item browser](../mechanics/InventoryBrowser.md)
- [Mobs](../mobs/Mobs.md) and [Items](Items.md)
- [Monster Spawner](../blocks/MonsterSpawner.md), [Trial Spawner](../blocks/TrialSpawner.md), and [Dispenser and Dropper](../blocks/DispenserAndDropper.md)
- [Game modes](../gamemodes/Gamemodes.md) and [Commands](../commands/Commands.md)

## Sources and verification

Source-reviewed at `aaeea0b263d995334061e562cd5b71a853540f7f` on 2026-10-02. Enumerated every registered egg and category output; followed inventory insertion, block/fluid use, both spawner setters, mob/part dispatch, offspring creation, dispenser registration and active permission/feature gates. The item family supplies common use rules; current mob owners supply natural spawning, variants, breeding, taming and care. No inventory request, egg use, dispenser activation, spawner change or gameplay test was performed.

[registrations]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/world/item/Items.java#L1794-L2021
[category]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1960-L2128
[browser-list]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[client-add]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[server-add]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1906
[boss-eggs]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/world/item/Items.java#L1863-L1865
[wither-egg]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/world/item/Items.java#L2002
[give]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/server/commands/GiveCommand.java#L23-L46
[give-register]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/commands/Commands.java#L207
[binding]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/world/item/Item.java#L455-L457
[egg-type]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L143-L154
[centipede]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/world/item/Items.java#L1806
[centipede-entity]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/world/entity/EntityType.java#L1167-L1169
[placement]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L49-L103
[fluid]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L105-L129
[creation]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/world/entity/EntityType.java#L1736-L1776
[stack-config]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/world/entity/EntityType.java#L1710-L1728
[consume]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1080
[block-dispatch]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L340-L402
[spawner-rule]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/world/level/GameRules.java#L241-L243
[spawner-enabled]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/server/MinecraftServer.java#L1525-L1527
[spawner]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/world/level/block/entity/SpawnerBlockEntity.java#L77-L81
[trial-spawner]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/world/level/block/entity/TrialSpawnerBlockEntity.java#L59-L67
[trial-reset]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawner.java#L340-L344
[mob-dispatch]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/world/entity/Mob.java#L1054-L1107
[offspring]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L181
[frog]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/world/entity/animal/frog/Frog.java#L272-L288
[crocodile]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/alexsmobs/entity/EntityCrocodile.java#L604-L608
[parrot]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/world/entity/animal/Parrot.java#L319-L321
[trader]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java#L101-L129
[fox-trust]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/world/entity/animal/Fox.java#L583-L585
[cachalot-parts]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/alexsmobs/entity/EntityCachalotPart.java#L53-L56
[squid-parts]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/alexsmobs/entity/EntityGiantSquidPart.java#L63-L64
[anaconda-parts]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/alexsmobs/entity/EntityAnacondaPart.java#L75-L76
[sauropod-parts]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/alexscaves/server/entity/living/SauropodPartEntity.java#L60-L66
[dispenser-registration]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/server/Bootstrap.java#L42-L57
[dispenser-eggs]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L81-L106
[dispenser-dispatch]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/world/level/block/DispenserBlock.java#L82-L112
[despawn]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/world/entity/Mob.java#L606-L610
[feature-filter]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/world/item/CreativeModeTab.java#L215-L240
[entity-feature]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/world/entity/EntityType.java#L1856-L1864
[block-request]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1254-L1277
[border]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/server/level/ServerLevel.java#L819-L822
[adventure]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L373
[fluid-permission]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/world/entity/player/Player.java#L1524-L1532
[entity-request]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1703-L1742
[player-interaction]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/world/entity/player/Player.java#L854-L890
[brush]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/worldedit/platform/WorldEditIntegration.java#L120-L141

[server-insert]: https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2006-L2017
