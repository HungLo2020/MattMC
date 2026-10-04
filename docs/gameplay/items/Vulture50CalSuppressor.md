# Vulture .50 Cal Suppressor

The Vulture .50 Cal Suppressor fits the **M107** and **M95**, suppresses the first-person muzzle-flash effect, and modifies both axes of camera recoil. The ".50 Cal" name does not make it a universal attachment for guns with .50 in their names. [Profile][profile] · [Flash gate][flash] · [Recoil loader][parser] · [Gun definitions][guns]

## Obtaining

Craft **one Vulture .50 Cal Suppressor** at the [TaCZ Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table), under **Muzzle**, using:

- **16 Iron Ingots**
- **6 Leather**
- **2 Redstone Dust**

[Recipe][recipe] · [Muzzle grouping][index] · [Table groups][groups]

Carry the materials in your inventory, choose the recipe, and activate the bottom-right craft control. The table makes the attachment item; install it separately through Refit. See [using a workbench](../blocks/TaCZWorkbenches.md#using-a-workbench) for the shared controls and Creative limits. [Recipe loading][loader] · [Material consumption][crafting]

It is also available from the Creative inventory, registered as `minecraft:muzzle_silencer_vulture`. [Item registration][registration] · [Creative entries][creative]

## Usage

Carry the silencer, hold a compatible gun in your **main hand**, press **Refit (Z by default)**, choose **Muzzle**, and select the carried attachment. Keep inventory space for the part you replace or unload. Installation consumes one carried attachment, including in Creative; it does not require standing at the workbench. See [refitting attachments](../mechanics/TaCZFirearms.md#refitting-attachments) for removal and inventory-space details. [Default key][keys] · [Refit choices][refit] · [Server installation][install]

It fits **only M107 and M95** in the bundled gun definitions. The Deagle 50 and Golden Deagle 357 use different accepted attachment IDs. The gun must accept both the **Muzzle type and this exact attachment ID**. [Compatibility check][fit] · [Gun definitions][guns]

## Properties

| Property | Value |
| --- | --- |
| Attachment type | Muzzle |
| Attachment level | Not level-based |
| Compatible guns | [M107 Sniper Rifle](M107SniperRifle.md), [M95 .50 Cal Antimaterial](M9550CalAntimaterial.md) |

[Attachment definition][definition] · [Exact gun acceptance][guns]

## Behavior

When installed, it blocks the muzzle-flash effect in the inspected **first-person gun-rendering path**. This is a local view effect, not a promise about what another player sees or about stealth. [First-person flash gate][flash]

It contributes **×0.66 pitch** (34% lower vertical amplitude by itself) and **×0.75 yaw** (25% lower horizontal amplitude by itself) to the gun's **camera-recoil curve**. These modifiers apply to the camera motion sampled for a shot, alongside the gun's own recoil and other applicable modifiers; they are not a reduction to the projectile's random spread or a damage bonus. [Profile][profile] · [Recoil calculation][camera] · [Modifier combination][camera-math] · [Camera application][camera-apply] · [Projectile creation][shot]

**The name does not mean quieter shots in this implementation.** The active client and server firing paths still select the gun's ordinary shoot sound and do not use this attachment's imported silence settings. This source review does not measure hearing distance or in-game audibility. [Client shot feedback][client-shot] · [Server shot][shot] · [Sound selection][sound]

The profile also lists aim-down-sights timing, accuracy, effective range, and headshot damage, but the current attachment loader does not apply those fields. Do not treat them as working bonuses or penalties when choosing this item. [Profile][profile] · [Attachment parser][parser] · [Ballistic calculations][ballistics] · [Aim timing][aim]

## Notes

Use the gun list rather than caliber shorthand when choosing this attachment. It occupies the same Muzzle slot as other muzzle equipment on the M107 or M95, so fitting it replaces that equipment. See [refitting attachments](../mechanics/TaCZFirearms.md#refitting-attachments) for replacement and unloading. [Stored slot][storage]

This item is part of the integrated [TaCZ firearms system](../mechanics/TaCZFirearms.md). Use [TaCZ Workbenches](../blocks/TaCZWorkbenches.md) for table recipes and general crafting help.

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Recipes, registration, exact gun acceptance, refit handling, and the active rendering, camera-recoil, and shooting paths were inspected. This is **source review, not an in-game test** of crafting, installation, recoil, sound, or multiplayer visibility.

[profile]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/attachments/muzzle_silencer_vulture_data.json#L1-L30
[flash]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L465-L514
[parser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L285-L333
[guns]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L10-L74
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipes/attachments/muzzle_silencer_vulture.json#L1-L27
[index]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/index/attachments/muzzle_silencer_vulture.json#L1-L6
[groups]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L151-L179
[loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L174
[crafting]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L42-L106
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2724-L2737
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1663-L1667
[keys]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L13-L22
[refit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczGunRefitScreen.java#L81-L111
[install]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2227-L2267
[fit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[definition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L156-L156
[camera]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L110-L147
[camera-math]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L515-L544
[camera-apply]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczCameraRecoil.java#L20-L66
[shot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L146-L181
[client-shot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L142-L160
[sound]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L371-L377
[ballistics]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L39-L76
[aim]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczGlock17AnimationController.java#L13-L60
[storage]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczRefitGun.java#L61-L80
