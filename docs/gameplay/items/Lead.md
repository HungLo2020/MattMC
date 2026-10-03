# Lead

A **Lead** (`minecraft:lead`) lets you guide eligible mobs and boats, tie them to a fence, or transfer their connections to another eligible entity. Leads stack to **64**. Crafting requires only [String](String.md), and the current interaction system also supports cutting connections with [Shears](Shears.md). [Item registration][item] · [Default stack size][stack] · [Shared interactions][interact]

## Obtaining

### Crafting

Use **five String to make two Leads** in a Crafting Table. Place String in the top-left, top-center, middle-left, middle-center and bottom-right slots; leave the other four slots empty. **No Slimeball is used.** [Exact recipe][recipe] · [Recipe loading][recipe-load] · [Crafting result][crafting]

### Loot and trader animals

These bundled sources can supply Leads. Chest and archaeology results are random; each successful Lead selection supplies **one** item. The percentages below are calculated from the checked tables' weights and roll counts, rather than measured in-game. [Loot selection][loot-rolls] · [Single-item entry][loot-item] · [Uniform roll count][loot-uniform]

| Source | Possible Leads | Chance of at least one |
| --- | --- | --- |
| Woodland Mansion chest | 1–3 when present | About 28.3% per chest using its mansion table |
| Ancient City chest | 1–10 when present | About 16.1% per chest using its main city table |
| Trail Ruins suspicious gravel with **common** archaeology loot | 1 | About 2.2% per completed brushing |

[Mansion loot][mansion-loot] · [Mansion chest assignment][mansion-place] · [Ancient City loot][city-loot] · [City chest template][city-template] · [Common archaeology loot][archaeology] · [Suspicious-gravel assignment][archaeology-place] · [Brushing and item release][brushing]

The bundled optional trade-rebalance pack retains the same Lead entry and relevant roll weights in the Ancient City table. Other data packs can replace these results. Ordinary suspicious gravel without that common Trail Ruins loot assignment is not established as a Lead source by this table. [Optional city table][city-rebalance] · [Loaded loot data][loot-load]

A [Wandering Trader](../mobs/WanderingTrader.md) event attempts to spawn **two [Trader Llamas](../mobs/TraderLlama.md)** attached to the merchant; either animal placement can fail. Use usable Shears on an attached Llama to drop its Lead without attacking the merchant. These Leads come from existing leash connections, not a Lead sale or the trader's death-loot table. The checked Wandering Trader offer list has no Lead trade. See the Trader Llama guide before keeping an animal, because its special despawn timer matters. [Overworld spawner][trader-install] · [Llama attachment][trader-spawn] · [Shears handling][shears] · [Trader offers][trader-offers] · [Empty trader loot][trader-loot]

