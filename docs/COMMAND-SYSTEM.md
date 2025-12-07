# Command System

## Overview

Minecraft's command system is built on **Brigadier**, Mojang's command parsing library. The system provides a powerful, type-safe command framework with argument parsing, validation, tab completion, and permission checking.

## Architecture

```
┌─────────────────────────────────────────┐
│         Player Input                    │
│         "/give @p diamond 64"           │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│         Brigadier Parser                │
│  - Tokenize command                     │
│  - Match command tree                   │
│  - Parse arguments                      │
│  - Validate syntax                      │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│      Command Execution Context          │
│  - CommandSourceStack (executor)        │
│  - Parsed arguments                     │
│  - Permission level                     │
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│      Command Implementation             │
│  - Execute command logic                │
│  - Return success/failure               │
│  - Send feedback messages               │
└─────────────────────────────────────────┘
```

## Core Components

### 1. Brigadier Library

**Package**: `com.mojang.brigadier`

Third-party command parsing framework developed by Mojang.

**Key Classes**:
- `CommandDispatcher`: Command registry and executor
- `LiteralArgumentBuilder`: Literal command nodes ("give", "teleport")
- `RequiredArgumentBuilder`: Argument nodes (player, item, position)
- `CommandContext`: Execution context with arguments
- `ArgumentType`: Argument parsers and validators

**Command Tree Structure**:
```
root
├─ give (literal)
│  ├─ <targets> (entity argument)
│  │  ├─ <item> (item argument)
│  │  │  └─ [<count>] (integer argument)
│  │  │     └─ [execute]
├─ teleport (literal)
│  ├─ <target> (entity argument)
│  │  └─ <location> (vec3 argument)
│  │     └─ [execute]
└─ ...
```

### 2. Commands Class

**Location**: `net.minecraft.commands.Commands`

Central command registration and management.

**Key Responsibilities**:
- Register all built-in commands
- Build command tree
- Handle command execution
- Manage command permissions
- Environment-specific registration (client vs server)

**Command Environment**:
- `ALL`: Available everywhere
- `DEDICATED`: Dedicated server only
- `INTEGRATED`: Singleplayer/LAN only

**Permission Levels**:
- **0**: All players (no commands)
- **1**: Bypass spawn protection
- **2**: Most commands (operators)
- **3**: Player management
- **4**: Server management (stop, save-all)

**Registration Example**:
```java
public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("give")
        .requires(source -> source.hasPermission(2))
        .then(Commands.argument("targets", EntityArgument.players())
            .then(Commands.argument("item", ItemArgument.item(context))
                .executes(context -> {
                    return giveItem(context.getSource(), 
                        EntityArgument.getPlayers(context, "targets"),
                        ItemArgument.getItem(context, "item"), 1);
                })
                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                    .executes(context -> {
                        return giveItem(context.getSource(),
                            EntityArgument.getPlayers(context, "targets"),
                            ItemArgument.getItem(context, "item"),
                            IntegerArgumentType.getInteger(context, "count"));
                    })
                )
            )
        );
    dispatcher.register(builder);
}
```

### 3. CommandSourceStack

**Location**: `net.minecraft.commands.CommandSourceStack`

Represents the command executor with context.

**Properties**:
- **Source**: Who/what executed the command (player, console, command block)
- **Position**: Execution position (for relative coordinates)
- **Rotation**: Execution rotation (for relative angles)
- **Level**: World where command executes
- **Permission Level**: Authorization level
- **DisplayName**: Name shown in feedback
- **Server**: Server instance

**Key Methods**:
- `hasPermission(int)`: Check permission level
- `sendSuccess()`: Send success message
- `sendFailure()`: Send error message
- `getEntity()`: Get executor entity (if any)
- `getPlayerOrException()`: Get player executor or throw
- `getLevel()`: Get current level/world

**Source Types**:
- Player entity
- Server console
- Command block
- Function file
- Remote console (RCON)
- Data command execution

### 4. Argument Types

**Location**: `net.minecraft.commands.arguments`

Type-safe argument parsers.

#### Built-in Argument Types

**Entity Arguments**:
- `EntityArgument.player()`: Single player
- `EntityArgument.players()`: Multiple players
- `EntityArgument.entity()`: Single entity
- `EntityArgument.entities()`: Multiple entities

