# Durability, broken items, and repair

**MattMC retains ordinary fully damaged equipment as broken item stacks.** Reaching the durability limit does not itself delete the stack in the current damage path. Keep useful broken gear for repair, but do not assume it still works normally or that every special item handles the broken state consistently.

## What the durability values mean

A damageable stack has maximum-damage and current-damage components and is not marked Unbreakable. Current damage is clamped between zero and the maximum. A normal stack is broken when its damage reaches that maximum; remaining durability is the difference between them.

The ordinary damage path updates that value and calls an equipment-break callback when the stack first becomes broken. It does not reduce the stack count or replace the item by itself. Further ordinary durability-change processing on an already-broken stack returns without adding more wear.

This describes the shared stack implementation. Individual interactions choose their wear amount and can have additional behavior. Enchantment-based durability modifiers and Creative's infinite-material ability can alter ordinary wear where those checks apply.

## What stops working

The normal broken-stack guards affect several paths:

- The stack's use, block-use, and living-entity item-interaction entry points reject the action
- Its mining-speed accessor returns the basic **1.0** value instead of the ordinary tool speed
- Its correct-tool-for-drops check fails. A block that requires the right tool may still be broken without yielding its expected item
- Normal weapon-hit hooks and mining-use hooks skip their item-specific processing
- Ordinary equipment updates do not apply a broken item's attribute modifiers, and the break callback stops its equipped effects

This is not a promise that every custom system checks the same guard. A mob or item handler that checks only an item ID can behave differently. See the [Wolf Armor warning](../items/WolfArmor.md#fully-damaged-armor-in-this-snapshot), where the special absorption check does not test the broken state.

Soft blocks that do not require a correct tool are a separate case; a broken pickaxe is not a reliable way to collect tool-gated ores just because it can still break something.

## Choose a repair method

| Method | Main inputs | Level payment | Enchantments and other data |
| --- | --- | --- | --- |
| [Anvil](AnvilMechanics.md) | Damaged item plus accepted repair material, matching donor, or applicable enchanted book | Calculated cost, capped at 40 levels | Starts from the left item; retains and evaluates enchantments/components according to Anvil rules |
| [Grindstone](../blocks/Grindstone.md) | Two matching damageable items for repair | No level payment | Removes non-curse enchantments; starts from the upper item's components and recalculates prior work |
| Crafting-grid repair | Two same-type items, one in each of two occupied slots | No level payment | Creates a fresh result; carries curses but does not copy ordinary custom names, trims, or other added components |

An Anvil material repair restores up to one quarter of maximum durability per accepted material, rounded down. An Anvil's matching-item repair adds a 12% bonus, while the Grindstone and crafting-grid routes add a 5% bonus. The specific formulas and eligibility matter; do not sacrifice named or enchanted gear without checking the output.

These methods can use ordinary retained broken gear because damageability and repair eligibility are separate from being able to use the item normally. An arbitrary ingredient is not a repair material just because it resembles the item.

## Crafting-grid repair details

The bundled special repair recipe needs exactly **two occupied input slots**, each holding one item of the same item type. Both must have maximum-damage and damage components. It fits in the inventory's 2 × 2 crafting grid and does not require an Anvil.

The result gets the two inputs' remaining durability plus **5% of the larger maximum durability, rounded down**, capped at that resulting maximum. Both inputs are consumed to produce one result.

Importantly, the implementation constructs a **new stack of the item type** rather than copying either input's full component data. It restores curses from both inputs, choosing the higher level for each curse. Other enchantments, custom names, trims, dyes, and prior-work values are not copied from the inputs; the result starts with the item type's defaults apart from the explicitly reconstructed durability and curses.

This differs from the Grindstone's upper-item copy and the Anvil's left-item copy. For valuable customized gear, inspect the output and use the appropriate method rather than assuming all three repairs preserve the same information.

## Do not infer a remainder from a helper name

The [Carrot on a Stick](../items/CarrotOnAStick.md) boost calls a helper named for conversion on break. That helper converts to a Fishing Rod only if the original stack becomes empty. The current durability path instead retains a nonempty broken stack, so a normal wear-out does not establish that Fishing Rod return.

Similarly, a break sound or animation does not prove that the inventory item was removed. Check the actual stack and repair route. These observations are from the inspected code, not a completed in-game wear-out or repair test.

## Related pages

- [Anvil operations](AnvilMechanics.md)
- [Grindstone](../blocks/Grindstone.md)
- [Mining tools](Mining.md)
- [Armor](Armor.md)
- [Wolf Armor](../items/WolfArmor.md)
- [Mechanics](Mechanics.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game break, repair, component-preservation, mining, or equipment test was run. Custom item components and specialized handlers can change behavior beyond the shared paths reviewed here.

- [Damage, broken-state guards, wear processing, and conversion](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/ItemStack.java)
- [Equipment change and break callbacks](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java)
- [Anvil repair and retained data](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/inventory/AnvilMenu.java)
- [Grindstone repair and data handling](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/inventory/GrindstoneMenu.java)
- [Crafting-grid repair algorithm](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/crafting/RepairItemRecipe.java)
- [Active repair recipe registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/repair_item.json)
- [Carrot on a Stick boost call](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/FoodOnAStickItem.java)
- [Wolf special absorption path](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java)
