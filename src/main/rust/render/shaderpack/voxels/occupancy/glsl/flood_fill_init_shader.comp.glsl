#version 450
layout(local_size_x = 8, local_size_y = 8, local_size_z = 8) in;
layout(set = 0, binding = 0, r8ui) uniform readonly uimage3D Occupancy;
layout(set = 0, binding = 1, rgba16f) uniform writeonly image3D TargetLight;
layout(set = 0, binding = 2, std140) uniform Uniforms2 {
    vec4 emission[256];
};
void main() {
    ivec3 position = ivec3(gl_GlobalInvocationID);
    if (any(greaterThanEqual(position, imageSize(Occupancy)))) return;
    uint voxel = imageLoad(Occupancy, position).r;
    vec4 light = vec4(0.0);
    if (voxel > 1u && voxel < 200u) {
        vec4 color = emission[voxel];
        light = vec4(color.rgb * color.rgb, color.a);
    }
    imageStore(TargetLight, position, light);
}
