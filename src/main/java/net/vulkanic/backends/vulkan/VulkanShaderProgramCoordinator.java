package net.vulkanic.backends.vulkan;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import net.vulkanic.PipelineDescriptor;
import net.vulkanic.VulkanicShaderStage;
import net.vulkanic.VulkanicSpirvModule;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.vulkan.VK10;

/**
 * Owns Vulkan's legacy shader/program compatibility state. Rendering code should
 * consume immutable snapshots from this coordinator instead of reading mutable
 * shader/program maps directly.
 */
final class VulkanShaderProgramCoordinator {
    private static final Pattern GLSL_STANDALONE_UNIFORM_MEMBER_PATTERN = Pattern.compile(
        "^\\s*(\\w+)\\s+(\\w+)(?:\\s*\\[\\s*(\\d+)\\s*\\])?\\s*;\\s*$"
    );
    private final AtomicInteger nextShaderId = new AtomicInteger(1);
    private final AtomicInteger nextProgramId = new AtomicInteger(1);
    private final AtomicInteger nextUniformLocationToken = new AtomicInteger(1);
    private final Map<Integer, VirtualShader> shaders = new ConcurrentHashMap<>();
    private final Map<Integer, VirtualProgram> programs = new ConcurrentHashMap<>();
    private final Map<Integer, UniformLocationRef> uniformLocationRefs = new ConcurrentHashMap<>();
    private volatile int boundProgramId;

    int createShader(VulkanicShaderStage stage) {
        int shaderId = nextShaderId.getAndIncrement();
        shaders.put(shaderId, new VirtualShader(stage));
        return shaderId;
    }

    int createProgram() {
        int programId = nextProgramId.getAndIncrement();
        programs.put(programId, new VirtualProgram());
        return programId;
    }

    VirtualShader requireShader(int shaderId) {
        VirtualShader shader = shaders.get(shaderId);
        if (shader == null) {
            throw new IllegalArgumentException("Unknown Vulkan virtual shader handle: " + shaderId);
        }
        return shader;
    }

    VirtualProgram requireProgram(int programId) {
        VirtualProgram program = programs.get(programId);
        if (program == null) {
            throw new IllegalArgumentException("Unknown Vulkan virtual program handle: " + programId);
        }
        return program;
    }

    @Nullable
    VirtualShader shader(int shaderId) {
        return shaders.get(shaderId);
    }

    @Nullable
    VirtualProgram program(int programId) {
        return programs.get(programId);
    }

    VirtualShader uploadShaderSource(
        int shaderId,
        @Nullable CharSequence source,
        Consumer<VirtualShader> nativeModuleRelease
    ) {
        VirtualShader shader = requireShader(shaderId);
        nativeModuleRelease.accept(shader);
        shader.source = source == null ? "" : source.toString();
        shader.compiledModule = null;
        shader.compileStatus = false;
        shader.infoLog = "";
        return shader;
    }

    VirtualShader beginCompile(int shaderId, Consumer<VirtualShader> nativeModuleRelease) {
        VirtualShader shader = requireShader(shaderId);
        nativeModuleRelease.accept(shader);
        return shader;
    }

    void markCompileSucceeded(VirtualShader shader, VulkanicSpirvModule compiledModule) {
        shader.compiledModule = compiledModule;
        shader.compileStatus = true;
        shader.infoLog = "";
    }

    void markCompileFailed(VirtualShader shader, String infoLog) {
        shader.compiledModule = null;
        shader.compileStatus = false;
        shader.infoLog = infoLog == null || infoLog.isBlank() ? "Shader compilation failed." : infoLog;
    }

    void markProgramLinkFailed(VirtualProgram program, String infoLog) {
        program.linkStatus = false;
        program.infoLog = infoLog == null || infoLog.isBlank() ? "Program link failed." : infoLog;
        clearLinkedReflection(program);
    }

    void markProgramLinked(VirtualProgram program, List<VulkanicSpirvModule> linkedSpirvModules) {
        program.linkedSpirvModules = List.copyOf(linkedSpirvModules);
        program.linkStatus = true;
        program.infoLog = "";
    }

    boolean containsProgram(int programId) {
        return programId != 0 && programs.containsKey(programId);
    }

    int programCount() {
        return programs.size();
    }

    List<Integer> programIdsSnapshot() {
        ArrayList<Integer> keys = new ArrayList<>(programs.keySet());
        Collections.sort(keys);
        return keys;
    }

    int boundProgramId() {
        return boundProgramId;
    }

    void bindProgram(int programId) {
        boundProgramId = programId;
    }

    void attachShader(int programId, int shaderId) {
        requireShader(shaderId);
        VirtualProgram program = requireProgram(programId);
        program.attachedShaderIds.add(shaderId);
        program.linkStatus = false;
    }

