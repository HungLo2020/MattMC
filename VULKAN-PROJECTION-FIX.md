# Vulkan Projection Matrix Fix

## Problem

The rotating cube appeared warped with a "fisheye" effect, allowing 5 sides to be visible simultaneously from certain angles. This was caused by using an OpenGL-style projection matrix in a Vulkan rendering context.

## Root Cause

### Coordinate System Differences

**OpenGL:**
- Clip space Y-axis: bottom (-1) to top (+1)
- Clip space depth (Z): near (-1) to far (+1)

**Vulkan:**
- Clip space Y-axis: top (0) to bottom (1) - **INVERTED**
- Clip space depth (Z): near (0) to far (1) - **DIFFERENT RANGE**

### The Bug

The original code used `glam::Mat4::perspective_rh()`, which generates an OpenGL-compatible projection matrix. When this matrix is used in Vulkan:

1. The Y-axis is not inverted, causing the image to be flipped
2. The depth mapping is incorrect, causing severe perspective distortion

This combination creates the "fisheye" warping effect where the cube appears distorted and multiple faces are visible when they shouldn't be.

## Solution

### Custom Vulkan Projection Matrix

We replaced the OpenGL-style projection with a custom Vulkan-compatible matrix:

```rust
pub fn get_projection_matrix(&self, aspect_ratio: f32) -> Mat4 {
    // Vulkan-compatible perspective projection
    let fov_y = 45.0_f32.to_radians();
    let near = 0.1;
    let far = 100.0;
    
    let f = 1.0 / (fov_y / 2.0).tan();
    
    Mat4::from_cols_array(&[
        f / aspect_ratio, 0.0,  0.0,                           0.0,
        0.0,             -f,    0.0,                           0.0,  // -f for Y-flip
        0.0,              0.0,  far / (near - far),           -1.0,
        0.0,              0.0,  (near * far) / (near - far),   0.0,
    ])
}
```

### Key Changes

1. **Y-axis flip**: The matrix uses `-f` instead of `f` in the [1,1] position
   - This inverts the Y-axis to match Vulkan's top-to-bottom convention
   
2. **Depth range mapping**: The Z-column is adjusted for Vulkan's [0, 1] depth range
   - `far / (near - far)` in [2,2] position
   - `(near * far) / (near - far)` in [3,2] position

## Matrix Breakdown

### Standard Perspective Matrix Structure

```
Column-major order (as used by glam):

[  f/aspect    0      0         0   ]
[     0       ±f      0         0   ]  ← Sign determines Y direction
[     0        0     z_map     -1   ]
[     0        0     z_offset   0   ]
```

Where:
- `f = 1 / tan(fov_y / 2)` - focal length factor
- `aspect` - width / height ratio
- `z_map` and `z_offset` - depth range mapping

### OpenGL vs Vulkan

**OpenGL** (what `perspective_rh` generates):
```
[2,2] = -(far + near) / (far - near)
[3,2] = -(2 * far * near) / (far - near)
[1,1] = f  (positive)
```

**Vulkan** (our custom matrix):
```
[2,2] = far / (near - far)
[3,2] = (near * far) / (near - far)
[1,1] = -f  (negative for Y-flip)
```

## Visual Impact

### Before (Warped):
- Cube appears distorted with fisheye effect
- Can see 5 sides simultaneously from certain angles
- Perspective feels "wrong" and exaggerated

### After (Fixed):
- Cube renders with proper perspective
- Only 3 sides visible at most (as expected for a cube)
- Natural perspective that matches real-world viewing

## Testing

The fix can be verified by:
1. Building and running: `cargo run`
2. Observing the rotating cube
3. Confirming that at most 3 faces are visible at any time
4. Verifying the perspective looks natural (not fisheye-distorted)

## Future Considerations

This same Vulkan projection matrix should be used for any future 3D rendering in this project. If switching back to OpenGL, the standard `glam::Mat4::perspective_rh()` would be appropriate.

## References

- [Vulkan Coordinate Systems](https://www.saschawillems.de/blog/2019/03/29/flipping-the-vulkan-viewport/)
- [OpenGL vs Vulkan Projection Matrices](https://matthewwellings.com/blog/the-new-vulkan-coordinate-system/)
- [glam Matrix Documentation](https://docs.rs/glam/latest/glam/struct.Mat4.html)
