# Stonecutter

The **Stonecutter** (`minecraft:stonecutter`) turns one accepted ingredient into a selected recipe's output, without fuel or a cooking timer. It is also a Mason job-site block. Available choices come from loaded, enabled stonecutting recipes, not an item's appearance or a general promise that every stone-like material works. [Block/menu][block] [menu] · [Recipe loading and filtering][recipes]

## Crafting and collecting

Put **3 ordinary Stone in a row**, with **1 Iron Ingot directly above the middle Stone**, to craft **1 Stonecutter**. This is a two-row pattern on the Crafting Table; Cobblestone and Smooth Stone cannot replace the named Stone ingredient. [Recipe][recipe]

Use an **unbroken pickaxe**, including Wood, to collect a placed Stonecutter. It requires a correct tool and is in the pickaxe tag, with no higher tool-tier requirement. Ordinary mining drops **one Stonecutter**; Silk Touch is unnecessary and Fortune adds nothing. Its loot has an explosion-survival condition. Registered hardness and blast resistance are both **3.5**, not measured breaking times. [Registry and properties][blocks] [properties] · [Mining tags and tool rules][pickaxe] [wood-denials][] [stone-tier][] [iron-tier][] [diamond-tier][] [tool][] [stack][] · [Harvest dispatch][player-tool] [harvest] · [Loot][loot]

## Using the menu

1. Place the Stonecutter and interact with it
2. Put the intended ingredient in the input slot
3. Select a displayed recipe
4. Take its result; each completed operation consumes **one input item** and produces the chosen recipe's output count

The preview does not spend the ingredient until the result is taken. The output slot cannot accept items placed into it. Changing the input to a different item type clears the selection and preview; select a recipe again. Adding more of the same input type does not by itself reset that selection. [Menu slots, selection and result removal][menu] · [Active screen and server buttons][screen] [buttons]

You can manually put an item with no matching recipe into the input slot, but it produces no choices or output. Shift-clicking from your inventory routes the item into the input only when the available stonecutting recipes accept it. An empty result is therefore usually a missing selection or matching recipe, not a fuel problem. [Ordinary input-slot acceptance][slot] · [Recipe filtering and shift-click handling][menu] [selection]

Each recipe checks its own ingredient. The result is a copy of the recipe-defined output, so names or other data on the input are not automatically copied to the cut result. There is no second ingredient slot. [Recipe matching and assembly][single-recipe] · [Registered stonecutting type][recipe-type] [recipe-serializer]

## Temporary input and automation

The input belongs to the player's open menu, **not to a stored inventory inside the placed workstation**. Closing the menu discards the untaken preview and returns remaining input through the normal player-inventory return path; items drop if that path cannot keep them. The workstation must remain present and within interaction range for the menu to stay valid. [Menu validity and cleanup][menu] · [Return rules][return] [inventory-return]

A Hopper cannot insert material into or collect a cut result from this private menu. The block has neither a block-entity inventory nor a world-container interface for the Hopper lookup. A Dropper facing the Stonecutter ejects its item through the ordinary no-container path instead of operating the cutter. [Block class][block] · [Hopper lookup][hopper] · [Dropper fallback][dropper]

The Stonecutter has **no inventory comparator output** or redstone-triggered cutting cycle. Items temporarily present in one player's menu do not turn it into a comparator-readable container. [Block callbacks][block] · [Default analog-output behavior][properties]

## Checked recipe examples

Material guides own the exact input/output recipes and yields. Use their tables to choose an efficient conversion and avoid assuming that a similar variant is accepted:

| Material family | Canonical recipe guide |
| --- | --- |
| Stone, Cobblestone, Stone Bricks and mossy forms | [Stonecutting choices](Stone.md#stonecutting-choices) |
| Deepslate construction | [Stonecutting shortcuts](Deepslate.md#stonecutting-shortcuts) |
| Tuff | [Tuff stonecutting](Tuff.md#stonecutting) |
| Sandstone and Red Sandstone | [Sandstone stonecutting](Sandstone.md#stonecutting) |
| Clay Bricks | [Brick slabs, stairs and walls](ClayAndBricks.md#brick-slabs-stairs-and-walls) |
| Blackstone | [Blackstone stonecutting](BlackstoneAndBasalt.md#stonecutting) |
| Nether Bricks | [Nether Brick stonecutting](NetherBricks.md#stonecutting) |
| Quartz | [Quartz stonecutting](Quartz.md#stonecutting) |
| End Stone and Purpur | [End material stonecutting](EndStoneAndPurpur.md#stonecutting) |
| Copper construction | [Copper Stonecutter conversions](CopperConstruction.md#stonecutter-conversions) |

These links are not a complete list of all accepted ingredients. The checked source contains **254 bundled stonecutting recipes**, each with an explicit item ingredient, and no Limestone input. See [Limestone](Limestone.md) for that family's integration limits. Enabled features and server data can change the choices actually shown. [Recipe loading and enabled-result checks][recipes] · [Input filtering][selection]

## Placement and village use

The front faces the player when placed. The block's shape is a full-width base **9/16 block high**; the blade artwork has no special damaging contact callback in the active block class. [Shape and placement][block]

An unemployed adult Villager can claim an available, reachable Stonecutter as a **Mason** job site. All its facing states belong to that job-site type, with room for **one claimant**; it needs no input item, fuel or completed cutting recipe. Babies and nitwits do not take this job through the normal acquisition path. [POI and profession registration][poi] [profession] · [Active Villager brain and job acquisition][villager] [goals][] [acquire][] [assign]

Placing or moving the block does not guarantee a particular Villager changes profession or offers a particular trade. Follow [Villager employment](../mobs/Villager.md#employment-and-changing-jobs) and [Trading](../trading/Trading.md) for job changes, experience locks, restocking and selected offers.

## Sources and verification

Source-reviewed on **2026-10-02** at `ae92d4575f1af7752d0f461bbf7bf0843c3cddf7`. Checked registration, exact workstation recipe and loot, tool tiers, all 254 bundled stonecutting resources, active recipe loading/menu selection/output consumption/cleanup, placement, automation/comparator limits and Mason acquisition wiring. Natural generation was not surveyed. No in-game crafting, cutting, harvesting, Hopper, collision or Villager test was run. Server data and later code can change these results.

Related: [Stonecutter item](../items/Stonecutter.md) · [Stone](Stone.md) · [Crafting](../crafting/Crafting.md) · [Workstations](catalog/workstations.md) · [Blocks](Blocks.md)

[blocks]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/Blocks.java
[items]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/Items.java
[properties]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java
[tool]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/ToolMaterial.java
[stack]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/ItemStack.java
[harvest]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L297
[player-tool]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[use-dispatch]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L343-L378
[recipes]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java
[hopper]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java
[dropper]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/DropperBlock.java
[comparator]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/ComparatorBlock.java
[poi]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiTypes.java
[profession]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/entity/npc/VillagerProfession.java
[villager]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/entity/npc/Villager.java
[goals]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/entity/ai/behavior/VillagerGoalPackages.java
[acquire]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/entity/ai/behavior/AcquirePoi.java
[assign]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/entity/ai/behavior/AssignProfessionFromJobSite.java
[block]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/level/block/StonecutterBlock.java
[menu]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/inventory/StonecutterMenu.java
[recipe]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/recipe/crafting/stonecutter.json
[loot]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/loot_table/blocks/stonecutter.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[wood-denials]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[stone-tier]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json
[iron-tier]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json
[diamond-tier]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/tags/block/needs_diamond_tool.json
[screen]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/client/gui/screens/inventory/StonecutterScreen.java
[buttons]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1866-L1880
[slot]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/inventory/Slot.java
[selection]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/crafting/SelectableRecipe.java
[single-recipe]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/crafting/SingleItemRecipe.java
[recipe-type]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/crafting/RecipeType.java
[recipe-serializer]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/crafting/RecipeSerializer.java
[return]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java
[inventory-return]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/entity/player/Inventory.java#L302-L323
