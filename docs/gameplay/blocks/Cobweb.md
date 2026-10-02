# Cobweb

**Cobweb** (`minecraft:cobweb`) slows movement through its space without forming a solid barrier. Bring **[Shears](../items/Shears.md)** to collect the block; an ordinary sword produces [String](../items/String.md) instead. [Registration][web-block] · [Harvesting rules](#collecting-cobwebs)

## Collecting cobwebs

Cobweb has hardness **4** and requires a correct tool before ordinary player mining can produce loot. Usable Shears and swords have explicit Cobweb harvesting rules, each with a base mining-speed multiplier of **15**. This is a tool rule, not a promised break time: player mining conditions still matter. [Block properties][web-block] · [Shears registration][shears-item] · [Shears rule][shears] · [Sword rule][sword] · [Player gate][tool-gate] · [Mining caller][mining]

| How it is broken | Ordinary player-mining result |
| --- | --- |
| Shears | **1 Cobweb** |
| Sword without Silk Touch | **1 String** |
| Empty hand or a tool that does not qualify for Cobweb drops | **No block loot** |

The loot table also preserves the Cobweb when the supplied tool has **Silk Touch I or higher**, but the mining caller must first pass the correct-tool check. Silk Touch alone does not make an unsuitable tool qualify. The bundled Silk Touch support tag contains axes, pickaxes, shovels and hoes, so this conditional loot branch is not an instruction to obtain an ordinarily enchanted Silk Touch sword or Shears. **Shears are the straightforward collection method.** Fortune has no count-increasing branch here; the String alternative has an explosion-survival condition. Block drops must be enabled. [Loot alternatives][web-loot] · [Tool matching][tool-match] · [Harvest gate][tool-gate] · [Silk Touch definition][silk-support] · [Supported tool groups][silk-tag] · [Drop rule][block-drops]

Both the block and its ordinary block item use `minecraft:cobweb`. It is listed in the **Natural Blocks** category. MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) provides a separate insertion route in Survival and Creative; the category entry is not a recipe or loot source. The bundled recipe data contains no crafting recipe producing Cobweb. [Item registration][web-item] · [Block-item helper][item-helper] · [Category owner][natural-category] · [Category entry][web-category]

## Finding and making placed cobwebs

**Mineshaft corridors are one verified generated source.** The bundled Mineshaft structure set selects the registered Mineshaft structures; corridor placement includes random Cobweb patches and additional checks near supports. Those extra placements require an interior position and sturdy neighbors, so a particular corridor is not guaranteed to contain a given number of webs. This route is subject to structure generation being enabled, eligible biomes, successful structure creation and the placement checks. [Structure set][mine-set] · [Normal Mineshaft data][mine-data] · [Eligible biomes][mine-biomes] · [Mineshaft pieces][mine-pieces] · [Corridor creation][mine-corridor] · [Web placement][mine-webs] · [Placement checks][mine-web-check]

The active generation chain is the chunk's structure-start step, generator selection and structure creation, then decoration calling each structure piece's placement method. These are source-verified generation hooks, not a searched world or a guaranteed nearby structure. [Generation gate][structure-starts] · [Selection][structure-selection] · [Creation][structure-generation] · [Decoration][structure-decorate] · [Piece dispatch][piece-dispatch]

**Weaving can create new placed Cobwebs.** When an affected living entity is removed because it was killed, the effect attempts to place **up to 2–3 Cobwebs** nearby. It checks up to 15 candidate positions within one block, accepting distinct replaceable positions with a sturdy upper face underneath. Fewer webs, including none, can result. A non-player victim requires `mobGriefing`; a player victim bypasses that particular rule check. This is a death-effect placement route, not the ordinary Spider item-drop table. [Removal caller][death-dispatch] · [Effect dispatch][effect-dispatch] · [Weaving checks and placement][weaving-effect] · [Requested count][weaving-count]

## Slowing, collision and falling

Cobweb has **no movement collision shape**, so it does not act as a floor or wall. Its separate inside-effect shape still covers the block space, allowing the entity intersection path to invoke its slowing callback. [Registration][web-block] · [Collision and inside shapes][shapes] · [Intersection caller][inside-dispatch] · [Callback dispatch][state-dispatch]

The callback requests these movement-component multipliers:

| Entity condition | Horizontal X/Z | Vertical Y |
| --- | --- | --- |
| Default | **0.25** | **0.05** |
| Living entity with Weaving | **0.5** | **0.25** |

The normal entity movement path applies the stored multiplier and clears its velocity; piston movement skips the multiplication. These are internal movement factors, not a universal blocks-per-second speed. Weaving makes passage less restrictive but does not remove the effect. [Cobweb factors][web-inside] · [Stored effect][stuck] · [Movement application][movement]

The default stuck callback **resets accumulated fall distance**. Cobweb also belongs to the fall-damage-resetting tag used by a separate movement ray check. It can therefore interrupt fall accumulation while you pass through it, but falling again after leaving the web can accumulate a new fall. These paths do not establish an in-game-tested safe landing arrangement. [Fall reset][stuck] · [Tag][fall-tag] · [Ray shape][fall-shape] · [Movement ray][fall-ray]

