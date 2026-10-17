# Locator Bar

The built-in **Locator Bar** shows the direction of other players and eligible tracked entities. It shares the HUD position used by the experience and mount-jump bars. Its markers come from the server's entity tracking; use [Minimap and waypoints](MinimapAndWaypoints.md) for VoxelMap's local saved places, world map and highlighting controls. [World tracking][world-track] · [HUD selection][selection]

## When the bar appears

The `locatorBar` game rule defaults to **`true`**. The client must also have at least one tracked waypoint before the Locator Bar is selected. With a waypoint present, the shared bar follows this priority:

1. A jumpable mount's bar takes priority while its jump charge or cooldown is greater than zero
2. Otherwise, a mode that uses experience shows the experience bar during the **100 client ticks after experience values are received**
3. Otherwise, the Locator Bar is selected

The experience timer restarts when the client receives its experience values; picking up an orb is not the only possible trigger. Without tracked waypoints, the HUD selects the mount bar, experience bar or an empty bar according to the current mount and game mode. Your level number can still appear with the Locator Bar when the mode uses experience and your level is greater than zero. See [Experience](Experience.md) for points, levels and Mending. [Rule default and callback][rule] · [Priority and selection][selection] · [Experience packet][xp-packet] · [Experience timer][xp-timer] · [Level number][bar-render]

Keep the HUD visible to see the bar: hiding the HUD skips its normal rendering call. A selected Locator Bar can still have no visible dots if its tracked targets are outside the displayed directions. [HUD visibility gate][hud-gate] · [Dot filter][dots]

## Read the directions and icons

Turn to bring a target's dot toward the center of the bar. Only horizontal directions **greater than −60° and up to +60°** relative to the camera are drawn, so a target behind you does not get a dot in that view. The camera entity itself is excluded. [Direction and position][dots]

An explicit icon color takes precedence; otherwise a scoreboard team color can supply it. If neither provides a color, the renderer derives one from the waypoint's UUID or name. Do not use a particular default color to identify a player. The bar draws icons and possible arrows, without player-name labels or coordinate readouts. [Team-color fallback][icon-color] · [Dot drawing][dots]

An up/down arrow is a cue from the current waypoint projection, not a fixed height difference in blocks. A block-position waypoint uses the target position, with a nearby client entity's eye position when available. **Chunk and distant direction-only waypoints use the projected horizon for their arrows**, so their arrows do not establish the target's actual elevation. [Position and height cue][position-cue] · [Chunk cue][chunk-cue] · [Direction-only cue][azimuth-cue]

## Range and location precision

For an ordinary receiver, the source must be closer than the **smaller of its transmit range and the receiver's receive range**. Equality is excluded. Ordinary players start with both attributes set to **60,000,000 blocks**, but that is a configured range, not unlimited coverage or a promise that every entity in the world is available for tracking. Positive transmit/receive attributes, world tracking and the game rule still matter. [Range filter][range] · [Player defaults][player-ranges] · [Transmit gate][transmit-gate] · [Receive gate][receive-gate] · [World tracking][world-track]

When the server creates an eligible connection, it chooses the supplied location detail:

- **Farther than 332 blocks:** horizontal direction only
- **Within 332 blocks, with the source chunk outside the receiver's view distance:** chunk position
- **Within 332 blocks, with that chunk in view distance:** block position

The chunk test uses the receiver's view distance. It does not check whether terrain blocks the view. An intervening wall is not a hiding condition in these checked connection and dot-rendering paths. [Chunk and range tests][range] · [Connection selection][connection-selection] · [Dot drawing][dots]

**332 blocks is a precision threshold, not a detection cap.** These are connection-selection rules; updates can retain or replace a connection according to its own checks. The bundled default icon style also changes sprite with distance, using **128** and **332** as its near/far thresholds and four sprites. Those style thresholds choose an icon, not whether a source is eligible. [Connection selection][connection-selection] · [Distance and chunk tests][range] · [Connection updates][connection-updates] · [Style thresholds][style] · [Default sprites][default-style]

