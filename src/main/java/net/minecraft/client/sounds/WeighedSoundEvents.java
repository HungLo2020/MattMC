package net.minecraft.client.sounds;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import org.jetbrains.annotations.Nullable;

/**
 * A view of one registered sound event. Weighting, nesting and resolution are
 * owned by the Rust registry; this keeps the event's identity and subtitle,
 * and draws from the caller's random source so its sequence is unchanged.
 */
@Environment(EnvType.CLIENT)
public class WeighedSoundEvents {
	private final ResourceLocation location;
	@Nullable
	private final Component subtitle;
	/** False for Java-side placeholders that have no registry entry. */
	private final boolean registered;

	public WeighedSoundEvents(ResourceLocation resourceLocation, @Nullable String string) {
		this(resourceLocation, string, false);
	}

	private WeighedSoundEvents(ResourceLocation resourceLocation, @Nullable String string, boolean registered) {
		this.location = resourceLocation;
		this.registered = registered;
		if (SharedConstants.DEBUG_SUBTITLES) {
			MutableComponent mutableComponent = Component.literal(resourceLocation.getPath());
			if ("FOR THE DEBUG!".equals(string)) {
				mutableComponent = mutableComponent.append(Component.literal(" missing").withStyle(ChatFormatting.RED));
			}

			this.subtitle = mutableComponent;
		} else {
			this.subtitle = string == null ? null : Component.translatable(string);
		}
	}

	/** A view of a registered event and its subtitle key. */
	static WeighedSoundEvents registered(ResourceLocation location, @Nullable String subtitle) {
		return new WeighedSoundEvents(location, subtitle, true);
	}

	public int getWeight() {
		return this.registered ? NativeSoundPolicy.eventWeight(this.location.toString()) : 0;
	}

	public Sound getSound(RandomSource randomSource) {
		if (!this.registered) {
			return SoundManager.EMPTY_SOUND;
		}
		NativeSoundPolicy.Resolved resolved = NativeSoundPolicy.resolve(this.location.toString(), randomSource::nextInt);
		if (resolved.kind() != NativeSoundPolicy.RESOLVE_SOUND) {
			return SoundManager.EMPTY_SOUND;
		}
		return new Sound(
			ResourceLocation.parse(resolved.location()),
			ConstantFloat.of(resolved.volume()),
			ConstantFloat.of(resolved.pitch()),
			resolved.weight(),
			Sound.Type.FILE,
			(resolved.flags() & NativeSoundPolicy.RESOLVED_STREAM) != 0,
			(resolved.flags() & NativeSoundPolicy.RESOLVED_PRELOAD) != 0,
			resolved.attenuationDistance()
		);
	}

	@Nullable
	public Component getSubtitle() {
		return this.subtitle;
	}
}
