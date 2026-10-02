# Placed animal eggs

Use this guide to hatch **Caiman, Platypus, Terrapin, Turtle, and Sniffer eggs**, choose suitable ground, and avoid losing a cluster. All five are registered blocks, but their acquisition, timing, and protection rules differ. Terrapin Egg has no registered inventory item in this snapshot. [Block registration][blocks] · [Egg items][items]

## Placement and hatch stages

Caiman, Platypus, and Turtle egg items place a one-egg cluster. Using the matching item on a cluster can increase it to **four eggs**; the cluster shares one `hatch` state, so adding an egg retains the existing cracking stage. Turtle's stacking check excludes secondary use (normally sneaking). Terrapin's block supports the same one-to-four `eggs` state, but lacks an item for normal placement or stacking. Sniffer Egg occupies a block by itself and has no cluster count. [Caiman placement][caiman-growth] · [Platypus placement][platypus-cluster] · [Turtle placement][turtle-growth] · [Terrapin states][terrapin-growth] · [Sniffer state][sniffer-tick]

A fresh egg starts at `hatch=0`: two advances reach stages **1** and **2**, then the next eligible tick hatches it and removes the block. The four cluster species use **random ticks**, not a fixed countdown; they need an area receiving random ticks. Sniffer uses separately scheduled ticks. Ground checks below control hatching, not a special placement-support requirement. [Caiman default][caiman-default] · [Turtle default][turtle-default] · [Platypus default][platypus-habitat] · [Terrapin default][terrapin-habitat] · [Sniffer default][sniffer-tick] · [Registration][blocks] · [Base survival check][base-survival] · [Caiman][caiman-hatch] · [Platypus][platypus-hatch] · [Terrapin][terrapin-hatch] · [Turtle][turtle-hatch] · [Sniffer][sniffer-hatch]

## Caiman eggs

**ID:** `minecraft:caiman_egg` · [Item](../items/CaimanEgg.md) · [Caiman care and commands](../mobs/Caiman.md)

The ordinary-category [inventory item browser](../mechanics/InventoryBrowser.md) can request this egg in Survival as well as Creative. The breeding route is incomplete with bundled data: Caimans accept `minecraft:caiman_breedables`, but that tag has no bundled definition. Their registered mating goal marks one parent as carrying an egg; the registered laying goal would place **exactly three eggs** above suitable ground with empty space above it. This conditional route does not establish an ordinary Survival breeding food. [Creative][creative] · [Food check][caiman-food] · [Tag declaration][caiman-tags] · [Registered goals][caiman-goals] · [Mating][caiman-mate] · [Laying][caiman-lay]

Place eggs above **Sand, Red Sand, or Suspicious Sand**. These are the bundled sand-tag entries. The alternative `minecraft:crocodile_spawns` ground tag has no bundled definition. On unsuitable ground, growth pauses. Each random tick on suitable ground advances hatching when the time-of-day value is strictly between **0.65 and 0.8**; outside that window it has a **1-in-15** chance. These values are the game's `getTimeOfDay(1.0)` result, not a fraction to multiply directly by 24,000 world ticks. [Habitat][caiman-habitat] · [Sand tag][sand] · [Growth gate][caiman-growth] · [Hatching][caiman-hatch]

A cluster of one to four eggs produces that many baby **Caimans (`minecraft:caiman`)**, each starting at age −24,000 ticks. The nearest non-spectator player within **20 blocks** becomes each hatchling's owner, and owned hatchlings are ordered to sit. If no eligible player is nearby, this path leaves them untamed. Ownership depends on who is nearest at hatching, not who placed the egg. [Hatching and ownership][caiman-hatch] · [Registry alias][caiman-alias] · [Active entity][caiman-entity]

Keep players off the cluster: the active footstep callback has a **1-in-100** chance to remove one egg, with no sneaking or `mobGriefing` exemption in this code. Only players pass its trample filter. Successful trampling assigns the player as a target to living Caimans in the egg block's box expanded by 25 blocks on each axis, except tame Caimans owned by that player. Mining also removes one egg at a time, but no bundled Caiman Egg block-loot table was found, so **Silk Touch recovery is not established**. [Footsteps and retaliation][caiman-step] · [Trample filter][caiman-trampler] · [Mining][caiman-growth]

## Platypus eggs

