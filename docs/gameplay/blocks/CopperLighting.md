# Copper lighting

Copper Bulbs are lamps that remember their lit state between power pulses. Copper Torches are steady lights with floor and wall forms. The eight Bulb variants oxidize or can be waxed; the two Torch block forms have no oxidation chain. [Bulb registrations][bulb-reg] · [Torch registrations][torch-reg] · [Weathering map][weather]

## Copper Bulbs

### Crafting and brightness

In a Crafting Table, put a **Blaze Rod in the center**, **Redstone Dust below it**, and **3 matching full copper blocks above, left, and right**. This makes **4 matching Copper Bulbs**. The recipe exists for each oxidation stage and wax state; the copper inputs must match exactly. Each waxed Bulb also has the separate shapeless conversion **1 unwaxed Bulb + 1 Honeycomb → 1 matching waxed Bulb**. [All recipes](#exact-variants-recipes-and-loot)

| Oxidation stage | Light while lit | Light while off | Comparator output while lit |
| --- | ---: | ---: | ---: |
| Unaffected | 15 | 0 | 15 |
| Exposed | 12 | 0 | 15 |
| Weathered | 8 | 0 | 15 |
| Oxidized | 4 | 0 | 15 |

Waxing retains the stage's brightness. Scraping to an earlier stage makes a lit bulb brighter; use the shared [waxing and scraping guide](CopperConstruction.md#waxing-and-scraping). Natural oxidation follows the shared [neighbor-based rules](CopperConstruction.md#oxidation-and-spacing). Conversions copy `lit` and `powered`, and the new bulb's placement callback checks the current signal again. [Light properties][bulb-reg] · [Lit-state emission][lit-light] · [Bulb callback][bulb] · [Aging callback][bulb-aging] · [State preservation][copy-state]

### Toggle control and comparator output

A fresh bulb starts **off and unpowered**. Each transition from no detected redstone power to detected power **toggles** its light. Removing power leaves the light unchanged and rearms it for another toggle. Keeping a signal on does not repeatedly toggle it. Placing a fresh bulb into an already-powered position runs the same check and turns it on. The callback changes state directly; this review does not claim an in-game pulse-width or timing measurement. [Placement and neighbor updates][bulb]

For a simple control loop, give the bulb a pulse to turn it on, let that signal end, and give another pulse to turn it off. A [Comparator](RedstoneComparator.md) reads **15 when lit and 0 when unlit**, independently of the incoming signal strength and the bulb's oxidation brightness. The bulb is not a redstone-conducting full block, so use a Comparator when you need its remembered light state as a circuit output. [Analog output][bulb] · [Comparator input path][comparator] · [Nonconducting registration][bulb-reg] · [Signal lookup][signal]

Bulbs have full-block collision, no placement-facing state, no continuing support requirement, and no waterlogged state. Mining returns the variant, without saving its previous `lit` or `powered` state in the ordinary drop. [Bulb state definition][bulb] · [Default shape/support][default] · [Loot tables](#exact-variants-recipes-and-loot)

### Collecting Bulbs

Use an **unbroken Stone, Copper, Iron, Diamond, or Netherite Pickaxe** for the block drop. Wooden/Golden Pickaxes, hand mining, and broken pickaxes do not satisfy these blocks' bundled tool gate. They have **hardness 3 and blast resistance 6**, and ordinary loot returns one matching oxidation/wax variant; Fortune and Silk Touch add no extra quantity or alternate block result. Explosions add a survival condition. [Pickaxe targets][mineable-pickaxe] · [Tier tag][needs_stone_tool] · [Wood exclusions][incorrect_for_wooden_tool] · [Gold exclusions][incorrect_for_gold_tool] · [Tool materials][tool] · [Broken drop guard][broken-drop] · [Harvest gate][harvest] · [Active harvest][harvest-call] [Bulb properties][bulb-reg]

## Copper Torches

The inventory item `minecraft:copper_torch` places either `minecraft:copper_torch` on a floor or `minecraft:copper_wall_torch` on a wall. There is no separate Wall Torch item. Craft **1 Copper Nugget above 1 Coal or Charcoal above 1 Stick** in a vertical column for **4 Copper Torches**; this needs a Crafting Table. [Exact recipe][r-copper_torch] · [Item registration][torch-item] · [Floor/wall item routing][standing-wall]

Both forms emit **light level 14**, without redstone input or a fuel supply. Their flame is the registered copper-flame particle. They are not Redstone Torches and have no powered/lit toggle state or oxidation/wax conversion in these handlers. [Light and particle registration][torch-reg] · [Torch behavior][torch] · [Wall Torch behavior][wall-torch] · [Default signal behavior][default-signal] · [Weathering map][weather] · [Wax map][wax]

A standing Torch needs center support on the upper face below. A Wall Torch needs a sturdy side face behind it. Removing that support makes the torch break; these forms have no waterlogging property. Both are instant-break, non-colliding blocks and ordinarily drop **1 Copper Torch**, with no tool tier, Silk Touch, or Fortune requirement. The wall form reuses the standing Torch's loot table. [Floor support][torch-base] · [Wall support][wall-torch] · [Properties][torch-reg] · [Wall loot inheritance][wall-loot] · [Torch loot][loot-copper_torch] · [Support removal][support-removal] · [Removal drops][removed-drops]

## Exact variants, recipes, and loot

| Registry ID | Recipes | Block loot |
| --- | --- | --- |
| `minecraft:copper_bulb` | [Craft][r-copper_bulb] | [Loot][loot-copper_bulb] |
| `minecraft:copper_torch` | [Craft][r-copper_torch] | [Loot][loot-copper_torch] |
| `minecraft:copper_wall_torch` | No direct production recipe | [Loot][loot-copper_wall_torch] |
| `minecraft:exposed_copper_bulb` | [Craft][r-exposed_copper_bulb] | [Loot][loot-exposed_copper_bulb] |
| `minecraft:oxidized_copper_bulb` | [Craft][r-oxidized_copper_bulb] | [Loot][loot-oxidized_copper_bulb] |
| `minecraft:waxed_copper_bulb` | [Craft][r-waxed_copper_bulb] · [Wax][r-waxed_copper_bulb_from_honeycomb] | [Loot][loot-waxed_copper_bulb] |
| `minecraft:waxed_exposed_copper_bulb` | [Craft][r-waxed_exposed_copper_bulb] · [Wax][r-waxed_exposed_copper_bulb_from_honeycomb] | [Loot][loot-waxed_exposed_copper_bulb] |
| `minecraft:waxed_oxidized_copper_bulb` | [Wax][r-waxed_oxidized_copper_bulb_from_honeycomb] · [Craft][r-waxed_oxidized_copper_bulb] | [Loot][loot-waxed_oxidized_copper_bulb] |
| `minecraft:waxed_weathered_copper_bulb` | [Craft][r-waxed_weathered_copper_bulb] · [Wax][r-waxed_weathered_copper_bulb_from_honeycomb] | [Loot][loot-waxed_weathered_copper_bulb] |
| `minecraft:weathered_copper_bulb` | [Craft][r-weathered_copper_bulb] | [Loot][loot-weathered_copper_bulb] |

## Sources and verification

Source-reviewed on **2026-10-02** at `1879e5f54378351fd773b9a2c10839eb9259c504`. All 10 block registrations, 13 producing recipes, 9 unique loot tables, relevant tool tags, bulb power/comparator callbacks, and torch placement were checked. No in-game test was run. Recipes, tags, loot, server rules, and later code changes can alter these results.

Related: [Blocks](Blocks.md) · [Copper construction](CopperConstruction.md) · [Copper catalog](catalog/copper.md) · [Items](../items/Items.md)

[bulb-reg]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/Blocks.java#L6355-L6390
[torch-reg]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/Blocks.java#L2043-L2051
[bulb]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/CopperBulbBlock.java#L18-L73
[bulb-aging]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/WeatheringCopperBulbBlock.java#L23-L40
[lit-light]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/Blocks.java#L7128-L7130
[comparator]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/ComparatorBlock.java#L97-L119
[signal]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/SignalGetter.java#L65-L81
[default]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L326
[default-signal]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L202-L229
[harvest]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[harvest-call]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[broken-drop]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/item/ItemStack.java#L586-L589
[tool]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L48
[wax]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/item/HoneycombItem.java#L24-L113
[weather]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/WeatheringCopper.java#L17-L103
[copy-state]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/Block.java#L512-L525
[torch]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/TorchBlock.java
[torch-base]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/BaseTorchBlock.java
[wall-torch]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/WallTorchBlock.java
[torch-item]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/item/Items.java#L526-L529
[standing-wall]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/item/StandingAndWallBlockItem.java#L13-L52
[wall-loot]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/Blocks.java#L7264-L7272
[support-removal]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/Block.java#L213-L226
[removed-drops]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/Level.java#L263-L283
[mineable-pickaxe]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[needs_stone_tool]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json
[incorrect_for_wooden_tool]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[incorrect_for_gold_tool]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/incorrect_for_gold_tool.json
[loot-copper_bulb]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/copper_bulb.json
[r-copper_bulb]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/copper_bulb.json
[loot-copper_torch]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/copper_torch.json
[r-copper_torch]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/copper_torch.json
[loot-copper_wall_torch]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/copper_torch.json
[loot-exposed_copper_bulb]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/exposed_copper_bulb.json
[r-exposed_copper_bulb]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/exposed_copper_bulb.json
[loot-oxidized_copper_bulb]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/oxidized_copper_bulb.json
[r-oxidized_copper_bulb]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/oxidized_copper_bulb.json
[loot-waxed_copper_bulb]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/waxed_copper_bulb.json
[r-waxed_copper_bulb]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/waxed_copper_bulb.json
[r-waxed_copper_bulb_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/waxed_copper_bulb_from_honeycomb.json
[loot-waxed_exposed_copper_bulb]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/waxed_exposed_copper_bulb.json
[r-waxed_exposed_copper_bulb]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/waxed_exposed_copper_bulb.json
[r-waxed_exposed_copper_bulb_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/waxed_exposed_copper_bulb_from_honeycomb.json
[loot-waxed_oxidized_copper_bulb]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/waxed_oxidized_copper_bulb.json
[r-waxed_oxidized_copper_bulb_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/waxed_oxidized_copper_bulb_from_honeycomb.json
[r-waxed_oxidized_copper_bulb]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/waxed_oxidized_copper_bulb.json
[loot-waxed_weathered_copper_bulb]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/waxed_weathered_copper_bulb.json
[r-waxed_weathered_copper_bulb]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_copper_bulb.json
[r-waxed_weathered_copper_bulb_from_honeycomb]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_copper_bulb_from_honeycomb.json
[loot-weathered_copper_bulb]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/weathered_copper_bulb.json
[r-weathered_copper_bulb]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/weathered_copper_bulb.json
