# End portals, frames, and gateways

**End Portal Frames** activate an End portal, **End portals** move between dimensions, and **End Gateways** move within their current dimension. Only the frame has a registered inventory item. Use the [End guide](../dimensions/End.md) for expedition preparation and respawning, [Eye of Ender](../items/EyeOfEnder.md) for searching and eye supplies, and [Ender Dragon](../mobs/EnderDragon.md) for the fight and respawn ritual. [Block registrations][end-blocks] [Gateway block][gateway-block] · [Item registry][items]

## End portal frame

**Block and item ID:** `minecraft:end_portal_frame`. The block has `facing=north|south|east|west` and `eye=false|true`. A newly placed frame faces opposite the placer's horizontal direction and starts without an eye; the default state faces north. It emits **light level 1**. A comparator reads **0** from an empty frame and **15** from a filled one. [Properties][end-blocks] · [Placement, states, and comparator][frame-state]

### Finding or placing frames

For the structure-based route, use the frames already placed in a [Stronghold](../structures/Stronghold.md). The active stronghold generator requires a portal room, places 12 frames, and rolls a prefilled-eye chance for each. If all twelve are prefilled, generation also places the active portal interior. This is not a guarantee of a stronghold location for a particular seed or modified world. [Portal-room requirement][stronghold] · [Generated frame ring][generated-frames]

Frames have **hardness −1** and **no loot table**: ordinary Survival mining cannot collect them, including with Silk Touch. No bundled recipe produces an End Portal Frame. The real item is listed in the ordinary Functional Blocks category, so MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) can supply it **in Survival as well as Creative**, subject to that browser's feature, cursor, and inventory-space checks. This listing is outside the permission-gated operator category. A permitted `give` command is another route. There is no ordinary interaction here for extracting an inserted eye. [Block properties][end-blocks] · [Mining rule][mining] · [Item registration][frame-item] · [Functional category][frame-category] · [Frame listing][creative] · [Browser list][browser-list] · [Client request][browser-client] · [Server insertion][browser-server] · [Give permission][give] · [Frame and insertion behavior][frame-state] [Eye use][eye-use]

### Ring layout and activation

Arrange **three frames on each of four sides around a 3×3 opening**: a 5×5 footprint with 12 frames and no required corner frames. All four sides must face **inward**. For a hand-built ring, standing in the middle and placing the surrounding frames outward makes their facing point back toward you. Filling a wrongly facing frame does not repair its direction. [Placement and facing][frame-state] · [Required pattern][frame-pattern]