**ID:** `minecraft:platypus_egg` · [Item](../items/PlatypusEgg.md) · [Platypus care and breeding limits](../mobs/Platypus.md)

The ordinary-category [inventory item browser](../mechanics/InventoryBrowser.md) can request this egg in Survival as well as Creative. A reliable breeding-to-egg route is not established: the active Platypus registers both a custom mating goal and an ordinary breeding goal; the custom laying goal still has its block-placement line commented out. Feeding fish is not a verified way to obtain this egg. [Creative][creative] · [Goal registration][platypus-goals] · [Custom mating][platypus-mate] · [Disabled laying][platypus-lay]

Use ground in either the **sand or dirt block tags**. Besides the three sand-tag blocks, the dirt tag includes Dirt, Grass Block, Podzol, Coarse Dirt, Mycelium, Rooted Dirt, Moss Block, Pale Moss Block, Mud, and Muddy Mangrove Roots. **Unsuitable ground destroys the entire cluster without an item drop on its next random tick.** On suitable ground, every random tick advances the stage; there is no additional day/night or random-chance gate. The third tick from a fresh state produces one baby **Platypus (`minecraft:platypus`) per egg**, at age −24,000 ticks. Hatching does not assign an owner. [Habitat][platypus-habitat] · [Sand][sand] · [Dirt][dirt] · [Hatching][platypus-hatch] · [Active entity][platypus-entity]

Keep both players and other entities off the eggs. The footstep callback gives any non-Platypus entity a **1-in-100** trample check; it does not consult sneaking or `mobGriefing`. A successful check removes one egg without a drop. Mining likewise removes one at a time, and no bundled Platypus Egg block-loot table was found to establish ordinary or Silk Touch recovery. [Trampling][platypus-step] · [Mining][platypus-cluster]

## Terrapin eggs

**Block ID:** `minecraft:terrapin_egg` · [Terrapin](../mobs/Terrapin.md)

**This block has no registered inventory item or inventory-browser entry.** For command-enabled testing, `/setblock ~ ~ ~ minecraft:terrapin_egg` places its default one-egg state. Do not expect `/give` or the Turtle Egg item to supply it. Terrapins accept the fish tag, and their registered mating goal sets an egg-carrying flag, but their separate laying goal is **not registered**. That unused goal's three-egg placement code is not a working breeding caller chain. [Block registration][blocks] · [Registered egg items][items] · [Creative entries][creative] · [Food][terrapin-food] · [Fish tag][fish] · [Active goals][terrapin-goals] · [Mating][terrapin-mate] · [Uninstalled laying goal][terrapin-lay]

For an already placed block, use **Sand, Red Sand, or Suspicious Sand** below it. Unsuitable ground pauses growth. Random ticks advance hatching whenever the game's time-of-day value is strictly between **0.65 and 0.69**, or with a **1-in-15** chance outside that window. Hatching creates one baby **Terrapin (`minecraft:terrapin`) per stored egg**, each at age −24,000 ticks and marked as coming from a bucket; it does not create a Turtle or assign an owner. [Habitat][terrapin-habitat] · [Sand][sand] · [Growth gate][terrapin-growth] · [Hatch result][terrapin-hatch] · [Active entity][terrapin-entity]

Players can trample these eggs with the **1-in-100** footstep check. Other entities fail its trample filter; sneaking and `mobGriefing` are not checked. Mining or a successful trample removes one egg. The custom drop method asks for Silk Touch and an egg block entity, but then constructs an item from the unmapped Terrapin Egg block. That resolves to **Air, an empty stack**, so this code does not provide a collectible Terrapin Egg. Do not mine a valuable cluster expecting to move it. [Footsteps][terrapin-step] · [Mining][terrapin-growth] · [Drop method][terrapin-loot] · [Block-to-item lookup][block-as-item] · [Air fallback][missing-item] · [Stack construction][stack-construction] · [Empty-stack rule][empty-stack]

Parent-pattern inheritance is not established: the hatch path removes the block before trying to retrieve its egg block entity, and normal block replacement removes that block entity. Do not rely on the parent-color routine alone as proof that breeding or command-supplied parent data will reach hatchlings. [Hatch order][terrapin-hatch] · [Block-entity removal][state-dispatch] · [Parent-trait routine][terrapin-parents]

## Turtle eggs

**ID:** `minecraft:turtle_egg` · [Item](../items/TurtleEgg.md) · [Turtle](../mobs/Turtle.md)