Tracking is managed separately in each server level/dimension, and departing players are removed from that level's manager. Do not use the bar as a cross-dimension tracker. An ordinary receiver also rejects a spectator source and a source carrying that receiver indirectly. A **spectator receiver bypasses that particular distance/source-spectator/passenger filter**; this does not bypass every tracking, attribute or HUD condition. Self-connections and first-tick connection creation are excluded separately. [Per-level manager][level-manager] · [Player departure][player-departure] · [Spectator and passenger filter][range] · [Connection gate][connection-selection]

## Stop transmitting your marker

The checked built-in hiding routes reduce the waypoint-transmit attribute. With the ordinary attributes, these suppress transmission:

- **Crouch:** the server applies the hiding modifier while the player satisfies `isCrouching`, then removes it otherwise. A held Sneak key or a crawling pose alone is not the condition this code tests. [Crouch update][crouch] · [Modifier][crouch-modifier]
- **Wear a Carved Pumpkin or a supported head/skull in the head slot:** the checked items are Skeleton Skull, Wither Skeleton Skull, Player Head, Zombie Head, Creeper Head, Dragon Head and Piglin Head. Carrying one in ordinary inventory does not apply this head-slot modifier. [Pumpkin][pumpkin] · [Head items][heads] · [Head-slot modifier][head-modifier]
- **Have Invisibility:** the effect supplies a transmit-range reduction. See [Invisibility, Glowing, and Nausea](../effects/VisibilityEffects.md#invisibility) for acquisition and its separate detection/rendering behavior. [Invisibility modifier][invisibility]

The crouch and head-item modifiers each apply **−1 to total multiplication**, giving a ×0 factor; Invisibility's base modifier also uses −1. When the resulting transmit range is nonpositive, the server untracks the entity and disconnects its current waypoint connections. These are controls for this built-in transmitter, not a guarantee of concealment from every spectator/operator tool, other mod or custom attribute setup. [Attribute response][transmit-update] · [Disconnecting a source][untrack]

## World rule and operator controls

Use [Game rules](GameRules.md#inspect-or-change-a-loaded-world) for permission, query/change and saving guidance. `/gamerule locatorBar` inspects the current value; `/gamerule locatorBar false` disables connections, and `/gamerule locatorBar true` enables connection updates again. The registered default is `true`, which need not be the current value of a saved world. This is a world rule, not a personal HUD preference. [Rule registration and callback][rule]

The active `/waypoint` command requires **permission level 2**. See [Commands](../commands/Commands.md#start-with-help-and-permissions) for command access and self-targeting. Useful forms include:

- `/waypoint list` lists the current command-source dimension's tracked transmitters
- `/waypoint modify @s color <color>` sets your entity's icon color; replace `<color>` with a command color argument
- `/waypoint modify @s color reset` removes that explicit color, allowing the normal fallbacks
- `/waypoint modify @s style set minecraft:bowtie` selects the bundled bowtie style
- `/waypoint modify @s style reset` restores the default style

These self-targeted examples are for an in-game entity source, not a server console. Icon changes target an entity and untrack/retrack its transmitter. The bowtie style uses its bowtie sprite below **64 blocks**, then distance-dependent default sprites. A custom style ID needs a matching client resource; selecting an arbitrary ID does not supply one. `/waypoint` has no subcommand here for creating named coordinate markers. Use [VoxelMap's save-a-place workflow](MinimapAndWaypoints.md#save-a-place-and-find-it-again) for that purpose. [Active registration][command-registration] · [Syntax and permission][waypoint-command] · [Icon changes and listing][waypoint-actions] · [Bowtie resources][bowtie-style] · [Style lookup][style-loader]

Listing alone does not teleport anyone. A clickable entity entry **suggests** an `/execute … run tp …` command; executing that command is a separate action. [List entry behavior][waypoint-list]

## Sources and verification

Source-reviewed on **2026-10-05** at `5218ac875eda9f2c4151ff1a97795f20f5f356cf`. The checked active route runs from world tracking and server connections through the registered waypoint packet, client waypoint storage and the selected HUD renderer. It is more than an unused command or sprite registration. [Server connection dispatch][server-connections] · [Packet registration][packet-registration] · [Packet operations][packet] · [Client handler][client-handler] · [Client storage][client-storage] · [HUD binding][hud-binding]

The current whole-frame Rust GUI route calls the HUD, turns its textured sprites into semantic blit records and submits them to native GUI admission. That admission requires a supported pipeline, valid affine geometry, semantic texture and resolved copied asset; missing assets or invalid records can be rejected. The frozen source includes the background, two arrows and five dot PNGs used by the bundled styles. Their presence and the active path do **not** verify a successful runtime image. [Active HUD call][active-hud] · [Sprite resolution][sprite-resolution] · [Atlas sprite call][atlas-blit] · [Semantic blit][semantic-blit] · [Collection and frame submission][frame-submit] · [Blit consumer][blit-consumer] · [Asset admission][native-admission] · [Geometry admission][geometry-admission]

No game was launched. No multiplayer session, command, packet delivery, equipment change, sprite upload or visual comparison was tested. Later builds, resource packs and custom attributes can change the documented behavior.

Related: [Mechanics](Mechanics.md) · [Minimap and waypoints](MinimapAndWaypoints.md) · [Experience](Experience.md) · [Game rules](GameRules.md) · [Commands](../commands/Commands.md) · [Visibility effects](../effects/VisibilityEffects.md)

[world-track]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/level/ServerLevel.java#L1858-L1894
[selection]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/Gui.java#L1614-L1637
[rule]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/GameRules.java#L217-L228
[xp-packet]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/multiplayer/ClientPacketListener.java#L1251-L1260
[xp-timer]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/player/LocalPlayer.java#L421-L425
[bar-render]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/Gui.java#L590-L612
[hud-gate]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/Gui.java#L236-L247
[dots]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/contextualbar/LocatorBarRenderer.java#L46-L93
[icon-color]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/waypoints/Waypoint.java#L66-L73
[position-cue]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/waypoints/TrackedWaypoint.java#L282-L319
[chunk-cue]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/waypoints/TrackedWaypoint.java#L165-L192
[azimuth-cue]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/waypoints/TrackedWaypoint.java#L112-L130
[range]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/waypoints/WaypointTransmitter.java#L22-L39
[player-ranges]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/player/Player.java#L220-L235
[transmit-gate]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3716-L3720
[receive-gate]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1009-L1024
[connection-selection]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3722-L3737
[connection-updates]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/waypoints/ServerWaypointManager.java#L119-L137
[style]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/resources/WaypointStyle.java#L15-L59
[default-style]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/assets/minecraft/waypoint_style/default.json
[level-manager]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/level/ServerLevel.java#L194
[player-departure]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/level/ServerLevel.java#L1916-L1921
[crouch]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/level/ServerPlayer.java#L609-L640
[crouch-modifier]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/level/ServerPlayer.java#L222-L224
[pumpkin]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/item/Items.java#L506-L513
[heads]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/item/Items.java#L2062-L2097
[head-modifier]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/waypoints/Waypoint.java#L22-L32
[invisibility]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/effect/MobEffects.java#L62-L71
[transmit-update]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1117-L1123
[untrack]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/waypoints/ServerWaypointManager.java#L46-L50
[command-registration]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/commands/Commands.java#L248
[waypoint-command]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/commands/WaypointCommand.java#L32-L93
[waypoint-actions]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/commands/WaypointCommand.java#L96-L167
[bowtie-style]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/resources/assets/minecraft/waypoint_style/bowtie.json
[style-loader]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/resources/WaypointStyleManager.java#L20-L36
[waypoint-list]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/commands/WaypointCommand.java#L126-L160
[server-connections]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/waypoints/ServerWaypointManager.java#L101-L137
[packet-registration]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L267
[packet]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/network/protocol/game/ClientboundTrackedWaypointPacket.java#L20-L85
[client-handler]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/multiplayer/ClientPacketListener.java#L2474-L2477
[client-storage]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/waypoints/ClientWaypointManager.java#L17-L40
[hud-binding]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/Gui.java#L217-L225
[active-hud]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/renderer/GameRenderer.java#L966-L984
[sprite-resolution]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/GuiGraphics.java#L297-L324
[atlas-blit]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/GuiGraphics.java#L351-L372
[semantic-blit]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/GuiGraphics.java#L610-L641
[frame-submit]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/renderer/GameRenderer.java#L1040-L1083
[blit-consumer]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/gui/render/GuiRenderer.java#L485-L510
[native-admission]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/vulkanic/gui/RustGalGuiRenderer.java#L1644-L1669
[geometry-admission]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/vulkanic/gui/RustGalGuiRenderer.java#L1739-L1745
