# Diamond Spear

A Diamond Spear combines 8-point starting damage with 1,561 durability. Keep it if you plan to upgrade: it is the required base for the Netherite Spear smithing recipe. [Material values][materials] · [Spear registrations][registrations] · [Upgrade recipe][smithing]

## Obtaining

Craft one with **one Diamond and two Sticks**, using the [shared Spear crafting pattern](../mechanics/Spears.md#craft-or-request-a-spear). [Recipe][diamond-recipe] · [Accepted material][diamond-repair]

It is also an ordinary listed item in MattMC's [inventory item browser](../mechanics/InventoryBrowser.md), whose item-request route works in Creative. [Tab entry][tab]

## Usage

The default full-strength ordinary hit and the release thrust each start at **8 damage points**, before enchantments and defenses. Ordinary melee has **1.2 attack speed**; hold use for at least **8 ticks** to release-thrust. Use [Spears](../mechanics/Spears.md#choose-the-attack-for-the-situation) for aiming, the close-range gap, and attack timing. [Registration][registrations] · [Attributes][attributes] · [Player defaults][player-attributes] · [Base speed][speed] · [Release threshold][constants]

## Behavior

A Netherite upgrade raises starting damage to 9 and maximum durability to 2,031 and adds fire-damage resistance to the item. It requires a Netherite Ingot and Netherite Upgrade Smithing Template. [Material settings][materials] · [Shared component settings][components] · [Upgrade recipe][smithing]

Held-use contact uses closing speed rather than this item's 8-point release value. The [moving-contact rules](../mechanics/Spears.md#moving-contact-and-riding) and [Lunge/enchantment limits](../mechanics/Spears.md#lunge-and-other-enchantments) apply to this Spear. [Contact calculation][contact]

## Notes

- Registry ID: `minecraft:diamond_spear`
- Maximum durability: **1,561**; Anvil repair accepts **Diamonds**. Fully worn Spears remain as broken stacks; see [wear and repair](../mechanics/Spears.md#wear-repair-and-broken-spears) before replacing one. [Repair registration][components] · [Material values][materials] · [Repair material][diamond-repair] · [Broken-stack handling][broken]
- Related: [Netherite Spear](NetheriteSpear.md) · [Spear family](../mechanics/Spears.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `bb9a8a060da02b64f23508b77794fb0f79307de4`. No in-game crafting, combat, enchanting, or repair test was run. Shared behavior and its verification limits are documented in the Spear family guide.

[attributes]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/SpearItem.java#L68-L81
[broken]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L485
[components]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/Items.java#L2800-L2812
[constants]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/SpearItem.java#L40-L52
[contact]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/SpearItem.java#L104-L137
[diamond-recipe]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/diamond_spear.json#L1-L16
[diamond-repair]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/item/diamond_tool_materials.json#L1-L5
[materials]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L32
[player-attributes]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/player/Player.java#L220-L227
[registrations]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/Items.java#L2046-L2052
[smithing]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/netherite_spear_smithing.json#L1-L9
[speed]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L15-L17
[tab]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1599-L1605