Entity overrides matter: **Spiders and Cave Spiders ignore Cobweb's stuck callback**, a **player whose flying ability is active** skips the default stuck handler, and the **Wither** overrides that handler with no effect. The flying check is about active flight, not simply being in Creative. These exceptions concern this callback; they are not a claim that every other entity or every fall-related path behaves identically. [Spider override][spider] · [Cave Spider inheritance][cave-spider] · [Player flight check][flying-player] · [Wither override][wither]

## Water and fire

Cobweb is **not waterloggable**. Water that successfully flows into its position replaces it: the fluid code permits the non-blocking Cobweb, calls Water's destruction hook, then places the fluid. That hook evaluates the block loot with an empty tool, selecting **String**, subject to the normal block-drop rule. This differs from mining by hand because flowing Water uses the direct block-drop path rather than the player's correct-tool gate. [Fluid eligibility][fluid-permission] · [Cobweb motion exception][web-motion] · [Replacement][fluid-spread] · [Water destruction hook][water-drops] · [Empty-tool loot context][empty-tool-loot] · [Drop dispatch][drop-dispatch] · [Loot][web-loot] · [Drop rule][block-drops]

The checked **ordinary spreading-fire** registry does not give Cobweb a flammability entry. Its fire burn/ignite lookup therefore does not provide a positive chance for that spreading-fire route. Do not infer that a burning entity destroys a web, or that every other destruction mechanism is excluded. [Fire odds and burning check][fire-odds] · [Flammability registration][fire-register] · [Cobweb callback][web-inside]

## Brewing use

Cobweb is the ingredient that turns an **Awkward Potion into a Potion of Weaving**. The same start-mix helper maps a Water Bottle plus Cobweb to a **Mundane Potion**, so begin with Awkward Potion for Weaving. Follow the [Brewing guide](../brewing/Brewing.md) and [Brewing Stand](BrewingStand.md) for the brewing workflow. [Active mix setup][brewing] · [Start-mix inputs][start-mix]

Related: [Cobweb item](../items/Cobweb.md) · [String](../items/String.md) · [Spider](../mobs/Spider.md) · [Cave Spider](../mobs/CaveSpider.md) · [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `b823010659d7b5095ed021b1c99cf85627e2082a`. Checked registration, category, all bundled recipe results, tool and loot gates, an active Mineshaft generation route, Weaving placement, movement and entity overrides, fall reset, Water replacement, spreading fire and brewing inputs. The generated-source examples are not exhaustive. No in-game harvesting, movement, fall, fluid, fire, brewing or world-generation test was run. Data packs and later code changes can change recipes, tags and loot.

[web-block]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Blocks.java#L689-L700
[shears-item]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L1742-L1744
[shears]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/ShearsItem.java#L32-L44
[sword]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/ToolMaterial.java#L62-L79
[tool-gate]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[mining]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[web-loot]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/loot_table/blocks/cobweb.json#L1-L58
[tool-match]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/component/Tool.java#L49-L57
[silk-support]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/enchantment/silk_touch.json#L26-L31
[silk-tag]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/tags/item/enchantable/mining_loot.json#L1-L8
[block-drops]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Block.java#L410-L415
[web-item]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L287-L287
[item-helper]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L2750-L2781
[natural-category]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L751-L759
[web-category]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1021-L1029
[mine-set]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/worldgen/structure_set/mineshafts.json#L1-L20
[mine-data]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/worldgen/structure/mineshaft.json#L1-L7
[mine-biomes]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/mineshaft.json#L1-L26
[mine-pieces]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftStructure.java#L38-L55
[mine-corridor]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java#L49-L74
[mine-webs]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java#L370-L393
[mine-web-check]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java#L545-L550
[structure-starts]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/chunk/status/ChunkStatusTasks.java#L40-L57
[structure-selection]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L453-L491
[structure-generation]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L554-L574
[structure-decorate]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L333-L345
[piece-dispatch]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/structure/StructureStart.java#L81-L101
[death-dispatch]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/LivingEntity.java#L704-L726
[effect-dispatch]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L267-L269
[weaving-effect]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/effect/WeavingMobEffect.java#L25-L51
[weaving-count]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/effect/MobEffects.java#L120-L122
[shapes]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L321-L331
[inside-dispatch]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/Entity.java#L1196-L1205
[state-dispatch]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L767-L776
[web-inside]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/WebBlock.java#L26-L36
[stuck]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/Entity.java#L2806-L2809
[movement]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/Entity.java#L695-L702
[fall-tag]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/tags/block/fall_damage_resetting.json#L1-L7
[fall-shape]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/ClipContext.java#L55-L63
[fall-ray]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/Entity.java#L704-L715
[spider]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/Spider.java#L115-L120
[cave-spider]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/monster/CaveSpider.java#L20-L25
[flying-player]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/player/Player.java#L1426-L1433
[wither]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/boss/wither/WitherBoss.java#L350-L352
[fluid-permission]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L401-L427
[web-motion]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L534-L538
[fluid-spread]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L268-L277
[water-drops]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/material/WaterFluid.java#L87-L91
[empty-tool-loot]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Block.java#L345-L350
[drop-dispatch]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Block.java#L371-L375
[fire-odds]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/FireBlock.java#L223-L249
[fire-register]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/FireBlock.java#L303-L517
[brewing]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L128-L145
[start-mix]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L230-L234
