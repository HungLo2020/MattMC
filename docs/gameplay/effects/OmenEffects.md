# Bad Omen, Raid Omen, Trial Omen, and Hero of the Village

**Keep an Ominous Bottle undrunk until you have chosen the encounter.** Bad Omen can become a village's Raid Omen countdown or a Trial Spawner's Trial Omen. Hero of the Village is a separate raid-victory reward that villagers use for discounts and gifts. Carrying a bottle does not grant any of these effects. [Bottle grant][bottle-effect] · [Village conversion][bad-omen] · [Trial conversion][trial-detection] · [Victory reward][raid-victory]

Times below use **20 game ticks per second**. Level I means amplifier `0`; a higher level and a longer duration are different properties. These are source-reviewed timings, not measured wall-clock guarantees on a paused or slow server. [Effect instances][effect-constructors] · [Server duration tick][effect-tick]

| Effect and ID | Ordinary grant | What the level changes |
| --- | --- | --- |
| Bad Omen, `minecraft:bad_omen` | Bottle levels I–V, **120,000 ticks / 100 minutes** | The resulting Raid Omen level or Trial Omen duration |
| Raid Omen, `minecraft:raid_omen` | Village conversion, same level as Bad Omen, **600 ticks / 30 seconds** | The amount added to the raid's stored omen level, capped at V |
| Trial Omen, `minecraft:trial_omen` | Trial Spawner conversion, always **level I** | Bottle strength changes duration to **18,000–90,000 ticks / 15–75 minutes**, not Trial Omen strength |
| Hero of the Village, `minecraft:hero_of_the_village` | Eligible raid winners, level equal to the raid's stored omen level, **48,000 ticks / 40 minutes** | The size of the villager trade discount; gift eligibility only checks presence |

[Registrations][registrations] · [Bottle][bottle-effect] · [Raid conversion][bad-omen] · [Raid level][raid-absorb] · [Trial duration][trial-transform] · [Hero grant][raid-victory] · [Discount][trade-discount] · [Gift eligibility][gift-target]

## Bad Omen

### Getting the effect

Finish drinking an [Ominous Bottle](../items/OminousBottle.md) to receive its level of Bad Omen. The bottle has no food-level requirement. Repeated bottles do not add their levels or durations together: the usual effect replacement/refresh rules apply, with stronger effects taking priority and longer equal-level applications refreshing the timer. [Item components][bottle-item] · [Consumption][consume] · [Grant][bottle-effect] · [Effect update][effect-update]

The bottle's acquisition owner has the full [obtaining routes](../items/OminousBottle.md#obtaining). The useful level distinction is:

- **Pillager captain with no assigned or nearby active raid:** one bottle, level **I–V**, when the bundled captain predicate and normal mob-loot gate pass. The pool has no player-kill condition and no Looting count function. This is a Pillager loot route, not a universal captain reward; the bundled Illusioner table has no reward pools. [Pillager pool][pillager-loot] · [Predicate defaults][captain-predicate] · [Captain and raid checks][captain-raid] · [Mob-loot gate][mob-loot-gate] · [Death-loot dispatch][death-loot] · [Illusioner table][illusioner-loot]
- **Normal Vault rewards:** possible bottle **I–II**. **Ominous Vault rewards:** possible bottle **III–V**. These are weighted common-table entries, each producing one bottle if selected; neither opening guarantees one. The parent tables can make multiple common rolls. Follow [Vault rewards](../blocks/Vault.md#normal-versus-ominous-rewards) for opening rules and the complete reward structure. [Normal parent][vault-normal] · [Normal common][vault-common] · [Ominous parent][vault-ominous] · [Ominous common][vault-ominous-common] · [Amplifier application][bottle-function]
- **MattMC inventory browser:** all five bottles are ordinary food/drink entries. The checked browser can insert them in Creative; this does not require the `/effect` command's operator permission. See [Inventory Browser](../mechanics/InventoryBrowser.md) for insertion behavior. [Category][browser-category] · [Variants][browser-variants] · [Browser list][browser-list] · [Client request][browser-client] · [Server handling][browser-server]

The ordinary potion registry and brewing mix list do not register a brewed potion for any of these four statuses. A bottle, an event conversion, and an operator-applied effect are distinct acquisition routes. [Potion types][potions] · [Brewing list][brewing]

### Becoming Raid Omen

On a server effect tick, a **nonspectator player outside Peaceful** with Bad Omen converts while standing in a recognized village, unless the nearby active raid is already at its maximum omen level V. Conversion removes Bad Omen, grants Raid Omen at the same level for 600 ticks, and saves the player's block position for the later attempt. Creative is not excluded by this village check. [Conversion][bad-omen] · [Nearby raid lookup][raid-lookup] · [Removal on effect return][effect-tick]

Village recognition uses occupied village points of interest and nearby sections; generated buildings alone are not enough. See [starting or avoiding a raid](../mechanics/Raid.md#starting-or-avoiding-a-raid) for settlement preparation and center selection. The `disableRaids` rule and the dimension's raid permission are checked at the later start attempt, not in this Bad Omen conversion. [Village query][village-query] · [Occupied points][village-poi] · [Village point types][village-tag] · [Start gates][raid-create]

## Raid Omen

**Finish drinking Milk before the final effect tick if you want to cancel the pending attempt.** Leaving the village does not cancel it: at remaining duration `1`, Raid Omen uses the saved position. It calls the current server level's raid manager, clears that saved position, and ends. [Final-tick behavior][raid-omen] · [Milk removal](#clearing-effects-and-cancellation)

The start attempt rejects a spectator, `disableRaids=true`, or a dimension type with `has_raids=false`. The bundled rule defaults to **false**; the Overworld permits raids and the Nether does not. If accepted, the manager creates or reuses a nearby raid and adds the player's Raid Omen level to its stored level, capped at V. A fresh raid has its own **300-tick / 15-second preparation countdown**. This is additional to the 30-second effect, and loaded terrain, village validity and spawn placement still matter. [Creation and extension][raid-create] · [Default rule][raid-rule] · [Overworld type][overworld] · [Nether type][nether] · [Level absorption][raid-absorb] · [Preparation][raid-initial] · [Active raid checks][raid-tick]

Raid level II–V enables **one bonus wave**, and the stored level also feeds qualifying raiders' equipment-enchantment checks. These are changes to the encounter, not a combat-stat bonus on the player. The [Raid guide](../mechanics/Raid.md#waves-and-difficulty) owns wave composition, difficulty, persistence and stopping conditions. Switching to Peaceful stops an ongoing raid in its tick; Milk does not. [Bonus-wave condition][raid-bonus] · [Equipment threshold][raid-enchant] · [Pillager consumer][pillager-buff] · [Vindicator consumer][vindicator-buff] · [Spawn caller][raid-buff-call] · [Peaceful check][raid-tick]

## Trial Omen

### Conversion and duration

A qualifying **non-ominous Trial Spawner** can detect a player carrying Bad Omen or Trial Omen. Under its normal detector the player must be neither Creative nor Spectator, have a block position **strictly less than 14 blocks** from the spawner, and pass the eye-to-spawner visual-ray check. This omen check always uses that visibility requirement, even when later encounter participants can join through the separate relaxed scan. Detection is checked every **20 ticks**, staggered by block position. [Default detector][trial-detector-install] · [Default range][trial-defaults] · [Player/visibility filter][trial-detector] · [Omen and participant scans][trial-detection]

If a detected player already has Trial Omen, the scan prefers that player over Bad Omen holders. Otherwise it selects one detected Bad Omen holder, removes that effect, and grants **Trial Omen I** for **18,000 ticks per Bad Omen level**. The spawner becomes ominous in the same conversion path; there is no Raid Omen-style 30-second warning. [Selection and conversion][trial-detection] · [Duration][trial-transform] · [Block change][trial-apply]

| Consumed Bad Omen level | Trial Omen I duration |
| --- | --- |
| I | 18,000 ticks / 15 minutes |
| II | 36,000 ticks / 30 minutes |
| III | 54,000 ticks / 45 minutes |
| IV | 72,000 ticks / 60 minutes |
| V | 90,000 ticks / 75 minutes |

The duration comes from the **level**, not Bad Omen's remaining time. An existing Trial Omen is neither consumed nor refreshed when it makes another eligible spawner ominous. The checked trigger only tests its presence, so command-created higher Trial Omen levels do not increase that trigger's strength. [Selection][trial-detection] · [Conversion formula][trial-transform]

### The player timer is not the spawner cooldown

During the waiting-for-players and active states, normal detection requires a configured spawnable mob, non-Peaceful difficulty, **`doMobSpawning=true`**, and **`spawnerBlocksEnabled=true`**. Both rules default to true. The separate **normal-spawner cooldown** branch can still detect an omen and switch the block to ominous before the cooldown finishes; that branch does not repeat the spawning gate before conversion. Actual active spawning still checks it. [State branches][trial-states] · [Spawning gates][trial-gates] · [Mob-spawn default][mob-rules] · [Spawner default][spawner-rule]

An **ominous spawner in cooldown does not perform the omen scan**. Its default cooldown is **36,000 ticks / 30 minutes** from encounter completion, distinct from the player's Trial Omen timer. Milk cannot reset that cooldown or turn an already-ominous spawner normal. When the block's cooldown finishes, its own state machine removes the ominous flag and resets the encounter; if the player still has Trial Omen, a later eligible detection can start another ominous encounter. [Cooldown scan exclusion][trial-detection] · [Completion and cooldown][trial-states] · [Defaults][trial-defaults] · [Block flag removal][trial-remove]

See [Trial Spawner: becoming ominous](../blocks/TrialSpawner.md#becoming-ominous) for mob replacement, hazards and rewards. A [Vault](../blocks/Vault.md) has separate normal/ominous configurations and keys; carrying an omen does not substitute for the required key. [Vault key checks][vault-key]

## Hero of the Village

### Earning the reward

The ordinary raider-death callback records a player when that player is the damage source's responsible entity and the raider belongs to the raid. Starting the raid or watching it does not by itself record a hero. When the started raid has no required waves or tracked raiders left and completes its 40-tick victory counter, it awards **48,000 ticks / 40 minutes** of Hero of the Village to recorded, nonspectator living entities that can be resolved in that server level. The effect level equals the raid's stored omen level. Being recorded does not guarantee an award while offline or in another dimension. [Hero recording][hero-record] · [UUID storage][hero-store] · [Victory and grant][raid-victory]

### Trade discount

For an eligible ordinary Villager trade, the effect reduces the **first input's base count**. At Hero levels I–V the factors are **30%, 36.25%, 42.5%, 48.75%, and 55%**. The reduction is rounded down to whole items, with a minimum reduction of one item before the final price clamp. Demand and reputation still contribute; the final first input is clamped from **one item** to that item's **stack limit**. The second input is unchanged. This is not a guaranteed percentage off the already displayed price or a restock. [Discount calculation][trade-discount] · [Final price and second input][trade-cost]

For example, a base first input of 20 with no other price adjustments becomes **14 / 13 / 12 / 11 / 9** at Hero I / II / III / IV / V. Villagers calculate the player-specific adjustment when starting the trading session and reset it when trading ends. Reopen the trade to check prices after gaining or losing the effect. See [Trading](../trading/Trading.md#prices-can-change) for offers, reputation, demand and restocking. [Session start][trade-start] · [Reset][trade-reset]

### Villager gifts

Villagers also have a gift behavior in their work, meeting and idle activities. It checks whether the **nearest visible player** has Hero of the Village; it does not search past a nearer visible nonhero for someone else. The effect's level is not used for gift selection. [Behavior installation][gift-install] · [Work][gift-work] · [Meeting][gift-meet] · [Idle][gift-idle] · [Nearest-player sensing][gift-sensor] · [Effect check][gift-target]

A gift-behavior instance starts with a **600-check delay**. Its counter decreases only on eligible start checks while a hero is visible. After a run stops, it chooses **600–6,600** eligible checks for its next delay. These are not guaranteed 30-second or five-minute deliveries: the villager's activity, sight, path and interruption matter. Once the behavior starts, it approaches within **strictly less than 5 blocks** by block position and throws after **more than 20 game ticks**. Gifts become dropped item entities, not direct inventory deposits. [Delay and approach][gift-behavior] · [Behavior lifetime][behavior-lifetime] · [World item creation][gift-throw]

The checked gift selection uses a baby table first, otherwise the profession table or the unemployed fallback. Examples are a **Poppy from a baby**, **Wheat Seeds from the unemployed fallback**, one of **Bread/Pumpkin Pie/Cookie from a Farmer**, and a **Book from a Librarian**. A Fletcher's tipped-arrow entries can roll a count of zero, so an attempted gift need not produce an item. These are separate gift rolls, not items removed from trade stock. [Profession mapping][gift-tables] · [Baby/fallback selection][gift-behavior] · [Gift loot caller][gift-loot-call] · [Baby][gift-baby] · [Unemployed][gift-unemployed] · [Farmer][gift-farmer] · [Librarian][gift-librarian] · [Fletcher][gift-fletcher]

## Clearing effects and cancellation

[Milk](../items/MilkBucket.md) removes **all four statuses**, along with other beneficial and harmful effects. Its ordinary drink takes **32 ticks / 1.6 seconds**, and clearing happens when consumption finishes. Starting to drink at the last moment is not enough. [Milk item][milk-item] · [Drink definition][milk-definition] · [Consumption completion][consume] · [Clear-all effect][milk-clear] · [Server removal][effect-clear]

- **Bad Omen:** remove it before a village or spawner converts it
- **Raid Omen:** finish removal before remaining duration `1` invokes the saved-position start attempt
- **Trial Omen:** removal prevents that player supplying this effect to later eligible spawner scans; it does not undo a block already made ominous, nor prevent another eligible player triggering one
- **Hero of the Village:** removal ends future effect-based gift eligibility and its discount on newly opened trading sessions

[Village trigger][bad-omen] · [Raid final tick][raid-omen] · [Trial trigger][trial-detection] · [Gift check][gift-target] · [Trade session][trade-start]

Operators with **command permission level 2** can use `/effect give` or `/effect clear` with the IDs above; command amplifiers start at zero and finite noninstant durations are in seconds. Directly granting Raid Omen does **not** create its saved village position: on a player without that position, the grant alone cannot start a raid. Use the ordinary Bad Omen conversion when testing the full chain. Bottle insertion through MattMC's inventory browser is a separate path. [Command permission and arguments][effect-command] · [Seconds and application][command-apply] · [Saved-position requirement][raid-omen]

## Related guides

- [Effects](Effects.md) · [Ominous Bottle](../items/OminousBottle.md) · [Milk Bucket](../items/MilkBucket.md)
- [Raid](../mechanics/Raid.md) · [Trial Spawner](../blocks/TrialSpawner.md) · [Vault](../blocks/Vault.md)
- [Villager](../mobs/Villager.md) · [Trading](../trading/Trading.md)

## Sources and verification

Source-reviewed at MattMC commit `8b9173b399a629578a7bf0168e4d3ea32b10e8a6` on **2026-10-03**. The review followed bottle components into consumption, registered effects into server effect ticks, Raid Omen into the server-ticked raid manager and victory, the live Trial Spawner block-entity ticker into detection/state changes, and villager brain/trading consumers into gifts and payment counts. The complete Pillager/Illusioner loot tables and all eight normal/ominous Vault reward tables were compared, including conditions, rolls and item functions. Bundled gift tables were also inspected. [Living-entity effect caller][living-effect-call] · [Effect dispatch][living-effect-tick] · [Raid-manager tick][raid-server-tick] · [World block-entity tick][world-block-tick] · [Chunk ticker][chunk-ticker] · [Trial block dispatch][trial-block-tick] · [Trial state dispatch][trial-server-tick] · [Villager brain tick][villager-tick]

No in-game bottle, village, raid, spawner, Milk, multiplayer, trading or gift test was run. Custom loot, dimension data, spawner configuration and server rules can change these defaults. Encounter layouts, full wave/reward tables, bottle acquisition and general villager mechanics remain owned by the linked guides.

[bottle-effect]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/OminousBottleAmplifier.java#L21-L38
[bad-omen]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/BadOmenMobEffect.java#L15-L34
[raid-omen]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/RaidOmenMobEffect.java#L15-L30
[trial-detection]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawnerStateData.java#L121-L178
[raid-victory]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/raid/Raid.java#L372-L389
[effect-constructors]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L49-L75
[effect-tick]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L214-L250
[registrations]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffects.java#L108-L118
[raid-absorb]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/raid/Raid.java#L218-L244
[trial-transform]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawnerStateData.java#L203-L210
[trade-discount]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/Villager.java#L467-L485
[gift-target]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/behavior/GiveGiftToHero.java#L118-L128
[bottle-item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L2696-L2702
[consume]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumable.java#L77-L101
[effect-update]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L123-L170
[pillager-loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/entities/pillager.json
[captain-raid]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/raid/Raider.java#L150-L160
[captain-predicate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/advancements/critereon/RaiderPredicate.java#L12-L30
[mob-loot-gate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Monster.java#L130-L133
[death-loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1474
[illusioner-loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/entities/illusioner.json
[vault-normal]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward.json
[vault-common]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward_common.json
[vault-ominous]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward_ominous.json
[vault-ominous-common]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/reward_ominous_common.json
[bottle-function]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/storage/loot/functions/SetOminousBottleAmplifierFunction.java#L40-L44
[browser-category]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1752-L1755
[browser-variants]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2255-L2261
[browser-list]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[browser-client]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-server]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L2029
[potions]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/Potions.java
[brewing]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java
[raid-lookup]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/level/ServerLevel.java#L1461-L1464
[village-query]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/level/ServerLevel.java#L1441-L1455
[village-poi]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiManager.java#L315-L327
[village-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/point_of_interest_type/village.json
[raid-create]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/raid/Raids.java#L110-L160
[raid-rule]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/GameRules.java#L133-L135
[overworld]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/dimension_type/overworld.json
[nether]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/dimension_type/the_nether.json
[raid-initial]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/raid/Raid.java#L124-L131
[raid-tick]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/raid/Raid.java#L252-L325
[raid-bonus]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/raid/Raid.java#L426-L444
[raid-enchant]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/raid/Raid.java#L766-L777
[pillager-buff]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Pillager.java#L235-L258
[vindicator-buff]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Vindicator.java#L167-L182
[raid-buff-call]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/raid/Raid.java#L550-L565
[trial-detector-install]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/TrialSpawnerBlockEntity.java#L23-L33
[trial-defaults]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawner.java#L374-L395
[trial-detector]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/trialspawner/PlayerDetector.java#L23-L51
[trial-apply]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawner.java#L99-L104
[trial-states]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawnerState.java#L55-L146
[trial-gates]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawner.java#L147-L155
[mob-rules]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/GameRules.java#L53-L56
[spawner-rule]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/GameRules.java#L241-L243
[trial-remove]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawner.java#L106-L109
[vault-key]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/vault/VaultBlockEntity.java#L246-L273
[hero-record]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/raid/Raider.java#L115-L134
[hero-store]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/raid/Raid.java#L779-L781
[trade-cost]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/trading/MerchantOffer.java#L85-L101
[trade-start]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/Villager.java#L320-L359
[trade-reset]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/Villager.java#L362-L383
[gift-install]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/Villager.java#L223-L248
[gift-work]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/behavior/VillagerGoalPackages.java#L74-L103
[gift-meet]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/behavior/VillagerGoalPackages.java#L150-L162
[gift-idle]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/behavior/VillagerGoalPackages.java#L178-L196
[gift-sensor]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/sensing/PlayerSensor.java#L27-L46
[gift-behavior]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/behavior/GiveGiftToHero.java#L40-L138
[behavior-lifetime]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/behavior/Behavior.java#L37-L79
[gift-throw]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/behavior/BehaviorUtils.java#L81-L95
[gift-tables]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/behavior/GiveGiftToHero.java#L24-L38
[gift-loot-call]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1544-L1580
[gift-baby]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/gameplay/hero_of_the_village/baby_gift.json
[gift-unemployed]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/gameplay/hero_of_the_village/unemployed_gift.json
[gift-farmer]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/gameplay/hero_of_the_village/farmer_gift.json
[gift-librarian]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/gameplay/hero_of_the_village/librarian_gift.json
[gift-fletcher]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/gameplay/hero_of_the_village/fletcher_gift.json
[milk-item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1531-L1533
[milk-definition]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L64-L73
[milk-clear]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/consume_effects/ClearAllStatusEffectsConsumeEffect.java#L20-L23
[effect-clear]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L943-L953
[effect-command]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/commands/EffectCommands.java#L33-L91
[command-apply]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/commands/EffectCommands.java#L153-L177
[living-effect-call]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L475-L486
[living-effect-tick]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L824-L844
[raid-server-tick]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/level/ServerLevel.java#L365-L368
[world-block-tick]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/Level.java#L440-L459
[chunk-ticker]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java#L716-L779
[trial-block-tick]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/TrialSpawnerBlock.java#L48-L61
[trial-server-tick]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawner.java#L267-L278
[villager-tick]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/Villager.java#L267-L272