    void detachShader(int programId, int shaderId, Consumer<VirtualShader> nativeModuleRelease) {
        VirtualProgram program = requireProgram(programId);
        program.attachedShaderIds.remove(shaderId);
        releaseShaderIfDeletionPendingAndDetached(shaderId, nativeModuleRelease);
    }

    void deleteShader(int shaderId, Consumer<VirtualShader> nativeModuleRelease) {
        VirtualShader shader = shaders.get(shaderId);
        if (shader == null) {
            return;
        }
        shader.deletionPending = true;
        releaseShaderIfDeletionPendingAndDetached(shaderId, nativeModuleRelease);
    }

    void deleteProgram(int programId, Consumer<VirtualShader> nativeModuleRelease) {
        VirtualProgram removedProgram = programs.remove(programId);
        if (removedProgram != null) {
            unregisterUniformLocationTokens(removedProgram);
            removedProgram.closeStandaloneUniformBacking();
            for (int shaderId : removedProgram.attachedShaderIds) {
                releaseShaderIfDeletionPendingAndDetached(shaderId, nativeModuleRelease);
            }
        }
        if (boundProgramId == programId) {
            boundProgramId = 0;
        }
    }

    private boolean isShaderAttachedToAnyProgram(int shaderId) {
        for (VirtualProgram program : programs.values()) {
            if (program.attachedShaderIds.contains(shaderId)) {
                return true;
            }
        }
        return false;
    }

    private void releaseShaderIfDeletionPendingAndDetached(int shaderId, Consumer<VirtualShader> nativeModuleRelease) {
        VirtualShader shader = shaders.get(shaderId);
        if (shader == null || !shader.deletionPending || isShaderAttachedToAnyProgram(shaderId)) {
            return;
        }

        VirtualShader removedShader = shaders.remove(shaderId);
        if (removedShader != null) {
            nativeModuleRelease.accept(removedShader);
        }
    }

    List<Map.Entry<Integer, VirtualShader>> compiledShaderEntriesSnapshot() {
        List<Map.Entry<Integer, VirtualShader>> entries = new ArrayList<>();
        for (Map.Entry<Integer, VirtualShader> entry : shaders.entrySet()) {
            VirtualShader shader = entry.getValue();
            if (shader.compileStatus && shader.compiledModule != null) {
                entries.add(Map.entry(entry.getKey(), shader));
            }
        }
        entries.sort(Map.Entry.comparingByKey());
        return entries;
    }

    List<Integer> sortedAttachedShaderIds(VirtualProgram program) {
        List<Integer> shaderIds = new ArrayList<>(program.attachedShaderIds);
        shaderIds.sort((left, right) -> {
            VirtualShader leftShader = shaders.get(left);
            VirtualShader rightShader = shaders.get(right);
            int leftStage = leftShader == null ? Integer.MAX_VALUE : leftShader.stage.ordinal();
            int rightStage = rightShader == null ? Integer.MAX_VALUE : rightShader.stage.ordinal();
            int stageCompare = Integer.compare(leftStage, rightStage);
            return stageCompare != 0 ? stageCompare : Integer.compare(left, right);
        });
        return shaderIds;
    }

    List<String> sourceSnapshotForReflection(VirtualProgram program) {
        List<String> sources = new ArrayList<>();
        for (int shaderId : sortedAttachedShaderIds(program)) {
            VirtualShader shader = shaders.get(shaderId);
            if (shader != null && shader.source != null && !shader.source.isBlank()) {
                sources.add(prepareSourceForReflection(shader));
            }
        }
        return sources;
    }

    static String prepareSourceForReflection(VirtualShader shader) {
        String source = shader.source;
        if (source == null || source.isBlank()) {
            return source;
        }
        return ShadercSpirvCompiler.prepareSourceForVulkanResourceReflection(shader.stage, source);
    }

    void clearLinkedReflection(VirtualProgram program) {
        program.activeUniformNames = List.of();
        program.activeUniforms = List.of();
        program.activeUniformBlocks = List.of();
        program.activeResourceBindings = List.of();
        program.linkedSpirvModules = List.of();
        program.fragmentOutputs = List.of();
        unregisterUniformLocationTokens(program);
    }

    void installReflection(
        VirtualProgram program,
        List<String> activeUniformNames,
        List<ReflectedUniform> activeUniforms,
        List<String> activeUniformBlocks,
        List<ReflectedResourceBinding> activeResourceBindings,
        List<String> standaloneUniformDeclarations,
        int[] computeWorkGroupSize
    ) {
        installReflection(
            program,
            activeUniformNames,
            activeUniforms,
            activeUniformBlocks,
            activeResourceBindings,
            standaloneUniformDeclarations,
            computeWorkGroupSize,
            List.of()
        );
    }

