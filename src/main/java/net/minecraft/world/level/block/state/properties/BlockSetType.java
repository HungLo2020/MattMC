package net.minecraft.world.level.block.state.properties;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.SoundType;

public record BlockSetType(
	String name,
	boolean canOpenByHand,
	boolean canOpenByWindCharge,
	boolean canButtonBeActivatedByArrows,
	BlockSetType.PressurePlateSensitivity pressurePlateSensitivity,
	SoundType soundType,
	SoundEvent doorClose,
	SoundEvent doorOpen,
	SoundEvent trapdoorClose,
	SoundEvent trapdoorOpen,
	SoundEvent pressurePlateClickOff,
	SoundEvent pressurePlateClickOn,
	SoundEvent buttonClickOff,
	SoundEvent buttonClickOn
) {
	private static final BlockSetType[] SETS = NativeBlockFamilies.createBlockSets();
	private static final Map<String, BlockSetType> TYPES = new Object2ObjectArrayMap<>();
	public static final Codec<BlockSetType> CODEC = Codec.stringResolver(BlockSetType::name, TYPES::get);
	public static final BlockSetType IRON = register(SETS[0]);
	public static final BlockSetType COPPER = register(SETS[1]);
	public static final BlockSetType GOLD = register(SETS[2]);
	public static final BlockSetType STONE = register(SETS[3]);
	public static final BlockSetType POLISHED_BLACKSTONE = register(SETS[4]);
	public static final BlockSetType OAK = register(SETS[5]);
	public static final BlockSetType SPRUCE = register(SETS[6]);
	public static final BlockSetType BIRCH = register(SETS[7]);
	public static final BlockSetType ACACIA = register(SETS[8]);
	public static final BlockSetType CHERRY = register(SETS[9]);
	public static final BlockSetType JUNGLE = register(SETS[10]);
	public static final BlockSetType DARK_OAK = register(SETS[11]);
	public static final BlockSetType PALE_OAK = register(SETS[12]);
	public static final BlockSetType CRIMSON = register(SETS[13]);
	public static final BlockSetType WARPED = register(SETS[14]);
	public static final BlockSetType MANGROVE = register(SETS[15]);
	public static final BlockSetType BAMBOO = register(SETS[16]);

	public static BlockSetType nativeView(int id) { return SETS[id]; }

	public BlockSetType(String string) {
		this(
			string,
			true,
			true,
			true,
			BlockSetType.PressurePlateSensitivity.EVERYTHING,
			SoundType.WOOD,
			SoundEvents.WOODEN_DOOR_CLOSE,
			SoundEvents.WOODEN_DOOR_OPEN,
			SoundEvents.WOODEN_TRAPDOOR_CLOSE,
			SoundEvents.WOODEN_TRAPDOOR_OPEN,
			SoundEvents.WOODEN_PRESSURE_PLATE_CLICK_OFF,
			SoundEvents.WOODEN_PRESSURE_PLATE_CLICK_ON,
			SoundEvents.WOODEN_BUTTON_CLICK_OFF,
			SoundEvents.WOODEN_BUTTON_CLICK_ON
		);
	}

	private static BlockSetType register(BlockSetType blockSetType) {
		TYPES.put(blockSetType.name, blockSetType);
		return blockSetType;
	}

	public static Stream<BlockSetType> values() {
		return TYPES.values().stream();
	}

	public static enum PressurePlateSensitivity {
		EVERYTHING,
		MOBS;
	}
}
