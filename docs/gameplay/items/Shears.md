# Shears

**Shears** (`minecraft:shears`) collect wool and fragile plants, harvest Honeycomb, trim growing tips, and remove eligible leashes or equipment. A normal pair stacks to **one** and has **238 durability**. MattMC keeps a fully worn pair as a broken item, so repair it rather than assuming it disappears. [Item registration][item] · [Stack limit][stack-limit] · [Retained broken items][wear]

## Obtaining

- **Craft one pair from two [Iron Ingots](IronIngot.md)** placed diagonally in a 2 × 2 grid: one in the upper-right slot and one in the lower-left. The mirrored diagonal also works, and the recipe fits the inventory grid. [Recipe][recipe] · [Mirrored pattern matching][pattern]
- A **novice [Shepherd](../mobs/Villager.md)** can sell one pair for a base price of **2 Emeralds**, with **12 uses before restocking**. Its two initial offers are selected from five entries, giving the Shears offer a **40% selection chance**. Demand and price adjustments can change the displayed price. The optional Trade Rebalance feature falls back to this ordinary Shepherd list. [Offer list][trade] · [Offer construction][trade-details] · [Two-offer caller and fallback][trade-caller] · [Selection][trade-selection] · [Price adjustment][trade-price]
- **Snowy village shepherd-house chests** can contain Shears. The connected house template uses a table with **1–5 rolls**, each with a **1-in-23** chance to select one pair: approximately **12.31%** for at least one pair in a chest using that table. This is a loot-table calculation, not a guarantee that a village contains the house. [House pool][house-pool] · [Chest-bearing template][house-template] · [Loot table][chest] · [Weighted rolls][loot-rolls] · [Default weights][loot-weights] · [Integer roll count][loot-count]

Shears are also listed in the [inventory item browser](../mechanics/InventoryBrowser.md), whose insertion route requires Creative. Browsing in Survival does not grant the item. [Category entry][creative]

## Usage

### Mining and collecting blocks

For unbroken Shears, the base mining speeds are **15 for Cobweb and the eleven tagged ordinary Leaves**, **5 for the sixteen Wool blocks**, and **2 for ordinary Vine and Glow Lichen**. Other blocks use the tool's **1** baseline. These are speed values before player modifiers, not fixed breaking times. **Ancient Leaves and Pewen vegetation are absent from the leaves tag**, even though their loot accepts Shears. [Tool rules][shears] · [Leaves tag][leaves-tag] · [Wool tag][wool-tag]

