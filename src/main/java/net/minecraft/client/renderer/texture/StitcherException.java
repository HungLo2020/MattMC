package net.minecraft.client.renderer.texture;

import java.util.List;
import java.util.Locale;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;

/** A sprite that does not fit the largest atlas Rust's stitcher may build. */
@Environment(EnvType.CLIENT)
public class StitcherException extends RuntimeException {
	private final List<String> allSprites;

	public StitcherException(String sprite, int width, int height, List<String> allSprites) {
		super(String.format(Locale.ROOT, "Unable to fit: %s - size: %dx%d - Maybe try a lower resolution resourcepack?", sprite, width, height));
		this.allSprites = List.copyOf(allSprites);
	}

	/** Every sprite as `name[WxH]`. */
	public List<String> getAllSprites() {
		return this.allSprites;
	}
}
