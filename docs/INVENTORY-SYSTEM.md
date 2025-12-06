# Inventory System

## Overview

The inventory system manages item storage, manipulation, and interaction across players, chests, furnaces, and other containers. It includes crafting, enchanting, trading, and item property systems.

## Architecture

```
┌─────────────────────────────────────────┐
│            Item Stack                   │
│  (Item type + count + components)       │
└──────────────┬──────────────────────────┘
               │
       ┌───────┴────────┐
       ▼                ▼
┌──────────────┐  ┌──────────────┐
│  Container   │  │  Equipment   │
│  (Storage)   │  │  (Worn/Held) │
└──────┬───────┘  └──────┬───────┘
       │                 │
       └────────┬────────┘
                ▼
        ┌───────────────┐
        │     Menu      │
        │  (UI + Logic) │
        └───────────────┘
```

## Core Components

### 1. ItemStack

**Location**: `net.minecraft.world.item.ItemStack`

Represents a stack of items with properties.

**Key Components**:
- **Item**: The item type (diamond, sword, etc.)
- **Count**: Stack size (1-64 typically)
- **Components**: Data attached to stack
  - Damage (durability)
  - Enchantments
  - Custom name
  - Lore
  - Attributes
  - NBT data

**Key Methods**:
- `isEmpty()`: Check if stack is empty
- `is(Item)`: Check item type
- `getCount()`: Get stack size
- `grow(int)`: Increase count
- `shrink(int)`: Decrease count
- `copy()`: Duplicate stack
- `split(int)`: Split stack
- `hurtAndBreak()`: Apply durability damage
- `getMaxDamage()`: Get durability
- `isEnchanted()`: Has enchantments
- `getHoverName()`: Display name

**Data Components** (`net.minecraft.world.item.component`):
Modern system for item data (replaces much NBT).

**Common Components**:
- `DAMAGE`: Durability damage
- `MAX_DAMAGE`: Maximum durability
- `UNBREAKABLE`: Cannot break
- `CUSTOM_NAME`: Custom name
- `LORE`: Flavor text lines
- `ENCHANTMENTS`: Enchantment list
- `STORED_ENCHANTMENTS`: Book enchantments
- `ATTRIBUTE_MODIFIERS`: Stat modifiers
- `DYED_COLOR`: Leather armor color
- `CONTAINER`: Contents (shulker boxes)
- `BUNDLE_CONTENTS`: Bundle contents
- `POTION_CONTENTS`: Potion effects
- `SUSPICIOUS_STEW_EFFECTS`: Stew effects
- `WRITABLE_BOOK_CONTENT`: Book pages
- `WRITTEN_BOOK_CONTENT`: Signed book
- `TOOL`: Tool properties
- `FOOD`: Food properties
- `FIREWORK_EXPLOSION`: Firework effects
- `FIREWORKS`: Firework properties
- `TRIM`: Armor trim
- `CUSTOM_MODEL_DATA`: Model override

### 2. Item Class

**Location**: `net.minecraft.world.item.Item`

Base class for all item types.

**Key Properties**:
- Max stack size (default 64)
- Max damage (durability)
- Rarity (common, uncommon, rare, epic)
- Craftable remainder (bucket → empty bucket)
- Food properties
- Enchantability

**Key Methods**:
- `use()`: Right-click in air
- `useOn()`: Right-click on block
- `finishUsingItem()`: Finish eating/drinking
- `releaseUsing()`: Release bow
- `inventoryTick()`: Called each tick in inventory
- `onCraftedBy()`: Called when crafted
- `getDefaultInstance()`: New ItemStack
- `isValidRepairItem()`: Check repair material

**Item Categories**:
- Tools (pickaxe, axe, shovel, hoe)
- Weapons (sword, trident, bow, crossbow)
- Armor (helmet, chestplate, leggings, boots)
- Food
- Blocks (block item representation)
- Potions and effects
- Decorative
- Miscellaneous

### 3. Container

**Location**: `net.minecraft.world.Container`

Interface for item storage.

**Key Methods**:
- `getContainerSize()`: Number of slots
- `isEmpty()`: Check if empty
- `getItem(slot)`: Get item in slot
- `removeItem(slot, count)`: Remove items
- `removeItemNoUpdate(slot)`: Remove without notification
- `setItem(slot, stack)`: Set slot contents
- `clearContent()`: Empty all slots
- `stillValid(player)`: Check access permission