**[Cobweb](../blocks/Cobweb.md#collecting-cobwebs) is the explicit correct-tool target:** serviceable Shears both qualify for its gated drops and recover **one Cobweb**. On other plants, an item-specific loot condition can matter even when the block has no required tool. A Shears collection rule does not make Shears a suitable pickaxe for ores. [Tool rules][shears] · [Cobweb registration][cobweb-block] · [Cobweb loot][loot-cobweb] · [Player harvest gate][harvest-gate]

Break the plant or block to collect these items; merely using/right-clicking it is a different action. The linked guides own planting, propagation and the full non-Shears loot alternatives.

| Block or family | Ordinary harvest with Shears |
| --- | --- |
| [Tree Leaves](../blocks/TreeLeaves.md#leaf-families-and-drops), [Ancient Leaves](../blocks/AncientPlants.md#ancient-leaves-decoration-decay-and-drops), [Pewen Branches and Pines](../blocks/Pewen.md#branches-pines-and-resources) | One matching decorative block instead of its sapling/resource rolls. Silk Touch is also accepted. [Ordinary leaf example][loot-oak] · [Ancient][loot-ancient] · [Branch][loot-pewen-branch] · [Pines][loot-pewen-pines] |
| [Short Grass and Fern](../blocks/GrassAndFerns.md#forms-and-exact-ids) | One matching short plant. An intact Tall Grass or Large Fern gives **two of its short counterpart**, not a tall inventory item. [Grass][loot-grass] · [Fern][loot-fern] · [Tall Grass][loot-tall-grass] · [Large Fern][loot-large-fern] |
| [Dead Bush](../blocks/DeadBush.md#finding-and-collecting) | One Dead Bush instead of the ordinary Stick roll. [Loot][loot-dead-bush] |
| [Seagrass](../blocks/Seagrass.md#finding-and-harvesting) | One Seagrass from the short form; **two** from an intact tall plant. [Short loot][loot-seagrass] · [Tall loot][loot-tall-seagrass] |
| [Hanging Roots](../blocks/HangingRootsAndSporeBlossom.md#hanging-roots), [Small Dripleaf](../blocks/Dripleaves.md#small-dripleaf), [Nether Sprouts](../blocks/NetherGroundAndVegetation.md#nether-sprouts) | One corresponding item. An intact two-block Small Dripleaf is harvested once, not once per half. [Roots][loot-roots] · [Dripleaf][loot-dripleaf] · [Sprouts][loot-sprouts] · [Two-block harvesting][double-plant] |
| [Ordinary Vine](../blocks/Vines.md#ordinary-vines) | One Vine per mined block, irrespective of its attached-face count. [Loot][loot-vine] |
| [Glow Lichen](../blocks/GlowLichen.md#harvesting) | **One item per occupied face**, up to six from one block. [Face-count loot][loot-lichen] |
| [Bush and both dry grasses](../blocks/ShrubsAndDryGrass.md#exact-forms-and-harvest), [Pale Hanging Moss](../blocks/MossAndPaleMoss.md#pale-hanging-moss) | One matching item. These tables also accept Silk Touch; Tall Dry Grass is a single-block plant and returns itself. [Bush][loot-bush] · [Short dry][loot-short-dry] · [Tall dry][loot-tall-dry] · [Moss][loot-pale-moss] |
| [Weeping and Twisting Vines](../blocks/Vines.md#recovering-nether-vines), [Archaic Vine](../blocks/PrimordialPlants.md#harvesting-and-climbing) | One matching Vine from each individually harvested tip or body. Shears select the guaranteed tool branch rather than the ordinary 33% fallback. [Weeping][loot-weeping] [body][loot-weeping-body] · [Twisting][loot-twisting] [body][loot-twisting-body] · [Archaic][loot-archaic] [body][loot-archaic-body] |

Silk Touch on another tool **does not replace Shears** for ordinary Grass/Ferns, Dead Bush, Seagrass, Hanging Roots, Small Dripleaf, Nether Sprouts, ordinary Vine or Glow Lichen. Removing support is also not a Shears harvest: detached blocks use their own tool-free drop path. In particular, cut vine segments individually when preserving a limited supply. [Vine loot][loot-vine] · [Glow Lichen loot][loot-lichen] · [Hanging Roots loot][loot-roots] · [Support-loss drops][destroy-block]

### Harvesting, carving and trimming in place

- **Full Bee Nest or Beehive:** use Shears without Sneak/Crouch at honey level **5** to drop **three Honeycomb** and reset honey to zero. For hand collection, provide recognized campfire smoke first; unsmoked occupied housing can anger Bees and release them in emergency mode. Follow [Bee housing](../blocks/BeeHousing.md#harvesting) for smoke placement and colony care. [Harvest handler][hive] · [Honeycomb table][honeycomb] · [Use routing][block-use]
- **Pumpkin:** use Shears without Sneak/Crouch to leave a **Carved Pumpkin** and drop **four Pumpkin Seeds**. This is a use action on an uncarved Pumpkin, not a mining drop or a Dispenser carving recipe. See [Pumpkin carving](../blocks/PumpkinAndMelon.md#carving-a-pumpkin). [Carving][pumpkin] · [Seed table][pumpkin-loot] · [Use routing][block-use]
- **Growing tips:** use unbroken Shears on a tip below age 25 to set it to **age 25** without removing the plant. This stops natural extension of **Kelp, Weeping Vines, Twisting Vines and Cave Vines**. It does not target a body segment, ordinary Vine or Pale Hanging Moss. Bone Meal can still extend eligible Kelp/Nether-vine tips; on Cave Vines it can still produce berries. A fruiting Cave Vine can harvest its berry before the held Shears reach their trimming action, so use the bare tip afterward. See [Kelp trimming](../blocks/Kelp.md#growth-bone-meal-and-trimming) and [Vine trimming](../blocks/Vines.md#trimming-climbing-and-water). [Shears use][trim] · [Tip growth and Bone Meal][head] · [Cave Vine behavior][cave-vines] · [Interaction priority][block-use]

The shared trimming action also accepts an **Archaic Vine tip**, but that plant already has no natural extension in its own random-tick handler. Setting its age does not disable inherited Bone Meal growth. The three ordinary successful hand actions above each request **one durability**, before the wear modifiers described below. [Archaic growth override][archaic] · [Trimming wear][trim] · [Hive wear][hive] · [Carving wear][pumpkin]

### Shearing mobs

Use Shears on the mob rather than attacking it. The following living targets have active hand-shearing callbacks, require **unbroken Shears** for hand use, and also support Dispenser shearing when eligible. A normal successful hand shear requests **one durability**. [Shared mob interaction][mob-interaction] · [Loaded shearing loot][shear-loot-load] · [Dispenser route][dispenser]

| Target | Eligibility and result |
| --- | --- |
| [Sheep](../mobs/Sheep.md#shearing-and-regrowth) | Adult and unsheared: **1–3 Wool of its current color**. Grazing regrows the coat; feeding Wheat does not directly do so. [Sheep callback][sheep] · [Color routing][sheep-loot] · [White count][white-shear] · [Regrowth][sheep-regrowth] |
| [Mooshroom](../mobs/Mooshroom.md#shearing) | Adult: **five mushrooms matching its red/brown variant**, and conversion into a Cow. This is a one-time conversion. [Hand use][moosh-hand] · [Conversion][moosh] · [Variant routing][moosh-route] · [Red][moosh-red] · [Brown][moosh-brown] |
| [Bogged](../mobs/Bogged.md#shearing-the-mushrooms) | Unsheared: **two mushrooms**, each independently Red or Brown, so the pair can match. The saved sheared state has no automatic regrowth; its hostile Poison-arrow behavior remains. [Hand use][bogged-hand] · [Result][bogged] · [Loot][bogged-loot] |
| [Snow Golem](../mobs/SnowGolem.md#shearing-and-preserving-it) | Still wearing its pumpkin: removes that covering and drops **one Carved Pumpkin**. [Callback][snow] · [Loot][snow-loot] |
| [Copper Golem](../mobs/CopperGolem.md#poppy-on-the-antenna) | Wearing an allowed antenna item: drops that item. The bundled tag contains **only Poppy**. [Hand use][copper-hand] · [Removal and readiness][copper] · [Actual tag][copper-tag] |

Some imported animals use a **Dispenser-only species-shearing route**. Ordinary handheld Shears do not invoke these callbacks; having an old helper or a shearable interface is not enough to establish hand use. Shared leash cutting can still happen first. [Current interface][shearable] · [Dispatcher][dispenser] · [Handheld item implementation][shears] · [Bison hand interaction][bison-hand] · [Mungus hand interaction][mungus-hand] · [Cockroach hand interaction][roach-hand]

| Dispenser target | Current callback result |
| --- | --- |
| [Bison](../mobs/Bison.md#shearing-and-fur-regrowth), adult and unsheared | **2–3 Bison Fur**, then a sheared coat; its guide owns the five-grazing regrowth cycle. [Callback and eligibility][bison] |
| [Alligator Snapping Turtle](../mobs/AlligatorSnappingTurtle.md#moss-and-seagrass), alive with moss | **One Seagrass**, then clears all moss. No Spiked Scute output. [Callback][turtle] |
| [Mungus](../mobs/Mungus.md#brown-mushroom-production-and-shearing), alive with a stored mushroom type and positive count | Removes **one stored mushroom**, with **no dropped mushroom item**. The current callback has no adult requirement. [Selected callback][mungus] |
| [Cockroach](../mobs/Cockroach.md#maracas-dancing-and-shearing), living adult with a head | Makes it headless; **no wing or fragment output**. [Callback][roach] |

### Leads, equipment and Tripwire

**Leash cutting takes priority over fleece, flowers and ordinary equipment removal.** Use unbroken Shears on an entity or fence knot to cut its own leash and nearby connections it holds. Each connection drops its Lead, while the whole successful interaction requests one durability. Avoid Sneak/Crouch because transferring your existing connections can take priority. See [Lead cutting](Lead.md#transferring-between-entities-and-cutting-connections). [Interaction order][entity-interaction] · [Cutting][leash-cut] · [Fence knot][knot]

Use unbroken Shears **without Sneak/Crouch** to remove eligible shearable equipment, including a [Saddle](Saddle.md#removing-a-saddle), [Harness](Harnesses.md#removing-and-recoloring-a-harness), or [Wolf Armor](WolfArmor.md#equipping-and-removing). Ordinary mobs must have no passengers; Wolf Armor uses an owner check. Removal drops the existing item, costs one durability before modifiers, and respects equipment-change prevention except in Creative. A Dispenser's Shears action does **not** call this general equipment-removal path. [Equipment gates][equipment] · [Removal and drop][equipment-removal] · [Unoccupied-mob rule][unoccupied] · [Wolf ownership][wolf-owner] · [Dispenser implementation][dispenser]

Imported owner controls have separate rules: [Elephant](../mobs/Elephant.md#riding-decoration-and-storage) removes its carpet before its chest and contents; [Raccoon](../mobs/Raccoon.md#taming-breeding-and-owner-controls) can remove its carpet after any held-item return. Those species callbacks request no Shears wear and do not test the broken state. Their guides explain the missing bundled taming-food routes. [Elephant interaction][elephant] · [Raccoon interaction][raccoon]

An owned, saddled [Komodo Dragon](../mobs/KomodoDragon.md#existing-owned-animals-commands-and-riding) also has a separate Shears branch that clears its saddle flag and attempts a Saddle drop, with no wear or broken-item check. These imported equipment-removal paths were source-reviewed, not tested in gameplay. [Komodo branch][komodo]

For **[Tripwire](../blocks/Tripwire.md#breaking-and-disarming)**, hold Shears in the **main hand and mine a wire segment**. The pre-removal handler marks it disarmed, suppressing its ordinary removal-triggered pulse. This is not a right-click action and does not prevent other circuit responses to detachment or an already powered line. The handler checks item identity, so retained broken Shears also reach this source path. [Disarming][tripwire] · [Hook state calculation][hook] · [Broken-block permission][broken-mining] · [Server removal order][mining]

### Using a Dispenser

Put Shears in a [Dispenser](../blocks/DispenserAndDropper.md) and point it at the target block space. When its Shears slot is selected, the registered action tries, in order:

1. A full tagged Bee Nest or Beehive directly in front. Those are the two bundled entries in the [beehives tag][beehives-tag]
2. Entities whose bounding boxes overlap that block space, trying an entity's leash connections before its eligible species-shearing callback
3. Stop after the first success; a failed attempt does not request durability

Keep other entities out of the target space when the particular animal matters. A successful action requests **one durability** and keeps the Shears in the Dispenser. It has no player associated with its wear call, so a Creative player's ordinary wear exemption does not apply. [Registration][dispenser-registration] · [Selected-item caller][dispenser-caller] · [Targeting, priority and wear][dispenser] · [Wear processing][wear]

Dispenser hive harvesting resets honey and requests normal Bee release with no player target, rather than running the hand-harvest anger routine; smoke is not required for that action. It does not make Bees safe from unrelated disturbances. Dispenser Shears do not mine plants, trim tips, carve Pumpkins or run imported owner-equipment interactions. [Hive and entity operations][dispenser] · [Bee housing details](../blocks/BeeHousing.md#dispenser-collection)

## Behavior

### Durability and broken Shears

Ordinary Survival mining with serviceable Shears requests **one durability for every mined non-fire block**, including instant-breaking plants and blocks outside the fast-mining list. The Shears override has no zero-hardness exemption. Shared enchantment processing can reduce wear, and player actions using the normal wear helper cost no durability in Creative. The last usable mining action checks drop eligibility and copies its loot tool **before** applying wear, so that action can still yield its otherwise eligible drop. [Mining wear][shears] · [Wear modifiers and Creative][wear] · [Mining order][mining]

At **238 damage**, the pair remains in the inventory as broken Shears. Normal hand mob shearing, shared leash/equipment cutting and tip trimming reject a broken pair; its tool speed falls to **1**, and it fails the correct-tool drop check for Cobweb. **Breaking a block is still allowed** where ordinary permissions allow it, which is different from obtaining tool-gated loot. Item-identity loot can still recognize the broken pair on blocks without that gate, such as ordinary Vine and Dead Bush. [Retained state][wear] · [Use and speed guards][broken-use] · [Drop/mining hooks][broken-drops] · [Block permission][broken-mining] · [Ungated plant registrations][vine-block] [dead-bush-block] · [Plant loot][loot-vine] [loot-dead-bush]

There are source-level exceptions: **hive harvesting, Pumpkin carving, Tripwire disarming and the Dispenser Shears handler lack a broken-item check**. Block-side hive/carving handlers run before the item-use guard, and further normal wear is ignored once the stack is broken. These are reviewed inconsistencies, not tested promises of unlimited use. The separate imported owner controls above have their own checks. [Block routing][block-use] · [Hive][hive] · [Pumpkin][pumpkin] · [Tripwire][tripwire] · [Dispenser][dispenser] · [Broken wear][wear]

### Repair and enchantments

Repair two pairs together using the [crafting grid, Grindstone or Anvil](../mechanics/Durability.md#choose-a-repair-method). With ordinary 238-durability pairs, crafting or Grindstone repair adds **11 durability** to their combined remaining durability; matching-item Anvil repair adds **28**, capped at 238. Crafting and Grindstone repair remove ordinary enchantments while preserving curses; Anvil repair is the route for preserving and combining applicable enchantments. **Iron Ingots are not an Anvil repair material for Shears** in this registration: it provides no repairable-material component. [Crafting repair][repair] · [Grindstone repair][grindstone] · [Anvil repair][anvil] · [Registration][item] · [Material check][repair-material]

An Anvil can apply **Efficiency V, Unbreaking III, Mending I and Curse of Vanishing I** under the bundled support tags. Ordinary Shears have no enchantability component, so they do **not** receive direct Enchanting Table offers. **Silk Touch and Fortune are not supported Shears enchantments in normal Survival**; a block table accepting “Shears or Silk Touch” means two alternative tool conditions. Efficiency affects applicable block-mining speed, not the quantities in the shearing tables. Follow [Mining enchantments](../enchanting/MiningEnchantments.md) and [Unbreaking/Mending](../enchanting/DurabilityEnchantments.md) for effects and book acquisition. [Mining support][mining-tag] · [Durability support][durability-tag] · [Vanishing support][vanishing-tag] · [Mining-loot exclusion][mining-loot-tag] · [Efficiency definition][efficiency-data] · [Unbreaking definition][unbreaking-data] · [Mending definition][mending-data] · [Vanishing definition][vanishing-data] · [Silk Touch/Fortune support][silk-data] [fortune-data] · [Table eligibility][enchantability] · [Table caller][table-caller] · [Anvil eligibility][anvil]

With **Mending**, hold the damaged pair in either hand while collecting experience orbs. Retained broken Shears remain eligible for that repair; leaving them elsewhere in the inventory does not equip them. See [Mending repair](../enchanting/DurabilityEnchantments.md#retained-broken-equipment-can-be-repaired). [Repair selection][mending-selection] · [XP repair][xp-repair]

## Notes

- Hand harvesting, mining, species shearing and Dispenser shearing are separate routes. A similarly named imported helper does not establish that the active dispatcher calls it
- Plant owners above retain their propagation, support and starter-acquisition details; the mob owners retain their breeding, regrowth and safety rules
- Related: [Iron Ingot](IronIngot.md), [Lead](Lead.md), [Durability and repair](../mechanics/Durability.md), and [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-03** at MattMC commit `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Checked registration, recipe matching, trade selection, connected chest data, actual loot/tag contents including bundled optional packs, current player/block/mob/Dispenser dispatch, imported callback signatures, wear, repair and enchantment eligibility. Loot and trade percentages are calculations from the checked definitions. **No in-game crafting, trading, chest search, harvesting, shearing, trimming, repair, broken-tool or Dispenser test was run.** Server data packs can change recipes, loot, tags and supported enchantments.

[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1742-L1744
[stack-limit]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Item.java#L386-L390
[wear]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L509
[recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/shears.json
[pattern]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/crafting/ShapedRecipePattern.java#L158-L190
[trade]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L164-L175
[trade-details]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1422-L1479
[trade-caller]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/npc/Villager.java#L818-L840
[trade-selection]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L232
[trade-price]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/trading/MerchantOffer.java#L93-L97
[house-pool]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/worldgen/template_pool/village/snowy/houses.json#L167-L178
[house-template]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/structure/village/snowy/houses/snowy_shepherds_house_1.nbt
[chest]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/chests/village/village_shepherd.json
[loot-rolls]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/storage/loot/LootPool.java#L60-L101
[loot-weights]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/storage/loot/entries/LootPoolSingletonContainer.java#L45-L55
[loot-count]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/storage/loot/providers/number/UniformGenerator.java#L24-L30
[creative]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1456-L1464
[shears]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/ShearsItem.java#L27-L58
[leaves-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/block/leaves.json
[wool-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/block/wool.json
[harvest-gate]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[double-plant]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/DoublePlantBlock.java#L100-L117
[destroy-block]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/Level.java#L263-L281
[hive]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/BeehiveBlock.java#L143-L202
[honeycomb]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/harvest/beehive.json
[block-use]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L340-L397
[pumpkin]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/PumpkinBlock.java#L35-L69
[pumpkin-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/carve/pumpkin.json
[trim]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/ShearsItem.java#L61-L85
[head]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/GrowingPlantHeadBlock.java#L19-L127
[cave-vines]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/CaveVinesBlock.java
[archaic]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexscaves/server/block/ArchaicVineBlock.java#L15-L42
[mob-interaction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1053-L1077
[dispenser]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/dispenser/ShearsDispenseItemBehavior.java#L19-L67
[sheep]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/sheep/Sheep.java#L137-L183
[sheep-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/shearing/sheep.json
[white-shear]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/shearing/sheep/white.json
[moosh-hand]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/MushroomCow.java#L119-L126
[moosh]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/MushroomCow.java#L173-L188
[moosh-red]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/shearing/mooshroom/red.json
[moosh-brown]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/shearing/mooshroom/brown.json
[bogged-hand]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/monster/Bogged.java#L72-L85
[bogged]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/monster/Bogged.java
[bogged-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/shearing/bogged.json
[snow]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/SnowGolem.java#L134-L165
[snow-loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/shearing/snow_golem.json
[copper-hand]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/coppergolem/CopperGolem.java#L222-L230
[copper]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/coppergolem/CopperGolem.java#L429-L440
[copper-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/shearable_from_copper_golem.json
[shearable]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Shearable.java
[bison-hand]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBison.java#L318-L344
[mungus-hand]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMungus.java#L381-L404
[roach-hand]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityCockroach.java#L198-L214
[bison]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityBison.java#L403-L417
[turtle]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityAlligatorSnappingTurtle.java#L372-L383
[mungus]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMungus.java#L643-L679
[roach]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityCockroach.java#L385-L401
[entity-interaction]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L2108-L2178
[leash-cut]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L2182-L2208
[knot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/decoration/LeashFenceKnotEntity.java#L70-L121
[equipment]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L2134-L2144
[unoccupied]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L512-L514
[wolf-owner]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java#L449-L452
[elephant]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityElephant.java#L517-L581
[raccoon]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityRaccoon.java#L192-L227
[komodo]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityKomodoDragon.java#L347-L374
[tripwire]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/TripWireBlock.java#L109-L123
[hook]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/TripWireHookBlock.java#L109-L151
[broken-mining]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/ItemStack.java#L1111-L1116
[mining]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L295
[dispenser-registration]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L378
[broken-use]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L379
[broken-drops]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/ItemStack.java#L576-L604
[repair]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/crafting/RepairItemRecipe.java
[grindstone]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/inventory/GrindstoneMenu.java#L109-L193
[anvil]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L128-L222
[repair-material]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/ItemStack.java#L1106-L1108
[mining-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/enchantable/mining.json
[durability-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/enchantable/durability.json
[vanishing-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/enchantable/vanishing.json
[mining-loot-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/item/enchantable/mining_loot.json
[enchantability]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/ItemStack.java#L965-L971
[loot-cobweb]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/cobweb.json
[loot-oak]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/oak_leaves.json
[loot-ancient]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/ancient_leaves.json
[loot-pewen-branch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/pewen_branch.json
[loot-pewen-pines]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/pewen_pines.json
[loot-grass]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/short_grass.json
[loot-fern]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/fern.json
[loot-tall-grass]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/tall_grass.json
[loot-large-fern]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/large_fern.json
[loot-dead-bush]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/dead_bush.json
[loot-seagrass]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/seagrass.json
[loot-tall-seagrass]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/tall_seagrass.json
[loot-roots]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/hanging_roots.json
[loot-dripleaf]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/small_dripleaf.json
[loot-sprouts]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/nether_sprouts.json
[loot-vine]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/vine.json
[loot-lichen]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/glow_lichen.json
[loot-bush]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/bush.json
[loot-short-dry]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/short_dry_grass.json
[loot-tall-dry]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/tall_dry_grass.json
[loot-pale-moss]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/pale_hanging_moss.json
[loot-weeping]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/weeping_vines.json
[loot-weeping-body]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/weeping_vines_plant.json
[loot-twisting]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/twisting_vines.json
[loot-twisting-body]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/twisting_vines_plant.json
[loot-archaic]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/archaic_vine.json
[loot-archaic-body]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/archaic_vine_plant.json

[cobweb-block]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/Blocks.java#L689-L700
[sheep-regrowth]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/sheep/Sheep.java#L282-L289
[equipment-removal]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L2211-L2228
[efficiency-data]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/enchantment/efficiency.json
[unbreaking-data]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/enchantment/unbreaking.json
[mending-data]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/enchantment/mending.json
[vanishing-data]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/enchantment/vanishing_curse.json
[silk-data]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/enchantment/silk_touch.json
[fortune-data]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/enchantment/fortune.json
[table-caller]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/inventory/EnchantmentMenu.java#L85-L97
[mending-selection]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L479-L497
[xp-repair]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ExperienceOrb.java#L293-L308

[beehives-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/block/beehives.json
[vine-block]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/Blocks.java#L2378-L2391
[dead-bush-block]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/Blocks.java#L727-L738
[shear-loot-load]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1555-L1576
[moosh-route]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/shearing/mooshroom.json

[dispenser-caller]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/DispenserBlock.java#L82-L108
