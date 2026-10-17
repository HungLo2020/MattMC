# Bundle

A **Bundle** stores mixed item stacks inside one inventory slot. Its capacity is one stack's worth of item weight: items that normally stack to 64 use less space than items that stack to 16, and an ordinary unstackable item fills it alone. The uncolored Bundle and all 16 colors use the same storage rules and each stack to one. [Registration][items] · [Capacity accounting][weight]

## Obtaining

Craft **one String directly above one Leather**, in the same column, to make **one empty Bundle**. This two-slot vertical recipe fits the personal 2 × 2 crafting grid as well as a [Crafting Table](../blocks/CraftingTable.md). See [String](String.md) and [Leather](Leather.md) for the ingredients' own supply routes. [Exact recipe][basic-recipe] · [Personal grid][personal-grid] · [Active crafting caller][personal-crafting]

The uncolored item also appears in bundled village chest loot: [cartographer][village_cartographer], [tannery][village_tannery], [weaponsmith][village_weaponsmith], and [plains][village_plains_house], [desert][village_desert_house], [savanna][village_savanna_house], [snowy][village_snowy_house] and [taiga][village_taiga_house] house tables. These tables include an empty alternative, so a matching chest does not guarantee a Bundle. Chest loot is filled when its stored loot table is unpacked. [Container opening][loot-menu] · [Loot resolution][loot-unpack]

All 17 variants are also listed in Tools & Utilities and available through MattMC's ordinary [Inventory Browser](../mechanics/InventoryBrowser.md) in **Creative**. That is a separate acquisition route from crafting and village loot. [Category entries][category] · [Browser list][browser] · [Server handling][browser-server]

## Usage

### Inventory controls

These controls apply to ordinary inventory slots. “On the cursor” means you have picked up that stack in the inventory screen. Left-click is the primary action; right-click is the secondary action. [Click dispatch][click-buttons]

| What you hold on the cursor | What you click | Action |
| --- | --- | --- |
| A Bundle | A nonempty item slot, with left-click | Move as many items as fit from that slot into the held Bundle |
| An item stack | A Bundle in a slot, with left-click | Move as many held items as fit into that Bundle |
| A Bundle | An empty slot, with right-click | Move the selected stored stack into the slot, or the newest stack if none is selected |
| Nothing | A Bundle in a slot, with right-click | Take the selected stored stack onto the cursor, or the newest stack if none is selected |

Removal takes a **stored stack**, not just one individual item. If a destination slot accepts only part of it, the remainder is put back into the Bundle. Slot pickup, insertion and modification rules still apply; a crafting output or restricted equipment slot need not behave like an ordinary inventory slot. [Held-Bundle actions][carried-bundle] · [Bundle-in-slot actions][resting-bundle] · [Stack removal][removal] · [Slot rules][slot-rules]

Successful insertion puts a new entry at the front. If the incoming items can merge with an existing stack of the same item and components, that combined stack moves to the front instead. This is why the default stack removed can change after topping up something already inside. [Insertion order and merging][insertion]

### Selecting a stored stack

Hover over a Bundle **in an inventory slot** and scroll to highlight one of its shown stacks, then right-click with an empty cursor to take it. Selection is sent to the current server menu. Moving the pointer off that slot, shift-moving or hotbar-swapping the Bundle clears the highlight; removing a stack also clears it. [Scroll selection and reset][scroll] · [Screen caller][scroll-caller] · [Server packet][selection-server] · [Menu selection][selection-menu] · [Removal reset][removal]

The tooltip shows at most 12 entries. With more than 12 stored stacks, part of the list is hidden and the tooltip shows a count of the hidden **items**. Scrolling selects only the shown entries; remove newer stacks to expose older hidden ones. The bar indicates stored weight and turns red when full. [Shown-entry limit][shown-count] · [Tooltip and hidden count][tooltip] · [Capacity bar][bars]

### Emptying in the world

With the Bundle in hand, hold **Use** to drop stored stacks into the world. The active use-tick handler starts with one stack, then continues dropping stacks while use continues. It uses the same selected-or-newest removal rule and leaves the Bundle itself in hand. This is a way to unload onto the ground; use the inventory controls when you want items placed into a slot. [Start using][use] · [Removal, drops and repeated use][drop-stack] · [Active tick caller][use-tick-caller]

## Behavior

### Capacity

Normal insertion stops at a total weight of **1**. For an ordinary item, each unit costs **1 divided by that stack's maximum stack size**. The number currently held in the incoming stack does not change its per-item cost. [Weight calculation][weight] · [Insertion limit][insertion]

| Ordinary item's maximum stack size | Space per item | Amount that fills an otherwise empty Bundle |
| --- | --- | --- |
| 64 | 1/64 | 64 items |
| 16 | 1/16 | 16 items |
| 1 | 1 | One item |

