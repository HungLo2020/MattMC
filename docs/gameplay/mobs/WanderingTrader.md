# Wandering Trader

A **Wandering Trader** brings a temporary selection of plants, blocks, supplies, and buying offers to the Overworld. Check its stock when it arrives: a naturally spawned trader eventually disappears, and sold-out offers do not replenish. It is a separate merchant from an employed [Villager](Villager.md). [Arrival][site] · [Offer selection][trade-start] · [Use counting][uses] · [Lifetime][countdown]

## Obtaining

### Natural visits

The normal arrival system runs in the **Overworld**. Both `doMobSpawning` and `doTraderSpawning` default to **true** and must permit its scheduler to run. It is independent of the ordinary creature-cap calculation and does not require a village, a profession block, or hostile difficulty. [Overworld installation][overworld] · [Other dimensions][other-worlds] · [Outer dispatch][outer-gate] · [Spawner loop][dispatch] · [Mob rule][mob-rule] · [Trader rule][trader-rule] · [Scheduler][scheduler]

In an uninterrupted ticking world, the scheduler checks its delay every **1,200 ticks** and starts a new main attempt after **24,000 ticks**, about **20 minutes** at the normal rate. A main attempt still needs random and placement checks to succeed. The checked comparison allows 26, 51, or 76 outcomes out of 100 as its chance rises after failed attempts, followed by a separate **1-in-10** roll when a player is available. These are code-level gates, **not an observed arrival rate or a promise of a visitor every day**. Disabling either rule pauses this scheduling path. [Delay and inclusive random comparison][scheduler] · [Second roll][site]

An attempt chooses a random living player in that world. It uses a registered **Bell meeting point within 48 blocks** when one is found, otherwise the player's position. The site search then samples up to ten surface positions, with horizontal offsets from **−48 through +47** on each axis around that center. A Bell can therefore change where it looks; ringing one does not force an arrival, and the candidate area is not always centered directly on the player. [Player selection][random-player] · [Meeting point and site search][site] · [Bell registration][bell] · [Position sampling][space]

The candidate needs valid ground and empty spawn space within the world border, followed by a **2 × 3 × 2** collision-clear check. The bundled excluded-biome tag contains **The Void**. A cramped or unsuitable site can fail even after the random rolls succeed. The event attempts to place **two [Trader Llamas](TraderLlama.md)** nearby and leash them to the merchant; either Llama placement can fail. [Placement registration][placement] · [Ground checks][ground] · [Space check][space] · [Excluded biome][excluded] · [Llama attempts][site] · [Llama placement][llama-spawn]

### Spawn Egg and commands

The [Wandering Trader Spawn Egg](../items/WanderingTraderSpawnEgg.md) is an ordinary listed item, so MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) can supply it in **Survival as well as Creative**. Egg placement creates the merchant directly. This bypasses the natural visit scheduler and does **not** add the two companion Llamas or the natural visit's countdown. [Egg registration][egg-item] · [Category listing][egg-category] · [Egg creation][egg-spawn] · [Default delay][default-delay] · [Event-only setup][site]

With command permission level **2 or higher**, `/summon minecraft:wandering_trader ~ ~ ~` creates an ordinary trader at the chosen position. With no custom entity data, its despawn delay remains zero and it has no event-created companions. Commands or modified egg data can explicitly change that state. The command is source-checked here; it was not executed. [Command syntax and permission][summon] · [Creation][summon-create] · [Loaded delay default][saved-delay]

## Trading

