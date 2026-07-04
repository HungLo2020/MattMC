package net.vulkanic.backends.vulkan;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;

import net.vulkanic.VulkanicUniformReflectionType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

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

    @Test
    public void writesAndReadsVec4ArrayElements() {
        ByteBuffer backingData = ByteBuffer.allocate(64).order(ByteOrder.nativeOrder());
        VulkanBackend.StandaloneUniformField field = new VulkanBackend.StandaloneUniformField(
            "clipPlanes",
            VulkanicUniformReflectionType.FLOAT_VEC4,
            new int[] {0},
            2,
            16
        );

        VulkanBackend.writeFloatUniform(field, backingData, new float[] {
            1.0F, 2.0F, 3.0F, 4.0F,
            5.0F, 6.0F, 7.0F, 8.0F
        });

        assertEquals(1.0F, backingData.getFloat(0));
        assertEquals(4.0F, backingData.getFloat(12));
        assertEquals(5.0F, backingData.getFloat(16));
        assertEquals(8.0F, backingData.getFloat(28));
        assertArrayEquals(
            new float[] {1.0F, 2.0F, 3.0F, 4.0F, 5.0F, 6.0F, 7.0F, 8.0F},
            VulkanBackend.readStandaloneUniformFloats(field, backingData, 0)
        );
    }

    @Test
    public void writesAndReadsVec3ArrayUsingStd140Stride() {
        ByteBuffer backingData = ByteBuffer.allocate(64).order(ByteOrder.nativeOrder());
        VulkanBackend.StandaloneUniformField field = new VulkanBackend.StandaloneUniformField(
            "samples",
            VulkanicUniformReflectionType.FLOAT_VEC3,
            new int[] {0},
            2,
            16
        );

        VulkanBackend.writeFloatUniform(field, backingData, new float[] {
            1.0F, 2.0F, 3.0F,
            4.0F, 5.0F, 6.0F
        });

        assertEquals(1.0F, backingData.getFloat(0));
        assertEquals(3.0F, backingData.getFloat(8));
        assertEquals(0.0F, backingData.getFloat(12));
        assertEquals(4.0F, backingData.getFloat(16));
        assertEquals(6.0F, backingData.getFloat(24));
        assertArrayEquals(
            new float[] {1.0F, 2.0F, 3.0F, 4.0F, 5.0F, 6.0F},
            VulkanBackend.readStandaloneUniformFloats(field, backingData, 0)
        );
    }
}
