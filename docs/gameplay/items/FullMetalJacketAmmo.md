# Full Metal Jacket Ammo

Full Metal Jacket Ammo is an installed **Ammo Modifier** attachment for TaCZ firearms. Fitting it does not apply the bundled armor-penetration, damage, projectile-speed, or piercing adjustments in this snapshot. See [Behavior](#behavior) before spending the recipe materials.

## Obtaining

Craft **one Full Metal Jacket Ammo** at the [TaCZ Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table), under **Extended Mag**. Carry these materials for one craft. The menu group comes from the bundled index; it is separate from the attachment's refit slot. [Recipe][recipe] · [Group][index] · [Active loader][loader] · [Table groups][groups]

| Material | Amount |
| --- | ---: |
| [Crying Obsidian](CryingObsidian.md) | 2 |
| [Diamonds](Diamond.md) | 5 |

The [workbench guide](../blocks/TaCZWorkbenches.md#using-a-workbench) explains crafting and output handling. Survival crafting consumes the listed materials; Creative still requires them to be present but does not consume them. [Craft transaction][craft]

It is registered as `minecraft:ammo_mod_fmj`, stacks to **1**, and also has a Creative Menu entry. The [inventory item browser](../mechanics/InventoryBrowser.md#mode-and-permission-limits) displays entries in Survival but does not grant ordinary Survival item insertion. [Registration][registration] · [Creative entries][creative]

## Usage

Carry the attachment, hold a compatible gun in your **main hand**, and open **Refit** with **Z** by default. Select **Ammo Modifier**, then choose the carried item. Installation requires both the correct attachment category and this exact item in that gun's accepted list. The 50 compatible guns are listed below. [Default key][keys] · [Refit opening][refit-input] · [Refit choices][refit-screen] · [Fit check][fit] · [Gun definitions][guns]

Only **one Ammo Modifier** occupies that slot. Installing transfers one carried attachment to the gun, including in Creative; firing does not spend the installed attachment. Continue reloading with the gun's ordinary matching ammunition: this item is **not a consumable cartridge** and does not fill the magazine. [Installation][refit-server] · [Slot storage][storage] · [Firing consumption][shot] · [Reload matching][reload]

Use **Unload** to remove it. Follow [Refitting attachments](../mechanics/TaCZFirearms.md#refitting-attachments) for replacement, removal and full-inventory handling, and leave inventory room even in Creative. In Survival, replacement returns or drops the old item; unloading with no room restores it to the gun. Creative inventory overflow can discard the returned item. [Refit transaction][refit-server] · [Inventory insertion][overflow]

## Properties

| Property | Value |
| --- | --- |
| Attachment type | Ammo Modifier |
| Attachment level | 1 |
| Compatible guns | [.30-06 Lonetrail Hand Cannon](3006LonetrailHandCannon.md), [.357 Rhino Revolver](357RhinoRevolver.md), [AA12 Shotgun](AA12Shotgun.md), [Accuracy International AWM](AccuracyInternationalAWM.md), [AKM](AKM.md), [AUG](AUG.md), [B93R](B93R.md), [CZ 75](CZ75.md), [DB-2 Durin](DB2Durin.md), [DB-4 Ursus](DB4Ursus.md), [Deagle 50](Deagle50.md), [FN EVOLYS Machine Gun](FNEVOLYSMachineGun.md), [FN FAL Battle Rifle](FNFALBattleRifle.md), [G36K](G36K.md), [Glock 17](Glock17.md), [Golden Deagle 357](GoldenDeagle357.md), [HK G3 Battle rifle](HKG3Battlerifle.md), [HK-416A5](HK416A5.md), [HK-MP5A5](HKMP5A5.md), [M1014 Battle Shotgun](M1014BattleShotgun.md), [M107 Sniper Rifle](M107SniperRifle.md), [M16A1 Service Rifle](M16A1ServiceRifle.md), [M16A4 Service Rifle](M16A4ServiceRifle.md), [M1911](M1911.md), [M249 Machine Gun](M249MachineGun.md), [M4A1 Carbine](M4A1Carbine.md), [M700 Sniper Rifle](M700SniperRifle.md), [M870](M870.md), [M95 .50 Cal Antimaterial](M9550CalAntimaterial.md), [M9A4](M9A4.md), [Mauser Kar98k Rifle](MauserKar98kRifle.md), [MK14 EBR](MK14EBR.md), [MK23 Offensive Pistol](MK23OffensivePistol.md), [P320](P320.md), [P90 PDW](P90PDW.md), [QBZ-191 Assault Rifle](QBZ191AssaultRifle.md), [QBZ-95 "Longbow"](QBZ95Longbow.md), [RPK](RPK.md), [SCAR-H Battle Rifle](SCARHBattleRifle.md), [SCAR-L Assault Rifle](SCARLAssaultRifle.md), [Sks Tactical Rifle](SksTacticalRifle.md), [SPAS-12 Multi-purpose Shotgun](SPAS12MultipurposeShotgun.md), [SPR-15 HB "Sagittarius"](SPR15HBSagittarius.md), [Springfield 1873 Trapdoor Rifle](Springfield1873TrapdoorRifle.md), [Taurus "Raging Hunter" Hand Cannon](TaurusRagingHunterHandCannon.md), [Timeless .50 Z-Type](Timeless50ZType.md), [Type 81-1 Service Rifle](Type811ServiceRifle.md), [UMP45 SMG](UMP45SMG.md), [UZI](UZI.md), [Vector SMG](VectorSMG.md) |

[Registered type and level][attachment] · [Type-and-item fit check][fit] · [Accepted gun definitions][guns]

## Behavior

**The armor-piercing effect is not implemented by the checked firing and hit paths.** The bundled profile declares armor-ignore and piercing changes, a damage multiplier, and a projectile-speed multiplier, but shot creation takes those ballistic values from the gun and its fire mode. The projectile uses its supplied damage curve, headshot multiplier and pierce count; it does not apply this installed modifier. Do not craft it expecting extra penetration or a different damage/speed tradeoff. [Bundled profile][profile] · [Shot creation][shot] · [Ballistics][ballistics] · [Entity hits][hit]

The **Full Metal Jacket** name is not a separate cartridge choice for reloading. For example, a compatible [Glock 17](Glock17.md) still reloads with [9mm Bullets](9mmBullet.md), while an [AA12](AA12Shotgun.md) still uses [12 Gauge Bullets](12GaugeBullet.md). [Gun definitions][guns] · [Reload matching][reload]

## Notes

- This item is part of the integrated [TaCZ firearms](../mechanics/TaCZFirearms.md) system.
- **Extended Mag** is its crafting-menu group, while **Ammo Modifier** is its installation slot. It does not increase magazine capacity: that lookup reads the separate Extended Mag slot. Imported `extended_mag_level` fields do not override the registered attachment level above. [Index][index] · [Registration data][attachment] · [Capacity lookup][magazine]
- Its bundled profile contains no camera-recoil modifiers. The shared recoil collector visits this slot but adds no modifier from this profile; aiming zoom reads the Scope slot. These paths do not supply a recoil or zoom bonus for this item. [Profile][profile] · [Recoil collector][recoil-collector] · [Recoil parsing][recoil-loader] · [Zoom selection][zoom]

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked registration, recipe/group loading, Java type-and-item compatibility, refit transactions, reload matching, and active firing, ballistics, projectile-hit, recoil and zoom consumers. This is **source review**, not an in-game crafting, refitting, reloading, accuracy, damage, explosion, ignition or multiplayer test. Imported descriptions and profile fields are not treated as proof of working effects.

[attachment]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L111-L115
[ballistics]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L40-L80
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L42-L68
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1663-L1671
[fit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[groups]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L151-L185
[guns]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L10-L73
[hit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L162-L203
[index]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/index/attachments/ammo_mod_fmj.json#L1-L8
[keys]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L13-L21
[loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L180
[magazine]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L321-L331
[overflow]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Inventory.java#L250-L290
[profile]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/attachments/ammo_mod_fmj_data.json#L1-L21
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipes/attachments/ammo_mod_fmj.json#L1-L21
[recoil-collector]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L110-L147
[recoil-loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L285-L315
[refit-input]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L36-L48
[refit-screen]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczGunRefitScreen.java#L81-L110
[refit-server]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2227-L2267
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2724-L2740
[reload]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L261-L297
[shot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L146-L181
[storage]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczRefitGun.java#L28-L80
[zoom]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczScopeData.java#L27-L51