Interact with a living adult that is not already serving another player, choose an offer, supply the shown payment, and take the result. See [Trading](../trading/Trading.md#make-a-trade) for the shared exchange workflow. No job-site block is needed for this merchant. [Interaction][trade-start] · [Taking the result][take-result]

A normal trader selects **nine offers**: **two buying offers**, **two special-goods offers**, and **five general-goods offers**. Each pool is sampled without reusing the same listing in that pool. These offers are available from the start; the trader has no trading-level progress bar and cannot unlock more tiers. Its caller uses this table directly, without the optional villager trade-rebalance switch. [Three active pools][trade-pools] · [Selection][selection] · [Active caller and hidden progress bar][trade-start] · [No villager-level experience][no-xp-level] · [No experience-level update][no-xp-change]

### Goods you can sell

Only **two** of these six listings are chosen for one trader. Each selected buying offer can be completed **twice**. Amounts are per completed trade. [Buying pool][buy-pool] · [Buying constructor][buy-constructor]

| You give | You receive | Uses per selected offer |
| --- | --- | ---: |
| 1 Water Bottle | 1 Emerald | 2 |
| 1 Water Bucket | 2 Emeralds | 2 |
| 1 Milk Bucket | 2 Emeralds | 2 |
| 1 Fermented Spider Eye | 3 Emeralds | 2 |
| 4 Baked Potatoes | 1 Emerald | 2 |
| 1 Hay Bale | 1 Emerald | 2 |

The Water Bottle offer checks the water-potion contents, so a different potion is not a substitute. **Selling a bottle or filled bucket consumes the whole payment item**; this trade does not return its empty container. Budget that container as part of the cost. [Water requirement][water-type] · [Payment consumption][consume-payment]

### Special goods

Two listings are chosen from this pool. The nine log species are separate entries, so both selected offers can be logs. [Special pool][special-pool] · [Sale amounts and stock][sale-constructor] · [Selection][selection]

| You pay | You receive | Uses per selected offer |
| --- | --- | ---: |
| 1 Emerald | 1 Packed Ice | 6 |
| 6 Emeralds | 1 Blue Ice | 6 |
| 1 Emerald | 4 Gunpowder | 2 |
| 3 Emeralds | 3 Podzol | 6 |
| 1 Emerald | 8 logs of the selected species | 4 |
| 6–20 Emeralds | 1 randomly enchanted Iron Pickaxe | 1 |
| 5 Emeralds | 1 extended Potion of Invisibility | 1 |

The log entries cover Acacia, Birch, Dark Oak, Jungle, Oak, Spruce, Cherry, Mangrove, and Pale Oak. The Pickaxe price is rolled when its offer is generated; it is **not a one-Emerald enchanted tool**, and its enchantments are not a fixed promise. [Log entries][special-pool] · [Pickaxe generation][pickaxe]

### General goods

The five general selections can include saplings and Mangrove Propagules, seeds, flowers, dyes, live Coral Blocks, filled fish buckets, and other growing or building supplies. This includes Pale Oak Saplings, Open Eyeblossoms, Pale Moss, Wildflowers, Dry Tall Grass, and Firefly Bushes in the bundled table. The complete pool is linked in the source; these are selected examples, not guaranteed stock. [General pool][general-pool]

| You pay | You receive | Uses per selected offer |
| --- | --- | ---: |
| 5 Emeralds | 1 selected Sapling or Mangrove Propagule | 8 |
| 3 Emeralds | 1 Tropical Fish Bucket or Pufferfish Bucket, as listed | 4 |
| 1 Emerald | 2 Small Dripleaves | 5 |
| 1 Emerald | 2 Pointed Dripstones | 5 |
| 1 Emerald | 8 Sand | 8 |
| 1 Emerald | 4 Red Sand | 6 |
| 5 Emeralds | 1 Nautilus Shell | 5 |

These amounts and use limits come from the active listings and sale constructor. Each kind of sapling and each fish-bucket kind is its own listing. Check the actual screen before preparing a purchase. [Listed examples][general-pool] · [Offer construction][sale-constructor]

### Stock does not restock

Completing a trade spends one use of that offer. When all its uses are spent, it stays sold out. Closing the screen, waiting for another day, or placing a workstation does not refill it or reroll its offers. The merchant caches and saves the generated offers, including their use counts; its active goals and trade path contain no villager restocking routine. [Use counting][uses] · [Sold-out check][out-of-stock] · [Cached offers][cached-offers] · [Saved offers][saved-offers] · [Goals][ai]

The normal generated offers start without demand or special-price adjustments, and this trader has no villager reputation, Hero-of-the-Village discount, or demand-update path. Each completed default trade produces **3–6 experience points** for players to collect; that does not promote the trader. [Offer defaults][offer-defaults] · [Active trading path][trade-start] · [Reward][trade-xp]

## Behavior

The trader is passive and has **20 health points, or 10 hearts**. It flees several threats, including Zombies, Pillagers, Evokers, Vindicators, Vexes, Illusioners, and Zoglins, and panics when hurt. Keep a useful merchant away from danger while shopping. Its attached Trader Llamas can target an attacker in defense of their leash holder. [Default attributes][attributes] · [Attribute inheritance][mob-attributes] · [Living health attribute][living-attributes] · [Health value][health-default] · [Avoidance goals][ai] · [Llama defense][defending-llamas]

When the world's outside-light check becomes dark, a visible trader drinks an Invisibility potion. When it becomes bright again, an invisible trader drinks Milk. This follows the world's light state, not the block light of its shelter; invisibility should not be treated as protection from damage. Drinking temporarily places the item in its main hand. [Drinking conditions][ai] · [Outside-light test][outside-light] · [Use-item goal][use-item]

Stand close and provide dry, safe footing while trading. The trade goal stops navigation, but water, leaving the ground, being hurt, or separation beyond four blocks can interrupt it. Closing the screen clears the trading session. [Trade goal][trade-goal] · [Menu validity][trade-valid] · [Closing the screen][close-menu]

There is no ordinary breeding route, and a matching Spawn Egg used on the trader cannot produce a baby: the ageable offspring factory returns no offspring. You cannot attach a Lead to the merchant itself. For keeping its animals, use [Trader Llama: finding and keeping one](TraderLlama.md#finding-and-keeping-one), then the shared [Llama care guide](Llama.md). [Offspring factory][no-breed] · [Egg offspring caller][egg-offspring] · [Leash restriction][leash]

## Despawning and drops

A natural visit starts with **48,000 game ticks**, about **40 minutes** at normal ticking speed. The remaining delay decreases only while the trader is ticking and **not trading**. An open, valid trading session pauses it; completing a trade does not reset it. Leaving the countdown unused until it reaches zero discards the trader. This is not an exact wall-clock lifetime. [Event setup][site] · [Countdown][countdown]

**Naming or confining the trader does not stop that countdown.** The timer does not check a custom name, the generic persistence flag, or whether the trader is riding in a vehicle. Its remaining delay is saved, so saving and loading does not grant a fresh visit. Ordinary distance-based despawning is disabled, but that is separate from the timed removal. [Name Tag effects][name] · [Timer condition][countdown] · [Saved state and distance rule][saved-delay]

The companion Llamas have their own related countdown and retention conditions; **taming is the ordinary lasting way to keep them**. Do not assume naming a Llama or merely putting it in a caravan preserves it. Follow the [Trader Llama timer guide](TraderLlama.md#the-timed-despawn-rule). [Llama conditions][llama-timer]

The trader's bundled death-loot table has **no item pools**, so killing it does not release the displayed trade stock or an ordinary pile of Emeralds. An item actually held while drinking can use the separate, chance-based equipment-drop path when its player-kill, mob-loot, and drop-prevention conditions allow it. Timer expiry is a discard, not that death-loot path. Leads belong to the companion Llamas' leash system; see their guide for separating them without killing the merchant. [Empty loot table][loot] · [Default loot key][loot-key] · [Entity lookup][entity-loot] · [Mob lookup][mob-loot] · [Loaded loot dispatch][loot-load] · [Mob-loot gate][loot-gate] · [Death dispatch][death-dispatch] · [Held equipment drops][equipment-drop] · [Drinking item][use-item] · [Timed discard][countdown]

## Notes

- Entity ID: `minecraft:wandering_trader`
- Spawn Egg ID: `minecraft:wandering_trader_spawn_egg`
- Registered as a creature, with a **0.6 × 1.95-block** adult size. [Entity registration][entity] · [Egg registration][egg-item]

## Related pages

- [Trading](../trading/Trading.md): the shared exchange screen and employed-Villager mechanics
- [Trader Llama](TraderLlama.md): companion acquisition, taming, and timed retention
- [Llama](Llama.md): food, cargo, decoration, and caravans
- [Wandering Trader Spawn Egg](../items/WanderingTraderSpawnEgg.md)
- [Emerald](../items/Emerald.md)
- [Inventory item browser](../mechanics/InventoryBrowser.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6` on 2026-10-02. Followed the installed Overworld scheduler and tick caller, placement checks, active offer table and payment constructors, interaction and drinking goals, timer, saved offers, Llama links, and loaded loot. No in-game spawning, trading, command, timing, or drop test was run. Timings and random gates are source-derived; altered entity data, game rules, resources, and equipment components can change the described defaults.

[site]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/WanderingTraderSpawner.java#L74-L107
[trade-start]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java#L99-L141
[uses]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L120-L128
[countdown]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java#L209-L220
[overworld]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/MinecraftServer.java#L417-L422
[other-worlds]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/MinecraftServer.java#L457-L470
[outer-gate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/level/ServerChunkCache.java#L370-L407
[dispatch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/level/ServerLevel.java#L460-L463
[mob-rule]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/GameRules.java#L53-L55
[trader-rule]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/GameRules.java#L167-L169
[scheduler]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/WanderingTraderSpawner.java#L39-L71
[random-player]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/level/ServerLevel.java#L904-L908
[bell]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiTypes.java#L135-L135
[space]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/WanderingTraderSpawner.java#L120-L146
[placement]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L176-L176
[ground]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/SpawnPlacementTypes.java#L24-L42
[excluded]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/worldgen/biome/without_wandering_trader_spawns.json#L1-L5
[llama-spawn]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/WanderingTraderSpawner.java#L110-L137
[egg-item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1997-L1999
[egg-category]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2114-L2114
[egg-spawn]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L89-L103
[default-delay]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java#L49-L57
[summon]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/commands/SummonCommand.java#L35-L76
[summon-create]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/commands/SummonCommand.java#L79-L107
[saved-delay]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java#L143-L160
[take-result]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/MerchantResultSlot.java#L48-L63
[trade-pools]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L714-L830
[selection]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L232
[no-xp-level]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L72-L75
[no-xp-change]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L112-L118
[buy-pool]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L714-L726
[buy-constructor]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1217-L1243
[water-type]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1166-L1172
[consume-payment]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/trading/MerchantOffer.java#L193-L203
[special-pool]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L728-L748
[sale-constructor]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1458-L1478
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1328-L1356
[general-pool]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L750-L828
[out-of-stock]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/trading/MerchantOffer.java#L164-L180
[cached-offers]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L98-L110
[saved-offers]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L158-L176
[ai]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java#L59-L97
[offer-defaults]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/trading/MerchantOffer.java#L54-L68
[trade-xp]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java#L163-L169
[attributes]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L267-L267
[mob-attributes]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Mob.java#L159-L161
[living-attributes]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L326-L331
[health-default]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L57-L59
[defending-llamas]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/animal/horse/TraderLlama.java#L125-L152
[outside-light]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/Level.java#L357-L363
[use-item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/goal/UseItemGoal.java#L25-L46
[trade-goal]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/goal/TradeWithPlayerGoal.java#L15-L39
[trade-valid]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L247-L250
[close-menu]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/MerchantMenu.java#L149-L169
[no-breed]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java#L99-L108
[egg-offspring]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L181
[leash]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L204-L207
[name]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/NameTagItem.java#L16-L29
[llama-timer]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/animal/horse/TraderLlama.java#L87-L107
[loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/entities/wandering_trader.json#L1-L4
[loot-key]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[entity-loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Entity.java#L3922-L3924
[mob-loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Mob.java#L402-L405
[loot-load]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1527
[loot-gate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L565-L567
[death-dispatch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1474
[equipment-drop]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Mob.java#L813-L838
[entity]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/EntityType.java#L1480-L1482
