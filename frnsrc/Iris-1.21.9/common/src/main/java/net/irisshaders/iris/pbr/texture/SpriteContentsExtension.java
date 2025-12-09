package frnsrc.Iris;

import org.jetbrains.annotations.Nullable;

public interface SpriteContentsExtension {
	@Nullable
	PBRSpriteHolder getPBRHolder();

	PBRSpriteHolder getOrCreatePBRHolder();
}
