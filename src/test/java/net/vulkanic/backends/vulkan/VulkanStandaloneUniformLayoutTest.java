package net.vulkanic.backends.vulkan;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VulkanStandaloneUniformLayoutTest {

    @Test
    public void packsScalarIntoVec3PaddingBeforeMatrix() {
        Map<String, List<Integer>> offsets = VulkanBackend.collectStandaloneUniformOffsets(List.of(
            "vec3 previousCameraPositionFract;",
            "int renderStage;",
            "mat4 gbufferProjection;"
        ));

        assertEquals(List.of(0), offsets.get("previousCameraPositionFract"));
        assertEquals(List.of(12), offsets.get("renderStage"));
        assertEquals(List.of(16), offsets.get("gbufferProjection"));
    }

    @Test
    public void matchesShaderPackCameraTailLayout() {
        Map<String, List<Integer>> offsets = VulkanBackend.collectStandaloneUniformOffsets(List.of(
            "ivec3 cameraPositionInt;",
            "ivec3 previousCameraPositionInt;",
            "vec3 cameraPositionFract;",
            "vec3 previousCameraPositionFract;",
            "int renderStage;",
            "mat4 gbufferProjection;",
            "mat4 gbufferProjectionInverse;",
            "int dhRenderDistance;",
            "mat4 dhProjection;",
            "mat4 dhProjectionInverse;"
        ));

        assertEquals(List.of(0), offsets.get("cameraPositionInt"));
        assertEquals(List.of(16), offsets.get("previousCameraPositionInt"));
        assertEquals(List.of(32), offsets.get("cameraPositionFract"));
        assertEquals(List.of(48), offsets.get("previousCameraPositionFract"));
        assertEquals(List.of(60), offsets.get("renderStage"));
        assertEquals(List.of(64), offsets.get("gbufferProjection"));
        assertEquals(List.of(128), offsets.get("gbufferProjectionInverse"));
        assertEquals(List.of(192), offsets.get("dhRenderDistance"));
        assertEquals(List.of(208), offsets.get("dhProjection"));
        assertEquals(List.of(272), offsets.get("dhProjectionInverse"));
    }

    @Test
    public void keepsVec3ArraysOnSixteenByteStride() {
        Map<String, List<Integer>> offsets = VulkanBackend.collectStandaloneUniformOffsets(List.of(
            "vec3 samples[2];",
            "float afterSamples;"
        ));

        assertEquals(List.of(0), offsets.get("samples"));
        assertEquals(List.of(32), offsets.get("afterSamples"));
    }
}
