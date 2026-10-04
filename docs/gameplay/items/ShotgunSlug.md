# Shotgun Slug

Shotgun Slug is an installed **Ammo Modifier** attachment for TaCZ firearms. Fitting it does not turn a shotgun shot into one projectile or apply its bundled accuracy changes in this snapshot. See [Behavior](#behavior) before spending the recipe materials.

## Obtaining

Craft **one Shotgun Slug** at the [TaCZ Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table), under **Extended Mag**. Carry these materials for one craft. The menu group comes from the bundled index; it is separate from the attachment's refit slot. [Recipe][recipe] · [Group][index] · [Active loader][loader] · [Table groups][groups]

| Material | Amount |
| --- | ---: |
| [Crying Obsidian](CryingObsidian.md) | 3 |
| [Netherite Scrap](NetheriteScrap.md) | 1 |

The [workbench guide](../blocks/TaCZWorkbenches.md#using-a-workbench) explains crafting and output handling. Survival crafting consumes the listed materials; Creative still requires them to be present but does not consume them. [Craft transaction][craft]

It is registered as `minecraft:ammo_mod_slug`, stacks to **1**, and also has a Creative Menu entry. The [inventory item browser](../mechanics/InventoryBrowser.md#mode-and-permission-limits) displays entries in Survival but does not grant ordinary Survival item insertion. [Registration][registration] · [Creative entries][creative]

## Usage

Carry the attachment, hold a compatible gun in your **main hand**, and open **Refit** with **Z** by default. Select **Ammo Modifier**, then choose the carried item. Installation requires both the correct attachment category and this exact item in that gun's accepted list. The 6 compatible guns are listed below. [Default key][keys] · [Refit opening][refit-input] · [Refit choices][refit-screen] · [Fit check][fit] · [Gun definitions][guns]

Only **one Ammo Modifier** occupies that slot. Installing transfers one carried attachment to the gun, including in Creative; firing does not spend the installed attachment. Continue reloading with the gun's ordinary matching ammunition: this item is **not a consumable cartridge** and does not fill the magazine. [Installation][refit-server] · [Slot storage][storage] · [Firing consumption][shot] · [Reload matching][reload]

Use **Unload** to remove it. Follow [Refitting attachments](../mechanics/TaCZFirearms.md#refitting-attachments) for replacement, removal and full-inventory handling, and leave inventory room even in Creative. In Survival, replacement returns or drops the old item; unloading with no room restores it to the gun. Creative inventory overflow can discard the returned item. [Refit transaction][refit-server] · [Inventory insertion][overflow]

## Properties

| Property | Value |
| --- | --- |
| Attachment type | Ammo Modifier |
| Attachment level | 1 |
| Compatible guns | [AA12 Shotgun](AA12Shotgun.md), [DB-2 Durin](DB2Durin.md), [DB-4 Ursus](DB4Ursus.md), [M1014 Battle Shotgun](M1014BattleShotgun.md), [M870](M870.md), [SPAS-12 Multi-purpose Shotgun](SPAS12MultipurposeShotgun.md) |

[Registered type and level][attachment] · [Type-and-item fit check][fit] · [Accepted gun definitions][guns]

## Behavior

**The single-slug conversion is not implemented by the checked firing path.** It keeps the gun's configured projectile count after this attachment is fitted. All six compatible guns still reload with [12 Gauge Bullets](12GaugeBullet.md). [Shot creation][shot] · [Gun definitions][guns] · [Reload matching][reload]

| Compatible gun | Projectiles per fired round |
| --- | ---: |
| [AA12 Shotgun](AA12Shotgun.md) | 10 |
| [DB-2 Durin](DB2Durin.md) | 16 |
| [DB-4 Ursus](DB4Ursus.md) | 10 |
| [M1014 Battle Shotgun](M1014BattleShotgun.md) | 8 |
| [M870](M870.md) | 9 |
| [SPAS-12 Multi-purpose Shotgun](SPAS12MultipurposeShotgun.md) | 8 |

These are created projectiles, not guaranteed hits. One fired round still spends **one loaded shell**, and the damage-curve calculation divides the gun's shot value among its projectiles. Do not multiply a listed shot-damage value by this count to claim guaranteed total damage. [Firing and shell consumption][shot] · [Damage division][ballistics] · [Reading firearm stats](../mechanics/TaCZFirearms.md#reading-firearm-stats)

The profile also contains aimed-spread, armor-ignore, damage and projectile-speed changes, but the active ballistic path does not read those attachment fields. It provides no verified accuracy or range improvement here. [Bundled profile][profile] · [Ballistics][ballistics]

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
[index]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/index/attachments/ammo_mod_slug.json#L1-L8
[keys]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L13-L21
[loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L180
[magazine]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L321-L331
[overflow]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Inventory.java#L250-L290
[profile]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/attachments/ammo_mod_slug_data.json#L1-L19
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipes/attachments/ammo_mod_slug.json#L1-L20
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
