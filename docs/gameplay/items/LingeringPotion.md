# Lingering Potion

## Obtaining

Brew a Splash Potion with **Dragon's Breath** to make a Lingering Potion of the same type; see [Brewing](../brewing/Brewing.md). Enabled potion types are also listed in the [Inventory Browser](../mechanics/InventoryBrowser.md), whose ordinary item insertion works in Survival and Creative. Browser access is separate from brewing. [Container recipe][lingering-brew] · [Potion listings][lingering-list] · [Enabled types][lingering-list-types]

## Usage

Throw an **effect-bearing** Lingering Potion to create a cloud. The registered item creates the lingering-potion projectile through its active throw callback. [Item binding][lingering-item] · [Projectile factory][lingering-factory] · [Throwing][lingering-throw]

## Behavior

On server impact, a normal Water potion with **no custom effects** takes the Water-specific branch; other contents with no effects also do not create an effect cloud. Contents with effects reach the lingering-cloud callback, which copies the item's potion components. A Water base **with custom effects** fails that special Water match and can create a cloud, so the base potion's name alone does not decide the result. [Impact branches][lingering-impact] · [Exact Water match][lingering-water-match] · [Effect-list check][lingering-has-effects] · [Cloud callback][lingering-cloud]

Cloud effects have their own application rules. See [Instant effects](../effects/InstantEffects.md#drinking-splashing-clouds-and-arrows) for Healing and Harming delivery, and [Brewing](../brewing/Brewing.md#splash-and-lingering-forms) for the shared potion-form rules.

## Notes

* This item is registered as `minecraft:lingering_potion`.

Source-reviewed on 2026-10-02 at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`, following item registration, active throwing, impact dispatch, the Water/custom-effect checks, and cloud creation. No in-game brewing, throwing, or cloud test was run.

[lingering-brew]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L133-L138
[lingering-item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L2251-L2255
[lingering-factory]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/LingeringPotionItem.java#L20-L42
[lingering-throw]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ThrowablePotionItem.java#L22-L40
[lingering-impact]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/AbstractThrownPotion.java#L69-L84
[lingering-water-match]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionContents.java#L79-L80
[lingering-has-effects]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/alchemy/PotionContents.java#L146-L148
[lingering-cloud]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/projectile/ThrownLingeringPotion.java#L31-L44
[lingering-list]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1756-L1770
[lingering-list-types]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2207-L2213
