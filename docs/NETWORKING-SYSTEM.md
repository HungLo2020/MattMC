# Networking System

## Overview

Minecraft's networking system is built on top of **Netty**, providing asynchronous, event-driven network communication between clients and servers. The system uses a packet-based protocol with multiple phases and supports compression, encryption, and automatic serialization.

## Architecture

```
┌──────────────┐                    ┌──────────────┐
│    Client    │                    │    Server    │
│              │                    │              │
│ ┌──────────┐ │                    │ ┌──────────┐ │
│ │ Packet   │ │                    │ │ Packet   │ │
│ │ Listener │ │                    │ │ Listener │ │
│ └────┬─────┘ │                    │ └────┬─────┘ │
│      │       │                    │      │       │
│ ┌────▼─────┐ │   Netty Channel    │ ┌────▼─────┐ │
│ │Connection│ │◄──────────────────►│ │Connection│ │
│ │          │ │   (encrypted)      │ │          │ │
│ └────┬─────┘ │                    │ └────┬─────┘ │
│      │       │                    │      │       │
│ ┌────▼─────┐ │                    │ ┌────▼─────┐ │
│ │ Protocol │ │                    │ │ Protocol │ │
│ │ Handlers │ │                    │ │ Handlers │ │
│ └──────────┘ │                    │ └──────────┘ │
└──────────────┘                    └──────────────┘
```

## Core Components

### 1. Connection Class

**Location**: `net.minecraft.network.Connection`

Central networking class managing the Netty channel and packet flow.

**Key Responsibilities**:
- Netty channel lifecycle management
- Packet sending and receiving
- Encryption setup
- Compression setup
- Disconnect handling
- Protocol state transitions

**Key Methods**:
- `send(Packet<?>)`: Send a packet
- `disconnect(Component)`: Disconnect with message
- `setupEncryption(Cipher, Cipher)`: Enable encryption
- `setupCompression(int, boolean)`: Enable compression
- `setReadOnly()`: Transition to read-only mode

**Threading**:
- Uses Netty's event loop for async I/O
- Packet handlers execute on network thread
- Game logic must be dispatched to main thread

### 2. Protocol System

**Location**: `net.minecraft.network.protocol`

Defines the communication protocol through different connection phases.

#### Protocol Phases

**1. HANDSHAKE** (`HandshakeProtocols`)
- Initial connection phase
- Client announces intention (status check or login)
- Version compatibility check
- Packet: `ClientIntentionPacket`

**2. STATUS** (`StatusProtocols`)
- Server list ping
- MOTD retrieval
- Player count information
- Server icon
- Packets:
  - `ServerboundStatusRequestPacket`
  - `ClientboundStatusResponsePacket`
  - `ServerboundPingRequestPacket`
  - `ClientboundPongResponsePacket`

**3. LOGIN** (`LoginProtocols`)
- Player authentication
- Encryption key exchange
- Compression negotiation
- Plugin messaging
- Packets:
  - `ServerboundHelloPacket` (username)
  - `ClientboundHelloPacket` (encryption request)
  - `ServerboundKeyPacket` (encryption response)
  - `ClientboundGameProfilePacket` (login success)

**4. CONFIGURATION** (`ConfigurationProtocols`)
- Resource pack negotiation
- Registry synchronization
- Feature flag exchange
- Brand information
- Plugin channel registration
- Transition to play state

**5. GAME** (`GameProtocols`)
- Active gameplay
- Main game packets
- Entity updates
- Block changes
- Player actions
- Chat messages

#### Protocol Direction

**PacketFlow** enum:
- `SERVERBOUND`: Client → Server
- `CLIENTBOUND`: Server → Client

### 3. Packet System

**Location**: `net.minecraft.network.protocol`

All network messages are packets implementing the `Packet<T>` interface.

#### Packet Interface

```java
public interface Packet<T extends PacketListener> {
    // Handles the packet logic
    void handle(T listener);
    
    // Returns the packet type for serialization
    PacketType<? extends Packet<T>> type();
}
```

#### Packet Categories

**Game Packets** (`net.minecraft.network.protocol.game`):

**Entity Packets**:
- `ClientboundAddEntityPacket`: Spawn entity
- `ClientboundRemoveEntitiesPacket`: Despawn entities
- `ClientboundTeleportEntityPacket`: Entity teleport
- `ClientboundSetEntityDataPacket`: Entity metadata sync
- `ClientboundSetEntityMotionPacket`: Entity velocity
- `ClientboundRotateHeadPacket`: Entity head rotation
- `ClientboundMoveEntityPacket`: Entity position updates

