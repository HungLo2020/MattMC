# Light Ammo Extended Mag II

Light Ammo Extended Mag II selects a level-2 magazine capacity on compatible TaCZ firearms. Check the gun-specific results below before choosing a tier.

## Obtaining

Craft **one Light Ammo Extended Mag II** at the [TaCZ Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table), in **Extended Mag**. Carry the following materials for one craft. [Recipe][recipe] · [Recipe group][index] · [Table groups][groups]

| Material | Amount |
| --- | ---: |
| [Iron Ingots](IronIngot.md) | 12 |
| [Lapis Lazuli](LapisLazuli.md) | 8 |

Craft this tier directly from these materials; it does not consume a lower-tier magazine. The [workbench guide](../blocks/TaCZWorkbenches.md#using-a-workbench) explains the shared crafting controls and inventory handling. [Craft transaction][craft]

It is registered as `minecraft:light_extended_mag_2` and also has a Creative Menu entry. The [inventory item browser](../mechanics/InventoryBrowser.md#mode-and-permission-limits) is visible in Survival, but its catalog does not grant ordinary Survival item insertion. Use the recipe above to craft it in Survival. [Registration][registration] · [Creative entries][creative]

## Usage

Carry the attachment, hold a compatible gun in your **main hand**, and open **Refit** with **Z** by default. Select **Extended Mag** and choose the carried attachment. Follow [Refitting attachments](../mechanics/TaCZFirearms.md#refitting-attachments) for the full installation, replacement, and removal procedure. [Default key][keys] · [Refit opening][refit-input] · [Server installation][refit-server]

Only one extended magazine occupies the slot, so tiers do not stack. Fitting the attachment does **not** add loaded ammunition. After increasing capacity, reload with that gun's matching ammunition to fill the extra space; see [Magazine, reserve, and reloading](../mechanics/TaCZFirearms.md#magazine-reserve-and-reloading) for partial Survival reloads. [Slot storage][slot-storage] · [Reload consumption][reload]

## Properties

| Property | Value |
| --- | --- |
| Attachment type | Extended Mag |
| Attachment level | 2 |
| Compatible guns | [B93R](B93R.md), [CZ 75](CZ75.md), [Deagle 50](Deagle50.md), [Glock 17](Glock17.md), [Golden Deagle 357](GoldenDeagle357.md), [HK-MP5A5](HKMP5A5.md), [M1911](M1911.md), [M1A1 Thompson](M1A1Thompson.md), [M9A4](M9A4.md), [MK23 Offensive Pistol](MK23OffensivePistol.md), [P320](P320.md), [Timeless .50 Z-Type](Timeless50ZType.md), [UMP45 SMG](UMP45SMG.md), [UZI](UZI.md), [Vector SMG](VectorSMG.md) |

[Attachment level][tier] · [Gun fit check][fit]

## Behavior

Level 2 selects the gun's configured tier-II capacity; it does not add a fixed number of rounds to every gun. **Base** below means no extended magazine is installed. These are magazine capacities in rounds, not rounds supplied by installing the attachment. [Capacity lookup][magazine] · [Level selection][level-selection]

For the [M1A1 Thompson](M1A1Thompson.md), tiers I and II both hold **30 rounds**. Tier II gives that gun no additional capacity over tier I; tier III selects **1 round**. [Thompson definition][gun-m1a1]

| Compatible gun | Base | With this attachment | Source |
| --- | ---: | ---: | --- |
| [B93R](B93R.md) | 20 | 26 | [Definition][gun-b93r] |
| [CZ 75](CZ75.md) | 16 | 23 | [Definition][gun-cz75] |
| [Deagle 50](Deagle50.md) | 7 | 10 | [Definition][gun-deagle] |
| [Glock 17](Glock17.md) | 17 | 25 | [Definition][gun-glock_17] |
| [Golden Deagle 357](GoldenDeagle357.md) | 9 | 15 | [Definition][gun-deagle_golden] |
| [HK-MP5A5](HKMP5A5.md) | 30 | 50 | [Definition][gun-hk_mp5a5] |
| [M1911](M1911.md) | 7 | 12 | [Definition][gun-m1911] |
| [M1A1 Thompson](M1A1Thompson.md) | 20 | 30 | [Definition][gun-m1a1] |
| [M9A4](M9A4.md) | 17 | 25 | [Definition][gun-m9a4] |
| [MK23 Offensive Pistol](MK23OffensivePistol.md) | 12 | 20 | [Definition][gun-hk_mk23] |
| [P320](P320.md) | 12 | 16 | [Definition][gun-p320] |
| [Timeless .50 Z-Type](Timeless50ZType.md) | 8 | 11 | [Definition][gun-timeless50] |
| [UMP45 SMG](UMP45SMG.md) | 25 | 40 | [Definition][gun-ump45] |
| [UZI](UZI.md) | 20 | 40 | [Definition][gun-uzi] |
| [Vector SMG](VectorSMG.md) | 20 | 40 | [Definition][gun-vector45] |

**Reducing capacity can lose loaded ammunition.** Before replacing or removing a magazine, use up rounds above the capacity you will switch to. Refitting leaves the stored loaded count unchanged. The next shot subtracts one round and then caps the remainder at the new capacity, discarding any excess without returning it to your inventory. Reload cannot start while the loaded count is already at or above capacity. [Attachment changes][refit-storage] · [Ammo limit][magazine] · [Shot consumption][shot] · [Reload check][reload-gate]

## Notes

- This item is part of the integrated TaCZ firearms system.
- Compatibility follows the individual gun's accepted attachments. The Light/Heavy name alone does not identify every gun that can use it. [Gun fit check][fit]
- Imported weight and aiming fields are not treated here as verified gameplay penalties. The active attachment profile path reads recoil modifiers, which this magazine's profile does not provide. [Attachment profile][profile] · [Profile consumer][profile-loader]
- The loose attachment's English tooltip displays **Capacity: 24 rounds** for this tier. That fixed label does not determine a gun's capacity; use the table above or the fitted gun's ammunition tooltip. [Attachment tooltip][attachment-tooltip] · [English labels][tooltip-labels] · [Gun tooltip][gun-tooltip]

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
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipes/attachments/light_extended_mag_2.json#L1-L21
[index]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/index/attachments/light_extended_mag_2.json#L1-L7
[tier]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L140
[profile]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/attachments/light_extended_mag_2_data.json#L1-L9
[gun-b93r]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L15
[gun-cz75]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L16
[gun-deagle]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L19
[gun-glock_17]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L24
[gun-deagle_golden]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L20
[gun-hk_mp5a5]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L27
[gun-m1911]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L44
[gun-m1a1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L32
[gun-m9a4]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L51
[gun-hk_mk23]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L26
[gun-p320]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L54
[gun-timeless50]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L69
[gun-ump45]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L71
[gun-uzi]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L72
[gun-vector45]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L73
