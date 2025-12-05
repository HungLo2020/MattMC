package net.minecraft.world.scores;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.NumberFormatTypes;
import org.jetbrains.annotations.Nullable;

public class Score implements ReadOnlyScoreInfo {
	public static final MapCodec<Score> MAP_CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				Codec.INT.optionalFieldOf("Score", 0).forGetter(Score::value),
				Codec.BOOL.optionalFieldOf("Locked", false).forGetter(Score::isLocked),
				ComponentSerialization.CODEC.optionalFieldOf("display").forGetter(score -> Optional.ofNullable(score.display)),
				NumberFormatTypes.CODEC.optionalFieldOf("format").forGetter(score -> Optional.ofNullable(score.numberFormat))
			)
			.apply(instance, Score::new)
	);
	private int value;
	private boolean locked = true;
	@Nullable
	private Component display;
	@Nullable
	private NumberFormat numberFormat;

	public Score() {
	}

	private Score(int i, boolean bl, Optional<Component> optional, Optional<NumberFormat> optional2) {
		this.value = i;
		this.locked = bl;
		this.display = (Component)optional.orElse(null);
		this.numberFormat = (NumberFormat)optional2.orElse(null);
	}

	@Override
	public int value() {
		return this.value;
	}

	public void value(int i) {
		this.value = i;
	}

	@Override
	public boolean isLocked() {
		return this.locked;
	}

	public void setLocked(boolean bl) {
		this.locked = bl;
	}

	@Nullable
	public Component display() {
		return this.display;
	}

	public void display(@Nullable Component component) {
		this.display = component;
	}

	@Nullable
	@Override
	public NumberFormat numberFormat() {
		return this.numberFormat;
	}

	public void numberFormat(@Nullable NumberFormat numberFormat) {
		this.numberFormat = numberFormat;
	}
}
