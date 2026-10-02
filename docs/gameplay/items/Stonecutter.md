# Stonecutter

**Stonecutter** (`minecraft:stonecutter`) is the item for the building-material workstation and Mason job site. Its [block guide](../blocks/Stonecutter.md) owns the recipe, menu and placed behavior. [Registration][items]

## Obtaining

Craft **1 Stonecutter from 3 ordinary Stone and 1 Iron Ingot**, using the [canonical arrangement](../blocks/Stonecutter.md#crafting-and-collecting). An **unbroken pickaxe**, including Wood, recovers one placed Stonecutter. Silk Touch is unnecessary, Fortune does not increase the drop, and explosion survival is conditional. [Recipe][recipe] · [Loot][loot]

## Using it

Place it, open its menu, insert a matching ingredient, select a recipe and take the result. Each operation consumes one input item; the chosen recipe defines the output count. It uses no fuel and has no cooking timer. See [menu behavior](../blocks/Stonecutter.md#using-the-menu) and the [material recipe guides](../blocks/Stonecutter.md#checked-recipe-examples).

The menu's input is temporary, with remaining items returned or dropped on closure. It is not stored in the Stonecutter item or block, and Hoppers cannot operate the menu. See [storage and automation limits](../blocks/Stonecutter.md#temporary-input-and-automation).

## Sources and verification

Source-reviewed on **2026-10-02** at `ae92d4575f1af7752d0f461bbf7bf0843c3cddf7`; the block guide links the active recipe, mining and menu evidence. No gameplay test was run. Server data can change recipe choices.

[Stonecutter block](../blocks/Stonecutter.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/Items.java
[recipe]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/recipe/crafting/stonecutter.json
[loot]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/loot_table/blocks/stonecutter.json
