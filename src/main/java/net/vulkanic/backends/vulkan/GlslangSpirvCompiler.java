package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicShaderStage;
import net.vulkanic.VulkanicSpirvModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class GlslangSpirvCompiler implements SpirvCompiler {

    private static final java.util.regex.Pattern LEGACY_VERTEX_ID_PATTERN = java.util.regex.Pattern.compile("\\bgl_VertexID\\b");
    private static final java.util.regex.Pattern LEGACY_INSTANCE_ID_PATTERN = java.util.regex.Pattern.compile("\\bgl_InstanceID\\b");
    private static final java.util.regex.Pattern LEGACY_FRAG_COORD_Y_PATTERN = java.util.regex.Pattern.compile("\\bgl_FragCoord\\s*\\.\\s*y\\b");
    private static final java.util.regex.Pattern LEGACY_FRAG_COORD_XY_VIEW_PATTERN = java.util.regex.Pattern.compile(
        "\\bgl_FragCoord\\s*\\.\\s*xy\\s*/\\s*vec2\\s*\\(\\s*viewWidth\\s*,\\s*viewHeight\\s*\\)"
    );
    private static final java.util.regex.Pattern LEGACY_FRAG_COORD_XY_NOISE_PATTERN = java.util.regex.Pattern.compile(
        "\\bgl_FragCoord\\s*\\.\\s*xy\\s*/\\s*128\\.0f?"
    );
    private static final java.util.regex.Pattern LEGACY_BAYER_FRAG_COORD_XY_PATTERN = java.util.regex.Pattern.compile(
        "\\b(Bayer(?:2|4|8|16|32|64|128)?)\\s*\\(\\s*gl_FragCoord\\s*\\.\\s*xy\\s*\\)"
    );
    private static final java.util.regex.Pattern LEGACY_FRAG_COORD_INPUT_DECLARATION_PATTERN = java.util.regex.Pattern.compile(
        "(?m)^\\s*(?:layout\\s*\\([^)]*\\)\\s*)?(?:(?:flat|smooth|noperspective)\\s+)?in\\s+\\w+\\s+gl_FragCoord\\s*;\\s*(?://.*)?(?:\\R|$)"
    );
    private static final java.util.regex.Pattern GLSL_VERSION_PATTERN = java.util.regex.Pattern.compile("(?m)^\\s*#version\\s+(\\d+)");
    private static final java.util.regex.Pattern VULKANIC_BACKEND_DEFINE_PATTERN = java.util.regex.Pattern.compile(
        "(?m)^\\s*#\\s*define\\s+VULKANIC_BACKEND\\b"
    );
    private static final java.util.regex.Pattern VIEW_HEIGHT_DECLARATION_PATTERN = java.util.regex.Pattern.compile("\\bfloat\\s+viewHeight\\s*;");
    private static final java.util.regex.Pattern STANDALONE_UNIFORM_DECLARATION_PATTERN = java.util.regex.Pattern.compile(
        "(?m)^\\s*(?:layout\\s*\\([^)]*\\)\\s*)?(?:lowp\\s+|mediump\\s+|highp\\s+)?uniform\\s+([^;{}=]+?)(?:\\s*=\\s*[^;]+)?\\s*;\\s*(?://.*)?$"
    );
    private static final java.util.regex.Pattern EXPLICIT_BINDING_PATTERN = java.util.regex.Pattern.compile(
        "layout\\s*\\(([^)]*\\bbinding\\s*=\\s*(\\d+)[^)]*)\\)"
    );
    private static final java.util.regex.Pattern SODIUM_REGION_OFFSET_UNIFORM_PATTERN = java.util.regex.Pattern.compile(
        "(?m)^\\s*(?:layout\\s*\\([^)]*\\)\\s*)?(?:lowp\\s+|mediump\\s+|highp\\s+)?uniform\\s+vec3\\s+u_RegionOffset\\s*;\\s*(?://.*)?(?:\\R|$)"
    );
    private static final java.util.regex.Pattern SODIUM_REGION_OFFSET_REFERENCE_PATTERN = java.util.regex.Pattern.compile("\\bu_RegionOffset\\b");
    private static final java.util.regex.Pattern DYNAMIC_TRANSFORMS_BLOCK_PATTERN = java.util.regex.Pattern.compile(
        "(?m)(?:layout\\s*\\([^)]*\\)\\s*)?uniform\\s+DynamicTransforms\\s*\\{"
    );
    private static final java.util.regex.Pattern VARYING_DECLARATION_PATTERN = java.util.regex.Pattern.compile(
        "(?m)^([ \\t]*)((?:(?:flat|smooth|noperspective|centroid|sample)[ \\t]+)*)(in|out)[ \\t]+"
            + "([A-Za-z_][A-Za-z0-9_]*(?:\\s*\\[[^\\]]+\\])?)\\s+"
            + "([A-Za-z_][A-Za-z0-9_]*)(\\s*\\[[^;]+\\])?\\s*;"
    );
    private static final java.util.Map<String, Integer> DH_TERRAIN_VARYING_LOCATIONS_BY_NAME = java.util.Map.of(
        "vPos", 0,
        "vertexColor", 1,
        "vertexWorldPos", 2,
        "vertexYPos", 3
    );
    static final String GENERATED_UNIFORM_BLOCK_NAME = "VulkanicStandaloneUniforms";

    @Override
    public VulkanicSpirvModule compile(VulkanicShaderStage stage, CharSequence source, String sourceName, String entryPoint) {
        Objects.requireNonNull(stage, "stage must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(sourceName, "sourceName must not be null");
        Objects.requireNonNull(entryPoint, "entryPoint must not be null");

        String compilerExecutable = resolveCompilerExecutable();
        String shaderSource = source.toString();
        if (shaderSource.isBlank()) {
            throw new IllegalStateException("Cannot compile blank shader source to SPIR-V: " + sourceName);
        }

        Path tempDirectory = null;
        try {
            tempDirectory = Files.createTempDirectory("vulkanic-spirv-");
            String stageSuffix = stageToken(stage);

            Path sourcePath = tempDirectory.resolve("shader." + stageSuffix + ".glsl");
            Path outputPath = tempDirectory.resolve("shader." + stageSuffix + ".spv");

            Files.writeString(sourcePath, shaderSource, StandardCharsets.UTF_8);

            List<String> command = new ArrayList<>();
            command.add(compilerExecutable);
            command.add("-V");
            command.add("-S");
            command.add(stageSuffix);
            command.add("--auto-map-bindings");
            command.add("--auto-map-locations");
            command.add("-e");
            command.add(entryPoint);
            command.add("-o");
            command.add(outputPath.toAbsolutePath().toString());
            command.add(sourcePath.toAbsolutePath().toString());

            Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

            byte[] output = process.getInputStream().readAllBytes();
            int exitCode = process.waitFor();
            String outputText = new String(output, StandardCharsets.UTF_8).trim();

            if (exitCode != 0) {
                throw new IllegalStateException(
                    "SPIR-V compilation failed for '" + sourceName + "' (stage=" + stage + ", entryPoint=" + entryPoint + ")"
                        + " using compiler '" + compilerExecutable + "' with exit code " + exitCode
                        + (outputText.isEmpty() ? "" : ": " + outputText)
                );
            }

            byte[] spirvBytes = Files.readAllBytes(outputPath);
            if (spirvBytes.length == 0) {
                throw new IllegalStateException(
                    "SPIR-V compiler produced empty output for '" + sourceName + "' using compiler '"
                        + compilerExecutable + "'."
                );
            }

            return new VulkanicSpirvModule(
                stage,
                entryPoint,
                spirvBytes,
                sourceName,
                compilerExecutable,
                shaderSource.contains("iris_FragData0"),
                "sodium:core/vulkan_chunk".equals(sourceName)
                    || shaderSource.contains("sodium_core_vulkan_chunk")
                    || shaderSource.contains("sodium:core/vulkan_chunk")
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Failed to invoke SPIR-V compiler for '" + sourceName + "'. Ensure glslangValidator is installed"
                    + " or set -Dvulkanic.spirv.compiler=/path/to/glslangValidator. Cause: " + exception.getMessage(),
                exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while compiling shader to SPIR-V: " + sourceName, exception);
        } finally {
            if (tempDirectory != null) {
                deleteTempDirectoryQuietly(tempDirectory);
            }
        }
    }

    private static String resolveCompilerExecutable() {
        String propertyValue = System.getProperty("vulkanic.spirv.compiler");
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue.trim();
        }

        String environmentValue = System.getenv("VULKANIC_SPIRV_COMPILER");
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue.trim();
        }

        return "glslangValidator";
    }

    private static String stageToken(VulkanicShaderStage stage) {
        return switch (stage) {
            case VERTEX -> "vert";
            case FRAGMENT -> "frag";
            case GEOMETRY -> "geom";
            case COMPUTE -> "comp";
            case TESSELLATION_CONTROL -> "tesc";
            case TESSELLATION_EVALUATION -> "tese";
        };
    }

    static String normalizeForVulkan(VulkanicShaderStage stage, String shaderSource) {
        return normalizeForVulkan(stage, shaderSource, null, -1);
    }

    static String normalizeForVulkan(VulkanicShaderStage stage, String shaderSource, String sourceName) {
        return normalizeForVulkan(stage, shaderSource, sourceName, null, -1);
    }

    static String normalizeForVulkan(
        VulkanicShaderStage stage,
        String shaderSource,
        List<String> standaloneUniformDeclarations,
        int standaloneUniformBindingIndex
    ) {
        return normalizeForVulkan(stage, shaderSource, null, standaloneUniformDeclarations, standaloneUniformBindingIndex);
    }

    static String normalizeForVulkan(
        VulkanicShaderStage stage,
        String shaderSource,
        String sourceName,
        List<String> standaloneUniformDeclarations,
        int standaloneUniformBindingIndex
    ) {
        if (shaderSource.isEmpty()) {
            return shaderSource;
        }

        String normalized = injectVulkanicBackendDefine(promoteVersionForVulkan(shaderSource));
        normalized = rewriteLightmapSkyCoordinateForVulkan(stage, sourceName, normalized);
        normalized = injectDistantHorizonsTerrainVaryingLocationsForVulkan(stage, normalized);
        if (stage == VulkanicShaderStage.FRAGMENT) {
            normalized = LEGACY_FRAG_COORD_INPUT_DECLARATION_PATTERN.matcher(normalized).replaceAll("");
        }
        normalized = prepareSourceForVulkanResourceReflection(stage, normalized);
        normalized = rewriteStandaloneUniformsForVulkan(
            normalized,
            standaloneUniformDeclarations,
            standaloneUniformBindingIndex
        );

        if (stage == VulkanicShaderStage.FRAGMENT) {
            normalized = rewriteFragmentCoordForVulkan(normalized);
            normalized = rewriteFramebufferTextureSamplingForVulkan(normalized);
        }

        if (stage != VulkanicShaderStage.VERTEX) {
            return normalized;
        }

        normalized = rewriteMinecraftLightingNormalsForVulkan(normalized);
        normalized = LEGACY_VERTEX_ID_PATTERN.matcher(normalized).replaceAll("gl_VertexIndex");
        normalized = LEGACY_INSTANCE_ID_PATTERN.matcher(normalized).replaceAll("gl_InstanceIndex");
        return rewriteVertexClipDepthForVulkan(normalized);
    }

    private static String rewriteLightmapSkyCoordinateForVulkan(
        VulkanicShaderStage stage,
        String sourceName,
        String shaderSource
    ) {
        if (stage != VulkanicShaderStage.FRAGMENT || !isMinecraftLightmapShader(sourceName, shaderSource)) {
            return shaderSource;
        }

        String skyCoordinateExpression = "floor(texCoord.y * 16) / 15";
        if (!shaderSource.contains(skyCoordinateExpression)) {
            return shaderSource;
        }

        return shaderSource.replace(
            skyCoordinateExpression,
            "floor((1.0 - texCoord.y) * 16) / 15"
        );
    }

    private static boolean isMinecraftLightmapShader(String sourceName, String shaderSource) {
        if (sourceName == null || !sourceName.endsWith(":core/lightmap")) {
            return false;
        }

        return shaderSource.contains("uniform LightmapInfo")
            && shaderSource.contains("SkyFactor")
            && shaderSource.contains("BlockFactor")
            && shaderSource.contains("texCoord.y");
    }

    private static String injectVulkanicBackendDefine(String shaderSource) {
        if (VULKANIC_BACKEND_DEFINE_PATTERN.matcher(shaderSource).find()) {
            return shaderSource;
        }

        java.util.regex.Matcher matcher = GLSL_VERSION_PATTERN.matcher(shaderSource);
        if (!matcher.find()) {
            return "#define VULKANIC_BACKEND 1\n" + shaderSource;
        }

        int lineEnd = shaderSource.indexOf('\n', matcher.end());
        if (lineEnd < 0) {
            return shaderSource + "\n#define VULKANIC_BACKEND 1";
        }

        return shaderSource.substring(0, lineEnd + 1)
            + "#define VULKANIC_BACKEND 1\n"
            + shaderSource.substring(lineEnd + 1);
    }

    private static String injectDistantHorizonsTerrainVaryingLocationsForVulkan(VulkanicShaderStage stage, String shaderSource) {
        if (stage != VulkanicShaderStage.VERTEX && stage != VulkanicShaderStage.FRAGMENT) {
            return shaderSource;
        }
        if (!isDistantHorizonsTerrainInterfaceShader(stage, shaderSource)) {
            return shaderSource;
        }

        boolean targetVertexOutputs = stage == VulkanicShaderStage.VERTEX;
        java.util.regex.Matcher matcher = VARYING_DECLARATION_PATTERN.matcher(shaderSource);
        StringBuffer rewritten = new StringBuffer();
        while (matcher.find()) {
            String direction = matcher.group(3);
            if ((targetVertexOutputs && !"out".equals(direction)) || (!targetVertexOutputs && !"in".equals(direction))) {
                matcher.appendReplacement(rewritten, java.util.regex.Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }

            int lineStart = shaderSource.lastIndexOf('\n', Math.max(0, matcher.start() - 1));
            String linePrefix = shaderSource.substring(lineStart < 0 ? 0 : lineStart + 1, matcher.start());
            if (linePrefix.contains("layout")) {
                matcher.appendReplacement(rewritten, java.util.regex.Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }

            Integer location = DH_TERRAIN_VARYING_LOCATIONS_BY_NAME.get(matcher.group(5));
            if (location == null) {
                matcher.appendReplacement(rewritten, java.util.regex.Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }

            String replacement = matcher.group(1)
                + "layout(location = " + location + ") "
                + matcher.group(2)
                + direction
                + " "
                + matcher.group(4)
                + " "
                + matcher.group(5)
                + (matcher.group(6) == null ? "" : matcher.group(6))
                + ";";
            matcher.appendReplacement(rewritten, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rewritten);
        return rewritten.toString();
    }

    private static boolean isDistantHorizonsTerrainInterfaceShader(VulkanicShaderStage stage, String shaderSource) {
        if (stage == VulkanicShaderStage.VERTEX) {
            return shaderSource.contains("in uvec4 vPosition;")
                && shaderSource.contains("out vec4 vPos;")
                && shaderSource.contains("out vec4 vertexColor;")
                && shaderSource.contains("out vec3 vertexWorldPos;");
        }

        return shaderSource.contains("in vec4 vertexColor;")
            && shaderSource.contains("in vec3 vertexWorldPos;")
            && shaderSource.contains("in vec4 vPos;")
            && shaderSource.contains("uDitherDhRendering");
    }

    static String prepareSourceForVulkanResourceReflection(VulkanicShaderStage stage, String shaderSource) {
        if (stage != VulkanicShaderStage.VERTEX || !isSodiumTerrainRegionOffsetSource(shaderSource)) {
            return shaderSource;
        }

        String rewritten = SODIUM_REGION_OFFSET_UNIFORM_PATTERN.matcher(shaderSource).replaceFirst("");
        if (rewritten.equals(shaderSource)) {
            return shaderSource;
        }

        if (!DYNAMIC_TRANSFORMS_BLOCK_PATTERN.matcher(rewritten).find()) {
            int insertOffset = findUniformBlockInsertionOffset(rewritten);
            rewritten = rewritten.substring(0, insertOffset)
                + "layout(std140) uniform DynamicTransforms {\n"
                + "    mat4 ModelViewMat;\n"
                + "    vec4 ColorModulator;\n"
                + "    vec3 ModelOffset;\n"
                + "    mat4 TextureMat;\n"
                + "    float LineWidth;\n"
                + "};\n\n"
                + rewritten.substring(insertOffset);
        }

        return SODIUM_REGION_OFFSET_REFERENCE_PATTERN.matcher(rewritten).replaceAll("ModelOffset");
    }

    private static boolean isSodiumTerrainRegionOffsetSource(String shaderSource) {
        return shaderSource.contains("u_RegionOffset")
            && shaderSource.contains("_vert_position")
            && shaderSource.contains("_get_draw_translation")
            && shaderSource.contains("getVertexPosition");
    }

    private static String rewriteFragmentCoordForVulkan(String shaderSource) {
        if (!shaderSource.contains("gl_FragCoord")
            || !VIEW_HEIGHT_DECLARATION_PATTERN.matcher(shaderSource).find()) {
            return shaderSource;
        }

        String lowerLeftXy = lowerLeftFragmentCoordXyExpression();
        String rewritten = LEGACY_FRAG_COORD_Y_PATTERN.matcher(shaderSource)
            .replaceAll(java.util.regex.Matcher.quoteReplacement("(viewHeight - gl_FragCoord.y)"));
        rewritten = LEGACY_FRAG_COORD_XY_VIEW_PATTERN.matcher(rewritten)
            .replaceAll(java.util.regex.Matcher.quoteReplacement("(" + lowerLeftXy + " / vec2(viewWidth, viewHeight))"));
        rewritten = LEGACY_FRAG_COORD_XY_NOISE_PATTERN.matcher(rewritten)
            .replaceAll(java.util.regex.Matcher.quoteReplacement("(" + lowerLeftXy + " / 128.0f)"));
        return LEGACY_BAYER_FRAG_COORD_XY_PATTERN.matcher(rewritten)
            .replaceAll("$1" + java.util.regex.Matcher.quoteReplacement("(" + lowerLeftXy + ")"));
    }

    private static String lowerLeftFragmentCoordXyExpression() {
        return "vec2(gl_FragCoord.x, viewHeight - gl_FragCoord.y)";
    }

    private static String rewriteFramebufferTextureSamplingForVulkan(String shaderSource) {
        if (!shaderSource.contains("texture")) {
            return shaderSource;
        }

        StringBuilder rewritten = new StringBuilder(shaderSource.length());
        int cursor = 0;
        while (cursor < shaderSource.length()) {
            int callStart = nextTextureCall(shaderSource, cursor);
            if (callStart < 0) {
                rewritten.append(shaderSource, cursor, shaderSource.length());
                break;
            }

            rewritten.append(shaderSource, cursor, callStart);
            int openParen = shaderSource.indexOf('(', callStart);
            int closeParen = findMatchingParen(shaderSource, openParen);
            if (openParen < 0 || closeParen < 0) {
                rewritten.append(shaderSource, callStart, shaderSource.length());
                break;
            }

            String functionName = shaderSource.substring(callStart, openParen);
            String arguments = shaderSource.substring(openParen + 1, closeParen);
            String replacementArguments = rewriteFramebufferTextureArguments(arguments);
            rewritten.append(functionName)
                .append('(')
                .append(replacementArguments)
                .append(')');
            cursor = closeParen + 1;
        }

        return rewritten.toString();
    }

    private static int nextTextureCall(String shaderSource, int start) {
        int texture = indexOfFunctionCall(shaderSource, "texture", start);
        int texture2D = indexOfFunctionCall(shaderSource, "texture2D", start);
        if (texture < 0) {
            return texture2D;
        }
        if (texture2D < 0) {
            return texture;
        }
        return Math.min(texture, texture2D);
    }

    private static String rewriteFramebufferTextureArguments(String arguments) {
        List<int[]> argumentRanges = topLevelArgumentRanges(arguments);
        if (argumentRanges.size() < 2) {
            return arguments;
        }

        String sampler = slice(arguments, argumentRanges.get(0)).trim();
        if (!isFramebufferSamplerName(sampler)) {
            return arguments;
        }

        int[] coordRange = argumentRanges.get(1);
        String coord = slice(arguments, coordRange).trim();
        if (coord.isEmpty() || coord.contains("vulkanicFramebufferTexCoord")) {
            return arguments;
        }

        StringBuilder rewritten = new StringBuilder(arguments.length() + coord.length() + 64);
        rewritten.append(arguments, 0, coordRange[0]);
        rewritten.append(framebufferTextureCoordExpression(coord));
        rewritten.append(arguments, coordRange[1], arguments.length());
        return rewritten.toString();
    }

    private static List<int[]> topLevelArgumentRanges(String arguments) {
        List<int[]> ranges = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int index = 0; index < arguments.length(); index++) {
            char c = arguments.charAt(index);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == ',' && depth == 0) {
                ranges.add(trimmedRange(arguments, start, index));
                start = index + 1;
            }
        }
        ranges.add(trimmedRange(arguments, start, arguments.length()));
        return ranges;
    }

    private static int[] trimmedRange(String text, int start, int end) {
        int trimmedStart = start;
        int trimmedEnd = end;
        while (trimmedStart < trimmedEnd && Character.isWhitespace(text.charAt(trimmedStart))) {
            trimmedStart++;
        }
        while (trimmedEnd > trimmedStart && Character.isWhitespace(text.charAt(trimmedEnd - 1))) {
            trimmedEnd--;
        }
        return new int[] {trimmedStart, trimmedEnd};
    }

    private static String slice(String text, int[] range) {
        return text.substring(range[0], range[1]);
    }

    private static boolean isFramebufferSamplerName(String sampler) {
        return sampler.matches("colortex\\d+")
            || sampler.matches("depthtex\\d*")
            || sampler.matches("gaux\\d+")
            || sampler.matches("dhDepthTex\\d*");
    }

    private static String framebufferTextureCoordExpression(String coord) {
        return "vec2((" + coord + ").x, 1.0f - (" + coord + ").y)";
    }

    private static String rewriteMinecraftLightingNormalsForVulkan(String shaderSource) {
        if (!shaderSource.contains("Light0_Direction") || shaderSource.contains("vulkanicMinecraftLightingNormal")) {
            return shaderSource;
        }

        String helper = ""
            + "vec3 vulkanicMinecraftLightingNormal(vec3 normal) {\n"
            + "    return vec3(normal.x, -normal.y, normal.z);\n"
            + "}\n\n";

        String computeFunction = ""
            + "vec2 minecraft_compute_light(vec3 lightDir0, vec3 lightDir1, vec3 normal) {\n"
            + "    return vec2(dot(lightDir0, normal), dot(lightDir1, normal));\n"
            + "}\n";
        if (shaderSource.contains(computeFunction)) {
            return shaderSource.replace(
                computeFunction,
                helper
                    + "vec2 minecraft_compute_light(vec3 lightDir0, vec3 lightDir1, vec3 normal) {\n"
                    + "    normal = vulkanicMinecraftLightingNormal(normal);\n"
                    + "    return vec2(dot(lightDir0, normal), dot(lightDir1, normal));\n"
                    + "}\n"
            );
        }

        String fallbackLightingBody = ""
            + "vec4 minecraft_mix_light(vec3 lightDir0, vec3 lightDir1, vec3 normal, vec4 color) {\n"
            + "    float light0 = max(0.0, dot(lightDir0, normal));\n";
        if (shaderSource.contains(fallbackLightingBody)) {
            return shaderSource.replace(
                fallbackLightingBody,
                helper
                    + "vec4 minecraft_mix_light(vec3 lightDir0, vec3 lightDir1, vec3 normal, vec4 color) {\n"
                    + "    normal = vulkanicMinecraftLightingNormal(normal);\n"
                    + "    float light0 = max(0.0, dot(lightDir0, normal));\n"
            );
        }

        return shaderSource;
    }

    private static String rewriteVertexClipDepthForVulkan(String shaderSource) {
        if (!shaderSource.contains("gl_Position") || shaderSource.contains("vulkanicOpenGlClipDepthToVulkan")) {
            return shaderSource;
        }

        int mainStart = indexOfFunctionCall(shaderSource, "main", 0);
        if (mainStart < 0) {
            return shaderSource;
        }

        int mainOpenParen = shaderSource.indexOf('(', mainStart);
        int mainCloseParen = findMatchingParen(shaderSource, mainOpenParen);
        if (mainOpenParen < 0 || mainCloseParen < 0) {
            return shaderSource;
        }

        int mainOpenBrace = nextNonWhitespaceIndex(shaderSource, mainCloseParen + 1);
        if (mainOpenBrace < 0 || shaderSource.charAt(mainOpenBrace) != '{') {
            return shaderSource;
        }

        int mainCloseBrace = findMatchingBrace(shaderSource, mainOpenBrace);
        if (mainCloseBrace < 0) {
            return shaderSource;
        }

        String remap = "\n    gl_Position.z = vulkanicOpenGlClipDepthToVulkan(gl_Position.z, gl_Position.w);\n";
        int helperOffset = findUniformBlockInsertionOffset(shaderSource);
        String withHelper = shaderSource.substring(0, helperOffset)
            + "float vulkanicOpenGlClipDepthToVulkan(float z, float w) {\n"
            + "    return 0.5f * (z + w);\n"
            + "}\n\n"
            + shaderSource.substring(helperOffset);
        int adjustedMainCloseBrace = mainCloseBrace + (withHelper.length() - shaderSource.length());
        return withHelper.substring(0, adjustedMainCloseBrace) + remap + withHelper.substring(adjustedMainCloseBrace);
    }

    private static int nextNonWhitespaceIndex(String text, int start) {
        for (int index = Math.max(0, start); index < text.length(); index++) {
            if (!Character.isWhitespace(text.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private static int findMatchingBrace(String text, int openBrace) {
        if (openBrace < 0 || openBrace >= text.length() || text.charAt(openBrace) != '{') {
            return -1;
        }
        int depth = 0;
        for (int index = openBrace; index < text.length(); index++) {
            char c = text.charAt(index);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static int indexOfFunctionCall(String shaderSource, String functionName, int start) {
        int cursor = Math.max(0, start);
        while (cursor < shaderSource.length()) {
            int index = shaderSource.indexOf(functionName, cursor);
            if (index < 0) {
                return -1;
            }
            int afterName = index + functionName.length();
            if (isIdentifierBoundary(shaderSource, index - 1)
                && isIdentifierBoundary(shaderSource, afterName)
                && nextNonWhitespaceIs(shaderSource, afterName, '(')) {
                return index;
            }
            cursor = afterName;
        }
        return -1;
    }

    private static boolean isIdentifierBoundary(String text, int index) {
        return index < 0 || index >= text.length() || !isGlslIdentifierPart(text.charAt(index));
    }

    private static boolean isGlslIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static boolean nextNonWhitespaceIs(String text, int start, char expected) {
        for (int index = start; index < text.length(); index++) {
            if (!Character.isWhitespace(text.charAt(index))) {
                return text.charAt(index) == expected;
            }
        }
        return false;
    }

    private static int findMatchingParen(String text, int openParen) {
        if (openParen < 0 || openParen >= text.length() || text.charAt(openParen) != '(') {
            return -1;
        }
        int depth = 0;
        for (int index = openParen; index < text.length(); index++) {
            char c = text.charAt(index);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static String promoteVersionForVulkan(String shaderSource) {
        java.util.regex.Matcher versionMatcher = GLSL_VERSION_PATTERN.matcher(shaderSource);
        if (!versionMatcher.find()) {
            return "#version 450\n" + shaderSource;
        }

        int declaredVersion = Integer.parseInt(versionMatcher.group(1));
        if (declaredVersion >= 450) {
            return shaderSource;
        }

        return shaderSource.substring(0, versionMatcher.start(1))
            + "450"
            + shaderSource.substring(versionMatcher.end(1));
    }

    private static String rewriteStandaloneUniformsForVulkan(
        String shaderSource,
        List<String> canonicalBlockMembers,
        int standaloneUniformBindingIndex
    ) {
        java.util.regex.Matcher matcher = STANDALONE_UNIFORM_DECLARATION_PATTERN.matcher(shaderSource);
        StringBuffer strippedSource = new StringBuffer();
        List<String> localBlockMembers = new ArrayList<>();

        while (matcher.find()) {
            String declaration = matcher.group(1).trim();
            if (declaration.isEmpty()) {
                matcher.appendReplacement(strippedSource, java.util.regex.Matcher.quoteReplacement(matcher.group()));
                continue;
            }

            String typeToken = declaration.split("\\s+", 2)[0];
            if (isOpaqueUniformType(typeToken)) {
                matcher.appendReplacement(strippedSource, java.util.regex.Matcher.quoteReplacement(matcher.group()));
                continue;
            }

            localBlockMembers.add(declaration + ";");
            matcher.appendReplacement(strippedSource, "");
        }

        matcher.appendTail(strippedSource);

        if (localBlockMembers.isEmpty()) {
            return shaderSource;
        }

        List<String> blockMembers = canonicalBlockMembers == null || canonicalBlockMembers.isEmpty()
            ? localBlockMembers
            : canonicalBlockMembers;
        int insertOffset = findUniformBlockInsertionOffset(strippedSource.toString());
        StringBuilder block = new StringBuilder();
        // Use the HIGHER of: (max explicit binding + 1) OR (count of non-explicitly-bound opaque
        // uniforms). The latter ensures the UBO is placed after all auto-mapped samplers, which
        // glslang assigns starting from binding 0 independently per resource type. Without this,
        // VulkanicStandaloneUniforms would get explicit binding 0 and collide with the first
        // auto-mapped sampler (e.g. colortex0 from Iris shaders that have no explicit bindings).
        int standaloneBindingIndex = standaloneUniformBindingIndex >= 0
            ? standaloneUniformBindingIndex
            : Math.max(
                findNextExplicitBindingIndex(strippedSource.toString()),
                countNonExplicitOpaqueUniforms(strippedSource.toString())
            );
        block.append("layout(std140, set = 0, binding = ")
            .append(standaloneBindingIndex)
            .append(") uniform ")
            .append(GENERATED_UNIFORM_BLOCK_NAME)
            .append(" {\n");

        for (String member : blockMembers) {
            block.append("    ").append(member).append("\n");
        }

        block.append("};\n\n");

        return strippedSource.substring(0, insertOffset)
            + block
            + strippedSource.substring(insertOffset);
    }

    private static boolean isOpaqueUniformType(String typeToken) {
        return typeToken.contains("sampler")
            || typeToken.contains("image")
            || typeToken.equals("atomic_uint");
    }

    private static int findUniformBlockInsertionOffset(String shaderSource) {
        java.util.regex.Matcher versionMatcher = GLSL_VERSION_PATTERN.matcher(shaderSource);
        if (!versionMatcher.find()) {
            return 0;
        }

        int lineEnd = shaderSource.indexOf('\n', versionMatcher.end());
        if (lineEnd < 0) {
            return shaderSource.length();
        }

        int offset = lineEnd + 1;
        while (offset < shaderSource.length()) {
            int nextLineEnd = shaderSource.indexOf('\n', offset);
            if (nextLineEnd < 0) {
                nextLineEnd = shaderSource.length();
            }

            String trimmed = shaderSource.substring(offset, nextLineEnd).trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#extension")) {
                offset = nextLineEnd < shaderSource.length() ? nextLineEnd + 1 : nextLineEnd;
                continue;
            }

            break;
        }

        return offset;
    }

    static int countNonExplicitOpaqueUniforms(String shaderSource) {
        java.util.regex.Matcher matcher = STANDALONE_UNIFORM_DECLARATION_PATTERN.matcher(shaderSource);
        int count = 0;
        while (matcher.find()) {
            String declaration = matcher.group(1).trim();
            if (declaration.isEmpty()) continue;
            String typeToken = declaration.split("\\s+", 2)[0];
            if (!isOpaqueUniformType(typeToken)) continue;
            // Only count if no explicit "binding" qualifier appears in the full match
            String fullMatch = matcher.group();
            if (!fullMatch.contains("binding")) {
                count++;
            }
        }
        return count;
    }

    static boolean hasStandaloneUniformBlockMembers(String shaderSource) {
        java.util.regex.Matcher matcher = STANDALONE_UNIFORM_DECLARATION_PATTERN.matcher(shaderSource);
        while (matcher.find()) {
            String declaration = matcher.group(1).trim();
            if (declaration.isEmpty()) {
                continue;
            }

            String typeToken = declaration.split("\\s+", 2)[0];
            if (!isOpaqueUniformType(typeToken)) {
                return true;
            }
        }
        return false;
    }

    private static int findNextExplicitBindingIndex(String shaderSource) {
        java.util.regex.Matcher matcher = EXPLICIT_BINDING_PATTERN.matcher(shaderSource);
        int maxBinding = -1;
        while (matcher.find()) {
            try {
                int binding = Integer.parseInt(matcher.group(2));
                if (binding > maxBinding) {
                    maxBinding = binding;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return maxBinding + 1;
    }

    private static void deleteTempDirectoryQuietly(Path directory) {
        try (var paths = Files.walk(directory)) {
            paths.sorted((left, right) -> right.getNameCount() - left.getNameCount())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
        } catch (IOException ignored) {
        }
    }
}
