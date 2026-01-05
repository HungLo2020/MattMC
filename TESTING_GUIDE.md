# WorldEdit In-Game Testing Guide

This guide explains how to test WorldEdit functionality in MattMC to verify commands are working correctly.

## Prerequisites

1. **Start the Server**
   ```bash
   cd /home/runner/work/MattMC/MattMC
   ./gradlew build
   cd build/libs
   java -Xmx2G -Xms2G -jar server.jar nogui
   ```

2. **Accept EULA**
   - Edit `eula.txt` and set `eula=true`
   - Restart the server

3. **Join the Server**
   - Start Minecraft 1.21.10
   - Connect to `localhost` (or your server IP)

4. **Get Operator Permissions**
   - In server console: `/op YourUsername`
   - This gives you access to all WorldEdit commands

## Getting the Wand Item

The wand is the primary tool for selecting regions:

1. **Open Creative Inventory** (Press `E`)
2. **Go to Operator Items Tab** (The command block icon)
3. **Find "WorldEdit Wand"** (Looks like a stick)
4. **Grab the wand** and put it in your hotbar

**Or use command:**
```
/give @s minecraft:wand
```

## Basic Selection Testing

### Test 1: Select a Region with the Wand

1. **Hold the wand** in your hand
2. **Right-click** on a block - You should see a message:
   ```
   Primary position set to (X, Y, Z)
   ```
3. **Left-click** on a different block - You should see:
   ```
   Secondary position set to (X, Y, Z)
   ```

### Test 2: Select with Commands

1. Stand where you want to set a position
2. Type: `//pos1`
   - Should see: `Primary position set to (X, Y, Z)`
3. Move to another location
4. Type: `//pos2`
   - Should see: `Secondary position set to (X, Y, Z)`

### Test 3: Check Selection Size

1. After setting both positions, type: `//size`
   - Should display: `Selection size: X blocks (width x height x depth)`

## Block Manipulation Testing

### Test 4: Fill Region with Blocks

1. **Select a small region** (5x5x5 recommended for testing)
2. Type: `//set stone`
   - Should see: `Set X blocks`
   - All blocks in selection should turn to stone

**Alternative blocks to test:**
- `//set glass` - Transparent blocks
- `//set wool` - Colorful blocks
- `//set minecraft:oak_planks` - Full resource location

### Test 5: Replace Blocks

1. **Select the stone region** from Test 4
2. Type: `//replace stone dirt`
   - Should see: `Replaced X blocks`
   - All stone should turn to dirt

### Test 6: Create Walls

1. **Select a region**
2. Type: `//walls brick`
   - Creates vertical walls around the selection
   - Floor and ceiling remain unchanged

### Test 7: Create Faces

1. **Select a region**
2. Type: `//faces gold_block`
   - Creates a hollow box
   - All 6 faces (top, bottom, 4 sides) should be gold

## History Testing (Undo/Redo)

### Test 8: Undo Changes

1. **Do a `//set` command**
2. Type: `//undo`
   - Should see: `Undid last edit (X blocks changed)`
   - Blocks should return to original state

### Test 9: Redo Changes

1. **After undoing**, type: `//redo`
   - Should see: `Redid last edit (X blocks changed)`
   - Blocks should return to the changed state

### Test 10: Multiple Undo Levels

1. Do several operations:
   - `//set stone`
   - `//set dirt`
   - `//set grass_block`
2. Type `//undo` three times
   - Each undo should reverse one operation
3. Type `//redo` three times
   - Each redo should reapply one operation

## Clipboard Testing

### Test 11: Copy and Paste

1. **Build a small structure** (e.g., 3x3 cube of different blocks)
2. **Select the structure** with wand or //pos commands
3. Type: `//copy`
   - Should see: `Copied X blocks to clipboard`
4. **Move to a different location**
5. Type: `//paste`
   - Should see: `Pasted X blocks`
   - Your structure should appear at new location

### Test 12: Cut and Paste

1. **Select a region with blocks**
2. Type: `//cut`
   - Original blocks should be removed
   - Should see: `Cut X blocks to clipboard`
3. **Move to new location**
4. Type: `//paste`
   - Blocks should appear
5. Type: `//undo` twice
   - Should restore original location and remove paste

### Test 13: Rotate Clipboard

1. **Copy a non-symmetrical structure**
2. Type: `//rotate 90`
   - Should see: `Clipboard rotated`
3. Type: `//paste`
   - Structure should appear rotated 90 degrees

## Generation Testing

### Test 14: Create Sphere

1. **Stand where you want the sphere center**
2. Type: `//sphere stone 5`
   - Should create a solid stone sphere, radius 5
   - Should see: `Created sphere: X blocks`

### Test 15: Create Hollow Sphere

1. Type: `//hsphere glass 8`
   - Should create a hollow glass sphere, radius 8
   - You should be able to walk inside

### Test 16: Create Cylinder

1. Type: `//cyl dirt 4 10`
   - Creates dirt cylinder, radius 4, height 10
   - Should see: `Created cylinder: X blocks`

### Test 17: Create Pyramid

