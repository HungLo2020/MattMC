# Coral

Build a colorful reef with **living Coral Blocks, Coral plants and Coral Fans**, or use their dead forms for dry decoration. Bring an **unbroken Silk Touch pickaxe** to collect every form safely, and prepare water and structural support before placing living pieces. [Registrations][blocks] · [Tool and loot rules](#harvesting-coral)

## Five species and their registered forms

MattMC registers **40 coral blocks**: five species, each with living/dead full blocks, plants, floor fans and wall fans. They use **30 item forms** because each wall fan shares its floor fan's item and loot table. All IDs below use the `minecraft:` namespace; there is no separate wall-fan inventory item. [Blocks][blocks] · [Items][items] · [Wall loot mapping][wall-loot] · [Shared fan item][fan-item]

### Tube coral

Living color: **blue**.

| Form | Living ID | Dead ID |
| --- | --- | --- |
| Full block | [`tube_coral_block`](../items/TubeCoralBlock.md) | [`dead_tube_coral_block`](../items/DeadTubeCoralBlock.md) |
| Plant | [`tube_coral`](../items/TubeCoral.md) | [`dead_tube_coral`](../items/DeadTubeCoral.md) |
| Floor fan | [`tube_coral_fan`](../items/TubeCoralFan.md) | [`dead_tube_coral_fan`](../items/DeadTubeCoralFan.md) |
| Wall fan | `tube_coral_wall_fan` | `dead_tube_coral_wall_fan` |

### Brain coral

Living color: **pink**.

| Form | Living ID | Dead ID |
| --- | --- | --- |
| Full block | [`brain_coral_block`](../items/BrainCoralBlock.md) | [`dead_brain_coral_block`](../items/DeadBrainCoralBlock.md) |
| Plant | [`brain_coral`](../items/BrainCoral.md) | [`dead_brain_coral`](../items/DeadBrainCoral.md) |
| Floor fan | [`brain_coral_fan`](../items/BrainCoralFan.md) | [`dead_brain_coral_fan`](../items/DeadBrainCoralFan.md) |
| Wall fan | `brain_coral_wall_fan` | `dead_brain_coral_wall_fan` |

### Bubble coral

Living color: **purple**.

| Form | Living ID | Dead ID |
| --- | --- | --- |
| Full block | [`bubble_coral_block`](../items/BubbleCoralBlock.md) | [`dead_bubble_coral_block`](../items/DeadBubbleCoralBlock.md) |
| Plant | [`bubble_coral`](../items/BubbleCoral.md) | [`dead_bubble_coral`](../items/DeadBubbleCoral.md) |
| Floor fan | [`bubble_coral_fan`](../items/BubbleCoralFan.md) | [`dead_bubble_coral_fan`](../items/DeadBubbleCoralFan.md) |
| Wall fan | `bubble_coral_wall_fan` | `dead_bubble_coral_wall_fan` |

### Fire coral

Living color: **red**.

| Form | Living ID | Dead ID |
| --- | --- | --- |
| Full block | [`fire_coral_block`](../items/FireCoralBlock.md) | [`dead_fire_coral_block`](../items/DeadFireCoralBlock.md) |
| Plant | [`fire_coral`](../items/FireCoral.md) | [`dead_fire_coral`](../items/DeadFireCoral.md) |
| Floor fan | [`fire_coral_fan`](../items/FireCoralFan.md) | [`dead_fire_coral_fan`](../items/DeadFireCoralFan.md) |
| Wall fan | `fire_coral_wall_fan` | `dead_fire_coral_wall_fan` |

### Horn coral

Living color: **yellow**.

| Form | Living ID | Dead ID |
| --- | --- | --- |
| Full block | [`horn_coral_block`](../items/HornCoralBlock.md) | [`dead_horn_coral_block`](../items/DeadHornCoralBlock.md) |
| Plant | [`horn_coral`](../items/HornCoral.md) | [`dead_horn_coral`](../items/DeadHornCoral.md) |
| Floor fan | [`horn_coral_fan`](../items/HornCoralFan.md) | [`dead_horn_coral_fan`](../items/DeadHornCoralFan.md) |
| Wall fan | `horn_coral_wall_fan` | `dead_horn_coral_wall_fan` |

Species change color and the corresponding dead/drop IDs, not the water, support or tool rules described below. [Species class assignments][blocks]

## Harvesting coral

These are ordinary Survival harvesting results with `doTileDrops` enabled, its default. Where a pickaxe is required, **an unbroken Wood pickaxe already meets the material tier**; higher tiers are unnecessary for the drop check. Fortune does not multiply coral drops or replace Silk Touch. [Drop dispatch][block] [rules] · [Correct-tool gates][blocks] [player-tool][] [broken-tool][] [break-dispatch] · [Pickaxe and tier tags][pickaxe] [wood-denials][] [stone-tier][] [iron-tier][] [diamond-tier][] [tool-material]

| What is placed | Tool needed to recover it | What happens without the preservation tool |
| --- | --- | --- |
| Living full Coral Block | Pickaxe with Silk Touch → 1 living block | Correct pickaxe without Silk Touch → 1 matching dead full block; wrong tool → nothing |
| Dead full Coral Block | Any correct pickaxe → 1 matching dead block | Wrong tool → nothing; Silk Touch is unnecessary |
| Living plant or fan, including wall fan | Silk Touch → 1 matching plant/fan item; no correct-tool tier gate | No Silk Touch → nothing |
| Dead plant or fan, including wall fan | Correct pickaxe **and** Silk Touch → 1 matching dead plant/fan item | Missing either requirement → nothing |

Living full-block loot: [Tube][loot-tube-coral-block] [Brain][loot-brain-coral-block] [Bubble][loot-bubble-coral-block] [Fire][loot-fire-coral-block] [Horn][loot-horn-coral-block] · Dead full-block loot: [Tube][loot-dead-tube-coral-block] [Brain][loot-dead-brain-coral-block] [Bubble][loot-dead-bubble-coral-block] [Fire][loot-dead-fire-coral-block] [Horn][loot-dead-horn-coral-block]

Living plant loot: [Tube][loot-tube-coral] [Brain][loot-brain-coral] [Bubble][loot-bubble-coral] [Fire][loot-fire-coral] [Horn][loot-horn-coral] · Dead plant loot: [Tube][loot-dead-tube-coral] [Brain][loot-dead-brain-coral] [Bubble][loot-dead-bubble-coral] [Fire][loot-dead-fire-coral] [Horn][loot-dead-horn-coral]

Living fan loot: [Tube][loot-tube-coral-fan] [Brain][loot-brain-coral-fan] [Bubble][loot-bubble-coral-fan] [Fire][loot-fire-coral-fan] [Horn][loot-horn-coral-fan] · Dead fan loot: [Tube][loot-dead-tube-coral-fan] [Brain][loot-dead-brain-coral-fan] [Bubble][loot-dead-bubble-coral-fan] [Fire][loot-dead-fire-coral-fan] [Horn][loot-dead-horn-coral-fan]

**Shears alone do not collect coral plants or fans.** Their loot explicitly checks Silk Touch. The dead small forms additionally have the correct-tool flag and belong to the pickaxe tag, unlike the living small forms. A fan collected from a wall becomes the same item used for its floor form. [Registration/tool distinction][blocks] [pickaxe] · [Shared wall loot and item][wall-loot] [fan-item]

Removing a plant/fan's supporting block is not a substitute for Silk Touch mining. Support-loss removal supplies an empty tool to the loot path, so those Silk Touch-only tables do not return a coral item. Collect the coral first. Explosions use their own loot conditions and are not the harvesting results in the table. [Support removal][plant-base] [wall-base] · [Removal/drop dispatch][block]

## Placement and structural support

Full Coral Blocks, living or dead, are full collision blocks with **hardness 1.5 and blast resistance 6**. They have no gravity or plant-style support requirement. Plants and both fan forms have no entity collision and are registered to break instantly, while retaining the harvesting rules above. [Properties and classes][blocks] [properties]

An upright plant or floor fan needs a **sturdy upper face directly below**. It is not restricted to Sand or another particular seabed material. A wall fan needs a **sturdy face behind it**, faces outward horizontally and has no ceiling-attached counterpart. The shared fan item chooses a valid floor or wall placement from the placement directions; use the top of a support for a floor fan and a side for a wall fan. Losing the required support breaks the small coral whether it is living or dead. [Floor support][plant-base] · [Wall support/facing][wall-base] · [Fan item selection][fan-item] · [Placement checks][placement] [support]

## Keeping living coral alive

Living coral checks **water, not biome temperature or brightness**:

- A full Coral Block needs water-tagged fluid in at least **one of the six adjacent block positions**
- A plant or fan can instead survive through its own **waterlogged** state; if it is not waterlogged, the same six-neighbor water test applies
- The water tag contains both **water and flowing water**. A waterlogged neighboring block can also supply the adjacent fluid
- Diagonal water and rain alone do not satisfy this scan

A full Coral Block has no waterlogged state. Placing a plant/fan in water sets its waterlogged state when the fluid is water-tagged and its **amount is 8**. Shallow flowing water does not meet that placement condition, although adjacent flowing water can still pass the life check. Using water-source blocks is a straightforward way to prepare a display. [Full-block scan][coral-block] · [Plant/fan water state and scan][plant-base] · [Fluid tag][water-tag]

Plants and fans use the standard waterlogging interface: a Water Bucket can add water, and an empty Bucket can take their stored water back out. That pickup collects the **water**, not the coral item. Drying a living piece can start its death check, so keep another qualifying water neighbor if removing its stored water. [Waterlogging/pickup][waterlogged] · [Bucket dispatch][bucket] · [State-change placement callback][chunk-placement] [living-plant][] [living-fan][] [living-wall]

### Drying and dead forms

When placement or a neighbor update detects dry living coral, it schedules a check **60–99 game ticks** later, nominally about **3–5 seconds at 20 ticks per second**. The scheduled callback checks water again: restoring qualifying water before it runs can save the coral. Pending scheduled checks are not a promise of a fresh full delay after every later change. These callbacks are scheduled ticks, so **`randomTickSpeed = 0` does not prevent coral death**. [Full-block scheduling][coral-block] · [Plant/fan scheduling][living-plant] [living-fan][] [living-wall][] · [Active tick dispatch and queue][scheduled-tick] [tick-queue]

If still dry, it becomes the **same species and same shape's dead block**. Small forms become non-waterlogged; a wall fan keeps its facing. Dead coral does not require water to remain dead, although dead plants/fans still require structural support and can be waterlogged. [Death transitions][coral-block] [living-plant][] [living-fan][] [living-wall] · [Dead classes][dead-plant] [dead-fan][] [wall-base][]

**Adding water or Bone Meal does not revive dead coral.** Living and dead forms are separate block IDs, and the registered dead classes have no revival callback. Bone Meal can create new living small coral in suitable water through the separate route below. No bundled coral crafting or revival recipe was found, and there is no recipe that packs plants/fans into a full Coral Block. [Dead registrations][blocks] · [Dead/base behavior][dead-plant] [dead-fan][] [wall-base][] [properties] · [Bone Meal path][bonemeal] · [Recipe loading][recipes]

## Finding coral and obtaining more

### Warm Ocean reefs

The verified normal-world reef route is **Warm Ocean**. Its `warm_ocean_vegetation` feature uses the ocean-floor heightmap and chooses among registered coral-tree, coral-claw and coral-mushroom generators. The shared generator selects full blocks from all five living species and can decorate them with living plants/fans and wall fans. Water and local placement checks still control the resulting formations. See [Ocean biomes](../biomes/Oceans.md#vegetation-and-resources) for the broader ocean selection. [Normal preset and biome selection][normal-preset] [biome-parameters][] [overworld-biomes][] · [Warm Ocean feature chain][warm-ocean] [reef-placement][] [reef-config][] [features][] [reef-selector][] [reef-tree][] [reef-claw][] [reef-mushroom][] [reef-feature][] · [Block selections][coral-blocks-tag] [corals-tag][] [wall-corals-tag][] · [Generation dispatch][biome-generation] [placed-feature]

The Normal preset does not select MattMC's bundled Primordial Ocean biome. Standalone coral feature resources also do not establish an additional generated destination. The [ocean integration notes](../biomes/Oceans.md#integrated-ocean-content-is-separate) explain that distinction.

### Bone Meal for plants and fans

In **Warm Ocean**, use Bone Meal on a **sturdy block face bordering a full water block**. For example, click the seabed's upper face with full water immediately above it. The active underwater-growth path starts only at a water block with fluid amount **8**, then makes up to **128 placement attempts** around it. Each attempted coral position must pass the coral-producing biome tag and the selected block's support check. This is not a guarantee of 128 plants or a particular species. [Bone Meal entry and attempts][bonemeal] · [Eligible biome tag][bonemeal-biomes]

The bundled biome tag contains **only Warm Ocean**. Candidate outputs include Seagrass and living coral plants/fans; a horizontal face can also select a wall fan. The underwater-growth tags contain all five living small-coral species and **no full Coral Blocks or dead forms**. This provides a renewable small-coral route without requiring a coral starter, but does not reproduce a full reef block. Bone Meal can be consumed even when the nearby attempts produce no desired coral. Harvest the resulting coral with the appropriate Silk Touch tool. [Output tags][bonemeal-blocks] [corals-tag][] [coral-plants-tag][] [wall-corals-tag] · [Selection, placement and consumption][bonemeal]

### Wandering Trader full blocks

A Wandering Trader can offer any of the five **living full Coral Blocks** at a base price of **3 Emeralds for 1 block**, with **8 uses** on that offer. These are candidates in the randomly selected trade pool, so a particular trader need not offer coral or the species you want. Repeated trader offers provide another full-block source; no Bone Meal growth of full blocks is established. [Coral trade entries][trades] · [Price/count/use construction][trade-values] · [Active trader selection][trader] [trade-selection]

## A small reef display

**Source-based example, not tested in gameplay:** prepare a shallow water-source pool with a solid floor and one solid side wall. Place a living full Coral Block with water touching a side. Add a plant or floor fan on the solid floor, and use the wall face for a wall fan. Keep the small pieces waterlogged. Use an unbroken Silk Touch pickaxe to move any piece later, collecting plants/fans before their supports.

For a dry display, let living pieces turn into their dead counterparts, then collect them using the dead-form tool rules. Water added afterward will not restore their color.

## Related pages

- [Ocean biomes](../biomes/Oceans.md), [Seagrass](Seagrass.md), and [Sea Pickles](SeaPickle.md)
- [Tube Coral Block](../items/TubeCoralBlock.md), [Tube Coral](../items/TubeCoral.md), [Tube Coral Fan](../items/TubeCoralFan.md), and [Dead Tube Coral](../items/DeadTubeCoral.md)
- [Enchanting](../enchanting/Enchanting.md), [Blocks](Blocks.md), and [Items](../items/Items.md)

## Sources and verification

Source-reviewed at `7af3a1594956f41ed57c3bf67d11ce006e61530f` on 2026-10-02. All 40 registrations, 30 shared/direct loot tables, tool tags, item-wall mappings, placement/water/death callbacks, recipes, active Warm Ocean generation, underwater Bone Meal and trader offers were checked. No gameplay harvesting, reef generation, death/revival, Bone Meal or trading test was run.

[blocks]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/Blocks.java#L4830-L5164
[items]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/Items.java#L882-L953
[wall-loot]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/Blocks.java#L7264-L7272
[fan-item]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/StandingAndWallBlockItem.java
[block]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/Block.java
[rules]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/GameRules.java
[player-tool]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[broken-tool]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[break-dispatch]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L297
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[wood-denials]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[stone-tier]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json
[iron-tier]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json
[diamond-tier]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/tags/block/needs_diamond_tool.json
[tool-material]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/ToolMaterial.java#L21-L48
[loot-tube-coral-block]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/tube_coral_block.json
[loot-brain-coral-block]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/brain_coral_block.json
[loot-bubble-coral-block]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/bubble_coral_block.json
[loot-fire-coral-block]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/fire_coral_block.json
[loot-horn-coral-block]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/horn_coral_block.json
[loot-dead-tube-coral-block]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/dead_tube_coral_block.json
[loot-dead-brain-coral-block]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/dead_brain_coral_block.json
[loot-dead-bubble-coral-block]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/dead_bubble_coral_block.json
[loot-dead-fire-coral-block]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/dead_fire_coral_block.json
[loot-dead-horn-coral-block]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/dead_horn_coral_block.json
[loot-tube-coral]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/tube_coral.json
[loot-brain-coral]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/brain_coral.json
[loot-bubble-coral]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/bubble_coral.json
[loot-fire-coral]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/fire_coral.json
[loot-horn-coral]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/horn_coral.json
[loot-dead-tube-coral]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/dead_tube_coral.json
[loot-dead-brain-coral]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/dead_brain_coral.json
[loot-dead-bubble-coral]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/dead_bubble_coral.json
[loot-dead-fire-coral]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/dead_fire_coral.json
[loot-dead-horn-coral]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/dead_horn_coral.json
[loot-tube-coral-fan]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/tube_coral_fan.json
[loot-brain-coral-fan]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/brain_coral_fan.json
[loot-bubble-coral-fan]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/bubble_coral_fan.json
[loot-fire-coral-fan]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/fire_coral_fan.json
[loot-horn-coral-fan]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/horn_coral_fan.json
[loot-dead-tube-coral-fan]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/dead_tube_coral_fan.json
[loot-dead-brain-coral-fan]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/dead_brain_coral_fan.json
[loot-dead-bubble-coral-fan]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/dead_bubble_coral_fan.json
[loot-dead-fire-coral-fan]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/dead_fire_coral_fan.json
[loot-dead-horn-coral-fan]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/loot_table/blocks/dead_horn_coral_fan.json
[plant-base]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/BaseCoralPlantTypeBlock.java
[wall-base]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/BaseCoralWallFanBlock.java
[properties]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java
[placement]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/BlockItem.java
[support]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/SupportType.java
[coral-block]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/CoralBlock.java
[water-tag]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/tags/fluid/water.json
[waterlogged]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/SimpleWaterloggedBlock.java
[bucket]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/BucketItem.java
[chunk-placement]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java#L327-L334
[living-plant]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/CoralPlantBlock.java
[living-fan]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/CoralFanBlock.java
[living-wall]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/CoralWallFanBlock.java
[scheduled-tick]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/server/level/ServerLevel.java#L766-L771
[tick-queue]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/ticks/LevelChunkTicks.java#L54-L71
[dead-plant]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/BaseCoralPlantBlock.java
[dead-fan]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/block/BaseCoralFanBlock.java
[bonemeal]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/BoneMealItem.java#L32-L147
[recipes]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L72-L88
[normal-preset]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[biome-parameters]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java
[overworld-biomes]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java
[warm-ocean]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/worldgen/biome/warm_ocean.json
[reef-placement]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/worldgen/placed_feature/warm_ocean_vegetation.json
[reef-config]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/worldgen/configured_feature/warm_ocean_vegetation.json
[features]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java
[reef-selector]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/levelgen/feature/SimpleRandomSelectorFeature.java
[reef-tree]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/levelgen/feature/CoralTreeFeature.java
[reef-claw]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/levelgen/feature/CoralClawFeature.java
[reef-mushroom]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/levelgen/feature/CoralMushroomFeature.java
[reef-feature]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/levelgen/feature/CoralFeature.java
[coral-blocks-tag]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/tags/block/coral_blocks.json
[corals-tag]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/tags/block/corals.json
[wall-corals-tag]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/tags/block/wall_corals.json
[biome-generation]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L350-L389
[placed-feature]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/level/levelgen/placement/PlacedFeature.java
[bonemeal-biomes]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/tags/worldgen/biome/produces_corals_from_bonemeal.json
[bonemeal-blocks]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/tags/block/underwater_bonemeals.json
[coral-plants-tag]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/resources/data/minecraft/tags/block/coral_plants.json
[trades]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L750-L831
[trade-values]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1422-L1480
[trader]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java#L133-L142
[trade-selection]: https://github.com/HungLo2020/MattMC/blob/7af3a1594956f41ed57c3bf67d11ce006e61530f/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L240
