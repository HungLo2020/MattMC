# Concrete and Concrete Powder

**Concrete Powder** is a falling building material made with sand, gravel, and dye. Water contact turns it into **Concrete of the same color**, a full building block that stays in place without support. Plan to switch tools when hardening powder: a shovel is efficient for powder, but collecting hardened Concrete requires an **unbroken pickaxe**. [Powder registrations and targets][powder-blocks] · [Concrete registrations][concrete-blocks] · [Conversion][hardening] · [Pickaxe tag][pickaxe] · [Shovel tag][shovel]

## Colors and item IDs

All 16 colors have a powder and hardened form. Each block and its item share the listed ID; each powder page has its exact dye recipe. Every registered powder converts to the corresponding concrete in the same row. [Block registrations][powder-blocks] · [Item registrations][items]

| Color | Powder block and item | Hardened block and item |
| --- | --- | --- |
| White | [`minecraft:white_concrete_powder`](../items/WhiteConcretePowder.md) | [`minecraft:white_concrete`](../items/WhiteConcrete.md) |
| Orange | [`minecraft:orange_concrete_powder`](../items/OrangeConcretePowder.md) | [`minecraft:orange_concrete`](../items/OrangeConcrete.md) |
| Magenta | [`minecraft:magenta_concrete_powder`](../items/MagentaConcretePowder.md) | [`minecraft:magenta_concrete`](../items/MagentaConcrete.md) |
| Light Blue | [`minecraft:light_blue_concrete_powder`](../items/LightBlueConcretePowder.md) | [`minecraft:light_blue_concrete`](../items/LightBlueConcrete.md) |
| Yellow | [`minecraft:yellow_concrete_powder`](../items/YellowConcretePowder.md) | [`minecraft:yellow_concrete`](../items/YellowConcrete.md) |
| Lime | [`minecraft:lime_concrete_powder`](../items/LimeConcretePowder.md) | [`minecraft:lime_concrete`](../items/LimeConcrete.md) |
| Pink | [`minecraft:pink_concrete_powder`](../items/PinkConcretePowder.md) | [`minecraft:pink_concrete`](../items/PinkConcrete.md) |
| Gray | [`minecraft:gray_concrete_powder`](../items/GrayConcretePowder.md) | [`minecraft:gray_concrete`](../items/GrayConcrete.md) |
| Light Gray | [`minecraft:light_gray_concrete_powder`](../items/LightGrayConcretePowder.md) | [`minecraft:light_gray_concrete`](../items/LightGrayConcrete.md) |
| Cyan | [`minecraft:cyan_concrete_powder`](../items/CyanConcretePowder.md) | [`minecraft:cyan_concrete`](../items/CyanConcrete.md) |
| Purple | [`minecraft:purple_concrete_powder`](../items/PurpleConcretePowder.md) | [`minecraft:purple_concrete`](../items/PurpleConcrete.md) |
| Blue | [`minecraft:blue_concrete_powder`](../items/BlueConcretePowder.md) | [`minecraft:blue_concrete`](../items/BlueConcrete.md) |
| Brown | [`minecraft:brown_concrete_powder`](../items/BrownConcretePowder.md) | [`minecraft:brown_concrete`](../items/BrownConcrete.md) |
| Green | [`minecraft:green_concrete_powder`](../items/GreenConcretePowder.md) | [`minecraft:green_concrete`](../items/GreenConcrete.md) |
| Red | [`minecraft:red_concrete_powder`](../items/RedConcretePowder.md) | [`minecraft:red_concrete`](../items/RedConcrete.md) |
| Black | [`minecraft:black_concrete_powder`](../items/BlackConcretePowder.md) | [`minecraft:black_concrete`](../items/BlackConcrete.md) |

## Crafting and color choices

Each powder recipe combines **four ordinary [Sand](../items/Sand.md), four [Gravel](../items/Gravel.md), and one matching-color dye** to make **eight Concrete Powder of that color**. Use a [Crafting Table](CraftingTable.md): the recipe is shapeless but occupies all nine crafting slots. Each powder page above identifies its exact dye and recipe source. **Red Sand is excluded**: the recipes name `minecraft:sand` directly rather than accepting a general sand tag. [Example white recipe][white-recipe] · [Example red recipe][red-recipe]

