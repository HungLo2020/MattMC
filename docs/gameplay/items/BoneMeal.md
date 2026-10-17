# Bone Meal

**Bone Meal** (`minecraft:bone_meal`) grows or propagates supported plants, renews some ground cover, and creates underwater vegetation. It also makes White Dye and packs into Bone Blocks. Ordinary stacks hold **64**. A valid growth attempt can spend one Bone Meal without producing a visible change. [Item registration][items] · [Default stack size][components] · [Application][bone-meal]

## Obtaining

| Source | Result and conditions |
| --- | --- |
| Craft **1 Bone** in any crafting slot | **3 Bone Meal**; a shapeless recipe. [Recipe][bone-recipe] |
| Craft **1 Bone Block** in any crafting slot | **9 Bone Meal**; a shapeless unpacking recipe. See [Bone Block](../blocks/BoneBlock.md) for fossil sources, mining, and reversible storage. [Recipe][unpack-recipe] |
| Complete a [Composter](../blocks/Composter.md) batch | **1 Bone Meal** at level 8. Accepted inputs first raise it to level 7, followed by a scheduled **20-game-tick** maturation step, nominally one second at 20 ticks per second. Interact with the ready block to release the item, or extract through its bottom face. [Composter][composter] |
| Kill a Cod, Salmon, Tropical Fish, or Pufferfish | Each has a separate **5% chance of 1 Bone Meal** in its death loot, with no player-kill condition or Looting modifier in that pool. Normal mob-loot rules still apply. Catching a fish with a rod or Bucket is not this death-loot action. [Cod][cod-loot] · [Salmon][salmon-loot] · [Tropical Fish][tropical-loot] · [Pufferfish][puffer-loot] · [Death-loot dispatch][death-loot] |
| Open a Trial Chamber **supply chest** | A selected Bone Meal entry gives **2–5**. The chest makes 3–5 weighted selections from its whole supply pool; Bone Meal is not guaranteed, and 2–5 is not a guaranteed total per chest. The reachable supply template carries this loot-table binding. [Loot][supply-loot] · [Structure configuration][trial-structure] · [Supply pool][supply-pool] · [Chest template][supply-template] |

