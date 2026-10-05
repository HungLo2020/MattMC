# Villager

Villagers can buy and sell goods once they are adults with a trading profession. Use them as a renewable source of selected supplies and [Emeralds](../items/Emerald.md), provided they can work and restock. See [Trading](../trading/Trading.md) for payments, levels, stock limits, and changing prices.

## Professions and job sites

An unemployed adult can claim an available job-site block it can reach. The block determines its profession. A nitwit cannot acquire a trading profession, and babies do not take jobs or open the trading screen.

| Profession | Job-site block |
| --- | --- |
| Armorer | Blast Furnace |
| Butcher | Smoker |
| Cartographer | Cartography Table |
| Cleric | Brewing Stand |
| Farmer | Composter |
| Fisherman | Barrel |
| Fletcher | Fletching Table |
| Leatherworker | Cauldron, including filled variants |
| Librarian | Lectern |
| Mason | Stonecutter |
| Shepherd | Loom |
| Toolsmith | Smithing Table |
| Weaponsmith | Grindstone |

Each job-site block has room for one claimant. A nearby block may already belong to another villager; proximity alone does not establish ownership. Keep a clear route to the claimed workstation so the villager can approach it and work.

## Employment and changing jobs

A level-1 villager with **zero villager experience** can return to unemployment after it loses its job site. This lets an untraded villager take a different profession when it later claims another kind of workstation. Changing profession clears the old offers.

After trading has awarded villager experience, removing the workstation does **not** make the villager forget its profession or reset its offers. It still needs an appropriate claimed workstation to replenish used trades. Do not dismantle a working trading setup expecting an experienced villager to become a new profession.

## Interacting and unlocking offers

Interact with an awake adult that has offers and is not already trading with another player. The trading menu shows that villager's selected offers, its current prices, and its progress toward the next level.