**Container Implementations**:

**SimpleContainer**:
- Basic array-based storage
- Used for temporary containers
- Configurable size

**CompoundContainer**:
- Combines multiple containers
- Example: Double chest (2 chests combined)

**WorldlyContainer**:
- Automation-aware container
- Defines accessible slots per side
- Furnaces, hoppers, etc.

### 4. Menu System

**Location**: `net.minecraft.world.inventory`

Server-side container UI logic.

#### AbstractContainerMenu

Base class for all container menus.

**Responsibilities**:
- Track container slots
- Handle slot clicks
- Validate actions
- Sync with client
- Detect changes

**Key Methods**:
- `addSlot()`: Register slot
- `clicked()`: Handle click
- `quickMoveStack()`: Shift-click
- `removed()`: Close menu
- `stillValid()`: Check if usable
- `broadcastChanges()`: Sync to client

**Built-in Menus**:
- `InventoryMenu`: Player inventory
- `ChestMenu`: Chest/double chest
- `HopperMenu`: Hopper
- `FurnaceMenu`: Furnace/smoker/blast furnace
- `BrewingStandMenu`: Brewing stand
- `EnchantmentMenu`: Enchanting table
- `AnvilMenu`: Anvil
- `BeaconMenu`: Beacon
- `CartographyTableMenu`: Cartography table
- `GrindstoneMenu`: Grindstone
- `LoomMenu`: Loom
- `SmithingMenu`: Smithing table
- `StonecutterMenu`: Stonecutter
- `MerchantMenu`: Trading
- `CraftingMenu`: Crafting table
- `ShulkerBoxMenu`: Shulker box

#### Slot System

**Location**: `net.minecraft.world.inventory.Slot`

Represents a single slot in a menu.

**Properties**:
- Container reference
- Slot index
- X/Y position (for rendering)

**Slot Types**:

**Regular Slot**: Normal storage
**FurnaceFuelSlot**: Only accepts fuel
**FurnaceResultSlot**: Output only
**ResultSlot**: Crafting output
**ArmorSlot**: Specific armor type
**EquipmentSlot**: Specific equipment

**Custom Slot Logic**:
- `mayPlace()`: Check if item allowed
- `mayPickup()`: Check if can take
- `onTake()`: When item taken
- `getMaxStackSize()`: Max stack in slot

### 5. Player Inventory

**Location**: `net.minecraft.world.entity.player.Inventory`

Player-specific inventory (36 slots + armor + offhand).

**Layout**:
- Slots 0-8: Hotbar (9 slots)
- Slots 9-35: Main inventory (27 slots)
- Slots 36-39: Armor (4 slots: feet, legs, chest, head)
- Slot 40: Offhand

**Key Methods**:
- `getSelected()`: Current hotbar item
- `add(ItemStack)`: Add item to inventory
- `getFreeSlot()`: Find empty slot
- `removeItem(ItemStack)`: Remove matching items
- `contains(ItemStack)`: Check if contains item
- `clearOrCountMatchingItems()`: Count/clear items
- `dropAll()`: Drop all items
- `save()`: Serialize to NBT
- `load()`: Deserialize from NBT

**Armor Slots**:
```java
public static final int SLOT_FEET = 0;
public static final int SLOT_LEGS = 1;
public static final int SLOT_CHEST = 2;
public static final int SLOT_HEAD = 3;
```

### 6. Crafting System

**Location**: `net.minecraft.world.item.crafting`

Recipe definition and execution.

#### Recipe Interface

```java
public interface Recipe<C extends RecipeInput> {
    boolean matches(C input, Level level);
    ItemStack assemble(C input, HolderLookup.Provider registries);
    ItemStack getResultItem(HolderLookup.Provider registries);
    RecipeType<?> getType();
}
```

#### Recipe Types

**CraftingRecipe**:
- Shaped: Fixed pattern
- Shapeless: Any arrangement
- Special: Custom logic (fireworks, books)

**SmeltingRecipe**:
- Furnace recipes
- Input, output, XP, time

**BlastingRecipe**:
- Blast furnace (faster, ores only)

**SmokingRecipe**:
- Smoker (faster, food only)

**CampfireCookingRecipe**:
- Campfire cooking