**Entity Selectors**:
- `@p`: Nearest player
- `@a`: All players
- `@r`: Random player
- `@e`: All entities
- `@s`: Self (executor)
- `@e[type=cow]`: All cows
- `@a[distance=..10]`: Players within 10 blocks

**Position Arguments**:
- `Vec3Argument.vec3()`: 3D coordinate (x, y, z)
- `Vec2Argument.vec2()`: 2D coordinate (x, z)
- `BlockPosArgument.blockPos()`: Block position
- `ColumnPosArgument.columnPos()`: Column (x, z)

**Coordinate Types**:
- Absolute: `100 64 200`
- Relative: `~5 ~-2 ~`
- Local: `^1 ^2 ^3` (relative to rotation)
- Mixed: `100 ~ ~5`

**Resource Arguments**:
- `ItemArgument.item()`: Item type
- `BlockStateArgument.block()`: Block type
- `EntityTypeArgument.entity()`: Entity type
- `ResourceLocationArgument.id()`: Namespaced ID
- `ResourceKeyArgument.key()`: Registry key

**Identifier Arguments**:
- `EntityAnchorArgument.anchor()`: Entity attachment point
- `ObjectiveArgument.objective()`: Scoreboard objective
- `TeamArgument.team()`: Scoreboard team
- `SlotArgument.slot()`: Inventory slot
- `OperationArgument.operation()`: Math operation

**Text Arguments**:
- `ComponentArgument.textComponent()`: JSON text
- `MessageArgument.message()`: Chat message
- `StringArgumentType.string()`: Simple string
- `StringArgumentType.word()`: Single word
- `StringArgumentType.greedyString()`: Rest of line

**Numeric Arguments**:
- `IntegerArgumentType.integer()`: Integer
- `FloatArgumentType.floatArg()`: Float
- `DoubleArgumentType.doubleArg()`: Double
- With optional min/max bounds

**Special Arguments**:
- `TimeArgument.time()`: Time duration (20t, 1s, 1d)
- `AngleArgument.angle()`: Rotation angle
- `ParticleArgument.particle()`: Particle type
- `GameProfileArgument.gameProfile()`: Player profile
- `UuidArgument.uuid()`: UUID

#### Custom Argument Types

Can create custom argument types by implementing `ArgumentType<T>`:

```java
public class MyArgument implements ArgumentType<MyType> {
    @Override
    public MyType parse(StringReader reader) throws CommandSyntaxException {
        // Parse from string
    }
    
    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(
        CommandContext<S> context, 
        SuggestionsBuilder builder
    ) {
        // Provide tab completions
    }
}
```

### 5. Command Execution

**Return Values**:
- `Command.SINGLE_SUCCESS` (1): Success
- `0`: Failure
- `> 1`: Success with count (affected entities, etc.)

**Exceptions**:
- `CommandSyntaxException`: Parse error
- `Dynamic exceptions`: Runtime errors

**Execution Flow**:
1. Parse command string
2. Build argument context
3. Check permissions
4. Validate arguments
5. Execute command
6. Return result
7. Send feedback

### 6. Built-in Commands

**Location**: `net.minecraft.server.commands`

Over 80 built-in commands.

#### Player Commands (Permission 0-1)

**Basic**:
- `/help`: Command help
- `/me`: Emote message
- `/msg`, `/tell`, `/w`: Private message
- `/trigger`: Trigger objectives

#### Operator Commands (Permission 2)

**Game Management**:
- `/gamemode`: Change game mode
- `/difficulty`: Set difficulty
- `/defaultgamemode`: Default for new players
- `/gamerule`: Modify game rules
- `/weather`: Control weather
- `/time`: Set time of day

**Player Management**:
- `/give`: Give items
- `/clear`: Clear inventory
- `/effect`: Apply effects
- `/enchant`: Enchant items
- `/experience`, `/xp`: Grant XP
- `/teleport`, `/tp`: Teleport entities

**World Management**:
- `/fill`: Fill region with blocks
- `/clone`: Copy region
- `/setblock`: Place block
- `/summon`: Spawn entity
- `/kill`: Kill entities
- `/seed`: Show world seed

**Advanced**:
- `/execute`: Complex conditional execution
- `/function`: Run function files
- `/data`: Modify NBT data
- `/scoreboard`: Scoreboard management
- `/bossbar`: Boss bar management
- `/team`: Team management

#### Server Commands (Permission 3-4)

