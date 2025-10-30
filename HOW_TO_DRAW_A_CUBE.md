# How to Draw a Cube in MattMC

This repository already includes a working implementation of a 3D rotating cube! Here's how to access it and understand how it works.

## Quick Access

To see a cube being drawn in the application:

1. **Build and run the project:**
   ```bash
   ./gradlew run
   ```

2. **Navigate through the menu:**
   - Start at the Title Screen
   - Click "Singleplayer"
   - Click the "Devplay" button (top-left corner)
   - You'll see a rotating, colored 3D cube!

3. **Exit the cube view:**
   - Press ESC to return to the Singleplayer menu

## Implementation Details

The cube drawing implementation is located in:
```
src/main/java/MattMC/screens/DevplayScreen.java
```

### How It Works

The `DevplayScreen` class demonstrates how to draw a 3D cube using OpenGL immediate mode. Here are the key components:

#### 1. Perspective Setup

The cube is rendered with a 3D perspective projection:

```java
// Set up perspective projection
float aspect = (float) width / height;
glMatrixMode(GL_PROJECTION);
glLoadIdentity();
float fov = 60f, zn = 0.1f, zf = 100f;
float top = (float) (Math.tan(Math.toRadians(fov * 0.5)) * zn);
float bottom = -top;
float right = top * aspect;
float left = -right;
glFrustum(left, right, bottom, top, zn, zf);
```

#### 2. Camera Positioning

The cube is positioned and rotated for a nice view:

```java
glMatrixMode(GL_MODELVIEW);
glLoadIdentity();
glTranslatef(0f, 0f, -6f);           // Move camera back
glRotatef(25f, 1f, 0f, 0f);          // Tilt down slightly
glRotatef(angleDeg, 0f, 1f, 0f);     // Rotate around Y-axis
```

#### 3. Drawing the Cube

The cube is drawn using 12 triangles (2 per face, 6 faces total). Each face has a different color:

- **Front (+Z)**: Yellow-Orange (`0xFFD166`)
- **Back (-Z)**: Teal (`0x06D6A0`)
- **Left (-X)**: Blue (`0x118AB2`)
- **Right (+X)**: Dark Blue (`0x073B4C`)
- **Top (+Y)**: Gold (`0xFFD700`)
- **Bottom (-Y)**: Red (`0xE63946`)

Example of drawing one face (Front):

```java
float h = size / 2f;  // Half size
setColor(0xFFD166, 1f);  // Yellow-Orange
glBegin(GL_TRIANGLES);
// First triangle
glVertex3f(-h, -h, +h); glVertex3f(+h, -h, +h); glVertex3f(+h, +h, +h);
// Second triangle
glVertex3f(-h, -h, +h); glVertex3f(+h, +h, +h); glVertex3f(-h, +h, +h);
glEnd();
```

#### 4. Edge Lines

Black edge lines are drawn around the cube for better visibility:

```java
setColor(0x0B1220, 1f);  // Dark color
glBegin(GL_LINES);
// Draw 12 edges of the cube
glVertex3f(-h, -h, -h); glVertex3f(+h, -h, -h);
// ... (11 more edges)
glEnd();
```

#### 5. Animation

The cube rotates smoothly using time-based animation:

```java
double dt = now - lastFrameTimeSec;
angleDeg += 45f * dt;  // 45 degrees per second
```

## Creating Your Own Cube

To create a cube in a new screen or modify the existing one:

1. **Enable depth testing:**
   ```java
   glEnable(GL_DEPTH_TEST);
   glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
   ```

2. **Set up perspective projection** (see above)

3. **Position your camera** using `glTranslatef` and `glRotatef`

4. **Draw each face** using triangles or quads:
   ```java
   glBegin(GL_TRIANGLES);  // or GL_QUADS
   glVertex3f(x, y, z);
   // ... more vertices
   glEnd();
   ```

5. **Disable depth testing** when done:
   ```java
   glDisable(GL_DEPTH_TEST);
   ```

## Key Concepts

### Coordinate System
- **X-axis**: Right (+) / Left (-)
- **Y-axis**: Up (+) / Down (-)
- **Z-axis**: Forward (+) / Backward (-)

### Cube Structure
A cube has:
- 8 vertices (corners)
- 12 edges
- 6 faces
- Each face needs 2 triangles or 1 quad

### Vertex Order
For proper face culling (optional), vertices should be specified in counter-clockwise order when viewed from the outside of the cube.

## Technologies Used

- **LWJGL 3.3.4**: Lightweight Java Game Library for OpenGL bindings
- **OpenGL**: Graphics rendering (immediate mode)
- **GLFW**: Window and input management

## Further Customization

You can customize the cube by modifying `DevplayScreen.java`:

- **Size**: Change the `cubeSize` field (default is 2 world units)
- **Colors**: Modify the hex color codes in the `drawCube()` method
- **Rotation speed**: Adjust the multiplication factor in `angleDeg += 45f * dt;`
- **Camera position**: Modify the `glTranslatef` values
- **Field of view**: Change the `fov` variable

## References

- Main entry point: `src/main/java/MattMC/Main.java`
- Game loop: `src/main/java/MattMC/core/Game.java`
- Screen interface: `src/main/java/MattMC/screens/Screen.java`
- Cube implementation: `src/main/java/MattMC/screens/DevplayScreen.java`
