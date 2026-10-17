# Sniper Ammo Extended Mag II

Sniper Ammo Extended Mag II selects a level-2 magazine capacity on compatible TaCZ firearms. Check the gun-specific results below before choosing a tier.

## Obtaining

Craft **one Sniper Ammo Extended Mag II** at the [TaCZ Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table), in **Extended Mag**. Carry the following materials for one craft. [Recipe][recipe] · [Recipe group][index] · [Table groups][groups]

| Material | Amount |
| --- | ---: |
| [Iron Ingots](IronIngot.md) | 26 |
| [Gold Ingots](GoldIngot.md) | 12 |
| [Lapis Lazuli](LapisLazuli.md) | 12 |

This recipe is crafted directly from materials; it does not require a lower-tier magazine. The [workbench guide](../blocks/TaCZWorkbenches.md#using-a-workbench) explains the shared crafting controls and inventory handling. [Craft transaction][craft]

It is registered as `minecraft:sniper_extended_mag_2` and also has a Creative Menu entry. The [inventory item browser](../mechanics/InventoryBrowser.md#mode-and-permission-limits) is visible in Survival, but its catalog does not grant ordinary Survival item insertion. Use the recipe above to craft it in Survival. [Registration][registration] · [Creative entries][creative]

## Usage

Carry the attachment, hold a compatible gun in your **main hand**, and open **Refit** with **Z** by default. Select **Extended Mag** and choose the carried attachment. Follow [Refitting attachments](../mechanics/TaCZFirearms.md#refitting-attachments) for the full installation, replacement, and **Unload** procedure. [Default key][keys] · [Refit opening][refit-input] · [Server installation][refit-server]

Only one extended magazine occupies the slot, so tiers do not stack. Fitting the attachment does **not** add loaded ammunition. Press **R** by default to reload after increasing capacity, and keep holding the gun until the reload completes. See [Magazine, reserve, and reloading](../mechanics/TaCZFirearms.md#magazine-reserve-and-reloading). [Slot storage][slot-storage] · [Reload completion][reload-gate] · [Reload consumption][reload]

The AWM uses [.338 Lapua Bullets](338LapuaBullet.md), the M107 and M95 use [.50 BMG](50BMG.md), and the M700 uses [.30-06 Springfield Bullets](3006SpringfieldBullet.md). These stack to **30**, **30**, and **36**, respectively, enough for any matching capacity shown here. Survival reloads take ammunition from the **first matching stack only**; combine small stacks or reload again if the gun remains partly loaded. Creative reloads supply missing rounds without consuming reserve ammunition. [Gun definitions][gun-ai_awp] · [M107][gun-m107] · [M700][gun-m700] · [M95][gun-m95] · [Ammunition definitions][ammo-stacks] · [Stack registration][ammo-registration] · [Reload consumption][reload]

## Properties

| Property | Value |
| --- | --- |
| Attachment type | Extended Mag |
| Attachment level | 2 |
| Maximum stack size | 1 |
| Compatible guns | [Accuracy International AWM](AccuracyInternationalAWM.md), [M107 Sniper Rifle](M107SniperRifle.md), [M700 Sniper Rifle](M700SniperRifle.md), [M95 .50 Cal Antimaterial](M9550CalAntimaterial.md) |

[Attachment level][tier] · [Item registration][registration] · [Gun fit check][fit]

**These do not fit every rifle.** [Mauser Kar98k Rifle](MauserKar98kRifle.md) and [Springfield 1873 Trapdoor Rifle](Springfield1873TrapdoorRifle.md) have no accepted extended-magazine slot. [MK14 EBR](MK14EBR.md) instead uses [Heavy Ammo Extended Mag II](HeavyAmmoExtendedMagII.md). [Kar98k fit][gun-kar98] · [Springfield fit][gun-springfield1873] · [MK14 fit][gun-mk14]

## Behavior

Level 2 selects the gun's configured tier-II capacity; it does not add a fixed number of rounds to every gun. **Base** below means no extended magazine is installed. These are magazine capacities in rounds, not rounds supplied by installing the attachment. [Capacity lookup][magazine] · [Level selection][level-selection]

| Compatible gun | Base | With this attachment | Source |
| --- | ---: | ---: | --- |
| [Accuracy International AWM](AccuracyInternationalAWM.md) | 5 | 7 | [Definition][gun-ai_awp] |
| [M107 Sniper Rifle](M107SniperRifle.md) | 10 | 14 | [Definition][gun-m107] |
| [M700 Sniper Rifle](M700SniperRifle.md) | 5 | 8 | [Definition][gun-m700] |
| [M95 .50 Cal Antimaterial](M9550CalAntimaterial.md) | 5 | 8 | [Definition][gun-m95] |

Compare the sniper magazine tiers: [I](SniperAmmoExtendedMagI.md), **II**, [III](SniperAmmoExtendedMagIII.md).

**Reducing capacity can lose loaded ammunition.** Before replacing or removing a magazine, use up rounds above the capacity you will switch to. Refitting leaves the stored loaded count unchanged. The next shot subtracts one round and then caps the remainder at the new capacity, discarding any excess without returning it to your inventory. Reload cannot start while the loaded count is already at or above capacity. [Attachment changes][refit-storage] · [Ammo limit][magazine] · [Shot consumption][shot] · [Reload check][reload-gate]

## Notes

- This item is part of the integrated TaCZ firearms system.
- Compatibility requires both the Extended Mag category and this exact attachment in the gun's accepted list; the Sniper name or ammunition caliber alone is not enough. [Gun fit check][fit]
- Imported weight and aiming fields are not treated here as verified gameplay penalties. The active attachment profile path reads recoil modifiers, which this magazine's profile does not provide. [Attachment profile][profile] · [Profile consumer][profile-loader]
- The loose attachment's English tooltip displays **Capacity: 24 rounds** for this tier. That fixed label does not determine a gun's capacity; use the table above or the fitted gun's ammunition tooltip. [Attachment tooltip][attachment-tooltip] · [English labels][tooltip-labels] · [Gun tooltip][gun-tooltip]

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. The bundled recipe, active workbench loader and group, registered attachment level, gun-specific fit checks, capacity lookup, refit storage, ammunition stack limits, and reload/firing consumers were checked. This is **source review**, not an in-game crafting, refitting, reloading, or combat test. The results describe this integrated snapshot. [Recipe loading][loader]

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
[ammo-registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2716-L2721
[ammo-stacks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L78-L91
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipes/attachments/sniper_extended_mag_2.json#L1-L27
[index]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/index/attachments/sniper_extended_mag_2.json#L1-L7
[tier]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L197
[profile]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/attachments/sniper_extended_mag_2_data.json#L1-L9
[gun-ai_awp]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L12
[gun-m107]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L41
[gun-m700]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L48
[gun-m95]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L50
[gun-kar98]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L29
[gun-springfield1873]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L66
[gun-mk14]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L53
