package net.minecraft.world.level.levelgen;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;
import java.lang.ref.Reference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.heightproviders.ConstantHeight;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.FeaturePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.DirectPoolAlias;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasBinding;
import net.minecraft.world.level.levelgen.structure.pools.alias.RandomGroupPoolAlias;
import net.minecraft.world.level.levelgen.structure.pools.alias.RandomPoolAlias;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jetbrains.annotations.Nullable;

/** Rust-owned {@code JigsawStructure.generate} ({@code structure/jigsaw.rs}): the
 * start height, alias lookup, start piece, biome check and jigsaw assembly over
 * the structure's pools and templates, which this exports once per structure.
 * Heights come from the native column source and the biome check from the
 * climate tree, carrying the thread's previous leaf. Java builds the start's
 * piece objects from the plan. Anything Rust does not model keeps Java's path. */
public final class NativeJigsaw {
    private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_structure_jigsaw_create",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle RELEASE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_structure_jigsaw_release",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle PLAN = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_structure_jigsaw_plan",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final Cleaner CLEANER = Cleaner.create();
    private static final boolean ENABLED = !Boolean.getBoolean("mattmc.worldgen.javaJigsaw");
    private static final Object UNSUPPORTED = new Object();
    /** Starts Rust planned, and jigsaw starts Java planned while native planning was on, by reason. */
    public static final AtomicLong PLANNED = new AtomicLong();
    public static final Map<String, AtomicLong> JAVA = new java.util.concurrent.ConcurrentHashMap<>();
    // By structure and registries; weak keys do not retain old worlds' structures.
    private static final Map<JigsawStructure, Object> EXPORTS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private NativeJigsaw() {
    }

    /** One structure's exported pools: the native data and the Java objects its indices name. */
    private static final class Export {
        final MemorySegment handle;
        final RegistryAccess registries;
        final StructureTemplateManager templates;
        final List<StructurePoolElement> elements;
        final int startPool, startName;
        /** By climate parameter list: per node, whether its leaf biome is in the structure's set. */
        final Map<Climate.ParameterList<?>, byte[]> accept = java.util.Collections.synchronizedMap(new IdentityHashMap<>());

        Export(MemorySegment handle, RegistryAccess registries, StructureTemplateManager templates, List<StructurePoolElement> elements, int startPool,
               int startName) {
            this.handle = handle;
            this.registries = registries;
            this.templates = templates;
            this.elements = elements;
            this.startPool = startPool;
            this.startName = startName;
            long address = handle.address();
            CLEANER.register(this, () -> {
                try {
                    RELEASE.invokeExact(MemorySegment.ofAddress(address));
                } catch (Throwable ignored) {
                }
            });
        }
    }

    private static void java(String reason) {
        JAVA.computeIfAbsent(reason, k -> new AtomicLong()).incrementAndGet();
    }

    public static String statistics() {
        return "jigsawPlanned=" + PLANNED.get() + " jigsawJava=" + new java.util.TreeMap<>(JAVA).toString().replace(' ', '_');
    }

