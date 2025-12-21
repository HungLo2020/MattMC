package frnsrc.Iris;

import java.util.function.IntSupplier;

public interface TextureAccess {
	TextureType getType();

	IntSupplier getTextureId();
}