    void installReflection(
        VirtualProgram program,
        List<String> activeUniformNames,
        List<ReflectedUniform> activeUniforms,
        List<String> activeUniformBlocks,
        List<ReflectedResourceBinding> activeResourceBindings,
        List<String> standaloneUniformDeclarations,
        int[] computeWorkGroupSize,
        List<VulkanicSpirvModule.FragmentOutput> fragmentOutputs
    ) {
        program.activeUniformNames = List.copyOf(activeUniformNames);
        program.activeUniforms = List.copyOf(activeUniforms);
        program.activeUniformBlocks = List.copyOf(activeUniformBlocks);
        program.activeResourceBindings = List.copyOf(activeResourceBindings);
        program.computeWorkGroupSize = computeWorkGroupSize.clone();
        program.standaloneUniformDeclarations = List.copyOf(standaloneUniformDeclarations);
        program.fragmentOutputs = List.copyOf(fragmentOutputs);
    }

    void initializeStandaloneUniformState(
        VirtualProgram program,
        Map<String, ReflectedUniform> reflectedUniformsByName
    ) {
        Map<String, List<Integer>> offsetsByName = collectStandaloneUniformOffsets(program.standaloneUniformDeclarations);
        Map<Integer, StandaloneUniformField> fieldsByLocation = new HashMap<>();
        int backingSize = 0;

        List<String> uniformNames = program.activeUniformNames;
        for (int location = 0; location < uniformNames.size(); location++) {
            String name = uniformNames.get(location);
            ReflectedUniform reflectedUniform = reflectedUniformsByName.get(name);
            if (reflectedUniform == null) {
                continue;
            }

            Optional<net.vulkanic.VulkanicUniformReflectionType> reflectionType =
                net.vulkanic.VulkanicUniformReflectionType.fromLegacyGlConstant(reflectedUniform.legacyType());
            if (reflectionType.isEmpty() || reflectionType.get().isSampler() || reflectionType.get().isImage()) {
                continue;
            }

            int typeSize = std140TypeSize(reflectionType.get());
            int arraySize = Math.max(1, reflectedUniform.arraySize());
            int stride = arraySize > 1 ? roundUpTo(typeSize, 16) : typeSize;
            List<Integer> offsets = offsetsByName.get(name);
            if (offsets == null || offsets.isEmpty()) {
                continue;
            }

            fieldsByLocation.put(
                location,
                new StandaloneUniformField(
                    name,
                    reflectionType.get(),
                    offsets.stream().mapToInt(Integer::intValue).toArray(),
                    arraySize,
                    stride
                )
            );
            for (int offset : offsets) {
                backingSize = Math.max(backingSize, offset + (stride * arraySize));
            }
        }

        backingSize = roundUpTo(backingSize, 16);
        synchronized (program) {
            unregisterUniformLocationTokens(program);
            program.standaloneFieldsByLocation = Map.copyOf(fieldsByLocation);
            program.standaloneBackingSize = backingSize;
            program.standaloneBackingData =
                backingSize > 0 ? ByteBuffer.allocate(backingSize).order(ByteOrder.nativeOrder()) : null;
            if (program.standaloneBackingData != null) {
                initializeStandaloneUniformDefaults(program.standaloneFieldsByLocation, program.standaloneBackingData);
            }
            program.standaloneDirty = backingSize > 0;
            program.closeStandaloneUniformBacking();
        }
    }

    int resolveUniformLocationToken(int programId, VirtualProgram program, String uniformName, int uniformIndex) {
        Integer existing = program.uniformLocationTokensByName.get(uniformName);
        if (existing != null) {
            return existing;
        }

        int token = nextUniformLocationToken.getAndIncrement();
        Integer raced = program.uniformLocationTokensByName.putIfAbsent(uniformName, token);
        if (raced != null) {
            return raced;
        }

        uniformLocationRefs.put(token, new UniformLocationRef(programId, uniformIndex));
        return token;
    }

    @Nullable
    UniformLocationRef resolveUniformLocationRef(int location) {
        if (location < 0) {
            return null;
        }
        return uniformLocationRefs.get(location);
    }

    private void unregisterUniformLocationTokens(VirtualProgram program) {
        for (Integer token : program.uniformLocationTokensByName.values()) {
            uniformLocationRefs.remove(token);
        }
        program.uniformLocationTokensByName.clear();
    }

