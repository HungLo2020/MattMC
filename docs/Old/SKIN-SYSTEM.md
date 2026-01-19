# Decentralized Skin System

## Overview

MattMC implements a fully decentralized player skin system that eliminates dependency on Mojang/Microsoft authentication servers. Instead of fetching skins from Mojang's servers, the system uses peer-to-peer synchronization where clients transmit their selected skins directly to game servers, which cache and broadcast them to other connected players.

## Removal of Mojang/Microsoft Authentication

### Previous Implementation
In vanilla Minecraft, the skin system was tightly integrated with Mojang's authentication infrastructure:
- Players authenticated with Mojang/Microsoft accounts
- Client fetched skin textures from Mojang's skin servers using player UUIDs
- Skins were cached locally but always validated against Mojang's servers
- Required active internet connection to authentication servers

### Current Implementation
The authentication system has been completely removed and replaced with:
- **Offline authentication**: Services created with `Services.createOffline()`
- **No Yggdrasil**: Authentication protocol removed entirely
- **No profile fetching**: Player profiles no longer retrieved from Mojang
- **Local skin storage**: All skins stored and managed locally or peer-to-peer

Key changes in `SkinManager.java`:
```java
// Old: Complex authentication and remote fetching
// New: Always returns default or locally configured skins
public CompletableFuture<Optional<PlayerSkin>> get(PlayerProfile playerProfile) {
    PlayerSkin playerSkin = DefaultPlayerSkin.get(playerProfile);
    return CompletableFuture.completedFuture(Optional.of(playerSkin));
}
```

## System Architecture

### 1. Client-Side Components

#### SkinLoader (`net.minecraft.client.resources.SkinLoader`)
- **Purpose**: Discovers and loads all available skins dynamically
- **Scan Locations**:
  - Built-in skins: `assets/minecraft/textures/entity/player/{wide,slim}/`
  - Custom skins: `<game_directory>/skins/`
- **Validation**: Checks PNG dimensions (64x64 or 64x32)
- **Storage**: Maintains list of `SkinEntry` records with display name, location, model type

#### ClientSkinCache (`net.minecraft.client.multiplayer.ClientSkinCache`)
- **Purpose**: Caches skins received from multiplayer servers
- **Storage**: Maps UUID → PlayerSkin with dynamic textures
- **Texture Management**: 
  - Converts PNG byte arrays to `NativeImage`
  - Creates `DynamicTexture` instances
  - Registers with `TextureManager` under `player_skins:<uuid>` namespace
- **Lifecycle**: Textures released when player disconnects

#### Customize Screen (`net.minecraft.client.gui.screens.CustomizeScreen`)
- **Access**: New "Customize" button on title screen
- **UI Elements**:
  - Dropdown selector listing all available skins
  - "Reload Skins" button for hot-loading custom skins
- **Persistence**: Selected skin saved to `options.txt` as `selectedSkin:<skin_name>`

### 2. Server-Side Components

#### PlayerSkinCache (`net.minecraft.server.players.PlayerSkinCache`)
- **Purpose**: Stores active player skins on the server
- **Storage**: Maps UUID → CachedSkin (name, byte[], isSlim)
- **Lifecycle**: 
  - Populated when player joins and uploads skin
  - Cleared when player disconnects

### 3. Network Protocol

#### Packets
Three custom packets handle skin synchronization:

**ServerboundPlayerSkinPacket** (Client → Server)
```java
record ServerboundPlayerSkinPacket(String skinName, byte[] skinData, boolean isSlimModel)
```
- Sent when client joins server
- Contains selected skin as PNG byte array (max 32KB)
- Includes model type flag (slim/wide)

**ClientboundPlayerSkinPacket** (Server → Client)
```java
record ClientboundPlayerSkinPacket(UUID playerId, String skinName, byte[] skinData, boolean isSlimModel)
```
- Broadcast to all players when someone joins
- Sent to new joiners for each existing player
- Enables clients to render other players' custom skins

**ClientboundRemovePlayerSkinPacket** (Server → Clients)
```java
record ClientboundRemovePlayerSkinPacket(UUID playerId)
```
- Sent when player disconnects
- Triggers cleanup of cached textures on all clients

## Data Flow

### Player Join Sequence
1. Client loads game, `SkinLoader` scans available skins
2. Player selects skin in Customize menu → saved to `options.txt`
3. Player joins server
4. Client sends `ServerboundPlayerSkinPacket` with selected skin
5. Server caches skin in `PlayerSkinCache`
6. Server broadcasts `ClientboundPlayerSkinPacket` to all other players
7. Server sends all existing player skins to the new joiner
8. Clients receive packets and populate `ClientSkinCache`
9. `PlayerInfo.getSkin()` retrieves skin from cache when rendering

