# .308 Winchester Bullet

## Obtaining

Craft it at the [TaCZ Ammo Assembly Table](../blocks/TaCZWorkbenches.md#ammo-assembly-table), under **Intermediate & Full-Power Rifle Cartridges**. Carry the materials in your inventory and use the craft control once for each batch. [Crafting rules][workbench-craft]

| Materials per craft | Output per click |
| --- | --- |
| 30 Copper Ingots · 10 Gunpowder · 1 Lapis Lazuli | **60 × .308 Winchester Bullet** |

[Recipe][recipe] · [Group selection][workbench-loading] · [Category names][category-names]

This **60-item batch** is larger than the normal **48-item stack size**; leave room for more than one stack.

It also appears in the Creative Menu and is registered as `minecraft:308`. [Creative entry][creative] · [Registration][registration]

## Usage

This ammunition is declared by the TaCZ guns listed below. Minigun has a zero-round magazine in the current integrated definitions, so the current integrated gun handlers cannot fire or reload it. For the other listed guns, carried ammunition is consumed in Survival when a completed reload adds rounds to the magazine. Firing uses the gun’s loaded magazine in both Survival and Creative. Creative reloads supply any missing magazine rounds without requiring or consuming carried ammunition. [Gun definitions][gun-definitions] · [Firing][firing] · [Reloading][reload] · [Zero-capacity firing gate][fire-gate]

## Properties

| Property | Value |
| --- | --- |
| Stack size | 48 |
| Used by | [FN EVOLYS Machine Gun](FNEVOLYSMachineGun.md), [FN FAL Battle Rifle](FNFALBattleRifle.md), [HK G3 Battle rifle](HKG3Battlerifle.md), [M134 Minigun](M134Minigun.md), [MK14 EBR](MK14EBR.md), [SCAR-H Battle Rifle](SCARHBattleRifle.md) |

## Behavior

For the listed guns that can reload, a Survival reload draws from the first matching inventory stack only, up to the missing magazine capacity. If that stack is too small, the reload is partial even when later stacks hold more. The HUD reserve is based on all matching carried stacks, capped at 9,999; Creative displays 9,999. [Reload supply][reload] · [Reserve counting][reserve] · [HUD][hud] · [HUD limit][hud-cap]

See [TaCZ Firearms](../mechanics/TaCZFirearms.md) for controls and shared ammunition rules.

## Notes

* This item is part of the integrated TaCZ firearms system.

## Sources and verification

Source-reviewed on **2026-10-03** at `cfa7057b6fe2b8dfa84e93f21932be2602eff749`. The recipe, registration, stack size, gun associations, and ammunition behavior were checked against the bundled sources. This is source review, not an in-game crafting or reload test. [Ammunition definition][ammo-definition]

[ammo-definition]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L82
[registration]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/Items.java#L2708-L2722
[creative]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1671
[gun-definitions]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L10-L108
[reload]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L261-L318
[firing]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L125-L218
[reserve]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L351-L369
[hud]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/client/tacz/TaczGunHudOverlay.java#L18-L58
[hud-cap]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/client/tacz/TaczGunHudOverlay.java#L122-L129
[workbench-craft]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L42-L68
[workbench-loading]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L124-L174
[category-names]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/resources/assets/minecraft/lang/en_us.json#L8541-L8547
[recipe]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/resources/data/minecraft/recipes/ammo/308.json#L1-L28
[fire-gate]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L78-L96