**StonecuttingRecipe**:
- Stonecutter recipes

**SmithingTransformRecipe**:
- Smithing table (netherite)

**SmithingTrimRecipe**:
- Armor trims

#### CraftingContainer

Grid for crafting:
- 2x2 (player inventory)
- 3x3 (crafting table)

**Key Methods**:
- `getWidth()`, `getHeight()`: Grid size
- `getItems()`: All items in grid
- `fillStackedContents()`: Ingredient check

#### RecipeManager

**Location**: `net.minecraft.world.item.crafting.RecipeManager`

Central recipe registry.

**Key Methods**:
- `getRecipeFor()`: Find matching recipe
- `getAllRecipesFor()`: All recipes of type
- `byKey()`: Get by ID

**Recipe Loading**:
- Loaded from data packs
- JSON format
- Cached for performance

### 7. Enchantment System

**Location**: `net.minecraft.world.item.enchantment`

Enchantments modify item behavior.

#### Enchantment Class

**Properties**:
- Rarity (common to very rare)
- Category (weapon, armor, tool, etc.)
- Min/max level
- Compatibility rules

**Common Enchantments**:
- **Protection**: Damage reduction
- **Sharpness**: Damage increase
- **Efficiency**: Mine faster
- **Fortune**: More drops
- **Silk Touch**: Block itself drops
- **Unbreaking**: Durability
- **Mending**: Repair with XP
- **Infinity**: Infinite arrows
- **Fire Aspect**: Set on fire
- **Knockback**: Push away
- **Looting**: More mob drops
- **Respiration**: Underwater breathing
- **Depth Strider**: Swim faster
- **Frost Walker**: Walk on water

#### EnchantmentHelper

**Location**: `net.minecraft.world.item.enchantment.EnchantmentHelper`

Utility for enchantment operations.

**Key Methods**:
- `getEnchantments()`: Get all enchantments
- `setEnchantments()`: Set enchantments
- `getItemEnchantmentLevel()`: Get enchantment level
- `enchantItem()`: Apply random enchantments
- `getDamageProtection()`: Calculate protection
- `getDamageBonus()`: Calculate damage bonus
- `getKnockbackBonus()`: Calculate knockback
- `getFireAspect()`: Get fire duration

### 8. Trading System

**Location**: `net.minecraft.world.item.trading`

Villager and wandering trader system.

#### MerchantOffer

Represents a trade offer.

**Components**:
- Base cost (primary item)
- Additional cost (optional secondary)
- Result item
- Uses (times traded)
- Max uses
- XP reward
- Price multiplier
- Demand
- Special price (reputation discount)

**Example**:
```
24 Emeralds → 1 Diamond Chestplate
```

#### Merchant Interface

```java
public interface Merchant {
    MerchantOffers getOffers();
    void notifyTrade(MerchantOffer offer);
    void notifyTradeUpdated(ItemStack stack);
    Level getLevel();
}
```

**Implementations**:
- Villager
- Wandering Trader

#### Villager Profession Trades

**Location**: `net.minecraft.world.entity.npc.VillagerTrades`

Defines trades for each profession level.

**Trade Levels**:
1. Novice (white badge)
2. Apprentice (iron badge)
3. Journeyman (gold badge)
4. Expert (emerald badge)
5. Master (diamond badge)

**Trade Refresh**:
- Twice per day (villager schedule)
- Restocks uses
- New trades at level up

### 9. Item Properties

**Location**: `net.minecraft.world.item.properties`

Dynamic item property system.

**Property Types**:

**Numeric Properties**:
- `minecraft:damage`: Damage value
- `minecraft:lefthanded`: Left-handed model
- `minecraft:cooldown`: Cooldown progress
- `minecraft:time`: World time
- `minecraft:custom_model_data`: Custom model

**Select Properties**:
- `minecraft:using_item`: Is using
- `minecraft:broken`: Is broken
- `minecraft:blocking`: Is blocking
- `minecraft:pulled`: Bow pull amount
- `minecraft:throwing`: Trident charging

**Conditional Properties**:
- Context-based values
- Used for model switching

### 10. Special Container Systems

#### Bundle

Stores multiple item types in one slot.

**Features**:
- Max 64 total items (scaled by stack size)
- Displays contents
- FIFO removal

#### Shulker Box

