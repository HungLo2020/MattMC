# 7.62x25mm Tokarev Bullet

## Obtaining

Craft it at the [TaCZ Ammo Assembly Table](../blocks/TaCZWorkbenches.md#ammo-assembly-table), under **Personal Defense Cartridges**. Carry the materials in your inventory and use the craft control once for each batch. [Crafting rules][workbench-craft]

| Materials per craft | Output per click |
| --- | --- |
| 10 Copper Ingots · 2 Gunpowder | **45 × 7.62x25mm Tokarev Bullet** |

[Recipe][recipe] · [Group selection][workbench-loading] · [Category names][category-names]

It also appears in the Creative Menu and is registered as `minecraft:762x25`. [Creative entry][creative] · [Registration][registration]

## Usage

No currently registered TaCZ firearm uses this ammunition ID. It is not a compatible reload supply for any gun in the current integrated gun list. [Gun definitions][gun-definitions] · [Ammo matching][reload]

## Properties

| Property | Value |
| --- | --- |
| Stack size | 60 |
| Used by | No integrated guns currently use this ammo |

## Behavior

Survival reloading and reserve-ammo counting select the held gun’s exact ammunition item. None of the current gun definitions selects this item; the general gun firing and reloading description does not establish a use for it. [Ammo matching][reload] · [Reserve counting][reserve]

See [TaCZ Firearms](../mechanics/TaCZFirearms.md) for controls and shared ammunition rules.

## Notes

* This item is part of the integrated TaCZ firearms system.

## Sources and verification

Source-reviewed on **2026-10-03** at `cfa7057b6fe2b8dfa84e93f21932be2602eff749`. The recipe, registration, stack size, gun associations, and ammunition behavior were checked against the bundled sources. This is source review, not an in-game crafting or reload test. [Ammunition definition][ammo-definition]

[ammo-definition]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L97
[registration]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/Items.java#L2708-L2722
[creative]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1671
[gun-definitions]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L10-L108
[reload]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L261-L318
[reserve]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L351-L369
[workbench-craft]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L42-L68
[workbench-loading]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L124-L174
[category-names]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/resources/assets/minecraft/lang/en_us.json#L8541-L8547
[recipe]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/resources/data/minecraft/recipes/ammo/762x25.json#L1-L23
