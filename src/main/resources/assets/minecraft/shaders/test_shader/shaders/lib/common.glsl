// Common definitions
#define PI 3.14159265359
#define TAU 6.28318530718

vec3 calculateLighting(vec3 normal) {
    return normal * 0.5 + 0.5;
}
