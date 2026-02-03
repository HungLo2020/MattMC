# Rotating Cube - Visual Specification

## Cube Layout

The cube consists of 6 faces, each with 2 triangles (12 triangles total, 36 vertices).

```
         [BLUE]
           +Y
            |
            |
    [CYAN]  |  [MAGENTA]
  -X -------+------- +X
            |
            |
         [YELLOW]
           -Y

  Looking down +Z axis:
  Front face: [RED]
  Back face: [GREEN]
```

## Face Colors

```
        Top (Blue)
           ___
          /   /|
  Left   /___/ |  Right
 (Cyan) |   | /  (Magenta)
        |___|/
       Bottom
      (Yellow)

       Front       Back
       (Red)      (Green)
```

## Rotation Animation

The cube rotates continuously on two axes:

```
Time:    0s          1s          2s          3s
       
Y-axis  0°    →    28.6°   →   57.3°   →   85.9°   (0.5 rad/s)
X-axis  0°    →    17.2°   →   34.4°   →   51.6°   (0.3 rad/s)

Creates a diagonal tumbling effect
```

## Camera Setup

```
                      Screen
                        ║
                        ║
                        ║
     Cube               ║
       @     ←──────────╫──── View Direction
    (0,0,0)    3 units  ║
                        ║
                     Camera
                    (0, 0, 3)

Field of View: 45°
Aspect Ratio: 800:600 (4:3)
Near Plane: 0.1
Far Plane: 100.0
```

## Transformation Pipeline

```
  Local Space        World Space        View Space       Clip Space
  (Cube Verts)  →   (Rotated)     →   (Camera)     →    (Screen)
       │                  │               │                │
   [-0.5,0.5]³      Model Matrix    View Matrix    Projection Matrix
                    (Rotation)      (LookAt)        (Perspective)
                         │               │                │
                         └───────────────┴────────────────┘
                                       │
                                  MVP Matrix
                             (sent to vertex shader)
```

## Vertex Data Structure

Each vertex contains:
```
Position: [f32; 3]  // x, y, z coordinates
Color:    [f32; 3]  // r, g, b values (0.0-1.0)
```

Example vertices for front face (Red):
```
v0: position=[-0.5, -0.5,  0.5], color=[1.0, 0.0, 0.0]  // Bottom-left
v1: position=[ 0.5, -0.5,  0.5], color=[1.0, 0.0, 0.0]  // Bottom-right
v2: position=[ 0.5,  0.5,  0.5], color=[1.0, 0.0, 0.0]  // Top-right
v3: position=[-0.5, -0.5,  0.5], color=[1.0, 0.0, 0.0]  // Bottom-left
v4: position=[ 0.5,  0.5,  0.5], color=[1.0, 0.0, 0.0]  // Top-right
v5: position=[-0.5,  0.5,  0.5], color=[1.0, 0.0, 0.0]  // Top-left

Two triangles: (v0,v1,v2) and (v3,v4,v5)
```

## Rendering Pipeline Flow

```
┌─────────────────┐
│  Application    │
│  (main.rs)      │
└────────┬────────┘
         │
         │ 1. Calculate time-based rotation
         │ 2. Build MVP matrix
         │
         ▼
┌─────────────────┐
│  Camera         │
│  (camera.rs)    │
└────────┬────────┘
         │
         │ 3. Push MVP to GPU
         │
         ▼
┌─────────────────┐
│ Vertex Shader   │
│ (shaders.rs)    │
│                 │
│ gl_Position =   │
│  mvp * position │
└────────┬────────┘
         │
         │ 4. Transform vertices
         │ 5. Pass colors through
         │
         ▼
┌─────────────────┐
│Fragment Shader  │
│ (shaders.rs)    │
│                 │
│ out_color =     │
│  vec4(color,1.0)│
└────────┬────────┘
         │
         │ 6. Rasterize triangles
         │ 7. Interpolate colors
         │
         ▼
┌─────────────────┐
│   Swapchain     │
│   (Screen)      │
└─────────────────┘
```

## File Organization

```
src/main/rust/
│
├── main.rs                    [438 lines]
│   ├── App struct
│   ├── Vulkan initialization
│   ├── Event loop
│   └── Render function
│
└── client/
    ├── mod.rs                 [1 line]
    │   └── pub mod renderer;
    │
    └── renderer/
        ├── mod.rs             [3 lines]
        │   ├── pub mod cube;
        │   ├── pub mod camera;
        │   └── pub mod shaders;
        │
        ├── cube.rs            [48 lines]
        │   ├── CubeVertex struct
        │   └── create_cube_vertices()
        │
        ├── camera.rs          [47 lines]
        │   ├── Camera struct
        │   ├── get_model_matrix()
        │   ├── get_view_matrix()
        │   ├── get_projection_matrix()
        │   └── get_mvp_matrix()
        │
        └── shaders.rs         [37 lines]
            ├── vertex_shader module
            └── fragment_shader module

Total: ~625 lines of Rust code
```

## Expected Visual Output

When running, you should see:
- A colorful cube in the center of the window
- Continuous rotation creating a tumbling effect
- Each face distinctly colored (6 different colors)
- Smooth animation at 60+ FPS
- Dark blue background (RGB: 0.1, 0.1, 0.15)

## Comparison to Previous Implementation

### Before (Rectangle):
- 2D rendering
- 6 vertices (2 triangles)
- Single color (orange)
- No transformations
- ~500 lines in one file

### After (Rotating Cube):
- 3D rendering
- 36 vertices (12 triangles, 6 faces)
- 6 different colors
- MVP matrix transformations
- Time-based rotation animation
- ~625 lines across 6 modular files
- Structure mirrors Java implementation
