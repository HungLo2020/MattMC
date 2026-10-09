package net.vulkanic.bridge;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static net.vulkanic.bridge.VulkanicGalBridge.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldShaderEnvironmentEncodingTest {
    @Test void nativeLayoutAndEncoderPreserveCopiedShadowDistanceAndAdjacentFog() throws Exception {
        try (var bridge = VulkanicGalBridge.create("rust-vulkan"); var arena = Arena.ofConfined()) {
            assertEquals(75, ABI_VERSION);
            var layout = Struct.WORLD_SHADER_ENVIRONMENT_FRAME;
            var item = arena.allocate(layout.byteSize(), 8);
            var encode = VulkanicGalBridge.class.getDeclaredMethod("encodeShaderEnvironment",
                Arena.class, MemorySegment.class, WorldShaderEnvironmentFrameRecord.class);
            encode.setAccessible(true);
            var components = WorldShaderEnvironmentFrameRecord.class.getRecordComponents();
            var constructor = WorldShaderEnvironmentFrameRecord.class.getDeclaredConstructor(
                Arrays.stream(components).map(c -> c.getType()).toArray(Class<?>[]::new));
            Object[] values = new Object[components.length];
            var disabled = WorldShaderEnvironmentFrameRecord.disabled();
            for (int i = 0; i < components.length; i++) {
                values[i] = components[i].getAccessor().invoke(disabled);
                if (components[i].getName().equals("fogCloudsEnd")) values[i] = 123.5F;
            }
            assertEquals("configuredShadowDistanceChunks", components[components.length - 1].getName());
            for (int distance : new int[]{0,2,32,-1,Integer.MIN_VALUE,Integer.MAX_VALUE}) {
                values[values.length - 1] = distance;
                var copied = constructor.newInstance(values);
                item.fill((byte) 0x7f);
                encode.invoke(null, arena, item, copied);
                assertEquals(layout.byteSize(), item.get(ValueLayout.JAVA_INT, layout.offset(0)));
                assertEquals(0, item.get(ValueLayout.JAVA_INT, layout.offset(1)));
                assertEquals(123.5F, item.get(ValueLayout.JAVA_FLOAT, layout.offset(63)));
                assertEquals(distance, item.get(ValueLayout.JAVA_INT, layout.offset(64)));
            }
            encode.invoke(null, arena, item, disabled);
            assertEquals(0, item.get(ValueLayout.JAVA_INT, layout.offset(64)));
        }
    }
}
