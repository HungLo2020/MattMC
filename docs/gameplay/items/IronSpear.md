# Iron Spear

An Iron Spear starts ordinary full-strength hits and release thrusts at 7 damage points and has 250 durability. It improves both figures over Copper without changing the family’s attack timing. [Material values][materials] · [Spear registrations][registrations]

## Obtaining

Craft one with **one Iron Ingot and two Sticks**, using the [shared Spear crafting pattern](../mechanics/Spears.md#craft-or-request-a-spear). [Recipe][iron-recipe] · [Accepted material][iron-repair]

It is also an ordinary listed item in MattMC's [inventory item browser](../mechanics/InventoryBrowser.md), whose item-request route works in Creative. [Tab entry][tab]

## Usage

The default full-strength ordinary hit and the release thrust each start at **7 damage points**, before enchantments and defenses. Ordinary melee has **1.2 attack speed**; hold use for at least **8 ticks** to release-thrust. Use [Spears](../mechanics/Spears.md#choose-the-attack-for-the-situation) for aiming, the close-range gap, and attack timing. [Registration][registrations] · [Attributes][attributes] · [Player defaults][player-attributes] · [Base speed][speed] · [Release threshold][constants]

## Behavior

Diamond raises starting damage to 8 and durability to 1,561, a much larger durability increase than the Copper-to-Iron step. [Material settings][materials] · [Shared component settings][components]

Held-use contact uses closing speed rather than this item's 7-point release value. The [moving-contact rules](../mechanics/Spears.md#moving-contact-and-riding) and [Lunge/enchantment limits](../mechanics/Spears.md#lunge-and-other-enchantments) apply to this Spear. [Contact calculation][contact]

## Notes

- Registry ID: `minecraft:iron_spear`
- Maximum durability: **250**; Anvil repair accepts **Iron Ingots**. Fully worn Spears remain as broken stacks; see [wear and repair](../mechanics/Spears.md#wear-repair-and-broken-spears) before replacing one. [Repair registration][components] · [Material values][materials] · [Repair material][iron-repair] · [Broken-stack handling][broken]
- Related: [Diamond Spear](DiamondSpear.md) · [Spear family](../mechanics/Spears.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `bb9a8a060da02b64f23508b77794fb0f79307de4`. No in-game crafting, combat, enchanting, or repair test was run. Shared behavior and its verification limits are documented in the Spear family guide.

[attributes]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/SpearItem.java#L68-L81
[broken]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L485
[components]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/Items.java#L2800-L2812
[constants]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/SpearItem.java#L40-L52
[contact]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/SpearItem.java#L104-L137
[iron-recipe]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/iron_spear.json#L1-L16
[iron-repair]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/item/iron_tool_materials.json#L1-L5
[materials]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L32
[player-attributes]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/player/Player.java#L220-L227
[registrations]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/Items.java#L2046-L2052
[speed]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L15-L17
[tab]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1599-L1605
