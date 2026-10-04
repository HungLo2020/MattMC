# Heavy Ammo Extended Mag III

Heavy Ammo Extended Mag III selects a level-3 magazine capacity on compatible TaCZ firearms. Check the gun-specific results below before choosing a tier. **On the M1 Carbine, this tier selects only 1 round.**

## Obtaining

Craft **one Heavy Ammo Extended Mag III** at the [TaCZ Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table), in **Extended Mag**. Carry the following materials for one craft. [Recipe][recipe] · [Recipe group][index] · [Table groups][groups]

| Material | Amount |
| --- | ---: |
| [Iron Ingots](IronIngot.md) | 32 |
| [Gold Ingots](GoldIngot.md) | 12 |
| [Diamonds](Diamond.md) | 3 |

Craft this tier directly from these materials; it does not consume a lower-tier magazine. The [workbench guide](../blocks/TaCZWorkbenches.md#using-a-workbench) explains the shared crafting controls and inventory handling. [Craft transaction][craft]

It is registered as `minecraft:extended_mag_3` and also has a Creative Menu entry. The [inventory item browser](../mechanics/InventoryBrowser.md#mode-and-permission-limits) is visible in Survival, but its catalog does not grant ordinary Survival item insertion. Use the recipe above to craft it in Survival. [Registration][registration] · [Creative entries][creative]

## Usage

Carry the attachment, hold a compatible gun in your **main hand**, and open **Refit** with **Z** by default. Select **Extended Mag** and choose the carried attachment. Follow [Refitting attachments](../mechanics/TaCZFirearms.md#refitting-attachments) for the full installation, replacement, and removal procedure. [Default key][keys] · [Refit opening][refit-input] · [Server installation][refit-server]

Only one extended magazine occupies the slot, so tiers do not stack. Fitting the attachment does **not** add loaded ammunition. After increasing capacity, reload with that gun's matching ammunition to fill the extra space; see [Magazine, reserve, and reloading](../mechanics/TaCZFirearms.md#magazine-reserve-and-reloading) for partial Survival reloads. [Slot storage][slot-storage] · [Reload consumption][reload]

## Properties

| Property | Value |
| --- | --- |
| Attachment type | Extended Mag |
| Attachment level | 3 |
| Compatible guns | [AA12 Shotgun](AA12Shotgun.md), [AKM](AKM.md), [AUG](AUG.md), [FN EVOLYS Machine Gun](FNEVOLYSMachineGun.md), [FN FAL Battle Rifle](FNFALBattleRifle.md), [G36K](G36K.md), [HK G3 Battle rifle](HKG3Battlerifle.md), [HK-416A5](HK416A5.md), [M1 Carbine](M1Carbine.md), [M16A1 Service Rifle](M16A1ServiceRifle.md), [M16A4 Service Rifle](M16A4ServiceRifle.md), [M249 Machine Gun](M249MachineGun.md), [M4A1 Carbine](M4A1Carbine.md), [MK14 EBR](MK14EBR.md), [QBZ-191 Assault Rifle](QBZ191AssaultRifle.md), [QBZ-95 "Longbow"](QBZ95Longbow.md), [RPK](RPK.md), [SCAR-H Battle Rifle](SCARHBattleRifle.md), [SCAR-L Assault Rifle](SCARLAssaultRifle.md), [Sks Tactical Rifle](SksTacticalRifle.md), [SPR-15 HB "Sagittarius"](SPR15HBSagittarius.md), [Type 81-1 Service Rifle](Type811ServiceRifle.md) |

[Attachment level][tier] · [Gun fit check][fit]

## Behavior

Level 3 selects the gun's configured tier-III capacity; it does not add a fixed number of rounds to every gun. **Base** below means no extended magazine is installed. These are magazine capacities in rounds, not rounds supplied by installing the attachment. [Capacity lookup][magazine] · [Level selection][level-selection]

**M1 Carbine exception:** this tier is accepted by the gun, but sets its magazine capacity to **1 round**, down from 15 without an extended magazine, 20 with tier I, or 30 with tier II. This is the literal selected capacity, not a bonus or a fallback marker. [Carbine definition][gun-m1] · [Capacity selection][level-selection]

| Compatible gun | Base | With this attachment | Source |
| --- | ---: | ---: | --- |
| [AA12 Shotgun](AA12Shotgun.md) | 8 | 32 | [Definition][gun-aa12] |
| [AKM](AKM.md) | 30 | 40 | [Definition][gun-ak47] |
| [AUG](AUG.md) | 30 | 50 | [Definition][gun-aug] |
| [FN EVOLYS Machine Gun](FNEVOLYSMachineGun.md) | 75 | 150 | [Definition][gun-fn_evolys] |
| [FN FAL Battle Rifle](FNFALBattleRifle.md) | 20 | 35 | [Definition][gun-fn_fal] |
| [G36K](G36K.md) | 30 | 100 | [Definition][gun-g36k] |
| [HK G3 Battle rifle](HKG3Battlerifle.md) | 20 | 40 | [Definition][gun-hk_g3] |
| [HK-416A5](HK416A5.md) | 30 | 100 | [Definition][gun-hk416d] |
| [M1 Carbine](M1Carbine.md) | 15 | **1** | [Definition][gun-m1] |
| [M16A1 Service Rifle](M16A1ServiceRifle.md) | 20 | 30 | [Definition][gun-m16a1] |
| [M16A4 Service Rifle](M16A4ServiceRifle.md) | 30 | 45 | [Definition][gun-m16a4] |
| [M249 Machine Gun](M249MachineGun.md) | 75 | 200 | [Definition][gun-m249] |
| [M4A1 Carbine](M4A1Carbine.md) | 30 | 60 | [Definition][gun-m4a1] |
| [MK14 EBR](MK14EBR.md) | 10 | 20 | [Definition][gun-mk14] |
| [QBZ-191 Assault Rifle](QBZ191AssaultRifle.md) | 30 | 75 | [Definition][gun-qbz_191] |
| [QBZ-95 "Longbow"](QBZ95Longbow.md) | 30 | 75 | [Definition][gun-qbz_95] |
| [RPK](RPK.md) | 40 | 75 | [Definition][gun-rpk] |
| [SCAR-H Battle Rifle](SCARHBattleRifle.md) | 20 | 40 | [Definition][gun-scar_h] |
| [SCAR-L Assault Rifle](SCARLAssaultRifle.md) | 30 | 70 | [Definition][gun-scar_l] |
| [Sks Tactical Rifle](SksTacticalRifle.md) | 10 | 25 | [Definition][gun-sks_tactical] |
| [SPR-15 HB "Sagittarius"](SPR15HBSagittarius.md) | 15 | 30 | [Definition][gun-spr15hb] |
| [Type 81-1 Service Rifle](Type811ServiceRifle.md) | 30 | 40 | [Definition][gun-type_81] |

**Reducing capacity can lose loaded ammunition.** Before replacing or removing a magazine, use up rounds above the capacity you will switch to. Refitting leaves the stored loaded count unchanged. The next shot subtracts one round and then caps the remainder at the new capacity, discarding any excess without returning it to your inventory. Reload cannot start while the loaded count is already at or above capacity. [Attachment changes][refit-storage] · [Ammo limit][magazine] · [Shot consumption][shot] · [Reload check][reload-gate]

For example, if you fit this tier to a Carbine with 15 loaded rounds, the count initially stays at 15. After the next shot, only 1 round remains: 1 was fired and 13 excess rounds were lost. [Stored ammo and cap][magazine] · [Shot consumption][shot]

## Notes

- This item is part of the integrated TaCZ firearms system.
- Compatibility follows the individual gun's accepted attachments. The Light/Heavy name alone does not identify every gun that can use it. [Gun fit check][fit]
- Imported weight and aiming fields are not treated here as verified gameplay penalties. The active attachment profile path reads recoil modifiers, which this magazine's profile does not provide. [Attachment profile][profile] · [Profile consumer][profile-loader]
- The loose attachment's English tooltip displays **Capacity: 33 rounds** for this tier. That fixed label does not determine a gun's capacity; use the table above or the fitted gun's ammunition tooltip. [Attachment tooltip][attachment-tooltip] · [English labels][tooltip-labels] · [Gun tooltip][gun-tooltip]
- The imported Carbine allow-list includes only Heavy I and II from this magazine family; the active Java definition also accepts this tier. The compatibility and capacity above follow the server's active fit check. [Imported allow-list][raw-allow] · [Gun definition][gun-m1] · [Server installation][refit-server]

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. The bundled recipe, active workbench loader and group, registered attachment level, gun-specific fit checks, capacity lookup, refit storage, and reload/firing consumers were checked. This is **source review**, not an in-game crafting, refitting, reloading, or combat test. The results describe this integrated snapshot. [Recipe loading][loader]

[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2724-L2740
[loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L174
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L42-L68
[groups]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L151-L184
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1663-L1671
[fit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L66
[refit-server]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2227-L2266
[refit-storage]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczRefitGun.java#L28-L48
[slot-storage]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczRefitGun.java#L61-L80
[magazine]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L308-L330
[level-selection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L215-L216
[reload]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L261-L297
[reload-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L184-L218
[shot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L146-L181
[keys]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L13-L21
[refit-input]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L36-L46
[profile-loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L285-L329
[attachment-tooltip]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczAttachmentItem.java#L36-L41
[tooltip-labels]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/assets/minecraft/lang/en_us.json#L8256-L8259
[gun-tooltip]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L242-L250
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipes/attachments/extended_mag_3.json#L1-L27
[index]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/index/attachments/extended_mag_3.json#L1-L7
[tier]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L121
[profile]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/attachments/extended_mag_3_data.json#L1-L9
[gun-aa12]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L11
[gun-ak47]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L13
[gun-aug]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L14
[gun-fn_evolys]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L21
[gun-fn_fal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L22
[gun-g36k]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L23
[gun-hk_g3]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L25
[gun-hk416d]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L28
[gun-m1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L35
[gun-m16a1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L42
[gun-m16a4]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L43
[gun-m249]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L45
[gun-m4a1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L47
[gun-mk14]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L53
[gun-qbz_191]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L56
[gun-qbz_95]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L57
[gun-rpk]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L60
[gun-scar_h]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L61
[gun-scar_l]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L62
[gun-sks_tactical]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L63
[gun-spr15hb]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L65
[gun-type_81]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L70
[raw-allow]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tacz_tags/attachments/allow_attachments/m1.json#L1-L8
