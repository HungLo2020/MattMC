# Inventory controls

Use these mouse and keyboard controls to organize your personal inventory and move supplies into a [Chest](../blocks/Chest.md#placement-and-access). Open your inventory with **Inventory** (default **E**). “On the cursor” means the stack you have picked up inside the screen. The item catalog beside the menu has [separate browser controls](InventoryBrowser.md#finding-and-requesting-an-item). [Inventory opening][open] · [Defaults][defaults]

The key names below are defaults; Inventory, Drop, hotbar and offhand bindings can be changed. **Shift-click uses the actual Shift modifier**, even though MattMC's default **Sneak is Left Ctrl**. Changing Sneak does not change this menu shortcut. These instructions cover ordinary mouse/keyboard use, not touchscreen controls or every custom menu. [Bindings][defaults] · [Rebinding][rebind] · [Click routing][mouse-click] · [Modifier checks][modifiers]

## Pick up, split and place

For ordinary inventory or chest slots, without holding Shift:

- **Left-click with an empty cursor:** pick up the whole stack.
- **Right-click with an empty cursor:** pick up half, rounded up. A stack of 5 gives you 3 and leaves 2.
- **Left-click while carrying items:** place as many as fit into an empty slot or a matching stack.
- **Right-click while carrying items:** place one into an empty slot or matching stack.
- Clicking a different stack can exchange it with the carried stack, if the slot accepts the incoming items and the whole carried stack fits. [Ordinary clicks][pickup] · [Slot limits][slot-insert]

Merging requires the **same item and the same saved components**, not just a similar name or icon. Items with different saved properties can remain separate. Every destination also has a stack limit; anything that does not fit stays on the cursor. [Matching][matching] · [Insertion][slot-insert]

## Move stacks quickly

With an empty cursor, **hold Shift and click a stack** to quick-transfer it. The current menu chooses the destination:

- **Chest open:** chest slots transfer into your main inventory and hotbar; your inventory and hotbar transfer into the chest. This does not automatically equip armor. [Chest destinations][chest-move]
- **Personal inventory open:** ordinary items move between the main inventory and hotbar. An item assigned to an armor or offhand equipment slot first tries that slot when it is empty. Crafting ingredients and equipped armor move back into the main inventory/hotbar. [Personal destinations][inventory-move] · [Equipment assignment][equipment]
- **Crafting Table open:** items from your inventory first try the crafting grid; if that fails, they try the other main-inventory/hotbar section. Grid ingredients and results move back into your inventory/hotbar. Quick-transfer is not automatic recipe arrangement; use the [crafting guide](../crafting/Crafting.md#reading-recipes) for the pattern. [Table destinations][table-move]

Transfers fill matching stacks before using empty slots. A full destination can leave part or all of the original stack behind. Other workstations and custom menus choose their own destinations. [Transfer helper][move-helper]

## Spread and collect items

Pick up a stack, then hold a mouse button and drag across eligible slots before releasing:

- **Left-drag:** divide the carried count evenly, rounded down, adding that share to each selected slot. For example, 10 items across 3 empty ordinary slots place 3 in each and leave 1 on the cursor.
- **Right-drag:** add one item to each selected slot, while supplies last.

Slots must be empty or hold matching items and components, accept the carried item, and allow dragging. Item and slot caps still apply. Left-drag adds shares; it does not equalize stacks that started with different counts. Items left over through rounding or capacity stay on the cursor. [Drag selection][drag-client] · [Distribution and caps][drag-server] · [Share sizes][drag-count]

**Double-left-click an ordinary stack without Shift** to pick it up and collect matching items from eligible slots in the open menu, up to that item's stack limit. With a chest open, collection can include both the chest and your inventory. It respects pickup permissions and menu exclusions; the personal and table crafting results are excluded. [Double-click dispatch][double-click] · [Collection][collect] · [Personal exclusion][inventory-collect] · [Table exclusion][table-collect]

## Hotbar, offhand and dropping

With an **empty cursor**, hover over an ordinary slot and press a configured **hotbar key** (defaults **1–9**) to exchange its contents with that hotbar slot. Use **Swap Item With Offhand** (default **F**) to exchange with the offhand. These swaps still obey the hovered slot's insertion, pickup and capacity rules. [Key actions][keys] · [Swap handling][swaps]

To deliberately drop items into the world:

- With an **empty cursor**, hover over an ordinary stack and press **Drop** (default **Q**) for one item, or **Control + Drop** for the whole stack. On macOS the GUI uses **Command** for this modifier. It is independent of the Sneak binding, and the slot must allow removal. [Drop dispatch][keys] · [Drop handling][drop] · [Platform modifier][platform-modifier]
- With a stack **on the cursor**, click the empty area outside the inventory window, **away from the item-browser panel**: left-click drops the carried stack; right-click drops one. [Outside routing][release] · [Outside drops][outside-drop]

**Do not use the item-browser panel as a ground-drop target.** Releasing a carried stack there clears the client cursor and sends a separate deletion request. Server admission uses the browser's [Creative ability gate](InventoryBrowser.md#mode-and-permission-limits); cursor disappearance alone does not establish server deletion in Survival. [Panel interception][panel-release] · [Delete sender][delete-sender] · [Admission gate][creative-gate] · [Admitted deletion][delete-server]

If the browser search field has focus, press Enter to leave it before using inventory keys. [Search focus][search-focus]

## Slots and items with special rules

- **Crafting results** accept no inserted items. Taking a result consumes recipe ingredients and takes its output batch, so right-click or Q need not remove just half or one result item. Shift-click and Control + Drop can repeat while matching results replenish. Use ordinary storage slots when you need precise splitting or one-stack dropping. See [Using the grid](../blocks/CraftingTable.md#using-the-grid). [Result slot][result-slot] · [Output batch][result-batch] · [Ingredient consumption][result-take] · [Repeated transfer][quick-repeat] · [Repeated drops][drop]
- **Armor slots** accept suitable equipment, hold one item, and can block removal through an armor-change prevention enchantment. A hotbar swap does not bypass these checks. If the incoming stack exceeds a restricted slot's limit, only the allowed count enters; the displaced item can go back into your inventory or drop if there is no room. [Armor rules][armor] · [Swap checks][swaps]
- **Bundles** intercept ordinary clicks to insert or remove their contents. For example, right-clicking a Bundle with an empty cursor takes a stored stack rather than half of the Bundle item. Follow the [Bundle inventory controls](../items/Bundle.md#inventory-controls). [Override priority][override] · [Bundle actions][bundle]

Related: [Mechanics](Mechanics.md) · [Chest storage](../blocks/Chest.md) · [Crafting](../crafting/Crafting.md) · [Inventory item browser](InventoryBrowser.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `cc140840a21e5c6c932c23abf34124418d6506b0`. Checked active screen input, menu packet registration and server dispatch, ordinary slot handling, personal/chest/table destinations, and Bundle overrides. No game, inventory, touchscreen or multiplayer interaction test was run. Server menu validity, permissions, item components and custom menu rules can limit an action. [Click sender][sender] · [Packet registration][packet] · [Server validation][server]

[open]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/Minecraft.java#L2059-L2066
[defaults]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/Options.java#L559-L590
[mouse-click]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/gui/screens/inventory/AbstractContainerScreen.java#L394-L468
[modifiers]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/input/InputWithModifiers.java#L51-L61
[pickup]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java#L438-L475
[slot-insert]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/Slot.java#L126-L151
[matching]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/ItemStack.java#L662-L672
[chest-move]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/ChestMenu.java#L51-L99
[inventory-move]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/InventoryMenu.java#L102-L158
[equipment]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3575-L3585
[table-move]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/CraftingMenu.java#L112-L155
[move-helper]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java#L639-L701
[drag-client]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/gui/screens/inventory/AbstractContainerScreen.java#L526-L541
[drag-server]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java#L341-L404
[drag-count]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java#L727-L744
[double-click]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/gui/screens/inventory/AbstractContainerScreen.java#L566-L589
[collect]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java#L540-L559
[inventory-collect]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/InventoryMenu.java#L163-L166
[table-collect]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/CraftingMenu.java#L158-L161
[keys]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/gui/screens/inventory/AbstractContainerScreen.java#L773-L800
[swaps]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java#L476-L512
[drop]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java#L519-L539
[platform-modifier]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/input/InputQuirks.java#L9-L15
[release]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/gui/screens/inventory/AbstractContainerScreen.java#L543-L650
[outside-drop]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java#L407-L417
[panel-release]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/gui/screens/inventory/AbstractContainerScreen.java#L654-L666
[delete-sender]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L501-L509
[creative-gate]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L40-L58
[delete-server]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1899
[search-focus]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L478-L523
[result-slot]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L24-L27
[result-batch]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/ResultContainer.java#L38-L41
[result-take]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L82-L110
[quick-repeat]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java#L418-L432
[armor]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/ArmorSlot.java#L32-L53
[override]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java#L563-L568
[bundle]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/BundleItem.java#L49-L126
[sender]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L441-L472
[packet]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L75-L81
[server]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1802-L1837
[rebind]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/gui/screens/options/controls/KeyBindsScreen.java#L62-L89
