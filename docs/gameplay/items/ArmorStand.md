# Armor Stand

An **Armor Stand** displays armor and other equipment. Place it, use an equipment item on it to dress it, and use an empty hand near a worn piece to take that piece back. Ordinary crafted stands have **no arms**, so normal hand interaction cannot give them a sword, tool, or other held item. [Equipment interaction][equipment] · [Default flags][defaults]

## Obtaining

Craft **one Armor Stand** at a [Crafting Table](../blocks/CraftingTable.md) using **six Sticks and one Smooth Stone Slab**:

- Top row: Stick, Stick, Stick
- Middle row: empty, Stick, empty
- Bottom row: Stick, Smooth Stone Slab, Stick

The slab must be **Smooth Stone Slab**, not Stone Slab or another slab. The item is registered as `minecraft:armor_stand` and stacks to **16**. [Recipe][recipe] · [Registration][registration]

Armor Stands also appear in the Creative catalog. Follow the [inventory browser's mode and permission limits](../mechanics/InventoryBrowser.md#mode-and-permission-limits); catalog visibility does not itself supply a Survival item. [Creative entries][creative-functional] · [Second entry][creative-redstone]

## Usage

### Place and face the stand

Use the item against a block's top or side; clicking the **underside** fails. Placement targets the clicked position if it is replaceable, otherwise the adjacent position on that face. Leave about two blocks of vertical room: the ordinary stand's placement box is **0.5 blocks wide and 1.975 blocks high**, and placement requires both collision clearance and no entities in that box. It is an entity rather than a placed block. [Placement][placement] · [Placement position][context] · [Entity registration and size][entity-type]

The stand's yaw snaps to **45-degree increments**, giving eight facing directions based on your orientation when you place it. The server creates the stand; ordinary Survival placement spends one item, while the shared Creative item-use path restores the held stack count. [Placement and facing][placement] · [Creative handling][server-use]

The shared placement path still applies server reach, world-border and spawn-protection checks, enabled features, item cooldowns, and game-mode restrictions. Spectators cannot place stands, and Adventure item-use restrictions apply before placement. Interactive blocks can take the click first; secondary use, normally crouching, skips that block interaction. A bound WorldEdit brush can also take the server action first. [Client use][client-use] · [Server admission][server-admission] · [World permissions][world-permissions] · [Server use][server-use] · [Adventure gate][item-use] · [Brush handling][brush]

### Put on and take off equipment

- **Equip:** hold a wearable item and use it on the stand. The item's equipment slot determines where it goes; you do not need to aim at that body part when putting it on.
- **Retrieve:** use an empty hand near the head, chest, legs, or feet to take the corresponding equipped piece into that hand. The clicked height matters, and overlapping regions can make it useful to aim higher or lower.
- **Replace:** a single held item can swap with an occupied slot. A held stack larger than one cannot replace an occupied slot; take the existing piece off first.
- **Creative:** equipping an empty slot copies one item without consuming the held stack. Removing or swapping existing equipment still transfers that equipment. Spectator interactions do not change it. [Slot choice][slot-choice] · [Equipment interaction and swaps][equipment] · [Client entity interaction][client-entity] · [Server entity interaction][server-entity]

### Arms and customized stands

Ordinary stands start with the arms flag off. The hand slots are disabled for normal interaction in that state; the player's off hand does not bypass that restriction. Wearable armor still works. A command-customized stand with `ShowArms` enabled can accept hand-slot items, subject to its other equipment restrictions. [Default flags][defaults] · [Equipment interaction][equipment]

Saved entity data also supports `Small`, `Invisible`, `NoBasePlate`, `Marker`, `DisabledSlots`, and `Pose`. These describe customized entities, not options unlocked by ordinary right-clicking. `DisabledSlots` can block equipping, swapping, or removal; marker stands cannot be picked by ordinary targeting and skip the equipment interaction. Do not assume a decorative stand created by commands behaves like a freshly crafted one. See [Commands](../commands/Commands.md) for permissions and safe experimentation. [Saved customization][customization] · [Slot locks][equipment] · [Marker targeting][marker] · [Summon data path][summon] · [Entity-data loading][entity-load]

## Behavior

### Break and recover safely

For a valuable display, **take off its equipment by hand before breaking the stand**. In Survival, use melee attacks on an ordinary stand: normally the first hit makes it wobble, and another hit within **five game ticks**, nominally **0.25 seconds at 20 ticks per second**, breaks it. A normal player break drops one Armor Stand plus its equipment. [Break behavior][damage] · [Recovery][drops] · [Breakable damage types][break-tag] · [Player damage types][player-tag]

Attacking still depends on server reach and interaction permissions, and players without building permission cannot use this break path. [Attack admission][server-entity] · [Player attack][player-attack] · [Server damage dispatch][damage-dispatch] · [Stand protection][attack-protection] · [Building permission][damage]

The stand and equipment drops in this path use the **`doTileDrops`** game rule, which defaults to true. With that rule off, those item drops are suppressed. Recovering equipment directly with an empty hand avoids relying on a death-drop path. [Drop gate][drop-gate] · [Game-rule name and default][drop-rule] · [Equipment retrieval][equipment]

The recovered stand item carries the stand's custom name, but this break path does not copy its pose or other entity flags into the item. Breaking and replacing a customized stand is therefore not a way to preserve the whole display setup. [Recovered item][drops]

### Hazards and destructive removal

- **Creative attacks:** immediately remove the stand without dropping the stand or its equipment. Retrieve anything you want first.
- **Fire and explosions:** the fire-damage and explosion branches can destroy the stand and run equipment drops, but do not create the recoverable stand item. The bundled stand loot table has no item pools to add it back. Explosions must be configured to affect this kind of entity; fire and nearby blasts are poor recovery tools.
- **Void damage and generic kill:** bypass the ordinary break-and-drop path and remove the stand without those recovery drops. [Damage branches][damage] · [Fire destruction and equipment drops][drops] · [Removal and explosion handling][removal] · [Ignition damage][ignite-tag] · [Burning damage][burn-tag] · [Bypass damage][bypass-tag] · [Bundled loot table][loot]

Ordinary stands do not respond to the usual entity-pushing interaction. They still use movement physics unless configured as a marker or given no gravity; nearby rideable minecarts have a special collision interaction. Build displays on stable surfaces and keep them clear of transport paths. [Physics and minecarts][physics] · [Movement][movement] · [Active movement and collision calls][movement-dispatch]

## Notes

- This item is registered as `minecraft:armor_stand` and has a maximum stack size of 16. [Registration][registration]
- Its checked Creative entries are **Functional Blocks** and **Redstone Blocks**. [Functional entry][creative-functional] · [Redstone entry][creative-redstone]
- A customized stand may be invisible, protected, or locked; failed interaction alone does not mean the recipe or ordinary placement is broken. [Customization][customization] · [Equipment locks][equipment] · [Damage gates][damage]

Related: [Trowel](Trowel.md) · [Commands](../commands/Commands.md) · [Game modes](../gamemodes/Gamemodes.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-04** at MattMC commit `78e8e0423084f010bb47e36132550619b37644c2`. Registration, bundled recipe and damage tags, client/server placement and entity interactions, equipment transfer, active damage dispatch, and recovery gates were inspected. Recipes, damage tags, and loot describe the bundled resources; data packs can change them. [Recipe loading][recipe-loader] No in-game crafting, placement, equipment, breaking, command-customization, or multiplayer test was run.

[recipe-loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L87
[context]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/context/BlockPlaceContext.java#L25-L55
[client-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L317-L376
[server-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L340-L399
[server-admission]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1255-L1299
[world-permissions]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerLevel.java#L819-L822
[item-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L371
[brush]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/worldedit/platform/WorldEditIntegration.java#L120-L142
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/armor_stand.json#L1-L17
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2128
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ArmorStandItem.java#L26-L59
[entity-type]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/EntityType.java#L269-L271
[defaults]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/decoration/ArmorStand.java#L79-L129
[equipment]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/decoration/ArmorStand.java#L184-L261
[slot-choice]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3575-L3578
[client-entity]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L428-L439
[server-entity]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1703-L1763
[customization]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/decoration/ArmorStand.java#L132-L163
[marker]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/decoration/ArmorStand.java#L545-L548
[damage]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/decoration/ArmorStand.java#L263-L315
[player-attack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L971-L1031
[damage-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Entity.java#L1772-L1784
[attack-protection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/decoration/ArmorStand.java#L550-L553
[drops]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/decoration/ArmorStand.java#L358-L387
[drop-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L387-L415
[drop-rule]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/GameRules.java#L60-L62
[removal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/decoration/ArmorStand.java#L434-L443
[ignite-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/damage_type/ignites_armor_stands.json#L1-L6
[burn-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/damage_type/burns_armor_stands.json#L1-L5
[bypass-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/damage_type/bypasses_invulnerability.json#L1-L6
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/armor_stand.json#L1-L4
[physics]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/decoration/ArmorStand.java#L111-L181
[movement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/decoration/ArmorStand.java#L399-L404
[creative-functional]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1031-L1118
[creative-redstone]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1283-L1362

[summon]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/commands/SummonCommand.java#L35-L111
[entity-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Entity.java#L1990-L2041
[break-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/damage_type/can_break_armor_stand.json#L1-L6
[player-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/damage_type/is_player_attack.json#L1-L6
[movement-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2815-L2864