### Player Disconnect Sequence
1. Player leaves server
2. Server removes skin from `PlayerSkinCache`
3. Server broadcasts `ClientboundRemovePlayerSkinPacket`
4. All clients purge the disconnected player's skin from memory
5. Associated `DynamicTexture` released from `TextureManager`

### Skin Rendering Logic
Located in `PlayerInfo.getSkin()`:
```java
1. Check if local player → load from SkinLoader with selected skin
2. Check ClientSkinCache for network-received skins
3. Fall back to default skin based on UUID hash
```

## Custom Skins

### File Requirements
- **Location**: `<game_directory>/skins/`
- **Format**: PNG image files
- **Dimensions**: 64x64 pixels (standard) or 64x32 pixels (legacy)
- **Naming**: Filename becomes display name in UI

### Loading Process
1. SkinLoader scans directory on startup
2. Validates each PNG file
3. Creates `SkinEntry` with " (Custom)" suffix
4. Default to wide model type for custom skins
5. Hot-reload available via "Reload Skins" button

### Example
```
game_directory/
├── saves/
├── skins/
│   ├── myskin.png      # Appears as "myskin (Custom)"
│   ├── character.png   # Appears as "character (Custom)"
│   └── avatar.png      # Appears as "avatar (Custom)"
└── options.txt         # Contains: selectedSkin:myskin (Custom)
```

## Built-in Skins

The following skins are included with the game:
- steve (Wide/Slim)
- alex (Wide/Slim)
- ari (Wide/Slim)
- efe (Wide/Slim)
- kai (Wide/Slim)
- makena (Wide/Slim)
- noor (Wide/Slim)
- sunny (Wide/Slim)
- zuri (Wide/Slim)
- hunglo (Wide/Slim)

Each available in both wide and slim model variants, located in:
- `assets/minecraft/textures/entity/player/wide/<name>.png`
- `assets/minecraft/textures/entity/player/slim/<name>.png`

## Key Files Modified

### Client
- `net/minecraft/client/Minecraft.java` - Initialize SkinLoader
- `net/minecraft/client/Options.java` - Add `selectedSkin` field
- `net/minecraft/client/gui/screens/TitleScreen.java` - Add Customize button
- `net/minecraft/client/gui/screens/CustomizeScreen.java` - New skin selection UI
- `net/minecraft/client/multiplayer/ClientPacketListener.java` - Handle skin packets, upload on join
- `net/minecraft/client/multiplayer/PlayerInfo.java` - Apply selected/cached skins
- `net/minecraft/client/resources/SkinManager.java` - Offline mode, no Mojang fetching

### Server
- `net/minecraft/server/MinecraftServer.java` - Initialize PlayerSkinCache
- `net/minecraft/server/network/ServerGamePacketListenerImpl.java` - Handle skin uploads
- `net/minecraft/server/players/PlayerList.java` - Sync skins on join/leave

### Network
- `net/minecraft/network/protocol/game/GamePacketTypes.java` - Register packet types
- `net/minecraft/network/protocol/game/GameProtocols.java` - Register packet codecs
- `net/minecraft/network/protocol/game/ServerboundPlayerSkinPacket.java` - Client→Server
- `net/minecraft/network/protocol/game/ClientboundPlayerSkinPacket.java` - Server→Client
- `net/minecraft/network/protocol/game/ClientboundRemovePlayerSkinPacket.java` - Cleanup

## Benefits

1. **No Online Dependency**: Works completely offline, no authentication required
2. **Privacy**: No data sent to external servers
3. **Customization**: Players can use any custom skin without account restrictions
4. **Simplicity**: No complex authentication flows or session management
5. **Performance**: Skins cached locally, no network delays for skin fetching
6. **Decentralization**: Each server independently manages skins for connected players

## Limitations

1. **Skin Persistence**: Skins only cached while players are online
2. **Transfer Size**: Skins sent as raw PNG data (up to 32KB per player)
3. **No Validation**: Any PNG file accepted without signature verification
4. **Memory Usage**: Each connected player's skin stored in server memory
5. **Per-Server**: Skin must be re-uploaded when connecting to different servers

## Future Considerations

- Compression of skin data before network transmission
- Optional server-side skin caching to disk for frequent players
- Skin pack/bundle support for distributing themed skin collections
- Client-side caching of other players' skins across sessions
