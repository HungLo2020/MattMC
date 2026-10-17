# Jack o'Lantern

A **Jack o'Lantern** (`minecraft:jack_o_lantern`) is a full decorative block that emits **light level 15**. It has a carved horizontal face, works as a golem-construction head, and can remain lit underwater. Plan the mounting block before placing one: a matching golem body can turn a decoration into a construction interaction. [Registration][reg] · [Placement and golem callback][pumpkin]

## Crafting and harvesting

Place **1 Carved Pumpkin directly above 1 ordinary Torch** to craft **1 Jack o'Lantern**. The two-row recipe fits the inventory grid. An uncarved Pumpkin, Soul Torch, Copper Torch, or Redstone Torch does not satisfy those exact ingredients. Use [Pumpkin farming and carving](PumpkinAndMelon.md#carving-a-pumpkin) to prepare the head. [Recipe][recipe]

Taiga village decoration can generate a Pumpkin pile whose block choices include Jack o'Lanterns. The bundled provider weights ordinary Pumpkins **19** and Jack o'Lanterns **1**; a selected pile need not contain a light, and a village need not select a pile. This is a source-verified extra route, not a guarantee for a particular village. [Decoration pool][taiga-decor] · [Placed feature][pile-placed] · [Block choices][pile-config] · [Feature dispatch][feature-dispatch] · [Pile placement][pile-feature]

Breaking it normally gives **1 Jack o'Lantern**, including by hand. An axe is the efficient mining tool, but the block does not require a correct tool for drops. Its hardness and blast resistance are both **1**. Silk Touch and Fortune do not change the self-drop; the loot includes an explosion-survival condition. Normal block drops must be enabled. [Properties][reg] · [Axe tag][axe] · [Loot][loot] · [Tool gate][harvest] · [Harvest dispatch][harvest-call] · [Drop rule][drops]

## Placement, water and light

The carved face points opposite the placing player's horizontal direction, toward the player in ordinary placement. It has full-block collision, no falling behavior, and no continuing support requirement. It can therefore remain suspended after a temporary placement block is removed. [Facing][pumpkin] · [Default shape/support][defaults]

It has **no waterlogged state**, but its full solid block prevents ordinary fluid flow from replacing it. It can be placed as an underwater light, occupying its own block space rather than storing water inside. Facing and redstone power do not change its light level, and it has no fuel supply or extinguish interaction. [Registration][reg] · [States][pumpkin] · [Solid/collision defaults][defaults] · [Fluid admission][fluid-entry]

Its light can melt nearby ordinary Ice or Snow layers when sufficient block light reaches them. Follow the [Ice](Ice.md#ordinary-ice-melting-and-breaking) and [Snow](Snow.md) rules when building in frozen areas; brightness 15 does not prove a universal mob-proof radius. [Ice check][ice] · [Snow check][snow]

## Golem heads and dispensers

The Jack o'Lantern is an accepted final head for all three checked pumpkin-pattern constructions:

- [Snow Golem](../mobs/SnowGolem.md#build-a-snow-golem): place it last over two full Snow Blocks
- [Iron Golem](../mobs/IronGolem.md#build-an-iron-golem): place it last on the completed four-Iron-Block pattern, with its required air spaces
- [Copper Golem and Copper Chest](CopperChests.md#creating-a-chest-with-a-copper-golem): place it last above one accepted full copper block

The linked guides own the exact layouts, accepted variants and resulting entities/chest. The successful construction consumes the head; its light block is not left behind. Head facing is not a condition of the three pattern matches, although it supplies the resulting Copper Chest's facing. [Head predicate, callbacks and patterns][pumpkin] · [Accepted copper bodies][copper]

**Use a Carved Pumpkin for dispenser-built golems.** The special dispenser placement handler is registered only for Carved Pumpkin. A normal Jack o'Lantern item lacks that handler and the Carved Pumpkin's wearable-head component, so a dispenser ejects it as an item instead of placing the head. Do not infer dispenser support from the two blocks sharing golem patterns. [Dispenser registration][dispenser] · [Item registrations][items] · [Fallback selection][fallback] · [Ordinary ejection][ejection]

## Wild Crows

Jack o'Lantern is in the `crow_fears` block tag. The active wild-Crow avoidance goal looks for tagged blocks and can fly away from them; tamed Crows are excluded from starting that goal. See [Crow behavior](../mobs/Crow.md#wild-behavior-and-combat) before putting one beside a farm or taming spot. This does not make it a general monster repellent. [Scare-block tag][crow-tag] · [Goal registration and avoidance][crow]

## Related pages

- [Jack o'Lantern item](../items/JackOLantern.md), [Carved Pumpkin](../items/CarvedPumpkin.md), and [Torch](Torch.md)
- [Copper Chests](CopperChests.md), [Iron Golem](../mobs/IronGolem.md), and [Snow Golem](../mobs/SnowGolem.md)
- [Lanterns](Lanterns.md), [Luminous blocks](LuminousBlocks.md), and [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `cf1c134b3f9ff634490e448fe26c335b90f82227`. Checked registration, recipe, tool tags and loot, Taiga village pile generation, facing/default support, fluid admission, all three golem patterns, dispenser routing, and the active Crow avoidance path. No in-game crafting, underwater placement, golem, dispenser, Crow, or lighting test was run. Data packs and game rules can change these results.

[reg]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L2095-L2106
[pumpkin]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/CarvedPumpkinBlock.java
[recipe]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/crafting/jack_o_lantern.json
[axe]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[loot]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/loot_table/blocks/jack_o_lantern.json
[copper]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/tags/block/copper.json
[dispenser]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L298-L317
[items]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/item/Items.java#L504-L524
[fallback]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/DispenserBlock.java#L102-L113
[ejection]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/core/dispenser/DefaultDispenseItemBehavior.java
[crow-tag]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/tags/block/crow_fears.json
[crow]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/alexsmobs/entity/EntityCrow.java
[taiga-decor]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/worldgen/template_pool/village/taiga/decor.json
[pile-placed]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/worldgen/placed_feature/pile_pumpkin.json
[pile-config]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/worldgen/configured_feature/pile_pumpkin.json
[feature-dispatch]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/levelgen/structure/pools/FeaturePoolElement.java
[pile-feature]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/levelgen/feature/BlockPileFeature.java
[defaults]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java
[fluid-entry]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L400-L427
[harvest]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[harvest-call]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L278-L294
[drops]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Block.java#L364-L420
[ice]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/IceBlock.java#L50-L63
[snow]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/SnowLayerBlock.java#L105-L111
