# Hay Bale

A **Hay Bale** stores nine [Wheat](../items/Wheat.md), cushions landings and turns the Campfire directly above it into a signal fire. Its exact block and item ID is `minecraft:hay_block`. Use [Wheat crop](Wheat.md) for farming and [Resource Storage Blocks](ResourceStorageBlocks.md) for the separate mineral-storage family. [Block registration][block] · [Item registration][item]

## Getting and unpacking Hay Bales

Craft **nine Wheat into one Hay Bale**. The bundled recipe is **shapeless**, but its nine ingredients occupy all nine slots of a [Crafting Table](CraftingTable.md). Craft one Hay Bale by itself to recover **nine Wheat**; that reverse recipe fits the personal crafting grid. No seeds substitute for the Wheat ingredient. [Packing recipe][pack] · [Unpacking recipe][unpack]

A verified generated source is **Hay Bale piles in Plains village decoration**. The village's town-center and street connections can reach the decor pool, which can select the Hay pile feature. Its randomized placement checks mean a village does not promise a particular bale count. This is one checked source, not a complete survey of every village style or template. [Village structure][village] · [Structure set][village-set] · [Jigsaw dispatch][jigsaw] · [Start pool][town-pool] · [Town-center connector][town-nbt] · [Street pool][street-pool] · [Street connector][street-nbt] · [Decor choice][decor] · [Placed feature][placed] · [Hay configuration][configured] · [Active feature placement][feature-call] [pile]

**Separate inventory route:** Hay Bale is an ordinary **Natural Blocks** category entry. MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) can insert listed items in Survival as well as Creative; that does not consume Wheat or establish a crafting/world-generation source. [Category][category] · [Hay entry][entry]

## Placement and recovery

Hay Bales have full-block collision and an `axis` state of `x`, `y` or `z`. Placement follows the axis of the clicked face: top/bottom for a vertical bale, side faces for a horizontal bale. They do not fall when the support below is removed and have no waterlogged state. [Hay class][hay] · [Axis placement][axis] · [Inherited shape/support][shape] · [Empty fluid][fluid]

**Hand breaking recovers one Hay Bale; Silk Touch is unnecessary.** The block has no correct-tool drop gate. An unbroken hoe gives the tagged mining-speed benefit, while Fortune does not increase the single-block loot. Hardness and blast resistance are both **0.5**; those are properties, not mining times. [Registration][block] · [Harvest check][gate] [harvest] · [Hoe tag][hoe] · [Hoe dispatch][hoe-dispatch] · [Tool speed][material] [speed][] [broken-speed][] · [Complete loot][loot] · [One-item default][one-item] [one-count] · [Strength interpretation][strength]

The loot has an explosion-survival condition, so an explosion can destroy the item. **Spreading fire can also consume placed Hay Bales.** Keep stored bales away from fire that can reach them; the block is explicitly in the flammability table. [Explosion condition][explosion] · [Fire registration][flammable] · [Fire removal][fire]

## Softer landings

Landing on a Hay Bale sends a **0.2 fall-damage multiplier** into the ordinary fall-damage calculation. This reduces the calculated fall damage by 80% before the living entity's final integer rounding and other applicable damage handling. It does **not** make a fall of any height safe. All three bale orientations use the same callback. [Landing dispatch][landing] · [Hay multiplier][hay] · [Living-entity calculation][fall]

Use a visible landing area and aim for the top of the bale; simply carrying a Hay Bale does not invoke the placed block's landing callback. [Landing dispatch][landing]

## Campfire signal smoke

Put a Hay Bale **directly beneath a Campfire or Soul Campfire**. The Campfire sets `signal_fire=true` when placed or when that lower neighbor changes; the check accepts the Hay Bale regardless of its axis. A lit Campfire then produces signal-smoke particles, whose lifetime is longer than ordinary cozy-smoke particles. Removing the bale restores the ordinary smoke choice. [Campfire registrations][campfire-blocks] · [Support check][signal] · [Lit client ticker][ticker] · [Particle dispatch][particles] · [Smoke choice][smoke-choice] · [Particle registration][particle-registration] · [Lifetime][lifetime]

