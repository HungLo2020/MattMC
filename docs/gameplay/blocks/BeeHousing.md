# Bee housing

**Bee Nests and Beehives share the same colony behavior:** each holds up to **three Bees**, stores honey from nectar deliveries, and can be harvested at honey level **5**. Bee Nests are the tree-generated form; Beehives are the craftable form. Their ordinary mining drops differ, so use Silk Touch when moving an occupied home. [Shared block registration][blocks] · [Capacity][capacity] · [Harvest][harvest] · [Nest loot][nest-loot] · [Hive loot][hive-loot]

## Getting started

Craft **one [Beehive](../items/Beehive.md)** with six planks and three [Honeycomb](../items/Honeycomb.md): a row of planks, a row of Honeycomb, and a second row of planks. The recipe accepts the planks tag, rather than requiring one wood species. A newly crafted hive is empty; placing housing does not generate a colony. Its item starts with no stored Bees and honey level zero. [Recipe][recipe] · [Item defaults][items]

For a first colony, find a tree-generated [Bee Nest](../items/BeeNest.md), or grow a suitable sapling near flowers. The tree decorator creates occupied nests with two or three Bees. See [Bee](../mobs/Bee.md#finding-bees) for the verified natural and sapling routes. [Occupied-nest generation][generation]

Place the home near safe flowers and leave its **front face unobstructed**. A newly placed block faces toward the player who places it. Ordinary Bee exits require the block immediately in front to have an empty collision shape; even a partial obstruction can prevent release. [Placement direction][facing] · [Exit collision check][exit]

## Bees choosing and entering a home

Both housing blocks are registered as Bee-home points of interest. A homeless Bee wanting shelter searches within **20 blocks**, filters out full homes, and prefers nearby available ones. Three is the normal occupant limit; adding more flowers does not increase it. Provide additional housing as the colony grows. [Home registration][poi] · [Bee-home tag][poi-tag] · [Home search][search] · [Capacity][capacity]

Bees enter to deliver nectar, shelter during the applicable night/rain condition, or rest after an extended nectar search. They need to reach the home and pass the entry checks. A Bee that is targeting an enemy, has stung, is still pollinating, or is avoiding nearby fire does not enter through that ordinary path. A full home can cause it to drop that home target and look elsewhere. [Entry motivation][motivation] · [Entry and full-home handling][enter]

Inside the housing, Bees are saved as occupants rather than remaining visible flying entities. Occupant data is serialized with the block entity, including the time already spent inside. Bees entering also leave vehicles and drop their leashes. [Occupant storage][store] · [Save/load][save] · [Occupant data][occupant]

## Nectar, honey, and exits

A Bee carrying nectar gets a stored minimum stay of **2,400 ticks**; a Bee without nectar gets **600 ticks**. At normal tick speed those thresholds are about two minutes and thirty seconds. The server attempts release after the threshold, and weather or a blocked entrance can delay it further. These are source timings, not fixed farm-output intervals. [Minimum stays][occupant] · [Release tick][tick] · [Exit conditions][exit]

**Honey increases when a nectar-carrying Bee successfully goes through the normal delivery-release path.** That path removes its nectar and raises the housing's honey level by one, with a 1% roll to raise it by two, capped at five. Simply filling the hive with Bees does not fill its honey. Blocking the exit also blocks this ordinary delivery path. [Honey delivery][delivery]

In an Overworld-like skylit dimension, ordinary release is blocked while it is dark outside or raining. The shared condition explicitly excludes the End and dimensions without skylight. Emergency release bypasses both this weather/night check and the normal front-block collision check. Do not treat a blocked entrance as protection against breaking or disturbing a hive. [Night/rain condition][weather] · [Normal and emergency exits][exit]

A comparator reads the current honey level, **0 through 5**. It does not count the Bees inside. A level-five home also produces honey-drip visuals under the relevant particle conditions. [Comparator output][comparator] · [Full-hive particles][particles]

## Harvesting

At **honey level 5**, use the housing without holding Sneak/Crouch:

| Held item | Harvest | Housing afterward |
| --- | --- | --- |
| [Shears](../items/Shears.md) | Three Honeycomb, normally with one point of Shears wear | Honey level 0 |
| [Glass Bottle](../items/GlassBottle.md) | One [Honey Bottle](../items/HoneyBottle.md), consuming one empty bottle | Honey level 0 |

Honeycomb drops into the world. A Honey Bottle replaces an emptied held bottle stack, otherwise goes into the player's inventory or drops if it cannot fit. The harvest is a use interaction; breaking the block follows different loot rules. Sneak/Crouch with a held item skips the block's normal use handler. [Harvest interaction][harvest] · [Honeycomb count][harvest-loot] · [Honey reset][reset] · [Block-use routing][use-routing]

For **manual harvesting**, provide recognized campfire smoke first. If the hive is smoky, harvesting resets honey without an emergency release. Without smoke, an occupied hive can anger nearby Bees and releases its occupants in emergency mode. [Smoke-dependent harvest][harvest]

The smoke check looks vertically below the housing, not around the apiary. Both lit [Campfires](../items/Campfire.md) and lit [Soul Campfires](../items/SoulCampfire.md) qualify. In a clear column it checks up to five blocks below. An intervening collision shape stops the normal search, with an additional check for a lit campfire immediately under that obstruction. A fire merely nearby or visible smoke particles are not sufficient evidence of protection. [Smoke algorithm][smoke] · [Campfire tag][campfires]

Protect Bees from touching the campfire itself: lit campfires still inflict contact damage. Also avoid ordinary fire beside the housing, which triggers emergency release and discourages entry. Smoke is useful for harvesting, but it does not make careless mining harmless. [Campfire contact][campfire-damage] · [Nearby fire][fire] · [Mining response][mining]

## Dispenser collection

A [Dispenser](../items/Dispenser.md) pointing directly at full housing can use **Shears** for Honeycomb or **Glass Bottles** for Honey Bottles. These are active registered behaviors. Shears normally take one point of wear; Honeycomb drops at the housing. The bottle behavior consumes an empty bottle and handles the filled bottle as a remainder. [Shears registration][shears-reg] · [Dispenser Shears][shears-dispenser] · [Dispenser bottles][bottle-dispenser]

These dispenser paths reset honey and request a **normal** Bee release with no player target. They do not run the manual harvest's nearby-player anger routine and do not require smoke for that interaction. Normal release can still be held up by night, rain, or the entrance. That is different from a player's unsmoked emergency harvest; it does not guarantee safety from other ways of angering Bees. [Shears release][shears-dispenser] · [Bottle release][bottle-dispenser] · [Release and targeting][release-target] · [Exit conditions][exit]

## Moving a colony with Silk Touch

Wait until the Bees you intend to move are inside, then mine the home with a **Silk Touch** tool and place the dropped item at the destination. Only stored occupants travel in the item; Bees still flying outside are not collected automatically. Night or rain can help gather a colony into its homes, subject to the ordinary entry conditions. [Stored occupants][save] · [Entry motivation][motivation] · [Nest loot][nest-loot] · [Hive loot][hive-loot]

Both Silk Touch loot paths copy the **stored Bees and honey level** into the item. The placement path reapplies the block-state and Bee components, and released Bees receive the new hive position. Keep the new entrance clear and provide flowers nearby. [Copied nest data][nest-loot] · [Copied hive data][hive-loot] · [Placement restoration][place] · [Bee component restoration][components] · [New hive position][new-home]

The mining differences are important:

- **Bee Nest without Silk Touch:** the bundled block-loot table provides no nest item
- **Beehive without Silk Touch:** the table drops an ordinary empty Beehive, without copying its stored Bees or honey
- **Either occupied home without Silk Touch:** the mining callback requests emergency release and angers nearby Bees

Silk Touch is the bundled enchantment in the tag that suppresses that mining-release routine. Campfire smoke is not a substitute: although it affects how released occupants respond, the ordinary mining callback still calls the separate nearby-Bee anger routine. [Mining callback][mining] · [Silk Touch tag][silk-tag] · [Released-occupant response][release-target] · [Loot differences][hive-loot]

## Fully worn Shears

MattMC retains broken tool stacks, and hive harvesting has a source-level exception to the usual broken-item expectations. The block's harvest handler checks **Shears identity and honey level**, but does not check whether the Shears are broken. Server block interaction calls that handler before the later item-stack block-use guard. Dispenser hive shearing likewise lacks a broken-Shears check. [Harvest handler][harvest] · [Server routing][use-routing] · [Item-stack guard][broken-guard] · [Dispenser handler][shears-dispenser]

The durability handler ignores further wear once a stack is broken. Therefore the source leaves these honeycomb-harvest routes reachable with fully worn Shears. This is an identified inconsistency, not an in-game-tested promise of unlimited tool use. Carry serviceable Shears for predictable equipment behavior. [Retained broken stacks][broken-stack]

## Related pages

- [Bee](../mobs/Bee.md)
- [Beehive](../items/Beehive.md)
- [Bee Nest](../items/BeeNest.md)
- [Honeycomb](../items/Honeycomb.md): crafting and waxing uses
- [Honey Bottle](../items/HoneyBottle.md): drinking and crafting
- [Blocks](Blocks.md)

## Verification scope

Source-reviewed at MattMC commit `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. Shared block/BlockEntity registration, the server ticker, home lookup, entry/release, occupant serialization and item components, honey progression, manual and dispenser harvest callbacks, smoke, Silk Touch, loot, and broken-Shears routing were inspected. No in-game colony, harvest, smoke, moving-hive, dispenser, or broken-tool test was run.

[blocks]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5748-L5757
[capacity]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/BeehiveBlockEntity.java#L115-L121
[harvest]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/BeehiveBlock.java#L143-L189
[nest-loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/bee_nest.json
[hive-loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/beehive.json
[recipe]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/beehive.json
[items]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L2463-L2474
[generation]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/levelgen/feature/treedecorators/BeehiveDecorator.java#L39-L67
[facing]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/BeehiveBlock.java#L254-L257
[exit]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/BeehiveBlockEntity.java#L189-L208
[poi]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiTypes.java#L136-L137
[poi-tag]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/point_of_interest_type/bee_home.json
[search]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Bee.java#L1027-L1063
[motivation]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Bee.java#L334-L345
[enter]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Bee.java#L750-L778
[store]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/BeehiveBlockEntity.java#L163-L182
[save]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/BeehiveBlockEntity.java#L287-L300
[occupant]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/BeehiveBlockEntity.java#L359-L395
[tick]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/BeehiveBlockEntity.java#L335-L345
[delivery]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/BeehiveBlockEntity.java#L209-L244
[weather]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Bee.java#L343-L345
[comparator]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/BeehiveBlock.java#L79-L87
[particles]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/BeehiveBlock.java#L209-L235
[harvest-loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/harvest/beehive.json
[reset]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/BeehiveBlock.java#L196-L207
[use-routing]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L358-L388
[smoke]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/CampfireBlock.java#L261-L281
[campfires]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/campfires.json
[campfire-damage]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/CampfireBlock.java#L108-L117
[fire]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/BeehiveBlockEntity.java#L92-L112
[mining]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/BeehiveBlock.java#L89-L127
[shears-reg]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L378
[shears-dispenser]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/core/dispenser/ShearsDispenseItemBehavior.java#L20-L51
[bottle-dispenser]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L325-L355
[release-target]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/BeehiveBlockEntity.java#L123-L147
[place]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/BlockItem.java#L64-L102
[components]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/BeehiveBlockEntity.java#L302-L314
[new-home]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/BeehiveBlockEntity.java#L397-L423
[silk-tag]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/enchantment/prevents_bee_spawns_when_mining.json
[broken-guard]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L370
[broken-stack]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/ItemStack.java#L449-L485
