# 5.56x45mm Bullet

## Obtaining

Craft it at the [TaCZ Ammo Assembly Table](../blocks/TaCZWorkbenches.md#ammo-assembly-table), under **Intermediate & Full-Power Rifle Cartridges**. Carry the materials in your inventory and use the craft control once for each batch. [Crafting rules][workbench-craft]

| Materials per craft | Output per click |
| --- | --- |
| 15 Copper Ingots · 3 Gunpowder | **45 × 5.56x45mm Bullet** |

[Recipe][recipe] · [Group selection][workbench-loading] · [Category names][category-names]

It also appears in the Creative Menu and is registered as `minecraft:556x45`. [Creative entry][creative] · [Registration][registration]

## Usage

This ammunition supplies the compatible TaCZ guns listed below. In Survival, carried ammunition is consumed when a completed reload adds rounds to the magazine. Firing uses the gun’s loaded magazine in both Survival and Creative. Creative reloads supply any missing magazine rounds without requiring or consuming carried ammunition. [Gun definitions][gun-definitions] · [Firing][firing] · [Reloading][reload]

## Properties

| Property | Value |
| --- | --- |
| Stack size | 60 |
| Used by | [AUG](AUG.md), [G36K](G36K.md), [HK-416A5](HK416A5.md), [M16A1 Service Rifle](M16A1ServiceRifle.md), [M16A4 Service Rifle](M16A4ServiceRifle.md), [M249 Machine Gun](M249MachineGun.md), [M4A1 Carbine](M4A1Carbine.md), [SCAR-L Assault Rifle](SCARLAssaultRifle.md), [SPR-15 HB "Sagittarius"](SPR15HBSagittarius.md) |

## Behavior

When ammunition is available, a Survival reload draws from the first matching inventory stack only, up to the missing magazine capacity. If that stack is too small, the reload is partial even when later stacks hold more. The HUD reserve is based on all matching carried stacks, capped at 9,999; Creative displays 9,999. [Reload supply][reload] · [Reserve counting][reserve] · [HUD][hud] · [HUD limit][hud-cap]

See [TaCZ Firearms](../mechanics/TaCZFirearms.md) for controls and shared ammunition rules.

## Notes

* This item is part of the integrated TaCZ firearms system.

## Sources and verification

Source-reviewed on **2026-10-03** at `cfa7057b6fe2b8dfa84e93f21932be2602eff749`. The recipe, registration, stack size, gun associations, and ammunition behavior were checked against the bundled sources. This is source review, not an in-game crafting or reload test. [Ammunition definition][ammo-definition]

[ammo-definition]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L93
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
[recipe]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/resources/data/minecraft/recipes/ammo/556x45.json#L1-L23
