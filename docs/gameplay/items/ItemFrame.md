# Item Frame

An **Item Frame** displays one item on a wall, floor, or ceiling. Use frames to label storage, show collected equipment, or arrange [Maps](Map.md). Placed frames are entities, and an occupied frame can also provide a rotation-based [Comparator](../blocks/RedstoneComparator.md) input. [Placement][placement] · [Display and signal][frame-use]

## Obtaining

Surround **one Leather with eight Sticks** in a 3 × 3 crafting grid to make **one Item Frame**. [Recipe][recipe]

| Left | Center | Right |
| --- | --- | --- |
| Stick | Stick | Stick |
| Stick | Leather | Stick |
| Stick | Stick | Stick |

Other reviewed sources are:

- An **Expert, level-4 Cartographer** can offer one frame for a base price of **7 Emeralds**. This is an offer in the normal trade pool; check the actual Villager's offers and current price. [Trade listing][trade] · [Offer selection][trade-selection]
- An [End Ship](../structures/EndCity.md#chest-loot-and-the-elytra-frame) generates an ordinary frame holding Elytra. In Survival, first recover the Elytra, then break the empty frame to collect the frame itself. Finding an End City does not guarantee a ship. [Ship reward][ship] · [Drops][frame-drops]
- Both frame variants are category-listed in the Creative catalog. Follow the [inventory browser's mode and permission limits](../mechanics/InventoryBrowser.md#mode-and-permission-limits): ordinary Survival catalog visibility does not establish server-accepted item insertion. [Catalog entries][catalog]

One ordinary frame is also an ingredient in the [Glow Item Frame](GlowItemFrame.md#obtaining) recipe. You craft that conversion using the inventory item; using Glow Ink on a placed frame instead follows its display interaction.

## Usage

### Placing a frame

Use the frame on the intended face of a support block. Side faces produce wall displays; top and bottom faces produce floor and ceiling displays. The space must pass collision and hanging-entity overlap checks, and the player must be allowed to use the item there. A full solid block is a straightforward support for a first display. [Placement][placement] · [All-face placement permission][placement-permission] · [Support check][support]

To place on an interactive block such as a Chest, activate your configured **Sneak/Crouch** control while using the frame on the block. This bypasses the ordinary block interaction and allows the held frame's placement attempt. [Block-use dispatch][block-use]

### Inserting, rotating, and hiding

Aim at the placed frame and use the interaction control:

| Frame and action | Result |
| --- | --- |
| Empty frame, holding an item | Displays one item; consumes one from the held stack in Survival |
| Empty frame, empty hand | No change |
| Occupied frame, ordinary use | Advances the displayed item's rotation |
| Occupied frame, Sneak/Crouch + use | Toggles the frame's visibility without changing its rotation |
| Occupied frame, attack | Removes its displayed item under the drop rules below |

The frame stores **one item**, even when you insert from a larger stack. It has eight rotation values; ordinary item models turn in **45-degree steps**. Using an occupied frame with another item in hand does not replace the display. Remove the old item first. [One-item storage][one-item] · [Interaction][frame-use] · [Rotation rendering][render]

**The visibility toggle is a MattMC interaction.** Put an item in the frame first, then Sneak/Crouch-use it to hide the frame surround while retaining the displayed item. Repeat while it is occupied to restore the surround. Sneaking with an item at an empty frame still inserts that item; it does not toggle visibility. Emptying a hidden frame does not reset its visibility, so refill it before using this toggle again. [Server interaction][frame-use] · [Client request][client-use] · [Server dispatch][server-use] · [Hidden-frame rendering][render]

### Displaying maps

Insert a filled [Map](Map.md) to display its saved terrain. The map display turns in **90-degree steps**, giving four visual orientations, while the frame still retains eight rotation values for its Comparator reading. This difference matters when using maps as redstone selectors. Surveying, copying, scaling, and locking remain covered by the Map and [Cartography Table](../blocks/CartographyTable.md) guides. [Map rendering][render] · [Frame rotation and signal][frame-use]

Insertion can fail when that map's saved data already has **256 tracked decorations**. This is the checked map-data limit, not a general limit of 256 frames in a world. [Insertion check][frame-use] · [Tracked-decoration count][map-limit]

## Behavior

### Support, removal, and drops

An ordinary frame needs a valid supporting block and clear placement space. The support check accepts a solid block; a horizontal frame also accepts a Repeater or Comparator support. Losing valid support causes the frame to break when its periodic server check runs. Frames facing the same direction cannot overlap another hanging entity in the checked space. [Support][support] · [Overlap check][overlap] · [Periodic break check][attached]

For a normally placed, unmodified frame in Survival, with entity drops enabled:

1. Attack the occupied frame once to release its displayed item while leaving the frame in place
2. Attack the now-empty frame to break it and recover one frame

Removing its support can drop both frame and contents together. Keep a safe floor beneath valuable displays, especially in an End Ship. The default displayed-item drop chance is 100%, but a frame's saved data can change it. A player with infinite materials, normally Creative, does not receive these ordinary break drops; disabling entity drops also suppresses them. Explosions do not use the normal item-only first-hit branch. [Attack and drops][frame-drops] · [Default and saved settings][saved] · [Attack dispatch][attack]

Frames marked `Fixed` in saved or supplied entity data behave differently: normal interaction passes without inserting, rotating, or toggling visibility, support checks are bypassed, and ordinary damage cannot remove them. Do not use such a frame to test normal Survival controls. [Fixed-frame gates][support] · [Fixed damage][frame-drops] · [Fixed interaction][frame-use]

### Reading rotation with a Comparator

Mount the frame on one side of a **redstone-conducting block**, then place a Comparator on the opposite side with its **rear input toward that block**. The frame must face away from the block on the far side of the Comparator. The reader searches that far-side space for exactly one frame with the matching direction; floor and ceiling frames do not match a horizontal Comparator's search. [Frame lookup][comparator]

An empty frame supplies **0**. An occupied frame supplies **1–8**, according to rotation; a newly placed frame with its first item supplies 1, and ordinary uses cycle through 2–8 and back to 1. Item type and quantity do not make it a container-fullness reading. Inserting/removing the item and rotating notify nearby Comparators. The visibility toggle changes neither the item nor its rotation. [Signal and interaction][frame-use] · [Content and rotation updates][one-item]

Keep the example block unpowered and the Comparator's side inputs clear when checking the values. The through-block frame lookup requires the initial rear signal to be below 15 and is bypassed when the adjacent block already supplies its own analog reading. Another analog source in the searched space can also affect the selected value. The [Comparator guide](../blocks/RedstoneComparator.md#compare-and-subtract-modes) owns the subsequent compare/subtract rules and update timing; the frame's 1–8 value is an input, not an unconditional final output. [Input selection][comparator]

## Notes

- This item is registered as `minecraft:item_frame`; the glowing variant is a separate item and entity
- Unmodified inventory frames stack to **64**, but each placed frame displays only one item
- [Glow Item Frames](GlowItemFrame.md) inherit these placement, interaction, support, and Comparator rules

[Registrations][registration] · [Default stack size][stack] · [Glow inheritance][glow]

Related: [Glow Item Frame](GlowItemFrame.md) · [Map](Map.md) · [Redstone Comparator](../blocks/RedstoneComparator.md) · [End City](../structures/EndCity.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked recipes, item/entity wiring, trade selection, the End Ship reward, client/server interaction and attack dispatch, support/drop checks, and the active render submission route. No in-game crafting, placement, hidden-frame, map, drop, trade, or circuit test was run. Data packs, entity data, permissions, and game rules can change the described conditions.

[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/item_frame.json
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2053-L2054
[trade]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L340-L445
[trade-selection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/Villager.java#L818-L842
[ship]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/EndCityPieces.java#L369-L387
[catalog]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1116-L1117
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/HangingEntityItem.java#L34-L75
[placement-permission]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemFrameItem.java#L15-L18
[block-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L340-L399
[support]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/decoration/ItemFrame.java#L117-L145
[overlap]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/decoration/HangingEntity.java#L98-L105
[attached]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/decoration/BlockAttachedEntity.java#L39-L90
[frame-drops]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/decoration/ItemFrame.java#L154-L243
[one-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/decoration/ItemFrame.java#L274-L328
[saved]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/decoration/ItemFrame.java#L331-L361
[frame-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/decoration/ItemFrame.java#L363-L415
[client-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L419-L438
[server-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1703-L1759
[attack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/decoration/BlockAttachedEntity.java#L60-L90
[render]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/entity/ItemFrameRenderer.java#L75-L140
[map-limit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/saveddata/maps/MapItemSavedData.java#L463-L465
[comparator]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ComparatorBlock.java#L97-L129
[stack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L381-L390
[glow]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/decoration/GlowItemFrame.java#L12-L50
