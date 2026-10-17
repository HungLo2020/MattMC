# Banner Pattern (Globe)

Globe Banner Pattern (`minecraft:globe_banner_pattern`) unlocks the **Globe** design in a Loom. It stacks to **one**. [Item registration][templates] · [Pattern tag][pattern-tag]

## Obtaining

Buy **one Globe Banner Pattern** from a **level-5 (Master) Cartographer**. The registered base offer costs **eight [Emeralds](Emerald.md)**; the price shown by a particular villager can change through trade-price adjustments. [Offer][trade] · [Offer construction][trade-offer] · [Adjusted cost][trade-price]

The current trade-update path selects offers for the villager's profession and level. Its experimental-trade fallback also uses the standard Cartographer list, which contains this offer. [Trade selection][trade-dispatch]

## Usage

Put a Banner, a Dye, and this template into a [Loom](../blocks/Loom.md#adding-a-pattern). Its pattern tag supplies only **Globe**, so the Loom selects that design automatically when the Banner and Dye are present. The Dye sets the new layer's color. Check the preview, then take the decorated Banner to apply it. [Template tag][pattern-tag] · [Selection and result][loom-menu]

## Behavior

Taking one result consumes **one Banner and one Dye**, while **the template remains in its slot for reuse**. The ordinary Loom menu removes those two inputs even in Creative mode. Closing the menu returns remaining inputs through the normal inventory/drop handling. [Result-slot consumption][loom-menu] · [Returning inputs][clear-menu]

A template still obeys the [Loom's pattern limits](../blocks/Loom.md#pattern-limits). For copying a finished design or removing its latest layer, follow [Banners](../blocks/Banners.md#patterns-and-duplication).

## Notes

- This item is registered as `minecraft:globe_banner_pattern`.
- The default English item name is **Banner Pattern**. This wiki uses **Globe** to distinguish the item; its design is **Globe** (`minecraft:globe`). The specific wiki label is not a separate item ID. [English names][names] · [Registered item name][item-name] · [Pattern resource][pattern]
- Compare sources and designs in [All Loom templates](../blocks/Loom.md#available-designs-and-templates).

## Sources and verification

Reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on 2026-10-04. Source and bundled-data review only; no in-game crafting, trading, loot, or Loom test.

[templates]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2387-L2424
[pattern-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/banner_pattern/pattern_item/globe.json
[pattern]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/banner_pattern/globe.json
[loom-menu]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/LoomMenu.java
[clear-menu]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java#L598-L611
[names]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/assets/minecraft/lang/en_us.json
[item-name]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L122-L125
[trade]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L452-L456
[trade-offer]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1422-L1479
[trade-price]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/trading/MerchantOffer.java
[trade-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/Villager.java#L819-L842
