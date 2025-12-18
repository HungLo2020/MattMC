# Distant Horizons MC 1.21.10 Port Documentation

## Overview
This document describes the changes made to properly port Distant Horizons 2.3.4b to Minecraft 1.21.10, fixing GUI translation and layout issues that occurred when preprocessor directives were removed.

## Issues Fixed

### 1. Vertical Stacking of GUI Elements
**Root Cause:** The `ButtonEntry.renderContent()` method signature changed in MC 1.21.6+ from `render()` to `renderContent()`, with different parameter semantics.

**Previous Code (Incorrect):**
```java
public void renderContent(GuiGraphics matrices, int x, int y, boolean hovered, float tickDelta) {
    // Incorrectly treated x,y as rendering positions
    int mouseX = 0;
    int mouseY = 0;
    SetY(button, y);  // WRONG: y is actually mouseY, not render position
    button.render(matrices, mouseX, mouseY, tickDelta);
}
```

**Fixed Code:**
```java
public void renderContent(GuiGraphics matrices, int mouseX, int mouseY, boolean hovered, float tickDelta) {
    // Correctly understands mouseX, mouseY are mouse coordinates
    int renderY = this.getContentY();  // Get actual rendering position
    SetY(button, renderY);  // CORRECT: use Entry's content Y position
    button.render(matrices, mouseX, mouseY, tickDelta);
}
```

**Key Changes:**
- Renamed parameters from `x, y` to `mouseX, mouseY` to reflect actual semantics
- Use `this.getContentY()` to get the correct vertical rendering position
- Pass actual mouse coordinates to widget.render() for hover detection

### 2. Missing Version Configuration
**Issue:** No `1.21.10.properties` file existed in DH's version properties

**Fix:** Created `/modules/distant-horizons-2.3.4b/versionProperties/1.21.10.properties` based on 1.21.8 configuration with updated:
- `minecraft_version=1.21.10`
- `fabric_api_version=0.129.0+1.21.10`
- `neoforge_version=21.10.2-beta`

### 3. Gradle Configuration
**Fix:** Updated `mcVer` in DH's `gradle.properties` from `1.21.8` to `1.21.10`

### 4. Missing Translation Files in Main Project Resources
**Root Cause:** When DH code is compiled directly into the game JAR (rather than loaded as a separate mod), Minecraft's resource loading system requires language files to be in the main project's resources directory.

**Issue:** Language files existed in `modules/distant-horizons-2.3.4b/coreSubProjects/core/src/main/resources/assets/distanthorizons/lang/` but were not accessible at runtime because they weren't in the main project's resources.

**Fix:** Copied `en_us.json` to `src/main/resources/assets/distanthorizons/lang/en_us.json` so translations are available when the game loads.

**Impact:** All DH GUI text now displays correctly:
- Button labels: "Done", "Cancel", "Reset"
- Boolean values: "True", "False"
- Config titles and tooltips
- Enum value translations

## API Changes in MC 1.21.6+

### Entry Rendering Method
In Minecraft 1.21.6+, the `ContainerObjectSelectionList.Entry` class changed its rendering method:

**Before (MC 1.20.1 - 1.21.5):**
```java
public void render(GuiGraphics matrices, int index, int y, int x, 
                   int entryWidth, int entryHeight, int mouseX, int mouseY, 
                   boolean hovered, float tickDelta)
```

**After (MC 1.21.6+):**
```java
public void renderContent(GuiGraphics matrices, int mouseX, int mouseY, 
                         boolean hovered, float tickDelta)
```

**Key Differences:**
1. Method name changed from `render` to `renderContent`
2. Fewer parameters - no index, entryWidth, entryHeight
3. Parameter order changed - mouseX/mouseY are now the first int parameters
4. Entry position is accessed via `this.getX()`, `this.getY()`, `this.getContentX()`, `this.getContentY()` methods

