# Phantom S1 Silencer

The Phantom S1 Silencer is a muzzle-flash option for the listed firearms, including the **Golden Deagle 357**. It adds no camera-recoil modifier, so compatibility is the main reason to check this model separately from other silencers. [Profile][profile] · [Flash gate][flash] · [Recoil loader][parser] · [Gun definitions][guns]

## Obtaining

Craft **one Phantom S1 Silencer** at the [TaCZ Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table), under **Muzzle**, using:

- **16 Iron Ingots**
- **6 Leather**
- **2 Redstone Dust**

[Recipe][recipe] · [Muzzle grouping][index] · [Table groups][groups]

Carry the materials in your inventory, choose the recipe, and activate the bottom-right craft control. The table makes the attachment item; install it separately through Refit. See [using a workbench](../blocks/TaCZWorkbenches.md#using-a-workbench) for the shared controls and Creative limits. [Recipe loading][loader] · [Material consumption][crafting]

It is also available from the Creative inventory, registered as `minecraft:muzzle_silencer_phantom_s1`. [Item registration][registration] · [Creative entries][creative]

## Usage

Carry the silencer, hold a compatible gun in your **main hand**, press **Refit (Z by default)**, choose **Muzzle**, and select the carried attachment. Keep inventory space for the part you replace or unload. Installation consumes one carried attachment, including in Creative; it does not require standing at the workbench. See [refitting attachments](../mechanics/TaCZFirearms.md#refitting-attachments) for removal and inventory-space details. [Default key][keys] · [Refit choices][refit] · [Server installation][install]

It fits the **34 firearms** linked below. The Golden Deagle 357 accepts Phantom S1 even though it does not accept Knight QD or Ursus; the ordinary Deagle 50 instead accepts the pistol silencer group. The gun must accept both the **Muzzle type and this exact attachment ID**. [Compatibility check][fit] · [Gun definitions][guns]

## Properties

| Property | Value |
| --- | --- |
| Attachment type | Muzzle |
| Attachment level | Not level-based |
| Compatible guns | [.30-06 Lonetrail Hand Cannon](3006LonetrailHandCannon.md), [Accuracy International AWM](AccuracyInternationalAWM.md), [AKM](AKM.md), [AUG](AUG.md), [FN EVOLYS Machine Gun](FNEVOLYSMachineGun.md), [FN FAL Battle Rifle](FNFALBattleRifle.md), [G36K](G36K.md), [Golden Deagle 357](GoldenDeagle357.md), [HK G3 Battle rifle](HKG3Battlerifle.md), [HK-416A5](HK416A5.md), [HK-MP5A5](HKMP5A5.md), [M1 Carbine](M1Carbine.md), [M1 Garand](M1Garand.md), [M16A1 Service Rifle](M16A1ServiceRifle.md), [M16A4 Service Rifle](M16A4ServiceRifle.md), [M1A1 Thompson](M1A1Thompson.md), [M249 Machine Gun](M249MachineGun.md), [M4A1 Carbine](M4A1Carbine.md), [M700 Sniper Rifle](M700SniperRifle.md), [MK14 EBR](MK14EBR.md), [MP40](MP40.md), [P90 PDW](P90PDW.md), [QBZ-191 Assault Rifle](QBZ191AssaultRifle.md), [QBZ-95 "Longbow"](QBZ95Longbow.md), [RPK](RPK.md), [SCAR-H Battle Rifle](SCARHBattleRifle.md), [SCAR-L Assault Rifle](SCARLAssaultRifle.md), [Sks Tactical Rifle](SksTacticalRifle.md), [SPR-15 HB "Sagittarius"](SPR15HBSagittarius.md), [Sturmgewehr 44](Sturmgewehr44.md), [Type 81-1 Service Rifle](Type811ServiceRifle.md), [UMP45 SMG](UMP45SMG.md), [UZI](UZI.md), [Vector SMG](VectorSMG.md) |

[Attachment definition][definition] · [Exact gun acceptance][guns]

## Behavior

When installed, it blocks the muzzle-flash effect in the inspected **first-person gun-rendering path**. This is a local view effect, not a promise about what another player sees or about stealth. [First-person flash gate][flash]

This attachment adds **no camera-recoil modifier**: its bundled profile has neither a recoil section nor the legacy recoil-modifier section used by the active loader. Do not craft it expecting a reduction in camera kick. [Profile][profile] · [Recoil loader][parser]

**The name does not mean quieter shots in this implementation.** The active client and server firing paths still select the gun's ordinary shoot sound and do not use this attachment's imported silence settings. This source review does not measure hearing distance or in-game audibility. [Client shot feedback][client-shot] · [Server shot][shot] · [Sound selection][sound]

The profile also lists aim-down-sights timing, accuracy, effective range, and firing rate, but the current attachment loader does not apply those fields. Do not treat them as working bonuses or penalties when choosing this item. [Profile][profile] · [Attachment parser][parser] · [Ballistic calculations][ballistics] · [Aim timing][aim] · [Firing interval][cadence]

## Notes

For a gun that accepts both, [Knight QD](KnightQDSilencer.md) costs the same materials and also adds a pitch camera-recoil modifier. Phantom S1 remains a separate fit choice, particularly for the Golden Deagle 357. [Knight QD profile][other-profile] · [Knight QD recipe][other-recipe] · [Gun definitions][guns]

This item is part of the integrated [TaCZ firearms system](../mechanics/TaCZFirearms.md). Use [TaCZ Workbenches](../blocks/TaCZWorkbenches.md) for table recipes and general crafting help.

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Recipes, registration, exact gun acceptance, refit handling, and the active rendering, camera-recoil, and shooting paths were inspected. This is **source review, not an in-game test** of crafting, installation, recoil, sound, or multiplayer visibility.

[profile]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/attachments/muzzle_silencer_phantom_s1_data.json#L1-L22
[flash]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L465-L514
[parser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L285-L333
[guns]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L10-L74
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipes/attachments/muzzle_silencer_phantom_s1.json#L1-L27
[index]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/index/attachments/muzzle_silencer_phantom_s1.json#L1-L6
[groups]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L151-L179
[loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L174
[crafting]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L42-L106
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2724-L2737
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1663-L1667
[keys]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L13-L22
[refit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczGunRefitScreen.java#L81-L111
[install]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2227-L2267
[fit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[definition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L152-L152
[client-shot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L142-L160
[shot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L146-L181
[sound]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L371-L377
[ballistics]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L39-L76
[aim]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczGlock17AnimationController.java#L13-L60
[cadence]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L104-L119
[other-profile]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/attachments/muzzle_silencer_knight_qd_data.json#L1-L21
[other-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipes/attachments/muzzle_silencer_knight_qd.json#L1-L27
