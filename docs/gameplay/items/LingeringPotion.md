# Lingering Potion

## Obtaining

Brew a Splash Potion with **Dragon's Breath** to make a Lingering Potion of the same type; see [Brewing](../brewing/Brewing.md). Enabled potion types are also listed in the [Inventory Browser](../mechanics/InventoryBrowser.md), whose ordinary item insertion works in Creative. Browser access is separate from brewing. [Container recipe][lingering-brew] · [Potion listings][lingering-list] · [Enabled types][lingering-list-types]

## Usage

Throw an **effect-bearing** Lingering Potion to create a cloud. The registered item creates the lingering-potion projectile through its active throw callback. [Item binding][lingering-item] · [Projectile factory][lingering-factory] · [Throwing][lingering-throw]

## Behavior

On server impact, a normal Water potion with **no custom effects** takes the Water-specific branch; other contents with no effects also do not create an effect cloud. Contents with effects reach the lingering-cloud callback, which copies the item's potion components. A Water base **with custom effects** fails that special Water match and can create a cloud, so the base potion's name alone does not decide the result. [Impact branches][lingering-impact] · [Exact Water match][lingering-water-match] · [Effect-list check][lingering-has-effects] · [Cloud callback][lingering-cloud]

### Cloud placement and depletion

**Aim where the intended recipients will stand.** The cloud starts at the projectile's position on impact, with a **3-block horizontal radius** and a height of only **half a block**. A recipient's bounding box must overlap that shallow height, and their horizontal center must be within the cloud's current radius. Being near the particles on another elevation, or only touching the cloud's outer edge with a wide body, is not enough. [Cloud placement][cloud-placement] · [Cloud dimensions][cloud-dimensions] · [Bounding-box overlap][cloud-overlap] · [Horizontal reach][cloud-recipients]

- **Allow time for contact:** the thrown potion's cloud waits **10 ticks**, then checks recipients every **5 ticks**. An accepted recipient has a **20-tick cooldown in that cloud**. Staying inside can allow another application after the cooldown; leaving and re-entering is not required and does not bypass it. [Initial wait][cloud-placement] · [Reapplication delay][cloud-defaults] · [Recipient checks][cloud-recipients]
- **Choose who shares the area:** clouds have no allies-only filter. The thrower and other nearby living entities can qualify if they are susceptible to potions and at least one of the cloud's effects accepts them. Keep unwanted recipients away from helpful clouds, and move out of harmful ones. [Recipient checks][cloud-recipients]
- **Expect a crowd to use up the cloud:** every accepted recipient reduces its **shared radius by half a block**. Later recipients in the same check face the smaller radius. The reduction still happens if an existing status effect is kept instead of replaced, so standing in a cloud can consume space without improving your effect. [Per-use shrink][cloud-depletion] · [Effect admission and update][cloud-effect-update]

The cloud also shrinks with time after its initial wait and disappears when its radius would fall below **half a block**. Its configured **600-tick duration** is an upper limit after the wait, not a promise of 30 seconds of usable area: shrinking and recipient use can remove it sooner. Time conversions assume 20 game ticks per second. Do not plan a group around a fixed number of doses from one bottle. [Duration and timed shrink][cloud-lifetime] · [Cloud setup][cloud-placement] · [Per-use shrink][cloud-depletion]

Cloud effects have their own application rules. See [Instant effects](../effects/InstantEffects.md#drinking-splashing-clouds-and-arrows) for Healing and Harming strength, [Movement effects](../effects/MovementEffects.md#delivery-changes-duration) for duration scaling and [refreshing effects](../effects/MovementEffects.md#refreshing-clearing-and-limits), and [Brewing](../brewing/Brewing.md#splash-and-lingering-forms) for the shared potion-form rules.

## Notes

* This item is registered as `minecraft:lingering_potion`.

Source-reviewed on 2026-10-02 at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`, following item registration, active throwing, impact dispatch, the Water/custom-effect checks, and cloud creation. No in-game brewing, throwing, or cloud test was run.

Cloud placement and depletion source-reviewed on **2026-10-10** at `f86206767dadde696adfed4e04c5ee97cd0d0885`, including the registered item, impact and Water/custom-effect gates, recipient geometry, reapplication and shared radius loss. The earlier source review above is retained. No in-game placement, crowd, lifetime or dose-count test was run.

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

[cloud-placement]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/entity/projectile/ThrownLingeringPotion.java#L31-L44
[cloud-dimensions]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/entity/AreaEffectCloud.java#L359-L362
[cloud-overlap]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/level/entity/EntitySection.java#L40-L54
[cloud-recipients]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/entity/AreaEffectCloud.java#L213-L237
[cloud-defaults]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/entity/AreaEffectCloud.java#L44-L58
[cloud-depletion]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/entity/AreaEffectCloud.java#L226-L247
[cloud-effect-update]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/entity/LivingEntity.java#L982-L1000
[cloud-lifetime]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/entity/AreaEffectCloud.java#L191-L213