Feed ready adult Turtles **Seagrass** to use their active mating route. One parent carries the eggs and returns near its home position. The registered laying goal needs the parent within **9 blocks** of home, out of water at its target, and a sand-tag block with air above it; after digging, it places **one to four eggs**. The ordinary-category [inventory item browser](../mechanics/InventoryBrowser.md) also supplies the item in Survival or Creative. [Food][turtle-food] · [Food check][turtle-food-check] · [Registered goals][turtle-goals] · [Mating][turtle-mate] · [Laying][turtle-lay] · [Creative][creative]

Eggs hatch on **Sand, Red Sand, or Suspicious Sand**; unsuitable ground pauses growth. Random ticks advance a stage when the time-of-day value is strictly between **0.65 and 0.69**, or with a **1-in-500** chance outside that window. A cluster produces one baby **Turtle (`minecraft:turtle`) per egg**, each at age −24,000 ticks, with its home position set to the hatch location. [Sand][sand] · [Growth gate][turtle-growth] · [Hatching and home][turtle-hatch] · [Active entity][turtle-entity]

Use a **Silk Touch tool** to collect eggs. Each successful Survival mining operation removes one egg from the cluster and the loot table supplies **one Turtle Egg** when Silk Touch is present; ordinary mining removes an egg without that drop. The dropped item carries no hatch progress, so a new placement restarts hatching. [Mining and remaining cluster][turtle-growth] · [Count reduction][turtle-step] · [Silk Touch loot][turtle-loot]

Sneaking avoids the Turtle Egg's **1-in-100 footstep** break check, but landing has a separate **1-in-3** check and is not protected by sneaking. Turtles, Bats, and non-living entities cannot trample it; players can, and other living entities can when `mobGriefing` is enabled. Zombies are excluded from the landing check, yet have a separate goal that deliberately destroys Turtle Eggs. That goal requires `mobGriefing`, looks for an egg with two air blocks above it, and can remove the **whole cluster** without dropping eggs. Fence the area and prevent mobs reaching the nest. [Footsteps and landing][turtle-step] · [Eligible tramplers][turtle-trampler] · [Sneaking check][sneaking] · [Zombie goal registration][zombie-goals] · [Turtle-only target][zombie-egg] · [Goal permission][remove-goal] · [Target space][remove-target] · [Whole-block removal][remove-egg]

## Sniffer eggs

**ID:** `minecraft:sniffer_egg` · [Item](../items/SnifferEgg.md) · [Sniffer](../mobs/Sniffer.md)

Brush **Suspicious Sand in warm ocean ruins** for a chance at an egg. The warm-ruin structure is in the ocean-ruin structure set; its active processor supplies the warm archaeology loot table, which contains Sniffer Egg. Ready adult Sniffers can also breed with **Torchflower Seeds**: the active breeding behavior calls the Sniffer's custom method, which drops **one egg item**, rather than placing a block or immediately spawning a baby. The ordinary-category [inventory item browser](../mechanics/InventoryBrowser.md) can supply the egg in Survival or Creative too. [Warm structure][warm-ruin] · [Structure set][ruin-set] · [Suspicious Sand processor][ruin-processor] · [Processor wiring][ruin-settings] · [Archaeology loot][ruin-loot] · [Brushing][brushing] · [Recovered loot][brush-drop] · [Food][sniffer-food] · [Breeding behavior][sniffer-brain] · [Caller][breed-caller] · [One-egg result][sniffer-mate] · [Creative][creative]

Place the egg on **Moss Block** to speed it up; the bundled `minecraft:sniffer_egg_hatch_boost` tag contains only that block, not Moss Carpet or Pale Moss Block. Other ground still works. Each of three scheduled stages waits **4,000–4,299 ticks** on boosting ground or **8,000–8,299 ticks** otherwise. With the support unchanged and the area continuously ticking, that totals **12,000–12,897 ticks** (about 10–10¾ minutes at 20 ticks/second) on Moss Block, or **24,000–24,897 ticks** (about 20–20¾ minutes) elsewhere. It does not use the cluster eggs' random-tick or time-of-day gates. Changing the support does not shorten a stage already scheduled; the next stage checks the ground again. [Schedule and hatching][sniffer-hatch] · [State-change scheduling caller][state-dispatch] · [Boost check][sniffer-boost-check] · [Boost tag][sniffer-boost]