    @Nullable
    LinkedProgramExecutionSnapshot linkedExecutionSnapshot(int programId, Set<VulkanicShaderStage> stages) {
        VirtualProgram program = programs.get(programId);
        if (program == null || !program.linkStatus) {
            return null;
        }
        Set<VulkanicShaderStage> normalizedStages = Set.copyOf(stages);
        if (normalizedStages.isEmpty()) {
            throw new IllegalArgumentException("stages must not be empty");
        }

        List<PipelineDescriptor.ResourceBinding> bindings = new ArrayList<>(program.activeResourceBindings.size());
        for (ReflectedResourceBinding resourceBinding : program.activeResourceBindings) {
            bindings.add(new PipelineDescriptor.ResourceBinding(
                resourceBinding.set(),
                resourceBinding.binding(),
                resourceBinding.name(),
                resourceBinding.type(),
                null,
                normalizedStages
            ));
        }

        return new LinkedProgramExecutionSnapshot(
            programId,
            program.linkStatus,
            program.debugLabel,
            program.linkedSpirvModules,
            program.vertexInputs,
            new java.util.LinkedHashMap<>(program.attributeLocationsByName),
            new PipelineDescriptor.ResourceLayout(bindings),
            program.activeUniformNames,
            program.activeUniforms,
            program.activeUniformBlocks,
            program.activeResourceBindings,
            program.computeWorkGroupSize.clone(),
            new java.util.LinkedHashMap<>(program.opaqueResourceUniformValuesByIndex),
            program.fragmentOutputs
        );
    }

    int samplerOrImageUnit(VirtualProgram program, PipelineDescriptor.ResourceBinding binding) {
        return samplerOrImageUnit(program.activeUniformNames, program.opaqueResourceUniformValuesByIndex, binding);
    }

    static int samplerOrImageUnit(
        List<String> activeUniformNames,
        Map<Integer, Integer> opaqueResourceUniformValuesByIndex,
        PipelineDescriptor.ResourceBinding binding
    ) {
        int uniformIndex = activeUniformNames.indexOf(binding.name());
        if (uniformIndex < 0) {
            return binding.binding();
        }
        return opaqueResourceUniformValuesByIndex.getOrDefault(uniformIndex, binding.binding());
    }

    @Nullable
    Integer uploadedOpaqueResourceUnit(VirtualProgram program, String uniformName) {
        int uniformIndex = program.activeUniformNames.indexOf(uniformName);
        if (uniformIndex < 0) {
            return null;
        }
        return program.opaqueResourceUniformValuesByIndex.get(uniformIndex);
    }

    boolean captureOpaqueResourceUniformInt(VirtualProgram program, int uniformIndex, int value) {
        if (uniformIndex < 0 || uniformIndex >= program.activeUniforms.size()) {
            return false;
        }
        ReflectedUniform uniform = program.activeUniforms.get(uniformIndex);
        Optional<net.vulkanic.VulkanicUniformReflectionType> reflectionType =
            net.vulkanic.VulkanicUniformReflectionType.fromLegacyGlConstant(uniform.legacyType());
        if (reflectionType.isPresent() && (reflectionType.get().isSampler() || reflectionType.get().isImage())) {
            program.opaqueResourceUniformValuesByIndex.put(uniformIndex, value);
            return true;
        }
        return false;
    }

    boolean writeStandaloneUniformInts(VirtualProgram program, int location, int[] values) {
        StandaloneUniformField field = program.standaloneFieldsByLocation.get(location);
        if (field == null) {
            return false;
        }

        synchronized (program) {
            ByteBuffer backingData = program.standaloneBackingData;
            if (backingData == null) {
                return false;
            }

            writeIntUniform(field, backingData, values);
            program.standaloneDirty = true;
            return true;
        }
    }

    boolean writeStandaloneUniformFloats(VirtualProgram program, int location, float[] values) {
        StandaloneUniformField field = program.standaloneFieldsByLocation.get(location);
        if (field == null) {
            return false;
        }

        synchronized (program) {
            ByteBuffer backingData = program.standaloneBackingData;
            if (backingData == null) {
                return false;
            }

            writeFloatUniform(field, backingData, values);
            program.standaloneDirty = true;
            return true;
        }
    }

    static int standaloneUniformBlockBindingIndex(VirtualProgram program) {
        for (ReflectedResourceBinding resourceBinding : program.activeResourceBindings) {
            if (ShadercSpirvCompiler.GENERATED_UNIFORM_BLOCK_NAME.equals(resourceBinding.name())) {
                return resourceBinding.binding();
            }
        }

        Set<DescriptorSlot> usedSlots = new java.util.LinkedHashSet<>();
        for (ReflectedResourceBinding resourceBinding : program.activeResourceBindings) {
            usedSlots.add(new DescriptorSlot(resourceBinding.set(), resourceBinding.binding()));
        }
        return nextUnusedBinding(0, usedSlots, 0);
    }

    static int nextUnusedBinding(int set, Set<DescriptorSlot> usedSlots, int startBinding) {
        int binding = Math.max(0, startBinding);
        while (usedSlots.contains(new DescriptorSlot(set, binding))) {
            binding++;
        }
        return binding;
    }

