# Trowel

A **Trowel** places a randomly chosen block from your hotbar. Use it for mixed paths, walls, floors, ruins, and terrain detail: arrange the palette in your hotbar, then use the Trowel against a block face. The registered item is `minecraft:trowel`, stacks to **one**, and has no normal durability cost. [Selection and use][trowel] · [Registration][registration] · [Default components][components]

## Obtaining

Craft **one Trowel** from **one Iron Ingot directly above one Stick**, in the same column with no other ingredients. This two-cell vertical recipe fits the inventory crafting grid as well as a [Crafting Table](../blocks/CraftingTable.md). It is not a shapeless recipe. [Recipe][recipe] · [Pattern matching][pattern] · [Crafting-grid trimming][crafting-input]

## Usage

1. Put the blocks you want to use in the hotbar. Blocks elsewhere in your inventory, including your off hand, are not part of the palette.
2. Hold the Trowel and press **Use** on a block face. An interactive block can take the action first; use the secondary-use control, normally crouching, to skip that block interaction.
3. Leave room for the chosen block and satisfy its placement requirements. A click can fail even when some other block in the palette would fit. [Selection and placement][trowel] · [Interaction order][client-use] · [Server use][server-use]

The Trowel's block selection and placement happen on the **server**. Its client-side callback reports success without choosing a block, so an apparent use animation does not establish that a block was placed. The shared interaction path still checks the world border, server reach and spawn protection, enabled features, item cooldowns, and game mode. Spectators cannot place; Adventure restrictions are checked on the **held Trowel** before its callback runs. A WorldEdit brush bound to the held item can also consume the server action before normal item use. [Trowel callback][trowel] · [Client use][client-use] · [Server admission][server-admission] · [World permissions][world-permissions] · [Server use][server-use] · [Adventure gate][item-use] · [Brush handling][brush]

## Behavior

### Control the palette by slots

The selection list contains every **nonempty hotbar stack whose item is a block item** (`BlockItem`), from slots 0–8. Every listed slot has the same chance of being selected. Stack size is not a weight, and identical blocks in separate slots count separately. [Selection][selection]

For example, one slot containing 64 Stone and one containing 1 Cobblestone gives each slot a **one-half selection chance**. Two Stone slots and one Cobblestone slot give Stone **two-thirds** of selections while all three stacks remain nonempty. Splitting a block across more slots increases its share; simply adding blocks to an existing stack does not. Once a stack runs out, it leaves the selection list and the proportions change. These are selection probabilities, not a promise of exact proportions in a finished wall. [Selection][selection]

The Trowel itself is not a block item, so a Trowel held in the hotbar leaves at most eight palette slots. [Registration][registration] · [Selection][selection]

### Selection is not a placement guarantee

The Trowel does **not** test every candidate for placement before drawing one. It picks once, calls that block item's placement routine, and returns the result. If the selected block cannot place, **there is no retry with another palette slot during that use**. With no eligible stacks, it places nothing. [Selection and single attempt][trowel]

The selected item uses the clicked block's replaceability to choose either that position or the adjacent position on the clicked face. The normal block-item path checks enabled blocks, replaceable space, a valid placement state, required support, and collision clearance; specialized block items can add their own restrictions. A plant without suitable support or a solid block obstructed by an entity can therefore prevent that attempt. The Trowel does not turn every inventory item that creates something in the world into an eligible block item. [Placement context][context] · [Placement checks][block-place] · [Support and collision][block-support]

### Materials and durability

Successful ordinary block placement consumes **one block from the selected hotbar stack in Survival**. It does not consume the Trowel. Creative's infinite-materials behavior keeps the selected blocks, but you still need a nonempty eligible stack in the hotbar to select it. A failed attempt that exits the normal placement checks does not reach their consumption step. The Trowel has no durability bar or repair requirement. [Placement and consumption][block-place] · [Consumption rule][consume] · [Infinite materials][infinite] · [Registration][registration] · [Default components][components]

## Notes

- For an even selection among block types, give each type one hotbar slot; remove unrelated block items from the hotbar.
- For a weighted palette, use additional slots for the types you want more often, and replenish nearly empty stacks before they disappear from the draw.
- For reliable placement, choose blocks that can all occupy the intended spaces. A palette containing support-dependent blocks can produce intermittent failures. [Selection][selection] · [Placement checks][block-place] · [Support and collision][block-support]

Related: [Building Wand](BuildingWand.md) · [WorldEdit Wand](WorldEditWand.md) · [Blocks](../blocks/Blocks.md) · [Items](Items.md)

## Trivia

The original MattMC wiki entry credits Quark's Trowel as its inspiration. The behavior in this guide was checked against MattMC's own implementation. [Original attribution][original]

## Sources and verification

Source-reviewed on **2026-10-04** at MattMC commit `78e8e0423084f010bb47e36132550619b37644c2`. Registration, the bundled shaped recipe, hotbar selection, active client/server item-use dispatch, block placement, and consumption were inspected. The recipe describes the bundled resources; data packs can change recipes. [Recipe loading][recipe-loader] No in-game crafting, placement, palette-distribution, or multiplayer test was run.

[recipe-loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L87
[context]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/context/BlockPlaceContext.java#L25-L55
[client-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L317-L376
[server-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L340-L399
[server-admission]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1255-L1299
[world-permissions]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerLevel.java#L819-L822
[item-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L354-L371
[brush]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/worldedit/platform/WorldEditIntegration.java#L120-L142
[trowel]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TrowelItem.java#L20-L84
[selection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TrowelItem.java#L44-L60
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2684
[components]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/trowel.json#L1-L16
[pattern]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/ShapedRecipePattern.java#L158-L194
[crafting-input]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/CraftingInput.java#L36-L81
[block-place]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BlockItem.java#L48-L87
[block-support]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BlockItem.java#L110-L142
[consume]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L1072-L1079
[infinite]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L1212-L1215
[original]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/docs/gameplay/items/Trowel.md#L28-L30
