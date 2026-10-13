package com.seibel.distanthorizons.core.dataObjects.transformers;

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPosMutable;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.HashSet;
import net.vulkanic.world.NativeDhRenderConversion;
import static net.vulkanic.world.NativeDhRenderConversion.*;
import static com.seibel.distanthorizons.core.dataObjects.transformers.FullDataToRenderDataTransformer.*;

/** Temporary object queries and publications; Rust chooses their order and owns
 * the conversion loop. Stock-world calls reuse a cleared worker context; custom
 * worlds retain distinct position objects and nested calls use temporary contexts. */
final class RenderColumnConversionContext implements NativeDhRenderConversion.Access {
    private static final DhLogger LOGGER = new DhLoggerBuilder().build();
    private static final LongOpenHashSet BROKEN_POS_SET = new LongOpenHashSet();
    private IClientLevelWrapper level;
    private FullDataSourceV2 source;
    private ColumnRenderSource render;
    private int x, z, blockX, blockZ;
    private ColumnArrayView output;
    private LongList input;
    private HashSet<IBlockStateWrapper> ignored, caves, snow;
    private int[] materials;
    private byte[] variants;
    private long[] positions;
    private final DhBlockPosMutable position = new DhBlockPosMutable();
    private static final ThreadLocal<RenderColumnConversionContext> ROOT = ThreadLocal.withInitial(RenderColumnConversionContext::new);
    private boolean active;
    private net.vulkanic.world.NativeDhConversionScope conversionScope;
    private IBlockStateWrapper block;
    private IBiomeWrapper biome;
    private int bottom;
    private byte variant;
    private long variantPosition;

    private RenderColumnConversionContext() {}

    static void convert(int flags, int caveY,
        IClientLevelWrapper level, FullDataSourceV2 source, ColumnRenderSource render,
        int x, int z, int blockX, int blockZ, ColumnArrayView output, LongList input,
        HashSet<IBlockStateWrapper> ignored, HashSet<IBlockStateWrapper> caves, HashSet<IBlockStateWrapper> snow,
        int[] materials, byte[] variants, long[] positions, net.vulkanic.world.NativeDhConversionScope conversionScope) {
        // Stock color queries copy the mutable position before handing it to
        // contextual tint providers. Custom worlds may retain the original.
        RenderColumnConversionContext context = level.getClass() == com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper.class
            ? ROOT.get() : new RenderColumnConversionContext();
        if (context.active) context = new RenderColumnConversionContext();
        context.active = true;
        context.level = level; context.source = source; context.render = render;
        context.x = x; context.z = z; context.blockX = blockX; context.blockZ = blockZ;
        context.output = output; context.input = input; context.ignored = ignored; context.caves = caves; context.snow = snow;
        context.materials = materials; context.variants = variants; context.positions = positions;
        context.position.setX(blockX); context.position.setY(0); context.position.setZ(blockZ);
        context.conversionScope = conversionScope;
        try { NativeDhRenderConversion.convert(flags, caveY, context); }
        finally {
            context.level = null; context.source = null; context.render = null;
            context.output = null; context.input = null;
            context.ignored = null; context.caves = null; context.snow = null;
            context.materials = null; context.variants = null; context.positions = null;
            context.conversionScope = null; context.block = null; context.biome = null;
            context.active = false;
        }
    }