    static Map<String, List<Integer>> collectStandaloneUniformOffsets(List<String> standaloneUniformDeclarations) {
        Map<String, List<Integer>> offsetsByName = new java.util.LinkedHashMap<>();
        int offset = 0;
        for (String declaration : standaloneUniformDeclarations) {
            java.util.regex.Matcher uniformMatcher = GLSL_STANDALONE_UNIFORM_MEMBER_PATTERN.matcher(declaration);
            if (!uniformMatcher.matches()) {
                continue;
            }

            String uniformTypeName = uniformMatcher.group(1);
            String uniformName = uniformMatcher.group(2);
            int arraySize = uniformMatcher.group(3) == null ? 1 : Integer.parseInt(uniformMatcher.group(3));
            Optional<net.vulkanic.VulkanicUniformReflectionType> reflectionType =
                net.vulkanic.VulkanicUniformReflectionType.fromGlslTypeName(uniformTypeName);
            if (reflectionType.isEmpty() || reflectionType.get().isSampler() || reflectionType.get().isImage()) {
                continue;
            }

            int baseAlignment = std140BaseAlignment(reflectionType.get());
            int typeSize = std140TypeSize(reflectionType.get());
            int stride = Math.max(1, arraySize) > 1 ? roundUpTo(typeSize, 16) : typeSize;
            offset = roundUpTo(offset, baseAlignment);
            offsetsByName.put(uniformName, List.of(offset));
            offset += stride * Math.max(1, arraySize);
        }

        return offsetsByName;
    }

    private static void initializeStandaloneUniformDefaults(
        Map<Integer, StandaloneUniformField> fieldsByLocation,
        ByteBuffer backingData
    ) {
        float[] identity4 = new float[] {
            1.0F, 0.0F, 0.0F, 0.0F,
            0.0F, 1.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 1.0F, 0.0F,
            0.0F, 0.0F, 0.0F, 1.0F
        };
        float[] identity3 = new float[] {
            1.0F, 0.0F, 0.0F,
            0.0F, 1.0F, 0.0F,
            0.0F, 0.0F, 1.0F
        };

        for (StandaloneUniformField field : fieldsByLocation.values()) {
            for (int offset : field.offsets()) {
                switch (field.type()) {
                    case FLOAT_MAT4 -> writeFloatUniformAtOffset(field, backingData, offset, identity4);
                    case FLOAT_MAT3 -> writeFloatUniformAtOffset(field, backingData, offset, identity3);
                    case FLOAT -> {
                        if (isOneDefaultStandaloneFloat(field.name())) {
                            backingData.putFloat(offset, 1.0F);
                        }
                    }
                    case FLOAT_VEC2 -> {
                        if ("u_TexCoordShrink".equals(field.name())) {
                            backingData.putFloat(offset, 1.0F);
                            backingData.putFloat(offset + 4, 1.0F);
                        }
                    }
                    default -> {
                    }
                }
            }
        }
    }

    private static boolean isOneDefaultStandaloneFloat(String name) {
        return "iris_FogEnd".equals(name)
            || "far".equals(name)
            || "viewWidth".equals(name)
            || "viewHeight".equals(name)
            || "aspectRatio".equals(name);
    }

    static void writeIntUniform(StandaloneUniformField field, ByteBuffer backingData, int[] values) {
        for (int offset : field.offsets()) {
            writeIntUniformAtOffset(field, backingData, offset, values);
        }
    }

    private static void writeIntUniformAtOffset(
        StandaloneUniformField field,
        ByteBuffer backingData,
        int offset,
        int[] values
    ) {
        int componentCount = standaloneUniformLogicalComponentCount(field.type());
        int elementCount = Math.min(field.arraySize(), Math.max(1, (values.length + componentCount - 1) / componentCount));
        int[] padded = new int[componentCount];

        for (int element = 0; element < elementCount; element++) {
            java.util.Arrays.fill(padded, 0);
            int sourceBase = element * componentCount;
            int copyLength = Math.min(componentCount, values.length - sourceBase);
            if (copyLength > 0) {
                System.arraycopy(values, sourceBase, padded, 0, copyLength);
            }
            writeIntUniformElementAtOffset(field, backingData, offset + (element * field.stride()), padded);
        }
    }

    private static void writeIntUniformElementAtOffset(
        StandaloneUniformField field,
        ByteBuffer backingData,
        int offset,
        int[] padded
    ) {
        switch (field.type()) {
            case INT, UINT, BOOL -> backingData.putInt(offset, padded[0]);
            case INT_VEC2, UINT_VEC2, BOOL_VEC2 -> {
                backingData.putInt(offset, padded[0]);
                backingData.putInt(offset + 4, padded[1]);
            }
            case INT_VEC3, UINT_VEC3, BOOL_VEC3 -> {
                backingData.putInt(offset, padded[0]);
                backingData.putInt(offset + 4, padded[1]);
                backingData.putInt(offset + 8, padded[2]);
            }
            case INT_VEC4, UINT_VEC4, BOOL_VEC4 -> {
                backingData.putInt(offset, padded[0]);
                backingData.putInt(offset + 4, padded[1]);
                backingData.putInt(offset + 8, padded[2]);
                backingData.putInt(offset + 12, padded[3]);
            }
            default -> {
            }
        }
    }

