# Grindstone

A Grindstone (`minecraft:grindstone`) repairs matching equipment and removes non-curse enchantments. It does not charge experience levels. Use an [Anvil](Anvil.md) instead when you want to retain and combine useful enchantments or change an item's name.

## Crafting and collecting

At a Crafting Table, place **Stick–Stone Slab–Stick** across the top row and **Plank–empty–Plank** below. This makes one Grindstone. The recipe uses the exact Stone Slab item and the planks tag; another stone-looking slab is not an automatic substitute. Integrated planks missing from that tag are not accepted by appearance alone.

Use a **pickaxe** to collect the block: it requires the correct tool for drops and belongs to the pickaxe mining tag. Its loot table returns the Grindstone item, subject to explosion survival.

It can be placed in floor, wall, or ceiling orientations. The active Grindstone survival check always returns true, so removing the neighboring attachment block does not itself make the Grindstone break. It is not a falling block like an anvil.

## Removing enchantments

1. Put one enchanted item in either input slot
2. Inspect the result carefully: **all non-curse enchantments will be removed together**
3. Take the output to consume the input and receive the result

The bundled curse tag contains Curse of Binding and Curse of Vanishing. These stay on the item; a Grindstone is not a curse-removal tool. An Enchanted Book with no enchantments remaining becomes an ordinary Book. A book that retains a curse remains enchanted.

A lone unenchanted damaged item does not produce a repair result. Each input stack must contain at most **one item** for the menu to calculate an output.

A **curse-only item still offers an output**, even though its curse remains and no enchantment experience is returned. Taking that result can recalculate prior work, but does not repair a lone damaged item. A fully repaired enchanted item is also eligible for disenchantment; current damage is not required for the single-item operation. [Single-item validation][current-inputs] · [Curse retention and prior work][current-removal] · [Experience calculation][current-consumption]

## Repairing two matching items

Place two damageable items of the **same item type** in the inputs. Their remaining durability is added, plus a bonus of **5% of the larger maximum durability, rounded down**. The output cannot exceed its resulting maximum durability. Both inputs are consumed when you take the result.

This repair also removes non-curse enchantments. Curses can carry across from either input. A curse already present on the upper item is retained rather than upgraded from the lower donor.

The result starts from the upper item's components, including its custom name. Other data from the lower item is not generally copied across. The durability and enchantments are then adjusted by the operation. For unusual component-modified items, the code uses the larger of the two maximum-durability values; inspect the result rather than assuming identical-looking items have identical data.

The ordinary repair bonus is smaller than the Anvil's 12% matching-item bonus, but the Grindstone does not charge levels. Choose based on the enchantments and item data you want to keep, not durability alone.

### Check before sacrificing a second item

Two fully repaired matching damageable items can still produce a result. **The Grindstone does not require a durability improvement**, so taking that output consumes both inputs even if the remaining item gains no durability. For a single enchanted item, leave the other slot empty unless you intend to sacrifice its contents. [Matching-item result][current-merge] · [Input consumption][current-consumption]

