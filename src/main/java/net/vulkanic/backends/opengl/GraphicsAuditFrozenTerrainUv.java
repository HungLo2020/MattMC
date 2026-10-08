package net.vulkanic.backends.opengl;

import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.ARBDirectStateAccess;
import org.lwjgl.opengl.ARBShaderImageLoadStore;

/** Frozen-only opt-in measurement. Retains normal position/color and one bounded owned buffer. */
public final class GraphicsAuditFrozenTerrainUv {
    private static final int BINDING = 15, LIMIT = 32, SAMPLE_WORDS = 7, WORDS = 1 + LIMIT * SAMPLE_WORDS;
    private static final int VERTEX_LIMIT = 128, VERTEX_WORDS = 12;
    private static boolean vertices() { return Boolean.getBoolean("mattmc.dev.graphicsAuditFrozenTerrainVertices"); }
    private static int bufferWords() { return WORDS + (vertices() ? 1 + VERTEX_LIMIT * VERTEX_WORDS : 0); }
    private static int buffer;
    private GraphicsAuditFrozenTerrainUv() {}
    public static boolean enabled() { return Boolean.getBoolean("mattmc.dev.graphicsAuditFrozenTerrainUv"); }

    public static String source(ResourceLocation name, String source) {
        if (!enabled()) return source;
        return switch (name.toString()) {
            case "sodium:blocks/block_layer_opaque.fsh" -> instrument(source);
            case "sodium:blocks/block_layer_opaque.vsh" -> vertices() ? instrumentAllVertices(source) : instrumentVertex(source);
            default -> source;
        };
    }

    static String instrumentVertex(String source) {
        String version = "#version 330 core";
        String position = "gl_Position = u_ProjectionMatrix * u_ModelViewMatrix * vec4(position, 1.0);";
        if (!source.contains(version) || !source.contains(position))
            throw new IllegalArgumentException("Clip observation requires the unchanged Frozen terrain vertex shader");
        return source.replace(version, version + "\nflat out vec4 audit_clip;")
            .replace(position, position + "\n    audit_clip = gl_Position;");
    }

    static String instrumentAllVertices(String source) {
        String original = instrumentVertex(source);
        String declarations = "#version 330 core\n#extension GL_ARB_shader_storage_buffer_object : require\n"
            + "#extension GL_ARB_shading_language_420pack : require\n"
            + "layout(std430, binding = 15) buffer AuditTerrainVertices { uint vertex_words[]; };";
        String anchor = "v_TexCoord = (_vert_tex_diffuse_coord_bias * u_TexCoordShrink) + _vert_tex_diffuse_coord;";
        if (!original.contains(anchor)) throw new IllegalArgumentException("vertex observation requires original UV assignment");
        String observation = """

            if (all(greaterThanEqual(position,vec3(-3.51,-0.63,-1.51))) &&
                all(lessThanEqual(position,vec3(-2.49,0.39,-0.49)))) {
                uint slot = atomicAdd(vertex_words[225],1u);
                if (slot < 128u) {
                    uint o = 226u + slot * 12u;
                    vertex_words[o] = uint(gl_VertexID);
                    vertex_words[o+1u] = uint(gl_InstanceID);
                    for (uint c=0u;c<3u;c++) vertex_words[o+2u+c] = floatBitsToUint(position[c]);
                    vertex_words[o+5u] = floatBitsToUint(1.0);
                    for (uint c=0u;c<4u;c++) vertex_words[o+6u+c] = floatBitsToUint(gl_Position[c]);
                    vertex_words[o+10u] = floatBitsToUint(v_TexCoord.x);
                    vertex_words[o+11u] = floatBitsToUint(v_TexCoord.y);
                }
            }
            """;
        return original.replace("#version 330 core", declarations).replace(anchor, anchor + observation);
    }

    static String instrument(String source) {
        String version = "#version 330 core";
        String color = "fragColor = _linearFog(color, v_FragDistance, u_FogColor, u_EnvironmentFog, u_RenderFog);";
        if (!source.contains(version) || !source.contains(color))
            throw new IllegalArgumentException("UV observation requires the unchanged Frozen terrain shader");
        String declarations = version + "\n#extension GL_ARB_shader_storage_buffer_object : require\n"
            + "#extension GL_ARB_shading_language_420pack : require\n"
            + "layout(std430, binding = 15) buffer AuditTerrainUv { uint audit_words[]; };\n"
            + "flat in vec4 audit_clip;";
        String observation = color + "\n"
            + "    if (all(equal(ivec2(gl_FragCoord.xy), ivec2(764,399)))) {\n"
            + "        uint slot = atomicAdd(audit_words[0], 1u);\n"
            + "        if (slot < 32u) { uint offset = 1u + slot * 7u;\n"
            + "            audit_words[offset] = floatBitsToUint(v_TexCoord.x);\n"
            + "            audit_words[offset+1u] = floatBitsToUint(v_TexCoord.y);\n"
            + "            audit_words[offset+2u] = floatBitsToUint(gl_FragCoord.z);\n"
            + "            audit_words[offset+3u] = floatBitsToUint(audit_clip.x);\n"
            + "            audit_words[offset+4u] = floatBitsToUint(audit_clip.y);\n"
            + "            audit_words[offset+5u] = floatBitsToUint(audit_clip.z);\n"
            + "            audit_words[offset+6u] = floatBitsToUint(audit_clip.w);\n"
            + "        }\n"
            + "    }";
        return source.replace(version,declarations).replace(color,observation);
    }

