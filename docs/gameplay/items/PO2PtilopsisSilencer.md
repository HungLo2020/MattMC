# PO-2 "Ptilopsis" Silencer

The PO-2 "Ptilopsis" Silencer fits the listed pistols and SMGs and suppresses their first-person muzzle-flash effect. It adds no camera-recoil modifier; its imported range and accuracy entries are not working bonuses in this snapshot. [Profile][profile] · [Flash gate][flash] · [Recoil loader][parser] · [Gun definitions][guns]

## Obtaining

Craft **one PO-2 "Ptilopsis" Silencer** at the [TaCZ Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table), under **Muzzle**, using:

- **14 Iron Ingots**
- **2 Redstone Dust**

[Recipe][recipe] · [Muzzle grouping][index] · [Table groups][groups]

Carry the materials in your inventory, choose the recipe, and activate the bottom-right craft control. The table makes the attachment item; install it separately through Refit. See [using a workbench](../blocks/TaCZWorkbenches.md#using-a-workbench) for the shared controls and Creative limits. [Recipe loading][loader] · [Material consumption][crafting]

It is also available from the Creative inventory, registered as `minecraft:muzzle_silencer_ptilopsis`. [Item registration][registration] · [Creative entries][creative]

## Usage

Carry the silencer, hold a compatible gun in your **main hand**, press **Refit (Z by default)**, choose **Muzzle**, and select the carried attachment. Keep inventory space for the part you replace or unload. Installation consumes one carried attachment, including in Creative; it does not require standing at the workbench. See [refitting attachments](../mechanics/TaCZFirearms.md#refitting-attachments) for removal and inventory-space details. [Default key][keys] · [Refit choices][refit] · [Server installation][install]

It fits the **15 firearms** linked below, the same gun set as Mirage and Wraith. The Deagle 50 accepts it; the Golden Deagle 357 does not. The gun must accept both the **Muzzle type and this exact attachment ID**. [Compatibility check][fit] · [Gun definitions][guns]

## Properties

| Property | Value |
| --- | --- |
| Attachment type | Muzzle |
| Attachment level | Not level-based |
| Compatible guns | [B93R](B93R.md), [CZ 75](CZ75.md), [Deagle 50](Deagle50.md), [Glock 17](Glock17.md), [HK-MP5A5](HKMP5A5.md), [M1911](M1911.md), [M1A1 Thompson](M1A1Thompson.md), [M9A4](M9A4.md), [MK23 Offensive Pistol](MK23OffensivePistol.md), [MP40](MP40.md), [P320](P320.md), [P90 PDW](P90PDW.md), [UMP45 SMG](UMP45SMG.md), [UZI](UZI.md), [Vector SMG](VectorSMG.md) |

[Attachment definition][definition] · [Exact gun acceptance][guns]

## Behavior

When installed, it blocks the muzzle-flash effect in the inspected **first-person gun-rendering path**. This is a local view effect, not a promise about what another player sees or about stealth. [First-person flash gate][flash]

This attachment adds **no camera-recoil modifier**: its bundled profile has neither a recoil section nor the legacy recoil-modifier section used by the active loader. Do not craft it expecting a reduction in camera kick. [Profile][profile] · [Recoil loader][parser]

**The name does not mean quieter shots in this implementation.** The active client and server firing paths still select the gun's ordinary shoot sound and do not use this attachment's imported silence settings. This source review does not measure hearing distance or in-game audibility. [Client shot feedback][client-shot] · [Server shot][shot] · [Sound selection][sound]

The profile also lists aim-down-sights timing, accuracy, and effective range, but the current attachment loader does not apply those fields. Do not treat them as working bonuses or penalties when choosing this item. [Profile][profile] · [Attachment parser][parser] · [Ballistic calculations][ballistics] · [Aim timing][aim]

## Notes

[Mirage](MirageSilencer.md) fits the same guns and costs **10 Iron Ingots and 2 Redstone Dust**, compared with Ptilopsis' 14 ingots. The reviewed effects do not establish a range or accuracy advantage for spending the extra iron. [Mirage profile][other-profile] · [Mirage recipe][other-recipe]

This item is part of the integrated [TaCZ firearms system](../mechanics/TaCZFirearms.md). Use [TaCZ Workbenches](../blocks/TaCZWorkbenches.md) for table recipes and general crafting help.

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Recipes, registration, exact gun acceptance, refit handling, and the active rendering, camera-recoil, and shooting paths were inspected. This is **source review, not an in-game test** of crafting, installation, recoil, sound, or multiplayer visibility.

[profile]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/attachments/muzzle_silencer_ptilopsis_data.json#L1-L19
[flash]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L465-L514
[parser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L285-L333
[guns]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L10-L74
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipes/attachments/muzzle_silencer_ptilopsis.json#L1-L21
[index]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/index/attachments/muzzle_silencer_ptilopsis.json#L1-L6
[groups]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L151-L179
[loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L174
[crafting]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L42-L106
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2724-L2737
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1663-L1667
[keys]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L13-L22
[refit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczGunRefitScreen.java#L81-L111
[install]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2227-L2267
[fit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[definition]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L153-L153
[client-shot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L142-L160
[shot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L146-L181
[sound]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L371-L377
[ballistics]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L39-L76
[aim]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/tacz/TaczGlock17AnimationController.java#L13-L60
[other-profile]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/data/attachments/muzzle_silencer_mirage_data.json#L1-L18
[other-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipes/attachments/muzzle_silencer_mirage.json#L1-L21
