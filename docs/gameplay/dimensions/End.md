# End

The End (`minecraft:the_end`) is the destination of activated End portals and the dimension with the Ender Dragon fight. Prepare supplies and a usable respawn point elsewhere before entering: both beds and attempts to set spawn with charged Respawn Anchors explode here.

## Reaching the End

[End portals and frames](../blocks/EndPortals.md#end-portal-frame) covers hand-built rings, the inventory-browser frame route in Creative, and activation details. [End Gateways](../blocks/EndPortals.md#end-gateway) covers gateway creation and linking.

1. Craft Eyes of Ender from one Ender Pearl and one Blaze Powder per eye, using the shapeless recipe
2. Use an eye away from an End Portal Frame to launch it toward a located stronghold. The bundled eye-location structure tag contains strongholds; a world must have an applicable structure for this search to succeed
3. Find the stronghold's portal room and fill its empty frame sockets with eyes. The generated ring has 12 frames, and some may already contain eyes
4. Once every correctly facing frame has an eye, the activation code fills the ring's 3×3 interior with End portal blocks. Enter that portal to travel

The stronghold generator includes a portal-room requirement. This is a route through a generated structure, not evidence that End Portal Frames can be collected or crafted in Survival. Ingredient gathering, modified structure settings, and particular seed locations are outside this page's verification.

## Arrival and the dragon fight

Entry uses the fixed End arrival area around X=100, Z=0 rather than multiplying the source coordinates. The entry code rebuilds a 5×5 Obsidian platform and clears the three block layers above it. **Keep permanent storage and buildings out of that arrival volume:** another entry can replace blocks there.

The default End world creates and ticks an Ender Dragon fight controller. Defeating its tracked dragon activates the central exit portal and creates another End Gateway while gateway locations remain available.

End Gateways are travel within the End. Their destination code keeps the same server world; they are not the central portal that returns you to your respawn point. This page does not verify End-city loot or provide a complete boss strategy.

## Leaving and respawning

The central End exit uses your valid personal respawn point, with a default-world-spawn fallback if that point is missing or unusable. It does **not** send you back through the stronghold portal. A valid charged Nether anchor can therefore be your destination if that is your saved respawn point.

Returning through the exit does not consume an anchor charge. An ordinary death respawn does consume one when a valid anchor is used. The first End exit also has a credits step before the player's return.

Set up your bed or anchor in a dimension that supports it, and check the surrounding space before starting the trip. The End's bundled rules support neither kind of ordinary respawn point.

## Hazards

- **The void:** falling below the world's lower boundary causes repeated out-of-world damage. Bring bridging supplies and keep a recoverable route across gaps
- **Explosive spawn items:** using a bed or trying to set spawn with a charged anchor causes an explosion rather than establishing an End respawn
- **Arrival-platform replacement:** do not treat the entrance platform and its cleared space as protected storage

## Related pages

- [End biomes](../biomes/EndBiomes.md): compare central terrain, Chorus-bearing Highlands, city-eligible Midlands, Barrens and Small End Islands

- [End City](../structures/EndCity.md) and [Elytra](../items/Elytra.md): outer-island exploration, ships, and safe flight limits

- [Ender Dragon](../mobs/EnderDragon.md): crystals, perching, breath, rewards, and respawning
- [Dragon Egg](../blocks/DragonEgg.md): trophy collection, teleporting, and falling

- [Dimensions](Dimensions.md)
- [Nether and anchor respawns](Nether.md#respawning-safely)
- [Beds](../blocks/Bed.md)

## Sources and verification

Reviewed at source `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game stronghold search, portal crossing, dragon fight, gateway, or respawn test was performed.

- [End dimension rules](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/dimension_type/the_end.json)
- [Eye recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/ender_eye.json), [eye use and portal activation](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/EnderEyeItem.java#L35-L105), and [located structure tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/worldgen/structure/eye_of_ender_located.json)
- [Stronghold structure set](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/worldgen/structure_set/strongholds.json), [portal-room requirement](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/levelgen/structure/structures/StrongholdStructure.java#L28-L51), [generated frames and eyes](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/levelgen/structure/structures/StrongholdPieces.java#L814-L848), and [complete-frame pattern](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/EndPortalFrameBlock.java#L83-L115)
- [Entry, exit, and first-exit credits](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/EndPortalBlock.java#L60-L108), [fixed arrival point](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/level/ServerLevel.java#L181), and [platform rebuilding](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/levelgen/feature/EndPlatformFeature.java)
- [Dragon-fight creation](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/level/ServerLevel.java#L289-L292), [fight tick](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/level/ServerLevel.java#L390-L393), and [dragon death, exit portal, and gateways](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/dimension/end/EndDragonFight.java#L364-L423)
- [Same-dimension gateway destination](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/EndGatewayBlock.java#L102-L119)
- [Personal respawn checks and charge use](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/level/ServerPlayer.java#L993-L1068), [default-spawn fallback](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/portal/TeleportTransition.java#L48-L81), [credits versus death return](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1771-L1782), and [spawn-block usage flag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/server/players/PlayerList.java#L430-L431)
- [Bed explosion](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/BedBlock.java#L80-L122), [anchor explosion](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/RespawnAnchorBlock.java#L92-L164), [below-world check](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/Entity.java#L546-L549), and [living-entity void damage](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2028-L2030)
