import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.common.wrappers.block.ClientBlockStateColorCache;
import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;

/** CPU-only initialized-cache fixture. Executes actual stock color-cache reads
 * on both repositories; does not emulate texture resolution or a running world. */
final class DhResolvedColorFixture {
    private static Object allocate(Class<?> type) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        var field = unsafe.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return unsafe.getMethod("allocateInstance", Class.class).invoke(field.get(null), type);
    }
    private static void set(Class<?> type, Object object, String name, Object value) throws Exception {
        var field = type.getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
    static ClientLevelWrapper create(IBlockStateWrapper[] blocks, int[] colors) throws Exception {
        var world = (ClientLevel)allocate(ClientLevel.class);
        set(Level.class, world, "registryAccess", RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        var dimension = new DimensionType(OptionalLong.empty(), true, false, false, true, 1.0, true, false,
            -64, 384, 384, BlockTags.INFINIBURN_OVERWORLD, BuiltinDimensionTypes.OVERWORLD_EFFECTS, 0,
            Optional.empty(), new DimensionType.MonsterSettings(false, true, ConstantInt.of(0), 0));
        set(Level.class, world, "dimensionTypeRegistration", Holder.direct(dimension));
        var constructor = ClientLevelWrapper.class.getDeclaredConstructor(ClientLevel.class);
        constructor.setAccessible(true);
        var wrapper = constructor.newInstance(world);
        var field = ClientLevelWrapper.class.getDeclaredField("blockColorCacheByBlockState"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var cache = (ConcurrentHashMap<BlockState, ClientBlockStateColorCache>)field.get(wrapper);
        for (int i = 0; i < blocks.length; i++) {
            var block = (BlockStateWrapper)blocks[i];
            if (block.blockState == null) continue; // Ignored air is never color-resolved.
            var color = (ClientBlockStateColorCache)allocate(ClientBlockStateColorCache.class);
            set(ClientBlockStateColorCache.class, color, "clientLevelWrapper", wrapper);
            set(ClientBlockStateColorCache.class, color, "blockState", block.blockState);
            set(ClientBlockStateColorCache.class, color, "blockStateWrapper", block);
            set(ClientBlockStateColorCache.class, color, "baseColor", colors[i]);
            set(ClientBlockStateColorCache.class, color, "needPostTinting", false);
            set(ClientBlockStateColorCache.class, color, "isColorResolved", true);
            cache.put(block.blockState, color);
        }
        return wrapper;
    }
}