Choose the color before crafting. The bundled recipe files do not provide a way to recolor already crafted concrete or powder, or to craft hardened concrete directly. Hardening is a world interaction with water, not a furnace recipe. All 16 color recipes and their result IDs were checked. [Powder recipes](#colors-and-item-ids) · [Hardening implementation][hardening]

## Hardening powder with water

A practical source-supported conversion setup is a row of supported powder positions **beside water at the same height**:

1. Contain the water so it can touch one side of the target block
2. Place powder in that adjacent position; qualifying water contact makes the placed block concrete immediately
3. Mine the concrete with an unbroken pickaxe, then place the next powder block

The side-contact conversion changes the powder position, so the neighboring water remains available for further blocks. This describes the checked placement path; the arrangement has not been tested in-game. [Placement and target replacement][hardening] · [Block-item placement][placement]

Both **water sources and flowing water** are in the bundled water-fluid tag. The checked interactions are:

- **Placement into water:** the powder's placement callback can return concrete directly when the target already contains water
- **Water above or beside dry powder:** placement and neighbor updates check for qualifying adjacent water. Diagonal water alone does not count
- **Waterlogged neighbors:** their water can qualify, but the facing side must pass the code's non-sturdy-face check; do not assume every waterlogged block exposes a qualifying face
- **Water underneath:** a dry placed block's adjacency check skips the direction straight down. Powder can still fall into water below it and harden through the falling-block route
- **Falling into water:** the active falling entity detects water at its position and can settle there without ordinary support below; successful placement then calls the powder's hardening callback

[Water tag][water] · [Placement, contact, and update callbacks][hardening] · [Direction order used by the contact loop][directions] · [Falling water detection and landing][falling-water]

Rain by itself does not run a powder-hardening conversion. Use actual placed water for the checked route. Lava is not in the water tag, so it is not a substitute. [Water test][hardening] · [Inherited precipitation callback][rain] · [Water tag][water]

## Falling and support

Dry powder inherits the active falling-block behavior. Placement and neighbor changes schedule a check after **two game ticks**. It becomes a falling entity when the block below is air, fire-tagged, liquid, or replaceable. Use a reliable solid support for dry powder builds. [Falling checks and delay][falling]

A dry falling powder block can settle when its destination can be replaced, the block can survive there, and support permits it. If it cannot settle, the ordinary failed-landing path can drop a powder item when entity drops are enabled. This is not a guaranteed hardened-concrete drop; water conversion occurs on the successful landing path. [Landing and item-drop conditions][falling-land]

Hardened concrete uses the ordinary full-block shape and has no continuing support requirement. Removing the block underneath does not make it fall back into powder. The powder and concrete remain different block/item forms. [Concrete registration][concrete-blocks] · [Default shape and survival][base-block]

## Mining and drops

| Form | Useful tool | Ordinary Survival drop | Registered hardness |
| --- | --- | --- | ---: |
| Concrete Powder | Shovel is faster; a hand can collect it | **1 matching-color powder** | 0.5 |
| Concrete | **Unbroken pickaxe required**; Wooden Pickaxe is sufficient | **1 matching-color concrete** | 1.8 |

All 16 hardened colors are in the pickaxe-mining tag and are registered as requiring the correct tool for drops. Powder has no such collection requirement; its family tag is included in the shovel-mining tag. A hand or shovel breaks hardened concrete without satisfying its drop gate. A broken pickaxe also fails the current correct-tool check. [Registrations][concrete-blocks] · [Powder properties][powder-blocks] · [Pickaxe tag][pickaxe] · [Powder tag][powder-tag] · [Shovel tag][shovel] · [Player drop gate][player-drops] · [Broken-tool rule][broken-tool]

The Wooden Pickaxe is sufficient because none of these concrete blocks are in its expanded incorrect-tool tags. Tool speed and valid-drop rules are applied through the active tool components. See [Mining](../mechanics/Mining.md) and [Durability](../mechanics/Durability.md) for broader tool guidance. [Wooden restrictions][wood-incorrect] · [Tool-material rules][tools] · [Tool tag binding][tool-properties] · [Wooden Pickaxe registration][wood-pickaxe]

Neither family's block loot has a Silk Touch condition or Fortune count multiplier. Both have an **explosion-survival condition**, so explosion recovery is not guaranteed. Once powder has hardened, mining uses the concrete form and its pickaxe requirement; Silk Touch does not turn concrete back into powder. Every color's item page links its own loot table. [Example powder loot][white-powder-loot] · [Example concrete loot][white-concrete-loot] · [Normal block-breaking dispatch][break-dispatch]

Related: [Sand](../items/Sand.md) · [Gravel](../items/Gravel.md) · [Water Bucket](../items/WaterBucket.md) · [Crafting](../crafting/Crafting.md) · [Blocks](Blocks.md) · [Items](../items/Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Checked all 32 block/item registrations and loot tables, all 16 powder recipes and matching hardening targets, nested mining/tool-tier tags, and current placement, neighbor-update, scheduled falling, landing, and collection callbacks. All bundled recipe JSON files were scanned for these outputs before describing recipe limits. This guide does not inventory structure or merchant sources. No in-game crafting, water conversion, falling, mining, or automated-farm test was run. Data packs can change recipes, tags, and loot; game rules affect item drops.

[powder-blocks]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L4648-L4727
[concrete-blocks]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L4590-L4647
[hardening]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/ConcretePowderBlock.java#L38-L94
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[shovel]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/mineable/shovel.json
[items]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L839-L870
[white-recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/white_concrete_powder.json
[red-recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/red_concrete_powder.json
[placement]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/BlockItem.java#L47-L142
[water]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/fluid/water.json
[directions]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/core/Direction.java#L32-L39
[falling-water]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/item/FallingBlockEntity.java#L158-L198
[rain]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Block.java#L490-L491
[falling]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/FallingBlock.java#L27-L70
[falling-land]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/item/FallingBlockEntity.java#L180-L231
[base-block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L326
[powder-tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/concrete_powder.json
[player-drops]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[broken-tool]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[wood-incorrect]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[tools]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ToolMaterial.java#L20-L48
[tool-properties]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Item.java#L431-L449
[wood-pickaxe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L1308
[white-powder-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/white_concrete_powder.json
[white-concrete-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/white_concrete.json
[break-dispatch]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L278-L302
