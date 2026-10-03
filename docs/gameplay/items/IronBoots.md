# Iron Boots

Iron Boots (`minecraft:iron_boots`) provide **2 armor points** and **0 toughness** while functional and worn in the **feet slot**. Its default maximum durability is **195**; its knockback-resistance bonus is **0**. [Registration][items] · [Material values][materials] · [Slot durability][slots] · [Item properties][properties] · [Attribute construction][attributes]

## Obtaining

At a Crafting Table, use **4 Iron Ingots** to make **one item**. In this grid, `X` means one [Iron Ingot](IronIngot.md) and `.` means an empty slot. [Exact recipe][recipe] · [Crafting output][craft-consumer]

```text
X . X
X . X
. . .
```

An ordinary **level-1 Armorer** can offer one piece for a base **4 Emeralds**. Only selected offers are added from the level’s pool, and the displayed price can change. The optional Trade Rebalance experiment has a separate table. [Armorer offers][armorer-trades] · [Trade selection][trade-selection] · [Random offers][random-offers] See [Trading](../trading/Trading.md).

This item is also an ordinary category entry in MattMC’s [inventory item browser](../mechanics/InventoryBrowser.md), which supports requesting listed items in Creative subject to its cursor, capacity and feature checks. [Armor entries][category] · [Browser list][browser-list] · [Client request][browser-client] · [Server insertion][browser-server]

## Usage

Equip it in the **feet slot**. Carrying it in ordinary inventory supplies none of the listed armor attributes. Mixed armor sets add their active piece values; the resulting damage reduction depends on the incoming hit, toughness and damage type. Use [Armor and damage reduction](../mechanics/Armor.md) for that calculation. [Slot-bound attributes][attributes] · [Equipped modifiers][equipment]

For the same slot, [Diamond Boots](DiamondBoots.md) provide **3 armor points** and **429 maximum durability**. Compare the slot you are replacing rather than treating this piece as the values of a complete set. [Material comparison][materials] · [Durability multipliers][slots]

All ordinary pieces in this material family can receive decorative armor trims; trims do not change their armor, toughness or durability. Follow [Smithing](../smithing/Smithing.md#example-apply-an-armor-trim). [Trim category tag][trim-tag] · [Matching slot tag][trim-slot]

## Behavior

At **195 damage**, the ordinary wear path retains a **broken Iron Boots stack** instead of deleting it. The equipment-break callback removes its attribute effects, and ordinary equipment updates do not apply a broken piece’s armor, toughness or knockback modifiers. Repair it before relying on those defenses. [Retained stack][broken] · [Break callback][break-effects] · [Equipment gate][equipment]

This does not establish that every passive effect stops. Applied [Protection enchantments](../enchanting/ProtectionEnchantments.md#retained-broken-armor-in-this-snapshot) can still contribute matching damage protection from retained broken armor in the checked implementation. Their iterator and damage consumer do not test the broken state. The intended passive-effect policy remains under [#800 review](https://github.com/HungLo2020/MattMC/issues/800#issuecomment-5961208983). [Enchantment iterator][protection] · [Damage consumer][protection-consumer]

## Repair

Use **[Iron Ingot](IronIngot.md)** in an Anvil for material repair. Each ingredient restores up to **48 durability** (one quarter of the default maximum, rounded down), limited by the damage remaining, with the Anvil’s normal level cost. [Accepted repair material][repair-tag] · [Material assignment][properties] · [Anvil repair][anvil]

Combining matching pieces is another repair route. [Durability and repair](../mechanics/Durability.md#choose-a-repair-method) compares Anvil, Grindstone and crafting-grid repair, including what happens to enchantments, names, dyes and trims. Check that guide before combining customized equipment.

## Notes

- Registry ID: `minecraft:iron_boots`
- Listed values describe the ordinary unmodified item; custom components and data packs can change them
- The acquisition routes above are checked examples, not an exhaustive chest-loot or mob-equipment inventory

## Related pages

- [Iron Ingot](IronIngot.md)
- [Armor and damage reduction](../mechanics/Armor.md)
- [Durability, broken items and repair](../mechanics/Durability.md)
- [Anvil mechanics](../mechanics/AnvilMechanics.md)
- [Protection enchantments](../enchanting/ProtectionEnchantments.md)
- [Smithing](../smithing/Smithing.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-03** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`. Checked item registration, material and slot properties, the named acquisition routes, active equipment and repair consumers, and the broken-stack distinction. No in-game crafting, trading, equipment, damage, repair, smithing or inventory-browser test was run.

[items]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1357-L1406
[materials]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/equipment/ArmorMaterials.java#L8-L33
[slots]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/equipment/ArmorType.java#L8-L28
[properties]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Item.java#L459-L466
[attributes]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/equipment/ArmorMaterial.java#L25-L42
[recipe]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/iron_boots.json
[craft-consumer]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/CraftingMenu.java#L53-L71
[armorer-trades]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L490-L529
[trade-selection]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/Villager.java#L818-L843
[random-offers]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/npc/AbstractVillager.java#L222-L235
[category]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1609-L1637
[browser-list]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[browser-client]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-server]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1938
[equipment]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2650-L2683
[trim-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/trimmable_armor.json
[trim-slot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/foot_armor.json
[broken]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ItemStack.java#L449-L485
[break-effects]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3540-L3553
[protection]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L135-L177
[protection-consumer]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1809-L1825
[repair-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/item/repairs_iron_armor.json
[anvil]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L125-L153
