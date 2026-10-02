# Flood Basalt and Fern Thatch

**Flood Basalt** is a tough, orientable full block; **Fern Thatch** is a full building block crafted from fern items. Their active classes and collection rules differ, so do not infer tool or plant behavior from their prehistoric theme. [Registrations][blocks] [items]

## Registered blocks

| Block | Registry ID | Hardness / blast resistance | Creative listing |
| --- | --- | --- | --- |
| [Flood Basalt](../items/FloodBasalt.md) | `minecraft:flood_basalt` | 3 / 100 | Building Blocks |
| [Fern Thatch](../items/FernThatch.md) | `minecraft:fern_thatch` | 0.5 / 0.5 | Natural Blocks |

Both have ordinary registered block items. This family has no registered stairs, slabs, walls, polished forms or other named variants. [Block/item registry][blocks] [items] · [Creative entries][creative]

## Flood Basalt: access and the mining limitation

Creative access is explicit, but no crafting recipe or natural deposit/structure placement was found in the reviewed bundled source and data. [Fissure Primal Magma](PrimalMagma.md#state-changes-and-fissure-replacement) names Flood Basalt as an initial fallback during terrain replacement, but can select neighboring or underlying states instead. That callback does not establish a normal Survival starter supply or a basalt farm. [Creative registration][creative] · [Fissure replacement][fissure]

The loot table names **one Flood Basalt**, but the registered block also **requires a correct tool for drops**. It is absent from all four standard mining tags. Standard pickaxes, axes, shovels and hoes therefore fail that gate even at high material tiers; **Silk Touch does not bypass it**. Breaking a supplied block with an ordinary pickaxe is not a verified way to recover the item. This is a source integration limitation, not a recommendation to upgrade to a stronger pickaxe. [Loot][loot-flood-basalt] · [Required-tool flag][blocks] · [Mining tags][pickaxe] [axe] [shovel] [hoe] · [Tool rules and server gate][tool-material] [tool-component] [item-tool] [player-tool] [break-dispatch]

### Orientation and placement

The pillar axis follows the face you click: top/bottom gives **vertical Y**, an east/west face gives **X**, and a north/south face gives **Z**. It keeps full collision and does not fall when support is removed. It has no special contact-damage or regeneration callback; the similarly named Primal Magma blocks are separate. [Pillar placement][pillar] · [Registration and base behavior][blocks] [properties]

Flood Basalt is also separate from ordinary [Basalt, Polished Basalt and Smooth Basalt](BlackstoneAndBasalt.md#basalt-variants-and-orientation). Their packing, cutting or smelting instructions do not provide a Flood Basalt conversion.

## Fern Thatch: crafting and collecting

Arrange **four Fern and/or Large Fern items in a 2 × 2 square → one Fern Thatch**. Every ingredient slot accepts the current `minecraft:ferns` item tag, containing **Fern and Large Fern**, so those two can be mixed. Grass, Fiddlehead and Cycad are not members of that ingredient tag. The recipe fits the inventory crafting grid. [Exact recipe][thatch-recipe] · [Tag][ferns] · [Per-slot ingredient matching][ingredient] [pattern]

There is a verified ordinary Survival input route: use **Shears on a small Fern** to collect its Fern item. Taiga's active grass patch selects Fern among its states, so the recipe does not depend on an unverified prehistoric starter plant. An intact Large Fern harvested with Shears also gives small Fern items according to its two-half loot conditions; the recipe's acceptance of a Large Fern item does not mean shearing returns that item. [Fern loot][fern-loot] [large-fern-loot] · [Normal biome selection][normal-preset] [biome-parameters] [overworld-biomes] · [Taiga feature chain][taiga] [fern-placement] [fern-config] [features] [patch] [simple-feature] [biome-generation] [placed-feature]

Ordinary mining gives **one Fern Thatch**, including by hand: it has no correct-tool requirement. Its loot has no Silk Touch or Fortune branch, and these enchantments do not change that output. As with ordinary block items, `doTileDrops` must be enabled. No standard axe/hoe/shovel/pickaxe speed tag includes it. [Properties][blocks] · [Loot][loot-fern-thatch] · [Drop dispatch][block] [rules] · [Mining tags][axe] [hoe] [shovel] [pickaxe]

### Building with thatch

Fern Thatch is a **full collision block**, not a plant that needs soil. It has no directional state, gravity, Bone Meal growth, random spreading or conversion back to ferns in the bundled recipes. Its non-occluding rendering property does not remove its collision. Use it as a green wall, roof or floor block once crafted. [Plain Block registration][blocks] · [Default shape and callbacks][properties] · [Recipe][thatch-recipe]

**Source-based example, not tested in gameplay:** shear four small Ferns, craft one Fern Thatch in the inventory grid, and place it as a roof accent. It can be recovered by ordinary hand mining. For a Flood Basalt accent obtained through Creative, choose the clicked face for the desired axis and account for its mining-drop limitation before placing it into a Survival build.

## Related pages

- [Ancient trees, Flytraps and Tree Stars](AncientPlants.md)
- [Primal Magma](PrimalMagma.md) and [Basalt](BlackstoneAndBasalt.md#basalt-variants-and-orientation)
- [Pewen](Pewen.md), [Primordial decorative plants](PrimordialPlants.md), and [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `e87cde38c872d30ae86139bbee181937603af769` on 2026-10-02. Registrations, exact recipe/tag inputs, loot, tool dispatch, placement and the fern feature chain were checked. All bundled recipes, biome definitions and structure palettes were inspected for additional acquisition routes. No gameplay crafting, mining, placement or generation test was run.

[blocks]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/Blocks.java
[items]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/Items.java
[creative]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[fissure]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/alexscaves/server/block/FissurePrimalMagmaBlock.java
[loot-flood-basalt]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/flood_basalt.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[axe]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[shovel]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/block/mineable/shovel.json
[hoe]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/block/mineable/hoe.json
[tool-material]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/ToolMaterial.java
[tool-component]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/component/Tool.java
[item-tool]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/Item.java#L249-L252
[player-tool]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[break-dispatch]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L297
[pillar]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/RotatedPillarBlock.java
[properties]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java
[thatch-recipe]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/recipe/fern_thatch.json
[ferns]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/item/ferns.json
[ingredient]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/crafting/Ingredient.java
[pattern]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/crafting/ShapedRecipePattern.java#L158-L194
[fern-loot]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/fern.json
[large-fern-loot]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/large_fern.json
[normal-preset]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[biome-parameters]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java
[overworld-biomes]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/biome/OverworldBiomeBuilder.java
[taiga]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/biome/taiga.json
[fern-placement]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/placed_feature/patch_grass_taiga_2.json
[fern-config]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/worldgen/configured_feature/patch_taiga_grass.json
[features]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/Feature.java
[patch]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/RandomPatchFeature.java
[simple-feature]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/feature/SimpleBlockFeature.java
[biome-generation]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L350-L389
[placed-feature]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/levelgen/placement/PlacedFeature.java
[loot-fern-thatch]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/fern_thatch.json
[block]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/Block.java
[rules]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/GameRules.java
