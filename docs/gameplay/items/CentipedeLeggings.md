# Centipede Leggings

**Centipede Leggings** are leg-slot armor crafted from [Centipede Legs](CentipedeLeg.md). In this MattMC snapshot they use **chainmail armor settings**: **4 armor points**, **225 durability**, and no extra toughness or knockback resistance. There is no registered speed or poison-resistance bonus. [Registration][centipede-items] · [Material][armor] · [Durability calculation][armor-types] · [Attribute construction][armor-attributes]

## Obtaining

At a crafting table, arrange **seven Centipede Legs** in the usual leggings shape to make **one Centipede Leggings**:

```text
Leg  Leg  Leg
Leg   ·   Leg
Leg   ·   Leg
```

The dot is an empty slot. [Exact recipe][leggings-recipe]

Legs come from [Cave Centipede head loot](../mobs/CaveCentipede.md#drops). The finished leggings are also an ordinary category entry that the [inventory item browser](../mechanics/InventoryBrowser.md) can insert in **Survival and Creative**, independently of crafting. [Category entry][categories]

## Usage

Equip them in the **legs slot** for their armor attributes. They do not stack. For shared armor and equipment rules, see [Armor](../mechanics/Armor.md). [Equipment and repair components][armor-properties] · [Durability properties][item-durability]

Repair them at an [Anvil](../mechanics/AnvilMechanics.md) with **Iron Ingots**, not Centipede Legs. Each ingot restores up to **56 durability**, one quarter of the default maximum rounded down, with the Anvil's level cost. Combining two matching leggings is another repair route; see [repair methods](../mechanics/Durability.md#choose-a-repair-method) before sacrificing customized equipment. [Repair material][chain-repair] · [Material binding][armor] · [Equipment properties][armor-properties] · [Anvil calculation][anvil]

## Behavior

At **225 damage**, the ordinary wear path retains the leggings as a fully broken stack. Normal equipment updates stop applying the broken stack's armor attributes; keep it for repair rather than relying on it for protection. [Retained stack][stack] · [Equipment guard][broken-armor] · [Durability and repair](../mechanics/Durability.md)

The item has an enchantability component, but is **absent from the bundled leg-armor tag** and therefore from the normal armor/durability enchantment memberships reached through that tag. Do not assume ordinary chainmail's Protection, Unbreaking or Mending eligibility carries over just because the material stats match. Custom data can change that membership. [Component setup][armor-properties] · [Leg armor membership][leg-tag] · [Armor enchantment grouping][armor-tag] · [Durability grouping][durability-tag] · [Support checks][enchant-support]

## Notes

Registered as `minecraft:centipede_leggings`. Its recipe is distinct from its Iron Ingot repair ingredient. [Registration][centipede-items] · [Recipe][leggings-recipe] · [Repair tag][chain-repair]

Related: [Centipede Leg](CentipedeLeg.md) · [Cave Centipede](../mobs/CaveCentipede.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `c1adfb58c73bd6918cde87943be31afef6a2ccf4`. Checked active registrations, relevant callers and bundled data. No in-game spawn, combat, feeding, drop, crafting, equipment or repair test was run. Server data packs and custom components may change these defaults.

[centipede-items]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/Items.java#L1754-L1755
[armor]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/equipment/ArmorMaterials.java#L15-L17
[armor-types]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/equipment/ArmorType.java#L8-L31
[armor-attributes]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/equipment/ArmorMaterial.java#L25-L39
[leggings-recipe]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/recipe/centipede_leggings.json
[categories]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[armor-properties]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/Item.java#L458-L466
[item-durability]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/Item.java#L382-L391
[chain-repair]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/tags/item/repairs_chain_armor.json
[anvil]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L120-L154
[stack]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L485
[broken-armor]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2663-L2681
[leg-tag]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/tags/item/leg_armor.json
[armor-tag]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/tags/item/enchantable/armor.json
[durability-tag]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/tags/item/enchantable/durability.json
[enchant-support]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L117-L130