**Block Packets**:
- `ClientboundBlockUpdatePacket`: Single block change
- `ClientboundSectionBlocksUpdatePacket`: Multi-block change
- `ClientboundBlockEntityDataPacket`: Block entity data
- `ClientboundBlockDestructionPacket`: Block breaking progress
- `ClientboundBlockEventPacket`: Block events (chest open, etc.)

**Chunk Packets**:
- `ClientboundLevelChunkWithLightPacket`: Chunk data with lighting
- `ClientboundForgetLevelChunkPacket`: Unload chunk
- `ClientboundLightUpdatePacket`: Lighting updates

**Player Action Packets**:
- `ServerboundUseItemPacket`: Right-click
- `ServerboundPlayerActionPacket`: Left-click, start/stop digging
- `ServerboundMovePlayerPacket`: Position/rotation updates
- `ServerboundInteractPacket`: Entity interaction
- `ServerboundSetCarriedItemPacket`: Hotbar selection

**Inventory Packets**:
- `ClientboundContainerSetContentPacket`: Full inventory sync
- `ClientboundContainerSetSlotPacket`: Single slot update
- `ServerboundContainerClickPacket`: Inventory click
- `ServerboundContainerClosePacket`: Close inventory

**Chat Packets**:
- `ServerboundChatPacket`: Player chat message
- `ClientboundPlayerChatPacket`: Broadcast chat message
- `ClientboundSystemChatPacket`: System message
- `ClientboundDisguisedChatPacket`: Chat with hidden sender

**Command Packets**:
- `ServerboundChatCommandPacket`: Execute command
- `ClientboundCommandsPacket`: Command tree sync
- `ClientboundCommandSuggestionsPacket`: Tab completion

### 4. Packet Serialization

**Location**: `net.minecraft.network.codec`

Packets are serialized using a codec system.

#### StreamCodec

Generic serialization interface for network buffers:

```java
public interface StreamCodec<B, V> {
    void encode(B buffer, V value);
    V decode(B buffer);
}
```

**FriendlyByteBuf**: Main buffer implementation
- Wraps Netty's ByteBuf
- Provides type-safe read/write methods
- Handles common types (int, string, UUID, BlockPos, etc.)
- Variable-length integer encoding (VarInt)
- Registry-based serialization

**Common Data Types**:
- Primitives: byte, short, int, long, float, double
- VarInt/VarLong: Variable-length integers
- String: Length-prefixed UTF-8
- UUID: Two longs
- BlockPos: Packed long (x, y, z)
- ItemStack: Complex serialization with NBT
- Component: JSON text component

### 5. Entity Data Synchronization

**Location**: `net.minecraft.network.syncher`

Automatic entity data synchronization system.

**SynchedEntityData**:
- Tracks entity data (health, armor, name, etc.)
- Detects changes
- Sends delta updates
- Efficient bandwidth usage

**EntityDataAccessor**:
- Type-safe data accessor
- Defined per entity type
- Examples:
  - Health (float)
  - Name (Component)
  - Flags (byte bitfield)
  - Pose (enum)

**EntityDataSerializers**:
- Built-in serializers for common types
- Custom serializers for game types

### 6. Encryption

**Location**: Connection class, encryption package

Minecraft uses AES/CFB8 encryption after login phase.

**Process**:
1. Server generates temporary RSA keypair (1024-bit)
2. Server sends public key to client
3. Client generates 128-bit AES shared secret
4. Client encrypts secret with server's public key
5. Server decrypts to obtain shared secret
6. Both enable AES/CFB8 encryption on connection
7. All subsequent packets are encrypted

**Mojang Authentication**:
- Client authenticates with Mojang/Microsoft servers
- Server verifies with Mojang session servers
- Prevents impersonation

### 7. Compression

Packets can be compressed using zlib to reduce bandwidth.

**Threshold**:
- Configured during login phase
- Packets larger than threshold are compressed
- Smaller packets sent uncompressed (overhead)

**Implementation**:
- Uses Java's Inflater/Deflater
- Netty pipeline handlers for transparent compression

### 8. Network Pipeline

**Location**: Connection initialization

Netty ChannelPipeline handlers in order:

1. **Splitter**: Separates packet frames (VarInt length prefix)
2. **Decoder**: Deserializes packets from bytes
3. **Prepender**: Adds length prefix to outgoing packets
4. **Encoder**: Serializes packets to bytes
5. **FlowControlHandler**: Prevents channel overflow
6. **PacketHandler**: Dispatches to packet listeners

