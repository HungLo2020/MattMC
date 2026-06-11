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
    private static final java.util.regex.Pattern GLSL_VERSION_PATTERN = java.util.regex.Pattern.compile("(?m)^\\s*#version\\s+(\\d+)");
    private static final java.util.regex.Pattern VIEW_HEIGHT_DECLARATION_PATTERN = java.util.regex.Pattern.compile("\\bfloat\\s+viewHeight\\s*;");
    private static final java.util.regex.Pattern STANDALONE_UNIFORM_DECLARATION_PATTERN = java.util.regex.Pattern.compile(
        "(?m)^\\s*(?:layout\\s*\\([^)]*\\)\\s*)?(?:lowp\\s+|mediump\\s+|highp\\s+)?uniform\\s+([^;{}]+?)\\s*;\\s*(?://.*)?$"
    );
    private static final java.util.regex.Pattern EXPLICIT_BINDING_PATTERN = java.util.regex.Pattern.compile(
        "layout\\s*\\(([^)]*\\bbinding\\s*=\\s*(\\d+)[^)]*)\\)"
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

    static String normalizeForVulkan(
        VulkanicShaderStage stage,
        String shaderSource,
        List<String> standaloneUniformDeclarations,
        int standaloneUniformBindingIndex
    ) {
        if (shaderSource.isEmpty()) {
            return shaderSource;
        }

        String normalized = promoteVersionForVulkan(shaderSource);
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

        normalized = LEGACY_VERTEX_ID_PATTERN.matcher(normalized).replaceAll("gl_VertexIndex");
        return LEGACY_INSTANCE_ID_PATTERN.matcher(normalized).replaceAll("gl_InstanceIndex");
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

        StringBuilder rewritten = new StringBuilder(arguments.length() + coord.length() + 48);
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
