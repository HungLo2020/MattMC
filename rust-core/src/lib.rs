use jni::JNIEnv;
use jni::objects::JClass;
use jni::sys::{jint, jlong, jfloat, jdouble};

/// Fast floor function for floats
#[no_mangle]
pub extern "system" fn Java_net_minecraft_util_MthRust_floor__F(
    _env: JNIEnv,
    _class: JClass,
    value: jfloat,
) -> jint {
    let i = value as jint;
    if value < i as jfloat {
        i - 1
    } else {
        i
    }
}

/// Fast floor function for doubles
#[no_mangle]
pub extern "system" fn Java_net_minecraft_util_MthRust_floor__D(
    _env: JNIEnv,
    _class: JClass,
    value: jdouble,
) -> jint {
    let i = value as jint;
    if value < i as jdouble {
        i - 1
    } else {
        i
    }
}

/// Fast lfloor function (long floor) for doubles
#[no_mangle]
pub extern "system" fn Java_net_minecraft_util_MthRust_lfloor(
    _env: JNIEnv,
    _class: JClass,
    value: jdouble,
) -> jlong {
    let l = value as jlong;
    if value < l as jdouble {
        l - 1
    } else {
        l
    }
}

/// Fast ceil function for floats
#[no_mangle]
pub extern "system" fn Java_net_minecraft_util_MthRust_ceil__F(
    _env: JNIEnv,
    _class: JClass,
    value: jfloat,
) -> jint {
    let i = value as jint;
    if value > i as jfloat {
        i + 1
    } else {
        i
    }
}

/// Fast ceil function for doubles
#[no_mangle]
pub extern "system" fn Java_net_minecraft_util_MthRust_ceil__D(
    _env: JNIEnv,
    _class: JClass,
    value: jdouble,
) -> jint {
    let i = value as jint;
    if value > i as jdouble {
        i + 1
    } else {
        i
    }
}

/// Fast ceilLong function for doubles
#[no_mangle]
pub extern "system" fn Java_net_minecraft_util_MthRust_ceilLong(
    _env: JNIEnv,
    _class: JClass,
    value: jdouble,
) -> jlong {
    let l = value as jlong;
    if value > l as jdouble {
        l + 1
    } else {
        l
    }
}

/// Clamp int value between min and max
#[no_mangle]
pub extern "system" fn Java_net_minecraft_util_MthRust_clamp__III(
    _env: JNIEnv,
    _class: JClass,
    value: jint,
    min: jint,
    max: jint,
) -> jint {
    value.max(min).min(max)
}

/// Clamp long value between min and max
#[no_mangle]
pub extern "system" fn Java_net_minecraft_util_MthRust_clamp__JJJ(
    _env: JNIEnv,
    _class: JClass,
    value: jlong,
    min: jlong,
    max: jlong,
) -> jlong {
    value.max(min).min(max)
}

/// Clamp float value between min and max
#[no_mangle]
pub extern "system" fn Java_net_minecraft_util_MthRust_clamp__FFF(
    _env: JNIEnv,
    _class: JClass,
    value: jfloat,
    min: jfloat,
    max: jfloat,
) -> jfloat {
    if value < min {
        min
    } else {
        value.min(max)
    }
}

/// Clamp double value between min and max
#[no_mangle]
pub extern "system" fn Java_net_minecraft_util_MthRust_clamp__DDD(
    _env: JNIEnv,
    _class: JClass,
    value: jdouble,
    min: jdouble,
    max: jdouble,
) -> jdouble {
    if value < min {
        min
    } else {
        value.min(max)
    }
}

/// Absolute value for floats
#[no_mangle]
pub extern "system" fn Java_net_minecraft_util_MthRust_abs__F(
    _env: JNIEnv,
    _class: JClass,
    value: jfloat,
) -> jfloat {
    value.abs()
}

/// Absolute value for ints
#[no_mangle]
pub extern "system" fn Java_net_minecraft_util_MthRust_abs__I(
    _env: JNIEnv,
    _class: JClass,
    value: jint,
) -> jint {
    value.abs()
}

/// Square a float
#[no_mangle]
pub extern "system" fn Java_net_minecraft_util_MthRust_square__F(
    _env: JNIEnv,
    _class: JClass,
    value: jfloat,
) -> jfloat {
    value * value
}

/// Square a double
#[no_mangle]
pub extern "system" fn Java_net_minecraft_util_MthRust_square__D(
    _env: JNIEnv,
    _class: JClass,
    value: jdouble,
) -> jdouble {
    value * value
}

/// Square an int
#[no_mangle]
pub extern "system" fn Java_net_minecraft_util_MthRust_square__I(
    _env: JNIEnv,
    _class: JClass,
    value: jint,
) -> jint {
    value * value
}

/// Square a long
#[no_mangle]
pub extern "system" fn Java_net_minecraft_util_MthRust_square__J(
    _env: JNIEnv,
    _class: JClass,
    value: jlong,
) -> jlong {
    value * value
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_floor_float() {
        // Note: These tests are for the core logic, not JNI
        let result = {
            let value = 3.7f32;
            let i = value as i32;
            if value < i as f32 { i - 1 } else { i }
        };
        assert_eq!(result, 3);

        let result = {
            let value = -3.7f32;
            let i = value as i32;
            if value < i as f32 { i - 1 } else { i }
        };
        assert_eq!(result, -4);
    }

    #[test]
    fn test_ceil_float() {
        let result = {
            let value = 3.2f32;
            let i = value as i32;
            if value > i as f32 { i + 1 } else { i }
        };
        assert_eq!(result, 4);

        let result = {
            let value = -3.7f32;
            let i = value as i32;
            if value > i as f32 { i + 1 } else { i }
        };
        assert_eq!(result, -3);
    }

    #[test]
    fn test_clamp() {
        assert_eq!(5i32.max(0).min(10), 5);
        assert_eq!((-5i32).max(0).min(10), 0);
        assert_eq!(15i32.max(0).min(10), 10);
    }

    #[test]
    fn test_square() {
        assert_eq!(5i32 * 5i32, 25);
        assert_eq!(3.0f32 * 3.0f32, 9.0f32);
    }
}