1. Type: `//pyramid gold_block 7`
   - Creates a golden pyramid, size 7
   - Should see: `Created pyramid: X blocks`

## Tool Testing

### Test 18: Super Pickaxe

1. **Hold any pickaxe**
2. Type: `//superpickaxe single`
   - Should see: `Super pickaxe enabled (single mode)`
3. **Left-click any block**
   - Block should break instantly
4. Type: `//` (just two slashes)
   - Should see: `Super pickaxe disabled`

### Test 19: Super Pickaxe Area Mode

1. **Hold a pickaxe**
2. Type: `//superpickaxe area 3`
   - Should see: `Super pickaxe enabled (area mode, range: 3)`
3. **Left-click a block**
   - Should break a 3x3x3 cube of blocks instantly

### Test 20: Brush Tool

1. **Hold any item** (stick, shovel, etc.)
2. Type: `//brush sphere stone 3`
   - Should see: `Sphere brush bound to [ItemName]`
3. **Right-click on blocks**
   - Should create stone spheres (radius 3) where you click
4. Type: `//none`
   - Should unbind the tool

## Navigation Testing

### Test 21: Jumpto

1. **Look at a distant block**
2. Type: `//jumpto`
   - Should teleport you to that block

### Test 22: Ascend/Descend

1. **Build a multi-floor structure**
2. Type: `//ascend`
   - Should teleport up one floor
3. Type: `//descend`
   - Should teleport down one floor

### Test 23: Up Command

1. Type: `//up 10`
   - Should teleport you 10 blocks up
   - Glass block placed under your feet

## Utility Testing

### Test 24: Drain Water

1. **Create a water pool** (use buckets or commands)
2. **Stand near the water**
3. Type: `//drain 10`
   - All water within 10 blocks should be removed

### Test 25: Fill Air Gaps

1. **Select a region with some air blocks**
2. Type: `//fill stone 5`
   - Air blocks within 5 blocks should fill with stone

## Schematic Testing

### Test 26: Save Schematic

1. **Build and select a structure**
2. Type: `//schem save myhouse`
   - Should see: `Schematic saved to myhouse.schem`
3. Check: `world/schematics/myhouse.schem` file should exist

### Test 27: Load Schematic

1. Type: `//schem load myhouse`
   - Should see: `Schematic loaded to clipboard`
2. **Move to empty area**
3. Type: `//paste`
   - Your saved structure should appear

### Test 28: List Schematics

1. Type: `//schem list`
   - Should show all saved schematics
   - Should include `myhouse.schem`

## Troubleshooting

### "No selection defined"
- Make sure you've set both pos1 and pos2
- Use `//pos1` and `//pos2` or the wand

### "Unknown block"
- Check spelling of block name
- Try using full ID: `minecraft:stone` instead of just `stone`
- Use tab completion to see available blocks

### "No permission"
- Make sure you're opped: `/op YourUsername`
- Check server console for permission errors

### Wand not in creative inventory
- Use: `/give @s minecraft:wand`
- Check that server compiled successfully

### Commands not working
- Make sure you use `//` (two slashes) not `/`
- Some commands might need full namespace: `minecraft:block_name`

## Quick Test Sequence

**5-Minute Validation Test:**

1. Get wand: `/give @s minecraft:wand`
2. Select region: Right-click, then left-click with wand
3. Fill region: `//set stone`
4. Check it worked: Blocks should be stone
5. Undo: `//undo` - Blocks should revert
6. Redo: `//redo` - Blocks should be stone again
7. Create sphere: `//sphere glass 5`
8. Copy structure: `//copy`
9. Move and paste: `//paste`
10. Save it: `//schem save test`

If all 10 steps work, your WorldEdit implementation is functional!

## Performance Testing

For large operations, test with progressively larger selections:

1. **Small**: 10x10x10 (1,000 blocks)
2. **Medium**: 50x50x50 (125,000 blocks)
3. **Large**: 100x100x100 (1,000,000 blocks)

Monitor:
- Server TPS (should stay near 20)
- Memory usage
- Time to complete operation
- Ability to undo large operations

## Common Test Patterns

### Pattern 1: Build, Modify, Undo
```
//pos1
//pos2
//set stone
//replace stone dirt
//undo
//undo
```

### Pattern 2: Copy Architecture
```
//pos1
//pos2
//copy
<move>
//paste
//rotate 90
//paste
//rotate 90
//paste
```

### Pattern 3: Terrain Shaping
```
//sphere dirt 20
//hsphere air 18
//overlay grass_block
```

## Success Criteria

✅ All commands execute without errors
✅ Block changes appear correctly in-game  
✅ Undo/redo works reliably
✅ Clipboard operations preserve structure
✅ Tools activate on item use
✅ Schematics save and load correctly
✅ Server remains stable during operations
✅ No console errors during testing

## Reporting Issues

If you find bugs during testing:

1. Note the exact command used
2. Check server console for errors
3. Try to reproduce with minimal steps
4. Note your selection size
5. Check if undo works
6. Report with full error message if any

---

**Happy Building with WorldEdit!** 🎮🔨