This changes the visible smoke signal, **not the Bee housing smoke-search range**. The Bee-safe smoke check still searches below the housing using its own lit-campfire and collision rules; it never reads `signal_fire`. Follow [Bee housing harvesting](BeeHousing.md#harvesting) for that layout and safety rule. [Smoke search][bee-smoke]

## Other uses

For one [Target](Target.md), place **one Hay Bale in the center and four Redstone Dust immediately above, below, left and right** in a Crafting Table. The Target guide owns projectile scoring and redstone output. [Target recipe][target]

Hay Bale items also have animal-feeding uses. See [Horse](../mobs/Horse.md) and [Llama](../mobs/Llama.md) for those animals; placing a bale in a pen is a different action from feeding an item. [Horse feeding branch][horse] · [Llama feeding branch][llama]

## Sources and verification

Source-reviewed at `b823010659d7b5095ed021b1c99cf85627e2082a` on 2026-10-02. Checked registration, recipes, complete block loot, the active harvest gate, axis/support/fluid behavior, landing calculation, signal-smoke dispatch, separate Bee smoke check and one Plains-village pile route. The village example is not an exhaustive world-generation inventory. No in-game crafting, fall, smoke, mining, fire or generation test was run. Data packs and later changes can alter these rules.

[gate]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[shape]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L304-L326
[fluid]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L228-L230
[explosion]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/storage/loot/predicates/ExplosionCondition.java#L27-L35
[strength]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L1143-L1153
[one-item]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/storage/loot/entries/LootItem.java#L33-L36
[material]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/ToolMaterial.java#L35-L48
[block]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Blocks.java#L3189-L3193
[item]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L657
[pack]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/recipe/crafting/hay_block.json#L1-L19
[unpack]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/recipe/crafting/wheat.json#L1-L11
[village]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/worldgen/structure/village_plains.json#L1-L15
[town-pool]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/worldgen/template_pool/village/plains/town_centers.json#L1-L12
[village-set]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/worldgen/structure_set/villages.json#L1-L30
[jigsaw]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/structure/structures/JigsawStructure.java#L135-L152
[town-nbt]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/structure/village/plains/town_centers/plains_fountain_01.nbt
[street-pool]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/worldgen/template_pool/village/plains/streets.json#L1-L149
[street-nbt]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/structure/village/plains/streets/corner_01.nbt
[decor]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/worldgen/template_pool/village/plains/decor.json#L1-L46
[placed]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/worldgen/placed_feature/pile_hay.json#L1-L4
[configured]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/worldgen/configured_feature/pile_hay.json#L1-L14
[feature-call]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/structure/pools/FeaturePoolElement.java#L82-L96
[category]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L752-L761
[entry]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1006-L1015
[hay]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/HayBlock.java#L11-L28
[axis]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/RotatedPillarBlock.java#L14-L57
[hoe]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/tags/block/mineable/hoe.json#L1-L12
[hoe-dispatch]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Item.java#L443-L445
[loot]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/loot_table/blocks/hay_block.json#L1-L21
[flammable]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/FireBlock.java#L462-L464
[fire]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/FireBlock.java#L235-L244
[landing]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/Entity.java#L1398-L1412
[fall]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1710-L1735
[campfire-blocks]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Blocks.java#L5423-L5447
[signal]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/CampfireBlock.java#L119-L154
[ticker]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/CampfireBlock.java#L310-L325
[particles]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/entity/CampfireBlockEntity.java#L102-L108
[smoke-choice]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/CampfireBlock.java#L235-L247
[particle-registration]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/client/particle/ParticleResources.java#L69-L70
[lifetime]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/client/particle/CampfireSmokeParticle.java#L11-L25
[bee-smoke]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/CampfireBlock.java#L261-L281
[target]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/recipe/crafting/target.json#L1-L17
[horse]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L413-L441
[llama]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/animal/horse/Llama.java#L178-L204
[pile]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/feature/BlockPileFeature.java#L18-L54
[harvest]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[speed]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/component/Tool.java#L39-L47
[broken-speed]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/ItemStack.java#L374-L379
[one-count]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/ItemStack.java#L248-L254