Leads are also listed in the [inventory item browser](../mechanics/InventoryBrowser.md). Item insertion requires **Creative** under its [mode and permission limits](../mechanics/InventoryBrowser.md#mode-and-permission-limits); browsing in Survival is not an acquisition route. [Category entry][creative]

## Usage

### Attaching and releasing

Hold a Lead and interact with an eligible entity within normal interaction reach. A successful new attachment consumes **one Lead in Survival**; Creative restores the held count. A target already held by another player cannot be taken with this interaction. If its existing holder is a fence knot or another non-player entity, the old connection drops its Lead before the new one is attached. [Server interaction dispatch][server-interact] · [Player interaction and Creative restoration][player-interact] · [Attachment action][interact]

To release an entity attached to you, interact with it **without Sneak/Crouch**, preferably empty-handed. Survival drops one Lead at that entity; ordinary Creative release removes the connection without dropping it. Interacting with a Lead still releases your own connection rather than adding a second one. [Release priority][interact] · [Lead-drop location][leash-drop]

Eligibility is checked for the entity being attached; it is not a universal taming or ownership test. Most mobs inherit a rule excluding enemy mobs, but species can override it. Reviewed examples include:

- **Boats**, including the shared boat interaction, support Leads before their boarding interaction [Boat behavior][boat]
- **Wolves** can be attached while not angry; that rule does not require taming [Wolf rule][wolf]
- **Squid** and **Dolphins** explicitly permit attachment [Squid rule][squid] · [Dolphin rule][dolphin]
- **Villagers and Wandering Traders**, **Pandas**, and **Turtles** explicitly reject attachment [Villager rule][villager] · [Panda rule][panda] · [Turtle rule][turtle]

These are examples, not a complete compatibility list. An older bundled mob method with a different signature does not override the active rule: the [Cave Centipede](../mobs/CaveCentipede.md), for example, still inherits the enemy exclusion. [Current shared rule][mob-rule] · [Centipede inheritance][centipede-type] · [Older overload][centipede-old] · [Monster classification][monster]

### Fence knots

Use a fence while holding your connections to transfer eligible nearby entities to a knot. An empty-hand fence interaction works; the Lead item's fence action also calls the same binding path. No additional Lead is consumed for the transfer. A valid anchor must belong to the bundled fence tag, which includes its wooden-fence tag and Nether Brick Fence. [Fence interaction][fence-use] · [Binding action][fence-bind] · [Fence tag][fence-tag] · [Nested wooden-fence tag][wooden-fences]

Interact with the **knot** to transfer your eligible connections onto it. If none transfer onto it, interacting without Sneak/Crouch instead takes its eligible connections back into your hand. Each attached entity must still be within its allowed distance of its new holder. The knot disappears when its last connection is removed. [Knot interaction and cleanup][knot]

### Transferring between entities and cutting connections

While holding one or more connections, **Sneak/Crouch and interact with another eligible entity** to transfer those connections to it. A living target must be an adult to become the holder this way; a boat can also be a holder. You do not need another Lead in your hand. Transfer only succeeds for connections within their own distance limit, and an entity cannot attach to itself. This transfer runs before ordinary release and Shears handling. [Transfer order][transfer] · [Attachment limits][eligibility]

For example, attach a boat to yourself, then Sneak/Crouch-interact with an adult [Happy Ghast](../mobs/HappyGhast.md). Boats support its four-point leash connection; this still uses the existing single Lead. The cargo's distance limit does not increase merely because a Happy Ghast holds it. [Boat connection points][boat-quad] · [Happy Ghast holder][happy] · [Four-point physics][quad]

Use **unbroken Shears without Sneak/Crouch** on an entity or fence knot to cut its own leash and nearby connections held by it. Each removed connection drops one Lead at its attached entity. One successful cutting interaction costs **one Shears durability in Survival** before normal modifiers, even when it removes multiple connections. Creative cutting causes no tool wear; Spectators cannot use this interaction. Cutting takes priority over removing equipment such as a [Saddle](Saddle.md) or [Harness](Harnesses.md). Avoid Sneak/Crouch here because a successful transfer can take priority over cutting. [Shared interaction order][interact] · [Cutting all connections][shears] · [Knot's Shears priority][knot] · [Wear processing][wear] · [Spectator gate][player-interact]

## Behavior

### Distance and movement

The normal snap limit is **more than 12 blocks between the entities' bounding-box centers**. Beyond that limit the connection breaks and drops its Lead at the attached entity. A Happy Ghast's **own** leash instead has a **16-block** snap limit. These are leash limits, not extended player interaction reach. [Distance test][eligibility] · [Tick and break behavior][leash-tick] · [Happy Ghast distances][happy]

The normal elastic distance is **6 blocks**, or **10** for a Happy Ghast's own leash. Pulling uses the actual attachment points and entity sizes, so six is not a universal center-to-center threshold for when movement starts. Ordinary pathfinding mobs also try to approach their holder at close range unless their behavior overrides that or they are panicking. Leave room around obstacles and keep cargo nearby rather than treating a Lead as a rigid rail. [Elastic calculations][quad] · [Pathfinding response][pathfinding]

### Recovery, saving and travel

- Normal Survival release, Shears cutting and excessive-distance breakage drop the connected Lead. The drop appears at the attached entity, not automatically in your inventory [Release][interact] · [Drop action][leash-drop]
- If the attached entity or its holder becomes dead, removed or otherwise unable to interact with the level, the shared tick drops the Lead only when `doEntityDrops` is enabled; otherwise it removes the connection without a drop [Validity test][entity-valid] · [Invalid-connection cleanup][leash-tick]
- Attaching an entity makes it leave a vehicle it is riding. If a mob subsequently becomes a passenger, that successful boarding drops its leash [Attachment and dismount][leash-set] · [Mob boarding][mob-rule]
- Mob and boat leash data are saved. Entity holders are recorded by UUID, while fence knots are recorded by block position. An unresolved entity holder eventually produces a Lead drop once the loaded entity's tick count exceeds 100 [Mob save/load][mob-save] · [Boat save/load][boat] · [Saved-holder restoration][leash-save] · [Saved holder format][leash-codec]
- **A Lead does not generally make a mob persistent.** The shared despawn check does not exempt an entity merely for being leashed. Trader Llamas have their own retention conditions, and their timer expiry explicitly removes the leash without dropping it [Shared despawn check][despawn] · [Trader Llama timer][llama-timer]
- Do not rely on a Lead to transport a companion through teleportation or a portal. Same-dimension separation can exceed the snap limit. Cross-dimension travel copies saved entity data but removes the source entity's live leash without a drop, so recovery depends on subsequent holder restoration; it is not a guaranteed tether across worlds. Release and collect the Lead before moving entities separately [Teleport copy][teleport-copy] · [Dimension cleanup][dimension-remove] · [Holder restoration][leash-save]

Tamed animals using the shared follow-owner behavior cannot use that behavior's normal following or teleport-to-owner path while leash data is present. A Lead is therefore not a way to make a pet teleport after you. [Follow-owner gate][follow-owner] · [Leash movement restriction][owner-restriction]

## Notes

- This guide describes the bundled implementation, including the current shared interaction order; data packs, game rules and later source changes can alter outcomes
- For animal care and special retention rules, use [Llama](../mobs/Llama.md), [Trader Llama](../mobs/TraderLlama.md), and [Happy Ghast](../mobs/HappyGhast.md). Related items: [String](String.md), [Shears](Shears.md), [Brush](Brush.md), and [Items](Items.md)

## Sources and verification

Source-reviewed at MattMC commit `2fff1ef19106350f806ddedd4fb3c3b4fbc44716` on **2026-10-03**. Recipe and loot resources, nested fence tags, trader spawning and offers, current interaction dispatch, leash ticks, save/load, travel and removal paths were inspected. The loot percentages are table calculations. **No in-game crafting, loot, leashing, cutting, hauling, despawn or portal test was run.**

[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L2140-L2141
[stack]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[interact]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L2134-L2178
[recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/lead.json#L1-L16
[recipe-load]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L84
[crafting]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/inventory/CraftingMenu.java#L47-L75
[loot-rolls]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/storage/loot/LootPool.java#L60-L101
[loot-item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/storage/loot/entries/LootItem.java#L29-L36
[loot-uniform]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/storage/loot/providers/number/UniformGenerator.java#L24-L31
[mansion-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/chests/woodland_mansion.json#L1-L69
[mansion-place]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/levelgen/structure/structures/WoodlandMansionPieces.java#L1215-L1230
[city-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/chests/ancient_city.json#L1-L411
[city-template]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/structure/ancient_city/structures/barracks.nbt
[archaeology]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/archaeology/trail_ruins_common.json#L1-L150
[archaeology-place]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/worldgen/processor_list/trail_ruins_roads_archaeology.json#L47-L74
[brushing]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/entity/BrushableBlockEntity.java#L60-L146
[city-rebalance]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/datapacks/trade_rebalance/data/minecraft/loot_table/chests/ancient_city.json#L1-L411
[loot-load]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L53-L70
[trader-install]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/MinecraftServer.java#L406-L422
[trader-spawn]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/npc/WanderingTraderSpawner.java#L92-L118
[shears]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L2182-L2208
[trader-offers]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L714-L830
[trader-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/entities/wandering_trader.json#L1-L4
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1456-L1464
[server-interact]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1702-L1740
[player-interact]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/player/Player.java#L854-L878
[leash-drop]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Leashable.java#L112-L139
[boat]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/vehicle/AbstractBoat.java#L671-L699
[wolf]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java#L657-L660
[squid]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Squid.java#L82-L85
[dolphin]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Dolphin.java#L349-L352
[villager]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L204-L207
[panda]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Panda.java#L313-L316
[turtle]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Turtle.java#L281-L284
[mob-rule]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1211-L1229
[centipede-type]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityCentipedeHead.java#L40-L50
[centipede-old]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityCentipedeHead.java#L295-L297
[monster]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/monster/Monster.java#L31-L35
[fence-use]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/FenceBlock.java#L71-L74
[fence-bind]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/LeadItem.java#L21-L58
[fence-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/block/fences.json#L1-L6
[wooden-fences]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/block/wooden_fences.json#L1-L16
[knot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/decoration/LeashFenceKnotEntity.java#L70-L121
[transfer]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L2108-L2132
[eligibility]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Leashable.java#L56-L70
[boat-quad]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/vehicle/AbstractBoat.java#L347-L369
[happy]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/HappyGhast.java#L478-L513
[quad]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Leashable.java#L208-L263
[leash-tick]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Leashable.java#L142-L203
[pathfinding]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/PathfinderMob.java#L51-L74
[entity-valid]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L328-L334
[leash-set]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Leashable.java#L293-L318
[mob-save]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L352-L384
[leash-save]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Leashable.java#L77-L108
[leash-codec]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Leashable.java#L360-L373
[despawn]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L598-L630
[llama-timer]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/horse/TraderLlama.java#L79-L106
[teleport-copy]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L2900-L2977
[dimension-remove]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L3050-L3054
[follow-owner]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/goal/FollowOwnerGoal.java#L35-L85
[owner-restriction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/TamableAnimal.java#L282-L284
[wear]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L509
