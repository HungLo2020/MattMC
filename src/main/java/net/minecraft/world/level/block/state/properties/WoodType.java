package net.minecraft.world.level.block.state.properties;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.SoundType;

public record WoodType(
	String name, BlockSetType setType, SoundType soundType, SoundType hangingSignSoundType, SoundEvent fenceGateClose, SoundEvent fenceGateOpen
) {
	private static final WoodType[] WOODS = NativeBlockFamilies.createWoods();
	private static final Map<String, WoodType> TYPES = new Object2ObjectArrayMap<>();
	public static final Codec<WoodType> CODEC = Codec.stringResolver(WoodType::name, TYPES::get);
	public static final WoodType OAK = register(WOODS[0]);
	public static final WoodType SPRUCE = register(WOODS[1]);
	public static final WoodType BIRCH = register(WOODS[2]);
	public static final WoodType ACACIA = register(WOODS[3]);
	public static final WoodType CHERRY = register(WOODS[4]);
	public static final WoodType JUNGLE = register(WOODS[5]);
	public static final WoodType DARK_OAK = register(WOODS[6]);
	public static final WoodType PALE_OAK = register(WOODS[7]);
	public static final WoodType CRIMSON = register(WOODS[8]);
	public static final WoodType WARPED = register(WOODS[9]);
	public static final WoodType MANGROVE = register(WOODS[10]);
	public static final WoodType BAMBOO = register(WOODS[11]);

	public static WoodType nativeView(int id) { return WOODS[id]; }

	public WoodType(String string, BlockSetType blockSetType) {
		this(string, blockSetType, SoundType.WOOD, SoundType.HANGING_SIGN, SoundEvents.FENCE_GATE_CLOSE, SoundEvents.FENCE_GATE_OPEN);
	}

	private static WoodType register(WoodType woodType) {
		TYPES.put(woodType.name(), woodType);
		return woodType;
	}

	public static Stream<WoodType> values() {
		return TYPES.values().stream();
	}
}
