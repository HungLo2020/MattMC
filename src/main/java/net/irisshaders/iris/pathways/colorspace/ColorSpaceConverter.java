package net.irisshaders.iris.pathways.colorspace;

import net.blaze3d.opengl.GlTexture;

public interface ColorSpaceConverter {
	void rebuildProgram(int width, int height, ColorSpace colorSpace);

	void process(GlTexture target);
}
