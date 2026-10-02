# Barrel

A Barrel (`minecraft:barrel`) is **27-slot shared storage** saved in the placed block. Any player who opens that Barrel accesses the same contents. It opens without a lid-clearance check, making it useful beneath shelves or in compact storage walls. Placing Barrels beside one another keeps their inventories separate. [Inventory and persistence][inventory] · [Opening the placed Barrel][open]

## Crafting and collecting

Use **six accepted planks and two accepted wooden slabs** in a Crafting Table to make **one Barrel**:

| Left | Center | Right |
| --- | --- | --- |
| Plank | Wooden Slab | Plank |
| Plank | Empty | Plank |
| Plank | Wooden Slab | Plank |

Each plank slot accepts the bundled planks tag, and each slab slot accepts the wooden-slabs tag. The 12 vanilla wood/Bamboo materials are included, and the slots may mix accepted materials. [Pewen](Pewen.md) and Bamboo Mosaic are absent from these ingredient tags; do not substitute them based only on appearance. The existing [wood construction guide](WoodConstruction.md#planks-and-materials) covers making the materials. [Complete Barrel recipe][recipe] · [Planks ingredient tag][planks] · [Wooden-slabs ingredient tag][slabs]

An axe is the tagged faster mining tool, but **no tool or minimum tier is required for the ordinary Barrel item drop**. Hardness and blast resistance are both **2.5**. The loot returns one Barrel and copies its custom name; Silk Touch and Fortune do not pack its inventory or increase the drop. The explosion-survival condition is separate from ordinary mining. [Barrel properties][registration] · [Axe tag][axe] · [Complete Barrel loot][loot] · [Correct-tool check][tool-gate] · [Survival drop dispatch][mining]

## Placement and access

The Barrel's front can face any of the **six directions**, including up or down. Placement points it opposite the nearest direction the player is looking. The front direction also sets where the opening sound is played; the menu still accesses the same 27 slots. [Facing and placement][facing] · [Front-positioned sound][sound]

The ordinary opening path does **not** check for a block above, clearance in front, or a sitting cat. An adjacent solid block therefore does not impose a lid restriction, provided you can reach an exposed face to interact. Barrels do not join into double storage. The container's normal validity check still requires its block entity to remain present and the player to remain within interaction range. [Menu-opening path][access] · [Three-row menu][menu] · [Container validity][valid] · [Block identity and interaction range][valid-helper]

Barrels do **not waterlog**: their states are facing and open/closed, with no stored-water state. Opening is tracked for animation and sounds; it does not replace the inventory or make its contents private to the current user. A custom lock supplied through supported data can restrict the menu, but an ordinary crafted Barrel has no such lock. [Barrel states][states] · [Opening and closing tracking][openers] · [Default lock and access checks][locks] · [Default empty fluid state][empty-fluid]

## Persistence and breaking a filled Barrel

Contents are saved and loaded with the Barrel's block entity, so closing the menu does not clear them. The menu points directly at that block inventory rather than a copy belonging to the player. Store items normally, then use the same Barrel later to retrieve them. [Block inventory save and load][save] · [Menu uses the provided container][menu-container]

**Breaking a filled Barrel spills its contents as separate item entities.** The block's own loot only carries the Barrel and its custom name. The active block-removal path calls the container's contents-drop handler before removing the block entity. Neither Silk Touch nor the item's empty container component changes this normal loot rule. Empty valuable contents before moving a Barrel, or use a [Shulker Box](ShulkerBox.md#breaking-and-carrying-contents) for the documented portable-storage behavior. [Name-only block loot][name-only] · [Active block-entity removal][removal] · [Container removal callback][spill] · [Contents released as item entities][item-drops] · [Empty container component on the item][item-default]

## Hoppers and comparator output

A [Hopper](Hopper.md) pointing into a Barrel can insert items; one beneath it can extract them. The Barrel is an ordinary container rather than a sided container, so **every face exposes all 27 slots**. Its facing and open state do not assign special input or output slots, and lid clearance is not checked. Normal hopper power, cooldown, space, and stack-compatibility rules still apply. [Hopper source and destination selection][hopper-target] · [Block-container lookup][hopper-container] · [All-slot fallback][slots] · [Insertion and extraction eligibility][insertion] · [Default container item acceptance][container-allow]

A [Redstone Comparator](RedstoneComparator.md) reads **inventory fullness**: 0 when empty, 15 when every slot is full for its item's permitted stack size, and intermediate values for partial storage. Opening an empty Barrel does not itself produce a fullness signal. [Barrel comparator hook][analog] · [Container fullness calculation][fullness]

For an exact reading, add each occupied slot's `item count ÷ allowed stack size`, then divide by 27. A nonempty Barrel outputs `1 + floor(14 × fullness)`. This accounts for nonstackable items and smaller stacks; counting item objects alone is insufficient. The allowed stack size is the smaller of the container limit and that item's maximum. [Discrete output rounding][rounding] · [Per-item stack limit][stack-limit]

## Fisherman job site and Piglins

All Barrel block states are registered as **Fisherman job sites**. The villager's active job-site behavior can assign the Fisherman profession after a suitable Barrel is acquired. Changing a storage area's Barrels can therefore affect nearby villagers' workstations. Job claiming does not give the villager a separate Barrel inventory or change the storage menu. Use the [Villager guide](../mobs/Villager.md#employment-and-changing-jobs) for employment and job-change conditions. [Barrel job-site registration][poi] · [All registered block states][poi-states] · [Fisherman profession binding][profession] · [Active job-site behavior][ai] · [Profession assignment][assignment]

Opening a Barrel can anger eligible nearby idle [Piglins](../mobs/Piglin.md) that can see the player. Breaking it also calls the anger behavior through the guarded-by-Piglins tag, **without the opening visibility filter**. This applies to player-placed storage too; the callback does not check who crafted it or owns its contents. [Barrel opening callback][piglin-open] · [Guarded blocks][guarded] · [Breaking callback][break-anger] · [Nearby Piglin filters][anger] · [Anger target eligibility][target]

## Related pages

- [Barrel item](../items/Barrel.md) and [Ender Chest](EnderChest.md)
- [Chest](Chest.md), [Shulker Box](ShulkerBox.md), and [Copper Chests](CopperChests.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `60699a119c4728a7bcaf15196f3c839cfcfd69dc` on 2026-10-02. Registrations, complete producing recipes and loot tables, expanded ingredient/mining tags, and the active menu, persistence, removal, hopper, comparator, and Piglin paths were reviewed. No gameplay test of storage, relocation, obstruction, dimension travel, respawn, automation, or villager employment was run.

[inventory]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/entity/BarrelBlockEntity.java#L26-L100
[open]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/BarrelBlock.java#L42-L50
[recipe]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/recipe/crafting/barrel.json
[planks]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/tags/item/planks.json
[slabs]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/tags/item/wooden_slabs.json
[registration]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/Blocks.java#L5311-L5315
[axe]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[loot]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/loot_table/blocks/barrel.json
[tool-gate]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[mining]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[facing]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/BarrelBlock.java#L92-L100
[sound]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/entity/BarrelBlockEntity.java#L133-L139
[access]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/BarrelBlock.java#L42-L50
[menu]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/entity/BarrelBlockEntity.java#L98-L101
[valid]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/entity/BaseContainerBlockEntity.java#L128-L141
[valid-helper]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/Container.java#L87-L98
[states]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/BarrelBlock.java#L27-L39
[openers]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/entity/BarrelBlockEntity.java#L29-L54
[locks]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/entity/BaseContainerBlockEntity.java#L27-L79
[empty-fluid]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L228-L230
[save]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/entity/BarrelBlockEntity.java#L61-L100
[menu-container]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/inventory/ChestMenu.java#L41-L71
[name-only]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/loot_table/blocks/barrel.json
[removal]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java#L311-L323
[spill]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java#L250-L254
[item-drops]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/Containers.java#L21-L46
[item-default]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/item/Items.java#L2431-L2433
[hopper-target]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L344-L352
[hopper-container]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L374-L388
[slots]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L174-L202
[insertion]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L280-L307
[container-allow]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/Container.java#L53-L59
[analog]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/BarrelBlock.java#L72-L80
[fullness]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java#L747-L767
[rounding]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/util/Mth.java#L524-L527
[stack-limit]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/Container.java#L31-L37
[poi]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiTypes.java#L120-L133
[poi-states]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiTypes.java#L88-L90
[profession]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/entity/npc/VillagerProfession.java#L115-L116
[ai]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/entity/ai/behavior/VillagerGoalPackages.java#L47-L65
[assignment]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/entity/ai/behavior/AssignProfessionFromJobSite.java#L18-L41
[piglin-open]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/BarrelBlock.java#L42-L48
[guarded]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/resources/data/minecraft/tags/block/guarded_by_piglins.json
[break-anger]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/level/block/Block.java#L480-L486
[anger]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L524-L533
[target]: https://github.com/HungLo2020/MattMC/blob/60699a119c4728a7bcaf15196f3c839cfcfd69dc/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L676-L687
