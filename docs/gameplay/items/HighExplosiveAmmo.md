# High Explosive Ammo

High Explosive Ammo is an installed **Ammo Modifier** attachment for TaCZ firearms. Fitting it does not add an impact explosion or convert shotgun pellets to a single slug in this snapshot. See [Behavior](#behavior) before spending the recipe materials.

## Obtaining

Craft **one High Explosive Ammo** at the [TaCZ Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table), under **Extended Mag**. Carry these materials for one craft. The menu group comes from the bundled index; it is separate from the attachment's refit slot. [Recipe][recipe] · [Group][index] · [Active loader][loader] · [Table groups][groups]

| Material | Amount |
| --- | ---: |
| [Crying Obsidian](CryingObsidian.md) | 3 |
| [End Crystal](EndCrystal.md) | 1 |

The [workbench guide](../blocks/TaCZWorkbenches.md#using-a-workbench) explains crafting and output handling. Survival crafting consumes the listed materials; Creative still requires them to be present but does not consume them. [Craft transaction][craft]

It is registered as `minecraft:ammo_mod_he`, stacks to **1**, and also has a Creative Menu entry. The [inventory item browser](../mechanics/InventoryBrowser.md#mode-and-permission-limits) displays entries in Survival but does not grant ordinary Survival item insertion. [Registration][registration] · [Creative entries][creative]

## Usage

Carry the attachment, hold a compatible gun in your **main hand**, and open **Refit** with **Z** by default. Select **Ammo Modifier**, then choose the carried item. Installation requires both the correct attachment category and this exact item in that gun's accepted list. The 14 compatible guns are listed below. [Default key][keys] · [Refit opening][refit-input] · [Refit choices][refit-screen] · [Fit check][fit] · [Gun definitions][guns]

Only **one Ammo Modifier** occupies that slot. Installing transfers one carried attachment to the gun, including in Creative; firing does not spend the installed attachment. Continue reloading with the gun's ordinary matching ammunition: this item is **not a consumable cartridge** and does not fill the magazine. [Installation][refit-server] · [Slot storage][storage] · [Firing consumption][shot] · [Reload matching][reload]

Use **Unload** to remove it. Follow [Refitting attachments](../mechanics/TaCZFirearms.md#refitting-attachments) for replacement, removal and full-inventory handling, and leave inventory room even in Creative. In Survival, replacement returns or drops the old item; unloading with no room restores it to the gun. Creative inventory overflow can discard the returned item. [Refit transaction][refit-server] · [Inventory insertion][overflow]

## Properties

| Property | Value |
| --- | --- |
| Attachment type | Ammo Modifier |
| Attachment level | 1 |
| Compatible guns | [Accuracy International AWM](AccuracyInternationalAWM.md), [Deagle 50](Deagle50.md), [Golden Deagle 357](GoldenDeagle357.md), [M1014 Battle Shotgun](M1014BattleShotgun.md), [M107 Sniper Rifle](M107SniperRifle.md), [M870](M870.md), [M95 .50 Cal Antimaterial](M9550CalAntimaterial.md), [MK14 EBR](MK14EBR.md), [MK23 Offensive Pistol](MK23OffensivePistol.md), [RPK](RPK.md), [SCAR-H Battle Rifle](SCARHBattleRifle.md), [SPAS-12 Multi-purpose Shotgun](SPAS12MultipurposeShotgun.md), [Taurus "Raging Hunter" Hand Cannon](TaurusRagingHunterHandCannon.md), [Timeless .50 Z-Type](Timeless50ZType.md) |

[Registered type and level][attachment] · [Type-and-item fit check][fit] · [Accepted gun definitions][guns]

## Behavior

**The explosive effect is not implemented by the checked projectile path.** The profile contains an explosion flag, but firing creates the normal TaCZ bullet without reading that flag. Its entity-hit path applies bullet damage and knockback; its block-hit path invokes the struck block's ordinary projectile callback and discards the bullet. Neither adds this modifier's explosion. This is a limitation of the current implementation, not an explosion option waiting to be enabled by a game rule. [Bundled profile][profile] · [Shot creation][shot] · [Bullet hit paths][hit-paths] · [Block callback][block-callback]

The bundled description also mentions converting shotgun shots to slugs. That conversion is not active: [M1014](M1014BattleShotgun.md) and [SPAS-12](SPAS12MultipurposeShotgun.md) still create **8 projectiles per fired round**, and [M870](M870.md) creates **9**. The profile's armor-ignore, headshot, pierce, firing-rate and spread changes are not applied by the checked firing/ballistic paths either. These projectile counts do not promise a number of hits or a total damage result. [Bundled description][descriptions] · [Gun definitions][guns] · [Shot creation][shot] · [Ballistics][ballistics] · [Firing rate][rate]

## Notes

- This item is part of the integrated [TaCZ firearms](../mechanics/TaCZFirearms.md) system.
- **Extended Mag** is its crafting-menu group, while **Ammo Modifier** is its installation slot. It does not increase magazine capacity: that lookup reads the separate Extended Mag slot. Imported `extended_mag_level` fields do not override the registered attachment level above. [Index][index] · [Registration data][attachment] · [Capacity lookup][magazine]
- Its bundled profile contains no camera-recoil modifiers. The shared recoil collector visits this slot but adds no modifier from this profile; aiming zoom reads the Scope slot. These paths do not supply a recoil or zoom bonus for this item. [Profile][profile] · [Recoil collector][recoil-collector] · [Recoil parsing][recoil-loader] · [Zoom selection][zoom]

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked registration, recipe/group loading, Java type-and-item compatibility, refit transactions, reload matching, and active firing, ballistics, projectile-hit, recoil and zoom consumers. This is **source review**, not an in-game crafting, refitting, reloading, accuracy, damage, explosion, ignition or multiplayer test. Imported descriptions and profile fields are not treated as proof of working effects.

[attachment]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L111-L115
[ballistics]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L40-L80
[block-callback]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L288-L291
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L42-L68
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1663-L1671
[descriptions]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/assets/minecraft/lang/en_us.json#L8743-L8752
[fit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[groups]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L151-L185
[guns]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L10-L73
[hit-paths]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L162-L231
[index]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/index/attachments/ammo_mod_he.json#L1-L8
[keys]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L13-L21
[loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L180
[magazine]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L321-L331
[overflow]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Inventory.java#L250-L290
[profile]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/attachments/ammo_mod_he_data.json#L1-L27
[rate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L101-L118
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipes/attachments/ammo_mod_he.json#L1-L20
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
