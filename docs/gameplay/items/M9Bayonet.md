# M9 Bayonet

M9 Bayonet fits the AUG, M16A1, M16A4, and M4A1 as a Muzzle attachment. Its installed model is supported, but the reviewed gun controls do not implement a bayonet attack, and its profile adds no camera-recoil modifier.

## Obtaining

Craft **one M9 Bayonet** at the [TaCZ Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table), in **Muzzle**. [Recipe][recipe] · [Group][index] · [Output loading][loader] · [Table groups][groups]

| Material | Amount |
| --- | ---: |
| [Iron Ingots](IronIngot.md) | 10 |
| Items in `#minecraft:logs` | 4 |

The wood ingredient accepts the **44 items** in the bundled `minecraft:logs` tag, including its nested tags. You can mix matching items across stacks to reach four. [Tag membership][logs] · [Nested overworld tags][logs_that_burn] · [Ingredient matching][tag-match] · [Inventory counting and consumption][craft]

- **36 overworld items:** Logs and Wood, each in ordinary and stripped forms, from [Oak][oak_logs], [Spruce][spruce_logs], [Birch][birch_logs], [Jungle][jungle_logs], [Acacia][acacia_logs], [Dark Oak][dark_oak_logs], [Mangrove][mangrove_logs], [Cherry][cherry_logs], and [Pale Oak][pale_oak_logs]
- **8 Nether items:** [Crimson][crimson_stems] and [Warped][warped_stems] Stems and Hyphae, each in ordinary and stripped forms

Planks and Bamboo blocks are outside this inspected tag. An item looking like wood, or the table showing one representative log icon, does not change which ingredients qualify. [Representative icon][tag-icon]

