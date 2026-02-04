// Rust FFI implementation of RayCastUtil
// This provides C-compatible functions that can be called via Java FFM
//
// Original Java class: com.seibel.distanthorizons.core.util.RayCastUtil
// Author: James Seibel (original Java), migrated to Rust for performance

/// This function should work for any 2 perpendicular axis, X and Y could be replaced with X, Y, or Z
///
/// # Arguments
/// * `ray_x` - the ray's starting X position
/// * `ray_y` - the ray's starting Z position
/// * `ray_x_direction` - the ray's X direction
/// * `ray_y_direction` - the ray's Y direction
/// * `square_min_x` - the square's X corner closest to negative infinity
/// * `square_min_y` - the square's Y corner closest to negative infinity
/// * `square_width` - the width of the square
#[no_mangle]
pub extern "C" fn raycastutil_ray_intersects_square(
    ray_x: f64,
    ray_y: f64,
    ray_x_direction: f64,
    ray_y_direction: f64,
    square_min_x: f64,
    square_min_y: f64,
    square_width: f64,
) -> i32 {
    let rounding_value = 0.05;

    // determine the other corner of the square
    let square_max_x = square_min_x + square_width;
    let square_max_y = square_min_y + square_width;

    // check if the ray originates in the square
    if ray_x >= square_min_x
        && ray_x <= square_max_x
        && ray_y >= square_min_y
        && ray_y <= square_max_y
    {
        return 1; // true
    }

    if is_roughly(ray_x_direction, 0.0, rounding_value)
        && is_roughly(ray_y_direction, 0.0, rounding_value)
    {
        // slope is in a direction perpendicular to this ray
        // this ray can be treated like a point,
        // checking if the point originated inside the square
        // should catch if this was true
        return 0; // false
    } else if is_roughly(ray_y_direction.abs(), 1.0, rounding_value)
        || is_roughly(ray_x_direction.abs(), 0.0, rounding_value)
    {
        // slope is straight up or down

        // is the ray pointing towards the square?
        if (ray_y_direction > 0.0 && ray_y > square_max_y)
            || // up
            (ray_y_direction < 0.0 && ray_y < square_min_y)
        {
            // down
            // the ray is pointing away from the square
            return 0; // false
        } else {
            // check if the ray's X value is between the square's left and right sides
            return if ray_x >= square_min_x && ray_x <= square_max_x {
                1 // true
            } else {
                0 // false
            };
        }
    } else if is_roughly(ray_x_direction.abs(), 1.0, rounding_value)
        || is_roughly(ray_y_direction, 0.0, rounding_value)
    {
        // slope is 0 (horizontal line)

        // is the ray pointing towards the square?
        if (ray_x_direction > 0.0 && ray_x > square_max_x)
            || // right
            (ray_x_direction < 0.0 && ray_x < square_min_x)
        {
            // left
            // the ray is pointing away from the square
            return 0; // false
        } else {
            // check if the ray's Y value is between the square's top and bottom sides
            return if ray_y >= square_min_y && ray_y <= square_max_y {
                1 // true
            } else {
                0 // false
            };
        }
    } else {
        // slope is a valid range (between -infinity and infinity)
        let slope = ray_y_direction / ray_x_direction;

        // move the square into ray space (where the ray is at the origin)
        let square_min_x = square_min_x - ray_x;
        let square_max_x = square_max_x - ray_x;

        let square_min_y = square_min_y - ray_y;
        let square_max_y = square_max_y - ray_y;

        let mut intersects_x = false;
        let mut intersects_y = false;

        // ray Y intersect
        // y = mx
        let y_intersect_min = slope * square_min_x;
        let y_intersect_max = slope * square_max_x;

        // does the intersection happen before the ray's origin?
        if (ray_y_direction > 0.0 && (y_intersect_min <= ray_y && y_intersect_max <= ray_y))
            || // moving in pos Y direction
            (ray_y_direction < 0.0 && (y_intersect_min >= ray_y && y_intersect_max >= ray_y))
        {
            // moving in neg Y direction
            return 0; // false
        }
        // does the line intersect the square?
        else if y_intersect_min >= square_min_y && y_intersect_min <= square_max_y {
            intersects_y = true;
        } else if y_intersect_max >= square_min_y && y_intersect_max <= square_max_y {
            intersects_y = true;
        }

        // ray X intersect
        // x = y/m
        let x_intersect_min = square_min_y / slope;
        let x_intersect_max = square_max_y / slope;

        // does the intersection happen before the ray's origin?
        if (ray_x_direction > 0.0 && (x_intersect_min <= ray_x && x_intersect_max <= ray_x))
            || // moving in pos X direction
            (ray_x_direction < 0.0 && (x_intersect_min >= ray_x && x_intersect_max >= ray_x))
        {
            // moving in neg X direction
            return 0; // false
        }
        // does the line intersect the square?
        else if x_intersect_min >= square_min_x && x_intersect_min <= square_max_x {
            intersects_x = true;
        } else if x_intersect_max >= square_min_x && x_intersect_max <= square_max_x {
            intersects_x = true;
        }

        // if the ray intersects both the top and side of the square, that means
        // the ray intersects the square as a whole
        return if intersects_x && intersects_y {
            1 // true
        } else {
            0 // false
        };
    }
}

/// used to get around floating point number rounding errors
#[inline]
fn is_roughly(input: f64, equals_val: f64, error_value: f64) -> bool {
    input >= equals_val - error_value && input <= equals_val + error_value
}
