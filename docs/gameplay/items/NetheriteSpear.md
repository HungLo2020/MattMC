# Netherite Spear

A Netherite Spear has the highest starting damage and durability of the seven material Spears: **9 damage points** and **2,031 durability**. Its item is resistant to fire damage; carrying it does not grant the player fire resistance. [Registration][registrations] · [Material values][materials] · [Fire-resistant property][fire-resistant] · [Item damage check][item-damage-resistance]

## Obtaining

Use a **Diamond Spear, Netherite Ingot, and Netherite Upgrade Smithing Template** at a Smithing Table to make one. The transformation carries the base stack's changed components into the result; inspect existing damage and enchantments rather than assuming the upgrade wipes them. [Recipe][smithing] · [Netherite material][netherite-repair] · [Smithing result][smithing-copy] · [Transformation][transmute] · [Component copy][component-copy]

It is also an ordinary listed item in MattMC's [inventory item browser](../mechanics/InventoryBrowser.md), whose item-request route works in Creative. [Tab entry][tab]

## Usage

The default full-strength ordinary hit and the release thrust each start at **9 damage points**, before enchantments and defenses. It retains **1.2 ordinary attack speed** and the family's **8-tick** minimum hold before release-thrust. Use [Spears](../mechanics/Spears.md#choose-the-attack-for-the-situation) for aiming and timing. [Shared registration][components] · [Attack attributes][attributes] · [Player defaults][player-attributes] · [Base speed][speed] · [Threshold][constants]

## Behavior

Netherite does not increase thrust reach or add its material damage bonus to held-contact damage. That attack depends on [closing speed](../mechanics/Spears.md#moving-contact-and-riding); [Lunge](../mechanics/Spears.md#lunge-and-other-enchantments) keeps the same movement, weather, and food restrictions as on cheaper Spears. [Reach rules][reach] · [Contact damage][contact] · [Lunge checks][lunge]

## Notes

- Registry ID: `minecraft:netherite_spear`
- Anvil material repair uses **Netherite Ingots**, not Diamonds. Keep an enchanted worn-out Spear for [repair](../mechanics/Spears.md#wear-repair-and-broken-spears): the normal durability path retains the broken item. [Repair registration][components] · [Repair material][netherite-repair] · [Broken stack][broken]
- Related: [Diamond Spear](DiamondSpear.md) · [Spear family](../mechanics/Spears.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `bb9a8a060da02b64f23508b77794fb0f79307de4`. No in-game smithing, combat, fire-resistance, or repair test was run. Shared behavior and its verification limits are documented in the Spear family guide.

[attributes]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/SpearItem.java#L68-L81
[broken]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L485
[component-copy]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/ItemStack.java#L627-L636
[components]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/Items.java#L2800-L2812
[constants]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/SpearItem.java#L40-L52
[contact]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/SpearItem.java#L104-L137
[fire-resistant]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/Item.java#L402-L404
[item-damage-resistance]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/ItemStack.java#L1101-L1104
[lunge]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/SpearItem.java#L260-L289
[materials]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L32
[netherite-repair]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/item/netherite_tool_materials.json#L1-L5
[player-attributes]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/player/Player.java#L220-L227
[reach]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/SpearItem.java#L316-L335
[registrations]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/Items.java#L2046-L2052
[smithing]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/netherite_spear_smithing.json#L1-L9
[smithing-copy]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/crafting/SmithingTransformRecipe.java#L32-L34
[speed]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L15-L17
[tab]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1599-L1605
[transmute]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/crafting/TransmuteResult.java#L47-L50
