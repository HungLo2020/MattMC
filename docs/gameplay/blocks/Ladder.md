# Ladder

A Ladder (`minecraft:ladder`) makes a vertical climbing route along a supported wall. **Every placed ladder needs its own sturdy side support**; a ladder above it does not hold it up. It can be waterlogged, collected by hand, and topped with a correctly aligned open trapdoor. [English name][name-ladder] · [Block registration][reg-ladder] · [Item registration][items-ladder] · [Support and placement][ladder] · [Climbing recognition][climb]

## Crafting and collection

Arrange **7 Sticks in an H pattern** at a [Crafting Table](CraftingTable.md): Stick–empty–Stick across the top and bottom, and three Sticks across the middle. This makes **3 Ladders**. The recipe accepts the ordinary Stick item, without separate wood-color variants. [Exact recipe][recipe-ladder] · [Stick](../items/Stick.md)

A placed Ladder drops **one Ladder**, including when mined by hand. An unbroken axe is the efficient tool, but no particular tool or tier is required for the item. Hardness and blast resistance are both **0.4**. Silk Touch and Fortune do not change the self-drop. Explosions apply a survival check, so they do not guarantee recovery. [Properties][reg-ladder] · [Axe targets][mineable-axe] · [Axe assignment][tool-family] · [Tool speed][tool] · [Broken-tool speed][broken-speed] · [Harvest check][harvest] · [Strength semantics][strength] · [Ladder loot][loot-ladder]

Ladders also generate on the wall leading to the upper floor of **tall Stronghold libraries**. This is one verified generated source, not a complete list of structures or a guarantee that every Stronghold library has an upper floor. [Library ladder placement][library] · [Library piece selection][stronghold-pool] · [Stronghold generation][stronghold]

## Placement and support

Place the Ladder against a **sturdy side face**. Its facing points away from the supporting block, and its thin collision panel occupies **3/16 of a block** beside that wall. It is not a full solid block across the passage. Placement tries horizontal attachment directions and fails if none provides suitable support. [Shape, facing, and support][ladder] · [Collision uses the block shape][default-support]

Build the backing wall along the full climbing route. A floor below the Ladder or another Ladder above/below is not a substitute for the block behind that rung. If its backing face stops being sturdy, the Ladder is removed on the support update and its ordinary drop is available. Clicking the front of an existing Ladder does not stack another Ladder outward on that same face; aim at the intended backing block instead. [Survival and placement rejection][ladder] · [Neighbor removal][removal] · [Drop dispatch][removal-drops] · [Loot][loot-ladder]

## Climbing and descent

Move into the Ladder's block space and press toward the wall to climb; the normal movement path also allows the jump input to give upward motion while on a climbable block. Release upward movement to slide down, or hold **sneak** to stop downward sliding during ordinary non-flying play. The movement handler limits downward motion and resets accumulated fall distance while it handles the entity as climbing. A Ladder only helps while the character is actually recognized as being on it, not merely somewhere beside a ladder wall. [Climbable tag][climbable] · [Current-block recognition][climb] · [Climbing motion and fall reset][movement] · [Sneak condition][sneak]

**Iron Chain is not a substitute for Ladder.** It is not in the bundled climbable tag and has its own narrow collision shape; see [Iron Chain](IronFixtures.md#iron-chain). [Climbable set][climbable] · [Chain shape][chain]

## Exiting through a trapdoor

An **open trapdoor directly above a Ladder** counts as a climbing continuation when the two blocks have **the same horizontal facing**. A closed trapdoor, a different facing, or a missing Ladder immediately below fails that special check. There is no wood-only restriction in the climbing test. [Exact trapdoor test][climb]

This works with the trapdoor forms covered in [wood construction](WoodConstruction.md#trapdoors), [copper construction](CopperConstruction.md#doors-and-trapdoors), and [Iron Trapdoor](IronFixtures.md#iron-trapdoor). Use the appropriate opening control: the iron form needs redstone, while the wood and copper forms described in those guides also open by hand. Opening leaves a vertical panel with collision, so the exit must have enough space beside it. [Trapdoor shape][trapdoor-shape] · [Iron control flags][iron-controls]

## Water and fuel

A Ladder can retain a water source in its block space. Placement in source Water sets `waterlogged`; placement checks distinguish it from Flowing Water. A Water Bucket can fill an eligible placed Ladder, and an empty Bucket can remove the stored water while leaving a supported Ladder. Neighbor updates schedule the stored fluid's update. Filling still follows ordinary bucket rules, including water evaporation in ultrawarm dimensions. [Ladder water state][ladder] · [Fluid registrations][fluids] · [Shared fill and pickup][water] · [Bucket restrictions][bucket]

A Ladder item is accepted as **300 ticks of furnace fuel** in the default fuel table. This describes the inventory item used as fuel, not a guarantee that a placed Ladder burns in the world. For cooking efficiency and partial burn time, see [Furnace fuel planning](Furnace.md#fuel-planning). [Default fuel duration and Ladder entry][fuel]

## Sources and verification

Source-reviewed at `3cc0d7d93500be7a135c5577511b5565406d42e6` on 2026-10-02. The English name, block/item registration, recipe and loot, tool eligibility, support/shape/water rules, active climbing and trapdoor checks, fuel entry, and cited Stronghold generation were inspected. No in-game crafting, mining, support-removal, climbing, water, fuel or world-generation test was run. Recipes and tags describe the bundled data.

[name-ladder]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/assets/minecraft/lang/en_us.json#L1768
[reg-ladder]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/Blocks.java#L1411-L1415
[items-ladder]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/item/Items.java#L484
[ladder]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/LadderBlock.java#L25-L125
[climb]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1652-L1676
[recipe-ladder]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/data/minecraft/recipe/crafting/ladder.json#L1-L16
[mineable-axe]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/data/minecraft/tags/block/mineable/axe.json#L1-L59
[tool-family]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/item/Item.java#L431-L440
[tool]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/item/ToolMaterial.java#L20-L48
[broken-speed]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/item/ItemStack.java#L374-L379
[harvest]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L656
[strength]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1143-L1153
[loot-ladder]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/data/minecraft/loot_table/blocks/ladder.json#L1-L21
[library]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/levelgen/structure/structures/StrongholdPieces.java#L640-L676
[stronghold-pool]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/levelgen/structure/structures/StrongholdPieces.java#L45-L66
[stronghold]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/levelgen/structure/structures/StrongholdStructure.java#L27-L51
[default-support]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L326
[removal]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/Block.java#L213-L224
[removal-drops]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/Level.java#L263-L280
[climbable]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/resources/data/minecraft/tags/block/climbable.json#L1-L13
[movement]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2458-L2499
[sneak]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3335-L3336
[chain]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/ChainBlock.java#L25-L84
[trapdoor-shape]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/TrapDoorBlock.java#L40-L110
[iron-controls]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/state/properties/BlockSetType.java#L11-L46
[fluids]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/material/Fluids.java#L6-L11
[water]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java#L18-L49
[bucket]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/item/BucketItem.java#L100-L130
[fuel]: https://github.com/HungLo2020/MattMC/blob/3cc0d7d93500be7a135c5577511b5565406d42e6/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L74
