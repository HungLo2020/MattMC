package net.vulkanic.backends.opengl;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GraphicsAuditFrozenTerrainUvTest {
    @Test void allVerticesRetainPositionAndUvAndBoundWrites() throws Exception {
        String original=Files.readString(Path.of("src/main/resources/assets/sodium/shaders/blocks/block_layer_opaque.vsh"));
        String probe=GraphicsAuditFrozenTerrainUv.instrumentAllVertices(original);
        assertEquals(1,probe.split("gl_Position =",-1).length-1);
        assertEquals(1,probe.split("v_TexCoord =",-1).length-1);
        assertTrue(probe.contains("if (slot < 128u)"));
        assertTrue(probe.contains("floatBitsToUint(gl_Position[c])"));
        int[] words=new int[1537]; words[0]=1; words[1]=-1;
        assertTrue(GraphicsAuditFrozenTerrainUv.vertexReceipt(words).contains("[4294967295,0,"));
        words[0]=129;
        assertTrue(GraphicsAuditFrozenTerrainUv.vertexReceipt(words).contains("\"truncated\":true"));
        assertThrows(IllegalArgumentException.class,()->GraphicsAuditFrozenTerrainUv.vertexReceipt(new int[1]));
    }
    @Test void instrumentationRetainsNormalOutputAndIsBounded() throws Exception {
        String original=Files.readString(Path.of("src/main/resources/assets/sodium/shaders/blocks/block_layer_opaque.fsh"));
        String probe=GraphicsAuditFrozenTerrainUv.instrument(original);
        String output="fragColor = _linearFog(color, v_FragDistance, u_FogColor, u_EnvironmentFog, u_RenderFog);";
        assertTrue(probe.contains(output));
        assertEquals(1,probe.split("fragColor =",-1).length-1);
        assertTrue(probe.contains("if (slot < 32u)"));
        assertTrue(probe.contains("ivec2(764,399)"));
        assertThrows(IllegalArgumentException.class,()->GraphicsAuditFrozenTerrainUv.instrument("bad"));
    }
    @Test void receiptsAreExactAndOverflowIsExplicit() {
        int[] words=new int[225];words[0]=1;words[1]=0x3f533ffe;words[2]=-1;words[3]=42;
        words[4]=Integer.MIN_VALUE;words[5]=0x3f800000;words[6]=2;words[7]=3;
        assertEquals("{\"count\":1,\"truncated\":false,\"samples\":[[1062420478,-1,42]],\"clipSamples\":[[-2147483648,1065353216,2,3]]}",
            GraphicsAuditFrozenTerrainUv.receipt(words));
        words[0]=33;assertTrue(GraphicsAuditFrozenTerrainUv.receipt(words).contains("\"truncated\":true"));
        assertThrows(IllegalArgumentException.class,()->GraphicsAuditFrozenTerrainUv.receipt(new int[3]));
    }
    @Test void vertexObservationPreservesTheNormalPositionAssignment() throws Exception {
        String original=Files.readString(Path.of("src/main/resources/assets/sodium/shaders/blocks/block_layer_opaque.vsh"));
        String probe=GraphicsAuditFrozenTerrainUv.instrumentVertex(original);
        String position="gl_Position = u_ProjectionMatrix * u_ModelViewMatrix * vec4(position, 1.0);";
        assertTrue(probe.contains(position));
        assertEquals(1,probe.split("gl_Position =",-1).length-1);
        assertTrue(probe.contains("flat out vec4 audit_clip;"));
        assertTrue(probe.indexOf("audit_clip = gl_Position;") > probe.indexOf(position));
        assertThrows(IllegalArgumentException.class,()->GraphicsAuditFrozenTerrainUv.instrumentVertex("bad"));
    }
}
