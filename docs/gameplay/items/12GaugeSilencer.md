# 12 Gauge Silencer

The 12 Gauge Silencer combines first-person muzzle-flash suppression with a modest vertical camera-recoil modifier on four compatible shotguns. The ammunition name alone does not establish whether a shotgun can accept it. [Profile][profile] · [Flash gate][flash] · [Recoil loader][parser] · [Gun definitions][guns]

## Obtaining

Craft **one 12 Gauge Silencer** at the [TaCZ Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table), under **Muzzle**, using:

- **18 Iron Ingots**
- **5 Leather**
- **2 Redstone Dust**

[Recipe][recipe] · [Muzzle grouping][index] · [Table groups][groups]

Carry the materials in your inventory, choose the recipe, and activate the bottom-right craft control. The table makes the attachment item; install it separately through Refit. See [using a workbench](../blocks/TaCZWorkbenches.md#using-a-workbench) for the shared controls and Creative limits. [Recipe loading][loader] · [Material consumption][crafting]

It is also available from the Creative inventory, registered as `minecraft:muzzle_silencer_sg`. [Item registration][registration] · [Creative entries][creative]

## Usage

Carry the silencer, hold a compatible gun in your **main hand**, press **Refit (Z by default)**, choose **Muzzle**, and select the carried attachment. Keep inventory space for the part you replace or unload. Installation consumes one carried attachment, including in Creative; it does not require standing at the workbench. See [refitting attachments](../mechanics/TaCZFirearms.md#refitting-attachments) for removal and inventory-space details. [Default key][keys] · [Refit choices][refit] · [Server installation][install]

It fits **AA12, M1014, M870, and SPAS-12**. Other guns using 12 Gauge ammunition, such as the double-barrel shotguns, are not accepted merely because they share that ammunition. The gun must accept both the **Muzzle type and this exact attachment ID**. [Compatibility check][fit] · [Gun definitions][guns]

## Properties

| Property | Value |
| --- | --- |
| Attachment type | Muzzle |
| Attachment level | Not level-based |
| Compatible guns | [AA12 Shotgun](AA12Shotgun.md), [M1014 Battle Shotgun](M1014BattleShotgun.md), [M870](M870.md), [SPAS-12 Multi-purpose Shotgun](SPAS12MultipurposeShotgun.md) |

[Attachment definition][definition] · [Exact gun acceptance][guns]

## Behavior

When installed, it blocks the muzzle-flash effect in the inspected **first-person gun-rendering path**. This is a local view effect, not a promise about what another player sees or about stealth. [First-person flash gate][flash]

It contributes **×0.90 pitch** (10% lower vertical amplitude by itself) to the gun's **camera-recoil curve**. It adds no yaw modifier. These modifiers apply to the camera motion sampled for a shot, alongside the gun's own recoil and other applicable modifiers; they are not a reduction to the projectile's random spread or a damage bonus. [Profile][profile] · [Recoil calculation][camera] · [Modifier combination][camera-math] · [Camera application][camera-apply] · [Projectile creation][shot]

**The name does not mean quieter shots in this implementation.** The active client and server firing paths still select the gun's ordinary shoot sound and do not use this attachment's imported silence settings. This source review does not measure hearing distance or in-game audibility. [Client shot feedback][client-shot] · [Server shot][shot] · [Sound selection][sound]

The profile also lists aim-down-sights timing, pellet spread, effective range, and firing rate, but the current attachment loader does not apply those fields. Do not treat them as working bonuses or penalties when choosing this item. [Profile][profile] · [Attachment parser][parser] · [Ballistic calculations][ballistics] · [Aim timing][aim] · [Firing interval][cadence]

## Notes

Use this when you want the verified flash and camera-kick effects on one of the four listed shotguns. It replaces another Muzzle attachment, so a choke or brake cannot occupy that same slot at the same time. See [refitting attachments](../mechanics/TaCZFirearms.md#refitting-attachments) before switching equipment. [Stored slot][storage]

This item is part of the integrated [TaCZ firearms system](../mechanics/TaCZFirearms.md). Use [TaCZ Workbenches](../blocks/TaCZWorkbenches.md) for table recipes and general crafting help.

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Recipes, registration, exact gun acceptance, refit handling, and the active rendering, camera-recoil, and shooting paths were inspected. This is **source review, not an in-game test** of crafting, installation, recoil, sound, or multiplayer visibility.

[profile]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/attachments/muzzle_silencer_sg_data.json#L1-L30
[flash]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L465-L514
[parser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L285-L333
[guns]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L10-L74
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipes/attachments/muzzle_silencer_sg.json#L1-L27
[index]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/index/attachments/muzzle_silencer_sg.json#L1-L6
[groups]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L151-L179
[loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L174
[crafting]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L42-L106
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2724-L2737
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1663-L1667
[keys]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L13-L22
[refit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczGunRefitScreen.java#L81-L111
[install]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2227-L2267
[fit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[definition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L154-L154
[camera]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L110-L147
[camera-math]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L515-L544
[camera-apply]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczCameraRecoil.java#L20-L66
[shot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L146-L181
[client-shot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L142-L160
[sound]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L371-L377
[ballistics]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L39-L76
[aim]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczGlock17AnimationController.java#L13-L60
[cadence]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L104-L119
[storage]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczRefitGun.java#L61-L80