**Player Control**:
- `/kick`: Kick player
- `/ban`, `/ban-ip`: Ban players
- `/pardon`, `/pardon-ip`: Unban players
- `/op`, `/deop`: Operator status
- `/whitelist`: Whitelist management

**Server Control**:
- `/stop`: Stop server
- `/save-all`, `/save-on`, `/save-off`: World saving
- `/list`: List players
- `/publish`: Open to LAN

### 7. Execute Command

**Location**: `ExecuteCommand.java`

Most powerful command, allows conditional execution.

**Subcommands**:

**Conditions**:
- `if/unless entity <selector>`: Entity exists
- `if/unless block <pos> <block>`: Block check
- `if/unless score`: Scoreboard comparison
- `if/unless data`: NBT data check
- `if/unless predicate`: Loot predicate
- `if/unless biome`: Biome check
- `if/unless dimension`: Dimension check

**Modifications**:
- `as <selector>`: Change executor
- `at <selector>`: Change position
- `positioned <pos>`: Set position
- `rotated <rot>`: Set rotation
- `facing <pos>`: Face position
- `align <axes>`: Align to block grid
- `anchored <anchor>`: Change anchor point
- `in <dimension>`: Change dimension

**Execution**:
- `run <command>`: Execute command
- `store result/success`: Store result in score/data

**Example**:
```
/execute as @a[scores={health=..10}] at @s if block ~ ~-1 ~ minecraft:diamond_block run effect give @s minecraft:instant_health 1 10
```
Translation: For all players with health ≤ 10, standing on diamond blocks, give instant health.

### 8. Tab Completion

Brigadier provides intelligent tab completion.

**Completion Types**:
- Literal command names
- Entity selectors
- Player names
- Block types
- Item types
- Coordinates (with current position hint)
- Enum values
- Custom suggestions

**Suggestion Sources**:
- Static lists (block types, items)
- Dynamic queries (player names)
- Context-aware (position from executor)
- Custom suggestion providers

### 9. Functions

**Location**: `.mcfunction` files in datapacks

Text files containing multiple commands.

**Features**:
- One command per line
- Comments with `#`
- No `/` prefix needed
- Can call other functions
- Executed as command source

**Example** (`give_starter_items.mcfunction`):
```
# Give starter items to player
give @s minecraft:stone_sword
give @s minecraft:bread 16
effect give @s minecraft:resistance 60 1
```

**Execution**:
```
/function namespace:path/to/function
```

### 10. Command Blocks

**Types**:
- **Impulse**: Execute once when powered
- **Chain**: Execute after previous in chain
- **Repeat**: Execute every tick when powered

**Modes**:
- **Needs Redstone**: Requires power
- **Always Active**: Always executes

**Settings**:
- Command text
- Success count output
- Previous output
- Track output (show in chat)
- Conditional (only if previous succeeded)

## Command Syntax

**General Format**:
```
/command [argument1] [argument2] ...
```

**Optional Arguments**: `[arg]`
**Required Arguments**: `<arg>`
**Literal Choices**: `option1|option2`

## Error Handling

**CommandSyntaxException**:
- Unknown command
- Invalid arguments
- Missing arguments
- Permission denied
- Execution failure

**Error Messages**:
- Red text
- Hover for more info
- Suggestions for corrections

## Performance Considerations

**Optimization**:
1. Cache command tree
2. Lazy parsing
3. Async tab completion
4. Limit recursion depth
5. Rate limit command blocks

**Command Block Limits**:
- Maximum command length
- Recursion depth limits
- Tick budget

## Key Files

- `Commands.java`: Command registration (700+ lines)
- `CommandSourceStack.java`: Execution context (500+ lines)
- `ExecuteCommand.java`: Execute command (1,300+ lines)
- Argument classes in `net.minecraft.commands.arguments`
- Command implementations in `net.minecraft.server.commands`

## Security Considerations

**Permission System**:
- Prevent unauthorized access
- Level-based restrictions
- Operator management

**Input Validation**:
- Argument type checking
- Range validation
- Entity selector limits

**Command Limits**:
- Recursion depth
- Execution time
- Rate limiting

## Related Systems

- [Server System](SERVER-SYSTEM.md) - Command execution
- [Networking System](NETWORKING-SYSTEM.md) - Command packets
- [Data System](DATA-SYSTEM.md) - Function files
- [Entity System](ENTITY-SYSTEM.md) - Entity selectors
