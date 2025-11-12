# Day/Night Cycle Implementation

This document describes the Minecraft-style day/night cycle implementation in MattMC.

## Overview

The day/night cycle follows Minecraft's implementation as closely as possible:
- Full cycle: 24,000 ticks (20 real-world minutes at 20 TPS)
- Sun rises at time 0, reaches zenith at 6,000 (noon), sets at 12,000
- Night lasts from 13,000 to 23,000, with sunrise/dawn from 23,000 to 24,000/0

## Components

### 1. DayCycle Class (`mattmc.world.level.DayCycle`)

Manages all time-of-day calculations:

**Time Tracking:**
- `tick()` - Advances time by one tick
- `getWorldTime()` / `setWorldTime()` - Get/set absolute world time
- `getTimeOfDay()` - Get time within current day (0-24,000)
- `getCelestialAngle()` - Get normalized time (0.0-1.0) for positioning celestial bodies

**Sun Calculations:**
- `getSunAngle()` - Returns sun angle in radians (0 at sunrise, π/2 at noon, π at sunset)
- `getSunDirection()` - Returns normalized direction vector for directional light
  - At sunrise: sun is at horizon (y ≈ 0)
  - At noon: sun is overhead (y ≈ 1)
  - At sunset: sun is at horizon (y ≈ 0)

**Visual Properties:**
- `getSkyColor()` - Returns RGB color for sky clear color
  - Day (0-11,000): Light blue (0.53, 0.81, 0.92)
  - Sunset (11,000-13,000): Orange gradient transition
  - Night (13,000-23,000): Dark blue (0.05, 0.05, 0.15)
  - Sunrise (23,000-24,000): Orange gradient back to day
  
- `getSkyBrightness()` - Returns brightness multiplier (0.3-1.0)
  - Full brightness during day (1.0)
  - Dims to 30% at night (0.3)
  - Smooth transitions during sunset/sunrise

### 2. Level Integration (`mattmc.world.level.Level`)

The Level class now includes:
- `DayCycle` instance for time tracking
- `tickDayCycle()` - Called once per game tick to advance time
- `getDayCycle()` - Access the day cycle for queries

### 3. Rendering (`mattmc.client.gui.screens.DevplayScreen`)

The game screen now:
- Calls `world.tickDayCycle()` in the tick method (20 TPS)
- Uses `world.getDayCycle().getSkyColor()` for dynamic sky color
- Sets up directional lighting with `setupDirectionalLight()`:
  - Enables OpenGL GL_LIGHT0 as a directional light
  - Sets light position based on sun direction (w=0 for directional)
  - Adjusts ambient and diffuse based on sky brightness
  - Uses GL_COLOR_MATERIAL for Minecraft-like lighting

### 4. Time Commands (`mattmc.client.gui.screens.CommandSystem`)

Added `/time` command support:
- `/time query` - Shows current time of day and world time
- `/time set <value>` - Set time to specific tick value
- `/time set day` - Set to 1000 (early morning)
- `/time set noon` - Set to 6000 (midday)
- `/time set night` - Set to 13000 (early night)
- `/time set midnight` - Set to 18000 (midnight)

### 5. Persistence (`mattmc.world.level.storage`)

World time is now saved and loaded:
- `LevelData` includes `worldTime` field
- `LevelStorageSource.saveWorld()` saves current world time
- `LevelStorageSource.loadWorld()` restores world time

## Technical Details

### Directional Light Setup

The sun is implemented as an OpenGL directional light (GL_LIGHT0):

```java
// Position with w=0 makes it directional (parallel rays)
float[] lightPos = {sunDir[0], sunDir[1], sunDir[2], 0.0f};
glLightfv(GL_LIGHT0, GL_POSITION, lightPos);

// Colors based on brightness
float[] ambient = {0.4f * brightness, 0.4f * brightness, 0.4f * brightness, 1.0f};
float[] diffuse = {brightness, brightness, brightness, 1.0f};
glLightfv(GL_LIGHT0, GL_AMBIENT, ambient);
glLightfv(GL_LIGHT0, GL_DIFFUSE, diffuse);
```

This approach:
- Uses fixed-function pipeline for compatibility
- Provides a directional light source suitable for future shader work
- Matches Minecraft's lighting style

### Sun Arc Calculation

The sun follows a half-circle arc across the sky:

```
Time 0 (sunrise):    angle = 0,     sin(0) = 0,     y ≈ 0 (horizon)
Time 6000 (noon):    angle = π/2,   sin(π/2) = 1,   y = 1 (overhead)
Time 12000 (sunset): angle = π,     sin(π) = 0,     y ≈ 0 (horizon)
```

The Y component (height) uses `sin(angle)` which naturally creates the arc from horizon to zenith and back.

## Testing

Comprehensive unit tests in `DayCycleTest` verify:
- Time advancement and wrapping
- Celestial angle calculations
- Sun direction normalization and positioning
- Sky color transitions
- Brightness calculations
- Initial time settings

All tests pass successfully, ensuring correct implementation of Minecraft's day/night cycle mechanics.

## Future Enhancements

Possible improvements:
- Moon rendering during night
- Stars in the night sky
- Weather effects (clouds, rain)
- Biome-specific sky colors
- Smooth color interpolation over multiple frames
- Shader-based lighting for more advanced effects
