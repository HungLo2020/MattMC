package net.minecraft.client.resources.model;

import java.util.List;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class QuadCollection {
	public static final QuadCollection EMPTY = new QuadCollection(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
	private final List<BakedQuad> all;
	private final List<BakedQuad> unculled;
	private final List<BakedQuad> north;
	private final List<BakedQuad> south;
	private final List<BakedQuad> east;
	private final List<BakedQuad> west;
	private final List<BakedQuad> up;
	private final List<BakedQuad> down;

	QuadCollection(
		List<BakedQuad> list,
		List<BakedQuad> list2,
		List<BakedQuad> list3,
		List<BakedQuad> list4,
		List<BakedQuad> list5,
		List<BakedQuad> list6,
		List<BakedQuad> list7,
		List<BakedQuad> list8
	) {
		this.all = list;
		this.unculled = list2;
		this.north = list3;
		this.south = list4;
		this.east = list5;
		this.west = list6;
		this.up = list7;
		this.down = list8;
	}

	public List<BakedQuad> getQuads(@Nullable Direction direction) {
		return switch (direction) {
			case null -> this.unculled;
			case NORTH -> this.north;
			case SOUTH -> this.south;
			case EAST -> this.east;
			case WEST -> this.west;
			case UP -> this.up;
			case DOWN -> this.down;
			default -> throw new MatchException(null, null);
		};
	}

	public List<BakedQuad> getAll() {
		return this.all;
	}
}
