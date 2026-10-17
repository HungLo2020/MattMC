package net.minecraft.resources;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Decoded forms of the primitive types native values embed
 * (`content/data/values.rs`), to and from their Java objects.
 */
public final class NativeValueTypes {
	private NativeValueTypes() {
	}

	/** {@code BlockPos.CODEC}: three ints. */
	public static BlockPos blockPos(Object value) {
		List<Object> xyz = NativeData.list(value);
		return new BlockPos((Integer)xyz.get(0), (Integer)xyz.get(1), (Integer)xyz.get(2));
	}

	public static Object blockPos(BlockPos pos) {
		return List.of(pos.getX(), pos.getY(), pos.getZ());
	}

	/** {@code UUIDUtil.CODEC}: four ints. */
	public static UUID uuid(Object value) {
		List<Object> ints = NativeData.list(value);
		return UUIDUtil.uuidFromIntArray(new int[]{(Integer)ints.get(0), (Integer)ints.get(1), (Integer)ints.get(2), (Integer)ints.get(3)});
	}

	public static Object uuid(UUID uuid) {
		int[] ints = UUIDUtil.uuidToIntArray(uuid);
		return List.of(ints[0], ints[1], ints[2], ints[3]);
	}

	/** {@code Vec3.CODEC}: three doubles. */
	public static Vec3 vec3(Object value) {
		List<Object> xyz = NativeData.list(value);
		return new Vec3((Double)xyz.get(0), (Double)xyz.get(1), (Double)xyz.get(2));
	}

	public static Object vec3(Vec3 vec) {
		return List.of(vec.x, vec.y, vec.z);
	}

	/** An int that also reads from another form ({@code RGB_COLOR_CODEC}, {@code ARGB_COLOR_CODEC}). */
	public static int color(Object value) {
		return (Integer)((NativeData.Variant)value).fields()[0];
	}

	public static Object color(int color) {
		return new NativeData.Variant(0, new Object[]{color});
	}

	/** A block state ID, read from a state or a block's name. */
	public static BlockState blockState(Object value) {
		Object id = value instanceof NativeData.Variant variant ? variant.fields()[0] : value;
		return Block.BLOCK_STATE_REGISTRY.byId((Integer)id);
	}

	public static Object blockStateOrName(BlockState state) {
		return new NativeData.Variant(0, new Object[]{Block.BLOCK_STATE_REGISTRY.getId(state)});
	}
}
