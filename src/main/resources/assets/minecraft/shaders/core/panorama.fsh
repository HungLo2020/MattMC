#version 330

uniform sampler2D Sampler0;

in vec3 texCoord0;

out vec4 fragColor;

void main() {
    vec3 direction = normalize(texCoord0);
    vec3 absDirection = abs(direction);
    vec2 uv;
    float faceIndex;

    if (absDirection.x >= absDirection.y && absDirection.x >= absDirection.z) {
        float invMajorAxis = 0.5 / absDirection.x;
        if (direction.x > 0.0) {
            faceIndex = 0.0;
            uv = vec2(-direction.z, -direction.y) * invMajorAxis + 0.5;
        } else {
            faceIndex = 1.0;
            uv = vec2(direction.z, -direction.y) * invMajorAxis + 0.5;
        }
    } else if (absDirection.y >= absDirection.z) {
        float invMajorAxis = 0.5 / absDirection.y;
        if (direction.y > 0.0) {
            faceIndex = 2.0;
            uv = vec2(direction.x, direction.z) * invMajorAxis + 0.5;
        } else {
            faceIndex = 3.0;
            uv = vec2(direction.x, -direction.z) * invMajorAxis + 0.5;
        }
    } else {
        float invMajorAxis = 0.5 / absDirection.z;
        if (direction.z > 0.0) {
            faceIndex = 4.0;
            uv = vec2(direction.x, -direction.y) * invMajorAxis + 0.5;
        } else {
            faceIndex = 5.0;
            uv = vec2(-direction.x, -direction.y) * invMajorAxis + 0.5;
        }
    }

    uv = clamp(uv, vec2(0.0), vec2(1.0));
    uv.y = (faceIndex + uv.y) / 6.0;
    fragColor = texture(Sampler0, uv);
}