**Optional Handlers**:
- **Encryptor/Decryptor**: Added after encryption setup
- **Compressor/Decompressor**: Added after compression setup

### 9. Packet Listeners

**Location**: `net.minecraft.network.protocol.*`

Packet listeners handle incoming packets for different phases.

**Client-Side Listeners**:
- `ClientHandshakePacketListener`: Handshake
- `ClientStatusPacketListener`: Status
- `ClientLoginPacketListener`: Login
- `ClientConfigurationPacketListener`: Configuration
- `ClientGamePacketListener`: Game

**Server-Side Listeners**:
- `ServerHandshakePacketListener`: Handshake
- `ServerStatusPacketListener`: Status  
- `ServerLoginPacketListener`: Login
- `ServerConfigurationPacketListener`: Configuration
- `ServerGamePacketListener`: Game

**Implementation**:
Each listener has methods corresponding to packets it handles.

### 10. Connection States

**State Machine**:

```
[HANDSHAKE] ─┬─► [STATUS] ─► [Disconnect]
             │
             └─► [LOGIN] ─► [CONFIGURATION] ─► [GAME] ─► [Disconnect]
```

**State Transitions**:
- Handshake determines next state (status or login)
- Cannot go backwards in state machine
- Invalid packets for current state cause disconnect

## Network Threads

**Client**:
- **Main Thread**: Game logic, rendering
- **Netty Client I/O**: Network operations
- Packets queued to main thread for processing

**Server**:
- **Main Thread**: World ticking, most game logic
- **Netty Server Boss**: Accept connections
- **Netty Server Worker**: Network I/O per connection
- Packets queued to main thread for processing

**Thread Safety**:
- Most game logic is NOT thread-safe
- Network handlers must schedule work on main thread
- Use `server.execute()` or `Minecraft.getInstance().execute()`

## Packet Bundling

**Location**: `net.minecraft.network.protocol.BundlerInfo`

Combines multiple packets into bundles for atomic delivery.

**Use Cases**:
- Entity spawn with metadata
- Chunk with block entities
- Ensures related packets arrive together

## Plugin Channels

**Location**: `net.minecraft.network.protocol.common`

Custom packet channels for mods/plugins.

**ServerboundCustomPayloadPacket**:
- Custom packet type
- Identified by ResourceLocation
- FriendlyByteBuf payload
- Bidirectional

**Common Uses**:
- Mod communication
- Client-server feature negotiation
- Custom data transfer

## Cookie System

**Location**: `net.minecraft.network.protocol.cookie`

Stores small data on client, readable by server.

**Use Cases**:
- Client preferences
- Session data
- State preservation across reconnects

## Performance Considerations

**Optimization Techniques**:

1. **Delta Encoding**: Only send changed data
2. **Batching**: Combine multiple updates
3. **Compression**: Reduce bandwidth for large packets
4. **Priority**: Critical packets sent first
5. **Throttling**: Rate limit non-critical updates

**Bandwidth Management**:
- Entity tracking distance limits
- Chunk view distance
- Tick-based entity updates
- Position delta compression (VecDeltaCodec)

**Network Metrics**:
- Packets sent/received per second
- Average packet size
- Compression ratio
- Network latency

## Error Handling

**Disconnect Reasons**:
- Protocol errors
- Timeout (30 seconds default)
- Kicked by server
- Client quit
- Invalid packet
- Internal error

**DisconnectionDetails**:
- Reason component
- Optional report
- Bug tracker link

## Key Files

- `Connection.java`: Core networking class (600+ lines)
- `Packet.java`: Base packet interface
- `FriendlyByteBuf.java`: Packet serialization buffer
- `ProtocolInfo.java`: Protocol phase management
- `SynchedEntityData.java`: Entity data synchronization
- Protocol packages: `protocol/game/`, `protocol/login/`, etc.

## Common Patterns

**Sending a Packet (Client)**:
```java
Minecraft.getInstance().getConnection().send(packet);
```

**Sending a Packet (Server)**:
```java
serverPlayer.connection.send(packet);
```

**Handling Packets**:
```java
public void handle(ServerGamePacketListener listener) {
    listener.handleCustomPayload(this);
}
```

**Thread-Safe Processing**:
```java
public void handle(ServerGamePacketListener listener) {
    listener.server.execute(() -> {
        // Process on main thread
    });
}
```

## Related Systems

- [Server System](SERVER-SYSTEM.md) - Server-side networking
- [Entity System](ENTITY-SYSTEM.md) - Entity data synchronization
- [Command System](COMMAND-SYSTEM.md) - Command packets
