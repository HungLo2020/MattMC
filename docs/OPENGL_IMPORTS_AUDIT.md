# OpenGL Import Audit Report

## Files with OpenGL imports OUTSIDE of backend/opengl

The following files still contain OpenGL imports and are NOT in the backend/opengl directory:

### Core Client Files (1 file)
1. `src/main/java/mattmc/client/Window.java` - Window/GLFW management

### GUI Component Files (4 files)
2. `src/main/java/mattmc/client/gui/components/ButtonRenderer.java` - Button rendering
3. `src/main/java/mattmc/client/gui/components/TextRenderer.java` - Text rendering
4. `src/main/java/mattmc/client/gui/components/TrueTypeFont.java` - Font rendering
5. `src/main/java/mattmc/client/gui/screens/AbstractMenuScreen.java` - Base menu screen

### GUI Screen Files (8 files)
6. `src/main/java/mattmc/client/gui/screens/ControlsScreen.java` - Controls configuration screen
7. `src/main/java/mattmc/client/gui/screens/CreateWorldScreen.java` - World creation screen
8. `src/main/java/mattmc/client/gui/screens/DevplayInputHandler.java` - Input handling
9. `src/main/java/mattmc/client/gui/screens/DevplayScreen.java` - Main gameplay screen
10. `src/main/java/mattmc/client/gui/screens/InventoryScreen.java` - Inventory screen
11. `src/main/java/mattmc/client/gui/screens/PauseScreen.java` - Pause menu
12. `src/main/java/mattmc/client/gui/screens/SelectWorldScreen.java` - World selection
13. `src/main/java/mattmc/client/gui/screens/TitleScreen.java` - Title screen

### Inventory Renderer (1 file)
14. `src/main/java/mattmc/client/gui/screens/inventory/InventoryRenderer.java` - Inventory rendering

### Utility Files (2 files)
15. `src/main/java/mattmc/client/util/SystemInfo.java` - System information
16. `src/main/java/mattmc/util/ColorUtils.java` - Color utilities

## Summary

**Total files with OpenGL imports outside backend/opengl: 16**

These files fall into several categories:
- Window/GLFW management (1 file)
- GUI rendering components (4 files)
- Screen implementations (8 files)
- Inventory rendering (1 file)
- Utilities (2 files)

## Recommendation

These files should be evaluated for:
1. **Move to backend/opengl** - If they contain OpenGL-specific rendering code
2. **Refactor** - Extract OpenGL calls to new classes in backend/opengl, keep logic separate
3. **Keep but document** - If they're infrastructure (like Window.java for GLFW setup)

The strict rule is: ANY file that imports org.lwjgl.opengl should be in backend/opengl.
