# Shulker Box

A **Shulker Box** is portable storage with **27 slots**. Place it to load or unload items, then break and collect the box to carry its contents. The uncolored box and all **16 dyed variants** share the same capacity and storage behavior. Their item forms each stack to one. [Container and menu][container] · [Item registration][items]

## Obtaining and colors

Make the first box using the [basic recipe on the Shulker Shell page](../items/ShulkerShell.md#crafting-a-shulker-box). Shells come from [Shulkers](../mobs/Shulker.md), including the inhabitants of [End Cities](../structures/EndCity.md).

To color a box, combine **one box and one matching dye** in two crafting slots. The input tag accepts the uncolored box and all 16 colors, so a box can be recolored directly. The recipe returns one box and preserves the input's saved components, including its contents and custom name. No emptying step is required. Dyeing a box **the color it already has produces no output**. [Box tag][item-tag] · [Example color recipe][red-recipe] · [Recipe matching][transmute] · [Result preservation][transmute-result]

| Item variant | Dye for that color |
| --- | --- |
| [Uncolored](../items/ShulkerBox.md) | Basic crafting or washing; no dye |
| [White](../items/WhiteShulkerBox.md) | White Dye |
| [Orange](../items/OrangeShulkerBox.md) | Orange Dye |
| [Magenta](../items/MagentaShulkerBox.md) | Magenta Dye |
| [Light Blue](../items/LightBlueShulkerBox.md) | Light Blue Dye |
| [Yellow](../items/YellowShulkerBox.md) | Yellow Dye |
| [Lime](../items/LimeShulkerBox.md) | Lime Dye |
| [Pink](../items/PinkShulkerBox.md) | Pink Dye |
| [Gray](../items/GrayShulkerBox.md) | Gray Dye |
| [Light Gray](../items/LightGrayShulkerBox.md) | Light Gray Dye |
| [Cyan](../items/CyanShulkerBox.md) | Cyan Dye |
| [Purple](../items/PurpleShulkerBox.md) | Purple Dye |
| [Blue](../items/BlueShulkerBox.md) | Blue Dye |
| [Brown](../items/BrownShulkerBox.md) | Brown Dye |
| [Green](../items/GreenShulkerBox.md) | Green Dye |
| [Red](../items/RedShulkerBox.md) | Red Dye |
| [Black](../items/BlackShulkerBox.md) | Black Dye |

Each variant page links its exact bundled recipe and block loot table. Uncolored and Purple are different item variants; washing any dyed box returns the uncolored one.

### Washing off a color

Use a **held dyed box on a Water Cauldron** with at least one water level. In Survival, this replaces it with one uncolored Shulker Box, keeps its stored items and other saved components, and consumes **one water level**. At the last level, the cauldron becomes empty. Washing does not return the dye. [All-color dispatch and washing][cauldron] · [Water level][water-level] · [Component copy][stack-copy]

In Creative, the same handler retains the original held colored box and adds the uncolored result to your inventory, or drops that result if it cannot fit. It still lowers the water. This follows Creative's non-consumption rule; it is different from the Survival replacement. [Result handling][item-utils] · [Creative consumption][consume]

## Placement and opening

Place the box on a block face. Its lid faces the side you clicked, so boxes can open upward, downward, or horizontally. Use the placed box to access its three rows of nine slots. It does not open as a carried inventory item through the ordinary block-item use path. [Placement and opening][block] · [Menu][menu] · [Block-item placement][block-item]

A closed box needs clearance for the lid's **half-block extension** in its facing direction. A full block immediately in that space prevents opening. Clear the lid's path rather than only checking the block above a sideways-facing box. The opening animation can push nearby entities, so avoid placing it against your only safe footing on a ledge. [Clearance and facing][block] · [Lid animation and pushing][container]

## Breaking and carrying contents

For ordinary Survival collection with block drops enabled, a Shulker Box needs **neither Silk Touch nor a minimum tool tier**. A pickaxe is the appropriate faster mining tool, but the block has no correct-tool requirement. Its loot returns a box of the same color with its stored contents. [Block properties][properties] · [Pickaxe membership][pickaxe] · [Tool gate][player] · [Uncolored loot][loot]

The normal loot copies the box's contents, custom name, lock, and any pending container-loot data. Placing the resulting item restores those supported components to the new block entity. This is not a promise that every command-added component survives normal breaking: the loot explicitly selects those four fields. [Loot copying][copy-components] · [Stored contents and name][base-container] · [Loot state][random-container] · [Placement restoration][block-item]

**Creative breaking has a separate rule:** a nonempty placed box drops a packed box; an empty one has no ordinary block-item drop. This special nonempty drop is created before the usual Creative suppression of block drops. [Box destruction callback][block] · [Destruction caller][destroy] · [Creative flag][player]

Collect the dropped item before leaving. Keeping contents through a block break does not protect the item from later damage or disappearance.

## Nesting and automation

You cannot put a Shulker Box **inside another Shulker Box** through the normal menu, even when the inner box is empty or another color. The menu checks the item's container eligibility, and all Shulker Box block items reject it. Hopper insertion separately rejects Shulker Box block items too. [Menu slot][slot] · [Item restriction][block-item] · [Sided insertion][container]

[Hoppers](Hopper.md) can insert ordinary allowed items into a placed box and extract its contents. Every face exposes all 27 slots; the box's lid direction does not assign special input or output slots. A hopper below pulls from the box above it, and a hopper pointing into the box pushes into it. Lid clearance is an opening-menu check, not a hopper-transfer requirement. Hopper power, cooldown, available space, and stack compatibility still matter. [Container faces][container] · [Transfer callers][hopper]

A Dispenser can place an available box item using the ordinary placement/component-restoration path. All 17 box variants have that behavior registered. This does not by itself describe or validate a complete loading machine. [Dispenser registration][dispense-registration] · [Placement behavior][dispense]

### Dispenser placement and Piston recovery

A Dispenser targets the block space **directly in front of its opening**, but that does not always become the box's lid direction. For an ordinary box item, **any non-air block immediately below the destination makes the lid face upward**; with air below, the lid faces the Dispenser's output direction. A solid floor is therefore a simple way to get an upright box from a horizontal Dispenser. Leave the lid clearance described [above](#placement-and-opening). [Destination and clicked face][cycle-dispense] · [Box facing][cycle-facing]

If that destination is not replaceable, placement fails and the selected box **stays in the Dispenser**; this handler does not fall back to ejecting it as a loose item. Successful placement consumes one box item and restores its supported saved components, so a filled box can be placed with its cargo. A [Dropper](DispenserAndDropper.md#dropper-transfer-and-ejection) does not perform this placement action. [Replaceability check][cycle-context] · [Failure, restoration and consumption][cycle-place]

An extending **ordinary Piston** can break a Shulker Box into its item instead of sliding the placed container along. All 17 variants have the native **Destroy** push reaction, which is handled before the ordinary block-entity movement rejection. With bundled loot and block drops enabled, the Piston supplies the box's block entity to the loot path, preserving the same saved contents/name/lock/loot fields as ordinary collection. A Sticky Piston is unnecessary. [Native variants][cycle-native] · [Push reaction][cycle-physics] · [Piston eligibility][cycle-push] · [Destruction and block entity][cycle-break] · [Box loot][cycle-loot] · [Drop spawning gate][cycle-drops]

For a small **source-derived, untested manual cycle**:

1. Face a horizontal Dispenser into a clear cell over a solid floor. Aim a separately controlled Piston at that same cell from a perpendicular side; start with its head retracted and leave room above the box's lid.
2. Load only the intended box and activate the Dispenser once. After placement, fill the box by hand or through the [Hopper rules](#nesting-and-automation) above.
3. Stop transfers, close the box and extend the Piston. Collect the dropped packed box before continuing; the stored cargo is not a promise of protection for the loose item.
4. Retract the Piston fully before dispensing another box. A non-replaceable block in the cell, including the extended head, prevents the next ordinary placement. Keep the two controls independent rather than driving both devices with one unexamined pulse.

Try the sequence with an empty box before valuable cargo. This describes placement and recovery, not a tested automatic loader, full-box detector, collection mechanism or timing circuit. Disabling block drops or changing loot can remove the expected recovery item. [Dispenser controls](DispenserAndDropper.md#facing-loading-and-activation) · [Piston controls](Pistons.md#facing-and-power) · [Dropped-item hazards](#fire-explosions-and-lost-items)

## Fire, explosions, and lost items

A dropped box has no default fire- or explosion-resistance component. If accepted damage destroys that **item entity**, the container-item destruction handler releases its stored items as separate drops at the same position. Those drops remain exposed to the fire, lava, further blasts, or other hazards there; this is not safe recovery storage. [Item defaults][items] · [Resistance check][resistance] · [Damage and destruction][item-entity] · [Contents release][block-item] · [Released entities][item-utils]

An explosion breaking the **placed block** uses its block-loot path, which can carry the contents into a box drop. That is a separate step from damage to a dropped box. The ordinary dropped-item expiry path simply discards the item and does not call the contents-release handler. Recover misplaced boxes promptly rather than relying on their contents spilling later. [Block explosion path][explosion] · [Item expiry][item-entity]

## Sources and verification

Dispenser placement and Piston recovery were additionally source-reviewed on **2026-10-15** at `729f781906b5545735eee043ae2a4d085f637f18`. Checked current native push properties, placement direction and rejection, component restoration, Piston destruction and the packed-box loot/removal path. No placement-cycle, cargo-recovery, circuit, timing or item-collection test was run; the earlier family-wide review below retains its original scope.

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Reviewed all 17 item/block variants and loot tables, all 16 color recipes, Water Cauldron dispatch, component collection/restoration, live break/use callbacks, and hopper/dispenser callers. No in-game storage, coloring, washing, break/drop, Creative, nesting, automation, fire, explosion, or expiry test was run. Custom loot, recipes, components, game rules, and external editing tools can change these outcomes.

[cycle-dispense]: https://github.com/HungLo2020/MattMC/blob/729f781906b5545735eee043ae2a4d085f637f18/src/main/java/net/minecraft/core/dispenser/ShulkerBoxDispenseBehavior.java#L16-L34
[cycle-facing]: https://github.com/HungLo2020/MattMC/blob/729f781906b5545735eee043ae2a4d085f637f18/src/main/java/net/minecraft/world/level/block/ShulkerBoxBlock.java#L92-L104
[cycle-context]: https://github.com/HungLo2020/MattMC/blob/729f781906b5545735eee043ae2a4d085f637f18/src/main/java/net/minecraft/world/item/context/DirectionalPlaceContext.java#L14-L27
[cycle-place]: https://github.com/HungLo2020/MattMC/blob/729f781906b5545735eee043ae2a4d085f637f18/src/main/java/net/minecraft/world/item/BlockItem.java#L47-L102
[cycle-native]: https://github.com/HungLo2020/MattMC/blob/729f781906b5545735eee043ae2a4d085f637f18/src/main/rust/content/block/definitions/catalog.rs#L710-L726
[cycle-physics]: https://github.com/HungLo2020/MattMC/blob/729f781906b5545735eee043ae2a4d085f637f18/src/main/rust/content/block/definitions/physics.rs#L702-L708
[cycle-push]: https://github.com/HungLo2020/MattMC/blob/729f781906b5545735eee043ae2a4d085f637f18/src/main/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java#L243-L260
[cycle-break]: https://github.com/HungLo2020/MattMC/blob/729f781906b5545735eee043ae2a4d085f637f18/src/main/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java#L284-L299
[cycle-loot]: https://github.com/HungLo2020/MattMC/blob/729f781906b5545735eee043ae2a4d085f637f18/src/main/resources/data/minecraft/loot_table/blocks/shulker_box.json#L1-L28
[cycle-drops]: https://github.com/HungLo2020/MattMC/blob/729f781906b5545735eee043ae2a4d085f637f18/src/main/java/net/minecraft/world/level/block/Block.java#L420-L425

Related: [Shulker Shell](../items/ShulkerShell.md) · [Shulker](../mobs/Shulker.md) · [Chest](Chest.md) · [Hopper](Hopper.md) · [Blocks](Blocks.md)

[container]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/ShulkerBoxBlockEntity.java
[items]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L772-L822
[item-tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/item/shulker_boxes.json
[red-recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/red_shulker_box.json
[transmute]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/crafting/TransmuteRecipe.java
[transmute-result]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/crafting/TransmuteResult.java
[cauldron]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/core/cauldron/CauldronInteraction.java#L146-L290
[water-level]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/LayeredCauldronBlock.java#L105-L109
[stack-copy]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ItemStack.java#L627-L637
[item-utils]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ItemUtils.java#L15-L45
[consume]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1080
[block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/ShulkerBoxBlock.java
[menu]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/inventory/ShulkerBoxMenu.java
[block-item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/BlockItem.java
[properties]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L7215-L7225
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[player]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/player/Player.java
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/shulker_box.json
[copy-components]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/storage/loot/functions/CopyComponentsFunction.java
[base-container]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/BaseContainerBlockEntity.java#L146-L163
[random-container]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/RandomizableContainerBlockEntity.java#L95-L110
[destroy]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L256-L296
[slot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/inventory/ShulkerBoxSlot.java
[hopper]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L106-L342
[dispense-registration]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L319-L323
[dispense]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/core/dispenser/ShulkerBoxDispenseBehavior.java
[item-entity]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/item/ItemEntity.java#L161-L291
[resistance]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ItemStack.java#L1097-L1104
[explosion]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L176-L199

Additional wiring: [Water Cauldron registration](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L2531-L2535), [cauldron item dispatch](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/AbstractCauldronBlock.java#L55-L61), [interaction bootstrap](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/Bootstrap.java#L49-L57), [transmute serializer registration](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/crafting/RecipeSerializer.java#L28), [component collection and application](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/BlockEntity.java#L285-L326), and [block-tag members](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/shulker_boxes.json)
