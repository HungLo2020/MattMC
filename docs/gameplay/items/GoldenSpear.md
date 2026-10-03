# Golden Spear

A Golden Spear has only 32 durability and the same 5-point starting damage as Wooden. Gold’s material does not provide a faster ordinary attack or a shorter release-thrust threshold. [Material values][materials] · [Spear registrations][registrations]

## Obtaining

Craft one with **one Gold Ingot and two Sticks**, using the [shared Spear crafting pattern](../mechanics/Spears.md#craft-or-request-a-spear). [Recipe][golden-recipe] · [Accepted material][gold-repair]

It is also an ordinary listed item in MattMC's [inventory item browser](../mechanics/InventoryBrowser.md), whose item-request route works in Creative. [Tab entry][tab]

## Usage

The default full-strength ordinary hit and the release thrust each start at **5 damage points**, before enchantments and defenses. Ordinary melee has **1.2 attack speed**; hold use for at least **8 ticks** to release-thrust. Use [Spears](../mechanics/Spears.md#choose-the-attack-for-the-situation) for aiming, the close-range gap, and attack timing. [Registration][registrations] · [Attributes][attributes] · [Player defaults][player-attributes] · [Base speed][speed] · [Release threshold][constants]

## Behavior

Its material enchantability value is 22, the highest of these seven Spears, but that does not expand the family’s supported enchantment tags. Check the Spear enchantment guide before spending books. [Material settings][materials] · [Shared component settings][components]

Held-use contact uses closing speed rather than this item's 5-point release value. The [moving-contact rules](../mechanics/Spears.md#moving-contact-and-riding) and [Lunge/enchantment limits](../mechanics/Spears.md#lunge-and-other-enchantments) apply to this Spear. [Contact calculation][contact]

## Notes

- Registry ID: `minecraft:golden_spear`
- Maximum durability: **32**; Anvil repair accepts **Gold Ingots**. Fully worn Spears remain as broken stacks; see [wear and repair](../mechanics/Spears.md#wear-repair-and-broken-spears) before replacing one. [Repair registration][components] · [Material values][materials] · [Repair material][gold-repair] · [Broken-stack handling][broken]
- Related: [Wooden Spear](WoodenSpear.md) · [Spear family](../mechanics/Spears.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `bb9a8a060da02b64f23508b77794fb0f79307de4`. No in-game crafting, combat, enchanting, or repair test was run. Shared behavior and its verification limits are documented in the Spear family guide.

[attributes]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/SpearItem.java#L68-L81
[broken]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L485
[components]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/Items.java#L2800-L2812
[constants]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/SpearItem.java#L40-L52
[contact]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/SpearItem.java#L104-L137
[gold-repair]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/item/gold_tool_materials.json#L1-L5
[golden-recipe]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/golden_spear.json#L1-L16
[materials]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L32
[player-attributes]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/player/Player.java#L220-L227
[registrations]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/Items.java#L2046-L2052
[speed]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L15-L17
[tab]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1599-L1605