### How AbstractSelectionList Calls renderContent
```java
// In AbstractSelectionList.renderItem():
protected void renderItem(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, E entry) {
    // ... selection rendering ...
    entry.renderContent(guiGraphics, mouseX, mouseY, Objects.equals(this.hovered, entry), delta);
}
```

The `mouseX` and `mouseY` parameters come from the widget's `renderWidget()` method, which receives them from the screen's render call.

## Verified APIs (Already Correct)

### GuiHelper.java
All methods are correct for MC 1.21.10:
- `Component.translatable()` - Available since MC 1.19.2
- `Component.literal()` - Available since MC 1.19.2  
- `Button.builder()` - Available since MC 1.19.4
- `widget.setX()/setY()` - Available since MC 1.19.4

### DhScreen.java
All drawing methods are correct for MC 1.21.10:
- `guiStack.drawCenteredString()` - Available in MC 1.21.10
- `guiStack.drawString()` - Available in MC 1.21.10
- `guiStack.setComponentTooltipForNextFrame()` - Available in MC 1.21.10
- `guiStack.setTooltipForNextFrame()` - Available in MC 1.21.10

### ConfigListWidget Constructor
The constructor signature is correct for MC 1.20.4+:
```java
super(minecraftClient, canvasWidth, canvasHeight - (topMargin + botMargin), topMargin, itemSpacing);
```

This maps to:
- `width` = canvasWidth
- `height` = canvasHeight - (topMargin + botMargin)
- `top` = topMargin
- `itemHeight` = itemSpacing

## Translation System

The translation system uses:
1. `Component.translatable(key)` to create translatable components
2. Language files that must be in the main project's resources for direct integration
3. `I18n.exists(key)` to check if a translation exists before using it

**Important**: When DH is compiled directly into the game JAR, language files must be in `src/main/resources/assets/distanthorizons/lang/` rather than only in the modules directory. This ensures Minecraft's resource loader can find and load the translations at runtime.

All of these APIs work correctly in MC 1.21.10.

## Files Modified

1. `/modules/distant-horizons-2.3.4b/common/src/main/java/com/seibel/distanthorizons/common/wrappers/gui/ClassicConfigGUI.java`
   - Fixed `ButtonEntry.renderContent()` method

2. `/modules/distant-horizons-2.3.4b/versionProperties/1.21.10.properties` (NEW)
   - Added version configuration for MC 1.21.10

3. `/modules/distant-horizons-2.3.4b/gradle.properties`
   - Updated `mcVer` from 1.21.8 to 1.21.10

4. `/src/main/resources/assets/distanthorizons/lang/en_us.json` (NEW)
   - Copied DH language file to main project resources
   - Required for translations to work when DH is compiled into game JAR

## Testing Recommendations

1. **GUI Layout Test:**
   - Open DH config GUI (`Options -> Video Settings -> Distant Horizons` or via mod menu)
   - Verify buttons are horizontally aligned, not vertically stacked
   - Check that text labels appear to the left of controls
   - Verify reset buttons appear to the right of controls

2. **Translation Test:**
   - Verify button labels show "Done", "Cancel", "Reset" (not translation keys)
   - Check that boolean options show "True"/"False"
   - Verify enum options show translated values
   - Hover over options to verify tooltips appear

3. **Interaction Test:**
   - Click buttons to verify they respond to mouse events
   - Hover over buttons to verify hover states work
   - Test text input fields for config values
   - Test enum cycling by clicking enum option buttons

## Future Considerations

If porting to newer Minecraft versions:
1. Check if `renderContent()` API remains stable
2. Verify `Component.translatable()` is still the correct translation API
3. Check if `ContainerObjectSelectionList` constructor signature changes
4. Update version properties file for the new MC version
5. Test all GUI interactions thoroughly

## References

- Original DH source (with preprocessor directives): `/frnsrc/distant-horizons-2.3.4b/`
- Modified DH source (1.21.10 port): `/modules/distant-horizons-2.3.4b/`
- MC 1.21.10 decompiled source: `/net/minecraft/client/gui/`
- DH Integration Guide: `/DH-INTEGRATION.md`