    public static void beforePass(boolean solid) {
        if (!enabled()) return;
        var caps = GL.getCapabilities();
        if (net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
                || !caps.GL_ARB_direct_state_access || !caps.GL_ARB_shader_storage_buffer_object
                || !caps.GL_ARB_shader_image_load_store)
            throw new IllegalStateException("Frozen UV observation requires OpenGL DSA/SSBO/image-load-store extensions");
        if (GL11.glGetInteger(GL32.GL_PROVOKING_VERTEX) != GL32.GL_LAST_VERTEX_CONVENTION)
            throw new IllegalStateException("Frozen clip observation requires the expected last-vertex convention; state was not changed");
        int prior = GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,BINDING);
        if (prior != 0 && prior != buffer)
            throw new IllegalStateException("Frozen UV diagnostic binding is already occupied");
        if (buffer == 0) {
            buffer = ARBDirectStateAccess.glCreateBuffers();
            ARBDirectStateAccess.glNamedBufferData(buffer,new int[bufferWords()],GL15.GL_DYNAMIC_READ);
        }
        int generic = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,BINDING,buffer);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,generic);
        if (solid) {
            ARBDirectStateAccess.glNamedBufferSubData(buffer,0L,new int[]{0});
            if (vertices()) ARBDirectStateAccess.glNamedBufferSubData(buffer,WORDS * 4L,new int[]{0});
        }
    }

    public static String capture() {
        if (!enabled() || buffer == 0) return "null";
        ARBShaderImageLoadStore.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT | GL42.GL_BUFFER_UPDATE_BARRIER_BIT);
        int[] words = new int[bufferWords()];
        ARBDirectStateAccess.glGetNamedBufferSubData(buffer,0L,words);
        String fragment = receipt(java.util.Arrays.copyOf(words,WORDS));
        if (!vertices()) return fragment;
        return fragment.substring(0,fragment.length()-1) + ",\"vertexObservation\":"
            + vertexReceipt(java.util.Arrays.copyOfRange(words,WORDS,words.length)) + "}";
    }

    static String vertexReceipt(int[] words) {
        if (words == null || words.length != 1 + VERTEX_LIMIT * VERTEX_WORDS)
            throw new IllegalArgumentException("invalid vertex receipt");
        long count = Integer.toUnsignedLong(words[0]);
        StringBuilder out = new StringBuilder("{\"count\":").append(count)
            .append(",\"truncated\":").append(count>VERTEX_LIMIT).append(",\"records\":[");
        for (int i=0;i<Math.min(count,VERTEX_LIMIT);i++) {
            if (i>0) out.append(',');
            out.append('[');
            for (int j=0;j<VERTEX_WORDS;j++) {
                if (j>0) out.append(',');
                out.append(Integer.toUnsignedLong(words[1+i*VERTEX_WORDS+j]));
            }
            out.append(']');
        }
        return out.append("]}").toString();
    }

    static String receipt(int[] words) {
        if (words == null || words.length != WORDS) throw new IllegalArgumentException("invalid UV receipt");
        long count = Integer.toUnsignedLong(words[0]);
        StringBuilder out = new StringBuilder("{\"count\":").append(count)
            .append(",\"truncated\":").append(count>LIMIT).append(",\"samples\":[");
        for (int i=0;i<Math.min(count,LIMIT);i++) {
            if (i>0) out.append(',');
            out.append('[').append(words[1+i*SAMPLE_WORDS]).append(',').append(words[2+i*SAMPLE_WORDS]).append(',')
                .append(words[3+i*SAMPLE_WORDS]).append(']');
        }
        out.append("],\"clipSamples\":[");
        for (int i=0;i<Math.min(count,LIMIT);i++) {
            if (i>0) out.append(',');
            out.append('[').append(words[4+i*SAMPLE_WORDS]).append(',').append(words[5+i*SAMPLE_WORDS])
                .append(',').append(words[6+i*SAMPLE_WORDS]).append(',').append(words[7+i*SAMPLE_WORDS]).append(']');
        }
        return out.append("]}").toString();
    }

    public static void close() {
        if (buffer == 0) return;
        if (GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,BINDING) == buffer) {
            int generic = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER,BINDING,0);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,generic == buffer ? 0 : generic);
        }
        GL15.glDeleteBuffers(buffer);
        buffer = 0;
    }
}