    static void writeFloatUniform(StandaloneUniformField field, ByteBuffer backingData, float[] values) {
        for (int offset : field.offsets()) {
            writeFloatUniformAtOffset(field, backingData, offset, values);
        }
    }

    private static void writeFloatUniformAtOffset(
        StandaloneUniformField field,
        ByteBuffer backingData,
        int offset,
        float[] values
    ) {
        int componentCount = standaloneUniformLogicalComponentCount(field.type());
        int elementCount = Math.min(field.arraySize(), Math.max(1, (values.length + componentCount - 1) / componentCount));

        for (int element = 0; element < elementCount; element++) {
            int sourceBase = element * componentCount;
            writeFloatUniformElementAtOffset(field, backingData, offset + (element * field.stride()), values, sourceBase);
        }
    }

    private static void writeFloatUniformElementAtOffset(
        StandaloneUniformField field,
        ByteBuffer backingData,
        int offset,
        float[] values,
        int sourceBase
    ) {
        switch (field.type()) {
            case FLOAT -> backingData.putFloat(offset, floatValueOrZero(values, sourceBase));
            case FLOAT_VEC2 -> {
                backingData.putFloat(offset, floatValueOrZero(values, sourceBase));
                backingData.putFloat(offset + 4, floatValueOrZero(values, sourceBase + 1));
            }
            case FLOAT_VEC3 -> {
                backingData.putFloat(offset, floatValueOrZero(values, sourceBase));
                backingData.putFloat(offset + 4, floatValueOrZero(values, sourceBase + 1));
                backingData.putFloat(offset + 8, floatValueOrZero(values, sourceBase + 2));
            }
            case FLOAT_VEC4 -> {
                backingData.putFloat(offset, floatValueOrZero(values, sourceBase));
                backingData.putFloat(offset + 4, floatValueOrZero(values, sourceBase + 1));
                backingData.putFloat(offset + 8, floatValueOrZero(values, sourceBase + 2));
                backingData.putFloat(offset + 12, floatValueOrZero(values, sourceBase + 3));
            }
            case FLOAT_MAT2 -> writeStd140MatrixColumns(backingData, offset, values, sourceBase, 2, 2);
            case FLOAT_MAT3 -> writeStd140MatrixColumns(backingData, offset, values, sourceBase, 3, 3);
            case FLOAT_MAT4 -> writeStd140MatrixColumns(backingData, offset, values, sourceBase, 4, 4);
            default -> {
            }
        }
    }

    private static float floatValueOrZero(float[] values, int index) {
        return index >= 0 && index < values.length ? values[index] : 0.0F;
    }

    private static void writeStd140MatrixColumns(
        ByteBuffer backingData,
        int baseOffset,
        float[] values,
        int sourceBase,
        int columns,
        int rows
    ) {
        for (int col = 0; col < columns; col++) {
            int columnOffset = baseOffset + (col * 16);
            for (int row = 0; row < rows; row++) {
                int sourceIndex = sourceBase + (col * rows) + row;
                float component = floatValueOrZero(values, sourceIndex);
                backingData.putFloat(columnOffset + (row * 4), component);
            }
        }
    }

    private static int standaloneUniformLogicalComponentCount(net.vulkanic.VulkanicUniformReflectionType type) {
        return switch (type) {
            case FLOAT, INT, UINT, BOOL -> 1;
            case FLOAT_VEC2, INT_VEC2, UINT_VEC2, BOOL_VEC2 -> 2;
            case FLOAT_VEC3, INT_VEC3, UINT_VEC3, BOOL_VEC3 -> 3;
            case FLOAT_VEC4, INT_VEC4, UINT_VEC4, BOOL_VEC4 -> 4;
            case FLOAT_MAT2 -> 4;
            case FLOAT_MAT3 -> 9;
            case FLOAT_MAT4 -> 16;
            default -> 1;
        };
    }

    static boolean standaloneUniformIsFloatLike(net.vulkanic.VulkanicUniformReflectionType type) {
        return switch (type) {
            case FLOAT, FLOAT_VEC2, FLOAT_VEC3, FLOAT_VEC4, FLOAT_MAT2, FLOAT_MAT3, FLOAT_MAT4 -> true;
            default -> false;
        };
    }

