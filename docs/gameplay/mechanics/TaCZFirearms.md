# TaCZ firearms

Hold a TaCZ firearm in your **main hand**, carry its matching ammunition, and use the gun-specific controls below. For making guns, ammunition, attachments, and the tables themselves, start with [TaCZ Workbenches](../blocks/TaCZWorkbenches.md). This guide describes MattMC's integrated implementation. [Held-gun controls][input]

## Getting started

A [Glock 17](../items/Glock17.md) is a straightforward first example: its [Gun Smith Table recipe](../blocks/TaCZWorkbenches.md#gun-smith-table) costs 16 Iron Ingots, and its [9mm ammunition](../items/9mmBullet.md) has an Ammo Assembly Table recipe. Put the gun away when opening a table; the normal gun controls take over attack and use while a firearm is held. [Input priority][input-priority] · [Gun controls][input]

Guns, ammunition, and attachments also have Creative entries. The [inventory item browser](InventoryBrowser.md#mode-and-permission-limits) displays its catalog in Survival, but ordinary Survival cannot insert those entries into an inventory. Crafting and catalog visibility are different acquisition routes. [Creative entries][creative]

## Controls

These are the **default** bindings. Change the TaCZ entries under **Options → Controls → Key Binds**, in **Timeless and Classics Guns**. They are separate from vanilla Attack/Destroy and Use Item/Place Block bindings. The controls below operate while a gun is in the main hand, no screen is open, and the player is not a spectator. [Defaults and registration][keys] · [Key Bind list][key-list] · [Key Bind editing][key-editing] · [English labels][key-labels] · [Held-gun controls][input]

| Action | Default | What it does |
| --- | --- | --- |
| Shoot | Left mouse button | Pulls the trigger; hold for AUTO fire |
| Aim | Hold right mouse button | Aims down sights; release to stop aiming |
| Reload | R | Starts a reload when the magazine is not full and ammunition is available |
| Fire Mode | G | Cycles the modes supported by that gun |
| Inspect | H | Plays an inspection animation |
| Refit | Z | Opens the attachment screen; Z again closes it with the default binding |

Remapping takes effect in the current session, but this snapshot registers TaCZ keys **after** saved options are loaded. Saved TaCZ binding changes are not restored by that startup path, so recheck them after restarting. [Startup order][options-startup] · [Options load][options-load] · [Saved-key loading][options-keys] · [Late registration][client-registration] · [Registration behavior][keys]

Aiming is held, not toggled. The aimed accuracy setting applies after the aim transition completes. Reload, fire-mode changes, and inspection are blocked by the normal client controls while an item is already being used. [Aim transition][aim] · [Input checks][input]

**Other listed TaCZ bindings are not working features in this snapshot.** Interact While Holding Gun (O), Crawl (C), Zoom (V), Melee (also V), and Open TAC Config (Alt+T) are registered, but the active handler does not implement their actions. In particular, a “Prone” accuracy row does not mean the C binding puts the player prone. [Registered keys][keys] · [Active handler][input] · [Accuracy by pose][pose]

## Firing and fire modes

With the **default mouse Shoot binding**:

- **SEMI:** each click requests one trigger pull. Holding Shoot does not continuously repeat it.
- **AUTO:** holding Shoot continues requesting shots, subject to the gun's firing cooldown.
- **BURST:** a trigger pull schedules that gun's configured burst, limited by loaded ammunition. Only burst definitions marked for continuous shooting repeat while Shoot is held. Scheduled rounds stop if the player dies, stops holding that same gun stack, or runs out of loaded ammunition.

Configured burst RPM does **not guarantee spacing between the rounds** in this source snapshot. The gun queues tasks with future tick stamps, but the server can admit them while it has processing time, so several rounds can run together. The trigger cooldown is a separate limit; see the [SCAR-L example](../items/SCARLAssaultRifle.md#usage). This timing caveat was source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2`, without an in-game rate test. [Burst task creation][burst-task-creation] · [Server admission][burst-task-admission] · [Queue processing][burst-task-queue]

This shared burst scheduling limitation is tracked in [issue #810](https://github.com/HungLo2020/MattMC/issues/810).

If Shoot is remapped to a keyboard key, keyboard repeat can generate further trigger pulls even in SEMI or a non-continuous BURST mode. The firing cooldown still applies. [Keyboard repeat][keyboard-repeat] · [Click consumption][input]

The HUD shows the selected mode. A gun with only one supported mode stays on that mode when Fire Mode is pressed. The client sends shooting, reload, and fire-selection requests through the active server packet path; the server performs the firing and magazine changes. [Client controls][input] · [Modes and scheduled shots][shots] · [Mode cycling][cycle-mode] · [Burst definitions][burst-data] · [HUD][hud] · [Packet transport][transport] · [Packet codec][codec] · [Play registration][protocol] · [Server actions][server]

Normally, each fired round removes **one loaded round**, even in Creative. A shotgun's multiple projectiles use one magazine round for that firing event. An empty gun cannot fire; Creative does not remove the need to reload its magazine. [Projectile creation and ammunition use][fire-round]

## Magazine, reserve, and reloading

The HUD's large ammunition number is the gun's **loaded magazine**. Its smaller reserve number counts matching ammunition carried in the player inventory. Reserve display is capped at **9,999**; Creative displays 9,999 regardless of carried ammunition. The item tooltip also shows loaded rounds and magazine capacity. [HUD][hud] · [Tooltip][tooltip] · [Reserve count][reserve]

Press Reload and keep holding the gun until the reload completes. Ammunition is added at completion, rather than as soon as the key is pressed. Reload time depends on the gun; some weapons also use the missing-round count, fire mode, or installed attachments to choose their timing. Reloading does not discard rounds already loaded. [Reload start and completion][reload-start] · [Use completion][use-completion] · [Timing][reload-timing] · [Adding rounds][reload-ammo]

In **Survival**, a completed reload takes rounds from the **first matching inventory stack only**, up to the magazine's missing capacity. A small first stack can therefore cause a partial reload even when the HUD shows enough total reserve to fill the gun. Reload again to use the next matching stack, or combine ammunition stacks before reloading. For example, an empty 17-round Glock with matching stacks of 5 and 20 rounds loads 5 on the first completed reload, although its reserve initially totals 25. [Stack selection and consumption][reload-ammo]

In **Creative**, a completed reload supplies all missing magazine rounds without needing or consuming carried ammunition. This exception applies to reloading, while firing still spends loaded rounds. A full magazine or a gun with zero base magazine capacity cannot start a reload. [Reload checks][reload-start] · [Creative supply][reload-ammo] · [Firing consumption][fire-round]

## Refitting attachments

The [Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table) makes attachment items. Install them from the **Refit** screen while holding the gun; installation itself does not require standing at a workbench.

1. Carry the attachment and leave room in your inventory for any attachment you remove.
2. Press **Z**, then select the attachment category. Unsupported categories are disabled.
3. Choose an attachment from the compatible carried items shown below the category. Compatibility requires the correct category **and** an attachment accepted by this particular gun; sharing a category is not enough.
4. To remove the installed attachment, select its category and use **Unload**. Press Z again or close the screen when finished.

The screen shows at most eight matching inventory entries for the selected category. Installing consumes one carried attachment, including in Creative. In Survival, replacing an attachment returns the old one to the inventory or drops it if it cannot fit; unloading with no room restores it to the gun. Keep spare inventory space in Creative too: its inventory insertion can discard a remainder. [Refit screen][refit-screen] · [Closing][refit-close] · [Gun compatibility][compatibility] · [Server installation and removal][refit-server] · [Inventory insertion][inventory-add]

An accepted extended magazine changes the gun's capacity to the size defined for that magazine level. It does **not** fill the new space automatically; reload to add rounds. [Magazine capacity][magazine] · [Attachment storage][refit-storage]

**Reducing magazine capacity can lose ammunition.** Before removing an extended magazine or replacing it with a smaller-capacity one, use up rounds above the new capacity. Refitting leaves the stored loaded count unchanged; the next shot reduces it by one and then caps it at the new capacity, discarding any excess without refunding ammunition. [Attachment changes][refit-storage] · [Loaded count and capacity limit][magazine] · [Next shot][fire-round]

Attachment compatibility and installation do not, by themselves, establish that every stat or effect advertised by an imported attachment file works here.

## Reading firearm stats

**Bullet speed** values shown in m/s are nominal conversions at **20 game ticks per second**, treating one block as one metre: the Glock 17's 7.5-block-per-tick launch setting is listed as 150 m/s. Fire modes can adjust the launch setting. Spread and inherited shooter movement can change the actual initial speed; drag and gravity change it during flight. Changed or delayed ticking also changes the wall-clock speed. [Glock setting][speed-setting] · [Mode adjustments][speed-modes] · [Launch motion][speed-launch] · [Flight][speed-flight] · [Tick rate][speed-ticks]

The item pages' **Damage** row is the gun definition's fallback shot value. It is not a promise of that much health lost on every hit. The active projectile path uses the bundled distance-based damage curve when available, applies fire-mode adjustments, and divides the value among the shot's projectiles. Headshots and the target's damage handling then affect the hit. Do not multiply a listed damage value by the pellet count to claim guaranteed total damage. [Shot creation][fire-round] · [Damage curve conversion][damage-curve] · [Hit handling][hit]

Falloff entries are distance thresholds: a point applies while the hit is **closer than** its listed distance; equality moves to the next point. This is a step change, not interpolation. For example, the [Glock 17](../items/Glock17.md#damage-falloff) has fallback Damage 6, but its bundled curve supplies 7 below 18 blocks, 5 from 18 to below 45, and 4 at 45 or more, before headshot and target handling. [Glock data][glock-data] · [Distance selection][distance]

## Current limitations

- **M134 Minigun cannot currently fire or reload.** Its definition declares .308 ammunition but a base magazine capacity of zero, no extended magazine sizes, and no accepted attachments. Creative, its AUTO/BURST modes, and refitting do not bypass the capacity checks. Its item page retains the declared stats for reference. See [M134 Minigun](../items/M134Minigun.md). [Definition][minigun] · [Firing and reload gates][reload-start] · [Fire gate][fire-gate] · [Magazine capacity][magazine]
- **Some ammunition has no compatible registered gun.** Check the ammunition page's **Used by** row before crafting; an available recipe does not establish a use. [Registered gun and ammunition definitions][definitions]
- **Crafted gun recipes do not install their listed attachment presets.** The current workbench output parser creates the registered item and count without applying recipe attachment fields. Obtain and install compatible attachments separately. [Output parser][recipe-output]

## Related pages

- [TaCZ Workbenches](../blocks/TaCZWorkbenches.md)
- [Glock 17](../items/Glock17.md), [9mm Bullet](../items/9mmBullet.md), and [M134 Minigun](../items/M134Minigun.md)
- [Items](../items/Items.md), [Combat](Combat.md), and [Mechanics](Mechanics.md)

## Sources and verification

Source-reviewed on **2026-10-03** at `cfa7057b6fe2b8dfa84e93f21932be2602eff749`. This review traced key registration, current input and refit callbacks, packet encoding and admission, live server handlers, reload completion, inventory consumption, projectile damage, and the dedicated workbench recipe loader. It is **source review**, not an in-game controls, crafting, combat, or multiplayer test. No upstream-mod behavior is assumed beyond these integrated paths.

Speed units were source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`, tracing the launch setting through projectile motion and game ticks. No in-game flight-speed test was run.

[input]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L35-L124
[input-priority]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/client/Minecraft.java#L2096-L2100
[creative]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1666-L1671
[keys]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L11-L60
[key-list]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/client/gui/screens/options/controls/KeyBindsList.java#L29-L52
[key-editing]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/client/gui/screens/options/controls/KeyBindsScreen.java#L62-L89
[key-labels]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/resources/assets/minecraft/lang/en_us.json#L8229-L8240
[options-startup]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/client/Minecraft.java#L454-L459
[options-load]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/client/Options.java#L1194-L1218
[options-keys]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/client/Options.java#L1339-L1345
[client-registration]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/client/Minecraft.java#L703-L705
[aim]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/client/tacz/TaczGlock17AnimationController.java#L38-L61
[pose]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L224-L237
[keyboard-repeat]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/client/KeyboardHandler.java#L525-L560
[shots]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L100-L143
[cycle-mode]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L334-L348
[burst-data]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczGunBurstData.java#L6-L22
[hud]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/client/tacz/TaczGunHudOverlay.java#L18-L129
[transport]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/fabricmc/fabric/api/client/networking/v1/ClientPlayNetworking.java#L34-L43
[codec]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/network/protocol/common/ServerboundCustomPayloadPacket.java#L21-L46
[protocol]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L59-L83
[server]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2194-L2225
[fire-round]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L145-L181
[tooltip]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L242-L251
[reserve]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L351-L369
[reload-start]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L184-L218
[use-completion]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3255-L3270
[reload-timing]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczGunReloadTimings.java#L7-L32
[reload-ammo]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L261-L305
[refit-screen]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/client/tacz/TaczGunRefitScreen.java#L32-L111
[refit-close]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/client/tacz/TaczGunRefitScreen.java#L214-L226
[compatibility]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[refit-server]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2227-L2267
[inventory-add]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/entity/player/Inventory.java#L250-L289
[magazine]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L308-L331
[refit-storage]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczRefitGun.java#L16-L82
[damage-curve]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L51-L61
[hit]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L162-L179
[glock-data]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/resources/data/minecraft/data/guns/glock_17_data.json#L1-L40
[distance]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L262-L272
[minigun]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L52
[fire-gate]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L83-L98
[definitions]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L10-L108
[recipe-output]: https://github.com/HungLo2020/MattMC/blob/cfa7057b6fe2b8dfa84e93f21932be2602eff749/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L124-L151

[burst-task-creation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L101-L145
[burst-task-admission]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/MinecraftServer.java#L1008-L1014
[burst-task-queue]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/util/thread/BlockableEventLoop.java#L87-L128

[speed-setting]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L24
[speed-modes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L64-L67
[speed-launch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L129-L154
[speed-flight]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L99-L134
[speed-ticks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/TickRateManager.java#L7-L29