Ordinary mining of a **ready, level-8 Composter** also drops one Bone Meal alongside its block; partial compost does not. For automated production, preserve room for Bone Meal in the receiving Hopper: the reviewed failed-extraction path can empty the Composter before the Hopper accepts its item. This potential loss is tracked in [issue #796](https://github.com/HungLo2020/MattMC/issues/796), remains untested in game, and is explained in [Composter automation](../blocks/Composter.md#hoppers-and-automation). [Composter loot][composter-loot] · [Output container][composter] · [Transfer path][hopper] · [Removal notification][simple-container]

Bone Meal also has Creative inventory entries. Catalog visibility does not establish a Survival source; see the [Inventory Browser's mode and permission limits](../mechanics/InventoryBrowser.md#mode-and-permission-limits). [Creative listings][creative]

## Usage

### Applying Bone Meal

Hold Bone Meal and use it on the target block. The game first checks whether that block supports Bone Meal **in its current state**. Once a target is accepted on the server, ordinary Survival use spends **one**, even if a random success check fails or the attempted feature finds no room. An invalid direct target does not spend Bone Meal through that route. [Target, success, and consumption order][bone-meal]

**Creative player use keeps the held count:** the active player-use dispatcher restores the original stack size after the item's action. This is separate from the item helper itself, which subtracts one during an accepted server attempt. [Player-use dispatcher][use-dispatch] · [Infinite-material ability][player]

The block's own interaction can run first. For example, an ordinary main-hand use on ripe Wheat, Carrots, Potatoes, or Beetroot can harvest and reset the crop; a berry-bearing Cave Vine can give its berries. That click does not also fertilize it. Use Bone Meal again on the newly reset crop or bare vine. [Interaction order][use-dispatch] · [Crop harvesting][crop] · [Cave Vine interaction][cave-vines] [cave-vine-body][]

### Supported plant and ground-cover routes

This is a practical selection of supported routes, not a complete target catalog. The linked block guides own detailed planting, harvesting, light, space, and height rules. Bone Meal has no universal growth amount or universal light exemption: each target supplies its own checks. [Shared dispatch][bone-meal] · [Registered blocks][blocks]

| Target | Practical effect and important limit |
| --- | --- |
| [Wheat](../blocks/Wheat.md), [Carrots, Potatoes, and Beetroot](../blocks/RootCrops.md), [Torchflower crop](../blocks/Torchflower.md), [Pitcher crop](../blocks/PitcherPlant.md) | Advances an immature crop. Growth amounts differ; Beetroot can gain **zero** age on a valid use, while Pitcher still checks light and room for its upper half. Fully grown states are not valid crop-growth targets. [Crops][crop] [beetroot][] [torchflower-crop][] [pitcher][] |
| [Pumpkin and Melon stems](../blocks/PumpkinAndMelon.md), [Cocoa](../blocks/Cocoa.md), [Sweet Berry Bush](../blocks/SweetBerryBush.md) | Advances immature growth. A stem reaching age 7 attempts its normal fruit-growth logic; Bone Meal does not force fruit into an unsuitable neighbor. Already mature stems and attached stems have no direct fertilizing action. [Stem][stem] [attached-stem][] [cocoa][] [sweet-berry][] |
| [Saplings and Azaleas](../blocks/SaplingsAndAzaleas.md), including [Pewen](../blocks/Pewen.md) and [Ancient Saplings](../blocks/AncientPlants.md) | Attempts tree growth. Ordinary saplings have a **45% advancement chance** and a stage before the tree attempt; clear space and any required planting pattern still matter. Azaleas have their own direct tree-attempt rule. [Sapling][sapling] [azalea][] |
| [Mangrove Leaves and hanging Propagules](../blocks/TreeLeaves.md#mangrove-propagules) | Leaves with air below create a hanging propagule; Bone Meal matures an immature hanging propagule. A planted propagule instead follows the tree-growth route. [Leaves][mangrove-leaves] [propagule][] |
| [Brown/Red Mushrooms](../blocks/Mushrooms.md) and [Crimson/Warped Fungi](../blocks/NetherFungi.md) | Have a **40% chance** to attempt a huge form. Fungi require their matching Nylium below; mushrooms and the selected huge features have their own placement requirements. A successful roll is not a guaranteed structure. [Mushroom][mushroom] [fungus][] |
| [Bamboo](../blocks/Bamboo.md), [Kelp](../blocks/Kelp.md), [Weeping/Twisting Vines](../blocks/Vines.md), and [Dripleaves](../blocks/Dripleaves.md) | Grow their respective shoots or column tips when their rules permit; Small Dripleaf converts to Big Dripleaf. Bone Meal on a connected Kelp or Nether-vine body acts at its tip. Bamboo can accept an attempt even when overhead space prevents growth. [Bamboo][bamboo] [bamboo-shoot][] · [Tip/body growth][growing-tip] [growing-body][] · [Dripleaves][small-dripleaf] [big-dripleaf][] |
| [Cave Vines / Glow Berries](../blocks/Vines.md#cave-vines-and-glow-berries) | Makes the selected bare segment bear berries. **It does not extend the vine**, including when used on the tip. [Tip][cave-vines] · [Body][cave-vine-body] |
| Grass Block and [Short Grass/Fern](../blocks/GrassAndFerns.md) | Grass Block with air above attempts nearby grass and biome-dependent flowers. Short Grass or Fern with suitable headroom becomes Tall Grass or Large Fern. The two tall forms themselves do not accept direct Bone Meal. [Grass Block][grass] · [Short plants][short-grass] |
| [Sunflower, Lilac, Rose Bush, and Peony](../blocks/Flowers.md); [Pink Petals and Wildflowers](../blocks/FlowerbedsAndLeafLitter.md) | The four tall flowers drop another matching item. Petals/Wildflowers add one segment up to four, then drop an item on further use. This does not imply the same behavior for every flower or for Leaf Litter. [Tall flowers][tall-flower] · [Flower beds][flower-bed] |
| [Bush, Firefly Bush, and Dry Grass](../blocks/ShrubsAndDryGrass.md); [Glow Lichen](../blocks/GlowLichen.md) | Bushes and Tall Dry Grass need a supported empty horizontal neighbor to spread; Tall Dry Grass produces Short Dry Grass. Short Dry Grass becomes the one-block-tall dry form. Glow Lichen attempts a supported face spread. [Bush][bush] [firefly][] [short-dry][] [tall-dry][] [lichen][] |
| [Moss and Pale Moss](../blocks/MossAndPaleMoss.md), [Nylium and Netherrack](../blocks/NetherGroundAndVegetation.md), [Rooted Dirt](../blocks/HangingRootsAndSporeBlossom.md) | Moss and Nylium trigger their configured vegetation features; **moss can replace eligible nearby terrain**. Pale Moss Carpet and Pale Hanging Moss have separate growth actions. Netherrack needs nearby Nylium to convert; Rooted Dirt needs air beneath it to add Hanging Roots. [Feature placer][feature-placer] [pale-carpet][] [hanging-moss][] [nylium][] [netherrack][] [rooted-dirt][] |

MattMC also has specific imported-plant routes: [Flytrap](../blocks/AncientPlants.md#flytrap) drops an extra plant item; [Fiddlehead](../blocks/PrimordialPlants.md#fiddlehead) spreads to a supported horizontal neighbor; [Cycad](../blocks/PrimordialPlants.md#bone-meal-growth) can add a block above a short column after a 50% roll; and [Archaic Vine](../blocks/PrimordialPlants.md#extending-the-vine) extends its downward tip into air. These behaviors do not establish a natural Survival starter source. [Flytrap][flytrap] · [Fiddlehead inheritance][fiddlehead] [bush][] · [Cycad][cycad] · [Archaic Vine][archaic]

### Underwater growth

For the water-spreading action, **use Bone Meal on a sturdy block face with a full Water block immediately outside that face**. The clicked block's own valid Bone Meal action takes priority; only if it does not qualify does the item try the adjacent water. The starting cell must be the actual Water block with fluid amount **8**. Flowing water of a lower amount and waterlogged non-Water blocks do not satisfy that starting test. [Hand-use routing and water test][bone-meal]

One accepted attempt spends one Bone Meal and runs **128 candidate attempts**, not 128 guaranteed plants. Placement checks the candidate's support, available water, and collisions. The default plant is Seagrass. Candidates in the bundled **Warm Ocean** biome can also select living coral plants or fans; those choices come from biome and block tags. Clicking a horizontal sturdy face enables an initial wall-coral choice in a qualifying biome. This route does not create full Coral Blocks or revive dead coral. [Spreading algorithm][bone-meal] · [Warm Ocean tag][coral-biomes] · [Underwater candidates][underwater-tag] [corals-tag][] [wall-corals][] · [Coral guide](../blocks/Coral.md)

Two direct plant actions are separate from that spread:

- **[Seagrass](../blocks/Seagrass.md):** Water directly above lets the existing plant become Tall Seagrass. Its upper-cell test is for a Water block; it is not the spread entry's amount-8 test. [Direct callback][seagrass]
- **[Sea Pickles](../blocks/SeaPickle.md):** the target must be waterlogged and sit on a living Coral Block from the coral-block tag. It becomes a cluster of four and attempts more clusters nearby. An already-four-pickle cluster can still accept Bone Meal to attempt spreading. [Direct callback][sea-pickle] · [Accepted coral blocks][coral-blocks]

### Crafting and storage

Craft **1 Bone Meal → 1 White Dye** in any crafting slot. For recipes or interactions that ask for White Dye, make the dye first. [White Dye recipe][white-recipe]

For compact storage, follow [Bone Block packing and unpacking](../blocks/BoneBlock.md#packing-and-unpacking-bone-meal). Unpack before fertilizing: Bone Blocks do not use the Bone Meal item action. [Separate item registrations][items]

## Behavior

### Dispensers

A powered **[Dispenser](../blocks/DispenserAndDropper.md)** that selects Bone Meal checks the block **directly in front**. It first tries that block's normal Bone Meal action, then the water-spreading action at that same position. Its registered behavior uses the same one-item consumption rules: a valid failed growth roll still spends one, while an invalid plant/water target leaves the stack intact and gives the failure sound. It does **not** eject the Bone Meal as a fallback. [Registered action][dispense] · [Behavior selection][dispenser] · [Failure sound][optional-dispense] · [Bootstrap wiring][bootstrap]

For underwater automation, the cell directly in front must satisfy the full-Water starting test. The Dispenser does not require a clicked support face and passes no face direction, so it has no hand-use initial wall-coral choice; later random coral/fan choices still use the biome tags. A Dispenser spends its stored Bone Meal even when a Creative player placed or powered it. A **Dropper** transfers or ejects the item instead of fertilizing. [Same helpers and null-direction call][dispense] [bone-meal][] · [Dropper action][dropper]

## Notes

- **Sugar Cane, Cactus, Nether Wart, Chorus, and ordinary Vines have no direct Bone Meal growth** in their registered classes. Their natural growth does not imply fertilizer support. [Registrations][blocks] · [Sugar Cane][sugar-cane] [cactus][] [nether-wart][] [chorus-plant][] [chorus-flower][] [vine][]
- An unsuitable target, a failed random roll, an obstructed growth feature, and a block interaction that runs before item use are different cases. Check the target guide before repeatedly spending Bone Meal
- Particles or a Dispenser success sound indicate an accepted action, not a guaranteed harvest or visible growth. Both are triggered after target acceptance, even if its growth success roll failed. [Item event][bone-meal] · [Dispenser event][dispense]

Related: [Composter](../blocks/Composter.md) · [Bone Block](../blocks/BoneBlock.md) · [Farmland](../blocks/Farmland.md) · [Dispenser and Dropper](../blocks/DispenserAndDropper.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Checked the active item/block registrations, exact bundled recipes and Bone Meal loot entries, fish death-loot dispatch, reachable Trial Chamber supply-template binding, Composter output, player-use order and Creative restoration, growth callbacks, underwater tags, and bootstrapped Dispenser behavior. Target-specific block guides retain their deeper feature, support, and harvest detail. No in-game acquisition, growth, Creative, underwater, or automation test was run. Recipes, loot, tags, features, and server settings can alter results.

[items]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java
[components]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/component/DataComponents.java
[bone-meal]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/BoneMealItem.java
[bone-recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/bone_meal.json
[unpack-recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/bone_meal_from_bone_block.json
[white-recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/white_dye.json
[cod-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/entities/cod.json
[salmon-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/entities/salmon.json
[puffer-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/entities/pufferfish.json
[tropical-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/entities/tropical_fish.json
[death-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java
[supply-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/chests/trial_chambers/supply.json
[trial-structure]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/worldgen/structure/trial_chambers.json
[supply-pool]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/worldgen/template_pool/trial_chambers/chests/contents/supply.json
[supply-template]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/structure/trial_chambers/chests/supply.nbt
[composter-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/composter.json
[simple-container]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/SimpleContainer.java
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[use-dispatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java
[player]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/player/Player.java
[coral-biomes]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/worldgen/biome/produces_corals_from_bonemeal.json
[underwater-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/block/underwater_bonemeals.json
[corals-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/block/corals.json
[wall-corals]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/block/wall_corals.json
[coral-blocks]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/block/coral_blocks.json
[dispense]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java
[optional-dispense]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/dispenser/OptionalDispenseItemBehavior.java
[bootstrap]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/Bootstrap.java
[archaic]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexscaves/server/block/ArchaicVineBlock.java
[blocks]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/Blocks.java
[composter]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/ComposterBlock.java
[hopper]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java
[crop]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/CropBlock.java
[cave-vines]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/CaveVinesBlock.java
[cave-vine-body]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/CaveVinesPlantBlock.java
[beetroot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/BeetrootBlock.java
[torchflower-crop]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/TorchflowerCropBlock.java
[pitcher]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/PitcherCropBlock.java
[stem]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/StemBlock.java
[attached-stem]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/AttachedStemBlock.java
[cocoa]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/CocoaBlock.java
[sweet-berry]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/SweetBerryBushBlock.java
[sapling]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/SaplingBlock.java
[azalea]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/AzaleaBlock.java
[mangrove-leaves]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/MangroveLeavesBlock.java
[propagule]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/MangrovePropaguleBlock.java
[mushroom]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/MushroomBlock.java
[fungus]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/FungusBlock.java
[bamboo]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/BambooStalkBlock.java
[bamboo-shoot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/BambooSaplingBlock.java
[growing-tip]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/GrowingPlantHeadBlock.java
[growing-body]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/GrowingPlantBodyBlock.java
[small-dripleaf]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/SmallDripleafBlock.java
[big-dripleaf]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/BigDripleafBlock.java
[grass]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/GrassBlock.java
[short-grass]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/TallGrassBlock.java
[tall-flower]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/TallFlowerBlock.java
[flower-bed]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/FlowerBedBlock.java
[bush]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/BushBlock.java
[firefly]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/FireflyBushBlock.java
[short-dry]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/ShortDryGrassBlock.java
[tall-dry]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/TallDryGrassBlock.java
[lichen]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/GlowLichenBlock.java
[feature-placer]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/BonemealableFeaturePlacerBlock.java
[pale-carpet]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/MossyCarpetBlock.java
[hanging-moss]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/HangingMossBlock.java
[nylium]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/NyliumBlock.java
[netherrack]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/NetherrackBlock.java
[rooted-dirt]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/RootedDirtBlock.java
[flytrap]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/custom/FlytrapBlock.java
[fiddlehead]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/custom/FiddleheadBlock.java
[cycad]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/custom/CycadBlock.java
[seagrass]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/SeagrassBlock.java
[sea-pickle]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/SeaPickleBlock.java
[dispenser]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/DispenserBlock.java
[dropper]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/DropperBlock.java
[sugar-cane]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/SugarCaneBlock.java
[cactus]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/CactusBlock.java
[nether-wart]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/NetherWartBlock.java
[chorus-plant]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/ChorusPlantBlock.java
[chorus-flower]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/ChorusFlowerBlock.java
[vine]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/VineBlock.java