Use an Eye of Ender on each empty frame. Ordinary Survival insertion spends one eye and sets `eye=true`; an already filled frame does not accept another. The final successful insertion checks the full ring, then destroys and replaces the **nine interior blocks** with End portal blocks. Clear belongings from that area first. The [eye guide](../items/EyeOfEnder.md#filling-portal-frames) covers the item interaction in detail. [Insertion and replacement][eye-use]

The ring is an **activation condition**, not a continuous frame-support requirement for active End portal cells. These cells do not run the Nether portal's frame check. Frames themselves do not require a supporting block beneath them in the checked placement/survival implementation. Removing or changing a frame through Creative tools or commands therefore does not provide the ordinary Nether-style collapse behavior. [Frame implementation][frame-all] · [End portal implementation][end-all] · [Default support and neighbor behavior][base-support] [Base update][base-update]

## End portal

**Block ID:** `minecraft:end_portal`. It has no frame-facing or eye state of its own and no registered item. It has no solid collision, emits **light level 15**, has hardness **−1**, and has no loot table. It cannot be mined in ordinary Survival or moved by pistons. Its contact shape is a horizontal layer from 6/16 to 12/16 of the block's height: travel requires contact with that layer, not just proximity to a frame. [Registration][end-blocks] · [Shape and contact][end-contact] · [Items][items] · [Mining][mining] · [Pistons][pistons]

Stronghold activation creates these cells. The tracked dragon's completed defeat also activates a central Bedrock exit podium containing the **same block ID**, without requiring End Portal Frame items. See [dragon victory](../mobs/EnderDragon.md#victory-and-rewards) and [respawning the dragon](../mobs/EnderDragon.md#respawning-the-dragon) for that progression and its destructive rebuilding. [Tracked kill][fight-kill] · [Active podium placement][podium]

### Entering the End

From any dimension other than the End, an End portal targets `minecraft:the_end`. It uses the fixed arrival point **(100, 50, 0)**, not a ratio of the departure X/Z. With the checked platform code, it rebuilds Obsidian at **X=98–102, Z=−2–2, Y=48**, and clears those same X/Z columns at **Y=49–51**. The player arrival point is centered at **(100.5, 49, 0.5)**. Keep permanent storage and builds out of that volume; later entries can destroy and replace blocks there. [Destination and player offset][end-destination] · [Fixed point][end-spawn] · [Platform rebuilding][platform]

Entering the End and later processed returns use the default **zero transition-time threshold**, but eligibility and the shared entity cooldown still matter. The first credits exit follows the separate return path described below. Alive passengers cannot initiate ordinary contact travel while riding, sleeping living entities are excluded, and entity-specific restrictions still apply. This is not the Nether portal's four-second default wait. Read [shared portal controls](NetherPortals.md#entry-and-cooldown-controls) before troubleshooting repeated contact. [Default portal timing][portal] · [Contact][end-contact] · [Shared dispatch][dispatch] · [Eligibility][eligibility] [Sleeping][sleeping]

### Leaving the End

Inside the End, these same blocks take a player toward their valid saved respawn point, with a default-spawn fallback when necessary. They do not remember the stronghold or operator portal used to enter. A first player exit has a credits step before the return; the credits-return path and later direct return both avoid spending a Respawn Anchor charge. Follow the [End return guide](../dimensions/End.md#leaving-and-respawning) for preparing a valid bed or anchor. [Destination and credits branch][end-destination] [End contact][end-contact] · [Personal respawn checks][respawn] · [Credits return][credits-return] · [Return charge flag][respawn-flag] · [Default fallback][default-spawn]

Non-player entities use the server's shared respawn data and their spawn-location adjustment rather than a player's personal bed or anchor. Missing destination worlds can prevent travel. The active processor also checks entity-specific cross-dimension permission; for example, an End-to-Overworld vehicle carrying a player who has not seen credits is refused by the base check. [Destination][end-destination] · [Shared respawn delegation][shared-spawn] · [Permission and dispatch][dispatch] [Eligibility][eligibility]

## End gateway

**Block ID:** `minecraft:end_gateway`. This is the single travel block inside a gateway's Bedrock casing. It has no ordinary item or selectable block-state variants, no solid collision, **light level 15**, hardness **−1**, and no loot table. Ordinary Survival mining and pistons cannot collect or move it. The generated casing is not a ring that players activate with eyes, nor does the gateway continuously validate that casing for support. [Registration][gateway-block] · [Gateway implementation][gateway-all] · [Feature casing][gateway-feature] · [Items][items] · [Mining][mining] · [Pistons][pistons]

### Where gateways come from

The bundled End dimension creates the normal dragon-fight controller. A tracked dragon defeat attempts to place a gateway while unused slots remain: the default tracker has **20 slots**, arranged around a horizontal radius of about **96 blocks**, at Y=75. These gateways begin without an assigned exit. The [dragon guide](../mobs/EnderDragon.md#victory-and-rewards) owns the full reward rules; spawning or killing an arbitrary dragon is not evidence that this fight-controller path ran. [Controller setup][fight-setup] · [Slots][fight-slots] · [Kill and gateway creation][fight-kill] · [Delayed configuration][gateway-delayed]

There is also an active **generated return gateway** route: the Normal preset uses the End biome source, that source can select End Highlands, and that biome includes the return-gateway feature with a rarity filter. Its configured exit is exactly **(100, 50, 0)**. This proves a generation route, not a guaranteed gateway near an End City or at any seed coordinate. [Normal preset][normal] · [End biome source][end-biomes] · [Highlands features][highlands] · [Placement][gateway-placement] · [Return configuration][gateway-return] · [Active feature dispatch][decoration]

### Contact and cooldown

An eligible entity must contact the gateway while its block entity is not cooling down. The gateway then marks the entity for the normal portal processor and starts a **40-tick gateway cooldown**, about two seconds at 20 TPS. That cooldown is shared by users of that placed gateway and is separate from the entity's own portal cooldown. A periodic 2,400-tick age interval also starts the gateway cooldown. The first 200 ticks drive the spawning visual; the entry check does not impose a separate 200-tick travel lock. [Entry and destination][gateway-travel] · [Age and cooldown][gateway-clock] · [Shared controls](NetherPortals.md#entry-and-cooldown-controls)

A thrown [Ender Pearl](../items/EnderPearl.md) is itself passed through the gateway with zero velocity. The owner's later teleport still depends on the pearl's impact and eligibility rules, and ordinary player pearl damage applies. Do not assume the gateway directly and immediately teleports the thrower just because a pearl entered it. [Pearl gateway transition][gateway-travel] · [Impact and owner teleport][pearl]

### Exit creation and arrival limits

**A gateway stays in its current server dimension.** It is not the central End exit. If an unassigned gateway is used **in the End**, it searches outward from the origin gateway's X/Z direction, starting around 1,024 blocks from the center and adjusting across nearby chunks. It looks for usable End Stone; if none is found, it attempts to generate an island near Y=75. It then selects a high block, places a counterpart gateway ten blocks above the selected surface, and stores the link. The new counterpart points back to the first gateway. These searches depend on the world, so the 1,024-block starting point is not a promised exit coordinate. [Same-dimension transition][gateway-travel] · [Exit search, fallback, and counterpart][gateway-exit]

For a normal non-exact link, arrival selects a highest full-collision block in a five-block horizontal square radius around the saved exit, excluding the center column and Bedrock, then places the entity above it. The earlier chunk search checks two cells above a candidate for non-full collision, but the final highest-block search is **not a full headroom, liquid, fall, or hazard check**. An exact link skips that final search. Inspect and secure both ends instead of treating either setting as a safety guarantee. [Final position and highest-block search][gateway-safe] · [Initial chunk candidates][gateway-candidate]

The naturally generated return gateway's exact (100, 50, 0) destination also **does not rebuild the End arrival platform**: it uses the same-dimension gateway path, not the End portal's platform-creation path. If that area has been altered, the old coordinates alone do not make arrival safe. [Exact return setting][gateway-return] · [Gateway transition][gateway-travel] · [Separate platform path][end-destination]

## Operator setup and verification

Operators can place these registered blocks with permission-gated block commands; only End Portal Frame is a registered inventory item. Gateway destination information is stored in its block entity, including `exit_portal` and `ExactTeleport`, not in ordinary block states. An unassigned gateway outside the End has no automatic exit-search fallback and returns no destination; merely placing it there does not connect dimensions. No bundled recipe produces any of the three block IDs on this page. [Block command][setblock] · [Item registry][items] · [Saved gateway data and exit selection][gateway-data] [Gateway exit][gateway-exit]

Source-reviewed on **2026-10-02** at `b823010659d7b5095ed021b1c99cf85627e2082a`. Checked active frame activation, portal contact/server dispatch, End entry and return, tracked and biome-generated gateway creation, exit search, registration, item distinction, and the frame's ordinary-category inventory-browser route. No in-game stronghold search, frame build, portal crossing, dragon fight, gateway, pearl, safe-arrival, or operator-command test was run. Custom data, rules, and saved fight or gateway state can alter results.

Related: [End Portal Frame item](../items/EndPortalFrame.md) · [Nether and Primordial Caves portals](NetherPortals.md) · [End](../dimensions/End.md) · [Blocks](Blocks.md)

[items]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java
[mining]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L347-L357
[pistons]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java#L226-L259
[setblock]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/commands/SetBlockCommand.java#L22-L40
[dispatch]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/Entity.java#L2460-L2501
[eligibility]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/Entity.java#L3065-L3079
[sleeping]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3443-L3446
[normal]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[end-blocks]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Blocks.java#L2544-L2565
[gateway-block]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Blocks.java#L4286-L4296
[frame-state]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/EndPortalFrameBlock.java#L25-L66
[frame-all]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/EndPortalFrameBlock.java
[frame-pattern]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/EndPortalFrameBlock.java#L83-L115
[stronghold]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/structure/structures/StrongholdStructure.java#L27-L51
[generated-frames]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/structure/structures/StrongholdPieces.java#L814-L848
[frame-item]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/Items.java#L583
[creative]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1259-L1263
[give]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/commands/GiveCommand.java#L25-L35
[eye-use]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/EnderEyeItem.java#L35-L67
[end-all]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/EndPortalBlock.java
[base-support]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L311
[base-update]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L150-L168
[end-contact]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/EndPortalBlock.java#L32-L71
[fight-kill]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/dimension/end/EndDragonFight.java#L364-L423
[podium]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/feature/EndPodiumFeature.java#L30-L80
[end-destination]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/EndPortalBlock.java#L73-L110
[end-spawn]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/level/ServerLevel.java#L180-L181
[platform]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/feature/EndPlatformFeature.java#L21-L38
[portal]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/Portal.java#L9-L12
[respawn]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/level/ServerPlayer.java#L993-L1058
[credits-return]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1765-L1783
[respawn-flag]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/players/PlayerList.java#L430-L431
[default-spawn]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/portal/TeleportTransition.java#L48-L81
[gateway-all]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/EndGatewayBlock.java
[gateway-feature]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/levelgen/feature/EndGatewayFeature.java#L15-L44
[fight-setup]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/level/ServerLevel.java#L289-L293
[fight-slots]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/dimension/end/EndDragonFight.java#L105-L122
[gateway-delayed]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/worldgen/configured_feature/end_gateway_delayed.json
[end-biomes]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/biome/TheEndBiomeSource.java#L11-L78
[highlands]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/worldgen/biome/end_highlands.json
[gateway-placement]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/worldgen/placed_feature/end_gateway_return.json
[gateway-return]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/resources/data/minecraft/worldgen/configured_feature/end_gateway_return.json
[decoration]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L308-L380
[gateway-travel]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/EndGatewayBlock.java#L87-L125
[gateway-clock]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/entity/TheEndGatewayBlockEntity.java#L30-L129
[pearl]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/entity/projectile/ThrownEnderpearl.java#L84-L147
[gateway-exit]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/entity/TheEndGatewayBlockEntity.java#L132-L193
[gateway-safe]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/entity/TheEndGatewayBlockEntity.java#L142-L218
[gateway-candidate]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/entity/TheEndGatewayBlockEntity.java#L224-L249
[gateway-data]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/level/block/entity/TheEndGatewayBlockEntity.java#L30-L65
[frame-category]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1031-L1279
[browser-list]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[browser-client]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-server]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L2029
[shared-spawn]: https://github.com/HungLo2020/MattMC/blob/b823010659d7b5095ed021b1c99cf85627e2082a/src/main/java/net/minecraft/server/level/ServerLevel.java#L1395-L1398