    /**
     * {@code structure.generate(...)} natively, or null when Java must plan the start
     * (nothing was drawn or changed then).
     */
    @Nullable
    public static StructureStart generate(JigsawStructure structure, RegistryAccess registries, ChunkGenerator generator, BiomeSource biomeSource,
                                          RandomState randomState, StructureTemplateManager templates, long seed, ChunkPos chunkPos, int references,
                                          LevelHeightAccessor height, java.util.function.Predicate<Holder<Biome>> biomes) {
        if (!ENABLED) return null;
        if (!(generator instanceof NoiseBasedChunkGenerator noise)) {
            java("generator");
            return null;
        }
        NativeColumns columns = NativeColumns.source(noise, height, randomState);
        if (columns == null) {
            java("columns");
            return null;
        }
        int[] surface = columns.matches(Heightmap.Types.WORLD_SURFACE_WG);
        var projectType = structure.nativeProjectStartToHeightmap();
        int[] projection = projectType.isPresent() ? columns.matches(projectType.get()) : new int[0];
        if (surface == null || projection == null) {
            java("heightmap");
            return null;
        }
        // The start height: constant or uniform anchors over the generator's context.
        var context = new WorldGenerationContext(generator, height);
        var startHeight = structure.nativeStartHeight();
        int kind, a, b;
        if (startHeight instanceof ConstantHeight constant) {
            kind = 0;
            a = constant.getValue().resolveY(context);
            b = 0;
        } else if (startHeight.getClass() == UniformHeight.class) {
            var uniform = (UniformHeight)startHeight;
            kind = 1;
            a = uniform.minInclusive().resolveY(context);
            b = uniform.maxInclusive().resolveY(context);
        } else {
            java("start height");
            return null;
        }
        Object entry = EXPORTS.get(structure);
        if (!(entry instanceof Export export && export.registries == registries && export.templates == templates)) {
            if (entry == UNSUPPORTED) {
                java("unsupported pools");
                return null;
            }
            Export created = export(structure, registries, templates);
            EXPORTS.put(structure, created == null ? UNSUPPORTED : created);
            if (created == null) {
                java("unsupported pools");
                return null;
            }
            entry = created;
        }
        Export export = (Export)entry;
        // The biome check: a plain multi-noise source's climate tree, or a fixed source's one biome.
        long climate = 0, nodes = 0, nodeCount = 0;
        byte[] accept = new byte[1];
        int fixedAccept = 0;
        Climate.ParameterList<Holder<Biome>> list = null;
        NativeBiomeSearch.Program program = null;
        if (biomeSource.getClass() == MultiNoiseBiomeSource.class && ((MultiNoiseBiomeSource)biomeSource).nativeParameters().getClass() == Climate.ParameterList.class) {
            list = ((MultiNoiseBiomeSource)biomeSource).nativeParameters();
            program = NativeBiomeSearch.climateProgram(randomState.sampler());
            if (program == null) {
                java("climate program");
                return null;
            }
            var parameters = list;
            accept = export.accept.computeIfAbsent(parameters, p -> {
                byte[] bytes = new byte[parameters.nativeNodeCount()];
                for (int node = 0; node < bytes.length; node++) {
                    Holder<Biome> leaf = parameters.nativeLeafValue(node);
                    bytes[node] = (byte)(leaf != null && biomes.test(leaf) ? 1 : 0);
                }
                return bytes;
            });
            climate = program.handle;
            nodes = list.nativeNodes().address();
            nodeCount = list.nativeNodeCount();
        } else if (biomeSource.getClass() == FixedBiomeSource.class) {
            fixedAccept = biomes.test(biomeSource.possibleBiomes().iterator().next()) ? 1 : 0;
        } else {
            java("biome source");
            return null;
        }
        var distance = structure.nativeMaxDistance();
        var padding = structure.nativeDimensionPadding();
        int[] head = {chunkPos.x, chunkPos.z, kind, a, b, export.startPool, structure.nativeStartJigsawName().isPresent() ? export.startName : -1,
            structure.nativeMaxDepth(), structure.nativeUseExpansionHack() ? 1 : 0, projectType.isPresent() ? 1 : 0, distance.horizontal(),
            distance.vertical(), padding == DimensionPadding.ZERO ? 0 : 1, padding.bottom(), padding.top(), height.getMinY(), height.getMaxY(), fixedAccept,
            surface.length, projection.length};
        int[] ints = new int[head.length + surface.length + projection.length];
        System.arraycopy(head, 0, ints, 0, head.length);
        System.arraycopy(surface, 0, ints, head.length, surface.length);
        System.arraycopy(projection, 0, ints, head.length + surface.length, projection.length);
        int status;
        long[] out;
        int previous;
        try (Arena arena = Arena.ofConfined()) {
            var intSegment = arena.allocateFrom(ValueLayout.JAVA_INT, ints);
            var longSegment = arena.allocateFrom(ValueLayout.JAVA_LONG, seed, climate, nodes, nodeCount);
            var acceptSegment = arena.allocateFrom(ValueLayout.JAVA_BYTE, accept);
            var previousSegment = arena.allocateFrom(ValueLayout.JAVA_INT, list == null ? -1 : list.previousNativeLeaf());
            int capacity = 1 << 16;
            var outSegment = arena.allocate(ValueLayout.JAVA_LONG, capacity);
            status = (int)PLAN.invokeExact(export.handle, columns.handle(), columns.program(), intSegment, ints.length, longSegment, acceptSegment,
                previousSegment, outSegment, capacity);
            out = status > 0 ? outSegment.asSlice(0, status * 8L).toArray(ValueLayout.JAVA_LONG) : null;
            previous = previousSegment.get(ValueLayout.JAVA_INT, 0);
        } catch (Throwable error) {
            throw new IllegalStateException("Native jigsaw planning failed", error);
        } finally {
            Reference.reachabilityFence(columns);
            Reference.reachabilityFence(program);
        }
        if (status == -2) {
            java("declined");
            return null;
        }
        if (status <= 0) throw new IllegalStateException("Native jigsaw planning returned " + status);
        if (list != null) list.setPreviousNativeLeaf(previous);
        PLANNED.incrementAndGet();
        if (out[0] == 0) return StructureStart.INVALID_START;
        int count = (int)out[4];
        if (count == 0) return StructureStart.INVALID_START;
        var builder = new StructurePiecesBuilder();
        var liquid = structure.nativeLiquidSettings();
        int at = 5;
        for (int piece = 0; piece < count; piece++) {
            var element = export.elements.get((int)out[at]);
            var pos = new BlockPos((int)out[at + 1], (int)out[at + 2], (int)out[at + 3]);
            int ground = (int)out[at + 4];
            var rotation = Rotation.values()[(int)out[at + 5]];
            var box = new BoundingBox((int)out[at + 6], (int)out[at + 7], (int)out[at + 8], (int)out[at + 9], (int)out[at + 10], (int)out[at + 11]);
            var poolPiece = new PoolElementStructurePiece(templates, element, pos, ground, rotation, box, liquid);
            int junctions = (int)out[at + 12];
            at += 13;
            for (int j = 0; j < junctions; j++, at += 5) {
                poolPiece.addJunction(new JigsawJunction((int)out[at], (int)out[at + 1], (int)out[at + 2], (int)out[at + 3],
                    out[at + 4] != 0 ? StructureTemplatePool.Projection.RIGID : StructureTemplatePool.Projection.TERRAIN_MATCHING));
            }
            builder.addPiece(poolPiece);
        }
        var start = new StructureStart(structure, chunkPos, references, builder.build());
        return start.isValid() ? start : StructureStart.INVALID_START;
    }