Trading unlocks further levels, up to level 5. The profession alone does not guarantee a particular offer: listings are selected from level-specific pools, and the optional trade-rebalance feature changes some pools. Consult the [trading guide](../trading/Trading.md#offers-and-trading-levels) before planning around a specific purchase.

If an offer sells out, allow the villager to work at its own job site. Work-based restocking is limited and not an immediate response to placing a block.

## Farmer crop tending

A Farmer can harvest mature **Wheat, Carrots, Potatoes and Beetroots**, then plant suitable items from its inventory on empty Farmland. Give an adult its own reachable [Composter](../blocks/Composter.md), access to the field, and planting supplies. Harvesting and planting are work activities, alongside using the workstation and walking; they are not a continuous or guaranteed production cycle. [Work activity and job requirement][farmer-brain] · [Work choices][farmer-work] · [Harvest and planting][farmer-harvest]

### Setting up the field

- **Allow daytime work:** the default adult schedule selects work from day tick 2,000 until 9,000. The villager needs a claimed job site for that activity. A Farmer outfit alone does not establish a working job-site claim. [Schedule][farmer-schedule] · [Work requirement][farmer-brain]
- **Keep the field reachable:** the Farmer notices Farmland within four blocks in each horizontal direction and two vertically from its current block position. Its harvest/plant search is smaller: the surrounding 3 × 3 × 3 positions. It must approach a selected target before acting. These are local searches, not the dimensions of a guaranteed farm design. [Farmland recognition][farmer-profession] · [Nearby field scan][farmer-sensor] · [Target selection and approach][farmer-harvest]
- **Supply the right items:** drop planting items where the Farmer can collect them. Pickup needs inventory room and an expired item pickup delay, and `mobGriefing` must be true. The same rule is checked before starting the harvest/plant routine. For the shared inventory and food rules, see [food pickup](#food-pickup-and-willingness). [Pickup admission][farmer-pickup] · [Shared pickup gate][farmer-pickup-gate] · [Crop-work gate][farmer-harvest]

### What Farmers harvest and plant

The bundled planting list contains exactly these six items. The harvesting check is narrower: it selects a mature block in the ordinary `CropBlock` family. [Planting tag][farmer-seeds] · [Harvest selection][farmer-harvest]

| Item in the Farmer's inventory | Planted crop | Farmer harvests the mature crop? |
| --- | --- | --- |
| Wheat Seeds | [Wheat](../blocks/Wheat.md) | Yes |
| Carrot | [Carrots](../blocks/RootCrops.md) | Yes |
| Potato | [Potatoes](../blocks/RootCrops.md) | Yes |
| Beetroot Seeds | [Beetroots](../blocks/RootCrops.md) | Yes |
| Torchflower Seeds | [Torchflower Crop](../blocks/Torchflower.md) | No: it becomes a separate flower block |
| Pitcher Pod | [Pitcher Crop](../blocks/PitcherPlant.md) | No: its crop uses the double-plant family |

[Wheat registration][farmer-wheat-block] · [Root-crop registrations][farmer-root-blocks] · [Carrot inheritance][farmer-carrot] · [Potato inheritance][farmer-potato] · [Beetroot registration][farmer-beetroot-block] · [Beetroot inheritance][farmer-beetroot] · [Torchflower transition][farmer-torchflower] · [Mature flower type][farmer-flower-block] · [Pitcher crop type][farmer-pitcher]

Planting requires **air directly above Farmland** and consumes **one item**. The Farmer scans its inventory in slot order and uses the first nonempty item that both belongs to the planting tag and places a block. All six bundled items meet that requirement. With mixed supplies, it can replace a harvested crop with a different one; it does not remember what grew there. For a single-crop field, keep the Farmer's planting supply to that crop. Data packs can change the tag, but adding an ordinary non-block item does not make it plantable by this routine. [Planting transaction][farmer-planting] · [Wheat item][farmer-wheat-item] · [Carrot and Potato items][farmer-root-items] · [Remaining planting items][farmer-other-items] · [Block-item construction][farmer-block-item]

The Farmer **breaks** a mature crop before replanting; it does not use MattMC's player-only [harvest-and-regrow controls](../blocks/RootCrops.md#mattmc-harvesting-controls). The planting check reads the state captured before that tick's break, so do not expect the same tick to both harvest and replant. Empty soil can remain when supplies, access or work activity interrupt the next planting opportunity. [Harvest and planting order][farmer-planting]

### Bone Meal and stalled crop work

Farmers can collect dropped **Bone Meal**, in addition to the general villager pickup list. Their [Composter work](../blocks/Composter.md#comparator-and-farmer-use) can also compost surplus Wheat Seeds and Beetroot Seeds and empty ready compost. The resulting Bone Meal is a loose item with a pickup delay; it still needs to be collected and does not go straight into the Farmer's inventory. Wheat-to-Bread conversion and food sharing are covered under [breeding and population](#breeding-and-population). [Farmer supplies][farmer-profession] · [Workstation routine][farmer-compost] · [Bone Meal release][farmer-compost-output] · [Pickup][farmer-pickup-gate]

A separate work behavior spends stored Bone Meal on a nearby **immature `CropBlock`**, including growing Torchflower Crops. It does not select Pitcher Crops, even though a player can fertilize them. It has its own timing and target checks and requires the villager to approach the crop. **This behavior does not check `mobGriefing`**, so disabling the rule is not a reliable way to stop fertilizing by a Farmer that already holds Bone Meal. [Bone Meal selection and timing][farmer-bonemeal] · [Application and consumption][farmer-bonemeal-use] · [Torchflower type][farmer-torchflower] · [Pitcher type][farmer-pitcher] · [Player fertilizing][farmer-pitcher-bonemeal]

If work stalls, check the job claim and work time first, then the route to the crop and the Farmer's planting supplies. A mature Torchflower or Pitcher Crop remaining untouched follows the limits above. For slow natural growth or soil reverting to Dirt, use the [Farmland](../blocks/Farmland.md), [Wheat](../blocks/Wheat.md) and [root-crop](../blocks/RootCrops.md) guides. These source-reviewed behaviors do not establish a tested automatic layout, harvest rate or item-collection yield.

## Breeding and population

To raise another villager, give **both adults enough food**, let them meet while awake, and leave a **reachable, unclaimed bed for the child**. A useful starting arrangement is a bed for each existing resident plus a spare, with clear routes between the villagers and beds. The actual birth check looks for an available home; it does not compare a village-wide villager count with a bed count. [Readiness][population-readiness] · [Birth and bed search][population-birth]

### Food pickup and willingness

Drop food where each villager can collect it. Interacting with a villager while holding food is not the ordinary animal-feeding action: the interaction handles trading or refusal, while dropped items enter the villager's inventory. Each villager needs **12 food points**, counted across its stored food reserve and inventory, and must be awake with no breeding cooldown. [Interaction][population-interact] · [Pickup][population-pickup] · [Readiness][population-readiness]

These are the four foods that contribute breeding points:

- **Bread:** 4 points each; 3 Bread supplies one villager's 12-point requirement
- **Carrots:** 1 point each; 12 Carrots supplies the requirement
- **Potatoes:** 1 point each; 12 Potatoes supplies the requirement
- **Beetroot:** 1 point each; 12 Beetroot supplies the requirement

Mixtures count too: 2 Bread and 4 Carrots total 12 points. These amounts are **per villager**, assuming no food already stored; one adult collecting the entire pair's supply does not make both ready. Wheat, seeds, Baked Potatoes and Beetroot Soup do not directly contribute breeding points. [Food values][population-food] · [Inventory total][population-food-total]

The dropped-item pickup path requires **`mobGriefing` to be true**, available inventory space, and an item whose pickup delay has expired. Villagers have eight inventory slots. Their pickup list also includes plantable seeds and Wheat, so seeing an item disappear into a villager is not proof that it received breeding food. Modified item tags can change what villagers collect, but the reviewed food-point map still contains only the four foods above. [Pickup gate][population-pickup-gate] · [Inventory][population-inventory] · [Pickup list][population-pickup-tag] · [Plantable items][population-seed-tag]

A Farmer can turn stored Wheat into Bread when working at its claimed Composter. That is an indirect supply route, not a reason to count Wheat as breeding food for every villager. Farmer harvesting also checks `mobGriefing`. The rule blocks dropped-item pickup and harvesting; the breeding-readiness and birth routines do not themselves check it, so switching the rule off is not a reliable way to stop villagers that already have enough food. [Farmer work selection][population-farmer-work] · [Bread-making caller][population-bread] · [Wheat conversion][population-bread-recipe] · [Harvest gate][population-harvest] · [Readiness][population-readiness]

### Sharing food

Nearby villagers can throw food to one another during social interactions. The donor needs at least **24 food points in its inventory**; it shares with a recipient below 12 inventory food points, or shares regardless of that recipient threshold when the donor is a Farmer. These checks use inventory food, not the donor's separate stored reserve. [Sharing conditions][population-sharing] · [Inventory thresholds][population-food-total]

Passing those checks does not guarantee a throw. With the normal 64-item stacks, the selected food stack must contain **more than 24 items**: a stack of 25–32 gives away the amount above 24; a stack of 33–64 gives away half, rounded down. For example, 6 Bread is worth 24 points but is too small a stack to share through this routine. Feed both intended parents directly instead of relying on a small pile to divide itself evenly. Thrown food still has to be picked up by the recipient. [Stack selection][population-sharing-stack] · [Default stack size][population-stack-default] · [Throwing an item][population-throw] · [Pickup gate][population-pickup-gate]

### Spare beds and reachable homes

All sixteen standard bed colors can register as homes. Only the **head half** supplies the home point, and each home has **one claim slot**. A bed with nobody lying in it can still be claimed by a villager that is elsewhere. Ordinary home-finding can claim available beds for existing residents, so adding one bed to a homeless group does not ensure it remains spare for a baby. [Bed registration][population-home-states] · [One claim per home][population-home-capacity] · [Claims][population-home-claims] · [Ordinary home search][population-home-acquire]

At the birth attempt, the initiating parent searches for a home with a free claim slot within **48 blocks of its block position**, measured in three dimensions. It must also obtain a path that the navigation system considers reachable. The home type passes a reach tolerance of 1 to that path search; this is not a promise that every bed within 48 blocks works, or a demand that the parent physically sleep in the bed first. The successful search reserves the claim for the child. [Birth search][population-bed-search] · [Search distance][population-poi-distance] · [Reserve an available point][population-poi-take] · [Home tolerance][population-home-capacity]

Keep the approach and space around and above the bed clear, and keep the parents close enough to meet. Treat this as practical pathfinding advice, not a verified enclosure blueprint: no exact room dimensions or universal headroom recipe were tested here. The birth search uses navigation reachability, while ordinary home acquisition also rejects beds whose sleeping-occupied flag is set. Neither a visible empty mattress nor decorative houses establish a spare claim. [Navigation][population-navigation] · [Collision checks][population-collisions] · [Ordinary bed validation][population-home-validation]

A trading profession, workstation, bell, completed trade, or recognized generated village is **not a prerequisite in this breeding path**. Unemployed villagers and nitwits can meet the same food, age and awake checks. Use [professions and job sites](#professions-and-job-sites) and [restocking](../trading/Trading.md#stock-and-restocking) for employment, [Raid](../mechanics/Raid.md#starting-or-avoiding-a-raid) for village recognition, and [Beds](../blocks/Bed.md) for player sleeping and respawn rules. [Installed breeding activity][population-idle] · [Readiness][population-readiness] · [Birth][population-birth]

### Mating, birth and growth

Breeding starts through the villagers' idle activity, alongside other choices, rather than immediately when food is collected. The partner selection looks for a visible eligible villager within **8 blocks**. The pair then approach each other and must remain eligible; at the birth moment their squared separation must be at most 5, about **2.24 blocks**. Give them room to approach instead of separating them behind an impassable divider. [Idle selection][population-idle] · [Partner choice][population-partner] · [Pair eligibility][population-pair] · [Mating sequence][population-mating]

Once the mating behavior starts, its birth attempt is scheduled **275–324 ticks later**, nominally **13.75–16.2 seconds at 20 TPS**. This is the duration of an uninterrupted attempt, not a promised wait after dropping food. Hearts may appear before the bed search succeeds. When the close pair reach the scheduled attempt, **both consume 12 food points before the search for a bed**. If no qualifying bed is found, both show angry particles and no baby is created; that failed search still spent the food. Fix the bed problem before repeatedly adding food. [Timing and food order][population-mating] · [Failed bed search][population-birth] · [Food consumption][population-readiness] · [Particles][population-particles]

On success, the child appears at the initiating parent's position and receives the reserved bed as its home. Both parents receive a **6,000-tick breeding cooldown**, normally **5 minutes**, and the newborn takes **24,000 ticking age updates**, normally **20 minutes**, to become an adult. The parents need enough food again as well as an expired cooldown before another attempt. These are tick-based durations, not guaranteed elapsed times while the villagers are unloaded or the server is paused or slow. [Birth, cooldown and child home][population-newborn] · [Age progression][population-age] · [Entity ticking][population-ticking]

### Troubleshooting breeding

- **Food stays on the ground:** check that it is one of the four breeding foods, `mobGriefing` allows pickup, and the villager has inventory room. The pickup list is broader than the breeding-food list. [Pickup checks][population-pickup] · [Rule and pickup delay][population-pickup-gate]
- **Both have food but do not start:** check that both are adults, awake, and past the previous birth's cooldown. Let them have idle time with a visible nearby partner. A hostile threat or recent injury can trigger panic, which clears the breeding target. [Readiness][population-readiness] · [Idle behavior][population-idle] · [Panic][population-panic]
- **Hearts turn into angry particles:** in this mating routine, that is the no-available-bed result. Check the spare bed's claim, distance and route, then replace the food spent on the failed attempt. [Attempt and failure][population-mating] · [Bed search][population-bed-search]
- **Breeding stops after one baby:** the baby has claimed the spare bed, both parents are cooling down, and both spent food. More births need another qualifying spare home and renewed parent readiness. [Successful birth][population-newborn] · [Readiness][population-readiness]
- **There are many houses, doors or workstations but no birth:** those do not substitute for the reachable free home used by this check. Count usable spare beds as a planning aid, and investigate individual claims and paths when the count looks sufficient. [Home search][population-bed-search] · [Available claims][population-poi-take]


## Creative use

The entity ID is `minecraft:villager`. For Creative testing and mapmaking, use the [Villager Spawn Egg](../items/VillagerSpawnEgg.md).

## Related pages

- [Hero of the Village gifts and discounts](../effects/OmenEffects.md#hero-of-the-village)

- [Raid preparation and village recognition](../mechanics/Raid.md#starting-or-avoiding-a-raid)

- [Trading](../trading/Trading.md)
- [Emerald](../items/Emerald.md)
- [Mobs](Mobs.md)

## Sources and verification

Jobs and trading were source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01, using active `src/main` code. No in-game employment or trading test was run. Breeding and population have the separate verification scope below; this is not a complete guide to natural spawning or raids.

- [Profession registration and job-site matching](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/npc/VillagerProfession.java)
- [Workstation blocks and one-claimant capacity](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiTypes.java)
- [Finding and reaching an available job site](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/ai/behavior/AcquirePoi.java) and [villager job-search rules](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/ai/behavior/VillagerGoalPackages.java#L30-L66)
- [Assigning a profession](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/ai/behavior/AssignProfessionFromJobSite.java) and [zero-experience profession reset](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/ai/behavior/ResetProfession.java)
- [Interaction, profession changes, and trading behavior](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/npc/Villager.java)
- [Workstation restocking](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/ai/behavior/WorkAtPoi.java)
- [Entity registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/EntityType.java#L1468-L1473) and [spawn-egg registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java#L1995)

Breeding and population were source-reviewed at `f5473e41dc4af8ced756db517fada27288df07a3` on 2026-10-04. The registered villager installs the idle breeding behavior and ticks its Brain; home points come from registered bed states and server block-state updates. No in-game pickup, sharing, breeding, bed-layout, cooldown or growth test was run. This verifies the reviewed code and bundled pickup tags, not a tested automatic breeder design. [Entity registration][population-register] · [Activity installation and caller][population-brain] · [Brain tick][population-dispatch] · [Bed-point updates][population-home-update]

Farmer crop tending was source-reviewed at `5218ac875eda9f2c4151ff1a97795f20f5f356cf` on 2026-10-05. The active villager installs and ticks the work behaviors and secondary-field sensor; the reviewed planting tag resolves to six registered block items. No in-game crop tending, mixed-inventory planting, Bone Meal, workstation, layout or output test was run. Existing employment, trading and population verification scopes above are unchanged. [Entity registration][farmer-entity] · [Server AI caller][farmer-ai-caller] · [Villager Brain tick][farmer-ai-tick] · [Behavior and sensor dispatch][farmer-dispatch] · [Sensor registration][farmer-sensor-type]

[population-readiness]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/npc/Villager.java#L652-L694
[population-birth]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/ai/behavior/VillagerMakeLove.java#L62-L117
[population-interact]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/npc/Villager.java#L320-L346
[population-pickup]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/npc/Villager.java#L789-L799
[population-food]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/npc/Villager.java#L94-L99
[population-food-total]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/npc/Villager.java#L801-L811
[population-pickup-gate]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/Mob.java#L439-L455
[population-inventory]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L40-L50
[population-pickup-tag]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/tags/item/villager_picks_up.json
[population-seed-tag]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/tags/item/villager_plantable_seeds.json
[population-farmer-work]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/ai/behavior/VillagerGoalPackages.java#L74-L98
[population-bread]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/ai/behavior/WorkAtComposter.java#L22-L32
[population-bread-recipe]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/ai/behavior/WorkAtComposter.java#L77-L92
[population-harvest]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/ai/behavior/HarvestFarmland.java#L49-L71
[population-sharing]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/ai/behavior/TradeWithVillager.java#L30-L60
[population-sharing-stack]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/ai/behavior/TradeWithVillager.java#L74-L110
[population-throw]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/ai/behavior/BehaviorUtils.java#L81-L94
[population-home-states]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiTypes.java#L45-L66
[population-home-capacity]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiTypes.java#L120-L135
[population-home-claims]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiRecord.java#L25-L64
[population-home-acquire]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/ai/behavior/AcquirePoi.java#L59-L101
[population-bed-search]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/ai/behavior/VillagerMakeLove.java#L82-L97
[population-poi-distance]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiManager.java#L212-L222
[population-poi-take]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiManager.java#L278-L285
[population-navigation]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/ai/navigation/GroundPathNavigation.java#L29-L56
[population-collisions]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/pathfinder/WalkNodeEvaluator.java#L286-L307
[population-home-validation]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/ai/behavior/VillagerGoalPackages.java#L60-L71
[population-idle]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/ai/behavior/VillagerGoalPackages.java#L178-L219
[population-partner]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/ai/behavior/InteractWith.java#L19-L44
[population-pair]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/ai/behavior/BehaviorUtils.java#L41-L50
[population-mating]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/ai/behavior/VillagerMakeLove.java#L30-L76
[population-particles]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/npc/Villager.java#L715-L726
[population-newborn]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/ai/behavior/VillagerMakeLove.java#L100-L117
[population-age]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/AgeableMob.java#L129-L164
[population-ticking]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/server/level/ServerLevel.java#L396-L413
[population-stack-default]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[population-panic]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/ai/behavior/VillagerPanicTrigger.java#L16-L32
[population-register]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/EntityType.java#L1468-L1470
[population-brain]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/npc/Villager.java#L223-L271
[population-dispatch]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/ai/Brain.java#L401-L405
[population-home-update]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/server/level/ServerLevel.java#L1418-L1434

[farmer-brain]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/npc/Villager.java#L223-L249
[farmer-work]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/ai/behavior/VillagerGoalPackages.java#L74-L102
[farmer-harvest]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/ai/behavior/HarvestFarmland.java#L36-L112
[farmer-schedule]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/schedule/Schedule.java#L24-L30
[farmer-profession]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/npc/VillagerProfession.java#L107-L114
[farmer-sensor]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/ai/sensing/SecondaryPoiSensor.java#L16-L45
[farmer-pickup]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/npc/Villager.java#L789-L816
[farmer-pickup-gate]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/Mob.java#L439-L455
[farmer-seeds]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/data/minecraft/tags/item/villager_plantable_seeds.json#L1-L10
[farmer-wheat-block]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/Blocks.java#L1270-L1284
[farmer-root-blocks]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/Blocks.java#L2714-L2723
[farmer-carrot]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/CarrotBlock.java#L13-L28
[farmer-potato]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/PotatoBlock.java#L13-L28
[farmer-beetroot-block]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/Blocks.java#L4276-L4280
[farmer-beetroot]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/BeetrootBlock.java#L19-L46
[farmer-torchflower]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/TorchflowerCropBlock.java#L19-L63
[farmer-flower-block]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/Blocks.java#L916-L930
[farmer-pitcher]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/PitcherCropBlock.java#L32-L50
[farmer-pitcher-bonemeal]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/PitcherCropBlock.java#L196-L213
[farmer-planting]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/ai/behavior/HarvestFarmland.java#L102-L153
[farmer-wheat-item]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/item/Items.java#L1354-L1356
[farmer-root-items]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/item/Items.java#L2056-L2059
[farmer-other-items]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/item/Items.java#L2236-L2239
[farmer-block-item]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/item/Items.java#L2704-L2706
[farmer-compost]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/ai/behavior/WorkAtComposter.java#L19-L71
[farmer-compost-output]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L284-L295
[farmer-bonemeal]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/ai/behavior/UseBonemeal.java#L27-L118
[farmer-bonemeal-use]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L78
[farmer-entity]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/EntityType.java#L1468-L1470
[farmer-ai-caller]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/Mob.java#L634-L672
[farmer-ai-tick]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/npc/Villager.java#L267-L272
[farmer-dispatch]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/ai/Brain.java#L401-L411
[farmer-sensor-type]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/ai/sensing/SensorType.java#L26-L30