    static boolean standaloneUniformIsIntegerLike(net.vulkanic.VulkanicUniformReflectionType type) {
        return switch (type) {
            case INT, UINT, BOOL, INT_VEC2, UINT_VEC2, BOOL_VEC2, INT_VEC3, UINT_VEC3, BOOL_VEC3,
                INT_VEC4, UINT_VEC4, BOOL_VEC4 -> true;
            default -> false;
        };
    }

    static float[] readStandaloneUniformFloats(
        StandaloneUniformField field,
        ByteBuffer backingData,
        int offset
    ) {
        int componentCount = standaloneUniformLogicalComponentCount(field.type());
        float[] values = new float[Math.max(1, field.arraySize()) * componentCount];
        int writeIndex = 0;
        for (int element = 0; element < Math.max(1, field.arraySize()); element++) {
            int elementOffset = offset + (element * field.stride());
            writeIndex = readStandaloneUniformFloatElement(field, backingData, elementOffset, values, writeIndex);
        }
        return values;
    }

    private static int readStandaloneUniformFloatElement(
        StandaloneUniformField field,
        ByteBuffer backingData,
        int offset,
        float[] values,
        int writeIndex
    ) {
        switch (field.type()) {
            case FLOAT -> values[writeIndex++] = backingData.getFloat(offset);
            case FLOAT_VEC2 -> {
                values[writeIndex++] = backingData.getFloat(offset);
                values[writeIndex++] = backingData.getFloat(offset + 4);
            }
            case FLOAT_VEC3 -> {
                values[writeIndex++] = backingData.getFloat(offset);
                values[writeIndex++] = backingData.getFloat(offset + 4);
                values[writeIndex++] = backingData.getFloat(offset + 8);
            }
            case FLOAT_VEC4 -> {
                values[writeIndex++] = backingData.getFloat(offset);
                values[writeIndex++] = backingData.getFloat(offset + 4);
                values[writeIndex++] = backingData.getFloat(offset + 8);
                values[writeIndex++] = backingData.getFloat(offset + 12);
            }
            case FLOAT_MAT2 -> writeIndex = readStd140MatrixColumns(backingData, offset, values, writeIndex, 2, 2);
            case FLOAT_MAT3 -> writeIndex = readStd140MatrixColumns(backingData, offset, values, writeIndex, 3, 3);
            case FLOAT_MAT4 -> writeIndex = readStd140MatrixColumns(backingData, offset, values, writeIndex, 4, 4);
            default -> {
            }
        }
        return writeIndex;
    }

    private static int readStd140MatrixColumns(
        ByteBuffer backingData,
        int baseOffset,
        float[] values,
        int writeIndex,
        int columns,
        int rows
    ) {
        for (int col = 0; col < columns; col++) {
            int columnOffset = baseOffset + (col * 16);
            for (int row = 0; row < rows; row++) {
                values[writeIndex++] = backingData.getFloat(columnOffset + (row * 4));
            }
        }
        return writeIndex;
    }

    static int[] readStandaloneUniformInts(
        StandaloneUniformField field,
        ByteBuffer backingData,
        int offset
    ) {
        int componentCount = standaloneUniformLogicalComponentCount(field.type());
        int[] values = new int[Math.max(1, field.arraySize()) * componentCount];
        int writeIndex = 0;
        for (int element = 0; element < Math.max(1, field.arraySize()); element++) {
            int elementOffset = offset + (element * field.stride());
            for (int component = 0; component < componentCount; component++) {
                values[writeIndex++] = backingData.getInt(elementOffset + (component * 4));
            }
        }
        return values;
    }

    static int roundUpTo(int value, int alignment) {
        if (alignment <= 0) {
            return value;
        }
        int remainder = value % alignment;
        return remainder == 0 ? value : value + (alignment - remainder);
    }

    private static int std140BaseAlignment(net.vulkanic.VulkanicUniformReflectionType type) {
        return switch (type) {
            case FLOAT, INT, UINT, BOOL -> 4;
            case FLOAT_VEC2, INT_VEC2, UINT_VEC2, BOOL_VEC2 -> 8;
            case FLOAT_VEC3, INT_VEC3, UINT_VEC3, BOOL_VEC3,
                FLOAT_VEC4, INT_VEC4, UINT_VEC4, BOOL_VEC4,
                FLOAT_MAT2, FLOAT_MAT3, FLOAT_MAT4 -> 16;
            default -> 16;
        };
    }

    private static int std140TypeSize(net.vulkanic.VulkanicUniformReflectionType type) {
        return switch (type) {
            case FLOAT, INT, UINT, BOOL -> 4;
            case FLOAT_VEC2, INT_VEC2, UINT_VEC2, BOOL_VEC2 -> 8;
            case FLOAT_VEC3, INT_VEC3, UINT_VEC3, BOOL_VEC3 -> 12;
            case FLOAT_VEC4, INT_VEC4, UINT_VEC4, BOOL_VEC4 -> 16;
            case FLOAT_MAT2 -> 32;
            case FLOAT_MAT3 -> 48;
            case FLOAT_MAT4 -> 64;
            default -> 16;
        };
    }

