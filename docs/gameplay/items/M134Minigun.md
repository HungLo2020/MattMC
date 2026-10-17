# M134 Minigun

**The M134 Minigun is obtainable, but cannot currently fire or reload in MattMC.** Its integrated definition has zero magazine capacity. [Definition][minigun] · [Firing gate][fire-gate] · [Reload gate][reload-start]

## Obtaining

The [TaCZ Gun Smith Table](../blocks/TaCZWorkbenches.md#gun-smith-table) has a recipe for one M134 Minigun: **40 Diamonds, 80 Gold Ingots, 10 Netherite Ingots, 320 Iron Ingots, and 10 Blaze Rods**. Its presence in the crafting menu does not make it operational. It also appears in the Creative Menu and is registered as `minecraft:minigun`. [Recipe][minigun-recipe] · [Output loader][recipe-output] · [Creative entries][creative] · [Registration][gun-registration]

## Usage

The definition associates it with [.308 Winchester Bullet](308WinchesterBullet.md), but both firing and reloading reject its **zero base magazine capacity**. Creative mode and its listed AUTO/BURST modes do not bypass those checks. It has no extended magazine sizes or supported attachments, so ordinary refitting cannot make it usable. The tables below record its declared configuration, not working firing performance. See [TaCZ firearms](../mechanics/TaCZFirearms.md#current-limitations). [Definition][minigun] · [Firing gate][fire-gate] · [Reload gate][reload-start] · [Magazine capacity][magazine] · [Extended-size fallback][extended-size] · [Attachment compatibility][compatibility]

## Properties

| Property | Value |
| --- | --- |
| Ammo | [.308 Winchester Bullet](308WinchesterBullet.md) |
| Magazine size | 0 |
| Extended magazine sizes | None |
| Fire modes | AUTO, BURST |
| RPM | 1200 |
| Damage | 8 |
| Pellets per shot | 1 |
| Bullet speed | 340 m/s |
| Lifetime | 0.8 seconds |
| Pierce | 2 |
| Headshot multiplier | 1.5 |
| Knockback | 0.1 |

## Accuracy

| Property | Value |
| --- | --- |
| Standing | 2.25 |
| Moving | 3 |
| Sneaking | 2.25 |
| Prone | 2.25 |
| Aiming down sights | 0.8 |

## Damage Falloff

* 50 blocks: 8
* 95 blocks: 7
* infinite blocks: 6

## Attachments

Supported attachment categories: None.

* No attachments are listed for this firearm.

## Notes

* This item is part of the integrated TaCZ firearms system.

## Sources and verification

Source-reviewed on **2026-10-03** at `cfa7057b6fe2b8dfa84e93f21932be2602eff749`. The zero-capacity conclusion follows the registered item through current firing, reload, and refit handlers. This is source review, not an in-game test. The declared .308 association is retained, but does not establish usable firing or reload behavior.

[minigun]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L52
[fire-gate]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L83-L98
[reload-start]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L184-L218
[minigun-recipe]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/resources/data/minecraft/recipes/gun/minigun.json#L1-L39
[recipe-output]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L124-L151
[creative]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1666-L1671
[gun-registration]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/Items.java#L2708-L2714
[magazine]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L308-L331
[extended-size]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L215-L216
[compatibility]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