The [workbench guide](../blocks/TaCZWorkbenches.md#using-a-workbench) covers crafting controls and inventory handling. The item is registered as `minecraft:bayonet_m9`, stacks to **one**, and also appears in the Creative Menu. Catalog visibility in the [inventory browser](../mechanics/InventoryBrowser.md#mode-and-permission-limits) does not grant ordinary Survival insertion; use the recipe for Survival crafting. [Registration][registration] · [Creative entries][creative]

## Usage

Carry the bayonet, hold a compatible gun in your **main hand**, and press **Z** by default to open **Refit**. Select **Muzzle** and choose the carried bayonet. Installation consumes one attachment; the table only crafts it. Use [Refitting attachments](../mechanics/TaCZFirearms.md#refitting-attachments) for replacement, Unload, and inventory-space limits. [Default binding][keys] · [Opening Refit][input] · [Selection][refit-screen] · [Installation and removal][refit-server]

There is **one Muzzle slot**. Fitting this bayonet replaces any brake or silencer in that slot; it cannot combine with another muzzle attachment. Modifiers from other attachment slots can still apply. [Slot storage][slot-storage] · [Modifier collection][collection]

If reducing camera kick is your goal, compare the [Cthulhu K7 Brake](CthulhuK7Brake.md), which accepts the same guns and has active recoil modifiers. See [6H3 Bayonet](6H3Bayonet.md) for the other bayonet's recipe and compatibility.

## Properties

| Property | Value |
| --- | --- |
| Attachment type | Muzzle |
| Attachment level | Not level-based |
| Compatible guns | [AUG](AUG.md), [M16A1 Service Rifle](M16A1ServiceRifle.md), [M16A4 Service Rifle](M16A4ServiceRifle.md), [M4A1 Carbine](M4A1Carbine.md) |

The accepted set is exactly these **four guns**: `aug`, `m16a1`, `m16a4`, and `m4a1`. The AKM uses the separate 6H3 bayonet. A Muzzle category alone does not establish a fit. [Gun allowlists][gun-definitions] · [Type and exact-ID check][fit] · [Attachment definition][definition]

## Behavior

**No installed bayonet attack is implemented through the inspected controls.** Melee is listed on **V**, but the active input handler never consumes that binding. The gun packet and server handler only implement Shoot, Reload, and Fire Mode. While the gun is held, the handler also consumes ordinary Attack input and returns before the normal attack route. The [firearms controls guide](../mechanics/TaCZFirearms.md#controls) documents this limitation. [Binding][keys] · [Active input][input] · [Packet actions][packet] · [Server actions][server-input] · [Normal-attack bypass][normal-attack]

The imported melee block lists damage `6`, distance `2`, range angle `45`, knockback `0.4`, prep `0.1`, and cooldown `0`. These are unused configuration values here, not working damage, reach, timing, or knockback guarantees. This conclusion concerns the installed bayonet path, not every possible attack with a loose item or a later implementation. [Imported profile][profile] · [Current input and server path][server-input]

The profile has no active `recoil` or `recoil_modifier` block. It contributes **no recoil adjustment**; it does not give the gun zero recoil. The gun's own recoil and modifiers from other installed slots still determine camera movement. Its imported weight `0.34` and ADS addend `0.03` are not applied as gun-weight or aim-time changes by the checked consumers. [Profile][profile] · [Profile loading][profile-loader] · [Collection][collection] · [Empty-modifier evaluation][evaluation] · [Aim transition][aim]

The installed model is submitted when its display and geometry load and the gun's `muzzle_pos` mount path resolves. The [AUG][aug-model], [M16A1][m16a1-model], [M16A4][m16a4-model], and [M4A1][m4a1-model] models contain that mount and a `muzzle_default` node. The bayonet display sets `show_muzzle: true`, so the reviewed visibility rule retains that default muzzle geometry alongside the bayonet. This retained mesh is part of the gun, not a second occupied attachment slot. [Bayonet display][display] · [Bayonet geometry][attachment-geometry] · [Display reader][display-reader] · [Attachment submission][attachment-render] · [Default-muzzle rule][default-muzzle]

Its ID does not contain `silencer`, so it does not trigger the first-person flash suppression check. Flash submission still depends on the gun's `muzzle_flash` node, available flash data, and the recent-shot timing check. [Flash conditions][flash]

## Notes

Source-reviewed on **2026-10-04** at `9bafc14d2e2943dcfe8a37e9e81dc001386b88bb`. The recipe and recursive log tag, exact gun fit, refit storage, imported profile, input/packet/server path, and model conditions were checked. This was **source review**, not an in-game crafting, melee, recoil, or visual test; model alignment and actual appearance were not verified. The inspected first-person caller connects held guns to this renderer. [Held-gun renderer][first-person]

[recipe]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/recipes/attachments/bayonet_m9.json
[index]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/index/attachments/bayonet_m9.json
[loader]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L174
[groups]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L151-L184
[logs]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/tags/item/logs.json
[logs_that_burn]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/tags/item/logs_that_burn.json
[tag-match]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L207-L225
[craft]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L42-L106
[oak_logs]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/tags/item/oak_logs.json
[spruce_logs]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/tags/item/spruce_logs.json
[birch_logs]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/tags/item/birch_logs.json
[jungle_logs]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/tags/item/jungle_logs.json
[acacia_logs]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/tags/item/acacia_logs.json
[dark_oak_logs]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/tags/item/dark_oak_logs.json
[mangrove_logs]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/tags/item/mangrove_logs.json
[cherry_logs]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/tags/item/cherry_logs.json
[pale_oak_logs]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/tags/item/pale_oak_logs.json
[crimson_stems]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/tags/item/crimson_stems.json
[warped_stems]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/tags/item/warped_stems.json
[tag-icon]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L227-L247
[registration]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/Items.java#L2724-L2740
[creative]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1663-L1671
[keys]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L13-L41
[input]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L36-L104
[refit-screen]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczGunRefitScreen.java#L78-L112
[refit-server]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2227-L2267
[slot-storage]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczRefitGun.java#L61-L80
[collection]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L110-L147
[gun-definitions]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L13-L73
[fit]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[definition]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L117-L117
[packet]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/network/protocol/common/custom/TaczGunInputC2SPayload.java#L34-L43
[server-input]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2205-L2225
[normal-attack]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/Minecraft.java#L2096-L2118
[profile]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/data/minecraft/data/attachments/bayonet_m9_data.json
[profile-loader]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L285-L329
[evaluation]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L519-L537
[aim]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczGlock17AnimationController.java#L13-L61
[aug-model]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/geo_models/gun/aug_geo.json
[m16a1-model]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/geo_models/gun/m16a1_geo.json
[m16a4-model]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/geo_models/gun/m16a4_geo.json
[m4a1-model]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/geo_models/gun/m4a1_geo.json
[display]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/display/attachments/bayonet_m9_display.json
[attachment-geometry]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/resources/assets/minecraft/geo_models/attachment/bayonet_m9_geo.json
[display-reader]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/tacz/TaczScopeData.java#L105-L127
[attachment-render]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L524-L616
[default-muzzle]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L1105-L1173
[flash]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java#L465-L514
[first-person]: https://github.com/HungLo2020/MattMC/blob/9bafc14d2e2943dcfe8a37e9e81dc001386b88bb/src/main/java/net/minecraft/client/renderer/ItemInHandRenderer.java#L668-L696