Hatching creates **one baby Sniffer (`minecraft:sniffer`)**, starting at age −48,000 ticks. Ordinary mining returns one egg without requiring Silk Touch; replacing it starts a new hatch sequence because the loot table does not preserve its hatch state. The Sniffer Egg class has no egg-trampling callback, and the Zombie egg-removal goal targets Turtle Eggs specifically. Its loot table still applies an explosion-survival condition, so explosions are not a safe collection method. [Hatch result][sniffer-hatch] · [Baby age][sniffer-age] · [Active entity][sniffer-entity] · [Block loot][sniffer-loot] · [Sniffer block implementation][sniffer-block] · [Zombie target][zombie-egg]

## Landing differences and verification

The Caiman, Platypus, and Terrapin classes contain a `float`-parameter landing method with a 1-in-3 check, but the current block callback uses `double`. Those methods do not override normal landing dispatch. Their active footstep checks still apply; do not borrow Turtle's landing or sneaking behavior for these eggs. [Caiman method][caiman-step] · [Platypus method][platypus-step] · [Terrapin method][terrapin-step] · [Current landing callback][landing] · [Entity dispatch][fall-dispatch]

Source-reviewed at MattMC commit `099b1184d1a7115915d880b436c8f58c1ed5a422` on 2026-10-02. Active registration, spawn factories, goal callers, item mappings, bundled tags, recipes, and loot were checked. No bundled recipes for these five egg IDs, Caiman breeding/crocodile-ground tag definitions, or Caiman/Platypus/Terrapin block-loot JSON files were found. Terrapin's custom drop method was checked separately. Data packs can change tags and loot. No in-game hatching, breeding, collection, trampling, or parent-trait test was run; timing is derived from source, not a measured wall-clock guarantee.

## Related pages

- [Placed dinosaur eggs](DinosaurEggs.md)
- [Subterranodon Egg](SubterranodonEgg.md)
- [Caiman Egg item](../items/CaimanEgg.md)
- [Platypus Egg item](../items/PlatypusEgg.md)
- [Turtle Egg item](../items/TurtleEgg.md)
- [Sniffer Egg item](../items/SnifferEgg.md)
- [Blocks](Blocks.md)

