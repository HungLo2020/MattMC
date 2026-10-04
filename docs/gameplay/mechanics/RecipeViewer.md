# Recipe Viewer

MattMC's built-in **Recipe Viewer** shows recipes that produce a hovered item. Open it with the configured **Recipe Viewer key, R by default**, to inspect ingredients and outputs before using the appropriate workstation. Its recipe controls do not craft, move ingredients into a grid, or grant a Survival item. The adjacent [inventory item browser](InventoryBrowser.md#mode-and-permission-limits) has separate item-insertion rules. [Default key][key] · [Opening][open] · [Viewer controls][controls] [tabs]

## Opening a recipe

1. Open your inventory or another container screen.
2. Hover a **nonempty inventory slot** or an entry in the attached item panel. For catalog search and panel-space problems, follow [Finding an item](InventoryBrowser.md#finding-and-requesting-an-item).
3. Press your Recipe Viewer key. A nonempty lookup opens the viewer; if it finds nothing, the container stays open. A hovered inventory slot takes priority over a panel entry. [Container input][container-input] · [Hover priority and lookup][open]

The binding is configurable, so check your controls if R does something else. In the container screen, the recipe shortcut is checked **before** the panel's search field receives the key. Inside an already open viewer, that order is reversed; see [Search focus](#search-focus-and-returning). [Saved bindings][bindings] · [Container input][container-input] · [Viewer input][controls]

**The viewer does not pause the game.** Find a safe place before spending time browsing. [Pause behavior][pause]

## Moving between recipes

- Use **< / > buttons** or the **Left / Right arrow keys** to move between recipes of the selected type. Buttons and the recipe counter appear when that type has more than one recipe; moving past either end wraps around. [Buttons][buttons] · [Keys][controls] · [Cycling][cycling]
- When several recipe types are available, click a tab above the recipe or press **1–9** for a tab in its displayed order. Only existing tabs can be selected. Do not assume a particular number always means Crafting or Smelting. Changing type starts at its first recipe. [Tab order][tab-order] · [Tabs and selection][tabs] · [Keys][controls]
- Hover a displayed ingredient or result and press the Recipe Viewer key to look for recipes **producing that item**. You can also use a hovered item-panel entry, which takes priority over recipe-area hover. This replaces the current view; it does not build a back history through the ingredients you visited. An empty lookup leaves the current recipe visible. [Hover selection][hover] · [Further lookup][follow]

### Search focus and returning

While the item-panel search field is focused, it consumes the viewer's keys, including recipe lookup, arrows, number keys and the Inventory key. Press **Enter** to leave search focus while keeping the search text. **Escape** clears the search text and leaves focus first; press Escape again to return. [Search handling][search-focus] · [Viewer input order][controls]

With search unfocused, **Escape or your configured Inventory key** returns to the original container, including after following an ingredient's recipe. A click outside the recipe area also returns, provided the panel or a recipe tab has not handled that click. Use the keys when you want a predictable return without clicking a catalog item. [Return keys][controls] · [Original parent][follow] · [Mouse handling][tabs]

## Reading the display

The viewer supports these six recipe families. Only families found for the selected output appear; a recipe for one device does not imply the other devices accept it. [Supported families][families] · [Lookup grouping][lookup]

| Recipe family | Where to perform the recipe |
| --- | --- |
| Crafting | [Crafting grid](../crafting/Crafting.md#reading-recipes), including the [Crafting Table](../blocks/CraftingTable.md#using-the-grid) |
| Smelting | [Furnace](../blocks/Furnace.md#smelting) |
| Blasting | [Blast Furnace](../blocks/Furnace.md#blast-furnace-and-smoker) |
| Smoking | [Smoker](../blocks/Furnace.md#blast-furnace-and-smoker) |
| Stonecutting | [Stonecutter](../blocks/Stonecutter.md#using-the-menu) |
| Smithing | [Smithing Table](../smithing/Smithing.md#the-three-inputs) |

Crafting displays arrange shaped ingredients in their pattern; shapeless ingredients also appear in a grid, without making those positions mandatory. Ingredient slots can cycle between alternatives. Read the actual recipe's ingredient and output counts rather than treating every changing icon as an extra required ingredient. [Crafting display][crafting-display] · [Ingredient alternatives][alternatives]

The **Coal icon in furnace-style displays is a fixed fuel indicator**. It neither requires Coal exclusively nor calculates how much fuel the recipe costs. Use [Fuel planning](../blocks/Furnace.md#fuel-planning) and [Smelting and cooking](../smelting/Smelting.md#fuel-and-output) for device and fuel rules. [Fixed indicator][fuel]

## When a recipe is missing

An empty lookup is **not proof that an item is unobtainable or uncraftable**. Check these limits before changing your ingredients:

- **Campfire Cooking is omitted.** Follow [Campfire cooking](../blocks/Campfires.md#cooking-four-items) for that route. The omission concerns cooking on a Campfire, not ordinary crafting recipes that make the Campfire block. [Type filter][lookup]
- **This is an output lookup.** It does not list every recipe that uses the hovered ingredient, or other acquisition routes such as loot, trades, growth, breeding and brewing. For example, [Cherry Sapling](../items/CherrySapling.md#obtaining) and [Spruce Sapling](../items/SpruceSapling.md#obtaining) have useful acquisition guides despite their empty producing-recipe lookups. [Output indexing][results]
- **Display coverage is limited.** The lookup uses only a recipe's first display and its first resolved output, grouped by item type. It skips unusable results and does not separately index every output variant or component combination. [Display selection][results]
- **The cached recipe list can be empty or stale.** In the checked source, a fresh ordinary remote-server connection does not supply the recipe data this lookup expects, so a craftable item can have no viewer result. A list populated earlier can instead survive a world change. Automatic rebuilding after recipe or resource reloads is not wired into the checked active callers; reopening the viewer is not a verified refresh fix. This is a source-derived limitation, not a claim that every multiplayer session shows the same result. [Cache and data access][cache] · [Client recipe access][client-level] [client-recipes] · [Client update][client-update] · [Client data type][client-type]

Check the item's acquisition guide and the actual workstation next. For a real crafting failure, use [Crafting checks](../crafting/Crafting.md#mattmc-specific-checks), [cooking checks](../smelting/Smelting.md#if-nothing-cooks), or [smithing checks](../smithing/Smithing.md#if-no-result-appears). A recipe display alone does not verify that your server's current ingredients, tags or recipe data will produce the result.

## Sources and verification

Source-reviewed on **2026-10-04** at `cc140840a21e5c6c932c23abf34124418d6506b0`. Checked the active container opening path, viewer controls and renderers, output lookup, client recipe-data path and cache callers. No live-client navigation, crafting, multiplayer, reload or visual test was performed. This guide does not certify every recipe display; later builds and server data can change results.

Related: [Inventory item browser](InventoryBrowser.md) · [Crafting](../crafting/Crafting.md) · [Mechanics](Mechanics.md)

[key]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/Options.java#L580
[bindings]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/Options.java#L1339-L1345
[container-input]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/gui/screens/inventory/AbstractContainerScreen.java#L733-L762
[open]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/gui/screens/inventory/AbstractContainerScreen.java#L921-L995
[controls]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/gui/screens/inventory/RecipeViewerScreen.java#L266-L311
[pause]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/gui/screens/inventory/RecipeViewerScreen.java#L484-L487
[buttons]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/gui/screens/inventory/RecipeViewerScreen.java#L103-L170
[cycling]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/gui/screens/inventory/RecipeViewerScreen.java#L412-L426
[tab-order]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/gui/screens/inventory/RecipeViewerScreen.java#L66-L78
[tabs]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/gui/screens/inventory/RecipeViewerScreen.java#L362-L410
[hover]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/gui/screens/inventory/RecipeViewerScreen.java#L178-L203
[follow]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/gui/screens/inventory/RecipeViewerScreen.java#L327-L358
[search-focus]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L478-L523
[families]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/gui/screens/inventory/RecipeViewerScreen.java#L437-L481
[lookup]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/recipe/RecipeLookupHelper.java#L33-L60
[crafting-display]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/gui/components/recipes/CraftingRecipeRenderer.java#L42-L119
[alternatives]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/gui/components/recipes/CraftingRecipeRenderer.java#L122-L146
[fuel]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/gui/components/recipes/FurnaceRecipeRenderer.java#L49-L77
[results]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/recipe/RecipeLookupHelper.java#L91-L132
[cache]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/recipe/RecipeLookupHelper.java#L23-L141
[client-level]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/multiplayer/ClientLevel.java#L807-L809
[client-recipes]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/multiplayer/ClientPacketListener.java#L492-L494
[client-update]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/multiplayer/ClientPacketListener.java#L1672-L1675
[client-type]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/multiplayer/ClientRecipeContainer.java#L12-L29
