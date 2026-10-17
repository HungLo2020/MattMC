package net.minecraft.world.level.material;

import com.google.common.base.Preconditions;

public class MapColor {
	private static final MapColor[] MATERIAL_COLORS = new MapColor[64];
	public static final MapColor NONE = new MapColor(0);
	public static final MapColor GRASS = new MapColor(1);
	public static final MapColor SAND = new MapColor(2);
	public static final MapColor WOOL = new MapColor(3);
	public static final MapColor FIRE = new MapColor(4);
	public static final MapColor ICE = new MapColor(5);
	public static final MapColor METAL = new MapColor(6);
	public static final MapColor PLANT = new MapColor(7);
	public static final MapColor SNOW = new MapColor(8);
	public static final MapColor CLAY = new MapColor(9);
	public static final MapColor DIRT = new MapColor(10);
	public static final MapColor STONE = new MapColor(11);
	public static final MapColor WATER = new MapColor(12);
	public static final MapColor WOOD = new MapColor(13);
	public static final MapColor QUARTZ = new MapColor(14);
	public static final MapColor COLOR_ORANGE = new MapColor(15);
	public static final MapColor COLOR_MAGENTA = new MapColor(16);
	public static final MapColor COLOR_LIGHT_BLUE = new MapColor(17);
	public static final MapColor COLOR_YELLOW = new MapColor(18);
	public static final MapColor COLOR_LIGHT_GREEN = new MapColor(19);
	public static final MapColor COLOR_PINK = new MapColor(20);
	public static final MapColor COLOR_GRAY = new MapColor(21);
	public static final MapColor COLOR_LIGHT_GRAY = new MapColor(22);
	public static final MapColor COLOR_CYAN = new MapColor(23);
	public static final MapColor COLOR_PURPLE = new MapColor(24);
	public static final MapColor COLOR_BLUE = new MapColor(25);
	public static final MapColor COLOR_BROWN = new MapColor(26);
	public static final MapColor COLOR_GREEN = new MapColor(27);
	public static final MapColor COLOR_RED = new MapColor(28);
	public static final MapColor COLOR_BLACK = new MapColor(29);
	public static final MapColor GOLD = new MapColor(30);
	public static final MapColor DIAMOND = new MapColor(31);
	public static final MapColor LAPIS = new MapColor(32);
	public static final MapColor EMERALD = new MapColor(33);
	public static final MapColor PODZOL = new MapColor(34);
	public static final MapColor NETHER = new MapColor(35);
	public static final MapColor TERRACOTTA_WHITE = new MapColor(36);
	public static final MapColor TERRACOTTA_ORANGE = new MapColor(37);
	public static final MapColor TERRACOTTA_MAGENTA = new MapColor(38);
	public static final MapColor TERRACOTTA_LIGHT_BLUE = new MapColor(39);
	public static final MapColor TERRACOTTA_YELLOW = new MapColor(40);
	public static final MapColor TERRACOTTA_LIGHT_GREEN = new MapColor(41);
	public static final MapColor TERRACOTTA_PINK = new MapColor(42);
	public static final MapColor TERRACOTTA_GRAY = new MapColor(43);
	public static final MapColor TERRACOTTA_LIGHT_GRAY = new MapColor(44);
	public static final MapColor TERRACOTTA_CYAN = new MapColor(45);
	public static final MapColor TERRACOTTA_PURPLE = new MapColor(46);
	public static final MapColor TERRACOTTA_BLUE = new MapColor(47);
	public static final MapColor TERRACOTTA_BROWN = new MapColor(48);
	public static final MapColor TERRACOTTA_GREEN = new MapColor(49);
	public static final MapColor TERRACOTTA_RED = new MapColor(50);
	public static final MapColor TERRACOTTA_BLACK = new MapColor(51);
	public static final MapColor CRIMSON_NYLIUM = new MapColor(52);
	public static final MapColor CRIMSON_STEM = new MapColor(53);
	public static final MapColor CRIMSON_HYPHAE = new MapColor(54);
	public static final MapColor WARPED_NYLIUM = new MapColor(55);
	public static final MapColor WARPED_STEM = new MapColor(56);
	public static final MapColor WARPED_HYPHAE = new MapColor(57);
	public static final MapColor WARPED_WART_BLOCK = new MapColor(58);
	public static final MapColor DEEPSLATE = new MapColor(59);
	public static final MapColor RAW_IRON = new MapColor(60);
	public static final MapColor GLOW_LICHEN = new MapColor(61);
	public final int col;
	public final int id;

	private MapColor(int i) {
		if (i >= 0 && i <= 63) {
			this.id = i;
			this.col = NativeMapColors.rgb(i);
			MATERIAL_COLORS[i] = this;
		} else {
			throw new IndexOutOfBoundsException("Map colour ID must be between 0 and 63 (inclusive)");
		}
	}

	public int calculateARGBColor(MapColor.Brightness brightness) {
		return this == NONE ? 0 : NativeMapColors.packedArgb(this.id << 2 | brightness.id);
	}

	public static MapColor byId(int i) {
		Preconditions.checkPositionIndex(i, MATERIAL_COLORS.length, "material id");
		return byIdUnsafe(i);
	}

	private static MapColor byIdUnsafe(int i) {
		MapColor mapColor = MATERIAL_COLORS[i];
		return mapColor != null ? mapColor : NONE;
	}

	public static int getColorFromPackedId(int i) {
		return NativeMapColors.packedArgb(i);
	}

	public byte getPackedId(MapColor.Brightness brightness) {
		return (byte)(this.id << 2 | brightness.id & 3);
	}

	public static enum Brightness {
		LOW(0),
		NORMAL(1),
		HIGH(2),
		LOWEST(3);

		private static final MapColor.Brightness[] VALUES = new MapColor.Brightness[]{LOW, NORMAL, HIGH, LOWEST};
		public final int id;
		public final int modifier;

		private Brightness(final int j) {
			this.id = j;
			this.modifier = NativeMapColors.brightness(j);
		}

		public static MapColor.Brightness byId(int i) {
			Preconditions.checkPositionIndex(i, VALUES.length, "brightness id");
			return byIdUnsafe(i);
		}

		static MapColor.Brightness byIdUnsafe(int i) {
			return VALUES[i];
		}
	}
}