    static final class VirtualShader {
        final VulkanicShaderStage stage;
        volatile String source;
        volatile VulkanicSpirvModule compiledModule;
        volatile long nativeShaderModuleHandle = VK10.VK_NULL_HANDLE;
        volatile boolean compileStatus;
        volatile boolean deletionPending;
        volatile String infoLog = "";
        @Nullable
        volatile String debugLabel;

        private VirtualShader(VulkanicShaderStage stage) {
            this.stage = stage;
        }
    }

    static final class VirtualProgram {
        final Set<Integer> attachedShaderIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
        final Map<String, Integer> attributeLocationsByName = new ConcurrentHashMap<>();
        final Map<String, Integer> uniformLocationTokensByName = new ConcurrentHashMap<>();
        final ConcurrentHashMap<Integer, Integer> opaqueResourceUniformValuesByIndex = new ConcurrentHashMap<>();
        volatile List<String> activeUniformNames = List.of();
        volatile List<ReflectedUniform> activeUniforms = List.of();
        volatile List<String> activeUniformBlocks = List.of();
        volatile List<ReflectedResourceBinding> activeResourceBindings = List.of();
        volatile List<String> standaloneUniformDeclarations = List.of();
        volatile List<VulkanicSpirvModule> linkedSpirvModules = List.of();
        volatile List<VulkanicSpirvModule.FragmentOutput> fragmentOutputs = List.of();
        volatile List<ReflectedVertexInput> vertexInputs = List.of();
        volatile int[] computeWorkGroupSize = new int[]{1, 1, 1};
        volatile Map<Integer, StandaloneUniformField> standaloneFieldsByLocation = Map.of();
        volatile int standaloneBackingSize;
        @Nullable
        volatile ByteBuffer standaloneBackingData;
        @Nullable
        volatile VulkanBuffer standaloneGpuBuffer;
        volatile boolean standaloneDirty;
        volatile boolean linkStatus;
        volatile String infoLog = "";
        @Nullable
        volatile String debugLabel;

        void closeStandaloneUniformBacking() {
            VulkanBuffer buffer = standaloneGpuBuffer;
            standaloneGpuBuffer = null;
            if (buffer != null && !buffer.isClosed()) {
                buffer.close();
            }
        }
    }

    record LinkedProgramExecutionSnapshot(
        int programId,
        boolean linkStatus,
        @Nullable String debugLabel,
        List<VulkanicSpirvModule> linkedSpirvModules,
        List<ReflectedVertexInput> vertexInputs,
        Map<String, Integer> attributeLocationsByName,
        PipelineDescriptor.ResourceLayout resourceLayout,
        List<String> activeUniformNames,
        List<ReflectedUniform> activeUniforms,
        List<String> activeUniformBlocks,
        List<ReflectedResourceBinding> activeResourceBindings,
        int[] computeWorkGroupSize,
        Map<Integer, Integer> opaqueResourceUniformValuesByIndex,
        List<VulkanicSpirvModule.FragmentOutput> fragmentOutputs
    ) {
        LinkedProgramExecutionSnapshot {
            linkedSpirvModules = List.copyOf(linkedSpirvModules);
            vertexInputs = List.copyOf(vertexInputs);
            attributeLocationsByName = Map.copyOf(attributeLocationsByName);
            activeUniformNames = List.copyOf(activeUniformNames);
            activeUniforms = List.copyOf(activeUniforms);
            activeUniformBlocks = List.copyOf(activeUniformBlocks);
            activeResourceBindings = List.copyOf(activeResourceBindings);
            computeWorkGroupSize = computeWorkGroupSize.clone();
            opaqueResourceUniformValuesByIndex = Map.copyOf(opaqueResourceUniformValuesByIndex);
            fragmentOutputs = List.copyOf(fragmentOutputs);
        }
    }

    record ReflectedVertexInput(int location, String typeName) {
    }

    record ReflectedUniform(String name, int arraySize, int legacyType) {
    }

    record ReflectedResourceRequest(String name, PipelineDescriptor.ResourceType type) {
    }

    record ReflectedResourceBinding(String name, PipelineDescriptor.ResourceType type, int set, int binding) {
    }

    record ExplicitDescriptorBinding(int set, int binding) {
    }

    record DescriptorSlot(int set, int binding) {
    }

    record StandaloneUniformField(
        String name,
        net.vulkanic.VulkanicUniformReflectionType type,
        int[] offsets,
        int arraySize,
        int stride
    ) {
        StandaloneUniformField {
            offsets = offsets.clone();
        }
    }

    record UniformLocationRef(int programId, int uniformIndex) {
    }
}
