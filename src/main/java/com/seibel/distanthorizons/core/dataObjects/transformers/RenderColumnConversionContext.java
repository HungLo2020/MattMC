package com.seibel.distanthorizons.core.dataObjects.transformers;

import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPosMutable;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.coreapi.util.BitShiftUtil;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.HashSet;
import net.vulkanic.world.NativeDhConversionScope;
import net.vulkanic.world.NativeDhRenderConversion;
import static net.vulkanic.world.NativeDhRenderConversion.*;
import static com.seibel.distanthorizons.core.dataObjects.transformers.FullDataToRenderDataTransformer.*;

/**
 * Answers one source conversion's contextual queries. Rust owns the column
 * loop and announces each column before its first query; the context then
 * holds that column's world position and, for semantic builds, its sidecars.
 */
final class RenderColumnConversionContext implements NativeDhRenderConversion.Access {
    private static final DhLogger LOGGER = new DhLoggerBuilder().build();
    private static final LongOpenHashSet BROKEN_POS_SET = new LongOpenHashSet();
    private final IClientLevelWrapper level;
    private final ClientLevelWrapper stockLevel;
    private final FullDataSourceV2 source;
    private final ColumnRenderSource render;
    private final HashSet<IBlockStateWrapper> ignored, caves, snow;
    private final boolean semantic;
    private final NativeDhConversionScope scope;
    private final int baseX, baseZ;
    private final byte detail;
    private final DhBlockPosMutable position = new DhBlockPosMutable();
    private int x, z, blockX, blockZ;
    // A tall semantic column's sidecars; null while converting in place.
    private int expandedLength;
    private int[] materials;
    private byte[] variants;
    private long[] positions;
    private IBlockStateWrapper block;
    private IBiomeWrapper biome;
    private int bottom;
    private byte variant;
    private long variantPosition;

    private RenderColumnConversionContext(IClientLevelWrapper level, FullDataSourceV2 source, ColumnRenderSource render,
        HashSet<IBlockStateWrapper> ignored, HashSet<IBlockStateWrapper> caves, HashSet<IBlockStateWrapper> snow,
        boolean semantic, NativeDhConversionScope scope) {
        this.level = level;
        this.stockLevel = level instanceof ClientLevelWrapper wrapper ? wrapper : null;
        this.source = source; this.render = render;
        this.ignored = ignored; this.caves = caves; this.snow = snow;
        this.semantic = semantic; this.scope = scope;
        this.baseX = DhSectionPos.getMinCornerBlockX(source.getPos());
        this.baseZ = DhSectionPos.getMinCornerBlockZ(source.getPos());
        this.detail = source.getDataDetailLevel();
    }

    static void convert(int flags, int caveY, IClientLevelWrapper level, FullDataSourceV2 source, ColumnRenderSource render,
        HashSet<IBlockStateWrapper> ignored, HashSet<IBlockStateWrapper> caves, HashSet<IBlockStateWrapper> snow,
        boolean semantic, NativeDhConversionScope scope) {
        var context = new RenderColumnConversionContext(level, source, render, ignored, caves, snow, semantic, scope);
        NativeDhRenderConversion.convertSource(flags, caveY, context, source.nativeSource().stageOwner(),
            render.nativeData(), render.verticalDataCount, scope.scratch(), scope.borrow(ignored, caves, snow));
    }

    @Override public long call(int operation, int index, long value) {
        return switch (operation) {
            case COLUMN_BEGIN -> { begin(index, (int)value); yield 0; }
            case COLUMN_END -> {
                finishSemanticColumn(source, render, x, z, blockX, blockZ, scope.scratch(), (int)value, materials, variants, positions);
                yield 0;
            }
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
    private void begin(int column, int expandedLength) {
        x = column / ColumnRenderSource.WIDTH;
        z = column % ColumnRenderSource.WIDTH;
        // bitshift is to account for LODs with a detail level greater than 0 so the block pos is correct
        blockX = baseX + BitShiftUtil.pow(x, detail);
        blockZ = baseZ + BitShiftUtil.pow(z, detail);
        position.setX(blockX); position.setY(0); position.setZ(blockZ);
        block = null; biome = null;
        this.expandedLength = expandedLength;
        if (semantic) {
            render.clearSemanticMaterialsForColumn(x, z);
            materials = expandedLength > 0 ? new int[expandedLength] : null;
            variants = expandedLength > 0 ? new byte[expandedLength] : null;
            positions = expandedLength > 0 ? new long[expandedLength] : null;
        }
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
        // Registered states carry intrinsic facts Rust derives from its registry
        // and caches per id. A wrapper without a registered state (an unknown
        // saved block) is answered through the ordered getters instead.
        if (block instanceof BlockStateWrapper stock && net.minecraft.world.level.block.NativeCollisionGeometry.ready()) {
            int state = net.minecraft.world.level.block.Block.getId(stock.blockState);
            if (stock == BlockStateWrapper.AIR
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
        else if (stockLevel != null && block instanceof BlockStateWrapper stock) {
            // Untinted resolved colors are position independent; Rust caches them per id.
            long constant = stockLevel.resolvedConstantColor(stock);
            if (constant >>> 32 != 0 && (int)constant == color) return Long.MIN_VALUE | Integer.toUnsignedLong(color);
        }
        return (color & 0xffff_ffffL) | ((long)material << 32);
    }
}