Two ordinary Enchanted Books do **not** produce an output together, even if their enchantments match. They are non-damageable and stack to one; the two-input path rejects that combination. Disenchant them one at a time, or use an [Anvil to combine books](../mechanics/AnvilMechanics.md#combining-enchantments). The same restriction applies to other non-damageable items whose maximum stack size is one. For stackable non-damageable enchanted items, both one-item inputs must match in item type and all components; that special path returns two processed items with any curses retained. [Two-input validation][current-merge] · [Matching components][current-matching] · [Enchanted Book registration][current-book]

## Experience returned

Taking a valid result awards experience orbs at the Grindstone. It is a refund calculation from the **non-curse enchantments on both inputs**, not a return of exactly the experience you originally spent.

For each non-curse enchantment, the code adds its minimum enchanting cost at its current level. Let that total be `S`. If `S` is positive, let `J = ceil(S / 2)`; the award is a uniformly selected whole number from **J through 2J − 1 experience points**. With no non-curse value, the award is zero. Curse removal is not occurring and curses contribute no experience.

This is experience **points**, unlike the whole-level payment shown by an Anvil. No level threshold or fuel is required by the Grindstone menu.

## Prior work and handling inputs

The result's prior-work penalty is recalculated from its remaining curse count. With no curses it becomes **0**; with one distinct curse it becomes **1**; with two it becomes **3**. Thus a Grindstone can clear ordinary prior-work penalties, but this does not preserve the useful enchantments it removes.

There is no rename control in this menu. A single-item operation preserves that input's name; a two-item operation starts with the upper item's name and other components. Use the Anvil when changing the name is your goal.

Taking the result clears both input slots. Closing the menu before committing returns or drops the input contents through the standard container cleanup. Do not use the slots as storage. The completed-use path has no Anvil-style wear roll.

**Taking the output normally consumes the inputs in Creative too.** The completed-use callback clears both input slots without a Creative exemption. [Completed-use handling][current-consumption]

## If an input or output is missing

- **The item will not enter a slot:** it must be damageable or actually hold an applied or stored enchantment. An ordinary plain Book is neither. [Slot admission][current-slots] · [Enchantment check][current-enchantments]
- **One unenchanted item gives no output:** damage alone is insufficient for a single input, whether the item is partly worn, fully broken, or fully repaired. Supply a matching repair donor or choose another [repair method](../mechanics/Durability.md#choose-a-repair-method). [Single-item validation][current-inputs]
- **Two items give no output:** check that they are the same item type and that each slot contains only one item. For non-damageable items, also check the stack-size and exact-component restrictions above. [Input counts][current-inputs] · [Matching-item validation][current-merge]
- **The curse or apparent result does not change:** curses remain, and a curse-only output gives no XP. A valid preview does not promise a repair improvement. [Curse handling][current-removal] · [Experience calculation][current-consumption]

## Automation and redstone

The slots belong to the open player menu, not to an inventory stored in the placed block. **Hoppers cannot insert into or extract from the Grindstone menu**, and a Comparator cannot read its inputs or output. Taking the result through the menu is what commits the repair or disenchantment. [Menu-local inputs][current-slots] · [Opening the menu][current-block] · [Native block policy][current-catalog] · [No stored block inventory][current-policy] · [Hopper inventory lookup][current-hopper] · [Comparator behavior][current-comparator]

A Dropper aimed at a bare Grindstone ejects its item instead of filling the menu slots. A separate container entity at that location is a different destination; it does not give automation access to the Grindstone menu. [Dropper destination check][current-dropper] · [World container lookup][current-hopper]

**Pistons cannot push or pull the Grindstone.** Its active block definition blocks piston movement. Mine and replace it when rearranging a workshop; removing its attachment block alone does not break it, as explained under [crafting and collecting](#crafting-and-collecting). [Native piston rule][current-physics] · [Piston movement check][current-piston]

## Weaponsmith job site

A Grindstone is a **Weaponsmith** job-site block, with room for one villager claim. Its floor, wall and ceiling states are all registered as that job site. Use the [Villager guide](../mobs/Villager.md#professions-and-job-sites) for claiming and access, and [employment and changing jobs](../mobs/Villager.md#employment-and-changing-jobs) before removing a trading villager's workstation. [Job-site states and capacity][current-poi] · [Profession matching][current-profession]

## Related pages

- [Enchanted Book recycling](../items/EnchantedBook.md#recycling-unwanted-books): when a book can return to a plain Book
- [Durability and repair choices](../mechanics/Durability.md#choose-a-repair-method)

- [Equipment curses](../enchanting/EquipmentCurses.md): why Binding and Vanishing survive repair and disenchantment

- [Grindstone item](../items/Grindstone.md)
- [Anvil repair and combining](../mechanics/AnvilMechanics.md)
- [Enchanting](../enchanting/Enchanting.md)
- [Villager professions](../mobs/Villager.md#professions-and-job-sites)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game repair, disenchantment, experience, mining, or placement test was run. Enchantment and item components and data packs can change the applicable rules.

- [Menu inputs, repair, enchantments, experience, and consumption](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/inventory/GrindstoneMenu.java)
- [Block interaction and support behavior](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/GrindstoneBlock.java)
- [Placement orientations](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/FaceAttachedHorizontalDirectionalBlock.java)
- [Recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/grindstone.json)
- [Registration and mining requirement](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/Blocks.java)
- [Pickaxe mining tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json)
- [Block loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/grindstone.json)
- [Curse tag](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/enchantment/curse.json)
- [Prior-work progression](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/inventory/AnvilMenu.java)

The input edge cases, Creative consumption, automation, piston restriction and Weaponsmith job site were additionally source-reviewed on **2026-10-10** at `4246f4e7bfc1f3ab7862272ebba5f1f38aa16953`. The active Java menu/interaction path, native block definition, recipe and loot were inspected; no gameplay or automation test was run. Custom item components can change damageability, stack size and enchantments. [Active block registration][current-registration] · [Native property projection][current-native] · [Comparator dispatch][current-state-dispatch] · [Native inventory-policy dispatch][current-state-inventory]

[current-slots]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/inventory/GrindstoneMenu.java#L31-L64
[current-consumption]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/inventory/GrindstoneMenu.java#L66-L104
[current-inputs]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/inventory/GrindstoneMenu.java#L122-L136
[current-merge]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/inventory/GrindstoneMenu.java#L139-L164
[current-removal]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/inventory/GrindstoneMenu.java#L180-L193
[current-enchantments]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L55-L87
[current-matching]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/item/ItemStack.java#L639-L670
[current-book]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/item/Items.java#L2110-L2117
[current-block]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/GrindstoneBlock.java#L70-L87
[current-catalog]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/rust/content/block/definitions/catalog.rs#L886
[current-policy]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/rust/content/block/definitions/policy.rs#L92-L100
[current-hopper]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L360-L387
[current-comparator]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L227-L237
[current-physics]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/rust/content/block/definitions/physics.rs#L815-L820
[current-piston]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java#L228-L260
[current-poi]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiTypes.java#L120-L133
[current-profession]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/entity/npc/VillagerProfession.java#L115-L123
[current-registration]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/Blocks.java#L3543-L3547
[current-native]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/state/NativeBlockDefinitions.java#L99-L122
[current-state-dispatch]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L639-L668
[current-state-inventory]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L887-L889
[current-dropper]: https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/block/DropperBlock.java#L60-L66
