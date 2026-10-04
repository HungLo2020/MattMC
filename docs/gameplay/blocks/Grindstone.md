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

## Repairing two matching items

Place two damageable items of the **same item type** in the inputs. Their remaining durability is added, plus a bonus of **5% of the larger maximum durability, rounded down**. The output cannot exceed its resulting maximum durability. Both inputs are consumed when you take the result.

This repair also removes non-curse enchantments. Curses can carry across from either input. A curse already present on the upper item is retained rather than upgraded from the lower donor.

The result starts from the upper item's components, including its custom name. Other data from the lower item is not generally copied across. The durability and enchantments are then adjusted by the operation. For unusual component-modified items, the code uses the larger of the two maximum-durability values; inspect the result rather than assuming identical-looking items have identical data.

The ordinary repair bonus is smaller than the Anvil's 12% matching-item bonus, but the Grindstone does not charge levels. Choose based on the enchantments and item data you want to keep, not durability alone.

## Experience returned

Taking a valid result awards experience orbs at the Grindstone. It is a refund calculation from the **non-curse enchantments on both inputs**, not a return of exactly the experience you originally spent.

For each non-curse enchantment, the code adds its minimum enchanting cost at its current level. Let that total be `S`. If `S` is positive, let `J = ceil(S / 2)`; the award is a uniformly selected whole number from **J through 2J − 1 experience points**. With no non-curse value, the award is zero. Curse removal is not occurring and curses contribute no experience.

This is experience **points**, unlike the whole-level payment shown by an Anvil. No level threshold or fuel is required by the Grindstone menu.

## Prior work and handling inputs

The result's prior-work penalty is recalculated from its remaining curse count. With no curses it becomes **0**; with one distinct curse it becomes **1**; with two it becomes **3**. Thus a Grindstone can clear ordinary prior-work penalties, but this does not preserve the useful enchantments it removes.

There is no rename control in this menu. A single-item operation preserves that input's name; a two-item operation starts with the upper item's name and other components. Use the Anvil when changing the name is your goal.

Taking the result clears both input slots. Closing the menu before committing returns or drops the input contents through the standard container cleanup. Do not use the slots as storage. The completed-use path has no Anvil-style wear roll.

## Related pages

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