    /** Interns resource locations as the IDs Rust compares. */
    private static final class Names {
        final Map<String, Integer> ids = new HashMap<>();

        int id(ResourceLocation location) {
            return this.ids.computeIfAbsent(location.toString(), k -> this.ids.size());
        }
    }

    @Nullable
    private static Export export(JigsawStructure structure, RegistryAccess registries, StructureTemplateManager templates) {
        var registry = registries.lookupOrThrow(Registries.TEMPLATE_POOL);
        var names = new Names();
        List<StructureTemplate> templateList = new ArrayList<>();
        Map<StructureTemplate, Integer> templateIds = new IdentityHashMap<>();
        List<StructurePoolElement> elements = new ArrayList<>();
        Map<StructurePoolElement, Integer> elementIds = new IdentityHashMap<>();
        // Pools by key, in discovery order; the start pool may also be a direct holder.
        Map<String, Integer> poolIds = new HashMap<>();
        List<StructureTemplatePool> pools = new ArrayList<>();
        List<Integer> poolKeys = new ArrayList<>();
        ArrayDeque<Object[]> queue = new ArrayDeque<>();
        java.util.function.Function<ResourceKey<StructureTemplatePool>, Integer> poolByKey = key -> {
            Integer known = poolIds.get(key.location().toString());
            if (known != null) return known;
            var holder = registry.get(key);
            if (holder.isEmpty()) return -1;
            int id = pools.size();
            poolIds.put(key.location().toString(), id);
            pools.add(holder.get().value());
            poolKeys.add(names.id(key.location()));
            queue.add(new Object[]{id});
            return id;
        };
        var start = structure.getStartPool();
        int startPool;
        if (start.unwrapKey().isPresent()) {
            startPool = names.id(start.unwrapKey().get().location());
            if (poolByKey.apply(start.unwrapKey().get()) < 0) return null;
        } else {
            return null;
        }
        // Every alias target, so a resolved alias always finds its pool.
        List<ResourceKey<StructureTemplatePool>> aliasTargets = new ArrayList<>();
        for (PoolAliasBinding binding : structure.getPoolAliases()) binding.allTargets().forEach(aliasTargets::add);
        for (var target : aliasTargets) poolByKey.apply(target);
        poolByKey.apply(Pools.EMPTY);
        List<Integer> fallbacks = new ArrayList<>();
        List<int[]> poolElements = new ArrayList<>();
        java.util.function.Function<StructurePoolElement, Integer> elementId = new java.util.function.Function<>() {
            @Override
            public Integer apply(StructurePoolElement element) {
                Integer known = elementIds.get(element);
                if (known != null) return known;
                if (element instanceof ListPoolElement listElement) {
                    for (var sub : listElement.getElements()) if (this.apply(sub) < 0) return -1;
                } else if (element instanceof SinglePoolElement single) {
                    var template = single.nativeTemplate(templates);
                    if (!templateIds.containsKey(template)) {
                        templateIds.put(template, templateList.size());
                        templateList.add(template);
                        for (var palette : template.nativePalettes()) {
                            for (var jigsaw : palette.jigsaws()) poolByKey.apply(jigsaw.pool());
                        }
                    }
                } else if (element != EmptyPoolElement.INSTANCE && element.getClass() != FeaturePoolElement.class) {
                    return -1;
                }
                int id = elements.size();
                elementIds.put(element, id);
                elements.add(element);
                return id;
            }
        };
        while (!queue.isEmpty()) {
            int pool = (int)queue.poll()[0];
            var value = pools.get(pool);
            var fallback = value.getFallback();
            if (fallback.unwrapKey().isEmpty()) return null;
            int fallbackId = poolByKey.apply(fallback.unwrapKey().get());
            while (fallbacks.size() <= pool) fallbacks.add(-1);
            fallbacks.set(pool, fallbackId);
            List<StructurePoolElement> expanded = value.nativeTemplates();
            int[] ids = new int[expanded.size()];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = elementId.apply(expanded.get(i));
                if (ids[i] < 0) return null;
            }
            while (poolElements.size() <= pool) poolElements.add(null);
            poolElements.set(pool, ids);
        }
        List<Integer> words = new ArrayList<>();
        words.add(names.id(ResourceLocation.withDefaultNamespace("bottom")));
        words.add(names.id(JigsawBlockEntity.EMPTY_ID));
        words.add(names.id(Pools.EMPTY.location()));
        words.add(templateList.size());
        for (var template : templateList) {
            var size = template.getSize();
            words.addAll(List.of(size.getX(), size.getY(), size.getZ(), template.nativePalettes().size()));
            for (var palette : template.nativePalettes()) {
                var jigsaws = palette.jigsaws();
                words.add(jigsaws.size());
                for (var jigsaw : jigsaws) {
                    var state = jigsaw.info().state();
                    var pos = jigsaw.info().pos();
                    words.addAll(List.of(pos.getX(), pos.getY(), pos.getZ(), JigsawBlock.getFrontFacing(state).get3DDataValue(),
                        JigsawBlock.getTopFacing(state).get3DDataValue(), jigsaw.jointType() == JigsawBlockEntity.JointType.ROLLABLE ? 1 : 0,
                        names.id(jigsaw.name()), names.id(jigsaw.pool().location()), names.id(jigsaw.target()), jigsaw.placementPriority(),
                        jigsaw.selectionPriority()));
                }
            }
        }
        words.add(elements.size());
        for (var element : elements) {
            words.add(element == EmptyPoolElement.INSTANCE ? 0 : element instanceof SinglePoolElement ? 1 : element instanceof FeaturePoolElement ? 2 : 3);
            words.add(element.getProjection() == StructureTemplatePool.Projection.RIGID ? 1 : 0);
            if (element instanceof SinglePoolElement single) {
                words.add(templateIds.get(single.nativeTemplate(templates)));
            } else if (element instanceof ListPoolElement listElement) {
                words.add(listElement.getElements().size());
                for (var sub : listElement.getElements()) words.add(elementIds.get(sub));
            }
        }
        words.add(pools.size());
        for (int pool = 0; pool < pools.size(); pool++) {
            words.add(poolKeys.get(pool));
            words.add(poolKeys.get(pool) == names.id(Pools.EMPTY.location()) ? 1 : 0);
            words.add(fallbacks.get(pool));
            int[] ids = poolElements.get(pool);
            words.add(ids.length);
            for (int id : ids) words.add(id);
        }
        if (!aliases(structure.getPoolAliases(), names, words)) return null;
        int[] array = words.stream().mapToInt(Integer::intValue).toArray();
        MemorySegment handle;
        try (Arena arena = Arena.ofConfined()) {
            handle = (MemorySegment)CREATE.invokeExact(arena.allocateFrom(ValueLayout.JAVA_INT, array), array.length);
        } catch (Throwable error) {
            throw new IllegalStateException("Cannot export jigsaw pools", error);
        }
        if (handle.address() == 0) return null;
        int startName = structure.nativeStartJigsawName().map(names::id).orElse(-1);
        return new Export(handle, registries, templates, elements, startPool, startName);
    }

    private static boolean aliases(List<PoolAliasBinding> bindings, Names names, List<Integer> words) {
        words.add(bindings.size());
        for (var binding : bindings) {
            if (binding instanceof DirectPoolAlias direct) {
                words.addAll(List.of(0, names.id(direct.alias().location()), names.id(direct.target().location())));
            } else if (binding instanceof RandomPoolAlias random) {
                var targets = random.targets().unwrap();
                words.addAll(List.of(1, names.id(random.alias().location()), targets.size()));
                for (var target : targets) {
                    words.add(names.id(target.value().location()));
                    words.add(target.weight());
                }
            } else if (binding instanceof RandomGroupPoolAlias group) {
                var groups = group.groups().unwrap();
                words.addAll(List.of(2, groups.size()));
                for (var weighted : groups) {
                    if (!aliases(weighted.value(), names, words)) return false;
                    words.add(weighted.weight());
                }
            } else {
                return false;
            }
        }
        return true;
    }
}