Portable storage that keeps contents when broken.

**Features**:
- 27 slots
- Retains items
- Dyeable
- Cannot place in shulker boxes (no recursion)

#### Ender Chest

Personal dimensional storage.

**Features**:
- Shared across all ender chests
- Player-specific
- Not dropped on death

### 11. Item Entity

**Location**: `net.minecraft.world.entity.item.ItemEntity`

Dropped items in the world.

**Properties**:
- ItemStack
- Age (despawns at 6000 ticks = 5 minutes)
- Pickup delay (2.5 seconds after drop)
- Thrower UUID
- Motion/velocity

**Behavior**:
- Physics simulation
- Merges with nearby same items
- Can be picked up by players
- Burns in lava/fire
- Affected by water current

### 12. Hopper System

**Location**: `net.minecraft.world.level.block.entity.HopperBlockEntity`

Automatic item transfer.

**Features**:
- Pulls from container above
- Pushes to container below/side
- Transfers one item per 8 ticks
- Can be disabled with redstone
- Minecart with hopper variant

**Transfer Logic**:
1. Check cooldown
2. Pull from above if possible
3. Push to output if possible
4. Set cooldown (8 ticks)

## Click Actions

**Menu Click Types**:
- `PICKUP`: Left/right click
- `QUICK_MOVE`: Shift-click
- `SWAP`: Number key (swap with hotbar)
- `CLONE`: Middle click (creative)
- `THROW`: Q key (drop)
- `QUICK_CRAFT`: Drag click
- `PICKUP_ALL`: Double-click

**Click Processing**:
1. Validate click
2. Update slots
3. Sync to client
4. Detect changes
5. Trigger callbacks

## Network Synchronization

**Packet Flow**:

**Server → Client**:
- `ClientboundContainerSetContentPacket`: Full sync
- `ClientboundContainerSetSlotPacket`: Single slot
- `ClientboundContainerSetDataPacket`: Container data (furnace progress)

**Client → Server**:
- `ServerboundContainerClickPacket`: Slot click
- `ServerboundContainerClosePacket`: Close container
- `ServerboundContainerButtonClickPacket`: Button click

**State ID**:
- Tracks synchronization state
- Prevents desync issues
- Validates click packets

## Performance Considerations

**Optimization Techniques**:
1. Batch slot updates
2. Lazy container searches
3. Cache recipe lookups
4. Minimize NBT operations
5. Delta synchronization

**Common Pitfalls**:
- Excessive slot updates
- Unoptimized container scans
- Missing stillValid() checks
- Recipe lookup in tight loops

## Key Files

- `ItemStack.java`: Item stack representation (1,000+ lines)
- `Item.java`: Base item class (600+ lines)
- `Container.java`: Container interface
- `AbstractContainerMenu.java`: Menu base (800+ lines)
- `Inventory.java`: Player inventory (500+ lines)
- `RecipeManager.java`: Recipe system
- `EnchantmentHelper.java`: Enchantment utilities (800+ lines)
- `HopperBlockEntity.java`: Hopper logic (600+ lines)

## Common Patterns

**Adding Item to Inventory**:
```java
if (!player.getInventory().add(itemStack)) {
    player.drop(itemStack, false);  // Drop if full
}
```

**Checking Item in Hand**:
```java
ItemStack held = player.getMainHandItem();
if (held.is(Items.DIAMOND_SWORD)) {
    // Do something
}
```

**Creating ItemStack**:
```java
ItemStack stack = new ItemStack(Items.DIAMOND, 64);
stack.set(DataComponents.CUSTOM_NAME, Component.literal("Special Diamond"));
```

**Crafting Recipe Check**:
```java
Optional<RecipeHolder<CraftingRecipe>> recipe = 
    level.getRecipeManager().getRecipeFor(
        RecipeType.CRAFTING, 
        craftingContainer, 
        level
    );
if (recipe.isPresent()) {
    ItemStack result = recipe.get().value().assemble(craftingContainer, registries);
}
```

## Related Systems

- [Data System](DATA-SYSTEM.md) - Recipes and loot tables
- [Entity System](ENTITY-SYSTEM.md) - Item entities and equipment
- [Networking System](NETWORKING-SYSTEM.md) - Inventory synchronization
- [Command System](COMMAND-SYSTEM.md) - Give/clear commands
