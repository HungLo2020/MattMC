# Profanity Filter Investigation Report

## Executive Summary

This document provides a comprehensive overview of all profanity filter-related functionality found in the MattMC codebase. The profanity filter system is a Minecraft feature that allows filtering of inappropriate text content in chat messages, signs, books, and other player-generated content.

## Table of Contents

1. [Core Components](#core-components)
2. [Client-Side Implementation](#client-side-implementation)
3. [Server-Side Implementation](#server-side-implementation)
4. [Configuration](#configuration)
5. [Data Flow](#data-flow)
6. [Usage Throughout the Codebase](#usage-throughout-the-codebase)

---

## Core Components

### 1. User Flag Definition
**File:** `net/minecraft/client/auth/UserApiService.java`

The profanity filter is controlled by a user flag from Mojang's services:

```java
enum UserFlag {
    SERVERS_ALLOWED,
    REALMS_ALLOWED,
    CHAT_ALLOWED,
    TELEMETRY_ENABLED,
    PROFANITY_FILTER_ENABLED  // Line 60
}
```

**Key Details:**
- This is an enum representing user account flags
- `PROFANITY_FILTER_ENABLED` indicates whether profanity filtering is enabled for a user
- Offline mode sets this to false by default

---

## Client-Side Implementation

### 1. Main Client Check
**File:** `net/minecraft/client/Minecraft.java`

```java
public boolean isTextFilteringEnabled() {
    return this.userProperties().flag(UserFlag.PROFANITY_FILTER_ENABLED);
}
```
**Line:** 2689-2691

This method checks if text filtering is enabled based on the user's properties.

### 2. Client Options
**File:** `net/minecraft/client/Options.java`

```java
this.minecraft.isTextFilteringEnabled(),  // Line 1631
```

The text filtering state is included when building client information that gets sent to the server.

### 3. Local Player
**File:** `net/minecraft/client/player/LocalPlayer.java`

```java
@Override
public boolean isTextFilteringEnabled() {
    return this.minecraft.isTextFilteringEnabled();
}
```
**Lines:** 521-523

Local player delegates text filtering check to the Minecraft instance.

**Sign Editing:**
```java
this.minecraft.setScreen(new HangingSignEditScreen(hangingSignBlockEntity, bl, this.minecraft.isTextFilteringEnabled()));  // Line 527
this.minecraft.setScreen(new SignEditScreen(signBlockEntity, bl, this.minecraft.isTextFilteringEnabled()));  // Line 529
```

### 4. Sign Rendering
**File:** `net/minecraft/client/renderer/blockentity/AbstractSignRenderer.java`

```java
FormattedCharSequence[] formattedCharSequences = signText.getRenderMessages(
    signRenderState.isTextFilteringEnabled, component -> { ... }
);  // Line 110

signRenderState.isTextFilteringEnabled = Minecraft.getInstance().isTextFilteringEnabled();  // Line 177
```

**File:** `net/minecraft/client/renderer/blockentity/state/SignRenderState.java`
```java
public boolean isTextFilteringEnabled;  // Line 16
```

### 5. Book Editing & Viewing
**File:** `net/minecraft/client/gui/screens/inventory/BookEditScreen.java`

```java
writableBookContent.getPages(Minecraft.getInstance().isTextFilteringEnabled()).forEach(this.pages::add);  // Line 51
```

**File:** `net/minecraft/client/gui/screens/inventory/BookViewScreen.java`

```java
boolean bl = Minecraft.getInstance().isTextFilteringEnabled();  // Line 271
```

---

## Server-Side Implementation

### 1. Server Text Filter (Abstract Base)
**File:** `net/minecraft/server/network/ServerTextFilter.java`

This is the core abstract class that implements text filtering on the server side.

**Key Features:**
- Abstract base class for text filtering implementations
- Uses worker thread pool for async processing
- Connects to external filtering APIs
- Supports both chat and join/leave events

**Main Methods:**
```java
public static ServerTextFilter createFromConfig(DedicatedServerProperties dedicatedServerProperties)
public TextFilter createContext(PlayerProfile playerProfile)
protected abstract FilteredText filterText(String string, IgnoreStrategy ignoreStrategy, JsonObject jsonObject)
```

**Configuration Loading:**
```java
return switch (dedicatedServerProperties.textFilteringVersion) {
    case 0 -> LegacyTextFilter.createTextFilterFromConfig(string);
    case 1 -> PlayerSafetyServiceTextFilter.createTextFilterFromConfig(string);
    default -> {
        LOGGER.warn("Could not create text filter - unsupported text filtering version used");
        yield null;
    }
};
```
**Lines:** 78-85

**Ignore Strategies:**
- `NEVER_IGNORE` - Never ignore filtered content
- `IGNORE_FULLY_FILTERED` - Ignore when entire message is filtered
- `ignoreOverThreshold(int)` - Ignore when filter count exceeds threshold

### 2. Legacy Text Filter (Version 0)
**File:** `net/minecraft/server/network/LegacyTextFilter.java`

Implementation of the older text filtering API.

**Key Features:**
- Uses Basic Authentication
- Supports chat, join, and leave endpoints
- Configuration from JSON

**Endpoints:**
- Chat: `v1/chat` (default)
- Join: `v1/join` (default)
- Leave: `v1/leave` (default)

**Filter Response:**
```java
protected FilteredText filterText(String string, IgnoreStrategy ignoreStrategy, JsonObject jsonObject) {
    boolean bl = GsonHelper.getAsBoolean(jsonObject, "response", false);
    if (bl) {
        return FilteredText.passThrough(string);
    } else {
        String string2 = GsonHelper.getAsString(jsonObject, "hashed", null);
        if (string2 == null) {
            return FilteredText.fullyFiltered(string);
        } else {
            JsonArray jsonArray = GsonHelper.getAsJsonArray(jsonObject, "hashes");
            FilterMask filterMask = this.parseMask(string, jsonArray, ignoreStrategy);
            return new FilteredText(string, filterMask);
        }
    }
}
```
**Lines:** 169-183

### 3. Player Safety Service Text Filter (Version 1)
**File:** `net/minecraft/server/network/PlayerSafetyServiceTextFilter.java`

Modern implementation using Microsoft's Player Safety Service.

**Key Features:**
- Uses Microsoft Azure AD authentication with certificates
- OAuth 2.0 token-based authentication
- Support for "fully filtered events" - specific event types that trigger full filtering
- Configurable connection timeout

**Authentication:**
```java
private IAuthenticationResult aquireIAuthenticationResult() {
    return (IAuthenticationResult)this.client.acquireToken(this.clientParameters).join();
}

protected void setAuthorizationProperty(HttpURLConnection httpURLConnection) {
    IAuthenticationResult iAuthenticationResult = this.aquireIAuthenticationResult();
    httpURLConnection.setRequestProperty("Authorization", "Bearer " + iAuthenticationResult.accessToken());
}
```

**Filter Logic:**
```java
protected FilteredText filterText(String string, IgnoreStrategy ignoreStrategy, JsonObject jsonObject) {
    JsonObject jsonObject2 = GsonHelper.getAsJsonObject(jsonObject, "result", null);
    if (jsonObject2 == null) {
        return FilteredText.fullyFiltered(string);
    } else {
        boolean bl = GsonHelper.getAsBoolean(jsonObject2, "filtered", true);
        if (!bl) {
            return FilteredText.passThrough(string);
        } else {
            for (JsonElement jsonElement : GsonHelper.getAsJsonArray(jsonObject2, "events", new JsonArray())) {
                JsonObject jsonObject3 = jsonElement.getAsJsonObject();
                String string2 = GsonHelper.getAsString(jsonObject3, "id", "");
                if (this.fullyFilteredEvents.contains(string2)) {
                    return FilteredText.fullyFiltered(string);
                }
            }
            JsonArray jsonArray2 = GsonHelper.getAsJsonArray(jsonObject2, "redactedTextIndex", new JsonArray());
            return new FilteredText(string, this.parseMask(string, jsonArray2, ignoreStrategy));
        }
    }
}
```
**Lines:** 143-164

### 4. Text Filter Interface
**File:** `net/minecraft/server/network/TextFilter.java`

Simple interface with a DUMMY implementation that passes everything through:

```java
public interface TextFilter {
    TextFilter DUMMY = new TextFilter() {
        @Override
        public CompletableFuture<FilteredText> processStreamMessage(String string) {
            return CompletableFuture.completedFuture(FilteredText.passThrough(string));
        }

        @Override
        public CompletableFuture<List<FilteredText>> processMessageBundle(List<String> list) {
            return CompletableFuture.completedFuture((List)list.stream()
                .map(FilteredText::passThrough)
                .collect(ImmutableList.toImmutableList()));
        }
    };

    CompletableFuture<FilteredText> processStreamMessage(String string);
    CompletableFuture<List<FilteredText>> processMessageBundle(List<String> list);
}
```

### 5. Filtered Text Data Structure
**File:** `net/minecraft/server/network/FilteredText.java`

Record that holds both raw and filtered text:

```java
public record FilteredText(String raw, FilterMask mask) {
    public static final FilteredText EMPTY = passThrough("");

    public static FilteredText passThrough(String string)
    public static FilteredText fullyFiltered(String string)
    public String filtered()
    public String filteredOrEmpty()
    public boolean isFiltered()
}
```

### 6. Filter Mask
**File:** `net/minecraft/network/chat/FilterMask.java`

Represents which characters in a string should be filtered:

**Three Types:**
- `PASS_THROUGH` - No filtering
- `FULLY_FILTERED` - Entire text filtered
- `PARTIALLY_FILTERED` - Specific characters filtered (uses BitSet)

**Key Methods:**
```java
public String apply(String string)  // Applies mask, returns filtered string or null
public Component applyWithFormatting(String string)  // Returns formatted component with # for filtered chars
```

**Filtered Style:**
```java
public static final Style FILTERED_STYLE = Style.EMPTY
    .withColor(ChatFormatting.DARK_GRAY)
    .withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.filtered")));
```

### 7. Filterable Wrapper
**File:** `net/minecraft/server/network/Filterable.java`

Generic wrapper that holds both raw and filtered versions of data:

```java
public record Filterable<T>(T raw, Optional<T> filtered) {
    public static <T> Filterable<T> passThrough(T object)
    public static Filterable<String> from(FilteredText filteredText)
    public T get(boolean bl)  // Returns filtered version if bl is true
}
```

### 8. Server Player Implementation
**File:** `net/minecraft/server/level/ServerPlayer.java`

```java
@Override
public boolean isTextFilteringEnabled() {
    return this.textFilteringEnabled;
}

public boolean shouldFilterMessageTo(ServerPlayer serverPlayer) {
    return serverPlayer == this ? false : this.textFilteringEnabled || serverPlayer.textFilteringEnabled;
}
```
**Lines:** 1999-2005

### 9. Client Information Transfer
**File:** `net/minecraft/server/level/ClientInformation.java`

```java
public record ClientInformation(
    String language,
    int viewDistance,
    ChatVisiblity chatVisibility,
    boolean chatColors,
    int modelCustomisation,
    HumanoidArm mainHand,
    boolean textFilteringEnabled,  // Line 15
    boolean allowsListing,
    ParticleStatus particleStatus
)
```

This record transfers the client's text filtering preference to the server.

### 10. Server Game Packet Listener
**File:** `net/minecraft/server/network/ServerGamePacketListenerImpl.java`

```java
return this.player.isTextFilteringEnabled() 
    ? Filterable.passThrough(filteredText.filteredOrEmpty()) 
    : Filterable.from(filteredText);
```
**Line:** 968

---

## Configuration

### Server Properties
**File:** `net/minecraft/server/dedicated/DedicatedServerProperties.java`

Two configuration options in `server.properties`:

```java
public final String textFilteringConfig = this.get("text-filtering-config", "");
public final int textFilteringVersion = this.get("text-filtering-version", 0);
```
**Lines:** 108-109

**Configuration Options:**
- `text-filtering-config` - JSON configuration string for the filter
- `text-filtering-version` - Version of filter to use:
  - `0` = Legacy filter
  - `1` = Player Safety Service filter
  - Other = No filter

### Server Initialization
**File:** `net/minecraft/server/dedicated/DedicatedServer.java`

```java
this.serverTextFilter = ServerTextFilter.createFromConfig(dedicatedServerSettings.getProperties());
```
**Line:** 114

```java
return this.serverTextFilter != null 
    ? this.serverTextFilter.createContext(serverPlayer.getGameProfile()) 
    : TextFilter.DUMMY;
```
**Line:** 791

---

## Data Flow

### Client to Server Flow

1. **User Account** - Mojang services set `PROFANITY_FILTER_ENABLED` flag
2. **Client Check** - `Minecraft.isTextFilteringEnabled()` checks the flag
3. **Client Information** - Setting included in `ClientInformation` packet
4. **Server Receives** - Server stores `textFilteringEnabled` in `ServerPlayer`
5. **Message Filtering** - When messages are sent:
   - Server creates filter context for player
   - Processes message through configured filter
   - Returns `FilteredText` with mask
   - Recipients see filtered or unfiltered based on their settings

### Filter Processing Pipeline

```
Player Message 
    → ServerTextFilter.requestMessageProcessing()
    → HTTP POST to filtering API
    → Response parsed to FilterMask
    → FilteredText created
    → Stored as Filterable<String>
    → Sent to recipients
    → Client displays based on isTextFilteringEnabled()
```

---

## Usage Throughout the Codebase

### 1. Sign Blocks
**File:** `net/minecraft/world/level/block/SignBlock.java`

```java
return Arrays.stream(signText.getMessages(player.isTextFilteringEnabled()))
```
**Line:** 147

### 2. Sign Block Entity
**File:** `net/minecraft/world/level/block/entity/SignBlockEntity.java`

Multiple uses for rendering and interaction:
```java
Style style = signText.getMessage(i, player.isTextFilteringEnabled()).getStyle();  // Line 147
if (player.isTextFilteringEnabled()) { ... }  // Line 148
for (Component component : this.getText(bl).getMessages(player.isTextFilteringEnabled())) { ... }  // Line 189
```

### 3. Sign Text
**File:** `net/minecraft/world/level/block/entity/SignText.java`

```java
Arrays.stream(this.getMessages(player.isTextFilteringEnabled())).anyMatch(...)  // Line 98
for (Component component : this.getMessages(player.isTextFilteringEnabled())) { ... }  // Line 129
```

### 4. Command Source Stack
**File:** `net/minecraft/commands/CommandSourceStack.java`

```java
return serverPlayer == serverPlayer2 ? false : 
    serverPlayer2 != null && serverPlayer2.isTextFilteringEnabled() || 
    serverPlayer.isTextFilteringEnabled();
```
**Line:** 458

This checks if any player in a message exchange has filtering enabled.

### 5. Player Entity
**File:** `net/minecraft/world/entity/player/Player.java`

```java
public boolean isTextFilteringEnabled() {
    return false;  // Default implementation
}
```
**Line:** 787

### 6. Book Content Components
**Files:**
- `net/minecraft/world/item/component/BookContent.java`
- `net/minecraft/world/item/component/WritableBookContent.java`
- `net/minecraft/world/item/component/WrittenBookContent.java`

These components use the filtering system for book pages.

---

## Architecture Summary

### Client Architecture
```
UserApiService (flag source)
    ↓
Minecraft.isTextFilteringEnabled()
    ↓
LocalPlayer.isTextFilteringEnabled()
    ↓
UI Components (signs, books, chat screens)
```

### Server Architecture
```
DedicatedServerProperties (config)
    ↓
ServerTextFilter.createFromConfig()
    ↓
LegacyTextFilter OR PlayerSafetyServiceTextFilter
    ↓
TextFilter.createContext(PlayerProfile)
    ↓
Filter messages via HTTP API
    ↓
FilteredText with FilterMask
    ↓
Filterable<T> wrapper
    ↓
Recipients receive based on their settings
```

### Data Structures
```
FilterMask (character-level masking)
    ↓
FilteredText (raw + mask)
    ↓
Filterable<T> (raw + optional filtered)
```

---

## Key Observations

1. **Dual System**: The profanity filter operates on both client and server:
   - Client: Controls what the local player sees
   - Server: Processes and filters messages before distribution

2. **User Control**: The filter is user-opt-in based on Mojang account settings, not server-forced

3. **Two Implementations**: 
   - Legacy version (v0) for older filtering APIs
   - Modern version (v1) using Microsoft Player Safety Service with OAuth

4. **Partial Filtering**: The system supports partial filtering (only specific characters) using BitSet masks

5. **Performance**: Uses async processing with thread pools to avoid blocking

6. **Visual Feedback**: Filtered characters shown as `#` in dark gray with hover text "chat.filtered"

7. **Scope**: Applies to:
   - Chat messages
   - Sign text
   - Book content
   - Any player-generated text content

8. **Offline Mode**: No filtering in offline mode (default OFFLINE_PROPERTIES)

---

## Files Modified or Related

### Core Filter Files
1. `net/minecraft/client/auth/UserApiService.java` - User flag definition
2. `net/minecraft/server/network/ServerTextFilter.java` - Abstract base
3. `net/minecraft/server/network/LegacyTextFilter.java` - Version 0 implementation
4. `net/minecraft/server/network/PlayerSafetyServiceTextFilter.java` - Version 1 implementation
5. `net/minecraft/server/network/TextFilter.java` - Interface
6. `net/minecraft/server/network/FilteredText.java` - Data structure
7. `net/minecraft/server/network/Filterable.java` - Generic wrapper
8. `net/minecraft/network/chat/FilterMask.java` - Character masking

### Client Implementation
9. `net/minecraft/client/Minecraft.java` - Main check
10. `net/minecraft/client/Options.java` - Client options
11. `net/minecraft/client/player/LocalPlayer.java` - Player implementation

### Server Implementation
12. `net/minecraft/server/level/ServerPlayer.java` - Server player
13. `net/minecraft/server/level/ClientInformation.java` - Client info transfer
14. `net/minecraft/server/network/ServerGamePacketListenerImpl.java` - Packet handling
15. `net/minecraft/server/dedicated/DedicatedServer.java` - Server initialization
16. `net/minecraft/server/dedicated/DedicatedServerProperties.java` - Configuration

### UI Components
17. `net/minecraft/client/renderer/blockentity/AbstractSignRenderer.java` - Sign rendering
18. `net/minecraft/client/renderer/blockentity/state/SignRenderState.java` - Render state
19. `net/minecraft/client/gui/screens/inventory/BookEditScreen.java` - Book editing
20. `net/minecraft/client/gui/screens/inventory/BookViewScreen.java` - Book viewing

### Game Logic
21. `net/minecraft/world/level/block/SignBlock.java` - Sign block
22. `net/minecraft/world/level/block/entity/SignBlockEntity.java` - Sign entity
23. `net/minecraft/world/level/block/entity/SignText.java` - Sign text
24. `net/minecraft/world/entity/player/Player.java` - Player base
25. `net/minecraft/commands/CommandSourceStack.java` - Command filtering

---

## End of Report

This report comprehensively documents all profanity filter-related code found in the MattMC codebase as of the inspection date. The system is a sophisticated, multi-layered implementation that provides user-controlled content filtering across client and server with support for both legacy and modern filtering APIs.