For example, 32 items with a maximum stack size of 64 plus eight items with a maximum stack size of 16 fill the Bundle. Different item types share that same allowance. A Beehive or Bee Nest item carrying bees has a special full-capacity cost of 1, regardless of its normal stack size. [Ordinary and bee-container weight][weight]

### Nested bundles and blocked items

You **can put a Bundle inside another Bundle**, but the inner Bundle costs its contents' weight **plus 1/16**. An empty inner Bundle therefore uses the space of four ordinary 64-stackable items; a full inner Bundle cannot fit. Nesting organizes items and consumes extra space. It does not increase the total allowance. To control which is the outer Bundle, hold that outer Bundle on the cursor and left-click the inner Bundle's slot: the cursor item's action runs first. [Nested overhead][nested-cost] · [Nested contents weight][weight] · [Click priority][click-priority] · [Held-Bundle insertion][carried-bundle]

**Shulker Boxes cannot go inside a Bundle**, even when empty or differently colored. The insertion gate checks container eligibility, and every Shulker Box block item rejects it. For portable storage with separate full-stack slots, see the [Shulker Box guide](../blocks/ShulkerBox.md). For placed storage, see [Chest](../blocks/Chest.md) and [Barrel](../blocks/Barrel.md). [Bundle gate][weight] · [Default eligibility][container-eligibility] · [Shulker exclusion][shulker-exclusion]

### Colors and recoloring

Combine **one Bundle and one target dye** in any two crafting-grid slots to produce that colored variant. The input tag includes the uncolored Bundle and all 16 colors. Recoloring copies the input's contents and other saved item components, including a custom name. An already matching colored Bundle does not produce a result from the same dye. Taking the output consumes one Bundle and one dye. [Accepted variants][bundles-tag] · [Matching and assembly][transmute] · [Output and unchanged-result check][transmute-result] · [Component copy][component-copy] · [Ingredient consumption][consume-inputs]

| Variant | Dye for its recipe |
| --- | --- |
| [White](WhiteBundle.md) | [White Dye](WhiteDye.md) · [Exact recipe][white-recipe] |
| [Orange](OrangeBundle.md) | [Orange Dye](OrangeDye.md) · [Exact recipe][orange-recipe] |
| [Magenta](MagentaBundle.md) | [Magenta Dye](MagentaDye.md) · [Exact recipe][magenta-recipe] |
| [Light Blue](LightBlueBundle.md) | [Light Blue Dye](LightBlueDye.md) · [Exact recipe][light_blue-recipe] |
| [Yellow](YellowBundle.md) | [Yellow Dye](YellowDye.md) · [Exact recipe][yellow-recipe] |
| [Lime](LimeBundle.md) | [Lime Dye](LimeDye.md) · [Exact recipe][lime-recipe] |
| [Pink](PinkBundle.md) | [Pink Dye](PinkDye.md) · [Exact recipe][pink-recipe] |
| [Gray](GrayBundle.md) | [Gray Dye](GrayDye.md) · [Exact recipe][gray-recipe] |
| [Light Gray](LightGrayBundle.md) | [Light Gray Dye](LightGrayDye.md) · [Exact recipe][light_gray-recipe] |
| [Cyan](CyanBundle.md) | [Cyan Dye](CyanDye.md) · [Exact recipe][cyan-recipe] |
| [Purple](PurpleBundle.md) | [Purple Dye](PurpleDye.md) · [Exact recipe][purple-recipe] |
| [Blue](BlueBundle.md) | [Blue Dye](BlueDye.md) · [Exact recipe][blue-recipe] |
| [Brown](BrownBundle.md) | [Brown Dye](BrownDye.md) · [Exact recipe][brown-recipe] |
| [Green](GreenBundle.md) | [Green Dye](GreenDye.md) · [Exact recipe][green-recipe] |
| [Red](RedBundle.md) | [Red Dye](RedDye.md) · [Exact recipe][red-recipe] |
| [Black](BlackBundle.md) | [Black Dye](BlackDye.md) · [Exact recipe][black-recipe] |

[Brown Bundle](BrownBundle.md) and the uncolored `minecraft:bundle` are distinct items. The current cauldron interactions do not register a Bundle-washing action; use dye recipes to change between colors. [Item registrations][items] · [Cauldron dispatch][cauldron]

### Saved contents and dropped items

Stored stacks are saved in the Bundle's contents component. If damage destroys a dropped Bundle item entity, its destruction callback releases its stored stacks at that location. They remain exposed to hazards there. Ordinary item expiry follows a direct discard path, so do not rely on a dropped Bundle spilling its contents when it expires. [Persistent contents][save-component] · [Contents serialization][contents-codec] · [Damage callback][destroy-caller] · [Bundle callback][destroy-bundle] · [Released items][release-contents] · [Expiry path][expiry]

## Notes

- The uncolored item ID is `minecraft:bundle`; each color page records its exact ID and recipe.
- This page owns shared Bundle controls and capacity. Color pages cover acquisition and point back here for storage behavior.

