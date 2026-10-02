# Fletching Table

A **Fletching Table** (`minecraft:fletching_table`) provides the job site for a Fletcher Villager. The current placed block has **no player crafting menu**. Interacting with it does not open an arrow, bow or crossbow workstation. [Block registration][registration] · [Plain-block registration path][plain-block] · [Default interaction][base-interaction] · [Default menu provider][base-menu]

## Crafting and collecting

Craft **one Fletching Table from 2 Flint above 4 planks**, using a two-wide, three-high pattern:

```text
Flint  Flint
Plank  Plank
Plank  Plank
```

Each plank slot accepts the planks tag, so different accepted woods may be mixed. Bamboo Planks and Crimson/Warped Planks qualify; Bamboo Mosaic does not. The three-row pattern requires a Crafting Table. [Recipe][fletching-recipe] · [Planks tag][planks]

An **axe speeds up breaking**, but the block does not require a correct tool or minimum tier for drops. Ordinary Survival harvesting by hand returns one Fletching Table. Its loot has no Silk Touch requirement, Fortune bonus or stored contents; explosion survival still applies. [Tool tag][axe] · [Registration][registration] · [Harvest gate][harvest] · [Loot][fletching-loot]

## Placement and current functionality

Place it as an ordinary full block. It has no facing property, so your placement direction does not rotate it. It also has no inventory, progress state or configurable mode. Breaking and replacing it does not preserve a hidden crafting job. [Plain Block type][plain-block] · [Default placement][placement] · [Empty state definition][no-states] · [Default shape and menu][base-menu]

The current registration uses the ordinary Block implementation, whose default interaction passes without opening a menu. There is no implemented Fletching Table recipe selection, arrow assembly, weapon repair or material-processing transaction. For existing crafting routes, use the [Arrow](../items/Arrow.md), [Bow](../items/Bow.md) and [Crossbow](../items/Crossbow.md) item guides. A Fletcher's trading interface belongs to the Villager. [Registration][registration] · [Interaction defaults][base-interaction] · [Menu default][base-menu]

There are no block slots for a [Hopper](Hopper.md) to fill or empty. A [Dropper](DispenserAndDropper.md) aimed at the table cannot load it with Flint, Sticks or Feathers. The table has no comparator output reporting contents or work, and powering it starts no processing cycle. [World-container lookup][hopper] · [Dropper fallback][dropper] · [Inherited signal behavior][base-interaction]

## Fletcher job site

The implemented workstation role is the **Fletcher profession**. The table's block state is registered as a Fletcher point of interest with **one claim**, and the profession accepts that job site. An eligible unemployed adult Villager still needs an available, reachable site through the normal acquisition path. Placing a table does not guarantee that a particular Villager immediately claims it. [POI registration][poi] · [Profession binding][profession] · [Acquisition][acquire] · [Assignment][assign]

No ammunition or crafting materials need to be supplied to the block. Fletchers use the normal work-at-job-site routine, which plays their work sound and may restock trades when allowed; it does not turn loose ingredients into arrows. Keep access to the claimed table available and follow [Villager](../mobs/Villager.md) and [Trading](../trading/Trading.md) for employment and restocking rules. [Work selection][work-selection] · [Work behavior][work]

## Sources and verification

Source-reviewed on 2026-10-02 at `96e5604a6abaec697de2004b1ba9775e303bfba7`. The absence of a player menu and processing inventory was checked through the actual block registration and inherited handlers. No gameplay test of crafting, placement, harvesting, redstone or profession behavior was run; this page makes no promise about future Fletching Table features.

Related: [Fletching Table item](../items/FletchingTable.md) · [Crafting Table](CraftingTable.md) · [Workstations](catalog/workstations.md) · [Blocks](Blocks.md)

[registration]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/Blocks.java#L5336-L5344
[plain-block]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/Blocks.java#L7274-L7293
[base-interaction]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L202-L234
[base-menu]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L304-L323
[fletching-recipe]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/recipe/crafting/fletching_table.json
[planks]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/item/planks.json
[axe]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[harvest]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[fletching-loot]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/resources/data/minecraft/loot_table/blocks/fletching_table.json
[placement]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/Block.java#L434-L437
[no-states]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/Block.java#L497-L498
[hopper]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L359-L388
[dropper]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/level/block/DropperBlock.java#L58-L75
[poi]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiTypes.java#L120-L133
[profession]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/npc/VillagerProfession.java#L101-L120
[acquire]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/ai/behavior/AcquirePoi.java#L59-L101
[assign]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/ai/behavior/AssignProfessionFromJobSite.java#L15-L41
[work-selection]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/ai/behavior/VillagerGoalPackages.java#L74-L101
[work]: https://github.com/HungLo2020/MattMC/blob/96e5604a6abaec697de2004b1ba9775e303bfba7/src/main/java/net/minecraft/world/entity/ai/behavior/WorkAtPoi.java#L21-L45