[blocks]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/Blocks.java#L4752-L4799
[items]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/Items.java#L872-L875
[caiman-growth]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/alexsmobs/block/BlockCaimanEgg.java#L120-L148
[platypus-cluster]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/alexsmobs/block/BlockPlatypusEgg.java#L127-L148
[turtle-growth]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/TurtleEggBlock.java#L132-L155
[terrapin-growth]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/alexsmobs/block/BlockTerrapinEgg.java#L147-L169
[sniffer-tick]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/SnifferEggBlock.java#L41-L49
[caiman-default]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/alexsmobs/block/BlockCaimanEgg.java#L14-L22
[turtle-default]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/TurtleEggBlock.java#L49-L52
[platypus-habitat]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/alexsmobs/block/BlockPlatypusEgg.java#L43-L54
[terrapin-habitat]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/alexsmobs/block/BlockTerrapinEgg.java#L57-L72
[base-survival]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L311
[caiman-hatch]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/alexsmobs/block/BlockCaimanEgg.java#L81-L118
[platypus-hatch]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/alexsmobs/block/BlockPlatypusEgg.java#L98-L125
[terrapin-hatch]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/alexsmobs/block/BlockTerrapinEgg.java#L111-L138
[turtle-hatch]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/TurtleEggBlock.java#L90-L123
[sniffer-hatch]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/SnifferEggBlock.java#L60-L93
[creative]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L949-L952
[caiman-food]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/alexsmobs/entity/EntityCaiman.java#L149-L151
[caiman-tags]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/alexsmobs/misc/AMTagRegistry.java#L95-L100
[caiman-goals]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/alexsmobs/entity/EntityCaiman.java#L89-L99
[caiman-mate]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/alexsmobs/entity/EntityCaiman.java#L435-L467
[caiman-lay]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/alexsmobs/entity/EntityCaiman.java#L470-L513
[caiman-habitat]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/alexsmobs/block/BlockReptileEgg.java#L54-L60
[sand]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/tags/block/sand.json#L1-L7
[caiman-alias]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/alexsmobs/entity/AMEntityRegistry.java#L30-L37
[caiman-entity]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/EntityType.java#L1161-L1163
[caiman-step]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/alexsmobs/block/BlockCaimanEgg.java#L39-L79
[caiman-trampler]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/alexsmobs/block/BlockCaimanEgg.java#L158-L168
[platypus-goals]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/alexsmobs/entity/EntityPlatypus.java#L182-L210
[platypus-mate]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/alexsmobs/entity/EntityPlatypus.java#L507-L542
[platypus-lay]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/alexsmobs/entity/EntityPlatypus.java#L569-L586
[dirt]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/tags/block/dirt.json#L1-L14
[platypus-entity]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/EntityType.java#L1059-L1065
[platypus-step]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/alexsmobs/block/BlockPlatypusEgg.java#L56-L100
[terrapin-food]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/alexsmobs/entity/EntityTerrapin.java#L290-L292
[fish]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/tags/item/fishes.json#L1-L10
[terrapin-goals]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/alexsmobs/entity/EntityTerrapin.java#L109-L120
[terrapin-mate]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/alexsmobs/entity/EntityTerrapin.java#L584-L620
[terrapin-lay]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/alexsmobs/entity/EntityTerrapin.java#L622-L669
[terrapin-entity]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/EntityType.java#L1453-L1456
[terrapin-step]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/alexsmobs/block/BlockTerrapinEgg.java#L74-L109
[terrapin-loot]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/alexsmobs/block/BlockTerrapinEgg.java#L179-L217
[block-as-item]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/Block.java#L529-L535
[missing-item]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/Item.java#L118-L120
[stack-construction]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/ItemStack.java#L248-L271
[empty-stack]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/ItemStack.java#L297-L299
[state-dispatch]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java#L311-L334
[terrapin-parents]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/alexsmobs/tileentity/TileEntityTerrapinEgg.java#L22-L45
[turtle-food]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/tags/item/turtle_food.json#L1-L5
[turtle-food-check]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/animal/Turtle.java#L233-L236
[turtle-goals]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/animal/Turtle.java#L146-L156
[turtle-mate]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/animal/Turtle.java#L296-L325
[turtle-lay]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/animal/Turtle.java#L437-L483
[turtle-entity]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/EntityType.java#L1449-L1452
[turtle-step]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/TurtleEggBlock.java#L54-L88
[turtle-loot]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/loot_table/blocks/turtle_egg.json#L1-L33
[turtle-trampler]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/TurtleEggBlock.java#L167-L173
[sneaking]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/Entity.java#L2545-L2551
[zombie-goals]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/monster/Zombie.java#L102-L112
[zombie-egg]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/monster/Zombie.java#L570-L588
[remove-goal]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/ai/goal/RemoveBlockGoal.java#L36-L49
[remove-target]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/ai/goal/RemoveBlockGoal.java#L141-L151
[remove-egg]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/ai/goal/RemoveBlockGoal.java#L106-L120
[warm-ruin]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/worldgen/structure/ocean_ruin_warm.json#L1-L9
[ruin-set]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/worldgen/structure_set/ocean_ruins.json#L8-L17
[ruin-processor]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanRuinPieces.java#L55-L60
[ruin-settings]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanRuinPieces.java#L268-L278
[ruin-loot]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/loot_table/archaeology/ocean_ruin_warm.json#L1-L57
[brushing]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/entity/BrushableBlockEntity.java#L60-L81
[brush-drop]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/entity/BrushableBlockEntity.java#L131-L146
[sniffer-food]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/tags/item/sniffer_food.json#L1-L5
[sniffer-brain]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/animal/sniffer/SnifferAi.java#L125-L135
[breed-caller]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/ai/behavior/AnimalMakeLove.java#L76-L85
[sniffer-mate]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/animal/sniffer/Sniffer.java#L333-L341
[sniffer-boost-check]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/SnifferEggBlock.java#L100-L102
[sniffer-boost]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/tags/block/sniffer_egg_hatch_boost.json#L1-L5
[sniffer-age]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/animal/sniffer/Sniffer.java#L410-L432
[sniffer-entity]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/EntityType.java#L1236-L1245
[sniffer-loot]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/loot_table/blocks/sniffer_egg.json#L1-L21
[sniffer-block]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/SnifferEggBlock.java#L27-L103
[landing]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/Block.java#L456-L458
[fall-dispatch]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/Entity.java#L1398-L1405
