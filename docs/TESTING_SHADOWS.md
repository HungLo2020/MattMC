# Testing Shadow Mapping

## Quick Start

Shadow mapping is implemented but **disabled by default** as it's an experimental feature.

## Enabling Shadows

### Method 1: Edit Options.txt

1. Run the game at least once to generate the `Options.txt` file
2. Close the game
3. Open `Options.txt` in the game directory
4. Find the line: `shadows=false`
5. Change it to: `shadows=true`
6. Save the file
7. Restart the game

### Method 2: Via Code (for testing)

In `OptionsManager.java`, change the default:
```java
private static boolean shadowsEnabled = false;  // Change to true
```

## What to Look For

When shadows are enabled and working correctly:

1. **During daytime** (when sun is visible):
   - Blocks should cast shadows on the ground
   - Shadow edges should be soft (PCF filtering)
   - Shadows should move with the sun's position
   - Shadows should darken surfaces but not make them completely black

2. **During nighttime** (when sun is below horizon):
   - No shadows should render (performance optimization)
   - Game should look the same as without shadows

3. **Performance**:
   - Expect ~20-30% FPS reduction when shadows are active
   - Shadow rendering only happens during daytime

## Known Issues / Limitations

### If the Game Appears Broken

If you see rendering artifacts or the screen appears cut in half:

1. **Disable shadows immediately** by setting `shadows=false` in Options.txt
2. This indicates a viewport/framebuffer state issue
3. Report the issue with:
   - Your GPU model
   - OpenGL version (`glxinfo | grep "OpenGL version"` on Linux)
   - Screen resolution
   - Any error messages in logs

### Expected Behavior When Disabled

With `shadows=false` (default):
- Game should render normally
- No performance impact
- No visual changes to lighting

### Current Limitations

1. **Shadow acne**: Some surfaces may show self-shadowing artifacts
2. **Peter panning**: Objects may appear slightly detached from shadows
3. **Limited range**: Shadows only cover ~128 units around player
4. **No cascading**: Distant shadows have lower resolution
5. **Block-only**: Only blocks cast shadows (no entities yet)

## Debugging

### Check if shadows are being rendered:

Look in the console/logs for:
```
[ShadowRenderer] Shadow renderer initialized with 2048x2048 shadow map
```

### Check OpenGL state:

The shadow system requires:
- OpenGL 3.0+ (for framebuffer objects)
- GL_EXT_framebuffer_object support
- Depth texture support

### Performance profiling:

Compare FPS with shadows on vs off:
- Expected impact: 20-30% reduction
- If >50% reduction, something is wrong

## Testing Checklist

- [ ] Game starts without errors (shadows disabled)
- [ ] Enable shadows in Options.txt
- [ ] Game renders correctly with shadows enabled
- [ ] Shadows appear during daytime
- [ ] No shadows during nighttime
- [ ] Shadows move with sun position
- [ ] FPS impact is acceptable
- [ ] No visual artifacts
- [ ] Disabling shadows works correctly

## Troubleshooting

### Shadows not visible

Check:
1. Is `shadows=true` in Options.txt?
2. Is it daytime? (Sun must be above horizon)
3. Are there blocks above you to cast shadows?
4. Is sky brightness > 0.3? (Use `/time query` command)

### Rendering broken

Solution:
1. Set `shadows=false` in Options.txt
2. Restart game
3. Game should work normally

### Performance too poor

Solutions:
1. Reduce shadow map resolution in code (change SHADOW_MAP_SIZE to 1024)
2. Reduce shadow frustum size (change SHADOW_FRUSTUM_SIZE to 64)
3. Disable shadows

## Advanced Testing

### Test different times of day:

```
/time set day      # Should see shadows
/time set noon     # Shortest shadows (sun overhead)
/time set sunset   # Long shadows
/time set night    # No shadows
```

### Test shadow quality:

Look for:
- Soft edges (PCF working)
- No severe aliasing
- Reasonable bias (no shadow acne or peter panning)
- Proper depth rendering

### Test edge cases:

- Underground (no shadows expected)
- High altitude (check shadow frustum coverage)
- Chunk boundaries (shadows should be seamless)
- Moving quickly (shadow map should update)

## Reporting Issues

If you find bugs, report:

1. Steps to reproduce
2. Expected vs actual behavior
3. Screenshots (if visual)
4. Options.txt contents
5. System info (GPU, OS, OpenGL version)
6. Console/log output

## Future Improvements

If shadows work well, future enhancements could include:

1. Cascaded shadow maps for better quality
2. Dynamic resolution based on performance
3. Entity shadows
4. Moon shadows at night
5. Quality presets (low/medium/high)
6. Shadow distance setting
