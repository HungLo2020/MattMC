# Sandstone and Red Sandstone

Sandstone turns loose Sand into stable building material. The ordinary pale family and orange Red Sandstone family each have **10 registered forms**: a base block, chiseled/cut/smooth finishes, stairs, slabs and selected walls. Their recipes keep the two color families separate. [Block registration][blocks] · [Item registration][items]

## Obtaining and mining

Craft **4 Sand in a 2 × 2 square into 1 Sandstone**, or use **4 Red Sand for 1 Red Sandstone**. The recipes name the exact sand item, so do not mix their colors. This fits the inventory crafting grid. See [Sand and Red Sand](SoilSandAndGravel.md#sand-red-sand-and-gravel-falling) for collecting the loose inputs. [Sandstone recipe][r-sandstone] · [Red Sandstone recipe][r-red_sandstone]

Use an **unbroken pickaxe of any ordinary material, including Wood**, to collect all 20 forms covered here. All require a correct tool and are pickaxe-tagged, without a higher material-tier restriction. Breaking one by hand does not collect its normal block drop. [Registrations][blocks] · [Pickaxe tag][pickaxe] · [Wood tier exclusions][wood-tier] · [Tool checks][tool] [player] [broken] · [Harvest dispatch][mining]

Ordinary correct-tool mining gives **one matching full block, stair or wall**, **one slab from a single slab**, or **two slabs from a double slab**. Silk Touch is not needed and Fortune adds no multiplier. Finished blocks retain their finish: mining Chiseled Sandstone does not turn it back into ordinary Sandstone or Sand. Explosions use separate survival/quantity conditions. [All exact loot tables](#registered-forms-and-loot)

## Forms and properties

The following choices exist in **both** the ordinary and red families. A dash indicates no corresponding registered form in this family.

| Finish | Full block | Stairs | Slab | Wall |
| --- | --- | --- | --- | --- |
| Base Sandstone / Red Sandstone | Yes | Yes | Yes | Yes |
| Cut Sandstone / Cut Red Sandstone | Yes | — | Yes | — |
| Chiseled Sandstone / Chiseled Red Sandstone | Yes | — | — | — |
| Smooth Sandstone / Smooth Red Sandstone | Yes | Yes | Yes | — |

[Exact registry entries][blocks] · [ID and item list](#registered-forms-and-loot)

**Slab strength is not always the same as its full-block ingredient.** These values come from the specific registrations and copied stair/wall properties:

| Forms, in either color | Hardness | Blast resistance |
| --- | ---: | ---: |
| Base, Cut and Chiseled full blocks; base stairs and base walls | 0.8 | 0.8 |
| Smooth full blocks and Smooth stairs; every registered Sandstone slab | 2 | 6 |

Hardness is not a measured breaking time. Tool speed and conditions affect the time to mine a block. The registered note-block instrument is the bass drum. [Registrations and strength][blocks] · [Property copying][properties] · [Stair inheritance][stairs]

## Crafting finishes and shapes

These instructions apply separately to either color. Use matching full blocks and slabs; the checked recipes do not recolor one family into the other.

| Exact arrangement or process | Output |
| --- | --- |
| 4 base Sandstone in a 2 × 2 square | 4 Cut Sandstone |
| 2 **base Sandstone Slabs** stacked vertically | 1 Chiseled Sandstone |
| Smelt 1 base Sandstone | 1 Smooth Sandstone |
| 6 base or Smooth full blocks in a 1/2/3 stair pattern | 4 matching stairs |
| 3 base, Cut or Smooth full blocks in a horizontal row | 6 matching slabs |
| 6 base Sandstone in two full rows | 6 matching walls |

Use a Crafting Table for three-wide or three-tall layouts. The chiseled recipe uses the ordinary slab of that color, **not its Cut or Smooth slab**. The recipe evidence below lists every variant and input. [Ordinary chiseled input][r-chiseled_sandstone] · [Red chiseled input][r-chiseled_red_sandstone] · [Complete recipes](#recipe-evidence)

Both Smooth recipes take **200 ticks** in a Furnace and specify **0.1 recipe experience** per input, nominally 10 seconds at 20 ticks per second. They accept the base Sandstone, not Cut/Chiseled blocks. There is no blasting version for these outputs in the checked recipe set. See [Furnace](Furnace.md) for fuel and experience collection. [Smooth Sandstone][r-smooth_sandstone] · [Smooth Red Sandstone][r-smooth_red_sandstone]

## Stonecutting

A [Stonecutter](Stonecutter.md) consumes one input block per operation. The choices and yields below exist in both colors, preserving the color family. [Menu consumption][cutter] · [All recipe definitions](#recipe-evidence)

| One input | Outputs available from that input |
| --- | --- |
| Base Sandstone | 1 Cut full block, 1 Chiseled full block, 1 base stair, 1 base wall, 2 base slabs, or 2 Cut slabs |
| Cut Sandstone | 2 Cut slabs |
| Smooth Sandstone | 1 Smooth stair or 2 Smooth slabs |

Stonecutting is more material-efficient for stairs: **4 full blocks give 4 stairs**, while the crafting pattern uses 6 full blocks for 4 stairs. Slab and wall material yields are the same as crafting, but the cutter permits a smaller batch. For example, 8 Sandstone can become 8 stairs at the cutter instead of requiring 12 Sandstone through crafting. This is a source-based planning example, not a tested build. [Stair crafting][r-sandstone_stairs] · [Stair cutting][r-sandstone_stairs_from_sandstone_stonecutting]

Chiseled Sandstone is not a Stonecutter input in the checked data. There is no reverse cutting route from Cut/Smooth/Chiseled back to the base block, and no recipe in this set recovers the original loose Sand from the building block. Check the selected output before processing a large supply. [Complete checked outputs](#recipe-evidence)

## Placement and use

Full Sandstone blocks use ordinary full-block collision and have **no selectable facing or pillar axis**, including the Cut and Chiseled finishes. They do not fall when their support is removed, so they behave differently from the loose Sand used to craft them. [Registered classes][blocks] · [Default shape and support][properties]

Use the shared [stone shape controls](Stone.md#placing-shaped-blocks) for stairs, slabs and walls:

- Slabs select an upper/lower half and combine only with the identical slab item. A regular slab cannot combine with a Red, Cut or Smooth slab. Double slabs clear the waterlogged state and return two slab items when mined
- Stairs use facing/half and automatic inner/outer corners; compatible neighboring stairs may be a different material or color
- Walls connect to supported faces, other walls, bars and suitably aligned gates through their connection rules; collision rises 1.5 blocks

Single slabs, stairs and walls can be waterlogged. Their shape changes do not convert the color or finish, and they stay in place when support underneath is removed. [Slabs][slabs] · [Stairs][stairs] · [Walls][walls]

## Registered forms and loot

| Item form | Block ID | Exact loot |
| --- | --- | --- |
| [Chiseled Red Sandstone](../items/ChiseledRedSandstone.md) | `minecraft:chiseled_red_sandstone` | [Loot][loot-chiseled_red_sandstone] |
| [Chiseled Sandstone](../items/ChiseledSandstone.md) | `minecraft:chiseled_sandstone` | [Loot][loot-chiseled_sandstone] |
| [Cut Red Sandstone](../items/CutRedSandstone.md) | `minecraft:cut_red_sandstone` | [Loot][loot-cut_red_sandstone] |
| [Cut Red Sandstone Slab](../items/CutRedSandstoneSlab.md) | `minecraft:cut_red_sandstone_slab` | [Loot][loot-cut_red_sandstone_slab] |
| [Cut Sandstone](../items/CutSandstone.md) | `minecraft:cut_sandstone` | [Loot][loot-cut_sandstone] |
| [Cut Sandstone Slab](../items/CutSandstoneSlab.md) | `minecraft:cut_sandstone_slab` | [Loot][loot-cut_sandstone_slab] |
| [Red Sandstone](../items/RedSandstone.md) | `minecraft:red_sandstone` | [Loot][loot-red_sandstone] |
| [Red Sandstone Slab](../items/RedSandstoneSlab.md) | `minecraft:red_sandstone_slab` | [Loot][loot-red_sandstone_slab] |
| [Red Sandstone Stairs](../items/RedSandstoneStairs.md) | `minecraft:red_sandstone_stairs` | [Loot][loot-red_sandstone_stairs] |
| [Red Sandstone Wall](../items/RedSandstoneWall.md) | `minecraft:red_sandstone_wall` | [Loot][loot-red_sandstone_wall] |
| [Sandstone](../items/Sandstone.md) | `minecraft:sandstone` | [Loot][loot-sandstone] |
| [Sandstone Slab](../items/SandstoneSlab.md) | `minecraft:sandstone_slab` | [Loot][loot-sandstone_slab] |
| [Sandstone Stairs](../items/SandstoneStairs.md) | `minecraft:sandstone_stairs` | [Loot][loot-sandstone_stairs] |
| [Sandstone Wall](../items/SandstoneWall.md) | `minecraft:sandstone_wall` | [Loot][loot-sandstone_wall] |
| [Smooth Red Sandstone](../items/SmoothRedSandstone.md) | `minecraft:smooth_red_sandstone` | [Loot][loot-smooth_red_sandstone] |
| [Smooth Red Sandstone Slab](../items/SmoothRedSandstoneSlab.md) | `minecraft:smooth_red_sandstone_slab` | [Loot][loot-smooth_red_sandstone_slab] |
| [Smooth Red Sandstone Stairs](../items/SmoothRedSandstoneStairs.md) | `minecraft:smooth_red_sandstone_stairs` | [Loot][loot-smooth_red_sandstone_stairs] |
| [Smooth Sandstone](../items/SmoothSandstone.md) | `minecraft:smooth_sandstone` | [Loot][loot-smooth_sandstone] |
| [Smooth Sandstone Slab](../items/SmoothSandstoneSlab.md) | `minecraft:smooth_sandstone_slab` | [Loot][loot-smooth_sandstone_slab] |
| [Smooth Sandstone Stairs](../items/SmoothSandstoneStairs.md) | `minecraft:smooth_sandstone_stairs` | [Loot][loot-smooth_sandstone_stairs] |

## Recipe evidence

The following are all **38 production recipes** for the selected 20 outputs in the checked recipe tree: crafting, two smelting definitions, and stonecutting. Names alone were not used to infer unregistered shapes.

| Recipe | Type | Output count |
| --- | --- | ---: |
| [chiseled red sandstone][r-chiseled_red_sandstone] | crafting_shaped | 1 |
| [chiseled sandstone][r-chiseled_sandstone] | crafting_shaped | 1 |
| [cut red sandstone][r-cut_red_sandstone] | crafting_shaped | 4 |
| [cut red sandstone slab][r-cut_red_sandstone_slab] | crafting_shaped | 6 |
| [cut sandstone][r-cut_sandstone] | crafting_shaped | 4 |
| [cut sandstone slab][r-cut_sandstone_slab] | crafting_shaped | 6 |
| [red sandstone][r-red_sandstone] | crafting_shaped | 1 |
| [red sandstone slab][r-red_sandstone_slab] | crafting_shaped | 6 |
| [red sandstone stairs][r-red_sandstone_stairs] | crafting_shaped | 4 |
| [red sandstone wall][r-red_sandstone_wall] | crafting_shaped | 6 |
| [sandstone][r-sandstone] | crafting_shaped | 1 |
| [sandstone slab][r-sandstone_slab] | crafting_shaped | 6 |
| [sandstone stairs][r-sandstone_stairs] | crafting_shaped | 4 |
| [sandstone wall][r-sandstone_wall] | crafting_shaped | 6 |
| [smooth red sandstone slab][r-smooth_red_sandstone_slab] | crafting_shaped | 6 |
| [smooth red sandstone stairs][r-smooth_red_sandstone_stairs] | crafting_shaped | 4 |
| [smooth sandstone slab][r-smooth_sandstone_slab] | crafting_shaped | 6 |
| [smooth sandstone stairs][r-smooth_sandstone_stairs] | crafting_shaped | 4 |
| [smooth red sandstone][r-smooth_red_sandstone] | smelting | 1 |
| [smooth sandstone][r-smooth_sandstone] | smelting | 1 |
| [chiseled red sandstone from red sandstone stonecutting][r-chiseled_red_sandstone_from_red_sandstone_stonecutting] | stonecutting | 1 |
| [chiseled sandstone from sandstone stonecutting][r-chiseled_sandstone_from_sandstone_stonecutting] | stonecutting | 1 |
| [cut red sandstone from red sandstone stonecutting][r-cut_red_sandstone_from_red_sandstone_stonecutting] | stonecutting | 1 |
| [cut red sandstone slab from cut red sandstone stonecutting][r-cut_red_sandstone_slab_from_cut_red_sandstone_stonecutting] | stonecutting | 2 |
| [cut red sandstone slab from red sandstone stonecutting][r-cut_red_sandstone_slab_from_red_sandstone_stonecutting] | stonecutting | 2 |
| [cut sandstone from sandstone stonecutting][r-cut_sandstone_from_sandstone_stonecutting] | stonecutting | 1 |
| [cut sandstone slab from cut sandstone stonecutting][r-cut_sandstone_slab_from_cut_sandstone_stonecutting] | stonecutting | 2 |
| [cut sandstone slab from sandstone stonecutting][r-cut_sandstone_slab_from_sandstone_stonecutting] | stonecutting | 2 |
| [red sandstone slab from red sandstone stonecutting][r-red_sandstone_slab_from_red_sandstone_stonecutting] | stonecutting | 2 |
| [red sandstone stairs from red sandstone stonecutting][r-red_sandstone_stairs_from_red_sandstone_stonecutting] | stonecutting | 1 |
| [red sandstone wall from red sandstone stonecutting][r-red_sandstone_wall_from_red_sandstone_stonecutting] | stonecutting | 1 |
| [sandstone slab from sandstone stonecutting][r-sandstone_slab_from_sandstone_stonecutting] | stonecutting | 2 |
| [sandstone stairs from sandstone stonecutting][r-sandstone_stairs_from_sandstone_stonecutting] | stonecutting | 1 |
| [sandstone wall from sandstone stonecutting][r-sandstone_wall_from_sandstone_stonecutting] | stonecutting | 1 |
| [smooth red sandstone slab from smooth red sandstone stonecutting][r-smooth_red_sandstone_slab_from_smooth_red_sandstone_stonecutting] | stonecutting | 2 |
| [smooth red sandstone stairs from smooth red sandstone stonecutting][r-smooth_red_sandstone_stairs_from_smooth_red_sandstone_stonecutting] | stonecutting | 1 |
| [smooth sandstone slab from smooth sandstone stonecutting][r-smooth_sandstone_slab_from_smooth_sandstone_stonecutting] | stonecutting | 2 |
| [smooth sandstone stairs from smooth sandstone stonecutting][r-smooth_sandstone_stairs_from_smooth_sandstone_stonecutting] | stonecutting | 1 |

## Verification and related pages

Source-reviewed on **2026-10-02** at `1879e5f54378351fd773b9a2c10839eb9259c504`: all 20 registrations/loot tables, mining tags, property inheritance, all 1,501 recipe outputs for the scoped recipe inventory, and shared placement classes. No in-game mining, crafting, smelting, placement or waterlogging test was run. This guide provides a crafting acquisition route; it does not inventory every natural deposit, structure or trade. Data packs can change recipes and loot.

[Blocks](Blocks.md) · [Stone category](catalog/stone.md) · [Sand and Gravel](SoilSandAndGravel.md) · [Stonecutter](Stonecutter.md) · [Furnace](Furnace.md)

[blocks]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/Blocks.java
[items]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/item/Items.java
[properties]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java
[stairs]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/StairBlock.java
[slabs]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/SlabBlock.java
[walls]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/WallBlock.java
[tool]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/item/ToolMaterial.java
[player]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/player/Player.java
[mining]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java
[broken]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/item/ItemStack.java
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[wood-tier]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[cutter]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/inventory/StonecutterMenu.java
[r-chiseled_red_sandstone]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/chiseled_red_sandstone.json
[r-chiseled_sandstone]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/chiseled_sandstone.json
[r-cut_red_sandstone]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/cut_red_sandstone.json
[r-cut_red_sandstone_slab]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/cut_red_sandstone_slab.json
[r-cut_sandstone]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/cut_sandstone.json
[r-cut_sandstone_slab]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/cut_sandstone_slab.json
[r-red_sandstone]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/red_sandstone.json
[r-red_sandstone_slab]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/red_sandstone_slab.json
[r-red_sandstone_stairs]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/red_sandstone_stairs.json
[r-red_sandstone_wall]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/red_sandstone_wall.json
[r-sandstone]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/sandstone.json
[r-sandstone_slab]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/sandstone_slab.json
[r-sandstone_stairs]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/sandstone_stairs.json
[r-sandstone_wall]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/sandstone_wall.json
[r-smooth_red_sandstone_slab]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/smooth_red_sandstone_slab.json
[r-smooth_red_sandstone_stairs]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/smooth_red_sandstone_stairs.json
[r-smooth_sandstone_slab]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/smooth_sandstone_slab.json
[r-smooth_sandstone_stairs]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/smooth_sandstone_stairs.json
[r-smooth_red_sandstone]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/smelting/smooth_red_sandstone.json
[r-smooth_sandstone]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/smelting/smooth_sandstone.json
[r-chiseled_red_sandstone_from_red_sandstone_stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/chiseled_red_sandstone_from_red_sandstone_stonecutting.json
[r-chiseled_sandstone_from_sandstone_stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/chiseled_sandstone_from_sandstone_stonecutting.json
[r-cut_red_sandstone_from_red_sandstone_stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/cut_red_sandstone_from_red_sandstone_stonecutting.json
[r-cut_red_sandstone_slab_from_cut_red_sandstone_stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/cut_red_sandstone_slab_from_cut_red_sandstone_stonecutting.json
[r-cut_red_sandstone_slab_from_red_sandstone_stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/cut_red_sandstone_slab_from_red_sandstone_stonecutting.json
[r-cut_sandstone_from_sandstone_stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/cut_sandstone_from_sandstone_stonecutting.json
[r-cut_sandstone_slab_from_cut_sandstone_stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/cut_sandstone_slab_from_cut_sandstone_stonecutting.json
[r-cut_sandstone_slab_from_sandstone_stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/cut_sandstone_slab_from_sandstone_stonecutting.json
[r-red_sandstone_slab_from_red_sandstone_stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/red_sandstone_slab_from_red_sandstone_stonecutting.json
[r-red_sandstone_stairs_from_red_sandstone_stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/red_sandstone_stairs_from_red_sandstone_stonecutting.json
[r-red_sandstone_wall_from_red_sandstone_stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/red_sandstone_wall_from_red_sandstone_stonecutting.json
[r-sandstone_slab_from_sandstone_stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/sandstone_slab_from_sandstone_stonecutting.json
[r-sandstone_stairs_from_sandstone_stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/sandstone_stairs_from_sandstone_stonecutting.json
[r-sandstone_wall_from_sandstone_stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/sandstone_wall_from_sandstone_stonecutting.json
[r-smooth_red_sandstone_slab_from_smooth_red_sandstone_stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/smooth_red_sandstone_slab_from_smooth_red_sandstone_stonecutting.json
[r-smooth_red_sandstone_stairs_from_smooth_red_sandstone_stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/smooth_red_sandstone_stairs_from_smooth_red_sandstone_stonecutting.json
[r-smooth_sandstone_slab_from_smooth_sandstone_stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/smooth_sandstone_slab_from_smooth_sandstone_stonecutting.json
[r-smooth_sandstone_stairs_from_smooth_sandstone_stonecutting]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/stonecutting/smooth_sandstone_stairs_from_smooth_sandstone_stonecutting.json
[loot-sandstone]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/sandstone.json
[loot-chiseled_sandstone]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/chiseled_sandstone.json
[loot-cut_sandstone]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/cut_sandstone.json
[loot-sandstone_stairs]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/sandstone_stairs.json
[loot-red_sandstone]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/red_sandstone.json
[loot-chiseled_red_sandstone]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/chiseled_red_sandstone.json
[loot-cut_red_sandstone]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/cut_red_sandstone.json
[loot-red_sandstone_stairs]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/red_sandstone_stairs.json
[loot-sandstone_slab]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/sandstone_slab.json
[loot-cut_sandstone_slab]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/cut_sandstone_slab.json
[loot-red_sandstone_slab]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/red_sandstone_slab.json
[loot-cut_red_sandstone_slab]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/cut_red_sandstone_slab.json
[loot-smooth_sandstone]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/smooth_sandstone.json
[loot-smooth_red_sandstone]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/smooth_red_sandstone.json
[loot-smooth_red_sandstone_stairs]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/smooth_red_sandstone_stairs.json
[loot-smooth_sandstone_stairs]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/smooth_sandstone_stairs.json
[loot-smooth_red_sandstone_slab]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/smooth_red_sandstone_slab.json
[loot-smooth_sandstone_slab]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/smooth_sandstone_slab.json
[loot-red_sandstone_wall]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/red_sandstone_wall.json
[loot-sandstone_wall]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/sandstone_wall.json