    @Override public long call(int operation, int index, long value, long extra) {
        return switch (operation) {
            case SIZE -> input.size();
            case POINT -> input.getLong(index);
            case RESOLVE -> resolve(index, (int)value);
            case SOLID -> block.isSolid() ? 1 : 0;
            case LIQUID -> block.isLiquid() ? 1 : 0;
            case OPACITY -> block.getOpacity();
            case SNOW -> snow.contains(block) ? 1 : 0;
            case COLOR -> color(index != 0);
            case COLOR_SELECTED -> {
                select((int)value, (int)(value >>> 32));
                yield color(index != 0);
            }
            case PREVIOUS_MATERIAL -> semanticMaterialAt(render, x, z, materials, index);
            case OUTPUT_GET -> output.get(index);
            case OUTPUT_SET -> { output.set(index, value); yield 0; }
            case SEMANTIC_SET, SEMANTIC_SET_UNAVAILABLE -> {
                if (operation == SEMANTIC_SET_UNAVAILABLE) { variant = 0; variantPosition = 0; }
                setSemanticMaterialAt(render, x, z, materials, index, (int)value);
                setSemanticVariantAt(render, x, z, variants, positions, index, variant, variantPosition);
                yield 0;
            }
            case SEMANTIC_MERGE, SEMANTIC_MERGE_UNAVAILABLE -> {
                if (operation == SEMANTIC_MERGE_UNAVAILABLE) { variant = 0; variantPosition = 0; }
                mergeSemanticVariantAt(render, x, z, variants, positions, index, variant, variantPosition);
                yield 0;
            }
            case BLOCK_MATERIAL -> block.getMaterialId();
            case INVALID_POINT -> RenderDataPointUtil.createDataPoint((int)(value >>> 32), (int)value, 0, 0, 0, index);
            default -> throw new IllegalArgumentException("Unknown Rust conversion request: " + operation);
        };
    }
    @Override public java.lang.foreign.MemorySegment inputOwner() {
        return input instanceof net.minecraft.world.level.chunk.NativeDhConversionReader reader
            ? reader.borrowOwner() : java.lang.foreign.MemorySegment.NULL;
    }
    @Override public java.lang.foreign.MemorySegment outputOwner() {
        return output.data instanceof net.vulkanic.world.NativeDhRenderSource nativeOutput
            && output.offset >= 0 && output.size >= 0 && output.offset <= nativeOutput.size() - output.size
            ? nativeOutput.borrowOwner() : java.lang.foreign.MemorySegment.NULL;
    }
    @Override public long outputWindow() { return ((long)output.size << 32) | (output.offset & 0xffff_ffffL); }
    @Override public java.lang.foreign.MemorySegment materialsOwner() {
        return conversionScope != null && input instanceof net.minecraft.world.level.chunk.NativeDhConversionReader
            ? conversionScope.borrow(ignored, caves, snow) : java.lang.foreign.MemorySegment.NULL;
    }
    private void preparePosition(int bottom) {
        this.bottom = bottom;
        variant = ColumnRenderSource.SEMANTIC_VARIANT_UNAVAILABLE;
        variantPosition = 0;
        position.setY(bottom + level.getMinHeight());
    }
    private void select(int id, int bottom) {
        preparePosition(bottom);
        biome = source.mapping.getBiomeWrapper(id);
        block = source.mapping.getBlockStateWrapper(id);
    }
    private long resolve(int id, int bottom) {
        preparePosition(bottom);
        try {
            biome = source.mapping.getBiomeWrapper(id);
            block = source.mapping.getBlockStateWrapper(id);
        } catch (IndexOutOfBoundsException e) {
            if (!BROKEN_POS_SET.contains(source.mapping.getPos())) {
                BROKEN_POS_SET.add(source.mapping.getPos());
                String levelId = level.getDhIdentifier();
                LOGGER.warn("Unable to get data point with id [" + id + "] "
                    + "(Max possible ID: [" + source.mapping.getMaxValidId() + "]) "
                    + "for pos [" + source.mapping.getPos() + "] in level [" + levelId + "]. "
                    + "Error: [" + e.getMessage() + "]. Further errors for this position won't be logged.");
            }
            return 4;
        }
        boolean ignore = ignored.contains(block);
        boolean cave = caves.contains(block);
        long facts = (ignore ? 1 : 0) | (cave ? 2 : 0);
        // Only exact stock wrappers and ordinary sets have intrinsic, immutable
        // getter semantics. Rust derives shape/fluid facts from its registry;
        // dynamic/unsupported shapes still request the ordered Java getters.
        if (block.getClass() == com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper.class
            && snow.getClass() == HashSet.class && net.minecraft.world.level.block.NativeCollisionGeometry.ready()) {
            var stock = (com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper)block;
            int state = net.minecraft.world.level.block.Block.getId(stock.blockState);
            if (stock == com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper.AIR
                || stock.blockState != null && net.minecraft.world.level.block.Block.stateById(state) == stock.blockState) {
                facts |= ((long)(state + 1) << 32) | ((long)Byte.toUnsignedInt(stock.getMaterialId()) << 16)
                    | (snow.contains(stock) ? 8 : 0);
            }
        }
        return facts;
    }
    private long color(boolean preserveSemantic) {
        int color = level.getBlockColor(position, biome, source, block);
        int material = ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE;
        if (preserveSemantic) {
            material = render.internSemanticMaterial(block.getSerialString(), biome.getSerialString());
            if (material > ColumnRenderSource.SEMANTIC_MATERIAL_UNAVAILABLE) {
                variant = ColumnRenderSource.SEMANTIC_VARIANT_EXACT;
                variantPosition = ColumnRenderSource.packSemanticVariantPosition(blockX, bottom + level.getMinHeight(), blockZ);
            }
        }
        else if (conversionScope != null
            && level.getClass() == com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper.class
            && block.getClass() == com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper.class) {
            long constant = ((com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper)level)
                .resolvedConstantColor((com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper)block);
            if (constant >>> 32 != 0 && (int)constant == color) return Long.MIN_VALUE | Integer.toUnsignedLong(color);
        }
        return (color & 0xffff_ffffL) | ((long)material << 32);
    }
}
