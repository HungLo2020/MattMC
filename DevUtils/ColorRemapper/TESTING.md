# ColorRemapper Testing Documentation

## Test 1: Basic Color Darkening
**Objective**: Verify that colors are darkened by the correct percentage.

**Setup**:
- Create a PNG with color #ffffff (white)
- Configuration: `DARKNESS_PERCENT=40`
- Color list: `ffffff`

**Expected Result**: All #ffffff pixels become #999999 (60% of original brightness)

**Calculation**: 
- Original: (255, 255, 255)
- 40% darker = 60% of original = (153, 153, 153) = #999999

**Status**: ✓ PASSED

## Test 2: Multiple Colors Darkening
**Objective**: Verify that multiple colors are darkened independently.

**Setup**:
- Create a PNG with three colors: #8b8b8b, #c6c6c6, #ffffff
- Configuration: `DARKNESS_PERCENT=40`
- Color list:
  - `8b8b8b`
  - `c6c6c6`
  - `ffffff`

**Expected Results**:
- #8b8b8b (139, 139, 139) → #535353 (83, 83, 83) [60% of original]
- #c6c6c6 (198, 198, 198) → #767676 (118, 118, 118) [60% of original]
- #ffffff (255, 255, 255) → #999999 (153, 153, 153) [60% of original]

**Status**: ✓ PASSED

## Test 3: Color Preservation (No Wipeout)
**Objective**: Verify that simultaneous application prevents color wipeout.

**Setup**:
- Create a PNG where one color, when darkened, matches another color in the list
- Configuration: `DARKNESS_PERCENT=40`
- Example scenario where darkened color A equals original color B

**Critical Requirement**: 
- Pixels that were originally color A should become A_darkened
- Pixels that were originally color B should become B_darkened
- **The newly created A_darkened pixels should NOT be darkened again to B_darkened**

**How It's Prevented**:
The script reads all pixel values from the original image ONCE, then applies all mappings based on those original values. This ensures that mappings are applied simultaneously rather than sequentially.

**Implementation Detail**:
```python
# For each pixel in the image:
original_color = pixel[x, y]

# Check against original color only
if original_color in color_mappings:
    pixel[x, y] = color_mappings[original_color]
```

**Status**: ✓ PASSED

## Test 4: Variable Darkness Percentage
**Objective**: Verify that the darkness percentage can be changed.

**Setup**:
- Test with different percentages: 0%, 25%, 50%, 75%, 100%
- Use white color (#ffffff) for easy verification

**Expected Results**:
- 0% darker: #ffffff → #ffffff (no change)
- 25% darker: #ffffff → #bfbfbf (191, 191, 191)
- 50% darker: #ffffff → #7f7f7f (127, 127, 127)
- 75% darker: #ffffff → #3f3f3f (63, 63, 63)
- 100% darker: #ffffff → #000000 (0, 0, 0)

**Status**: ✓ PASSED

## Test 5: Dynamic Color Loading
**Objective**: Verify that any number of colors can be read from the config file.

**Setup**:
- Add multiple colors to color_mappings.txt
- Run the script

**Expected Result**: All colors are loaded and darkened correctly

**Status**: ✓ PASSED

## Test 6: In-Place File Updates
**Objective**: Verify that files are overwritten without name changes.

**Setup**:
- Create test.png
- Run color_remapper.py

**Expected Result**: test.png is modified in place (same filename)

**Status**: ✓ PASSED

## Conclusion
All tests passed successfully. The ColorRemapper tool correctly:
- Darkens colors by the specified percentage
- Applies darkening to multiple colors simultaneously
- Preserves color integrity (no wipeout)
- Handles configurable darkness percentages
- Handles any number of colors dynamically
- Overwrites files in place
