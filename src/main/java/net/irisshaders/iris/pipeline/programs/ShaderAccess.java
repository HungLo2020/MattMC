package net.irisshaders.iris.pipeline.programs;

import net.blaze3d.vertex.VertexFormat;
import net.blaze3d.vertex.VertexFormatElement;

public class ShaderAccess {
	public static final VertexFormat IE_FORMAT = VertexFormat.builder()
		.add("Position", VertexFormatElement.POSITION)
		.add("Color", VertexFormatElement.COLOR)
		.add("UV0", VertexFormatElement.UV0)
		.add("Normal", VertexFormatElement.NORMAL)
		.padding(1)
		.build();

	// TODO SPS 1.21.2
}