Source-reviewed on **2026-10-02** at `1d7e3e91f2a2694339f78b8673993e98d496ca5f`. Checked all 17 registrations and crafting recipes, the Bundle item tag, exact recipe/loot resource scopes, village chest template references, component copying, inventory click/selection callers and item-use/destruction callbacks. No game, browser, inventory, crafting, loot, nesting or recovery test was run. Custom data packs and item components can change these source-defined outcomes.

[items]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/Items.java#L1612-L1662
[weight]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/component/BundleContents.java#L55-L77
[basic-recipe]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/recipe/crafting/bundle.json#L1-L16
[personal-grid]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/inventory/InventoryMenu.java#L48-L53
[personal-crafting]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/inventory/InventoryMenu.java#L80-L85
[village_cartographer]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/loot_table/chests/village/village_cartographer.json
[village_tannery]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/loot_table/chests/village/village_tannery.json
[village_weaponsmith]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/loot_table/chests/village/village_weaponsmith.json
[village_plains_house]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/loot_table/chests/village/village_plains_house.json
[village_desert_house]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/loot_table/chests/village/village_desert_house.json
[village_savanna_house]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/loot_table/chests/village/village_savanna_house.json
[village_snowy_house]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/loot_table/chests/village/village_snowy_house.json
[village_taiga_house]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/loot_table/chests/village/village_taiga_house.json
[loot-menu]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/level/block/entity/RandomizableContainerBlockEntity.java#L84-L93
[loot-unpack]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/RandomizableContainer.java#L72-L89
[category]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1464-L1480
[browser]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[browser-server]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1919
[click-buttons]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java#L407-L442
[carried-bundle]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/BundleItem.java#L49-L85
[resting-bundle]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/BundleItem.java#L87-L126
[removal]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/component/BundleContents.java#L208-L225
[slot-rules]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/inventory/Slot.java#L98-L151
[insertion]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/component/BundleContents.java#L159-L205
[scroll]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/client/gui/BundleMouseActions.java#L26-L73
[scroll-caller]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/client/gui/screens/inventory/AbstractContainerScreen.java#L239-L254
[selection-server]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L548-L551
[selection-menu]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java#L314-L319
[shown-count]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/component/BundleContents.java#L79-L85
[tooltip]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/client/gui/screens/inventory/tooltip/ClientBundleTooltip.java#L97-L140
[bars]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/BundleItem.java#L141-L157
[use]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/BundleItem.java#L128-L139
[drop-stack]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/BundleItem.java#L188-L229
[use-tick-caller]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3168-L3173
[nested-cost]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/component/BundleContents.java#L29-L29
[click-priority]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java#L563-L568
[container-eligibility]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/Item.java#L341-L343
[shulker-exclusion]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/BlockItem.java#L189-L192
[bundles-tag]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/tags/item/bundles.json#L1-L21
[transmute]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/crafting/TransmuteRecipe.java#L38-L77
[transmute-result]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/crafting/TransmuteResult.java#L16-L55
[component-copy]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/ItemStack.java#L627-L637
[consume-inputs]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L82-L108
[white-recipe]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/recipe/crafting/white_bundle.json#L1-L10
[orange-recipe]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/recipe/crafting/orange_bundle.json#L1-L10
[magenta-recipe]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/recipe/crafting/magenta_bundle.json#L1-L10
[light_blue-recipe]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/recipe/crafting/light_blue_bundle.json#L1-L10
[yellow-recipe]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/recipe/crafting/yellow_bundle.json#L1-L10
[lime-recipe]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/recipe/crafting/lime_bundle.json#L1-L10
[pink-recipe]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/recipe/crafting/pink_bundle.json#L1-L10
[gray-recipe]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/recipe/crafting/gray_bundle.json#L1-L10
[light_gray-recipe]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/recipe/crafting/light_gray_bundle.json#L1-L10
[cyan-recipe]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/recipe/crafting/cyan_bundle.json#L1-L10
[purple-recipe]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/recipe/crafting/purple_bundle.json#L1-L10
[blue-recipe]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/recipe/crafting/blue_bundle.json#L1-L10
[brown-recipe]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/recipe/crafting/brown_bundle.json#L1-L10
[green-recipe]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/recipe/crafting/green_bundle.json#L1-L10
[red-recipe]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/recipe/crafting/red_bundle.json#L1-L10
[black-recipe]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/recipe/crafting/black_bundle.json#L1-L10
[cauldron]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/core/cauldron/CauldronInteraction.java
[save-component]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/core/component/DataComponents.java#L209-L211
[contents-codec]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/component/BundleContents.java#L23-L52
[destroy-caller]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/item/ItemEntity.java#L264-L286
[destroy-bundle]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/BundleItem.java#L244-L251
[release-contents]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/ItemUtils.java#L41-L46
[expiry]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/item/ItemEntity.java#L173-L175
