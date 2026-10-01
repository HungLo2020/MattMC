#version 450
layout(local_size_x = 8, local_size_y = 8, local_size_z = 8) in;
layout(set = 0, binding = 0, r8ui) uniform readonly uimage3D Occupancy;
layout(set = 0, binding = 1, rgba16f) uniform readonly image3D SourceLight;
layout(set = 0, binding = 2, rgba16f) uniform writeonly image3D TargetLight;
layout(set = 0, binding = 3, std140) uniform Uniforms3 {
    vec4 emission[256];
};
layout(set = 0, binding = 4, std140) uniform Uniforms4 {
    vec4 tint[20];
};
layout(set = 0, binding = 5, std140) uniform Uniforms5 {
    ivec4 PreviousCameraMinusCurrent;
    ivec4 VolumeExtent;
    ivec4 UpdateSchedule;
    vec4 CameraForward;
};
vec4 sampleReprojectedLight(ivec3 position, ivec3 extent) {
    // Complementary's un-clamped texelFetch of previousPos is meaningful only
    // inside the prior camera-relative field. Make its edge behavior explicit
    // and backend-neutral rather than duplicating the nearest border texel.
    if (any(lessThan(position, ivec3(0))) || any(greaterThanEqual(position, extent))) {
        return vec4(0.0);
    }
    return imageLoad(SourceLight, position);
}
vec4 sampleNeighborLight(ivec3 position, ivec3 extent) {
    // `GetLightAverage` clamps just its six neighbor probes.
    return imageLoad(SourceLight, clamp(position, ivec3(0), extent - 1));
}
void main() {
    ivec3 position = ivec3(gl_GlobalInvocationID);
    ivec3 extent = VolumeExtent.xyz;
    if (any(greaterThanEqual(position, extent))) return;
    ivec3 previousPosition = position - PreviousCameraMinusCurrent.xyz;
    bool preserveBehindView = UpdateSchedule.z != 0 &&
        (abs(position - extent / 2).x + abs(position - extent / 2).y + abs(position - extent / 2).z > 16) &&
        dot(normalize(vec3(position) / vec3(extent) - 0.5), normalize(CameraForward.xyz)) < 0.0;
    if (preserveBehindView) {
        imageStore(TargetLight, position, sampleReprojectedLight(previousPosition, extent));
        return;
    }
    bool halfRatePreserved = UpdateSchedule.x != 0 &&
        ((UpdateSchedule.y == 0 && position.x * 2 < extent.x) ||
         (UpdateSchedule.y != 0 && position.x * 2 > extent.x));
    if (halfRatePreserved) {
        imageStore(TargetLight, position, sampleReprojectedLight(previousPosition, extent));
        return;
    }
    vec4 light = sampleReprojectedLight(previousPosition, extent);
    light += sampleNeighborLight(previousPosition + ivec3(1, 0, 0), extent);
    light += sampleNeighborLight(previousPosition + ivec3(-1, 0, 0), extent);
    light += sampleNeighborLight(previousPosition + ivec3(0, 1, 0), extent);
    light += sampleNeighborLight(previousPosition + ivec3(0, -1, 0), extent);
    light += sampleNeighborLight(previousPosition + ivec3(0, 0, 1), extent);
    light += sampleNeighborLight(previousPosition + ivec3(0, 0, -1), extent);
    light /= 7.2;
    uint voxel = imageLoad(Occupancy, position).r;
    if (voxel == 1u) {
        light = vec4(0.0);
    } else if (voxel >= 200u) {
        light.rgb *= tint[min(voxel - 200u, 19u)].rgb;
    } else if (voxel > 1u && voxel < 200u) {
        vec4 color = emission[voxel];
        light = max(light, vec4(color.rgb * color.rgb, color.a));
    }
    imageStore(TargetLight, position, light);
}
